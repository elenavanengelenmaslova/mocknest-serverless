# Implementation Plan

- [x] 1. Baseline (root cause already CONFIRMED — see design.md and `.agents/tasks/dribble-coalesce-investigation.md`)
  - [x] 1.1 Confirm current `handleRequest` has no `output.close()`/`use { }` and note every early `return` that must become `return@use`
  - [x] 1.2 Run `./gradlew clean test` and confirm the full build passes before starting changes

- [x] 2. Fix: close the Lambda stream via `output.use { }`
  - [x] 2.1 In `StreamingRuntimeLambdaHandler.handleRequest`, wrap the entire body in `output.use { }` so `close()` runs on every exit path
  - [x] 2.2 Convert each early `return` (after `writeS3ChunkedResponse`, `writeChunkedResponse`, the 200MB guard, `parseRequest` failure) to `return@use`; do not add a manual `output.close()` alongside `use`
  - [x] 2.3 Keep all existing `output.flush()` calls (per-chunk in `ChunkedResponseWriter`, and trailing flush in each handler path) so the runtime drains before close
  - [x] 2.4 Confirm the S3 path still reads in bounded ≤1MB increments (no full-object materialization)
  - [x] 2.5 Write unit/handler tests (MockK, Given-When-Then, `runTest` virtual time) asserting: stream is closed exactly once after the body is fully written (all paths — S3 chunked, in-memory chunked, standard, error); close happens after the last chunk, never before; per-chunk flush is preserved
  - [x] 2.6 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Strengthen the post-deploy streaming test to detect buffering
  - [x] 3.1 Rewrite `scripts/post-deploy-test.sh::test_streaming_sse_chunked_delay` to register a dribble mock with ≥~16KB per chunk (avoid network coalescing)
  - [x] 3.2 Capture `time_starttransfer` and `time_total` (and/or timestamped byte arrival); assert `(time_total - time_starttransfer)` is a meaningful fraction of `totalDuration`, failing on a terminal burst
  - [x] 3.3 Keep the existing total-duration assertion as a secondary check (timing preserved)
  - [x] 3.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 3a. Add a realistic SSE "AI conversation" progressive-delivery test (the demo use case)
  - [x] 3a.1 Add `test_streaming_sse_ai_conversation` to `scripts/post-deploy-test.sh`: register a mock with `Content-Type: text/event-stream` whose body is a multi-token LLM-style SSE transcript — many `data: {"delta":"<token>"}` events ending with `data: [DONE]`, padded so each dribble chunk is ≥~16KB (so chunks are not network-coalesced). Use `numberOfChunks` ≈ number of SSE events and a `totalDuration` of ~5000ms so tokens visibly trickle like a chatbot reply
  - [x] 3a.2 Stream the response with a timestamping reader (record a timestamp per received chunk); assert (a) the first SSE event arrives well before the last (first-to-last spread ≥ ~50% of `totalDuration`), (b) events arrive monotonically over time rather than in one burst, (c) the full transcript including the terminal `data: [DONE]` is received intact
  - [x] 3a.3 Assert the SSE framing is preserved end-to-end (each delivered chunk boundary does not corrupt `data: ...\n\n` events; the concatenated body equals the registered transcript byte-for-byte)
  - [x] 3a.4 Document this as the canonical streaming demo (the `curl --http1.1 -N` one-liner that shows tokens trickling) so it doubles as a repeatable demo and a regression guard
  - [x] 3a.5 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Verify coverage and quality
  - [x] 4.1 Run `./gradlew koverHtmlReport` and verify 90%+ coverage for new/changed code
  - [x] 4.2 Run `./gradlew koverVerify` to enforce the threshold
  - [x] 4.3 Review test quality: Given-When-Then naming, proper assertions, edge cases (single chunk, zero-delay, S3 vs inline)

- [ ] 5. Deploy and verify on the live stack
  - [~] 5.1 Deploy the fix to the `mocknest-serverless` stack
  - [~] 5.2 Re-import a dribble mock with multi-KB-per-chunk body; run `curl --http1.1 -N` piped to a byte-timestamping reader; confirm chunks spread across the dribble window (not a single burst)
  - [~] 5.3 Confirm a >6MB body returns in full (end-to-end streaming)
  - [~] 5.4 Run the strengthened `scripts/post-deploy-test.sh` streaming suite against the deployed endpoint and confirm it passes
