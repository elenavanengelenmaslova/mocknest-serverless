# Design Document: IAM Auth Mode

## Overview

This feature adds AWS IAM (SigV4) as a selectable authentication mode for all API Gateway endpoints in a MockNest Serverless deployment. Authentication mode is chosen at deploy time via a new SAM parameter `AuthMode` (values: `API_KEY` | `IAM`, default: `API_KEY`). No Lambda application code changes are required — auth enforcement is handled entirely at the API Gateway layer through SAM conditional resources.

The SAR release validation pipeline (`sar-test-pipeline.yml`) is extended to run two independent validation runs — one per auth mode — each deploying its own isolated test stack, executing health checks against `/__admin/health` and `/ai/generation/health`, reporting results, and cleaning up regardless of outcome.

### Design Decisions

- **Auth at the edge only**: API Gateway handles auth before the request reaches Lambda. This keeps Lambda code unchanged and auth logic in one place.
- **Single parameter, full consistency**: One `AuthMode` parameter drives all endpoint auth via SAM `Conditions`, ensuring no endpoint can accidentally use a different mode.
- **Default unchanged**: `API_KEY` remains the default so all existing deployments are unaffected.
- **No API key resources in IAM mode**: When `AuthMode=IAM`, the `MockNestApiKey`, `MockNestUsagePlan`, and `MockNestUsagePlanKey` resources are conditionally excluded to avoid creating unused resources.
- **Independent validation runs**: The two pipeline jobs run independently so a failure in one does not suppress the other's result.

---

## Architecture

The change touches two areas:

1. **SAM template** (`deployment/aws/sam/template.yaml`) — adds the `AuthMode` parameter, a `Condition`, and conditional auth configuration on all three Lambda event sources.
2. **SAR validation pipeline** (`sar-test-pipeline.yml` and `sar-publish.yml`) — splits the single test job into two independent validation runs, each with its own deploy/health-check/cleanup/report cycle.

```mermaid
flowchart TB
    subgraph SAM Template
        P[AuthMode Parameter\nAPI_KEY | IAM\ndefault: API_KEY]
        C{IsIamMode\nCondition}
        P --> C

        subgraph API_KEY path
            AK[ApiKeyRequired: true\non all events]
            RES[MockNestApiKey\nMockNestUsagePlan\nMockNestUsagePlanKey]
            OUT[MockNestApiKey Output]
        end

        subgraph IAM path
            IA[Authorizer: AWS_IAM\non all events]
            NORES[No API key resources]
        end

        C -- false --> AK
        C -- false --> RES
        C -- false --> OUT
        C -- true --> IA
        C -- true --> NORES
    end

    subgraph SAR Validation Pipeline
        PUB[SAR Publish]
        PUB --> RUN1
        PUB --> RUN2

        subgraph RUN1 [API_KEY Validation Run]
            D1[Deploy\nAuthMode=API_KEY]
            H1[Health checks\nx-api-key header]
            C1[Cleanup]
            S1[Report]
            D1 --> H1 --> C1 --> S1
        end

        subgraph RUN2 [IAM Validation Run]
            D2[Deploy\nAuthMode=IAM]
            H2[Health checks\nSigV4 signed]
            C2[Cleanup]
            S2[Report]
            D2 --> H2 --> C2 --> S2
        end

        RUN1 --> SUMMARY
        RUN2 --> SUMMARY
        SUMMARY[Combined Summary\nFail if either run failed]
    end
```

---

## Components and Interfaces

### 1. SAM Template — `AuthMode` Parameter

A new top-level parameter is added:

```yaml
AuthMode:
  Type: String
  Default: 'API_KEY'
  AllowedValues:
    - API_KEY
    - IAM
  Description: |
    Authentication mode for all API Gateway endpoints.
    API_KEY (default): callers supply an x-api-key header. An API key and usage plan are created automatically.
    IAM: callers sign requests with AWS Signature Version 4. No API key is created.
```

### 2. SAM Template — `IsIamMode` Condition

```yaml
Conditions:
  IsIamMode: !Equals [!Ref AuthMode, 'IAM']
  IsApiKeyMode: !Not [IsIamMode]
```

`IsIamMode` drives the conditional auth on event sources via `!If`. `IsApiKeyMode` is required as a named condition for use on resources and outputs, because CloudFormation's `Condition` property on a resource or output must reference a named condition — inline `!Not` expressions are not valid there.

### 3. SAM Template — Endpoint Auth Configuration

All three Lambda event sources (`AdminRoutes`, `MockRoutes`, `AIRoutes`) gain conditional auth:

```yaml
Auth:
  ApiKeyRequired: !If [IsIamMode, false, true]
  Authorizer: !If [IsIamMode, AWS_IAM, NONE]
```

