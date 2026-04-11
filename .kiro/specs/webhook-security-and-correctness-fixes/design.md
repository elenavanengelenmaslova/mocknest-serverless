# Webhook Security and Correctness Fixes — Bugfix Design

## Overview

This design covers a broad set of security, correctness, and code-quality fixes across the webhook and async dispatch subsystem of MockNest Serverless. The bugs span four severity tiers:

- **Critical / Security** — sensitive data leaks (captured-headers side-channel, URL PII in logs, fail-open redaction)
- **Major** — silent event loss, misconfigured infrastructure, flawed test assertions, and transient-vs-poison-pill confusion
- **Minor / Nitpick** — unused imports, misleading test names, timing-dependent sleeps, and minor inefficiencies
- **Feature gaps** — IAM role separation and priming isolation for runtime vs. generation Lambdas

The fix strategy is: minimal, targeted changes to each affected file, validated by the existing test suite plus new tests that exercise the corrected behaviour. No architectural changes are required; all fixes stay within the existing clean-architecture boundaries.

---

## Glossary

- **Bug_Condition (C)**: The set of inputs or states that trigger a defect — formalised as `isBugCondition(X)` pseudocode in each section below.
- **Property (P)**: The desired correct behaviour for inputs where C holds — formalised as `expectedBehavior(result)`.
- **Preservation**: Existing correct behaviour for inputs where C does NOT hold — must be unchanged by the fix.
- **F**: The original (unfixed) function or component.
- **F'**: The fixed function or component.
- **capturedHeaders**: The `ConcurrentHashMap<UUID, HttpHeaders>` in `WebhookServeEventListener` used to pass headers between `beforeResponseSent` and `afterComplete`.
- **NO_OP_URL**: `"http://localhost:0/mocknest-noop"` — the harmless redirect target used by `WebhookAsyncEventPublisher`.
- **AsyncEvent**: The Kotlinx-serializable data class published to SQS by `WebhookAsyncEventPublisher` and consumed by `RuntimeAsyncHandler`.
- **SQS DLQ**: The `MockNestWebhookDLQ` dead-letter queue that receives messages after all retry attempts are exhausted.
- **VisibilityTimeout**: The SQS queue attribute that controls how long a message is hidden from other consumers after being received.
- **SnapStart**: AWS Lambda feature that pre-initialises the JVM and captures a snapshot; `RuntimeAsyncPrimingHook` warms up the snapshot.
- **WebhookConfig**: Data class parsed from environment variables; holds `sensitiveHeaders`, `webhookTimeoutMs`, `asyncTimeoutMs`, and `requestJournalPrefix`.
- **redactServeEvent**: Method on `RedactSensitiveHeadersFilter` that serialises a `ServeEvent` to JSON with sensitive header values replaced by `[REDACTED]`.
- **poison-pill**: An SQS message whose JSON body cannot be deserialised — should be skipped (logged and deleted) rather than retried.
- **transient failure**: A delivery failure caused by a network error or HTTP 5xx — should be retried by SQS.

---

## Bug Details

### Bug Condition

The bugs manifest across six distinct conditions, each formalised below.

**Formal Specification:**

```
FUNCTION isCapturedHeadersLeakCondition(X: ServeEvent): Boolean
  RETURN X triggers beforeResponseSent()
         AND (no listener registered OR no URL in webhook definition)
         AND Lambda invocation is warm (capturedHeaders may contain prior entries)
END FUNCTION

FUNCTION isUrlPiiLogCondition(X: WebhookDispatchEvent): Boolean
  RETURN X.url contains query parameters OR X.url contains fragment
END FUNCTION

FUNCTION isRedactionFailOpenCondition(X: ServeEvent): Boolean
  RETURN serialization OR redaction of X throws an exception inside redactServeEvent()
END FUNCTION

FUNCTION isSilentDropCondition(X: WebhookTransformCall): Boolean
  RETURN sqsPublisher.publish(X) throws an exception
END FUNCTION

FUNCTION isBlankQueueUrlCondition(X: AppStartupConfig): Boolean
  RETURN X.webhookQueueUrl IS blank OR empty
END FUNCTION

FUNCTION isTransientDeliveryFailureCondition(X: SQSMessage): Boolean
  RETURN Json.decodeFromString(X.body) SUCCEEDS
         AND outbound HTTP call FAILS with network error OR HTTP 5xx status
END FUNCTION
```

