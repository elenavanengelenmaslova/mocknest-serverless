# Bugfix Requirements Document

## Introduction

This document captures the requirements for a broad set of security, correctness, and code-quality fixes across the webhook and async dispatch subsystem of MockNest Serverless. The findings span four areas:

1. **Critical / Security** — data leaks, fail-open redaction, and silent event loss
2. **Major** — silent failure modes, misconfigured infrastructure, and flawed test assertions
3. **Minor / Nitpick** — code quality, unused imports, misleading test names, and minor inefficiencies
4. **Feature gaps** — IAM role separation and priming isolation for runtime vs. generation Lambdas

Left unaddressed, the critical and major issues can expose sensitive credentials in CloudWatch and S3, silently drop webhook events, cause duplicate SQS processing, and produce false-positive CI results. The minor issues accumulate technical debt and reduce maintainability.

---

## Bug Analysis

### Current Behavior (Defect)

#### Critical / Security

1.1 WHEN `beforeResponseSent()` in `WebhookServeEventListener` executes a bare `return` inside the inline `runCatching` block and no listener or URL is present THEN the system exits the entire method via a non-local return, skipping `capturedHeaders.remove(serveEvent.id)`, leaving sensitive side-channel header entries alive for subsequent warm Lambda invocations

1.2 WHEN the webhook dispatch, success, or failure log lines in `WebhookServeEventListener` emit the raw `url` variable THEN the system writes callback URLs including query-string tokens and tenant IDs to CloudWatch Logs

1.3 WHEN `RedactSensitiveHeadersFilter.redactServeEvent` encounters any serialization or redaction error THEN the system returns `mapper.writeValueAsString(event)` — the fully unredacted JSON — and writes it to S3, leaking sensitive headers

#### Major

1.4 WHEN `sqsPublisher.publish` fails inside `WebhookAsyncEventPublisher.transform` THEN the system swallows the exception in `onFailure` and still returns `webhookDefinition.withUrl(NO_OP_URL)`, silently dropping the webhook event with no observable signal to WireMock

1.5 WHEN `MOCKNEST_WEBHOOK_QUEUE_URL` is not set (defaults to empty string) THEN the system instantiates and registers `WebhookAsyncEventPublisher` anyway, causing all webhook events to be silently dropped without any startup warning or error

1.6 WHEN `RuntimeAsyncHandler.handleRecord` catches any exception including transient network or HTTP 5xx delivery failures THEN the system logs a warning and returns normally, preventing SQS from retrying the message and routing it to the DLQ

1.7 WHEN all four CI suite jobs (`test-rest`, `test-graphql`, `test-soap`, `test-webhook`) run in parallel against the same shared deployed MockNest stack THEN the system experiences cross-job interference because the suites mutate shared WireMock state and the webhook suite inspects the global request journal

1.8 WHEN `test_webhook_delivery` in `post-deploy-test.sh` scans the entire journal body for `x-api-key` and `[REDACTED]` THEN the system can produce false positives from stale journal entries unrelated to the current callback

1.9 WHEN the trigger POST in `post-deploy-test.sh` is sent immediately after creating the mapping stub with no retry THEN the system can return 404 if the mapping has not yet propagated, causing a spurious test failure

1.10 WHEN `MOCKNEST_WEBHOOK_TIMEOUT_MS` is hardcoded to `"10000"` in the SAM template THEN the system can exceed the deployed `LambdaTimeout`, causing the Lambda to be killed before the HTTP client times out

1.11 WHEN `MockNestWebhookQueue` uses the default SQS `VisibilityTimeout` of 30 seconds, which equals `RuntimeAsyncTimeout` THEN the system risks duplicate processing of the same message if the Lambda does not complete within the visibility window

1.12 WHEN the S3 `ExpireRequestJournalRecords` lifecycle rule sets `ExpirationInDays` but omits `NoncurrentVersionExpirationInDays` THEN the system retains noncurrent versions of request journal objects in S3 indefinitely

1.13 WHEN `WebhookConfigTest` uses a no-op `withEnv()` and a local `buildConfig()` that duplicates `WebhookConfig.fromEnv()` logic THEN the system allows production parsing to drift without the tests detecting it

1.14 WHEN `RuntimeAsyncHandler` forwards `asyncTimeoutMs` into `WebhookRequest.timeoutMs` but `WebhookHttpClient` always uses `webhookConfig.webhookTimeoutMs` THEN the system ignores per-request timeout overrides, making `WebhookRequest.timeoutMs` a dead field

