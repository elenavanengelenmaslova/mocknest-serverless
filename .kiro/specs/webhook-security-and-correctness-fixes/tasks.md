# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Critical Security Bugs (capturedHeaders leak, URL PII in logs, fail-open redaction, silent SQS drop, blank queue URL, transient failure swallowed)
  - **CRITICAL**: Write these property-based tests BEFORE implementing any fix — failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **GOAL**: Surface counterexamples that demonstrate each bug exists
  - **Scoped PBT Approach**: Scope each property to the concrete failing case(s) for reproducibility
  - Test 1.1 — capturedHeaders leak: call `beforeResponseSent` with a `ServeEvent` that has no webhook listener; assert `capturedHeaders` is empty after the call. Expect FAIL on unfixed code (entry remains due to non-local return bypassing cleanup).
  - Test 1.2 — URL PII in logs: call the dispatch log path with a URL containing `?token=secret`; capture log output and assert the query string is absent. Expect FAIL on unfixed code (raw URL logged).
  - Test 1.3 — Fail-open redaction: mock `mapper.writeValueAsString` to throw on first call; call `redactServeEvent`; assert the return value is NOT the unredacted JSON. Expect FAIL on unfixed code (`getOrElse` retries the same failing call).
  - Test 1.4 — Silent SQS drop: wire a failing `SqsPublisherInterface`; call `transform`; assert the returned definition URL is NOT `NO_OP_URL` or that an exception is thrown. Expect FAIL on unfixed code (exception swallowed, NO_OP_URL returned silently).
  - Test 1.5 — Blank queue URL: instantiate `MockNestConfig` with a blank `webhookQueueUrl`; assert `WebhookAsyncEventPublisher` is NOT registered as a WireMock extension. Expect FAIL on unfixed code (publisher registered unconditionally).
  - Test 1.6 — Transient failure rethrow: wire `WebhookHttpClientInterface` to throw `IOException`; call `handleRecord` with a valid JSON body; assert the exception propagates. Expect FAIL on unfixed code (exception swallowed by uniform `runCatching`).
  - Run all tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist)
  - Document counterexamples found to understand root cause
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-buggy webhook and redaction behavior
  - **IMPORTANT**: Follow observation-first methodology — observe UNFIXED code behavior for non-buggy inputs first
  - Observe: `WebhookAsyncEventPublisher.transform` with a valid URL and working SQS publisher returns `webhookDefinition.withUrl(NO_OP_URL)` and publishes successfully
  - Observe: `RedactSensitiveHeadersFilter.redactServeEvent` with no sensitive headers returns unchanged JSON
  - Observe: `RedactSensitiveHeadersFilter.redactServeEvent` with sensitive headers returns JSON with `[REDACTED]` values
  - Observe: `RuntimeAsyncHandler.handleRecord` with malformed JSON logs error and returns normally (poison-pill skip)
  - Observe: `RuntimeAsyncHandler.handleRecord` with a well-formed `AsyncEvent` dispatches the webhook without error
  - Observe: `MockNestConfig` registers all four WireMock extensions when queue URL is non-blank
  - Write property-based tests capturing these observed behaviors from Preservation Requirements in design (Requirements 3.1–3.13)
  - For all non-buggy inputs: `F(X) = F'(X)` — same SQS publish behavior, same redaction output, same HTTP dispatch outcome, same Spring context wiring
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 3. Fix Group A — Critical / Security (Kotlin source)

  - [x] 3.1 Fix capturedHeaders non-local return in WebhookServeEventListener (Bug 1.1)
    - In `beforeResponseSent`, replace bare `return` inside the `runCatching` lambda with `return@runCatching` so the non-local return no longer bypasses `capturedHeaders.remove(serveEvent.id)`
    - Alternatively restructure with `try/finally` to guarantee cleanup regardless of exit path
    - Ensure `capturedHeaders.remove(serveEvent.id)` executes on every code path including early exits
    - _Bug_Condition: isCapturedHeadersLeakCondition(X) — no listener or no URL, warm Lambda invocation_
    - _Expected_Behavior: capturedHeaders does NOT contain X.id after beforeResponseSent returns_
    - _Preservation: ServeEvents with a valid listener and URL continue to be processed normally_
    - _Requirements: 2.1_

  - [x] 3.2 Fix URL PII redaction in logs in WebhookServeEventListener (Bug 1.2)
    - Extract a `fun redactUrl(url: String): String` utility using `java.net.URI` that strips query string and fragment, returning only scheme + host + port + path
    - Apply `redactUrl(url)` at all three log call sites in `WebhookServeEventListener` (dispatch, success, failure)
    - Write unit tests for `redactUrl` covering: URL with query string, URL with fragment, URL with both, plain URL with no query/fragment, URL with port
    - _Bug_Condition: isUrlPiiLogCondition(X) — X.url contains query parameters or fragment_
    - _Expected_Behavior: log lines emit only scheme+host+port+path, query string and fragment omitted_
    - _Preservation: URLs without query strings or fragments are logged unchanged_
    - _Requirements: 2.2_

  - [x] 3.3 Fix fail-closed redaction in RedactSensitiveHeadersFilter (Bug 1.3)
    - In `redactServeEvent`, change the `getOrElse` fallback from `mapper.writeValueAsString(event)` (the same failing call) to either rethrowing the exception with `logger.error` at ERROR level, or returning a minimal safe placeholder `"""{"id":"${event.id}","redactionError":true}"""`
    - Elevate the log level from WARN to ERROR for serialization/redaction failures
    - Update KDoc on `redactServeEvent` to accurately describe the fail-closed contract
    - _Bug_Condition: isRedactionFailOpenCondition(X) — serialization or redaction throws_
    - _Expected_Behavior: result is NOT unredacted JSON; either exception rethrown or safe placeholder returned; ERROR logged_
    - _Preservation: ServeEvents that serialize and redact successfully continue to return correct redacted JSON_
    - _Requirements: 2.3_

  - [x] 3.4 Run verification after Group A fixes
    - Run `./gradlew :software:application:test`
    - Confirm exit code 0 and all tests pass before proceeding to Group B

