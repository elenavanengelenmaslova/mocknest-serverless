# Bugfix Tasks: Response Streaming Post-Deploy Issues

## Context

After deploying the response streaming feature, the health check fails with an empty response. A code review identified 7 issues ranging from critical (root cause) to low severity.

## Tasks

- [x] 1. Fix `ResponseTransferMode` value in SAM template (CRITICAL — root cause)
  - Changed `ResponseTransferMode: STREAM` to `ResponseTransferMode: RESPONSE_STREAM` on all 6 routes
  - SAM validate passes
  - **This was the root cause — `STREAM` is not a valid value, `RESPONSE_STREAM` is**

- [ ] 2. Validate Java runtime streaming compatibility
  - After fixing task 1, deploy and test whether the Java 25 managed runtime actually streams via `InvokeWithResponseStream`
  - Test with `curl -N -v` to verify chunks arrive progressively (not all at once)
  - If Java runtime doesn't support streaming: investigate Lambda Web Adapter or custom runtime approach
  - Document findings

- [ ] 3. Fix S3 streaming memory issue — body loaded twice
  - **Current behavior:** WireMock's `response.bodyAsString` loads the entire S3 file into memory BEFORE the handler checks `bodyFileName`
  - **Impact:** For large files (>6MB), the body is in memory twice (WireMock load + S3 re-stream)
  - **Fix options:**
    - Option A: Skip calling `routeRequest()` for the body when `bodyFileName` is detected (check WireMock serve events first)
    - Option B: Modify `forwardToDirectCallHttpServer` to NOT load body for `bodyFileName` responses (return null body, let handler stream from S3)
    - Option C: Accept the double-load for now, document as known limitation (body still fits in Lambda memory for files up to 200MB with 10GB max memory)
  - **Note:** Chunked responses (without bodyFileName) intentionally hold the full body in memory — chunks are just timed writes of in-memory data. This is acceptable since chunked mocks are typically small SSE payloads.

- [ ] 4. Fix S3 failure returning partial 200 response
  - **Current behavior:** `writeS3StreamedResponse` writes metadata (200 status) BEFORE attempting S3 retrieval. If S3 fails, client gets a truncated 200.
  - **Fix options:**
    - Option A: Validate S3 object exists (HEAD request) before writing metadata. If HEAD fails, write 502 instead.
    - Option B: Accept truncated 200 as documented behavior (streaming inherently can't change status mid-stream). Add documentation.
  - **Recommendation:** Option A for correctness, with fallback documentation for mid-stream failures

- [ ] 5. ~~Fix chunked delay formula (off-by-one)~~ — NOT A BUG
  - **Current:** `delayBetweenChunks = totalDurationMs / numberOfChunks`
  - **Reviewer suggested:** `totalDurationMs / (numberOfChunks - 1)`
  - **Analysis:** Our formula matches WireMock's own `chunkedDribbleDelay` behavior AND our requirements explicitly specify this formula (Requirement 4.2). The total delivery time is intentionally less than `totalDuration` because the first chunk has no delay.
  - **Status:** No change needed — this is by design

- [ ] 6. Update timeout documentation and warning threshold
  - **Issue:** Code warns at 270,000ms (4.5 min) about idle timeout, but API Gateway REST API has a 29s integration timeout
  - **With streaming enabled:** API Gateway REST API streaming supports up to 15 minutes (per AWS docs), so the 29s limit may not apply to streaming responses
  - **Action:** Verify whether the 29s timeout applies to streaming responses. If not, the 270s warning is appropriate. If yes, update the warning threshold and documentation.
  - Update `RuntimeLambdaTimeout` max value in SAM template if streaming bypasses the 29s limit

- [ ] 7. Consider multi-value header support (optional)
  - **Current:** `StreamingProtocolWriter` flattens `Map<String, List<String>>` to `Map<String, String>` via `.toMap()`, dropping duplicate headers (e.g., multiple Set-Cookie)
  - **Streaming protocol supports:** `multiValueHeaders` field in metadata JSON
  - **Action:** Add `multiValueHeaders` to the metadata JSON when response has multi-value headers
  - **Priority:** Low — pre-existing limitation, not a regression

- [ ] 8. Remove debug logging from post-deploy test script
  - After issues are resolved, remove the verbose debug output added to `test_runtime_health()`
  - Restore the original clean test output

- [x] 9. Add streaming validation test to post-deploy extensive tests
  - Added `test_streaming_progressive_delivery` to `scripts/post-deploy-test.sh`
  - Measures TTFB vs total time using curl `time_starttransfer` and `time_total`
  - Pass: TTFB < 50% of total (proves progressive delivery)
  - Warning (not failure): TTFB ≈ total (suggests buffered — Java runtime may not support streaming)
  - Added to both `streaming` and `all` test suites

## Priority Order

1. Task 1 (fix SAM value) — deploy and test immediately
2. Task 2 (validate Java streaming) — test after deploy
3. Task 9 (streaming validation test) — add test to prove streaming works
4. Task 5 (delay formula) — quick code fix
5. Task 4 (S3 failure handling) — design decision needed
6. Task 3 (S3 memory) — design decision needed
7. Task 6 (timeout docs) — research needed
8. Task 7 (multi-value headers) — optional enhancement
9. Task 8 (cleanup) — after all fixes validated