1.15 WHEN `RuntimeAsyncPrimingHook.primeCredentialsProvider` calls `DefaultChainCredentialsProvider().resolve()` inside `runBlocking` with no timeout THEN the system can block indefinitely on slow credential sources during SnapStart priming

1.16 WHEN `RuntimeAsyncSpringContextTest` runs the test named "Given async profile active When Spring context loads Then wireMockServer bean is NOT registered by MockNestConfig" THEN the system asserts that `directCallHttpServerFactory` is absent instead of `wireMockServer`, making the test name and assertion body contradict each other

#### Minor / Nitpick

1.17 WHEN `WebhookHttpClientTest` is compiled THEN the system includes an unused import `java.net.SocketTimeoutException`

1.18 WHEN `WebhookHttpClientTest` asserts a result type at lines 63–65 THEN the system uses `assertInstanceOf` followed by a cast instead of the idiomatic `assertIs<WebhookResult.Success>(result)`

1.19 WHEN `WebhookHttpClientTest` asserts headers at lines 219–228 THEN the system only checks `x-correlation-id` and omits `content-type`

1.20 WHEN `WebhookHttpClientTest` runs the test named "empty body is sent" at lines 240–248 THEN the system only asserts the HTTP method and does not verify the body is empty

1.21 WHEN `WebhookHttpClientTest` runs the timeout test at lines 183–212 THEN the system duplicates coverage already present in the `NetworkErrors` test group

1.22 WHEN `WebhookHttpClient` stores a full `WebhookConfig` but only uses `webhookTimeoutMs` THEN the system has an unnecessarily broad dependency with no explanatory comment

1.23 WHEN `WebhookConfigPropertyTest` defines a local `parseHeaders()` at lines 18–23 THEN the system duplicates production parsing logic, allowing drift

1.24 WHEN `WebhookConfigPropertyTest` runs the test at lines 42–55 THEN the system executes a redundant `forEach` loop that is superseded by the subsequent `assertEquals`

1.25 WHEN `WebhookServeEventListenerTest` is compiled THEN the system includes unused imports `ParameterizedTest` and `MethodSource`

1.26 WHEN `WebhookServeEventListenerTest` runs `AfterMatchRedaction` tests at lines 94–123 THEN the system only asserts no exception is thrown and does not verify the state of `capturedHeaders`

1.27 WHEN `WebhookServeEventListenerPrototypeTest` waits for async behavior at lines 194, 272, 334, and 407 THEN the system uses `Thread.sleep(200)` instead of condition polling, making tests timing-dependent and flaky

1.28 WHEN `WebhookTemplateRenderingPropertyTest` calls `setSerializationInclusion(ALWAYS)` at lines 35–37 THEN the system applies a serialization setting in a deserialization-only test, which has no effect

1.29 WHEN `WebhookRedactionPropertyTest` runs at lines 164–197 THEN the system does not explicitly verify the fallback behavior for non-sensitive headers

1.30 WHEN `RedactSensitiveHeadersFilter` KDoc at lines 62–66 states "does not parse into objects" THEN the system's implementation actually parses into a `MutableMap`, making the documentation incorrect

1.31 WHEN `RedactSensitiveHeadersFilter` performs the cast at lines 86–92 THEN the system relies on an undocumented unchecked cast assumption (`Map<*,*>` → `MutableMap<String, Any?>`)

1.32 WHEN `SqsWebhookPublisher` logs a WARN before rethrowing at lines 18–32 THEN the system may produce duplicate log entries if the caller also logs the same exception

1.33 WHEN `S3RequestJournalStoreTest` asserts `assertFalse(savedJson.contains("secret-key"))` at lines 44–60 THEN the system produces a vacuously true assertion because `savedJson` is the mocked redacted value

1.34 WHEN `MockNestConfigWebhookWiringTest` calls `Thread.sleep(1000)` at lines 73–76 THEN the system introduces an unnecessary delay because `transform()` blocks synchronously

1.35 WHEN `RuntimeAsyncHandlerLocalStackTest` uses `capturingClient` at lines 38–40 THEN the system bypasses real HTTP, making it not a true integration test despite the name

1.36 WHEN `RuntimeAsyncHandlerLocalStackTest` calls `assertNotNull(capturedRequest)` at lines 71–74 followed by direct property access THEN the system does not capture the non-null return value, requiring a redundant null check

