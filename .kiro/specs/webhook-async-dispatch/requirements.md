# Requirements Document

## Introduction

This feature is a redesign and extension of MockNest Serverless webhook support, consisting of three interconnected parts:

1. **SQS-based async webhook dispatch** — replaces the current synchronous `WebhookServeEventListener` approach. The existing implementation uses a custom `mocknest-webhook` listener name, breaking WireMock mapping compatibility and adding latency to every matched request. The new approach uses WireMock's built-in `webhook` extension name. When a stub with a `webhook` listener is matched, the runtime uses WireMock's built-in webhook templating to resolve the callback URL, headers, and body against the triggering request, then publishes a fat async event containing the fully rendered callback request to a dedicated SQS queue. A separate `runtime-async` Lambda reads the event and executes the outbound callback directly from the event payload — no mapping lookup, no template resolution needed. The `runtime-async` Lambda is conceptually an extension of the mock runtime family and is the place where future async connectors (e.g. EventBridge) can be plugged in.

2. **S3-backed persistent request journal** — WireMock's in-memory request journal is unreliable for pipeline verification when async webhook callbacks land on a different Lambda invocation. The request journal backing store must be replaced with an S3-backed implementation using WireMock's extensibility mechanism, so the journal is persistent across Lambda invocations. This also enables the Traffic Analysis roadmap feature (Priority 4).

3. **Centralized secret redaction** — sensitive header values are currently visible in runtime logs and request journal output. Redaction must be applied centrally at the single point where requests enter the logging/journal pipeline, covering runtime logs, request journal output, S3-persisted records, and webhook-related logging. Must not affect request matching, auth, or forwarding behavior.

All three Lambda functions (runtime, generation, runtime-async) use the same application jar. This is required because separate jars cause cumulative SAR artifact size problems. Dependency growth must be kept minimal and standard AWS constructs preferred.

A dedicated `MockNestWebhookQueue` SQS queue and a dedicated `MockNestWebhookDLQ` dead-letter queue are added to the SAM template. The existing `MockNestDLQ` is not repurposed. The existing `MockStorage` S3 bucket is used for the persistent request journal. The existing `WebhookHttpClientInterface`, `WebhookRequest`, and `WebhookResult` models are reused by the runtime-async Lambda.

## Glossary

- **MockNest_Runtime**: The MockNest Serverless WireMock-compatible runtime running on AWS Lambda.
- **RuntimeAsync_Lambda**: A separate Lambda function in the runtime family that reads fat async events from SQS and executes the configured async action (e.g. outbound webhook call) directly from the event payload. Does not load mappings from storage or resolve templates — all rendering is done by the runtime before publishing. Uses the same application jar as the runtime and generation Lambdas. Runs under its own dedicated IAM execution role (`MockNestRuntimeAsyncRole`). This is the extension point for future async connectors.
- **AsyncEvent**: The fat JSON message published to SQS by the runtime when a stub with a `webhook` listener is matched. Contains `actionType` (e.g. `"webhook"`), the fully rendered callback `url`, `method`, `headers`, and `body`, plus non-secret auth metadata (e.g. `auth.type: "aws_iam"` and optional region/service overrides). Does not contain credentials or secret values.
- **WebhookTransformer**: A WireMock extension registered in the runtime Lambda that intercepts matched stubs with a `webhook` `serveEventListener`, publishes an `AsyncEvent` to `MockNestWebhookQueue`, and suppresses the built-in WireMock async HTTP dispatch.
- **S3_Journal**: The S3-backed persistent request journal implemented via WireMock's extensibility/state mechanism. Replaces the default in-memory journal as the backing store. Stores request records in the `MockStorage` bucket under a configurable key prefix (default `requests/`).
- **Sensitive_Header**: An HTTP request header whose value must not appear in logs, admin-visible request records, S3-persisted records, or any other observable output (e.g. `x-api-key`, `authorization`, `proxy-authorization`, `x-amz-security-token`).
- **Redaction_Filter**: The centralized component that applies `[REDACTED]` substitution to `Sensitive_Header` values before any write to logs, journal, or S3. Applied at the single entry point into the logging/journal pipeline.
- **Webhook**: An outbound HTTP call triggered after a mock response is served, configured using WireMock's standard `serveEventListeners` format with `name: "webhook"`.
- **MockNestWebhookQueue**: A new dedicated SQS queue added to the SAM template for async events.
- **MockNestWebhookDLQ**: A new dedicated SQS dead-letter queue for `MockNestWebhookQueue`, capturing messages that fail after the configured maximum receive count.
- **MockNestRuntimeAsyncRole**: A dedicated IAM execution role for the RuntimeAsync_Lambda, separate from `MockNestLambdaRole`. Scoped to only the permissions the runtime-async Lambda needs.
- **MockNestDLQ**: The existing SQS dead-letter queue for failed Lambda invocations. Not repurposed for webhook dispatch.
- **MockStorage**: The existing S3 bucket in the SAM template used for mappings, response payloads, and the `S3_Journal`.
- **Post-Deploy_Test**: A test executed against the deployed MockNest instance in AWS after deployment, as part of the CI/CD pipeline.
- **OIDC_Role**: The GitHub Actions OIDC IAM role used by the CI/CD pipeline to authenticate with AWS.

