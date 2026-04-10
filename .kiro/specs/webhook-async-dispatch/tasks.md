# Implementation Plan: Webhook Async Dispatch

## Overview

Implement SQS-based async webhook dispatch, S3-backed persistent request journal, and centralized
sensitive header redaction. All three Lambda functions share the same jar; the runtime-async Lambda
is selected via `SPRING_CLOUD_FUNCTION_DEFINITION: "runtimeAsyncRouter"`.

The chosen interception approach is a `WebhookTransformer` (implements `com.github.tomakehurst.wiremock.extension.WebhookTransformer`) registered under the built-in name `"webhook"`. This is a different extension type from `ServeEventListener` and should not be overwritten by the built-in `Webhooks` extension. Task 3.2 includes a mandatory integration test that verifies the extension fires correctly and that no duplicate delivery occurs. If the extension is not invoked, the test will surface the registration issue so it can be fixed (e.g. rename to `mocknest-webhook` if needed).

## Tasks

- [x] 1. Update models and config in the application layer
  - [x] 1.1 Update `WebhookModels.kt`: replace `Header`/`HeaderValueSource` with `AwsIam` variant in `WebhookAuthConfig`; add `AsyncEvent` data class
    - Remove `WebhookAuthConfig.Header` and `HeaderValueSource` sealed classes
    - Add `data class AwsIam(val region: String? = null, val service: String? = null) : WebhookAuthConfig()`
    - Add `@Serializable data class AsyncEvent(val actionType: String, val url: String, val method: String, val headers: Map<String, String>, val body: String?, val auth: AsyncEventAuth)`
    - Add `@Serializable data class AsyncEventAuth(val type: String, val region: String? = null, val service: String? = null)`
    - Keep `WebhookRequest`, `WebhookResult`, `WebhookAuthConfig.None`
    - _Requirements: 2.2, 3.1_

  - [x] 1.2 Update `WebhookConfig.kt`: add `asyncTimeoutMs` and `requestJournalPrefix` fields
    - Add `MOCKNEST_WEBHOOK_ASYNC_TIMEOUT_MS` env var (default `30_000L`) → `asyncTimeoutMs`
    - Add `MOCKNEST_REQUEST_JOURNAL_PREFIX` env var (default `"requests/"`) → `requestJournalPrefix`
    - Extend default sensitive headers to include `proxy-authorization` and `x-amz-security-token`
    - _Requirements: 2.8, 4.8, 5.4_

  - [x] 1.3 Write unit tests for updated `WebhookConfig` and `WebhookModels`
    - Given env vars set When `fromEnv()` called Then new fields are populated correctly
    - Given no env vars When `fromEnv()` called Then defaults are applied including new sensitive headers
    - Given `AsyncEvent` When serialized/deserialized Then round-trips correctly
    - _Requirements: 2.8, 4.8, 5.4_

- [ ] 2. Create `SqsPublisherInterface` in the application layer
  - [x] 2.1 Create `SqsPublisherInterface.kt` in `application/runtime/extensions`
    - Define `interface SqsPublisherInterface { suspend fun publish(queueUrl: String, messageBody: String) }`
    - _Requirements: 2.1_