### Examples

**1.1 — capturedHeaders leak (Critical)**
- Warm Lambda receives a `ServeEvent` with no webhook listener → `beforeResponseSent` hits `return` inside `runCatching` → `capturedHeaders.remove(id)` is skipped → stale entry persists for the next invocation.

**1.2 — URL PII in logs (Critical)**
- Webhook callback URL is `https://api.example.com/hook?token=secret123` → log line emits the full URL including `?token=secret123` → token visible in CloudWatch.

**1.3 — Fail-open redaction (Critical)**
- `mapper.writeValueAsString(event)` throws `JsonProcessingException` inside `redactServeEvent` → `getOrElse` returns the result of `mapper.writeValueAsString(event)` again (which may also throw, or succeed and return unredacted JSON) → sensitive headers written to S3.

**1.4 — Silent SQS drop (Major)**
- `sqsPublisher.publish(...)` throws `SqsException` → `onFailure` logs a warning → `transform` returns `webhookDefinition.withUrl(NO_OP_URL)` → WireMock proceeds as if the webhook was dispatched → event silently lost.

**1.5 — Blank queue URL at startup (Major)**
- `MOCKNEST_WEBHOOK_QUEUE_URL` not set → defaults to `""` → `WebhookAsyncEventPublisher` instantiated with empty `queueUrl` → every webhook event silently dropped with no startup warning.

**1.6 — Transient failure swallowed (Major)**
- `RuntimeAsyncHandler.handleRecord` wraps everything in `runCatching` → network timeout on outbound HTTP call → `onFailure` logs and returns normally → SQS does not retry → message deleted from queue → event lost.

---

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- A webhook event with a valid SQS queue URL and reachable callback URL MUST continue to be published to SQS and delivered via the async Lambda.
- `RedactSensitiveHeadersFilter` processing a `ServeEvent` with no sensitive headers MUST continue to return the serialised JSON unchanged.
- `RedactSensitiveHeadersFilter` processing a `ServeEvent` with sensitive headers MUST continue to replace those values with `[REDACTED]`.
- `RuntimeAsyncHandler` receiving a well-formed SQS message MUST continue to deserialise and dispatch the webhook without error.
- `RuntimeAsyncHandler` receiving a malformed JSON body (poison pill) MUST continue to log and skip the message without rethrowing.
- `WebhookAsyncEventPublisher.transform` called with a `WebhookDefinition` that has no URL MUST continue to return `webhookDefinition.withUrl(NO_OP_URL)` without publishing to SQS.
- `MockNestConfig` MUST continue to register `WebhookAsyncEventPublisher`, `RedactSensitiveHeadersFilter`, `NormalizeMappingBodyFilter`, and `DeleteAllMappingsAndFilesFilter` as WireMock extensions.
- The SAM template MUST continue to create all existing resources after infrastructure fixes.
- `RuntimeAsyncPrimingHook` MUST continue to prime JSON deserialisation, the HTTP client, and the credentials provider chain.

**Scope:**
All inputs that do NOT satisfy any of the six bug conditions above are unaffected by this fix. This includes:
- Webhook events with valid URLs and reachable SQS queues
- `ServeEvent` objects with no sensitive headers
- SQS messages with well-formed JSON bodies and reachable callback URLs
- All non-webhook Lambda invocations (generation, runtime request handling)

---

## Hypothesized Root Cause

### Critical / Security

1. **Non-local return inside `runCatching` (1.1)**: `beforeResponseSent` uses a bare `return` inside the `runCatching` lambda. In Kotlin, `return` inside a lambda passed to an inline function performs a non-local return from the enclosing function, bypassing the `finally`-equivalent cleanup (`capturedHeaders.remove`).

