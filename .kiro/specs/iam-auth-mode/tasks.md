# Implementation Plan: IAM Auth Mode

## Overview

Pure IaC and pipeline changes — no Lambda application code changes. Tasks are organized per user story so each can be delivered and validated independently. Testing means SAM structural validation (`sam validate --lint`, `cfn-lint`) and integration validation via the extended SAR pipeline.

Property-based testing is not applicable (no pure functions over wide input spaces). The SAR pipeline integration tests serve as the integration validation layer.

---

## Tasks

- [ ] 1. Add `AuthMode` parameter and conditions to SAM template (Req 1, Req 4)
  - In `deployment/aws/sam/template.yaml`, add the `AuthMode` parameter under `Parameters`:
    - `Type: String`, `Default: 'API_KEY'`, `AllowedValues: [API_KEY, IAM]`
    - Description explaining both values and their behavior (satisfies Req 4.1)
  - Add `Conditions` block with two named conditions:
    - `IsIamMode: !Equals [!Ref AuthMode, 'IAM']` — used with `!If` on event source auth
    - `IsApiKeyMode: !Not [IsIamMode]` — used as `Condition:` on resources and outputs (CloudFormation requires a named condition here; inline `!Not` is not valid)
  - _Requirements: 1.1, 1.2, 4.1, 4.2_

  - [ ]* 1.1 Validate template structure after parameter and condition addition
    - Run `sam validate --lint` against the updated template
    - Run `cfn-lint deployment/aws/sam/template.yaml`
    - Confirm `AuthMode` appears in `AllowedValues` with exactly `API_KEY` and `IAM`
    - Confirm `IsIamMode` condition expression is `!Equals [!Ref AuthMode, 'IAM']`
    - Confirm `IsApiKeyMode` condition expression is `!Not [IsIamMode]`
    - Confirm `Default` is `API_KEY`
    - _Requirements: 1.1, 1.2_

- [ ] 2. Apply conditional auth configuration to all three Lambda event sources (Req 1, Req 2, Req 3)
  - In `deployment/aws/sam/template.yaml`, update the `Auth` block on each event source:
    - `AdminRoutes` (MockNestRuntimeFunction)
    - `MockRoutes` (MockNestRuntimeFunction)
    - `AIRoutes` (MockNestGenerationFunction)
  - Each `Auth` block becomes:
    ```yaml
    Auth:
      ApiKeyRequired: !If [IsIamMode, false, true]
      Authorizer: !If [IsIamMode, AWS_IAM, NONE]
    ```
  - When `AuthMode=API_KEY`: `ApiKeyRequired: true`, `Authorizer: NONE` (existing behavior preserved)
  - When `AuthMode=IAM`: `ApiKeyRequired: false`, `Authorizer: AWS_IAM`
  - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [ ]* 2.1 Validate conditional auth on all three event sources
    - Run `sam validate --lint` and `cfn-lint`
    - Confirm all three event sources (`AdminRoutes`, `MockRoutes`, `AIRoutes`) have the `!If [IsIamMode, ...]` pattern on both `ApiKeyRequired` and `Authorizer`
    - _Requirements: 1.3, 1.4, 1.5_

- [ ] 3. Make API key resources conditional on `API_KEY` mode (Req 2, Req 3)
  - In `deployment/aws/sam/template.yaml`, add `Condition: IsApiKeyMode` to:
    - `MockNestApiKey` resource
    - `MockNestUsagePlan` resource
    - `MockNestUsagePlanKey` resource
  - Make the `MockNestApiKey` CloudFormation output conditional:
    ```yaml
    MockNestApiKey:
      Condition: IsApiKeyMode
      Description: "API key ID (only present when AuthMode=API_KEY)"
      Value: !GetAtt MockNestApiKey.APIKeyId
      Export:
        Name: !Sub "${AWS::StackName}-api-key"
    ```
  - Note: `Condition` on a resource or output must be a named condition — `IsApiKeyMode` is used here, not an inline `!Not` expression
  - _Requirements: 2.4, 2.5, 3.4, 4.5, 4.6_

  - [ ]* 3.1 Validate conditional resource exclusion
    - Run `sam validate --lint` and `cfn-lint`
    - Confirm `MockNestApiKey`, `MockNestUsagePlan`, `MockNestUsagePlanKey` resources each have `Condition: IsApiKeyMode`
    - Confirm `MockNestApiKey` output has `Condition: IsApiKeyMode`
    - _Requirements: 2.4, 3.4, 4.5, 4.6_