1.37 WHEN `WebhookAsyncEventPublisherIntegrationTest` waits for async behavior at lines 106–117, 147–152, and 183–189 THEN the system uses `Thread.sleep(500)` instead of polling assertions

1.38 WHEN `RuntimeLambdaHandler` and multiple other components reference the `"!async"` profile literal THEN the system repeats the string across components with no shared constant or meta-annotation

1.39 WHEN `MockNestRuntimeAsyncSqsAccess` IAM policy is evaluated THEN the system is missing `sqs:ChangeMessageVisibility` permission, which is required for visibility timeout extension

1.40 WHEN `MOCKNEST_WEBHOOK_TIMEOUT_MS` and `MOCKNEST_SENSITIVE_HEADERS` are defined in the SAM template THEN the system hardcodes these values instead of exposing them as SAM parameters with defaults

1.41 WHEN `README.md` line 49 is linted THEN the system produces an MD037 warning due to a trailing space inside bold emphasis

1.42 WHEN `RuntimeAsyncSpringContextTest` uses `assert()` at lines 159–160 THEN the system relies on JVM assertion flags being enabled instead of using `kotlin.test.assertTrue()`

1.43 WHEN `RuntimeAsyncSpringContextTest` mixes `@MockitoBean` and `mockk()` at line 18 THEN the system uses two different mocking frameworks in the same test class

1.44 WHEN `S3RequestJournalStore.getAll()` fetches journal entries at lines 93–105 THEN the system makes N sequential `storage.get()` calls instead of using a bulk `storage.getMany()` operation

1.45 WHEN `S3RequestJournalStore.removeLast()` deletes an entry at lines 116–124 THEN the system deletes `keys.first()` (an arbitrary key) rather than the oldest entry

#### Feature Gaps

1.46 WHEN the runtime Lambda and generation Lambda share `MockNestLambdaRole` THEN the system grants the runtime Lambda unnecessary Bedrock access (`bedrock:InvokeModel`, `bedrock:InvokeModelWithResponseStream`) that it does not require

1.47 WHEN both the runtime priming hook and generation priming hook execute together under the same Spring profile THEN the system cannot independently configure or disable priming for each Lambda type, coupling concerns that should be separate

---

### Expected Behavior (Correct)

#### Critical / Security

2.1 WHEN `beforeResponseSent()` in `WebhookServeEventListener` executes inside the inline `runCatching` block and no listener or URL is present THEN the system SHALL use `return@runCatching` (or a `try/finally` block) so that `capturedHeaders.remove(serveEvent.id)` always executes before the method returns

2.2 WHEN the webhook dispatch, success, or failure log lines in `WebhookServeEventListener` emit a URL THEN the system SHALL log only a redacted URL containing scheme, host, port, and path — with query string and fragment stripped — in all three log call sites

2.3 WHEN `RedactSensitiveHeadersFilter.redactServeEvent` encounters any serialization or redaction error THEN the system SHALL fail closed by rethrowing the exception or returning a minimal safe placeholder JSON, and SHALL elevate the log level to ERROR

#### Major

2.4 WHEN `sqsPublisher.publish` fails inside `WebhookAsyncEventPublisher.transform` THEN the system SHALL only return `webhookDefinition.withUrl(NO_OP_URL)` after a successful publish, and SHALL surface the failure so WireMock can handle it or so it is clearly observable

2.5 WHEN `MOCKNEST_WEBHOOK_QUEUE_URL` is blank at startup THEN the system SHALL either skip instantiation and registration of `WebhookAsyncEventPublisher` or throw a clear startup exception if async mode is required but the URL is absent

2.6 WHEN `RuntimeAsyncHandler.handleRecord` catches an exception THEN the system SHALL distinguish poison-pill failures (JSON parse errors — swallow and skip) from transient delivery failures (network errors, HTTP 5xx — rethrow so SQS retries and eventually routes to the DLQ)

2.7 WHEN CI suite jobs run against a shared deployed MockNest stack THEN the system SHALL serialize suite jobs so each depends on the previous, or SHALL give each suite an isolated deployment or namespace to prevent cross-job interference

2.8 WHEN `test_webhook_delivery` in `post-deploy-test.sh` asserts redaction THEN the system SHALL extract only the journal entry referencing `/mocknest/webhook-callback` and assert on that isolated entry, not the entire journal body

2.9 WHEN the trigger POST in `post-deploy-test.sh` is sent after creating the mapping stub THEN the system SHALL use a bounded retry/poll loop before failing, to tolerate mapping propagation delay