2. **Raw URL logged without sanitisation (1.2)**: The three log call sites in `WebhookServeEventListener` pass the raw `url` string directly to the logger. No URL sanitisation utility exists.

3. **`getOrElse` fallback calls the same failing operation (1.3)**: `redactServeEvent` catches a serialisation error and falls back to `mapper.writeValueAsString(event)` — the same call that just failed. If it succeeds on the second attempt it returns unredacted JSON; if it fails again the exception propagates. Either way the fail-closed contract is violated.

### Major

4. **`onFailure` swallows SQS publish exception (1.4)**: `WebhookAsyncEventPublisher.transform` wraps the entire publish in `runCatching { ... }.onFailure { ... }` and always returns `webhookDefinition.withUrl(NO_OP_URL)` regardless of outcome.

5. **No guard on blank `queueUrl` at startup (1.5)**: `MockNestConfig.wireMockServer` instantiates `WebhookAsyncEventPublisher` unconditionally, even when `MOCKNEST_WEBHOOK_QUEUE_URL` is blank.

6. **Uniform `runCatching` in `handleRecord` (1.6)**: `RuntimeAsyncHandler.handleRecord` wraps both JSON parsing and HTTP dispatch in a single `runCatching`, treating all failures identically. Transient delivery failures should be rethrown so SQS can retry.

7. **Parallel CI jobs mutate shared WireMock state (1.7)**: `test-rest`, `test-graphql`, `test-soap`, and `test-webhook` all run with `needs: setup` but no dependency on each other, so they execute in parallel against the same deployed stack.

8. **Journal scan not scoped to current callback (1.8)**: `test_webhook_delivery` scans the entire journal body for `x-api-key` and `[REDACTED]`, which can match stale entries from prior test runs.

9. **No retry after stub registration (1.9)**: The trigger POST is sent immediately after `POST /__admin/mappings` with no retry loop, risking a 404 if mapping propagation is delayed.

10. **`MOCKNEST_WEBHOOK_TIMEOUT_MS` hardcoded to 10 000 ms (1.10)**: `LambdaTimeout` defaults to 30 s but can be set as low as 3 s. The hardcoded 10 000 ms can exceed the Lambda timeout.

11. **`VisibilityTimeout` equals `RuntimeAsyncTimeout` (1.11)**: Default SQS `VisibilityTimeout` is 30 s, which equals the default `RuntimeAsyncTimeout`. If the Lambda takes the full 30 s, the message becomes visible again before deletion, risking duplicate processing.

12. **S3 lifecycle rule missing `NoncurrentVersionExpirationInDays` (1.12)**: The `ExpireRequestJournalRecords` rule expires current versions but not noncurrent versions, causing indefinite retention of old object versions.

13. **`WebhookConfigTest` duplicates production parsing logic (1.13)**: Tests use a local `buildConfig()` that mirrors `WebhookConfig.fromEnv()` rather than calling it directly, allowing the two to drift silently.

14. **`WebhookRequest.timeoutMs` is a dead field (1.14)**: `RuntimeAsyncHandler` sets `timeoutMs = webhookConfig.asyncTimeoutMs` on `WebhookRequest`, but `WebhookHttpClient` always uses `webhookConfig.webhookTimeoutMs`, ignoring the per-request value.

15. **`primeCredentialsProvider` blocks indefinitely (1.15)**: `DefaultChainCredentialsProvider().resolve()` inside `runBlocking` has no timeout, so a slow IMDS or container credentials endpoint can block SnapStart snapshot creation indefinitely.

16. **Test name and assertion contradict each other (1.16)**: `RuntimeAsyncSpringContextTest` test named "wireMockServer bean is NOT registered" asserts `directCallHttpServerFactory` is absent instead of `wireMockServer`.

---

## Correctness Properties

Property 1: Bug Condition — capturedHeaders cleanup always executes

_For any_ `ServeEvent` X where `isCapturedHeadersLeakCondition(X)` holds (no listener or no URL), the fixed `beforeResponseSent'(X)` SHALL ensure `capturedHeaders` does NOT contain `X.id` after the method returns, regardless of the early-exit path taken.