- [ ] 4. Checkpoint — SAM template complete
  - Ensure `sam validate --lint` and `cfn-lint` both pass with zero errors
  - Confirm the template compiles cleanly before proceeding to pipeline changes
  - Ask the user if any questions arise.

- [ ] 5. Add `auth-mode` input to `sar-test-pipeline.yml` and ensure run isolation (Req 5, Req 7)
  - In `.github/workflows/sar-test-pipeline.yml`, add `auth-mode` input to both `workflow_call` and `workflow_dispatch` trigger blocks:
    ```yaml
    auth-mode:
      required: false
      type: string
      default: 'API_KEY'
      description: 'Authentication mode to validate: API_KEY or IAM'
    ```
  - Update the stack name in the deploy step to incorporate both the run ID and the auth mode so that concurrent runs (one per mode) do not conflict with each other
  - Pass `AuthMode` parameter override in the SAR deploy step:
    ```bash
    --parameter-overrides \
      Name=DeploymentName,Value=sartest${{ github.run_id }} \
      Name=AuthMode,Value=${{ inputs.auth-mode }}
    ```
  - _Requirements: 5.1, 5.2, 5.3, 7.4_

  - [ ]* 5.1 Validate pipeline YAML syntax
    - Confirm the workflow file parses without errors (GitHub Actions YAML lint)
    - Confirm `auth-mode` input appears in both `workflow_call` and `workflow_dispatch` sections
    - Confirm stack name incorporates the auth mode to prevent conflicts between concurrent runs
    - _Requirements: 5.1, 7.4_

- [ ] 6. Update stack output retrieval and API key step to be auth-mode-aware (Req 5)
  - In `sar-test-pipeline.yml`, update the "Retrieve and validate stack outputs" step:
    - `MockNestApiKey` output retrieval is conditional on `inputs.auth-mode == 'API_KEY'`
    - In `IAM` mode, skip API key ID retrieval entirely; do not fail if the output is absent
    - `MockNestApiUrl` and `MockStorageBucket` retrieval is unconditional
  - Update the "Retrieve API key value" step:
    - Wrap the entire step with `if: inputs.auth-mode == 'API_KEY'`
    - In `IAM` mode this step is skipped; `API_KEY_VALUE` is never set
  - _Requirements: 5.5, 5.6, 4.5, 4.6_

  - [ ]* 6.1 Validate conditional output retrieval logic
    - Confirm the step condition `if: inputs.auth-mode == 'API_KEY'` is present on the API key retrieval step
    - Confirm the outputs step does not fail when `MockNestApiKey` output is absent (IAM mode)
    - _Requirements: 4.5, 4.6, 5.5, 5.6_