2.10 WHEN `MOCKNEST_WEBHOOK_TIMEOUT_MS` is set in the SAM template THEN the system SHALL derive it from `LambdaTimeout` minus a safety buffer, or SHALL add a constraint or mapping that prevents it from exceeding the Lambda timeout

2.11 WHEN `MockNestWebhookQueue` is configured in the SAM template THEN the system SHALL set `VisibilityTimeout` to `RuntimeAsyncTimeout` plus a buffer (e.g. 60 s), or SHALL wire it to a configurable parameter, to prevent duplicate processing

2.12 WHEN the S3 `ExpireRequestJournalRecords` lifecycle rule is defined THEN the system SHALL include `NoncurrentVersionExpirationInDays: !Ref RequestJournalRetentionDays` to expire noncurrent versions alongside current ones

2.13 WHEN `WebhookConfigTest` tests environment-variable parsing THEN the system SHALL route tests through a `WebhookConfig.from(environment: Map<String, String>)` factory method so that production parsing logic is exercised directly

2.14 WHEN `WebhookHttpClient` uses a per-request timeout THEN the system SHALL either apply `WebhookRequest.timeoutMs` as a per-call OkHttp timeout or SHALL remove the field and stop forwarding it from `RuntimeAsyncHandler`

2.15 WHEN `RuntimeAsyncPrimingHook.primeCredentialsProvider` resolves credentials THEN the system SHALL wrap the call in `withTimeout(1_000)` inside the existing `runBlocking` block to prevent indefinite blocking

2.16 WHEN `RuntimeAsyncSpringContextTest` runs the test "Given async profile active When Spring context loads Then wireMockServer bean is NOT registered by MockNestConfig" THEN the system SHALL assert that `wireMockServer` is absent, matching the test name

#### Minor / Nitpick

2.17 WHEN `WebhookHttpClientTest` is compiled THEN the system SHALL NOT include the unused import `java.net.SocketTimeoutException`

2.18 WHEN `WebhookHttpClientTest` asserts a result type THEN the system SHALL use `assertIs<WebhookResult.Success>(result)` instead of `assertInstanceOf` followed by a cast

2.19 WHEN `WebhookHttpClientTest` asserts headers THEN the system SHALL also assert `content-type` in addition to `x-correlation-id`

2.20 WHEN `WebhookHttpClientTest` runs the test named "empty body is sent" THEN the system SHALL include `assertEquals("", recorded.body.readUtf8())` to verify the body is empty

2.21 WHEN `WebhookHttpClientTest` covers timeout behavior THEN the system SHALL consolidate the duplicate timeout test with the `NetworkErrors` group

2.22 WHEN `WebhookHttpClient` stores `WebhookConfig` but only uses `webhookTimeoutMs` THEN the system SHALL either narrow the dependency to only `webhookTimeoutMs` or add a TODO comment explaining the broader dependency

2.23 WHEN `WebhookConfigPropertyTest` needs to parse headers THEN the system SHALL reuse the production parsing logic rather than duplicating it locally

2.24 WHEN `WebhookConfigPropertyTest` runs the test at lines 42–55 THEN the system SHALL remove the redundant `forEach` loop that is superseded by the subsequent `assertEquals`

2.25 WHEN `WebhookServeEventListenerTest` is compiled THEN the system SHALL NOT include unused imports `ParameterizedTest` and `MethodSource`

2.26 WHEN `WebhookServeEventListenerTest` runs `AfterMatchRedaction` tests THEN the system SHALL add state verification of `capturedHeaders` in addition to asserting no exception

2.27 WHEN `WebhookServeEventListenerPrototypeTest` waits for async behavior THEN the system SHALL use Awaitility or condition polling instead of `Thread.sleep(200)`

2.28 WHEN `WebhookTemplateRenderingPropertyTest` configures the mapper THEN the system SHALL NOT call `setSerializationInclusion(ALWAYS)` in a deserialization-only test

2.29 WHEN `WebhookRedactionPropertyTest` tests non-sensitive header fallback THEN the system SHALL explicitly verify that non-sensitive header values are preserved unchanged

2.30 WHEN `RedactSensitiveHeadersFilter` KDoc describes `redactHeadersInJson` THEN the system SHALL update the comment to accurately state that the implementation parses into a `MutableMap`

2.31 WHEN `RedactSensitiveHeadersFilter` performs the unchecked cast THEN the system SHALL add a `@Suppress` annotation with an explanatory comment documenting the assumption