- [x] 4. Verify bug condition exploration test now passes for Group A bugs
  - **Property 1: Expected Behavior** - Critical Security Bugs (Group A subset: 1.1, 1.2, 1.3)
  - **IMPORTANT**: Re-run the SAME tests from task 1 for bugs 1.1, 1.2, 1.3 — do NOT write new tests
  - The tests from task 1 encode the expected behavior
  - Run bug condition exploration tests 1.1, 1.2, 1.3 from step 1
  - **EXPECTED OUTCOME**: Tests PASS (confirms Group A bugs are fixed)
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 5. Fix Group B — Major (Kotlin source)

  - [x] 5.1 Surface SQS publish failures in WebhookAsyncEventPublisher (Bug 1.4)
    - Remove the `runCatching` wrapper around the SQS publish call inside `transform`
    - Let the exception propagate so WireMock can observe it
    - Keep `webhookDefinition.withUrl(NO_OP_URL)` only on the success path (after a successful publish)
    - The `onFailure` log-and-swallow pattern must be removed; failure must be observable
    - _Bug_Condition: isSilentDropCondition(X) — sqsPublisher.publish(X) throws_
    - _Expected_Behavior: exception propagates OR ERROR logged and NO_OP_URL NOT returned on failure path_
    - _Preservation: successful SQS publish continues to redirect to NO_OP_URL_
    - _Requirements: 2.4_

  - [x] 5.2 Guard blank queue URL at startup in MockNestConfig (Bug 1.5)
    - In `wireMockServer`, add a check: if `webhookQueueUrl.isBlank()`, log a WARN and skip instantiation and registration of `WebhookAsyncEventPublisher` (do not add it to the extensions list)
    - Ensure the remaining three extensions (`NormalizeMappingBodyFilter`, `DeleteAllMappingsAndFilesFilter`, `RedactSensitiveHeadersFilter`) are still registered regardless
    - _Bug_Condition: isBlankQueueUrlCondition(X) — X.webhookQueueUrl is blank or empty_
    - _Expected_Behavior: WebhookAsyncEventPublisher NOT registered; WARN logged; no startup exception_
    - _Preservation: non-blank queue URL continues to register all four extensions_
    - _Requirements: 2.5_

  - [x] 5.3 Distinguish poison-pill from transient failure in RuntimeAsyncHandler (Bug 1.6)
    - Split `handleRecord` into two separate `runCatching` blocks:
      1. JSON parsing block — catch `SerializationException` only; log ERROR and return (poison-pill skip, no rethrow)
      2. HTTP dispatch block — do NOT catch; let network errors and HTTP 5xx exceptions propagate so SQS retries and eventually routes to DLQ
    - Remove the single uniform `runCatching` that treats all failures identically
    - _Bug_Condition: isTransientDeliveryFailureCondition(X) — valid JSON, HTTP delivery fails with network error or 5xx_
    - _Expected_Behavior: handleRecord rethrows on transient failure; SQS retries; message eventually routed to DLQ_
    - _Preservation: malformed JSON (poison-pill) continues to be logged and skipped without rethrowing_
    - _Requirements: 2.6_

  - [x] 5.4 Remove dead timeoutMs field from WebhookRequest (Bug 1.14)
    - Remove `timeoutMs` field from `WebhookRequest` data class
    - Remove the `timeoutMs = webhookConfig.asyncTimeoutMs` forwarding in `RuntimeAsyncHandler.dispatchWebhook`
    - Verify `WebhookHttpClient` continues to use `webhookConfig.webhookTimeoutMs` for all calls (no behavior change)
    - Update `RuntimeAsyncPrimingHook.primeHttpClient` to remove `timeoutMs` from the priming `WebhookRequest`
    - _Requirements: 2.14_

  - [x] 5.5 Add timeout to credential resolution in RuntimeAsyncPrimingHook (Bug 1.15)
    - In `primeCredentialsProvider`, wrap `DefaultChainCredentialsProvider().resolve()` in `withTimeout(1_000)` inside the existing `runBlocking` block
    - Ensure the `runCatching` wrapper still catches `TimeoutCancellationException` so a slow credential source never blocks SnapStart snapshot creation
    - _Requirements: 2.15_

  - [x] 5.6 Run verification after Group B fixes
    - Run `./gradlew :software:infra:aws:runtime:test`
    - Run `./gradlew :software:application:test`
    - Confirm exit code 0 and all tests pass before proceeding to Group C

  - [x] 5.7 Run full build verification after Groups A+B
    - Run `./gradlew clean test`
    - Confirm exit code 0 before proceeding to Group C

