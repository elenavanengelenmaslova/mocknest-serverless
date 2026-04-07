# Implementation Plan: Webhook Support

## Overview

Implement reliable webhook/callback-style behavior for MockNest Serverless. The plan follows clean architecture order: prototype first, then application layer interfaces and components, then infrastructure layer, then wiring, then deployment config, then tests, then documentation.

## Tasks

- [x] 1. Prototype and validate `ServeEventListener` behavior
  - Write a minimal standalone test (outside the main test suite) that validates four critical assumptions:
    1. **Redaction timing:** Mutating `ServeEvent` request headers in `afterMatch()` results in the redacted values being stored in the journal (i.e., `requestJournal.requestReceived()` sees the mutated headers, not the originals)
    2. **Name collision:** Registering a `ServeEventListener` named `"webhook"` replaces the built-in `Webhooks` extension and prevents the async dispatch from firing
    3. **Webhook parameters in `beforeResponseSent`:** `serveEvent` in `beforeResponseSent()` contains the `serveEventListeners` parameters from the matched stub mapping
    4. **Original request access:** The original `Request` object (with unredacted header values) is accessible in `beforeResponseSent()` after `afterMatch()` has mutated the `ServeEvent` — needed for `original_request_header` value source
  - Use a real `WireMockServer` with a real port and OkHttp `MockWebServer` as the callback target
  - Document findings in a comment block at the top of `WebhookServeEventListener.kt`
  - **This task is a gate: if any assumption fails, the design must be revised before proceeding with tasks 2–8**
  - _Requirements: 1.1, 4.1, 7.1_

- [x] 2. Define application layer interfaces and data models
  - Create `WebhookHttpClientInterface`, `WebhookRequest`, and `WebhookResult` in `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/`
  - `WebhookResult` is a sealed class with `Success(statusCode: Int)` and `Failure(statusCode: Int?, message: String)` subtypes
  - `WebhookRequest` is a data class with `url`, `method`, `headers`, `body`, `timeoutMs`
  - Create `WebhookAuthConfig` sealed class hierarchy in `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/`:
    ```kotlin
    sealed class WebhookAuthConfig {
        object None : WebhookAuthConfig()
        data class Header(
            val injectName: String,
            val valueSource: HeaderValueSource,
        ) : WebhookAuthConfig()
        // Future: data class AwsIam(val region: String? = null) : WebhookAuthConfig()
    }

    sealed class HeaderValueSource {
        // v1 — implemented
        data class OriginalRequestHeader(val headerName: String) : HeaderValueSource()
        // Future — not implemented in v1:
        // data class Static(val value: String) : HeaderValueSource()
        // data class SecretRef(val secretRef: String) : HeaderValueSource()
        // data class EnvVar(val envVar: String) : HeaderValueSource()
    }
    ```
  - _Requirements: 1.1, 3.1, 3.3_

  - [x] 2.1 Write unit tests for `WebhookRequest`, `WebhookResult`, and `WebhookAuthConfig` data models
    - Test Given valid fields When constructing WebhookRequest Then all properties are accessible
    - Test Given Success result When checking type Then is Success subtype
    - Test Given Failure result with null statusCode When checking message Then message is preserved
    - Test Given Header auth config with OriginalRequestHeader When accessing fields Then injectName and headerName are accessible
    - _Requirements: 1.1, 3.1_

- [x] 3. Implement `WebhookConfig`
  - Create `WebhookConfig` data class in `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/config/`
  - Read `MOCKNEST_SELF_URL`, `MOCKNEST_SENSITIVE_HEADERS` (default: `x-api-key,authorization`), `MOCKNEST_WEBHOOK_TIMEOUT_MS` (default: `10000`) from `System.getenv()`
  - Normalize sensitive header names to lowercase and store as `Set<String>`
  - _Requirements: 1.4, 4.3_

  - [x] 3.1 Write unit tests for `WebhookConfig`
    - Test Given all env vars set When constructing WebhookConfig Then all fields are populated correctly
    - Test Given no env vars set When constructing WebhookConfig Then defaults are applied (`sensitiveHeaders` = `{x-api-key, authorization}`, `webhookTimeoutMs` = 10000)
    - Test Given `MOCKNEST_SENSITIVE_HEADERS` with mixed-case names When constructing Then names are normalized to lowercase
    - Test Given `MOCKNEST_SENSITIVE_HEADERS` with whitespace around names When constructing Then names are trimmed
    - _Requirements: 1.4, 4.3_

  - [x] 3.2 Write property-based tests for `WebhookConfig` sensitive header parsing
    - Use `@ParameterizedTest` with 12+ diverse `MOCKNEST_SENSITIVE_HEADERS` values (single header, multiple comma-separated, mixed case, extra whitespace, empty string, duplicates)
    - Store test cases in `src/test/resources/test-data/webhook/sensitive-header-configs.csv`
    - Property: for any valid comma-separated header name list, all names appear in `sensitiveHeaders` as lowercase trimmed strings
    - **Validates: Requirements 4.3**

