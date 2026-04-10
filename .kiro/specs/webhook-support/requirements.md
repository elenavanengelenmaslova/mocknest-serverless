# Requirements Document

## Introduction

This feature adds reliable webhook/callback-style behavior to MockNest Serverless. A mock definition can trigger an outbound HTTP call (a webhook) to another endpoint after serving its response. The primary use case for the first increment is calling another mock endpoint within the same deployed MockNest instance, enabling simulation of chained or event-driven service interactions in tests.

WireMock has built-in webhook support in its webhook/callback model. However, asynchronous webhook dispatch may be unreliable in AWS Lambda because the execution environment can be frozen once the runtime has finished processing. This feature defines how MockNest ensures reliable webhook delivery in that runtime model, adds structured outbound authentication for webhook calls, and establishes a validated test strategy for both local and deployed environments.

The design should not unnecessarily constrain future expansion to AI endpoints, Lambda URLs, or other integration targets beyond same-instance callbacks.

## Glossary

- **MockNest Runtime**: The MockNest Serverless WireMock-compatible runtime running on AWS Lambda.
- **Webhook**: An outbound HTTP call triggered by the MockNest Runtime after serving a mock response, using the WireMock-compatible webhook configuration model supported by MockNest.
- **Webhook Auth Config**: The structured authentication configuration for an outbound webhook call, specifying where auth is injected and where the auth value comes from.
- **Auth Type**: The mechanism by which authentication is applied to the outbound webhook (e.g. `header`). Future roadmap: `aws_iam`.
- **Value Source**: Where the auth value is obtained at dispatch time (e.g. `original_request_header`, `static`, `secret_ref`, `env_var`). Only `original_request_header` is implemented in v1.
- **Webhook Template**: A template applied to webhook body, headers, or URL using data from the triggering request.
- **Sensitive Header**: An HTTP request or response header whose value must not appear in logs, admin-visible request records, or any other observable output (e.g. `x-api-key`, `Authorization`).
- **Post-Deploy Test**: A test executed against the deployed MockNest instance in AWS after deployment, as part of the CI/CD pipeline.
- **Local Integration Test**: A test executed locally using a local MockNest or WireMock instance, providing fast feedback without requiring AWS deployment.
- **OIDC Role**: The GitHub Actions OIDC IAM role used by the CI/CD pipeline to authenticate with AWS and retrieve the API key securely.

---

## Functional Requirements

### Requirement 1: Reliable Webhook Execution in Lambda

**User Story:** As a developer, I want webhook calls to complete reliably in the AWS Lambda runtime, so that my mock-triggered callbacks are not silently dropped due to Lambda execution context freezing.

#### Acceptance Criteria

1. WHEN a mock with a configured webhook is matched and a response is served, THE MockNest Runtime SHALL ensure webhook execution completes before the Lambda handler returns its response to the caller.
2. THE MockNest Runtime SHALL support the WireMock-compatible webhook configuration model (`serveEventListeners` format), while preserving compatibility with the legacy `postServeActions` format that WireMock continues to accept.
3. IF the outbound webhook HTTP call fails (non-2xx response or network error), THEN THE MockNest Runtime SHALL log the failure including the target URL and HTTP status code, and SHALL NOT prevent the mock response from being returned to the original caller.
4. THE MockNest Runtime SHALL enforce a configurable timeout for outbound webhook calls so that a slow or unresponsive target does not exhaust the Lambda execution budget.

---

### Requirement 2: Webhook Templating

**User Story:** As a developer, I want to use templates in webhook bodies, headers, and URLs, so that the outbound webhook call can include data from the original triggering request.

#### Acceptance Criteria

1. WHEN a webhook `body` field contains template expressions, THE MockNest Runtime SHALL render the template using data from the triggering HTTP request before sending the outbound call.
2. WHEN a webhook `headers` field contains template expressions, THE MockNest Runtime SHALL render each header value using data from the triggering HTTP request before sending the outbound call.
3. WHEN a webhook `url` field contains template expressions, THE MockNest Runtime SHALL render the URL using data from the triggering HTTP request before sending the outbound call.
4. THE MockNest Runtime SHALL support webhook templates that can access data from the original triggering request, consistent with the WireMock webhook templating model (e.g. `{{originalRequest.body}}`, `{{originalRequest.headers.X-Correlation-Id}}`, `{{jsonPath originalRequest.body '$.field'}}`).
5. IF a template expression in a webhook definition cannot be rendered (e.g. missing field), THEN THE MockNest Runtime SHALL log the rendering failure and SHALL send the webhook with the unrendered expression replaced by an empty string.

---

### Requirement 3: Webhook Authentication Model

**User Story:** As a developer, I want to configure outbound webhook authentication using a structured model that separates where auth is injected from where the auth value comes from, so that the configuration is extensible without breaking changes as new value sources are added.

#### Acceptance Criteria

1. THE MockNest Runtime SHALL support a structured webhook auth configuration with two independent concerns:
   - **auth type** — where authentication is applied to the outbound request (e.g. `header`)
   - **value source** — where the auth value is obtained at dispatch time