When `AuthMode=API_KEY`: `ApiKeyRequired: true`, `Authorizer: NONE` (API Gateway default key enforcement).  
When `AuthMode=IAM`: `ApiKeyRequired: false`, `Authorizer: AWS_IAM`.

### 4. SAM Template — Conditional API Key Resources

The three API key resources are wrapped with `Condition: IsApiKeyMode` (only created in API_KEY mode):

- `MockNestApiKey`
- `MockNestUsagePlan`
- `MockNestUsagePlanKey`

The `MockNestApiKey` CloudFormation output is also conditional:

```yaml
MockNestApiKey:
  Condition: IsApiKeyMode
  Description: "API key ID (only present when AuthMode=API_KEY)"
  Value: !GetAtt MockNestApiKey.APIKeyId
```

Note: `Condition` on a resource or output must reference a named condition. Using `Condition: !Not [IsIamMode]` inline is not valid CloudFormation — hence the need for the named `IsApiKeyMode` condition.

### 5. SAR Validation Pipeline — Two Validation Runs

The existing `sar-test-pipeline.yml` reusable workflow is extended with an `auth-mode` input parameter. The caller (`sar-publish.yml`) invokes it twice — once per mode — as independent jobs.

**New input on `sar-test-pipeline.yml`:**

```yaml
auth-mode:
  required: false
  type: string
  default: 'API_KEY'
  description: 'Authentication mode to validate: API_KEY or IAM'
```

**`sar-publish.yml` job structure:**

```yaml
test-api-key:
  needs: publish
  uses: ./.github/workflows/sar-test-pipeline.yml
  with:
    aws-region: ...
    version: ...
    auth-mode: 'API_KEY'
  secrets: ...

test-iam:
  needs: publish
  uses: ./.github/workflows/sar-test-pipeline.yml
  with:
    aws-region: ...
    version: ...
    auth-mode: 'IAM'
  secrets: ...

summary:
  needs: [publish, test-api-key, test-iam]
  if: always()
  ...
```

Both `test-api-key` and `test-iam` run independently (no `needs` dependency between them), so a failure in one does not block the other.

### 6. SAR Validation Pipeline — Auth-Mode-Specific Stack Naming

Each run uses a unique stack name incorporating both the run ID and the auth mode:

```
mocknest-sar-test-<run_id>-apikey   # for API_KEY run
mocknest-sar-test-<run_id>-iam      # for IAM run
```

This ensures concurrent runs never conflict.

### 7. SAR Validation Pipeline — Deployment Step

The deploy step passes the `AuthMode` parameter override:

```bash
--parameter-overrides \
  Name=DeploymentName,Value=sartest${{ github.run_id }} \
  Name=AuthMode,Value=${{ inputs.auth-mode }}
```

### 8. SAR Validation Pipeline — Health Check Authentication

The health check step branches on `auth-mode`:

**API_KEY mode** (existing behavior, unchanged):
```bash
curl -H "x-api-key: $API_KEY_VALUE" "${API_URL}/__admin/health"
curl -H "x-api-key: $API_KEY_VALUE" "${API_URL}/ai/generation/health"
```

**IAM mode** (new):
```bash
# Use a real SigV4-signed request against the deployed API URL
curl --aws-sigv4 "aws:amz:${REGION}:execute-api" \
  --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" \
  -H "x-amz-security-token: $AWS_SESSION_TOKEN" \
  "${API_URL}/__admin/health"
```

The pipeline's OIDC role (`GitHubOIDCAdmin`) must have `execute-api:Invoke` permission on the test stack's API. This is granted via an IAM policy on the role.

### 9. SAR Validation Pipeline — Stack Output Retrieval

In API_KEY mode, the pipeline retrieves the API key ID from stack outputs and then fetches the key value from API Gateway (existing behavior).

In IAM mode, the pipeline skips API key retrieval entirely — no `MockNestApiKey` output exists in the stack.

### 10. SAR Validation Pipeline — Per-Run Summary

Each validation run produces a summary section that includes:
- Auth mode tested (`API_KEY` or `IAM`)
- Overall pass/fail status
- Phase that failed (if any) with HTTP status code or error message
- Cleanup status

The combined summary job aggregates both run results and marks the overall release as failed if either run failed.

---

## Data Models

No new data models are introduced. The feature is purely infrastructure and pipeline configuration.

**SAM template parameter additions:**

| Parameter | Type | Default | AllowedValues |
|-----------|------|---------|---------------|
| `AuthMode` | String | `API_KEY` | `API_KEY`, `IAM` |

**SAM template condition additions:**

| Condition | Expression |
|-----------|------------|
| `IsIamMode` | `!Equals [!Ref AuthMode, 'IAM']` |
| `IsApiKeyMode` | `!Not [IsIamMode]` |