- [x] 3. Implement `WebhookAsyncEventPublisher` — WireMock extension that intercepts `webhook` dispatch
  - [x] 3.1 Create `WebhookAsyncEventPublisher.kt` in `application/runtime/extensions`
    - Implement as a WireMock `WebhookTransformer` (implements `com.github.tomakehurst.wiremock.extension.WebhookTransformer`) registered under the name `"webhook"`. A `WebhookTransformer` is a different extension type from `ServeEventListener` and should not be overwritten by the built-in `Webhooks` extension.
    - In `transform(serveEvent, webhookDefinition)`: use the incoming `WebhookDefinition` (which WireMock has already resolved via its built-in templating) to extract the rendered URL, method, headers, and body
    - Parse `auth` metadata from `webhookDefinition.extraParameters` (or stub parameters): build `AsyncEventAuth` with `type: "none"` or `type: "aws_iam"` plus optional region/service
    - Build `AsyncEvent` and serialize to JSON (kotlinx-serialization); call `sqsPublisher.publish(queueUrl, json)`
    - Return a `WebhookDefinition` that points to a no-op / localhost URL so the built-in HTTP executor fires at a harmless target — OR return null/empty if the API allows suppression. Check WireMock 3.x `WebhookTransformer` API to determine the correct suppression mechanism.
    - Log publish success at INFO, failure at WARN; never log header values
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3_

  - [x] 3.2 Write integration test to verify `WebhookAsyncEventPublisher` is invoked correctly
    - Create `WebhookAsyncEventPublisherIntegrationTest.kt` in `infra/aws/runtime/src/test/kotlin`
    - Start a real `WireMockServer` on a real port with `WebhookAsyncEventPublisher` registered (using an in-process `SqsPublisherInterface` stub that captures published messages)
    - Register a trigger stub with `serveEventListeners: [{name: "webhook", parameters: {url: "http://localhost:{port}/callback", method: "POST", body: "test"}}]`
    - Fire the trigger request via real HTTP
    - Assert that the in-process SQS stub captured exactly one `AsyncEvent` message with the correct `url`, `method`, and `body`
    - Assert the built-in WireMock outbound HTTP call did NOT fire to the real callback URL (i.e. no duplicate delivery) — use a `MockWebServer` as the callback target and assert it received at most one request
    - If the test fails because `WebhookAsyncEventPublisher` was never invoked: check that the extension is registered correctly in `WireMockConfiguration.extensions(...)` and that the stub's `serveEventListeners` name matches `getName()` on the transformer. If the built-in still fires, investigate whether `WebhookTransformer.transform()` returning a modified definition suppresses or redirects the HTTP call.
    - _Requirements: 1.1, 1.4, 2.1, 2.2_

  - [x] 3.3 Write unit tests for `WebhookAsyncEventPublisher`
    - Given stub with template expressions in URL/body When `transform()` called Then `AsyncEvent` contains already-rendered values
    - Given stub with `auth.type: "aws_iam"` When `transform()` called Then `AsyncEvent.auth.type == "aws_iam"` with correct region/service
    - Given stub with no auth block When `transform()` called Then `AsyncEvent.auth.type == "none"`
    - Given SQS publish fails When `transform()` called Then failure is logged at WARN and no exception propagates
    - _Requirements: 1.3, 2.1, 2.2, 3.1_

- [x] 4. Delete `WebhookServeEventListener` and update `MockNestConfig`
  - [x] 4.1 Delete `WebhookServeEventListener.kt`
    - Remove the file entirely — replaced by `WebhookAsyncEventPublisher`
    - _Requirements: 1.5_

  - [x] 4.2 Update `MockNestConfig.kt`: wire `WebhookAsyncEventPublisher`, `S3RequestJournalStore`, and `RedactSensitiveHeadersFilter`
    - Replace `WebhookServeEventListener` with `WebhookAsyncEventPublisher` in the `.extensions(...)` call
    - Add `S3RequestJournalStore` wired via `ObjectStorageInterface` and `WebhookConfig.requestJournalPrefix`
    - Add `RedactSensitiveHeadersFilter` to extensions
    - Inject `SqsPublisherInterface` and SQS queue URL env var into `WebhookAsyncEventPublisher`
    - _Requirements: 1.3, 4.1, 5.1_

  - [x] 4.3 Update `MockNestConfigWebhookWiringTest` to reflect new wiring
    - Replace `mocknest-webhook` listener name with `webhook` in the test stub
    - Verify `sqsPublisher.publish(...)` is called instead of `webhookHttpClient.send(...)`
    - _Requirements: 1.1, 2.1_

- [x] 5. Implement `S3RequestJournalStore` — S3-backed WireMock request journal
  - [x] 5.1 Create `S3RequestJournalStore.kt` in `application/runtime/journal`
    - Implement WireMock's `RequestJournalStore` (or equivalent WireMock 3.x extension point for plugging in a custom backing store)
    - On `put(ServeEvent)`: apply `RedactSensitiveHeadersFilter` logic to headers, serialize to JSON, call `objectStorage.save("${prefix}${id}", json)` — catch and log at WARN on failure, never throw
    - On `get(id)`: call `objectStorage.get("${prefix}${id}")`, deserialize, return `Optional`
    - On `getAll()`: call `objectStorage.listPrefix(prefix)`, stream-fetch and deserialize records
    - On `remove(id)`: call `objectStorage.delete("${prefix}${id}")`
    - Store records containing: request ID, timestamp, HTTP method, URL path, query params, headers (redacted), body
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9_

  - [x] 5.2 Write unit tests for `S3RequestJournalStore`
    - Given a `ServeEvent` with sensitive headers When `put()` called Then S3 record has `[REDACTED]` for sensitive header values
    - Given a stored record When `get(id)` called Then returns deserialized record matching original (with redacted headers)
    - Given S3 `save` throws When `put()` called Then WARN is logged and no exception propagates
    - Given S3 `get` throws When `get(id)` called Then WARN is logged and empty Optional returned
    - _Requirements: 4.3, 4.4, 4.5, 4.7_