**Validates: Requirements 2.1**

---

Property 2: Bug Condition — URL PII stripped from logs

_For any_ webhook dispatch event X where `isUrlPiiLogCondition(X)` holds (URL contains query string or fragment), the fixed dispatch log lines SHALL emit only the scheme, host, port, and path of `X.url`, with query string and fragment omitted.

**Validates: Requirements 2.2**

---

Property 3: Bug Condition — Redaction fails closed

_For any_ `ServeEvent` X where `isRedactionFailOpenCondition(X)` holds (serialisation or redaction throws), the fixed `redactServeEvent'(X)` SHALL NOT return the unredacted JSON. It SHALL either rethrow the exception or return a minimal safe placeholder, and SHALL log at ERROR level.

**Validates: Requirements 2.3**

---

Property 4: Bug Condition — SQS publish failure is observable

_For any_ webhook transform call X where `isSilentDropCondition(X)` holds (SQS publish throws), the fixed `transform'(X)` SHALL NOT silently return `webhookDefinition.withUrl(NO_OP_URL)`. The failure SHALL be observable — either by propagating the exception or by logging at ERROR level and NOT redirecting to the no-op URL.

**Validates: Requirements 2.4**

---

Property 5: Bug Condition — Blank queue URL prevented at startup

_For any_ startup config X where `isBlankQueueUrlCondition(X)` holds (queue URL is blank), the fixed startup SHALL either skip registration of `WebhookAsyncEventPublisher` or throw a clear startup exception, and SHALL NOT silently instantiate the publisher with a blank URL.

**Validates: Requirements 2.5**

---

Property 6: Bug Condition — Transient delivery failures trigger SQS retry

_For any_ SQS message X where `isTransientDeliveryFailureCondition(X)` holds (valid JSON, but HTTP delivery fails with network error or 5xx), the fixed `handleRecord'(X)` SHALL rethrow the exception so SQS retries the message and eventually routes it to the DLQ.

**Validates: Requirements 2.6**

---

Property 7: Preservation — Non-buggy inputs behave identically before and after fix

_For any_ input X where none of the six bug conditions hold, the fixed system SHALL produce the same observable result as the original system: same SQS publish behaviour, same redaction output, same HTTP dispatch outcome, same Spring context wiring.

**Validates: Requirements 3.1–3.13**

---

## Fix Implementation

### Changes Required

#### Group A — Critical / Security (Kotlin source)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/WebhookServeEventListener.kt`

*(Note: this file does not yet exist in the repository — it is referenced in the requirements as the location of `beforeResponseSent`. If the listener logic lives elsewhere, the same fix applies.)*

**Fix 1.1 — capturedHeaders cleanup**
- Replace bare `return` inside `runCatching` with `return@runCatching` (or restructure with `try/finally`) so `capturedHeaders.remove(serveEvent.id)` always executes.

**Fix 1.2 — URL redaction in logs**
- Extract a `fun redactUrl(url: String): String` utility that strips query string and fragment using `java.net.URI`.
- Apply it at all three log call sites (dispatch, success, failure).

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/RedactSensitiveHeadersFilter.kt`

**Fix 1.3 — Fail-closed redaction**
- In `redactServeEvent`, change the `getOrElse` fallback from `mapper.writeValueAsString(event)` to either:
  - Rethrowing the exception: `getOrElse { e -> logger.error(e) { "..." }; throw e }`, or
  - Returning a safe placeholder: `"""{"id":"${event.id}","redactionError":true}"""`.
- Elevate the log level from WARN to ERROR.

#### Group B — Major (Kotlin source)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/WebhookAsyncEventPublisher.kt`