---

## Requirements

### Requirement 1: Standard WireMock Webhook Compatibility

**User Story:** As a developer, I want to configure webhooks using the standard WireMock `webhook` listener name, so that my mock mappings are fully compatible with WireMock tooling and documentation without MockNest-specific changes.

#### Acceptance Criteria

1. THE MockNest_Runtime SHALL accept webhook configurations using `serveEventListeners` with `name: "webhook"` in WireMock mapping definitions.
2. THE MockNest_Runtime SHALL NOT require users to use any MockNest-specific listener name (such as `mocknest-webhook`) in their mapping definitions.
3. WHEN a stub mapping contains a `serveEventListeners` entry with `name: "webhook"`, THE MockNest_Runtime SHALL intercept the webhook dispatch, use WireMock's built-in webhook templating to resolve the callback URL, headers, and body against the triggering request, and publish a fat `AsyncEvent` containing the fully rendered callback request to `MockNestWebhookQueue` instead of executing the HTTP call synchronously.
4. THE interception mechanism SHALL ensure that the webhook dispatch work is captured and enqueued exactly once. The built-in WireMock outbound webhook HTTP execution SHALL NOT also execute — duplicate callback delivery is not permitted. The chosen extension point (e.g. `WebhookTransformer` or `ServeEventListener`) must suppress or replace the built-in async dispatch, not run alongside it.
5. THE MockNest_Runtime SHALL remove the existing `WebhookServeEventListener` and its `mocknest-webhook` listener name from the runtime Lambda.

---

### Requirement 2: SQS-Based Async Webhook Dispatch

**User Story:** As a developer, I want webhook calls to be dispatched asynchronously via SQS, so that mock response latency is not increased by outbound HTTP calls and the dispatch is decoupled from the Lambda execution context.

#### Acceptance Criteria

1. WHEN a mock with a `webhook` `serveEventListener` is matched, THE MockNest_Runtime SHALL use WireMock's built-in webhook templating model to resolve all template expressions in the webhook URL, headers, and body (e.g. `{{originalRequest.body}}`, `{{jsonPath originalRequest.body '$.field'}}`) against the triggering request, then publish a fat `AsyncEvent` to `MockNestWebhookQueue` before the response is returned to the caller.
2. THE `AsyncEvent` SHALL contain: `actionType: "webhook"`, the fully rendered `url`, `method`, `headers`, and `body`. It SHALL also contain non-secret auth metadata required by the RuntimeAsync_Lambda (e.g. `auth.type: "aws_iam"` and optional region/service overrides). It SHALL NOT contain credentials, secret values, or raw unrendered template expressions.
3. THE MockNest_Runtime SHALL NOT make the outbound webhook HTTP call synchronously in the request/response path.
4. THE RuntimeAsync_Lambda SHALL read `AsyncEvent` records from `MockNestWebhookQueue` and execute the outbound HTTP call directly from the event payload. It SHALL NOT need to load the mapping from storage or resolve templates to execute the callback.
5. THE RuntimeAsync_Lambda SHALL use the existing `WebhookHttpClientInterface`, `WebhookRequest`, and `WebhookResult` models from the application layer.
6. THE RuntimeAsync_Lambda SHALL NOT load the WireMock server, serve mock requests, or load mappings from storage.
7. IF the outbound webhook HTTP call fails (non-2xx response or network error), THEN THE RuntimeAsync_Lambda SHALL log the failure including the target URL and HTTP status code, and SHALL allow the SQS visibility timeout and `MockNestWebhookDLQ` to handle retries and exhausted delivery.
8. THE RuntimeAsync_Lambda SHALL enforce a configurable timeout for outbound webhook HTTP calls so that a slow or unresponsive target does not exhaust the Lambda execution budget.
9. THE RuntimeAsync_Lambda is the designated extension point for future async connectors (e.g. EventBridge). Future connector types SHALL be added here without modifying the runtime Lambda.