- [-] 4. Implement `WebhookServeEventListener`
  - Create `WebhookServeEventListener` in `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/`
  - Implements `com.github.tomakehurst.wiremock.extension.ServeEventListener`
  - Constructor takes `WebhookHttpClientInterface` and `WebhookConfig` as `private val`
  - `getName()` returns `"webhook"` — replaces the built-in async `Webhooks` extension
  - `applyGlobally()` returns `true` — needed so redaction applies to all requests

  - **`afterMatch(serveEvent, parameters)`:**
    - Capture the original `Request` object from `serveEvent` before any mutation (store as a thread-local or pass via a mechanism accessible in `beforeResponseSent`)
    - Iterate over all request headers in `serveEvent.getRequest()`
    - For each header whose name (lowercased) is in `WebhookConfig.sensitiveHeaders`, replace its value(s) with `[REDACTED]`
    - Catch all exceptions: log at `ERROR`, do not rethrow

  - **`beforeResponseSent(serveEvent, parameters)`:**
    - Check if the matched stub has `serveEventListeners` with name `"webhook"`; if not, return immediately
    - Extract webhook parameters (url, method, body, auth block)
    - Resolve `{{mocknest-self-url}}` placeholder in URL from `WebhookConfig.selfUrl`
    - Parse `WebhookAuthConfig` from the `auth` block (default: `None` if absent)
    - Build outbound headers based on auth config:
      - `None`: no auth headers added
      - `Header(injectName, OriginalRequestHeader(headerName))`: read `headerName` from the original (pre-redaction) request, inject as `injectName` in the outbound request — never log the value
    - Build `WebhookRequest` with `WebhookConfig.webhookTimeoutMs`
    - Call `webhookHttpClient.send(request)` — blocking
    - On `WebhookResult.Failure`: log at `WARN` with URL and status code only — never log header values — never rethrow
    - Catch all exceptions: log at `WARN`, never rethrow

  - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.8, 4.1, 4.4, 6.1, 7.1, 7.3_

  - [x] 4.1 Write unit tests for `WebhookServeEventListener` — redaction
    - Test Given request with `x-api-key` header When afterMatch called Then `x-api-key` value is `[REDACTED]` in ServeEvent
    - Test Given request with `authorization` header When afterMatch called Then `authorization` value is `[REDACTED]`
    - Test Given request with non-sensitive header When afterMatch called Then header value is unchanged
    - Test Given request with mixed-case sensitive header name (e.g. `X-Api-Key`) When afterMatch called Then value is still redacted (case-insensitive match)
    - Test Given afterMatch throws internally When called Then no exception propagates
    - _Requirements: 4.1, 4.3, 4.4_

  - [x] 4.2 Write unit tests for `WebhookServeEventListener` — webhook dispatch with auth config
    - Test Given stub with webhook listener and no auth block When beforeResponseSent called Then `webhookHttpClient.send()` is called with no injected auth header
    - Test Given stub with webhook listener and `auth.type=none` When beforeResponseSent called Then `webhookHttpClient.send()` is called with no injected auth header
    - Test Given stub with webhook listener and `auth.type=header, source=original_request_header` When beforeResponseSent called Then the named header from the original request is injected under the configured inject name
    - Test Given stub without webhook listener When beforeResponseSent called Then `webhookHttpClient.send()` is NOT called
    - Test Given webhook client returns `Failure` When beforeResponseSent called Then warning is logged and no exception is thrown
    - Test Given webhook client throws exception When beforeResponseSent called Then warning is logged and no exception propagates
    - Test Given timeout configured When beforeResponseSent called Then `WebhookRequest.timeoutMs` matches `WebhookConfig.webhookTimeoutMs`
    - Test Given webhook URL contains `{{mocknest-self-url}}` When beforeResponseSent called Then placeholder is replaced with `WebhookConfig.selfUrl`
    - Test Given `WebhookConfig.selfUrl` is null and URL contains `{{mocknest-self-url}}` When beforeResponseSent called Then placeholder is replaced with empty string and failure is logged
    - _Requirements: 1.1, 1.3, 3.1, 3.8, 7.1, 7.3_

  - [x] 4.3 Write property-based tests — Property 2: Failure isolation
    - Use `@ParameterizedTest` with 12+ diverse test data files in `src/test/resources/test-data/webhook/failure-isolation/`
    - Each file: a JSON object with `mockResponse` (status, body, headers) and `webhookFailureType` (non-2xx, network-error, timeout)
    - Mock `WebhookHttpClientInterface` to return the specified failure; verify `beforeResponseSent()` does not throw and returns normally
    - **Property 2: Failure isolation**
    - **Validates: Requirements 1.3, 7.3**

  - [x] 4.4 Write property-based tests — Property 5: Redaction completeness
    - Use `@ParameterizedTest` with 15+ diverse test data files in `src/test/resources/test-data/webhook/redaction/`
    - Each file: a JSON object with `sensitiveHeaders` list, `requestHeaders` map, `expectedRedacted` list, `expectedPreserved` list
    - Cover: default headers, custom headers, mixed case, short values, long values, UUID values, special chars, empty value, multiple sensitive headers in same request
    - **Property 5: Redaction completeness**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**

  - [x] 4.5 Write property-based tests — Property 3: Template rendering fidelity
    - Use `@ParameterizedTest` with 12+ diverse test data files in `src/test/resources/test-data/webhook/template-rendering/`
    - Each file: a JSON object with `triggerRequest` (body, headers, path) and `webhookParameters` (already-rendered values from WireMock's template engine) and `expectedSentValues`
    - Verify the values passed to `webhookHttpClient.send()` match the expected rendered values
    - **Property 3: Template rendering fidelity**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4**

  - [x] 4.6 Write property-based tests — Property 4: Auth header injection from original request
    - Use `@ParameterizedTest` with 10+ diverse incoming header values in `src/test/resources/test-data/webhook/auth-injection/`
    - Each file: a JSON object with `incomingHeaderName`, `incomingHeaderValue`, `injectName`, `expectedOutboundHeaderValue`
    - Verify the outbound webhook request contains `injectName: incomingHeaderValue`
    - Verify the value does NOT appear in any log output (capture via test log appender)
    - **Property 4: Auth header injection from original request**
    - **Validates: Requirements 3.4, 3.8, 6.1**

- [x] 5. Checkpoint — Ensure application layer tests pass
  - Run `./gradlew :software:application:test` and confirm all tests pass
  - Ask the user if questions arise

- [x] 6. Implement `WebhookHttpClient` in infrastructure layer
  - Create `WebhookHttpClient` in `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/webhook/`
  - Implements `WebhookHttpClientInterface`
  - Constructor takes `WebhookConfig` as `private val`; build `OkHttpClient` with `callTimeout(webhookConfig.webhookTimeoutMs, MILLISECONDS)` — reuse client across calls (Spring singleton)
  - `send(request: WebhookRequest): WebhookResult`: use `OkHttpClient.newCall(okHttpRequest).execute()` (blocking, not `enqueue`)
  - Map non-2xx responses to `WebhookResult.Failure(statusCode, message)`
  - Map `IOException` / `SocketTimeoutException` to `WebhookResult.Failure(null, message)`
  - Map 2xx responses to `WebhookResult.Success(statusCode)`
  - Do not log header values — log only URL, method, and status code
  - _Requirements: 1.1, 1.4, 7.1_

  - [x] 6.1 Write unit tests for `WebhookHttpClient`
    - Test Given 200 response When send called Then returns `WebhookResult.Success(200)`
    - Test Given 503 response When send called Then returns `WebhookResult.Failure(503, ...)`
    - Test Given `SocketTimeoutException` When send called Then returns `WebhookResult.Failure(null, ...)`
    - Test Given `IOException` When send called Then returns `WebhookResult.Failure(null, ...)`
    - Test Given timeout configured When client built Then OkHttpClient has matching `callTimeout`
    - _Requirements: 1.1, 1.4_

  - [x] 6.2 Write property-based tests for `WebhookHttpClient` — Property 1 (partial)
    - Use `@ParameterizedTest` with 10+ diverse `WebhookRequest` inputs (different methods, header combinations, body present/absent)
    - Use OkHttp's `MockWebServer` to verify the outbound request is constructed correctly and the call is synchronous (blocking)
    - **Property 1 partial: Webhook delivery — HTTP client correctness**
    - **Validates: Requirements 1.1, 7.1**

- [x] 7. Wire new extension into `MockNestConfig`
  - Update `MockNestConfig.kt` in `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/config/`
  - Add `webhookConfig(): WebhookConfig` `@Bean`
  - Add `webhookHttpClient(webhookConfig): WebhookHttpClientInterface` `@Bean` — returns `WebhookHttpClient` from infra layer via interface (clean architecture boundary preserved)
  - Update `wireMockServer(...)` bean to register the extension:
    ```kotlin
    .extensions(
        NormalizeMappingBodyFilter(storage),
        DeleteAllMappingsAndFilesFilter(storage),
        WebhookServeEventListener(webhookHttpClient, webhookConfig),  // NEW — named "webhook", replaces built-in
    )
    ```
  - _Requirements: 1.1, 4.1_

  - [x] 7.1 Write unit tests for `MockNestConfig` webhook wiring
    - Test Given Spring context loads When wireMockServer bean created Then `WebhookServeEventListener` is registered as extension with name `"webhook"`
    - _Requirements: 1.1_

- [x] 8. Implement local integration test `WebhookIntegrationTest`
  - Create `WebhookIntegrationTest.kt` in `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/`
  - Use a `WireMockServer` started on a random port with `WebhookServeEventListener` registered — a real HTTP listener is required because `WebhookHttpClient` makes real outbound HTTP calls via OkHttp
  - Do NOT use `DirectCallHttpServer` — it bypasses the HTTP stack

  - **Test scenario 1 — Webhook delivery with `original_request_header` auth:**
    1. Start `WireMockServer` on a random port
    2. Register callback mock: `POST /mocknest/payments/callback` → 200 OK
    3. Register trigger mock: `POST /mocknest/orders` → 202 Accepted, with `serveEventListeners` webhook targeting `http://localhost:{port}/mocknest/payments/callback`, with `auth: { type: header, inject: { name: x-api-key }, value: { source: original_request_header, headerName: x-api-key } }`
    4. Call trigger mock via real HTTP with `x-api-key: test-key-value`
    5. Assert trigger response is 202 Accepted
    6. Query `/__admin/requests` — no polling needed (webhook is synchronous)
    7. Assert callback mock received a request
    8. Assert `x-api-key` header in the callback journal entry is `[REDACTED]` (not `test-key-value`)

  - **Test scenario 2 — Sensitive header redaction on direct inbound requests:**
    1. Send a request to any mock with `x-api-key: test-key-value`
    2. Query `/__admin/requests`
    3. Assert `x-api-key` value is `[REDACTED]`

  - Runnable with: `./gradlew :software:infra:aws:runtime:test --tests "*WebhookIntegrationTest*"`
  - No AWS credentials required
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ] 8.1 Write property-based integration test — Property 1 (end-to-end)
    - Use `@ParameterizedTest` with 10+ diverse trigger mock configurations stored in `src/test/resources/test-data/webhook/integration/trigger-configs/`
    - For each config: register trigger + callback mocks, call trigger via real HTTP, assert callback was received (no polling — synchronous)
    - **Property 1: Webhook delivery before handler returns**
    - **Validates: Requirements 1.1, 7.1, 8.3**

  - [ ] 8.2 Write property-based integration test — Property 6
    - Use `@ParameterizedTest` with 10+ diverse sensitive header values stored in `src/test/resources/test-data/webhook/integration/sensitive-values/`
    - Capture log output via test log appender; query `/__admin/requests`; assert sensitive values absent from both
    - **Property 6: Sensitive value non-exposure in logs**
    - **Validates: Requirements 3.8, 4.5, 6.1**