2. THE webhook auth configuration SHALL be specified per webhook definition, not globally.
3. THE following auth types SHALL be supported in v1:
   - `header` — injects a named header into the outbound webhook request
4. THE following value sources SHALL be supported in v1:
   - `original_request_header` — copies a named header value from the incoming trigger request
5. THE following value sources SHALL be documented as future roadmap items, not implemented in v1:
   - `static` — a fixed value specified directly in the mapping
   - `secret_ref` — a value resolved at dispatch time from a secure secret backend (e.g. AWS Secrets Manager)
   - `env_var` — a value read from a Lambda environment variable at dispatch time
6. `aws_iam` (SigV4 signing) SHALL be documented as a future auth type roadmap item, not implemented in v1.
7. THE MockNest Runtime SHALL document the supported auth configuration syntax and all v1 value sources.
8. THE MockNest Runtime SHALL NOT log the value of any header injected via the auth config at any log level.

---

### Requirement 4: Sensitive Header Redaction

**User Story:** As a security-conscious operator, I want sensitive header values (such as API keys) to be redacted from WireMock's request journal and admin API responses, so that secrets are not exposed through observable outputs.

#### Acceptance Criteria

1. THE MockNest Runtime SHALL redact the values of configured sensitive header names from all entries stored in WireMock's in-memory request journal.
2. WHEN admin request records are queried, THE MockNest Runtime SHALL return records in which configured sensitive header values are replaced with a redaction marker rather than the actual value.
3. THE MockNest Runtime SHALL support configuration of which header names are treated as sensitive, with a default set that includes common secret-bearing headers such as `x-api-key` and `authorization`.
4. THE MockNest Runtime SHALL apply redaction to both inbound request headers recorded by WireMock and to headers included in webhook-related request records.
5. THE MockNest Runtime SHALL NOT log the unredacted value of any configured sensitive header at any log level.

---

### Requirement 5: Same-Instance Webhook Calls

**User Story:** As a developer, I want a mock to trigger a webhook that calls another mock endpoint in the same MockNest instance, so that I can simulate chained or event-driven service interactions in my tests.

#### Acceptance Criteria

1. WHEN a webhook target URL points to a path served by the same MockNest Runtime instance, THE MockNest Runtime SHALL make the outbound HTTP call to that endpoint using the instance's own resolvable base URL.
2. WHEN the target mock endpoint requires API key authentication, THE MockNest Runtime SHALL support including the required API key header in the outbound webhook request via the configured webhook auth config.
3. WHEN a webhook call to a same-instance mock endpoint is received, THE MockNest Runtime SHALL record the inbound webhook request in the request journal with sensitive header values redacted, enabling verification of webhook delivery.

---

## Non-Functional and Security Requirements

### Requirement 6: Security Constraints

**User Story:** As a security-conscious operator, I want all sensitive values to be handled safely throughout the webhook lifecycle, so that secrets are never inadvertently exposed.

#### Acceptance Criteria

1. THE MockNest Runtime SHALL ensure that sensitive header values are never written to logs, request journals, admin API responses, or any other observable output at any point in the webhook lifecycle.
2. THE MockNest Runtime SHALL apply sensitive header redaction consistently regardless of whether the request was a direct inbound call or an inbound webhook callback.

### Requirement 7: Lambda Runtime Compatibility

**User Story:** As a platform operator, I want webhook execution to be compatible with AWS Lambda's execution model, so that webhooks do not cause silent failures or resource exhaustion.

#### Acceptance Criteria

1. THE MockNest Runtime SHALL complete all webhook HTTP calls within the Lambda execution context, before the handler returns, to prevent silent loss of webhook calls due to context freezing.
2. THE MockNest Runtime SHALL respect the Lambda execution timeout budget when dispatching webhooks, and SHALL NOT allow webhook execution to cause Lambda timeout errors for the original caller.
3. THE MockNest Runtime SHALL handle webhook dispatch failures gracefully, ensuring that a failed webhook does not affect the response returned to the original caller.

---

## Test and Validation Requirements

### Requirement 8: Local Integration Test

**User Story:** As a developer, I want to run a local integration test that validates webhook behavior without deploying to AWS, so that I get fast feedback during development.

#### Acceptance Criteria

1. THE MockNest Runtime SHALL support webhook execution in a local development environment using the same configuration as the deployed Lambda runtime.
2. WHEN running locally, THE MockNest Runtime SHALL resolve relative webhook URLs against the local base URL.
3. THE MockNest Runtime SHALL include at least one local integration test that: registers a trigger mock with a `serveEventListeners` webhook targeting a callback mock on the same instance, calls the trigger mock, and verifies that the callback mock received the expected inbound request.
4. THE local integration test SHALL verify that configured sensitive header values are redacted in admin request records.
5. THE local integration test SHALL be executable with a single Gradle command without requiring AWS credentials.

---

### Requirement 9: Post-Deploy Pipeline Validation

**User Story:** As a CI/CD engineer, I want a post-deploy test that validates webhook behavior end-to-end against the deployed MockNest instance, so that I can confirm webhooks work correctly in the real Lambda runtime.