---

### Requirement 3: Webhook Auth in the RuntimeAsync Lambda

**User Story:** As a developer, I want the runtime-async Lambda to support AWS IAM SigV4-signed webhook calls, so that MockNest can call back to IAM-protected endpoints in the same or another AWS account.

#### Acceptance Criteria

1. THE RuntimeAsync_Lambda SHALL support two auth modes for outbound webhook calls:
   - **No auth** (default): outbound request uses only the static headers provided in the mapping definition's `headers` field.
   - **AWS IAM (SigV4)**: outbound request is signed using AWS Signature Version 4, using the RuntimeAsync_Lambda's own execution role credentials.
2. WHEN a webhook definition specifies `auth.type: "aws_iam"`, THE RuntimeAsync_Lambda SHALL sign the outbound HTTP request using SigV4 with the region and service derived from the target URL or from explicit configuration in the mapping.
3. WHEN the callback target is an IAM-protected API Gateway endpoint (e.g. the MockNest IAM-mode API), the RuntimeAsync_Lambda execution role MUST be granted `execute-api:Invoke` permission on the target API Gateway route(s). This permission SHALL be grantable from the target stack (e.g. via a resource-based policy or IAM policy in the target stack), supporting same-account and cross-stack scenarios.
4. THE RuntimeAsync_Lambda SHALL NOT log any credential or signing material at any log level.
5. Static headers provided in the WireMock mapping `headers` field SHALL be forwarded as-is by the RuntimeAsync_Lambda without modification.
6. THE following auth modes are NOT implemented in this increment and SHALL be documented as future roadmap items:
   - `original_request_header` — forwarding a header value from the triggering request
   - `secret_ref` — a value resolved at dispatch time from AWS Secrets Manager
   - `env_var` — a value read from a Lambda environment variable at dispatch time
7. THE RuntimeAsync_Lambda execution role SHALL be a dedicated IAM role (`MockNestRuntimeAsyncRole`) separate from `MockNestLambdaRole`. It SHALL have only the permissions required: SQS receive/delete on `MockNestWebhookQueue`, CloudWatch Logs write, and `execute-api:Invoke` on IAM-protected callback targets (granted directly or via target-stack policy). It SHALL NOT have S3 read permissions — the runtime-async Lambda does not load mappings from storage.

---

### Requirement 4: S3-Backed Persistent Request Journal

**User Story:** As a developer, I want the WireMock request journal to be backed by S3 instead of in-memory storage, so that request history is persistent across Lambda invocations and can be used for webhook callback verification and future traffic analysis.

#### Acceptance Criteria