- [x] 6. Implement `RedactSensitiveHeadersFilter` — admin API read-time redaction
  - [x] 6.1 Create `RedactSensitiveHeadersFilter.kt` in `application/runtime/extensions`
    - Implement `AdminRequestFilterV2` intercepting `GET /__admin/requests` and `GET /__admin/requests/{id}`
    - In the filter: parse the JSON response body, replace values of sensitive header names (from `WebhookConfig.sensitiveHeaders`) with `"[REDACTED]"`, return modified response
    - Must NOT modify the actual `ServeEvent` or `LoggedRequest` objects — only the serialized JSON output
    - Case-insensitive header name matching
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 6.2 Write unit tests for `RedactSensitiveHeadersFilter`
    - Given response JSON with `x-api-key` header When filter applied Then value is `[REDACTED]`
    - Given response JSON with `authorization` header When filter applied Then value is `[REDACTED]`
    - Given response JSON with `x-amz-security-token` header When filter applied Then value is `[REDACTED]`
    - Given response JSON with non-sensitive header When filter applied Then value is unchanged
    - Given header name in mixed case When filter applied Then still redacted (case-insensitive)
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 7. Checkpoint — application layer complete
  - Ensure all application-layer tests pass: `./gradlew :software:application:test`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement `SqsWebhookPublisher` — AWS SQS implementation in infra layer
  - [x] 8.1 Create `SqsWebhookPublisher.kt` in `infra/aws/runtime/webhook`
    - Implement `SqsPublisherInterface` using Kotlin AWS SDK v2 `SqsClient`
    - `publish(queueUrl, messageBody)`: call `sqsClient.sendMessage { this.queueUrl = queueUrl; messageBody = messageBody }`
    - Use `runCatching` and log failures at WARN; rethrow so caller can handle
    - _Requirements: 2.1_

  - [x] 8.2 Update `WebhookInfraConfig.kt`: add `SqsWebhookPublisher` bean and `runtimeAsyncRouter` function bean
    - Add `@Bean fun sqsWebhookPublisher(): SqsPublisherInterface = SqsWebhookPublisher(SqsClient { region = ... })`
    - Add `@Bean fun runtimeAsyncRouter(handler: RuntimeAsyncHandler): Function<SQSEvent, Unit>` that routes SQS events to `RuntimeAsyncHandler`
    - Add `aws.sdk.kotlin:sqs` dependency to `infra/aws/runtime/build.gradle.kts`
    - _Requirements: 2.1, 7.2_

  - [x] 8.3 Write unit tests for `SqsWebhookPublisher`
    - Given valid queue URL and message When `publish()` called Then SQS `sendMessage` is invoked with correct parameters
    - Given SQS client throws When `publish()` called Then exception is logged and rethrown
    - _Requirements: 2.1_