- [x] 9. Checkpoint — Ensure all tests pass
  - Run `./gradlew :software:infra:aws:runtime:test` and confirm all tests pass
  - Ask the user if questions arise

- [ ] 10. Update SAM template with webhook environment variables
  - Add 3 new env vars to both `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam` `Environment.Variables` blocks:
    - `MOCKNEST_SELF_URL`: `!Sub` with `!If [IsIamMode, ...]` selecting the correct API ID
    - `MOCKNEST_SENSITIVE_HEADERS`: `"x-api-key,authorization"`
    - `MOCKNEST_WEBHOOK_TIMEOUT_MS`: `"10000"`
  - No new IAM permissions required for v1 — `original_request_header` reads from the incoming request, no external backend calls
  - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
  - _Requirements: 5.1_

- [ ] 11. Add `webhook` test suite to `scripts/post-deploy-test.sh`
  - Add `test_webhook_delivery()` function following the existing pattern:
    1. Register callback mock: `POST /mocknest/webhook-callback` → 200 OK
    2. Register trigger mock: `POST /mocknest/webhook-trigger` → 202 Accepted, with `serveEventListeners` webhook targeting `$API_URL/mocknest/webhook-callback`, with `auth: { type: header, inject: { name: x-api-key }, value: { source: original_request_header, headerName: x-api-key } }`
    3. Call trigger: `POST $API_URL/mocknest/webhook-trigger` with `x-api-key: $API_KEY` — the trigger endpoint requires the API key to satisfy API Gateway auth; the webhook auth config copies this same key to the outbound callback call
    4. Poll `GET $API_URL/__admin/requests` (max 10s, 1s interval) to find a request matching `/mocknest/webhook-callback`
    5. Assert callback request found; fail with descriptive error if timeout exceeded
    6. Assert `x-api-key` header value in the callback journal entry is `[REDACTED]`
    7. Cleanup: `DELETE $API_URL/__admin/mappings`
  - Add `webhook` case to the `main()` `case` statement
  - Add `webhook` to the `all` suite execution sequence
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.7_