1. THE MockNest_Runtime SHALL replace the default in-memory WireMock request journal with an S3-backed implementation using WireMock's extensibility/state mechanism.
2. THE S3_Journal SHALL be the persistent source of truth for all inbound request records, including webhook callback requests.
3. WHEN a request is served by the MockNest_Runtime, THE S3_Journal SHALL store a redacted JSON record of the request in the `MockStorage` S3 bucket under a configurable key prefix (default `requests/`).
4. THE S3_Journal SHALL apply `Sensitive_Header` redaction to all header values before writing to S3 — raw sensitive header values SHALL NOT be written to S3.
5. THE S3_Journal SHALL store records containing at minimum: request ID, timestamp, HTTP method, URL path, query parameters, request headers (redacted), and request body.
6. THE S3_Journal SHALL support retrieval of stored request records to serve `/__admin/requests` responses, enabling cross-invocation request verification.
7. IF an S3 read or write fails, THE S3_Journal SHALL log the failure at `WARN` level and SHALL NOT affect the response returned to the caller.
8. THE MockNest_Runtime SHALL expose a configurable S3 key prefix for request journal records via an environment variable (`MOCKNEST_REQUEST_JOURNAL_PREFIX`), defaulting to `requests/`.
9. THE S3_Journal implementation SHALL enable future traffic analysis features (Priority 4 roadmap), including unmatched request tracking via `/__admin/requests/unmatched`.

---

### Requirement 5: Centralized Sensitive Header Redaction

**User Story:** As a security-conscious operator, I want sensitive header values to be redacted centrally at the single point where requests enter the logging and journal pipeline, so that secrets are never exposed in logs, admin API responses, or S3-persisted records regardless of which code path processes the request.

#### Acceptance Criteria

1. THE Redaction_Filter SHALL be applied at the single entry point into the logging/journal pipeline, covering all of the following outputs:
   - Runtime Lambda logs (e.g. "Runtime Lambda request: ... headers ...")
   - WireMock request journal output returned by `/__admin/requests`
   - S3-persisted request records written by the `S3_Journal`
   - Any webhook-related request logging in the `WebhookTransformer`
2. THE Redaction_Filter SHALL replace the value of each `Sensitive_Header` with the string `[REDACTED]` and SHALL preserve the header name.
3. THE Redaction_Filter SHALL NOT modify the actual inbound request used for stub matching, auth verification, or request forwarding — only the copy written to logs, journal, or S3 SHALL be redacted.
4. THE list of `Sensitive_Header` names SHALL be configurable via the `MOCKNEST_SENSITIVE_HEADERS` environment variable (comma-separated, case-insensitive), with a default set that includes at minimum: `x-api-key`, `authorization`, `proxy-authorization`, `x-amz-security-token`.
5. THE Redaction_Filter SHALL apply redaction consistently to both inbound mock requests and inbound webhook callback requests.
6. THE MockNest_Runtime SHALL NOT log the unredacted value of any configured `Sensitive_Header` at any log level.

---

### Requirement 6: Pipeline Test Verification via S3 Journal

**User Story:** As a CI/CD engineer, I want the post-deploy pipeline test to verify webhook callback delivery by polling the S3-backed request journal, so that verification is reliable across Lambda invocations and does not depend on in-memory request journal state.

#### Acceptance Criteria

1. THE pipeline tests SHALL run against two separate deployments with different auth modes:
   - **Deployment A (`API_KEY` mode)**: runs the 3 existing test suites (REST, SOAP, GraphQL or equivalent). Does NOT run the webhook callback integration test.
   - **Deployment B (`IAM` mode)**: runs the webhook/callback integration test. The runtime Lambda resolves templating and enqueues a fat `AsyncEvent` to `MockNestWebhookQueue`; the RuntimeAsync_Lambda executes the outbound callback directly from the event payload using `aws_iam` SigV4 signing; verification polls the S3-backed request journal.