- [x] 9. Implement `RuntimeAsyncHandler` — Lambda handler for async webhook dispatch
  - [x] 9.1 Create `RuntimeAsyncHandler.kt` in `infra/aws/runtime/runtimeasync`
    - Accept `SQSEvent` from Spring Cloud Function adapter; iterate records
    - Deserialize each record body as `AsyncEvent` (kotlinx-serialization)
    - For `actionType == "webhook"`: build `WebhookRequest` from `AsyncEvent` fields; dispatch via `WebhookHttpClientInterface`
    - For `auth.type == "aws_iam"`: sign the outbound request using AWS SDK v2 SigV4 (`software.amazon.awssdk:auth` HTTP signing) with the Lambda execution role credentials before passing to `WebhookHttpClientInterface`
    - For `auth.type == "none"`: forward static headers as-is
    - On `WebhookResult.Failure`: log at WARN with target URL and status code; do NOT throw (allow SQS visibility timeout / DLQ to handle retries)
    - On deserialization error: log at ERROR and do NOT throw (poison-pill protection)
    - Never log credential or signing material at any log level
    - _Requirements: 2.4, 2.5, 2.6, 2.7, 2.8, 3.1, 3.2, 3.4, 3.5_

  - [x] 9.2 Write unit tests for `RuntimeAsyncHandler`
    - Given `AsyncEvent` with `auth.type: "none"` When handler invoked Then `WebhookHttpClientInterface.send()` called with correct URL/method/headers/body
    - Given `AsyncEvent` with `auth.type: "aws_iam"` When handler invoked Then outbound request includes SigV4 `Authorization` header
    - Given `WebhookResult.Failure` When handler processes event Then WARN is logged and no exception thrown
    - Given malformed JSON record When handler processes event Then ERROR is logged and no exception thrown
    - _Requirements: 2.4, 2.7, 3.1, 3.4_

  - [x] 9.3 Write LocalStack integration test for `RuntimeAsyncHandler`
    - Start LocalStack with SQS; publish a real `AsyncEvent` JSON to a test queue
    - Invoke `RuntimeAsyncHandler` directly with the SQS event payload
    - Use `MockWebServer` as the callback target; assert it received the expected request
    - Assert no sensitive header values appear in log output
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6_

- [x] 10. Checkpoint — infra layer complete
  - Ensure all infra-layer tests pass: `./gradlew :software:infra:aws:runtime:test`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Update SAM template — SQS queues, RuntimeAsync Lambda, IAM roles
  - [x] 11.1 Add `MockNestWebhookQueue` and `MockNestWebhookDLQ` SQS resources
    - `MockNestWebhookDLQ`: standard SQS queue, `MessageRetentionPeriod: 1209600` (14 days)
    - `MockNestWebhookQueue`: standard SQS queue with `RedrivePolicy` pointing to `MockNestWebhookDLQ`, `maxReceiveCount: 3`; enable SSE (`SqsManagedSseEnabled: true`)
    - _Requirements: 7.4, 7.5, 7.11_

  - [x] 11.2 Add `MockNestRuntimeAsyncRole` IAM role
    - `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` on `MockNestWebhookQueue`
    - `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents` for CloudWatch Logs
    - No S3 permissions
    - _Requirements: 3.7, 7.6_

  - [x] 11.3 Add `MockNestRuntimeAsyncFunction` and `MockNestRuntimeAsyncFunctionIam` Lambda functions
    - Same `CodeUri` as runtime/generation functions (`mocknest-serverless.jar`)
    - `SPRING_CLOUD_FUNCTION_DEFINITION: "runtimeAsyncRouter"`
    - `MAIN_CLASS: "nl.vintik.mocknest.infra.aws.MockNestApplication"`
    - SQS event source mapping to `MockNestWebhookQueue`, batch size 1
    - Role: `MockNestRuntimeAsyncRole`
    - Timeout and memory via SAM parameters (`RuntimeAsyncTimeout` default 30, `RuntimeAsyncMemorySize` default 256)
    - Add `MockNestRuntimeAsyncLogGroup` CloudWatch log group with `LogRetentionDays`
    - _Requirements: 7.1, 7.2, 7.3, 7.8, 7.9, 7.10_

  - [x] 11.4 Grant `MockNestLambdaRole` SQS send permission on `MockNestWebhookQueue`
    - Add `sqs:SendMessage` on `MockNestWebhookQueue` to `MockNestLambdaRole` policies
    - _Requirements: 7.7_

  - [x] 11.5 Update runtime Lambda env vars in SAM template
    - Add `MOCKNEST_WEBHOOK_QUEUE_URL: !GetAtt MockNestWebhookQueue.QueueUrl` to both `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam`
    - Add `MOCKNEST_REQUEST_JOURNAL_PREFIX: "requests/"` to both runtime functions
    - Update `MOCKNEST_SENSITIVE_HEADERS` default to include `proxy-authorization,x-amz-security-token`
    - _Requirements: 2.1, 4.8, 5.4_

  - [x] 11.6 Validate SAM template
    - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
    - _Requirements: 7.1–7.11_