- [ ] 7. Implement auth-mode-specific health check authentication (Req 5)
  - In `sar-test-pipeline.yml`, update the "Execute health checks" step to branch on `inputs.auth-mode`:
  - **API_KEY path** (existing behavior, unchanged):
    ```bash
    curl -s -w "%{http_code}" -o /tmp/response.json \
      --max-time 30 \
      -H "x-api-key: $API_KEY_VALUE" \
      "${API_URL}${endpoint_path}"
    ```
  - **IAM path** (new — SigV4 signed via curl `--aws-sigv4`):
    ```bash
    curl -s -w "%{http_code}" -o /tmp/response.json \
      --max-time 30 \
      --aws-sigv4 "aws:amz:${AWS_REGION}:execute-api" \
      --user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}" \
      -H "x-amz-security-token: ${AWS_SESSION_TOKEN}" \
      "${API_URL}${endpoint_path}"
    ```
  - Both paths check `/__admin/health` and `/ai/generation/health` (Req 5.4)
  - Credentials are never logged; `API_KEY_VALUE` is already masked; IAM credentials come from the OIDC session environment
  - _Requirements: 5.4, 5.5, 5.6, 6.4_

  - [ ]* 7.1 Validate health check branching logic
    - Confirm the `API_KEY` branch uses `x-api-key` header
    - Confirm the `IAM` branch uses `--aws-sigv4` with `execute-api` service
    - Confirm both branches check exactly `/__admin/health` and `/ai/generation/health`
    - Confirm no credential values are echoed or included in summary output
    - _Requirements: 5.4, 5.5, 5.6, 6.4_

- [ ] 8. Update per-run summary report to include auth mode and failure details (Req 6)
  - In `sar-test-pipeline.yml`, update the "Generate pipeline summary report" step:
    - Add `**Auth Mode:** ${{ inputs.auth-mode }}` to the report header
    - When a health check fails, include the HTTP status code and truncated response body (no credentials)
    - When cleanup fails, record a cleanup warning section in the summary
    - Ensure no API key values or IAM credential strings appear in any summary output
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [ ]* 8.1 Validate summary report content
    - Confirm `Auth Mode` field is present in the summary output
    - Confirm failure messages include HTTP status code
    - Confirm cleanup warnings are surfaced in the summary
    - Confirm no secrets or credential values are present in summary text
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [ ] 9. Checkpoint — `sar-test-pipeline.yml` complete
  - Ensure the reusable workflow file is valid YAML and all new inputs/steps are consistent
  - Ask the user if any questions arise.

- [ ] 10. Split `sar-publish.yml` into two independent validation runs and combined summary (Req 5, Req 6, Req 7)
  - In `.github/workflows/sar-publish.yml`, replace the single `test` job with two independent jobs:
    ```yaml
    test-api-key:
      needs: publish
      uses: ./.github/workflows/sar-test-pipeline.yml
      with:
        aws-region: ${{ inputs.aws-region || 'eu-west-1' }}
        version: ${{ inputs.version || github.event.release.tag_name }}
        auth-mode: 'API_KEY'
      secrets:
        AWS_ACCOUNT_ID: ${{ secrets.AWS_ACCOUNT_ID }}

    test-iam:
      needs: publish
      uses: ./.github/workflows/sar-test-pipeline.yml
      with:
        aws-region: ${{ inputs.aws-region || 'eu-west-1' }}
        version: ${{ inputs.version || github.event.release.tag_name }}
        auth-mode: 'IAM'
      secrets:
        AWS_ACCOUNT_ID: ${{ secrets.AWS_ACCOUNT_ID }}
    ```
  - `test-api-key` and `test-iam` have no `needs` dependency on each other — they run independently so a failure in one does not block the other (Req 5.8)
  - Update the `summary` job:
    - `needs: [publish, test-api-key, test-iam]`
    - Report result of both runs individually (Req 6.3)
    - Mark overall release as failed if either run failed (Req 5.7)
  - _Requirements: 5.1, 5.7, 5.8, 6.3, 7.1, 7.4_

  - [ ]* 10.1 Validate `sar-publish.yml` structure
    - Confirm `test-api-key` and `test-iam` jobs both exist with `needs: publish`
    - Confirm neither `test-api-key` nor `test-iam` lists the other in `needs`
    - Confirm `summary` job has `needs: [publish, test-api-key, test-iam]` and `if: always()`
    - Confirm summary marks overall failure if either run failed
    - _Requirements: 5.7, 5.8, 6.3_