2. THE Post-Deploy_Test for Deployment B SHALL register a trigger mock with a `serveEventListeners` webhook (using `name: "webhook"`) targeting a callback mock endpoint on the same deployed MockNest instance, with `auth.type: "aws_iam"` configured.
3. THE Post-Deploy_Test for Deployment B SHALL invoke the trigger endpoint using AWS IAM authentication (SigV4 signing) with the GitHub Actions OIDC role credentials. The OIDC role must have `execute-api:Invoke` permission on the Deployment B API Gateway.
4. WHEN the trigger mock is called during the Post-Deploy_Test, THE Post-Deploy_Test SHALL verify webhook callback delivery by polling the `MockStorage` S3 bucket (via the `S3_Journal` key prefix) for a request record matching the callback endpoint path.
5. THE Post-Deploy_Test SHALL poll S3 for the callback request record with a configurable timeout (default 30 seconds) and retry interval (default 2 seconds), to account for SQS delivery latency and Dispatcher_Lambda cold start.
6. IF the S3 polling does not find the expected callback request record within the configured timeout, THEN THE Post-Deploy_Test SHALL fail with a descriptive error message identifying the expected path and elapsed time.
7. THE Post-Deploy_Test for Deployment B SHALL NOT use or assert on `x-api-key` header values. Redaction assertions SHALL target IAM-relevant sensitive headers present in the callback request record, such as `authorization` and `x-amz-security-token`.
8. THE Post-Deploy_Test SHALL assert that any IAM-sensitive header values in the S3-persisted callback request record are `[REDACTED]`.
9. THE Post-Deploy_Test SHALL be integrated into `scripts/post-deploy-test.sh` as a test suite option (`webhook`), following the same structure as existing test suites.
10. THE Post-Deploy_Test SHALL be invocable from the existing `workflow-integration-test.yml` GitHub Actions workflow by passing `test-suite: webhook`.

---

### Requirement 7: RuntimeAsync Lambda Infrastructure

**User Story:** As a platform operator, I want the runtime-async Lambda to be a separate function using the same application jar, so that the runtime Lambda is not burdened with outbound HTTP calls and SAR artifact size is not increased.

#### Acceptance Criteria

1. THE SAM template SHALL define a new `MockNestRuntimeAsyncFunction` Lambda function (and a corresponding IAM-mode variant) that is triggered by SQS messages from `MockNestWebhookQueue`.
2. THE RuntimeAsync_Lambda SHALL use the same application jar as the runtime and generation Lambda functions. No separate jar SHALL be built for the runtime-async Lambda.
3. THE RuntimeAsync_Lambda SHALL have an SQS event source mapping to `MockNestWebhookQueue` with a batch size of 1.
4. THE SAM template SHALL define a dedicated `MockNestWebhookQueue` SQS queue for async events, with `MockNestWebhookDLQ` as its dead-letter queue.
5. THE SAM template SHALL define a dedicated `MockNestWebhookDLQ` SQS dead-letter queue with a configurable maximum receive count (default 3) and message retention period.
6. THE SAM template SHALL define a dedicated `MockNestRuntimeAsyncRole` IAM role for the RuntimeAsync_Lambda. This role SHALL be separate from `MockNestLambdaRole` and SHALL include: `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` on `MockNestWebhookQueue`; `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents` for CloudWatch Logs. It SHALL NOT include S3 permissions — the runtime-async Lambda executes directly from the event payload and does not load mappings from storage. For IAM-protected callback targets, `MockNestRuntimeAsyncRole` must have `execute-api:Invoke` on the target API Gateway route(s); this permission may be granted directly in the role policy or via a resource-based policy grant from the target stack.
7. THE `MockNestLambdaRole` (used by the runtime Lambda) SHALL be granted `sqs:SendMessage` permission on `MockNestWebhookQueue` to publish `AsyncEvent` records.
8. THE RuntimeAsync_Lambda SHALL have a configurable timeout via a SAM parameter, defaulting to 30 seconds.
9. THE RuntimeAsync_Lambda SHALL have a configurable memory size via a SAM parameter, defaulting to 256 MB.
10. THE SAM template SHALL add a CloudWatch Log Group for the RuntimeAsync_Lambda with the same `LogRetentionDays` parameter as existing log groups.
11. THE existing `MockNestDLQ` SHALL NOT be repurposed for webhook dispatch messages — it remains the dead-letter queue for failed Lambda invocations only.

---

### Requirement 8: Local Integration Test

**User Story:** As a developer, I want to run a local integration test that validates the full async webhook dispatch flow without deploying to AWS, so that I get fast feedback during development.

#### Acceptance Criteria