2.32 WHEN `SqsWebhookPublisher` logs before rethrowing THEN the system SHALL ensure the log entry is not duplicated by the caller, either by removing the pre-rethrow log or by coordinating with the caller's logging

2.33 WHEN `S3RequestJournalStoreTest` asserts redaction THEN the system SHALL use a non-vacuous assertion that would fail if the real (unredacted) value were returned

2.34 WHEN `MockNestConfigWebhookWiringTest` waits for `transform()` to complete THEN the system SHALL NOT use `Thread.sleep(1000)` because `transform()` blocks synchronously

2.35 WHEN `RuntimeAsyncHandlerLocalStackTest` is named as a LocalStack integration test THEN the system SHALL either wire `WebhookHttpClient` against a `MockWebServer` for real HTTP validation or rename the test to reflect its actual scope

2.36 WHEN `RuntimeAsyncHandlerLocalStackTest` calls `assertNotNull(capturedRequest)` THEN the system SHALL capture the non-null return value to avoid a subsequent redundant null check

2.37 WHEN `WebhookAsyncEventPublisherIntegrationTest` waits for async behavior THEN the system SHALL use polling assertions instead of `Thread.sleep(500)`

2.38 WHEN multiple components reference the `"!async"` profile literal THEN the system SHALL extract it to a shared constant or meta-annotation to avoid repetition

2.39 WHEN `MockNestRuntimeAsyncSqsAccess` IAM policy is evaluated THEN the system SHALL include `sqs:ChangeMessageVisibility` to allow visibility timeout extension

2.40 WHEN `MOCKNEST_WEBHOOK_TIMEOUT_MS` and `MOCKNEST_SENSITIVE_HEADERS` are defined in the SAM template THEN the system SHALL expose them as SAM parameters with sensible defaults

2.41 WHEN `README.md` line 49 is linted THEN the system SHALL NOT have a trailing space inside bold emphasis

2.42 WHEN `RuntimeAsyncSpringContextTest` asserts a boolean condition THEN the system SHALL use `kotlin.test.assertTrue()` instead of `assert()`

2.43 WHEN `RuntimeAsyncSpringContextTest` uses mocking THEN the system SHALL align to a single mocking framework (MockK-first, using `@MockkBean`)

2.44 WHEN `S3RequestJournalStore.getAll()` fetches journal entries THEN the system SHALL use `storage.getMany()` for bulk reads instead of N sequential `storage.get()` calls

2.45 WHEN `S3RequestJournalStore.removeLast()` deletes an entry THEN the system SHALL delete the oldest entry (by key sort or timestamp) rather than an arbitrary `keys.first()`

#### Feature Gaps

2.46 WHEN the runtime Lambda and generation Lambda are deployed THEN the system SHALL use separate IAM execution roles so the runtime Lambda does not have Bedrock access

2.47 WHEN the runtime Lambda and generation Lambda are primed THEN the system SHALL use separate Spring profiles and separate priming hook configurations so runtime priming and generation priming are independent and individually configurable

---

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a webhook event is dispatched with a valid SQS queue URL and a reachable callback URL THEN the system SHALL CONTINUE TO publish the `AsyncEvent` to SQS and deliver the outbound HTTP call successfully

3.2 WHEN `RedactSensitiveHeadersFilter` processes a `ServeEvent` with no sensitive headers THEN the system SHALL CONTINUE TO return the serialized JSON unchanged

3.3 WHEN `RedactSensitiveHeadersFilter` processes a `ServeEvent` with sensitive headers present THEN the system SHALL CONTINUE TO replace those header values with `[REDACTED]` in the serialized JSON written to S3

3.4 WHEN `RuntimeAsyncHandler` receives a well-formed SQS message with a valid `AsyncEvent` THEN the system SHALL CONTINUE TO deserialize and dispatch the webhook without error

3.5 WHEN `RuntimeAsyncHandler` receives an SQS message with a malformed JSON body (poison pill) THEN the system SHALL CONTINUE TO log the error and skip the message without rethrowing

3.6 WHEN `WebhookAsyncEventPublisher.transform` is called with a `WebhookDefinition` that has no URL THEN the system SHALL CONTINUE TO return `webhookDefinition.withUrl(NO_OP_URL)` without publishing to SQS

3.7 WHEN `MockNestConfig` wires the `WireMockServer` bean THEN the system SHALL CONTINUE TO register `WebhookAsyncEventPublisher`, `RedactSensitiveHeadersFilter`, `NormalizeMappingBodyFilter`, and `DeleteAllMappingsAndFilesFilter` as extensions