- [ ] 12. Update GitHub Actions workflow for webhook test suite
  - Add `webhook` to the `test-suite` `choice` options in `.github/workflows/workflow-integration-test.yml` (both `workflow_call` and `workflow_dispatch` inputs)
  - Add `test-webhook` job following the same pattern as `test-rest`:
    - `needs: setup`
    - `if: success() && (inputs.test-suite == 'all' || inputs.test-suite == 'webhook')`
    - Retrieve API key, mask immediately with `echo "::add-mask::$API_KEY"`
    - Run `./scripts/post-deploy-test.sh webhook`
  - _Requirements: 9.6_

- [ ] 13. Update documentation
  - Update `docs/USAGE.md`: add a "Webhook Support" section with:
    - Complete mapping example using `serveEventListeners` format with `auth.type=header, source=original_request_header`
    - Explanation of the two-level auth config structure (`type` + `value.source`) and why it is designed this way
    - Mention of future value sources (`static`, `secret_ref`, `env_var`) and `aws_iam` auth type as roadmap items
    - Table of webhook env vars (`MOCKNEST_SELF_URL`, `MOCKNEST_SENSITIVE_HEADERS`, `MOCKNEST_WEBHOOK_TIMEOUT_MS`) with defaults and descriptions
    - Explanation of the Lambda execution context constraint and how synchronous dispatch addresses it
    - Note on recommended minimum Lambda timeout (30s) when webhooks are used
    - Note that sensitive headers are redacted before journal storage
    - Known limitation: if the webhook callback is handled by a different Lambda invocation, its journal entry will be in that invocation's in-memory journal
    - Clarification that the trigger request must include the API key to satisfy MockNest / API Gateway auth, and the webhook auth config copies it to the outbound callback call
  - Update `README.md`: list webhook/callback support as a validated current feature
  - Update `README-SAR.md`: mention webhook support as a supported feature
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [ ] 14. Final checkpoint — Verify test coverage and quality
  - [ ] 14.1 Run `./gradlew koverHtmlReport` and verify 80%+ coverage for new code (aim for 90%+)
  - [ ] 14.2 Run `./gradlew koverVerify` to enforce coverage threshold
  - [ ] 14.3 Review test quality: Given-When-Then naming, proper assertions, edge case coverage for all 6 correctness properties

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster first pass
- Task 1 (prototype) is a gate — if any of the four assumptions fail, the design must be revised before proceeding
- `WebhookServeEventListener` is registered under the name `"webhook"` to replace the built-in async `Webhooks` extension
- `applyGlobally()` returns `true` so redaction applies to all requests, not just those with webhook stubs
- Redaction happens in `afterMatch()` — before `requestJournal.requestReceived()` — so sensitive headers are never stored in plain text
- The `original_request_header` value source reads from the original `Request` object before redaction is applied to the `ServeEvent` — the implementation must preserve access to the pre-redaction values
- Webhook dispatch happens in `beforeResponseSent()` — synchronously, before the response is returned — so the call completes before Lambda freezes
- The local integration test uses a real `WireMockServer` port — no AWS credentials required
- No Secrets Manager dependency, no new IAM permissions, no external backend calls in v1
- The `WebhookAuthConfig` sealed class is designed to accommodate future value sources (`Static`, `SecretRef`, `EnvVar`) and future auth types (`AwsIam`) without breaking changes to existing configs
- Run `sam validate` after every SAM template change (task 10) before committing