- [x] 12. Update `post-deploy-test.sh` — IAM-mode webhook test
  - [x] 12.1 Rewrite `test_webhook_delivery()` for IAM mode + S3 journal polling
    - Register callback mock at `/mocknest/webhook-callback` (same as before)
    - Register trigger mock using `serveEventListeners: [{name: "webhook"}]` (standard WireMock name) with `auth.type: "aws_iam"`
    - Call trigger endpoint using SigV4 (`--aws-sigv4` curl option, already in `CURL_OPTS` for IAM mode)
    - Poll `MockStorage` S3 bucket under `requests/` prefix for a record matching `/mocknest/webhook-callback` path (use `aws s3api list-objects-v2` + `aws s3api get-object`)
    - Poll with configurable timeout (default 30s) and retry interval (default 2s)
    - Assert `authorization` and `x-amz-security-token` header values in the S3 record are `[REDACTED]`
    - Fail with descriptive message if polling times out
    - Remove all references to `x-api-key` auth in this test function
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9_

- [x] 13. Update `workflow-integration-test.yml` — webhook job IAM-only guard
  - [x] 13.1 Add `AUTH_MODE == IAM` guard to `test-webhook` job
    - Add condition: `needs.setup.outputs.auth-mode == 'IAM'` to the `test-webhook` job `if:` expression
    - Webhook test must only run on Deployment B (IAM mode); skip silently on API_KEY deployments
    - _Requirements: 6.1_

- [x] 14. Write local integration test for full async dispatch flow
  - [x] 14.1 Create `WebhookAsyncDispatchIntegrationTest.kt` in `infra/aws/runtime/src/test/kotlin`
    - Start a real `WireMockServer` with `WebhookAsyncEventPublisher` and `S3RequestJournalStore` (backed by LocalStack S3) registered
    - Use LocalStack SQS (via TestContainers) as the `SqsPublisherInterface` implementation
    - Register a trigger stub with `serveEventListeners: [{name: "webhook", parameters: {url: ..., method: POST, auth: {type: "aws_iam"}}}]`
    - Register a callback stub on the same WireMock server
    - Fire the trigger request; consume the SQS message; invoke `RuntimeAsyncHandler` directly with the event
    - Assert the callback stub received the expected request
    - Assert the S3 journal record for the callback request has `authorization` and `x-amz-security-token` set to `[REDACTED]`
    - Assert no raw sensitive header values appear in captured log output
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

- [x] 15. Update documentation
  - [x] 15.1 Update `docs/USAGE.md` with async webhook model, auth modes, env vars, and S3 journal
    - Document `serveEventListeners` with `name: "webhook"` (standard WireMock format)
    - Document `auth.type: "none"` and `auth.type: "aws_iam"` with examples; note `secret_ref`/`env_var` as future roadmap
    - Document all new env vars: `MOCKNEST_WEBHOOK_ASYNC_TIMEOUT_MS`, `MOCKNEST_REQUEST_JOURNAL_PREFIX`, updated `MOCKNEST_SENSITIVE_HEADERS` defaults
    - Document S3 journal key prefix format and how to use it for pipeline verification
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [x] 15.2 Update `README.md` to list async webhook/callback support as a validated current feature
    - _Requirements: 9.5_

- [x] 16. Final checkpoint — verify coverage and quality
  - [x] 16.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new code (aim for 90%+)
  - [x] 16.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [x] 16.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Task 3.2 is a mandatory integration test — it verifies `WebhookAsyncEventPublisher` actually fires and that no duplicate delivery occurs. If the extension is not invoked, check registration in `WireMockConfiguration.extensions(...)` and whether the stub's `serveEventListeners` name matches `getName()`. If the built-in still fires alongside it, the name may need to change to `mocknest-webhook` (stubs would then use that name instead of `webhook`).
- `MockNestRuntimeAsyncRole` has NO S3 permissions — the runtime-async Lambda executes directly from the `AsyncEvent` payload
- SigV4 signing in `RuntimeAsyncHandler` uses AWS SDK v2 (`software.amazon.awssdk:auth`) with the Lambda execution role credentials; credentials and signing material must never be logged
- The existing `MockNestDLQ` is NOT repurposed — `MockNestWebhookDLQ` is a separate queue
- All three Lambda functions use the same `mocknest-serverless.jar`; the runtime-async entry point is selected via `SPRING_CLOUD_FUNCTION_DEFINITION: "runtimeAsyncRouter"`