**Fix 1.4 — Surface SQS publish failure**
- Remove the `runCatching` wrapper around the publish call inside `transform`.
- Let the exception propagate so WireMock can observe it.
- Keep the no-op redirect only on the success path.

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/config/MockNestConfig.kt`

**Fix 1.5 — Guard blank queue URL**
- In `wireMockServer`, add a check: if `webhookQueueUrl.isBlank()`, log a WARN and skip instantiation of `WebhookAsyncEventPublisher` (do not register it as an extension).

**File**: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/runtimeasync/RuntimeAsyncHandler.kt`

**Fix 1.6 — Distinguish poison-pill from transient failure**
- Split `handleRecord` into two `runCatching` blocks:
  1. JSON parsing — catch `SerializationException`; log ERROR and return (poison-pill skip).
  2. HTTP dispatch — do NOT catch; let network/5xx exceptions propagate so SQS retries.

**Fix 1.14 — Remove dead `timeoutMs` field or wire it**
- Either apply `request.timeoutMs` as a per-call OkHttp timeout in `WebhookHttpClient`, or remove `timeoutMs` from `WebhookRequest` and stop forwarding it from `RuntimeAsyncHandler`.
- Chosen approach: remove the field from `WebhookRequest` and the forwarding in `RuntimeAsyncHandler` (simpler, no behaviour change).

**Fix 1.15 — Timeout on credential resolution**
- In `RuntimeAsyncPrimingHook.primeCredentialsProvider`, wrap `DefaultChainCredentialsProvider().resolve()` in `withTimeout(1_000)` inside the existing `runBlocking` block.

#### Group C — Major (Infrastructure / CI)

**File**: `.github/workflows/workflow-integration-test.yml`

**Fix 1.7 — Serialise CI suite jobs**
- Add `needs: [setup, test-rest]` to `test-graphql`, `needs: [setup, test-rest, test-graphql]` to `test-soap`, and `needs: [setup, test-rest, test-graphql, test-soap]` to `test-webhook` so suites run sequentially.

**File**: `scripts/post-deploy-test.sh`

**Fix 1.8 — Scope journal assertion to current callback**
- In `test_webhook_delivery`, extract only the journal entry whose URL path matches `/webhook-callback` (already done in the current implementation via `grep -q 'webhook-callback'`). Ensure the redaction assertion operates on `callback_record` (the isolated entry), not the full journal body.

**Fix 1.9 — Retry trigger POST**
- After registering the trigger stub, add a bounded retry loop (e.g. 3 attempts, 1 s apart) before the trigger POST to tolerate mapping propagation delay.

**File**: `deployment/aws/sam/template.yaml`

**Fix 1.10 — Derive `MOCKNEST_WEBHOOK_TIMEOUT_MS` from `LambdaTimeout`**
- Add a SAM parameter `WebhookTimeoutMs` with a default derived from `LambdaTimeout` minus a 5 s buffer, or use a `Mappings` / `!Sub` expression.
- Alternatively, expose `WebhookTimeoutMs` as a SAM parameter with a sensible default (e.g. 25 000 ms) and add a constraint that it must be less than `LambdaTimeout * 1000`.

**Fix 1.11 — Set `VisibilityTimeout` to `RuntimeAsyncTimeout + buffer`**
- Set `VisibilityTimeout: !Sub "${RuntimeAsyncTimeout}0"` is not valid; instead use a `Mappings` block or a dedicated `WebhookQueueVisibilityTimeout` parameter defaulting to 60 s (30 s timeout + 30 s buffer).

**Fix 1.12 — Add `NoncurrentVersionExpirationInDays` to lifecycle rule**
- In the `ExpireRequestJournalRecords` lifecycle rule, add:
  ```yaml
  NoncurrentVersionExpirationInDays: !Ref RequestJournalRetentionDays
  ```

**Fix 1.39 — Add `sqs:ChangeMessageVisibility` to `MockNestRuntimeAsyncSqsAccess`**
- Add `sqs:ChangeMessageVisibility` to the SQS IAM policy for the RuntimeAsync role.

**Fix 1.40 — Expose `MOCKNEST_WEBHOOK_TIMEOUT_MS` and `MOCKNEST_SENSITIVE_HEADERS` as SAM parameters**
- Add `WebhookTimeoutMs` and `SensitiveHeaders` SAM parameters with sensible defaults.
- Reference them in the Lambda `Environment.Variables` blocks.