1. THE local integration test SHALL use a real `WireMockServer` started on a real port with the webhook interception extension registered.
2. THE local integration test SHALL use a local SQS emulation (e.g. LocalStack or an in-process stub) to capture published `AsyncEvent` records.
3. THE local integration test SHALL invoke the RuntimeAsync_Lambda logic directly (not via AWS) to process the captured `AsyncEvent` and make the outbound HTTP call to the callback mock directly from the event payload — no mapping lookup, no template resolution needed at this stage.
4. THE local integration test SHALL verify that the callback mock received the expected inbound request.
5. THE local integration test SHALL verify that the S3_Journal record for the callback request has any IAM-sensitive header values (e.g. `authorization`, `x-amz-security-token`) set to `[REDACTED]`.
6. THE local integration test SHALL verify that the `Redaction_Filter` is applied to runtime log output — no raw sensitive header values SHALL appear in log output during the test.
7. THE local integration test SHALL be executable with a single Gradle command without requiring AWS credentials.

---

### Requirement 9: Documentation Updates

**User Story:** As a user discovering MockNest Serverless, I want webhook support to be clearly documented with the new async dispatch model and standard `webhook` listener name, so that I can configure it correctly.

#### Acceptance Criteria

1. THE `docs/USAGE.md` file SHALL be updated to document the async webhook dispatch model, including a complete mapping example using `serveEventListeners` with `name: "webhook"`.
2. THE `docs/USAGE.md` file SHALL document the supported auth modes (`none` and `aws_iam`) with examples, and mention `secret_ref` and `env_var` as future roadmap items.
3. THE `docs/USAGE.md` file SHALL document all environment variables used to configure webhook behavior, sensitive header redaction, and S3 request journal persistence, including their defaults and purpose.
4. THE `docs/USAGE.md` file SHALL document the S3-backed request journal feature, including the key prefix format and how to use it for pipeline verification.
5. THE `README.md` file SHALL list async webhook/callback support as a validated current feature.

---

## Risks, Open Questions, and Design Considerations

### Constraint: Single Jar for All Lambda Functions
All three Lambda functions (runtime, generation, runtime-async) must use the same application jar. The runtime-async Lambda entry point must be selectable via the `SPRING_CLOUD_FUNCTION_DEFINITION` environment variable or an equivalent lightweight routing mechanism that does not require Spring context initialization for the runtime-async path.

### Design Consideration: RuntimeAsync Lambda Cold Start
The runtime-async Lambda must be lightweight to avoid the cold start problem seen in the runtime Lambda (which loads Spring + WireMock + S3 mappings). It executes directly from the `AsyncEvent` payload — no WireMock server startup, no S3 mapping loading. The single-jar constraint means the runtime-async Lambda shares the classpath but must not trigger heavy initialization paths.

### Design Consideration: S3 Journal WireMock Extension Point
WireMock's `RequestJournal` interface and related extension points must be used to replace the in-memory journal with an S3-backed implementation. The design phase must identify the correct WireMock 3.x extension point (e.g. `StoreBackedRequestJournal`, custom `RequestJournalStore`, or equivalent) that allows plugging in an S3-backed store without forking WireMock internals.

### Design Consideration: Redaction Centralization
The `ServeEvent` and `LoggedRequest` objects are immutable — redaction must be applied at read time for journal output, and before write for S3 and logs. An `AdminRequestFilterV2` is the correct hook for `/__admin/requests` output redaction. S3 writes must apply redaction before serialization.

### Design Consideration: SQS Message Sensitivity
The `AsyncEvent` contains the fully rendered callback URL, headers, and body. If the webhook mapping includes sensitive values in headers or body, those values will be present in the SQS message. SQS SSE (server-side encryption) is recommended. The `AsyncEvent` does not contain credentials or signing material — auth is resolved by the RuntimeAsync_Lambda at execution time using its own IAM role.

### Risk: SQS Message Delivery Guarantees
SQS standard queues provide at-least-once delivery. The runtime-async Lambda must be idempotent with respect to duplicate `AsyncEvent` deliveries (i.e. duplicate webhook calls to the same callback target). This is acceptable for the v1 use case (test pipeline verification) but should be documented.

### Risk: Pipeline Verification Timing
The post-deploy test polls S3 for the callback request record. The end-to-end latency includes: SQS publish → SQS delivery → RuntimeAsync_Lambda cold start → outbound HTTP call → S3 journal write. A 30-second polling timeout with 2-second intervals should be sufficient for typical scenarios, but cold starts on the runtime-async Lambda may require tuning.