**Pipeline workflow input additions:**

| Input | Workflow | Type | Default |
|-------|----------|------|---------|
| `auth-mode` | `sar-test-pipeline.yml` | string | `API_KEY` |

---

## Error Handling

### SAM Template

- `AuthMode` uses `AllowedValues` to reject invalid values at CloudFormation validation time, before any resources are created.
- If a deployer passes an unsupported value, CloudFormation returns a parameter validation error with a clear `ConstraintDescription`.

### Validation Pipeline

| Failure scenario | Handling |
|-----------------|----------|
| Deployment fails (either run) | Stack events logged; cleanup attempted; run marked failed; other run continues |
| Health check returns non-200 (either run) | HTTP code and response body logged (credentials masked); cleanup attempted; run marked failed |
| API key retrieval fails (API_KEY run) | Error logged; run marked failed; cleanup attempted |
| SigV4 signing fails (IAM run) | Error logged; run marked failed; cleanup attempted |
| Stack deletion fails (either run) | Warning recorded in summary; pipeline does not fail on cleanup errors |
| S3 bucket still exists after deletion | Warning recorded in summary |
| IAM role lacks `execute-api:Invoke` (IAM run) | Health check returns 403; logged as auth failure; run marked failed |

Cleanup steps always run with `if: always()` regardless of prior step outcomes.

No API key values or IAM credentials are included in summary output. API key values are masked with `::add-mask::` immediately upon retrieval.

---

## Documentation Updates

The following user-facing and maintainer-facing documentation files must be updated as part of this feature.

### README.md

- Add `AuthMode` to the Configuration Reference table: `API_KEY` (default) or `IAM`; SAM parameter only; note that API key is the default and IAM is a supported alternative
- In the Quick Start health check and usage examples, add a note that the `x-api-key` header applies to API key mode (the default); in IAM mode, requests must be SigV4-signed instead
- Update the Deployment for Developers section to mention `AuthMode` as a deployable parameter

### README-SAR.md

- Add `AuthMode` to the Input Parameters table: `API_KEY` (default) or `IAM`; explain that API key mode creates an API key and usage plan, IAM mode requires SigV4-signed requests and does not create an API key
- Update the Security section so it reflects both supported auth modes, with API key as the default; remove the implication that API key is the only mechanism
- Note that in IAM mode the `MockNestApiKey` CloudFormation output is not present

### docs/USAGE.md

- In the Prerequisites and Setup sections, add a short note that the guide uses API key mode (the default); in IAM mode, replace the `x-api-key` header with SigV4 request signing
- In the Health Checks section, add a note that the `x-api-key` header is required in API key mode; in IAM mode, the same endpoints are called with SigV4-signed requests instead

### docs/SAR_PUBLISHING.md

- Update the release validation description to reflect that every SAR release is now validated in both auth modes (API key and IAM) as independent runs
- Document that IAM-mode validation requires the GitHub OIDC role (`GitHubOIDCAdmin`) to have `execute-api:Invoke` permission on the deployed API
- Note that both validation runs must pass before a release is considered successful

This feature consists entirely of IaC (SAM template changes) and CI/CD pipeline changes. Property-based testing is not applicable here — there are no pure functions with wide input spaces to test. The appropriate strategies are:

**Template snapshot / structural tests (SMOKE)**

Verify the SAM template structure for both `AuthMode` values using `sam validate` and CloudFormation template inspection:

- `AuthMode` parameter exists with `AllowedValues: [API_KEY, IAM]` and `Default: API_KEY`
- `IsIamMode` condition is defined correctly
- All three event sources (`AdminRoutes`, `MockRoutes`, `AIRoutes`) have conditional auth configuration
- `MockNestApiKey`, `MockNestUsagePlan`, `MockNestUsagePlanKey` resources are absent when `AuthMode=IAM`
- `MockNestApiKey` output is absent when `AuthMode=IAM`
- `sam validate` passes for both parameter values

These checks can be automated as part of the build pipeline using `sam validate --lint` and `cfn-lint`.

**Integration tests (INTEGRATION)**

Executed by the extended SAR validation pipeline on every release:

- API_KEY run: deploy with `AuthMode=API_KEY`, verify `/__admin/health` and `/ai/generation/health` return HTTP 200 with `x-api-key` header
- IAM run: deploy with `AuthMode=IAM`, verify same endpoints return HTTP 200 with SigV4-signed requests
- Both runs: verify cleanup removes the test stack and S3 bucket

**Manual / exploratory**

- Verify that a request without credentials returns HTTP 403 in both modes
- Verify that the SAR deployment UI shows the `AuthMode` parameter with the correct description and default