**Fix 1.46 — Separate IAM roles for runtime and generation Lambdas**
- Create a new `MockNestGenerationLambdaRole` that includes Bedrock access.
- Update `MockNestLambdaRole` (used by the runtime Lambda) to remove `MockNestBedrockAccess`.
- Wire `MockNestGenerationFunction` and `MockNestGenerationFunctionIam` to `MockNestGenerationLambdaRole`.

#### Group D — Minor / Nitpick (Kotlin test source)

The following are targeted, low-risk test improvements. Each is a single-file change:

- **1.16**: Fix `RuntimeAsyncSpringContextTest` assertion to check `wireMockServer` absence (not `directCallHttpServerFactory`).
- **1.17**: Remove unused import `java.net.SocketTimeoutException` from `WebhookHttpClientTest`.
- **1.18**: Replace `assertInstanceOf` + cast with `assertIs<WebhookResult.Success>(result)`.
- **1.25**: Remove unused imports `ParameterizedTest` and `MethodSource` from `WebhookServeEventListenerTest`.
- **1.27**: Replace `Thread.sleep(200)` with Awaitility polling in `WebhookServeEventListenerPrototypeTest`.
- **1.30**: Update `RedactSensitiveHeadersFilter` KDoc to accurately describe the `MutableMap` parsing.
- **1.34**: Remove `Thread.sleep(1000)` from `MockNestConfigWebhookWiringTest` (transform is synchronous).
- **1.37**: Replace `Thread.sleep(500)` with polling assertions in `WebhookAsyncEventPublisherIntegrationTest`.
- **1.42**: Replace `assert()` with `kotlin.test.assertTrue()` in `RuntimeAsyncSpringContextTest`.

---

## Testing Strategy

### Validation Approach

Testing follows a two-phase approach:

1. **Exploratory / fix-checking**: Write tests that exercise the bug condition on the UNFIXED code to confirm the root cause, then verify they pass on the fixed code.
2. **Preservation checking**: Run the full existing test suite after each fix to confirm no regressions.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate each bug BEFORE the fix. Confirm or refute root cause hypotheses.

**Test Cases**:

1. **capturedHeaders leak test** — Call `beforeResponseSent` with a `ServeEvent` that has no webhook listener; assert `capturedHeaders` is empty after the call. Will fail on unfixed code (entry remains).
2. **URL PII log test** — Call the dispatch log path with a URL containing `?token=secret`; capture log output and assert the query string is absent. Will fail on unfixed code.
3. **Fail-open redaction test** — Mock `mapper.writeValueAsString` to throw on first call; call `redactServeEvent`; assert the return value is NOT the unredacted JSON. Will fail on unfixed code.
4. **Silent SQS drop test** — Wire a failing `SqsPublisherInterface`; call `transform`; assert the returned definition URL is NOT `NO_OP_URL` (or that an exception is thrown). Will fail on unfixed code.
5. **Blank queue URL test** — Instantiate `MockNestConfig` with a blank `webhookQueueUrl`; assert `WebhookAsyncEventPublisher` is NOT registered as a WireMock extension. Will fail on unfixed code.
6. **Transient failure rethrow test** — Wire `WebhookHttpClientInterface` to throw `IOException`; call `handleRecord` with a valid JSON body; assert the exception propagates. Will fail on unfixed code.

**Expected Counterexamples**:
- `capturedHeaders` contains the `ServeEvent` ID after `beforeResponseSent` returns early.
- Log output contains the full URL including query string.
- `redactServeEvent` returns the full unredacted JSON when serialisation fails.
- `transform` returns `NO_OP_URL` even when SQS publish throws.
- `WebhookAsyncEventPublisher` is registered even with a blank queue URL.
- `handleRecord` swallows `IOException` and returns normally.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behaviour.