3.8 WHEN the CI `setup` job completes health checks and cleanup THEN the system SHALL CONTINUE TO run the `test-rest`, `test-graphql`, `test-soap`, and `test-webhook` suite jobs (in serialized order after the fix)

3.9 WHEN `S3RequestJournalStore` writes a request journal entry THEN the system SHALL CONTINUE TO store the entry under the `requests/` prefix in S3

3.10 WHEN `MockNestWebhookQueue` receives a message that the RuntimeAsync Lambda fails to process after all retries THEN the system SHALL CONTINUE TO route the message to `MockNestWebhookDLQ`

3.11 WHEN the SAM template is deployed THEN the system SHALL CONTINUE TO create all existing resources: `MockStorage`, `MockNestLambdaRole`, `MockNestRuntimeAsyncRole`, `MockNestDLQ`, `MockNestWebhookDLQ`, `MockNestWebhookQueue`, both API Gateway APIs, all Lambda functions, and all CloudWatch log groups

3.12 WHEN `RuntimeAsyncPrimingHook` runs in a SnapStart environment THEN the system SHALL CONTINUE TO prime JSON deserialization, the HTTP client, and the credentials provider chain

3.13 WHEN `WebhookConfig.fromEnv()` parses environment variables THEN the system SHALL CONTINUE TO produce the same configuration values as before the test refactor

---

## Bug Condition Summary

### Primary Bug Conditions

```pascal
FUNCTION isCapturedHeadersLeakCondition(X)
  INPUT: X of type ServeEvent
  OUTPUT: boolean
  RETURN X triggers beforeResponseSent() AND (no listener OR no URL) AND execution is on a warm Lambda invocation
END FUNCTION

FUNCTION isUrlPiiLogCondition(X)
  INPUT: X of type WebhookDispatchEvent
  OUTPUT: boolean
  RETURN X.url contains query parameters OR fragment
END FUNCTION

FUNCTION isRedactionFailOpenCondition(X)
  INPUT: X of type ServeEvent
  OUTPUT: boolean
  RETURN serialization OR redaction of X throws an exception
END FUNCTION

FUNCTION isSilentDropCondition(X)
  INPUT: X of type WebhookTransformCall
  OUTPUT: boolean
  RETURN sqsPublisher.publish(X) throws an exception
END FUNCTION

FUNCTION isBlankQueueUrlCondition(X)
  INPUT: X of type AppStartupConfig
  OUTPUT: boolean
  RETURN X.webhookQueueUrl is blank OR empty
END FUNCTION

FUNCTION isTransientDeliveryFailureCondition(X)
  INPUT: X of type SQSMessage
  OUTPUT: boolean
  RETURN Json.decodeFromString(X.body) succeeds AND outbound HTTP call fails with network error OR 5xx status
END FUNCTION
```

### Fix Checking Properties

```pascal
// Property: capturedHeaders cleanup always runs
FOR ALL X WHERE isCapturedHeadersLeakCondition(X) DO
  result ← beforeResponseSent'(X)
  ASSERT capturedHeaders does NOT contain X.id after result
END FOR

// Property: URLs are redacted in logs
FOR ALL X WHERE isUrlPiiLogCondition(X) DO
  logOutput ← dispatchWebhook'(X)
  ASSERT logOutput does NOT contain X.url.queryString
  ASSERT logOutput does NOT contain X.url.fragment
END FOR

// Property: Redaction fails closed
FOR ALL X WHERE isRedactionFailOpenCondition(X) DO
  result ← redactServeEvent'(X)
  ASSERT result does NOT equal mapper.writeValueAsString(X)
  ASSERT result is placeholder OR exception is thrown
END FOR

// Property: SQS failure is surfaced
FOR ALL X WHERE isSilentDropCondition(X) DO
  result ← transform'(X)
  ASSERT result is NOT webhookDefinition.withUrl(NO_OP_URL) silently
  ASSERT failure is observable (exception propagated OR error logged with no noop redirect)
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation — non-buggy inputs behave identically before and after fix
FOR ALL X WHERE NOT isCapturedHeadersLeakCondition(X)
             AND NOT isUrlPiiLogCondition(X)
             AND NOT isRedactionFailOpenCondition(X)
             AND NOT isSilentDropCondition(X)
             AND NOT isBlankQueueUrlCondition(X)
             AND NOT isTransientDeliveryFailureCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```
