# Dribble Coalesce Investigation — Root Cause Report

## Summary answer (confidence: HIGH)

MockNest's chunked‑dribble responses coalesce into a single burst at the end because
the Runtime streaming handler **never closes the Lambda `OutputStream`** (and never wraps
it in a `.use { }` block). It only calls `output.flush()`.

On the AWS Lambda JVM `RequestStreamHandler` response‑streaming path, `flush()` alone does
**not** reliably push bytes to API Gateway between writes. The runtime only guarantees
delivery of what has been written once the handler **closes** the output stream — that
close is the "response complete / drain now" signal. Until then the runtime may hold the
written bytes. With a dribble mock the handler sits in a `delay()` loop for the full
~5 s, flushing after each small chunk, but because the stream is never closed the runtime
does not forward those flushes; everything is drained at once when `handleRequest` returns
and the runtime tears down / closes the stream for the handler. That produces exactly the
observed shape: nothing for ~4.8 s, then all chunks in a burst between 4.80 s and 4.84 s,
for every body size (300 B, 200 KB, 2 MB).

This is the **same bug the sibling repo already hit and fixed**. The sibling repo's
engineering log documents it verbatim (see Evidence §4): omitting `close()` produces a
clean handler exit, clean CloudWatch logs, and a response that is delivered only when the
runtime's own cleanup drains the buffer — "on a slow first invocation there happened to be
enough time ... on fast warm invocations the Lambda exited before the runtime finished
delivering." The sibling's fix was to wrap the handler body in `output.use { ... }` plus an
explicit `output.flush()` before the implicit close. MockNest's handler flushes but omits
the close, so it reproduces the pre‑fix behaviour.

A secondary, contributing factor (confidence: MEDIUM) is that MockNest's own
post‑deploy streaming test writes a tiny body (≈57 bytes split into 5 chunks ≈ 11 bytes
each), far below any plausible AWS streaming write threshold — so even with a correct
close it is a weak progressive‑delivery probe. The sibling proves progressive delivery with
a 12 MB object in 1 MB chunks. But the missing `close()` is sufficient on its own to
explain the coalescing at all sizes, including 2 MB.

---

## Evidence

### 1. MockNest writes directly to the real AWS stream, flushes per chunk, but never closes it

`software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandler.kt`

