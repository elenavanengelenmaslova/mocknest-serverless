# Design Document

## Overview

This design fixes the bug where `chunkedDribbleDelay` mock responses are delivered in a single terminal burst instead of trickling to the client, even though the dribble delay is applied and the API Gateway integration is correctly set to `responseTransferMode=STREAM`.

The scope is confined to the runtime infrastructure layer:
- `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandler.kt`
- `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriter.kt`
- `scripts/post-deploy-test.sh` (strengthen the streaming assertion)

No SAM/template changes (integration already correct). No streaming wire-protocol changes (metadata + 8-null-byte delimiter + body is correct and unchanged).

## Root cause (CONFIRMED — HIGH confidence)

Confirmed by the investigation at `.agents/tasks/dribble-coalesce-investigation.md`.

**`StreamingRuntimeLambdaHandler.handleRequest` never closes the Lambda `OutputStream`.** It flushes per chunk (correctly, on the real AWS stream — no intervening buffer), but on the AWS Lambda JVM `RequestStreamHandler` response-streaming path, `flush()` alone does NOT reliably push bytes to API Gateway between writes. The runtime only forwards the written bytes when the handler **closes** the stream — close is the "response complete, drain now" signal. Because the handler sits in the `delay()` loop for the full ~5s and never closes, the runtime holds everything and drains it all when `handleRequest` returns and the runtime tears down the stream. Hence the burst at ~4.8s for every body size (300B, 200KB, 2MB).

The following alternative hypotheses were **refuted** by the investigation (do not pursue):
- Intervening `BoundedBuffer`/`BufferedOutputStream` swallowing flushes — MockNest writes the AWS `output` straight through; no wrapper exists.
- Delay loop running before the prelude is committed — `writeMetadata` runs before the delay loop in both chunked paths.
- Per-chunk flush not reaching the real stream — it does reach it; the runtime just doesn't honour it without a close.

The sibling repo (`aws-lambda-streaming-jvm-runtime`) hit this exact bug and documented it in `docs/log.md`; its fix is `output.use { ... output.flush() }` in `StreamHandler.handleRequest`. MockNest has the `flush()` but not the enclosing `use`/`close()`. That single divergence is the bug.

Secondary (contributing, not causal): MockNest's post-deploy progressive test uses a ~57-byte body (~11 bytes/chunk), far too small to be a reliable progressive-delivery probe and it only *warns* on failure rather than failing — which is why this shipped undetected.

## Fix (confirmed)

### Change 1 (the fix): close the Lambda stream via `output.use { }`

**File:** `software/infra/aws/runtime/.../function/StreamingRuntimeLambdaHandler.kt`, method `handleRequest`.

Wrap the entire handler body in `output.use { }` so `close()` runs on every exit path (normal return, the early `return`s after `writeS3ChunkedResponse` / `writeChunkedResponse` / the 200MB guard / `parseRequest` failure, and on exceptions). Keep all existing `flush()` calls so the runtime drains fully before the close (the sibling's log notes a residual last-chunk race that the trailing flush prevents).

```kotlin
override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
    output.use {
        val httpRequest = parseRequest(input, output) ?: return@use
        // ... existing routing / chunked / S3 / standard-response logic unchanged ...
        // convert the current early `return` statements to `return@use`
        // every branch keeps its output.flush(); the enclosing use { } adds close()
    }
}
```

Implementation notes:
- Convert the method's early `return` statements to `return@use` (or restructure) so the `use` block still closes on those paths.
- Do not add a manual `output.close()` in addition to `use { }` — `use` is the single idiomatic close (per the project's Kotlin-idiom rule).
- Close must happen only after the final chunk (the `use` block naturally guarantees this since the chunk loop runs inside it). A mid-body failure still truncates and propagates without rewriting the already-committed status.

### Change 2: `ChunkedResponseWriter` flushes per chunk (already correct — keep unchanged)

`writeChunked` / `writeChunkedFromStream` already do delay → write → `output.flush()` per chunk on the real stream. No change. The bug was never here.

### Change 3: Preserve zero-memory guarantee (unchanged)

The S3 path (`writeChunkedFromStream`) still reads in bounded ≤1MB increments. No buffer-sizing change; do not materialize the whole S3 object.

## Testing strategy

### Unit / handler test (catch without deploy — Requirement 4.3)

Add a test that drives `StreamingRuntimeLambdaHandler` (or `ChunkedResponseWriter` directly) with a spy `OutputStream` and asserts:
- `write` then `flush` is invoked per chunk, in order, on the actual stream passed in;
- the number of flushes ≥ number of chunks;
- the stream is finalized (flushed/closed) exactly once after the last chunk, never before.

Use `runTest` / virtual time for the coroutine delays (per testing standards — avoid real sleeps). Follow MockK + Given-When-Then conventions.

### Strengthen post-deploy test (Requirement 4.1/4.2)

Replace the total-time-only assertion in `scripts/post-deploy-test.sh::test_streaming_sse_chunked_delay` with a progressive-delivery assertion adapted for a dribble mock:
- Register a dribble mock with a body large enough per chunk to avoid network coalescing (e.g. ≥ ~16KB/chunk).
- Capture byte arrival over time (stream the response and record a timestamp when the first bytes arrive vs when the last bytes arrive), or capture `time_starttransfer` and `time_total`.
- Assert the first-byte-to-last-byte spread covers a substantial fraction of the dribble duration (i.e. bytes did NOT all arrive in a terminal burst). This is the dribble-adapted analog of the sibling's `test_streaming_progressive_delivery` (TTFB < 50% of total). For a dribble mock where delay is intentional, assert instead that `(time_total - time_starttransfer)` is a meaningful fraction of `totalDuration`, confirming trickle.

### Live verification (Requirement 5)

After deploy, run `curl --http1.1 -N` piped to a byte-timestamping reader against a multi-KB-per-chunk dribble mock and confirm chunks spread across the dribble window. Separately confirm a >6MB body returns in full.

## Correctness properties

1. **Progressive:** for a dribble mock with N chunks over D ms and non-coalesced chunk sizes, byte arrivals are distributed across ~D, not concentrated at the end.
2. **Timing preserved:** total elapsed ≈ `(N-1)*(D/N)`.
3. **Bounded memory:** S3 path holds ≤1MB at a time.
4. **Protocol intact:** metadata + 8-null-byte delimiter + body, status committed once.
5. **No premature close:** stream is finalized only after the last chunk; a mid-body failure truncates and propagates, never rewrites status.

## Risks

- **Tiny chunks may still coalesce after the fix.** The close() fix restores progressive delivery, but AWS may still coalesce writes below its internal streaming threshold regardless of close(). The sibling proves trickle with 1MB chunks. So the live verification (and the strengthened test) must use a multi-KB-per-chunk body. If sub-threshold chunks still coalesce after the fix, that is a platform limit, not a MockNest bug — raise per-chunk size for the demo. This is the only thing not fully provable without the redeploy.
- **Early-return conversion.** `handleRequest` has several early `return`s; all must become `return@use` (or the body restructured) so the stream is closed on every path. Missing one re-introduces the bug on that path.