**Pseudocode:**
```
FOR ALL X WHERE isCapturedHeadersLeakCondition(X) DO
  beforeResponseSent'(X)
  ASSERT capturedHeaders does NOT contain X.id
END FOR

FOR ALL X WHERE isUrlPiiLogCondition(X) DO
  logOutput ← dispatchWebhook'(X)
  ASSERT logOutput does NOT contain X.url.queryString
  ASSERT logOutput does NOT contain X.url.fragment
END FOR

FOR ALL X WHERE isRedactionFailOpenCondition(X) DO
  result ← redactServeEvent'(X)
  ASSERT result IS NOT unredacted JSON
  ASSERT result IS placeholder OR exception thrown
END FOR

FOR ALL X WHERE isSilentDropCondition(X) DO
  ASSERT transform'(X) throws OR logs ERROR
  ASSERT returned URL IS NOT NO_OP_URL on failure path
END FOR

FOR ALL X WHERE isBlankQueueUrlCondition(X) DO
  ASSERT WebhookAsyncEventPublisher NOT registered
END FOR

FOR ALL X WHERE isTransientDeliveryFailureCondition(X) DO
  ASSERT handleRecord'(X) throws
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed system produces the same result as the original.

**Pseudocode:**
```
FOR ALL X WHERE NOT isCapturedHeadersLeakCondition(X)
             AND NOT isUrlPiiLogCondition(X)
             AND NOT isRedactionFailOpenCondition(X)
             AND NOT isSilentDropCondition(X)
             AND NOT isBlankQueueUrlCondition(X)
             AND NOT isTransientDeliveryFailureCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```

**Testing Approach**: Property-based testing with `@ParameterizedTest` is recommended for preservation checking because:
- It generates many test cases automatically across the input domain.
- It catches edge cases that manual unit tests might miss.
- It provides strong guarantees that behaviour is unchanged for all non-buggy inputs.

**Test Cases**:
1. **Valid webhook dispatch preservation** — Verify that a webhook with a valid URL and reachable SQS queue continues to publish and deliver correctly after all fixes.
2. **Redaction preservation (no sensitive headers)** — Verify that `ServeEvent` objects with no sensitive headers continue to return unchanged JSON.
3. **Redaction preservation (sensitive headers present)** — Verify that sensitive headers continue to be replaced with `[REDACTED]`.
4. **Poison-pill preservation** — Verify that malformed JSON SQS messages continue to be skipped without rethrowing.
5. **WireMock extension registration preservation** — Verify that all four extensions continue to be registered by `MockNestConfig`.

### Unit Tests

- Test `beforeResponseSent` cleanup path: assert `capturedHeaders` is empty after early exit.
- Test `redactUrl` utility: assert query string and fragment are stripped for various URL shapes.
- Test `redactServeEvent` fail-closed: assert placeholder or exception when serialisation fails.
- Test `transform` with failing SQS publisher: assert exception propagates or ERROR is logged.
- Test `MockNestConfig` with blank queue URL: assert `WebhookAsyncEventPublisher` not registered.
- Test `handleRecord` with `IOException`: assert exception propagates (transient failure).
- Test `handleRecord` with malformed JSON: assert no exception (poison-pill skip).
- Test `primeCredentialsProvider` with slow credential source: assert completes within 2 s.
- Test `RuntimeAsyncSpringContextTest` assertion fix: assert `wireMockServer` is absent.

### Property-Based Tests

- Generate random URLs (with and without query strings/fragments) and verify `redactUrl` always strips query and fragment.
- Generate random `ServeEvent` objects with random header sets and verify redaction output is consistent before and after fix.
- Generate random `AsyncEvent` JSON payloads and verify `handleRecord` correctly distinguishes poison-pill (invalid JSON) from valid events.

### Integration Tests

- Full webhook dispatch flow: register stub → trigger → poll SQS → verify delivery (existing `WebhookAsyncDispatchIntegrationTest`).
- SAM template validation: `sam validate` passes after all template changes.
- CI workflow serialisation: verify `test-graphql` `needs` includes `test-rest` in the updated YAML.
- Shell script syntax: `bash -n scripts/post-deploy-test.sh` passes after script changes.