- [ ] 11. Verify `GitHubOIDCAdmin` IAM role has `execute-api:Invoke` permission (Req 3, Req 5)
  - In `deployment/aws/shared/github-oidc-role.yaml`, check whether the `GitHubOIDCAdmin` role policy already includes `execute-api:Invoke`
  - If absent, add a statement granting `execute-api:Invoke` on `arn:aws:execute-api:*:*:*` to the role
  - This permission is required for the IAM validation run's SigV4-signed health checks to succeed (Req 3.3, Req 5.6)
  - _Requirements: 3.2, 3.3, 5.6_

  - [ ]* 11.1 Validate OIDC role template
    - Run `cfn-lint deployment/aws/shared/github-oidc-role.yaml`
    - Confirm `execute-api:Invoke` is present in the role's policy statements
    - _Requirements: 3.2, 3.3_

- [ ] 12. Final checkpoint — full validation
  - Run `sam validate --lint` on `deployment/aws/sam/template.yaml` — must pass with zero errors
  - Run `cfn-lint deployment/aws/sam/template.yaml` — must pass with zero errors or only acceptable warnings
  - Run `cfn-lint deployment/aws/shared/github-oidc-role.yaml` — must pass
  - Confirm all GitHub Actions YAML files are syntactically valid
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Update README.md
  - Add `AuthMode` to the Configuration Reference table: `API_KEY` (default) or `IAM`; SAM parameter only; note that API key is the default and IAM is a supported alternative
  - In the Quick Start health check and usage examples (Step 3 and Step 4), add a short note that the `x-api-key` header applies to API key mode (the default); in IAM mode, requests must be SigV4-signed instead
  - In the Deployment for Developers section, add `AuthMode` as an example custom parameter
  - _Requirements: 1, 2, 3_

- [ ] 14. Update README-SAR.md
  - Add `AuthMode` to the Input Parameters table: `API_KEY` (default) or `IAM`; explain that API key mode creates an API key and usage plan, IAM mode requires SigV4-signed requests and does not create an API key
  - Update the Security section so it reflects both supported auth modes with API key as the default; remove the statement that implies API key is the only mechanism
  - Add a note that in IAM mode the `MockNestApiKey` CloudFormation output is not present
  - _Requirements: 1, 3, 4_

- [ ] 15. Update docs/USAGE.md
  - In the Prerequisites section, add a short note that the guide uses API key mode (the default); in IAM mode, replace the `x-api-key` header with SigV4 request signing
  - In the Health Checks section (Admin Health Check and AI Generation Health Check), add a note that the `x-api-key` header is required in API key mode; in IAM mode, the same endpoints are called with SigV4-signed requests instead
  - _Requirements: 2, 3_

- [ ] 16. Update docs/SAR_PUBLISHING.md
  - Update the release validation description to reflect that every SAR release is now validated in both auth modes (API key and IAM) as independent runs, and both must pass
  - Document that IAM-mode validation requires the GitHub OIDC role (`GitHubOIDCAdmin`) to have `execute-api:Invoke` permission on the deployed API
  - _Requirements: 5, 6, 7_

## Notes

- Tasks marked with `*` are optional structural/lint validation sub-tasks; they are recommended but can be skipped for speed
- Each top-level task maps to one or more user stories and can be delivered independently
- The SAR pipeline integration tests (tasks 5–10) serve as the integration validation layer for Req 5–7
- No Lambda application code changes are required; all changes are in `deployment/aws/sam/template.yaml`, `.github/workflows/sar-test-pipeline.yml`, `.github/workflows/sar-publish.yml`, and optionally `deployment/aws/shared/github-oidc-role.yaml`
- Two named SAM conditions are needed: `IsIamMode` (for `!If` on event sources) and `IsApiKeyMode` (for `Condition:` on resources and outputs)
- Documentation updates (tasks 13–16) cover: `README.md`, `README-SAR.md`, `docs/USAGE.md`, `docs/SAR_PUBLISHING.md`