#### Acceptance Criteria

1. THE Post-Deploy Test SHALL register a trigger mock with a `serveEventListeners` webhook targeting a callback mock endpoint on the same deployed MockNest instance.
2. WHEN the trigger mock is called during the Post-Deploy Test, THE Post-Deploy Test SHALL verify that the callback mock endpoint received the expected inbound webhook request using the most reliable available verification mechanism for the deployed Lambda runtime.
3. THE Post-Deploy Test SHALL retrieve the API key using the existing OIDC Role-based mechanism (`aws apigateway get-api-key`) and SHALL mask it immediately using `echo "::add-mask::$API_KEY"` before any further use. This is a security requirement, not a style preference.
4. THE Post-Deploy Test SHALL call the trigger endpoint with the API key to satisfy MockNest / API Gateway auth, while the outbound callback auth is supplied separately via the webhook auth config.
5. THE Post-Deploy Test SHALL be integrated into the existing `scripts/post-deploy-test.sh` script as a new test suite option (`webhook`), following the same structure as existing test suites.
6. THE Post-Deploy Test SHALL be invocable from the existing `workflow-integration-test.yml` GitHub Actions workflow by passing `test-suite: webhook`.
7. IF the webhook delivery verification step does not confirm the expected webhook request within a configurable timeout, THEN THE Post-Deploy Test SHALL fail with a descriptive error message.

---

## Documentation Requirements

### Requirement 10: User-Facing Documentation

**User Story:** As a user discovering MockNest Serverless, I want webhook support to be clearly documented, so that I can understand how to configure and use it.

#### Acceptance Criteria

1. THE `docs/USAGE.md` file SHALL be updated to include a webhook usage section with at least one complete example showing a mapping with `serveEventListeners`, including a webhook body using template expressions and the `original_request_header` value source.
2. THE `docs/USAGE.md` file SHALL document the v1 auth config model (`type: header`, `source: original_request_header`) with a complete example, and mention `secret_ref`, `static`, `env_var`, and `aws_iam` as future roadmap items.
3. THE `docs/USAGE.md` file SHALL document the environment variables used to configure webhook behavior and sensitive header redaction, and their purpose.
4. THE `docs/USAGE.md` file SHALL explain the Lambda execution context constraint and how MockNest addresses it.
5. THE `README.md` file SHALL list webhook/callback support as a validated current feature, replacing or updating any existing placeholder entry.
6. THE SAR application description (as referenced in `docs/SAR_PUBLISHING.md` and `README-SAR.md`) SHALL mention webhook support as a supported feature.

---

## Risks, Open Questions, and Design Considerations

### Critical Risk: Lambda Execution Context Freezing
WireMock's built-in webhook dispatch is asynchronous (fires after the response is sent via a `ScheduledExecutorService`). In Lambda, the execution context may be frozen before the async HTTP call completes. Requirement 1 mandates that dispatch completes before the handler returns. The implementation uses `ServeEventListener.BEFORE_RESPONSE_SENT` to execute the webhook synchronously. This must be validated by a prototype before full implementation.

### Open Question: Verification Mechanism for Post-Deploy Test
The post-deploy test uses `/__admin/requests` polling to verify webhook delivery. This is reliable when the webhook call completes synchronously before the trigger response is returned (the core design guarantee). However, if the webhook callback is handled by a different Lambda invocation due to concurrency, the callback's journal entry will be in that invocation's in-memory journal. In practice, for same-instance calls with low concurrency (typical in test scenarios), the same Lambda instance handles both. This limitation is documented.

### Design Consideration: Auth Config Extensibility
The auth config uses a two-level structure (`type` + `value.source`) so that new value sources can be added under an existing type without changing the top-level shape, and new auth types (e.g. `aws_iam`) can be added alongside `header` without breaking existing configs. The v1 implementation covers `type: header` with `source: original_request_header` only.

### Design Consideration: Future Value Sources
`secret_ref` (AWS Secrets Manager), `static`, and `env_var` are natural next value sources. They share the same `type: header` injection mechanism and only differ in how the value is resolved. Adding them requires implementing a new `HeaderValueSource` variant and a corresponding resolver — no changes to the auth config shape or the dispatch path.

### Design Consideration: Future Auth Types
`aws_iam` (SigV4 signing) is the natural next auth type for calling Lambda URLs or IAM-protected API Gateway endpoints. It does not inject a header — it signs the entire request. It is not implemented in v1 but the sealed class structure accommodates it as a peer of `Header`.

### Design Consideration: Webhook Timeout Default
The configurable timeout for outbound webhook calls should account for the round-trip latency of same-instance calls through API Gateway (potentially including a Lambda cold start). A default of 10 seconds is a reasonable starting point, with documentation recommending a minimum Lambda timeout of 30 seconds when webhooks are used.

### Design Consideration: Sensitive Header Configuration
The set of sensitive headers is configurable via a comma-separated environment variable, with a default that includes `x-api-key` and `authorization`. The exact variable name and format are design decisions documented in `USAGE.md`.