- `handleRequest(input, output, context)` receives the AWS‑provided `OutputStream` and
  passes that same `output` straight through to `writer.writeResponse(...)`,
  `writer.writeMetadata(...)`, and `chunkedWriter.writeChunked(...) / writeChunkedFromStream(...)`.
  There is **no** `ByteArrayOutputStream`, no `BoundedBuffer`, no wrapping buffer — the
  stream handed to the body writers IS the Lambda stream. (So hypothesis #4 "wrong
  OutputStream / intermediate buffer" is **REFUTED**.)
- `writeChunkedResponse(...)` writes metadata, then `runBlocking { chunkedWriter.writeChunked(...) }`,
  then `output.flush()`.
- `writeS3ChunkedResponse(...)` writes metadata, streams from S3 via
  `chunkedWriter.writeChunkedFromStream(...)`, then `output.flush()`.
- Every exit path ends in `output.flush()` and **returns**. Searching the whole runtime
  module for `output.close()` / `output.use` / `.use {` on the Lambda stream finds
  **nothing** (the only `.use`/`close()` hits are OkHttp responses, SQS/S3 test clients,
  and a test `InputStream` wrapper). **The handler never closes the response stream.**

### 2. ChunkedResponseWriter's flush discipline is correct (hypotheses #1 and #3 REFUTED)

`software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriter.kt`

- `writeChunked(...)`: loops `chunkSizes`, `delay(delayBetweenChunks)` **before** each
  chunk after the first, then `output.write(body, offset, chunkSize)` followed by
  `output.flush()` **inside the loop, once per chunk**. Ordering is delay → write → flush,
  which is the correct trickle ordering.
- `writeChunkedFromStream(...)`: same structure — `delay(...)` between chunks, `output.write(...)`,
  then `output.flush()` per chunk.
- The `delay()` calls are real `kotlinx.coroutines.delay`, driven by `runBlocking` in the
  handler; they genuinely suspend between flushes.

So the per‑chunk `flush()` IS being invoked on the real Lambda stream between delays
(hypothesis #3 confirmed as *not* the problem), and there is no intermediate
`BoundedBuffer` swallowing the flush (hypothesis #1 **REFUTED** — MockNest does not even
use streaming‑core's `BoundedBuffer`/`copy`; it has its own writer). The flushes land on
the correct stream; they simply are not honoured by the runtime because the stream is
never closed.

### 3. The prelude is committed BEFORE the delay loop (hypothesis #2 REFUTED)

In both `writeChunkedResponse` and `writeS3ChunkedResponse`, `writer.writeMetadata(output, …)`
runs **before** entering `runBlocking { ... writeChunked... }`. `ResponseWriter.writeMetadata`
writes the prelude JSON + 8 null bytes straight to the stream
(`aws-lambda-streaming-jvm-runtime/streaming-core/.../ResponseWriter.kt`, `writeMetadata`:
`output.write(prelude); output.write(ByteArray(DELIMITER_LEN))`). So the metadata/delimiter
are written up front; the delay loop does not precede the prelude. Delay‑before‑commit is
**not** the cause.

### 4. The sibling repo documents this exact failure and its fix — MockNest matches the pre‑fix state

Sibling (`/Users/e.maslova/Kiro/aws-lambda-streaming-jvm-runtime`), engineering log
`docs/log.md`, section **"Lambda streaming: `OutputStream` must be closed explicitly —
partial body on warm invocations"**:

- Root cause recorded: the handler "wrote to the `OutputStream` but never called `close()`
  on it. AWS Lambda streaming requires the handler to close the output stream to signal to
  the runtime that the response is complete. Without an explicit `close()`, the runtime
  flushes what it can before tearing down the execution environment" — enough time on a
  slow cold invocation, not enough on fast warm ones. (Content rephrased for compliance.)
- The documented AWS guidance quoted there: close the output stream at the end of the
  handler.
- Resolution: (1) wrap the handler body in `output.use { }` so `close()` runs on every exit
  path, and (2) add an explicit `output.flush()` before the close so the runtime drains its
  buffer completely.

Sibling handler `streaming-s3-example/src/main/kotlin/nl/vintik/lambda/streaming/StreamHandler.kt`
implements exactly that:

```kotlin
override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
    output.use {
        when (val result = requestResolver.resolve(input)) { ... }
        output.flush()
    }
}
```

`output.use { ... }` calls `output.close()` on exit. **MockNest has the `flush()` but not
the enclosing `use`/`close()`.** That is the one material divergence in flush/close
discipline between the two handlers.

### 5. The sibling's progressive‑delivery proof uses a LARGE body; MockNest's probe is tiny

- Sibling `scripts/pipeline-streaming-test.sh`: seeds a **12 MB** object
  (`TEST_OBJECT_SIZE=12582912`) and `test_streaming_progressive_delivery` streams it,
  asserting TTFB < 50% of total. `docs/log.md` records a real run of a ~21.7 MB object:
  `First byte: 6.97s | Total: 12.70s` — genuinely progressive, byte‑identical. The sibling
  body copy uses streaming‑core `copy(...)` (`BoundedBuffer.kt`): 1 MB buffer, `flush()`
  after each 1 MB chunk, i.e. ~12–21 flushes of 1 MB each.
- MockNest `scripts/post-deploy-test.sh` `test_streaming_progressive_delivery`: registers a
  mock whose body is `"chunk1-data chunk2-data chunk3-data chunk4-data chunk5-data"`
  (≈57 bytes) with `numberOfChunks=5`, so ~11 bytes per chunk. Even if `close()` were
  present, 11‑byte writes are far below any realistic AWS streaming write threshold and are
  a poor progressive‑delivery signal. (Note MockNest's script only *warns* on failure; the
  sibling's *fails* — so MockNest's own test would not have caught this.)

### 6. streaming‑core version note (requested)

- MockNest declares `nl.vintik:aws-lambda-streaming-core:2.2.0`
  (`software/infra/aws/core/build.gradle.kts`).
- The sibling working tree is at git `v2.2.0-13-g454450f` — i.e. **13 commits past the
  v2.2.0 tag**, so the sibling source I read is slightly *newer* than the published 2.2.0
  MockNest consumes. The relevant API/semantics are unchanged between them: `ResponseWriter`
  (`writeMetadata`/`writeResponse`) writes straight to the passed `OutputStream` with no
  internal buffering, and `copy(...)` flushes per 1 MB chunk. **Flag:** minor version drift
  (sibling tree ahead of 2.2.0), but it does not affect this analysis — MockNest does not
  use streaming‑core's `copy`/`BoundedBuffer` for the dribble path at all; it uses its own
  `ChunkedResponseWriter`.

---

## Hypothesis scorecard

| # | Hypothesis | Verdict |
|---|------------|---------|
| 1 | BoundedBuffer threshold swallows flush | **REFUTED** — MockNest doesn't use `BoundedBuffer`; its own `ChunkedResponseWriter` flushes the real stream per chunk. |
| 2 | Delay loop runs before prelude is committed | **REFUTED** — `writeMetadata` runs before the `runBlocking` delay loop in both chunked paths. |
| 3 | Coroutine `delay`/flush not reaching real stream | **REFUTED (as written)** — delays suspend and `flush()` is invoked per chunk on the real stream; the issue is the stream is never closed, so the runtime doesn't honour the flushes. |
| 4 | Writes go to a ByteArrayOutputStream / intermediate buffer | **REFUTED** — the AWS `output` is passed straight through, no wrapper. |
| 5 | Sibling flush discipline differs | **CONFIRMED** — sibling wraps in `output.use { }` (closes the stream) and flushes; MockNest flushes but never closes. This is the divergence. |
| — | (Primary) Missing `output.close()` | **CONFIRMED root cause** — matches the sibling's documented pre‑fix bug and the observed drain‑at‑exit burst. |
| — | (Secondary) Tiny chunk sizes in the probe | **CONTRIBUTING** — weakens the test but does not explain the 2 MB burst; close() does. |

---

## Recommended fix

**File:** `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandler.kt`
**Method:** `handleRequest`

Wrap the entire handler body in `output.use { }` so `close()` runs on every exit path
(normal return, early `return` after writing a chunked/S3/error response, and uncaught
exceptions), keeping the existing `flush()` calls so the runtime drains before the close.

Sketch:

```kotlin
override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
    output.use {
        val httpRequest = parseRequest(input, output) ?: return@use
        // ... existing routing / chunked / S3 / standard-response logic unchanged ...
        // every branch keeps its output.flush(); the enclosing use { } adds close()
    }
}
```

Notes for the implementer:

- The current method uses several early `return` statements (after `writeS3ChunkedResponse`,
  `writeChunkedResponse`, the 200 MB guard, and `parseRequest` failure). Inside `output.use { }`
  convert those to `return@use` (or restructure) so the `use` block still closes the stream.
  Equivalently, keep the early returns but put the `use` at the very top so the lambda owns
  the whole body.
- Keep the existing per‑chunk `output.flush()` in `ChunkedResponseWriter` and the trailing
  `output.flush()` in each handler path. The sibling log notes a residual race where the last
  chunk could be dropped on fast warm invocations even with `close()` unless an explicit
  `flush()` precedes the close — the existing trailing flushes satisfy that, so keep them.
- Do **not** call `output.close()` manually *and also* rely on callers/other code to close —
  `use { }` is the idiomatic single close and matches the project's Kotlin‑idiom rule.

Secondary (test quality, not required for the fix but recommended):

- In `scripts/post-deploy-test.sh::test_streaming_progressive_delivery`, use a larger body
  (e.g. hundreds of KB to a few MB) and/or an S3‑backed dribble mock, and consider making a
  clear buffered result **fail** rather than only warn, so a regression is caught in the
  pipeline the way the sibling's test catches it.

---

## Confidence and what cannot be verified without redeploy

- **HIGH** that the missing `output.close()`/`use { }` is the root cause: it is a direct,
  documented match to the sibling's own pre‑fix bug, the sibling's fix is precisely the
  thing MockNest lacks, and it explains the all‑sizes drain‑at‑exit burst that a pure
  chunk‑size/threshold theory does not.
- **Cannot be fully proven without redeploying** that adding `output.use { }` alone fully
  restores progressive trickle for the *tiny* dribble bodies — AWS may still coalesce very
  small writes below its internal streaming write threshold regardless of close(). The
  decisive confirmation is a post‑fix redeploy tested with (a) a multi‑MB dribble body and
  (b) a timestamped reader, expecting chunks to arrive spread across the ~5 s window rather
  than bursting at the end. If small bodies still coalesce after the close() fix, raise the
  per‑chunk size (or accept that sub‑threshold chunks can't be trickled by the platform).
- The sibling tree being 13 commits ahead of the published `2.2.0` is noted but immaterial
  here; the dribble path uses MockNest's own `ChunkedResponseWriter`, not streaming‑core's
  `copy`.