- [x] 6. Verify bug condition exploration test now passes for Group B bugs
  - **Property 1: Expected Behavior** - Major Kotlin Bugs (Group B subset: 1.4, 1.5, 1.6)
  - **IMPORTANT**: Re-run the SAME tests from task 1 for bugs 1.4, 1.5, 1.6 — do NOT write new tests
  - Run bug condition exploration tests 1.4, 1.5, 1.6 from step 1
  - **EXPECTED OUTCOME**: Tests PASS (confirms Group B bugs are fixed)
  - _Requirements: 2.4, 2.5, 2.6_

- [x] 7. Verify preservation tests still pass after Groups A+B
  - **Property 2: Preservation** - Non-buggy webhook and redaction behavior
  - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
  - Run all preservation property tests from step 2
  - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions from Group A and B fixes)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 8. Fix Group C — Major Infrastructure / CI

  - [x] 8.1 Serialise CI suite jobs in workflow-integration-test.yml (Bug 1.7)
    - Add `needs: [setup, test-rest]` to `test-graphql`
    - Add `needs: [setup, test-rest, test-graphql]` to `test-soap`
    - Add `needs: [setup, test-rest, test-graphql, test-soap]` to `test-webhook`
    - This ensures suites run sequentially and do not mutate shared WireMock state concurrently
    - _Requirements: 2.7_

  - [x] 8.2 Scope journal assertion to current callback in post-deploy-test.sh (Bug 1.8)
    - In `test_webhook_delivery`, ensure the redaction assertion operates on `callback_record` (the isolated entry matching `/webhook-callback`), not the full journal body
    - Verify the existing `grep -q 'webhook-callback'` filter is applied before any redaction assertions
    - Add a comment explaining the scoping to prevent future regression
    - Run `bash -n scripts/post-deploy-test.sh` to verify shell syntax
    - _Requirements: 2.8_

  - [x] 8.3 Add retry loop for trigger POST in post-deploy-test.sh (Bug 1.9)
    - After registering the trigger stub, add a bounded retry loop (3 attempts, 1 s apart) before the trigger POST to tolerate mapping propagation delay
    - The retry should check for a non-404 response before proceeding
    - Run `bash -n scripts/post-deploy-test.sh` to verify shell syntax
    - _Requirements: 2.9_

  - [x] 8.4 Checkpoint — verify shell script syntax
    - Run `bash -n scripts/post-deploy-test.sh`
    - Confirm exit code 0

  - [x] 8.5 Fix webhook timeout derivation in SAM template (Bug 1.10)
    - Add a `WebhookTimeoutMs` SAM parameter with a sensible default (e.g. `25000`) and a description noting it must be less than `LambdaTimeout * 1000`
    - Replace the hardcoded `"10000"` value for `MOCKNEST_WEBHOOK_TIMEOUT_MS` in both `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam` `Environment.Variables` with `!Ref WebhookTimeoutMs`
    - _Requirements: 2.10_

  - [x] 8.6 Set SQS VisibilityTimeout to RuntimeAsyncTimeout + buffer (Bug 1.11)
    - Add a `WebhookQueueVisibilityTimeout` SAM parameter defaulting to `60` (seconds) with a description explaining it should be `RuntimeAsyncTimeout` plus a buffer to prevent duplicate processing
    - Set `VisibilityTimeout: !Ref WebhookQueueVisibilityTimeout` on `MockNestWebhookQueue`
    - _Requirements: 2.11_

  - [x] 8.7 Add NoncurrentVersionExpirationInDays to S3 lifecycle rule (Bug 1.12)
    - In the `ExpireRequestJournalRecords` lifecycle rule on `MockStorage`, add:
      ```yaml
      NoncurrentVersionExpirationInDays: !Ref RequestJournalRetentionDays
      ```
    - This ensures noncurrent versions of request journal objects expire alongside current versions
    - _Requirements: 2.12_

  - [x] 8.8 Add sqs:ChangeMessageVisibility to MockNestRuntimeAsyncSqsAccess IAM policy (Bug 1.39)
    - Add `sqs:ChangeMessageVisibility` to the `Action` list in `MockNestRuntimeAsyncSqsAccess` policy on `MockNestRuntimeAsyncRole`
    - _Requirements: 2.39_

  - [x] 8.9 Expose MOCKNEST_SENSITIVE_HEADERS as a SAM parameter (Bug 1.40)
    - Add a `SensitiveHeaders` SAM parameter with default `"x-api-key,authorization,proxy-authorization,x-amz-security-token"`
    - Replace the hardcoded `MOCKNEST_SENSITIVE_HEADERS` value in all four Lambda function `Environment.Variables` blocks (`MockNestRuntimeFunction`, `MockNestRuntimeFunctionIam`, `MockNestRuntimeAsyncFunction`, `MockNestRuntimeAsyncFunctionIam`) with `!Ref SensitiveHeaders`
    - _Requirements: 2.40_

  - [x] 8.10 Separate IAM roles for runtime and generation Lambdas (Bug 1.46)
    - Create a new `MockNestGenerationLambdaRole` IAM role that includes `MockNestBedrockAccess` and `MockNestS3Access` (without SQS webhook queue permissions) and `AWSLambdaBasicExecutionRole`
    - Remove `MockNestBedrockAccess` from `MockNestLambdaRole` (the runtime role) — the runtime Lambda does not need Bedrock access
    - Wire `MockNestGenerationFunction` and `MockNestGenerationFunctionIam` to `Role: !GetAtt MockNestGenerationLambdaRole.Arn`
    - Verify `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam` remain wired to `MockNestLambdaRole`
    - _Requirements: 2.46_

  - [x] 8.11 Checkpoint — validate SAM template after all Group C template changes
    - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1`
    - Confirm exit code 0 before proceeding to Group D

- [x] 9. Fix Group D — Minor / Nitpick (Kotlin test source)

  - [x] 9.1 Fix RuntimeAsyncSpringContextTest assertion (Bug 1.16)
    - Change the assertion in the test "Given async profile active When Spring context loads Then wireMockServer bean is NOT registered by MockNestConfig" to assert that `wireMockServer` is absent (not `directCallHttpServerFactory`)
    - _Requirements: 2.16_

  - [x] 9.2 Replace assert() with kotlin.test.assertTrue() in RuntimeAsyncSpringContextTest (Bug 1.42)
    - Replace all `assert(...)` calls with `kotlin.test.assertTrue(...)` so assertions are not dependent on JVM assertion flags
    - _Requirements: 2.42_

  - [x] 9.3 Remove unused imports from WebhookHttpClientTest (Bug 1.17)
    - Remove unused import `java.net.SocketTimeoutException`
    - _Requirements: 2.17_

  - [x] 9.4 Replace assertInstanceOf + cast with assertIs in WebhookHttpClientTest (Bug 1.18)
    - Replace `assertInstanceOf` followed by a cast with `assertIs<WebhookResult.Success>(result)` (idiomatic Kotlin test assertion)
    - _Requirements: 2.18_

  - [x] 9.5 Remove unused imports from WebhookServeEventListenerTest (Bug 1.25)
    - Remove unused imports `ParameterizedTest` and `MethodSource`
    - _Requirements: 2.25_

  - [x] 9.6 Replace Thread.sleep with Awaitility polling in WebhookServeEventListenerPrototypeTest (Bug 1.27)
    - Replace all `Thread.sleep(200)` calls with Awaitility condition polling to eliminate timing-dependent flakiness
    - _Requirements: 2.27_

  - [x] 9.7 Remove Thread.sleep from MockNestConfigWebhookWiringTest (Bug 1.34)
    - Remove `Thread.sleep(1000)` — `transform()` is synchronous and no sleep is needed
    - _Requirements: 2.34_

  - [x] 9.8 Replace Thread.sleep with polling in WebhookAsyncEventPublisherIntegrationTest (Bug 1.37)
    - Replace all `Thread.sleep(500)` calls with Awaitility or condition polling assertions
    - _Requirements: 2.37_

  - [x] 9.9 Update RedactSensitiveHeadersFilter KDoc (Bug 1.30)
    - Update the KDoc on `redactHeadersInJson` to accurately state that the implementation parses into a `MutableMap` (not "does not parse into objects")
    - _Requirements: 2.30_

  - [x] 9.10 Run verification after Group D fixes
    - Run `./gradlew :software:application:test`
    - Run `./gradlew :software:infra:aws:runtime:test`
    - Confirm exit code 0 and all tests pass

  - [x] 9.11 Run full build verification after all groups
    - Run `./gradlew clean test`
    - Confirm exit code 0

- [x] 10. Checkpoint — Ensure all tests pass
  - Re-run the bug condition exploration tests from task 1 — all six MUST pass
  - Re-run the preservation property tests from task 2 — all MUST pass
  - Run `./gradlew clean test` — confirm exit code 0
  - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` — confirm exit code 0
  - Run `bash -n scripts/post-deploy-test.sh` — confirm exit code 0
  - Ask the user if any questions arise before marking complete
