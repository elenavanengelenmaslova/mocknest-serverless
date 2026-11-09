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
  - [ ] 5.1 Deploy the fix to the `mocknest-serverless` stack
  - [ ] 5.2 Re-import a dribble mock with multi-KB-per-chunk body; run `curl --http1.1 -N` piped to a byte-timestamping reader; confirm chunks spread across the dribble window (not a single burst)
  - [ ] 5.3 Confirm a >6MB body returns in full (end-to-end streaming)
  - [ ] 5.4 Run the strengthened `scripts/post-deploy-test.sh` streaming suite against the deployed endpoint and confirm it passes

- [x] 6. Adopt the `streaming-sse` extension to make dribble delay actually trickle (the real fix)

  Root-cause note: experiments on the live stack proved per-chunk dribble-with-delay does NOT trickle
  through API Gateway REST + the managed Java Lambda streaming runtime — the runtime holds small
  writes in an internal buffer (~100KB) and only forwards on buffer-fill or stream close, so `flush()`
  between small chunks does nothing (sections 2–5 above addressed stream-close and test strength, but
  could not make small delayed chunks trickle). The publisher library now ships a `streaming-sse`
  extension (`nl.vintik:aws-lambda-streaming-sse`, released with `aws-lambda-streaming-core`) whose
  `PaddedSseWriter` inflates each SSE event past the runtime buffer threshold with ignorable SSE
  comment padding, and whose `PacedSseEmitter` applies the inter-chunk delay — which together make
  small delayed chunks reach the client progressively. This section adopts it.

  - [x] 6.1 Bump `software/infra/aws/core/build.gradle.kts`: upgrade `nl.vintik:aws-lambda-streaming-core` and add `api("nl.vintik:aws-lambda-streaming-sse")` (keep the single shared version in sync). NOTE: the sse artifact was never published at `2.3.1` (Maven Central only has sse `2.4.0`/`2.4.1`); adopted `2.4.1` for both artifacts (earliest co-release of the sse module, latest in-sync version)
  - [x] 6.2 Review the `streaming-sse` API (`PaddedSseWriter`, `PacedSseEmitter`) from the library source/README at tag `v2.4.1` (GitHub `elenavanengelenmaslova/aws-lambda-response-streaming-jvm`); confirmed constructor params (head/tail pad bytes, head pad events) and delay bounds (`NO_DELAY`/`MIN_DELAY_MILLIS`/`MAX_DELAY_MILLIS`) and the padding/size trade-off
  - [x] 6.3 Route the SSE dribble paths through `PacedSseEmitter`/`PaddedSseWriter` via a new `SseDribbleWriter`: `writeChunkedResponse` (in-memory) and `writeS3ChunkedResponse` (S3) emit padded SSE events paced by `totalDuration`; `chunkedDribbleDelay { numberOfChunks, totalDuration }` maps onto the emitter's per-event delay (`totalDuration / numberOfChunks`, clamped to the emitter bounds)
  - [x] 6.4 Preserve the zero-memory-streaming guarantee on the S3 path: `SseDribbleWriter.writeSseChunkedFromStream` emits events from the bounded ≤1MB read loop; the standard (non-dribble) path is unchanged
  - [x] 6.5 SSE-vs-non-SSE scope decided: padding is applied ONLY for `text/event-stream` bodies (per-chunk pacing supported); non-SSE dribble keeps progressive-by-size delivery via `ChunkedResponseWriter`. Captured in the handler (`isServerSentEvents` + KDoc) and in `docs/USAGE.md`
  - [x] 6.6 Reduced `ChunkedResponseWriter` to the non-SSE dribble path (kept for size-based progressive delivery of arbitrary bodies); existing unit tests still pass
  - [x] 6.7 Wrote `SseDribbleWriterTest` (MockK-style, Given-When-Then, `runTest`) covering head/tail padding tiers, inter-event pacing (none before first / after last), terminal `event: end`/`[DONE]`, byte-for-byte payload recovery, and S3 bounded-memory reads; updated handler + integration SSE tests
  - [x] 6.8 Ran `./gradlew clean test` — BUILD SUCCESSFUL (499 tests)

- [ ] 7. Re-verify dribble delay on the live stack with `streaming-sse`
  - [ ] 7.1 Build the clean jar (`--no-build-cache`) and deploy to the `mocknest-serverless` stack (foreground deploy; poll `describe-stacks` for `UPDATE_COMPLETE`; confirm deployed Lambda `CodeSha256` matches the local jar)
  - [ ] 7.2 Register a `text/event-stream` dribble mock (multi-token transcript, `totalDuration ~5000ms`) and run `curl --http1.1 -N` piped to a byte-timestamping reader; confirm tokens arrive spread across the dribble window (first-to-last spread ≥ ~50% of `totalDuration`, multiple distinct arrival buckets — NOT a terminal burst)
  - [ ] 7.3 Confirm a >6MB body still returns in full (end-to-end streaming preserved)
  - [ ] 7.4 Run the strengthened `scripts/post-deploy-test.sh` streaming suite (incl. `test_streaming_sse_chunked_delay` and `test_streaming_sse_ai_conversation`) against the deployed endpoint and confirm it passes
  - [ ] 7.5 Record the canonical demo GIF (`curl --http1.1 -N` showing tokens trickling) via asciinema→agg, key/host kept off-screen, and save under `docs/images/demo/`

- [ ] 8. Update documentation to reflect that dribble delay works via `streaming-sse`
  - [ ] 8.1 Revise `docs/USAGE.md` streaming section: time-paced per-token SSE dribble IS supported via the `streaming-sse` padding approach; document the response-size trade-off (padding inflates the body, e.g. a ~15KB answer becomes several MB) and the per-chunk delay semantics; correct the earlier "not supported on REST API" wording
  - [ ] 8.2 Update `README.md` and `README-SAR.md` streaming/SSE wording accordingly, and embed the trickling-tokens demo GIF from 7.5
  - [ ] 8.3 Ensure the progressive-large-response GIF (`streaming-progress.gif`) and the new token-trickle GIF are both referenced where appropriate
