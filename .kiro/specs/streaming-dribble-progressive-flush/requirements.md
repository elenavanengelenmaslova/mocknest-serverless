# Requirements Document

## Introduction

A deployed MockNest runtime serves `chunkedDribbleDelay` mock responses with the correct total delay, but the response body is **not delivered progressively**: all chunks arrive in a single burst after the full dribble duration has elapsed. This defeats the purpose of streaming (SSE simulation, progressive delivery, and crucially the >6MB response ceiling that only end-to-end streaming provides).

This is a bug spec. The feature (`zero-memory-streaming`, `streaming-library-refactor`) is already implemented and the AWS infrastructure is correctly configured. The defect is in the server-side flush path: per-chunk writes do not reach the client between inter-chunk delays.

## Observed Behavior (ground truth)

Captured against the live deployment (`sksfacxr61.execute-api.eu-west-1.amazonaws.com/mocks`, runtime version `0.11.1`) after a fresh deploy:

- A mock with `chunkedDribbleDelay { numberOfChunks: 10, totalDuration: 5000 }` and a 200KB body (10 × 20KB chunks) was requested with `curl --http1.1 -N` piped to a byte-timestamping reader.
- Result: **nothing arrives for ~4.8s, then all 10 chunks flush between 4.80s and 4.84s**:

  ```
  [ 4.80s] +20000 bytes
  [ 4.81s] +20000 bytes
  ...
  [ 4.83s] +20000 bytes
  [ 4.84s] +100 bytes
  ```

- Same shape for 300-byte and 2MB bodies: `time_starttransfer ≈ time_total`.
- The dribble delay IS applied (total ≈ 5s); only the *progressive* delivery is missing.

## What is already confirmed correct (do NOT change)

1. **API Gateway integration is streaming.** The deployed REST integration for `/mocknest/{proxy+}` has `responseTransferMode=STREAM`, `type=AWS_PROXY`, and a URI ending in `/response-streaming-invocations`. Verified via `aws apigateway get-integration`. The gateway is not buffering by misconfiguration.
2. **The handler is a streaming handler.** `StreamingRuntimeLambdaHandler` implements `RequestStreamHandler` (java25, SnapStart on) and writes the metadata + 8-null-byte delimiter prelude via streaming-core `ResponseWriter` before the body.
3. **`ChunkedResponseWriter` does flush per chunk.** Its `writeChunked` / `writeChunkedFromStream` loops: optional `delay(...)`, `output.write(chunk)`, `output.flush()`.

## Reference: a working sibling implementation

The sibling repo `aws-lambda-streaming-jvm-runtime` (publisher of `nl.vintik:aws-lambda-streaming-core`) streams a 12MB S3 object over its own REST API Gateway endpoint and its pipeline test (`scripts/pipeline-streaming-test.sh::test_streaming_progressive_delivery`) passes with **TTFB < 50% of total** — genuine progressive delivery with the same `curl --http1.1 --no-buffer` client. This proves progressive streaming over API Gateway REST is achievable in this stack; MockNest diverges somewhere in its flush path.

## Glossary

- **Handler**: `StreamingRuntimeLambdaHandler` (`software/infra/aws/runtime/.../function/StreamingRuntimeLambdaHandler.kt`)
- **ChunkedResponseWriter**: `software/infra/aws/runtime/.../streaming/ChunkedResponseWriter.kt`
- **ResponseWriter**: streaming-core prelude writer (`nl.vintik:aws-lambda-streaming-core:2.2.0`)
- **AWS stream**: the `OutputStream` the Lambda Java runtime passes to `handleRequest`; for response streaming this is the runtime's `HttpResponseStream`
- **Progressive delivery**: response bytes reaching the client incrementally as each chunk is written, not coalesced into one burst
- **Dribble delay note**: for a dribble mock the inter-chunk delay dominates elapsed time, so `TTFB ≈ total` is expected EVEN when streaming works. The decisive observable is the **timestamped arrival pattern** (chunks spread over time vs one burst), not the TTFB/total ratio.

## Requirements

### Requirement 1: Dribble chunks reach the client progressively

**User Story:** As a developer demoing streaming, I want each dribble chunk to arrive at the client as it is written, so that the response visibly trickles rather than arriving all at once after the delay.

#### Acceptance Criteria

1. WHEN a mock with `chunkedDribbleDelay { numberOfChunks: N, totalDuration: D }` is served, THE Handler SHALL cause each chunk's bytes to be delivered to the client no later than shortly after that chunk is written (before the next inter-chunk delay elapses).
2. WHEN the body is large enough that chunks are not coalesced by the network (e.g. ≥ a few KB per chunk), THE observable arrival pattern SHALL show chunks spread across the dribble duration, NOT a single terminal burst.
3. THE total elapsed time SHALL remain approximately `(N-1) * (D/N)` (the existing dribble timing behavior MUST be preserved).

### Requirement 2: Root cause is identified and fixed in the flush path

**User Story:** As a maintainer, I want the specific mechanism that buffers chunk writes fixed at its source, so that the fix is correct rather than a workaround.

#### Acceptance Criteria

1. THE fix SHALL address the identified root cause of coalescing. Candidate mechanisms to confirm during design (see the coalesce investigation report at `.agents/tasks/dribble-coalesce-investigation.md`):
   a. `flush()` on the handler's wrapped `OutputStream` does not propagate to the underlying AWS `HttpResponseStream` / network.
   b. The chunk writes land in an intermediate buffer (e.g. a `BufferedOutputStream`, `BoundedBuffer`, or the streaming-core writer) that only drains on close.
   c. The dribble delays run before the response stream/prelude is actually committed to the network.
2. THE fix SHALL ensure the real AWS response stream is flushed between chunks (e.g. writing/flushing directly to the AWS-provided `OutputStream` without an intervening buffering stream, or invoking the correct flush on the underlying stream).
3. THE fix SHALL NOT regress the zero-memory-streaming guarantee: the S3 path (`writeS3ChunkedResponse` / `writeChunkedFromStream`) MUST still hold at most one bounded (~1MB) buffer in memory.
4. THE fix SHALL NOT alter the API Gateway SAM configuration (already correct) and SHALL NOT change the streaming wire protocol (metadata + 8-null-byte delimiter + body).

### Requirement 3: Both the inline and S3 chunked paths are fixed

**User Story:** As a developer, I want progressive delivery for both inline-body dribble mocks and S3-backed dribble mocks, so that behavior is consistent.

#### Acceptance Criteria

1. THE fix SHALL apply to the in-memory chunked path (`writeChunkedResponse` → `ChunkedResponseWriter.writeChunked`).
2. THE fix SHALL apply to the S3 streaming chunked path (`writeS3ChunkedResponse` → `ChunkedResponseWriter.writeChunkedFromStream`).
3. THE standard (non-dribble) streaming response path SHALL continue to work unchanged.

### Requirement 4: A test actually detects buffering (close the gap that let this ship)

**User Story:** As a maintainer, I want an automated test that fails when the response is buffered instead of streamed, so that this regression cannot silently return.

#### Acceptance Criteria

1. THE existing post-deploy streaming test (`scripts/post-deploy-test.sh::test_streaming_sse_chunked_delay`) currently asserts only `time_total >= expected_min` and CANNOT distinguish buffered-with-delay from progressive delivery. It SHALL be strengthened to detect progressive delivery.
2. THE strengthened test SHALL measure the arrival of bytes over time (not only total elapsed), using a body large enough that chunks are not network-coalesced, and SHALL fail if all bytes arrive in a single terminal burst. (Mirror the sibling's `test_streaming_progressive_delivery` approach, adapted for a dribble mock where the delay is intentional — assert that at least one chunk arrives meaningfully before the final chunk, i.e. first-byte-to-last-byte spread covers a substantial fraction of the dribble duration.)
3. THE fix SHALL be covered by a unit/integration test that asserts `output.flush()` (or the real-stream flush) is invoked between chunks on the actual stream the handler writes to, so the regression is caught without a deploy.

### Requirement 4a: Realistic SSE / AI-conversation streaming test (the demo scenario)

**User Story:** As the operator demoing MockNest, I want a test that mocks an LLM-style token stream (Server-Sent Events) and proves the tokens trickle to the client like a chatbot reply, so the test both guards the regression and serves as the canonical streaming demo.

#### Acceptance Criteria

1. THE post-deploy suite SHALL include a test that registers a `text/event-stream` mock whose body is a multi-event LLM-style transcript (many `data: {"delta":"<token>"}` events terminated by `data: [DONE]`), with `chunkedDribbleDelay` set so tokens are spread over a visible duration (~5s) and each chunk is large enough (≥~16KB) to avoid network coalescing.
2. THE test SHALL stream the response with a timestamping reader and assert progressive arrival: the first event arrives well before the last (first-to-last spread ≥ ~50% of `totalDuration`), events arrive over time rather than in a single terminal burst.
3. THE test SHALL assert the SSE framing and full transcript (including the terminal `data: [DONE]`) are delivered intact and byte-identical to what was registered.
4. THE test SHALL be documented as the repeatable streaming demo (the `curl --http1.1 -N` command that visibly trickles tokens).

### Requirement 5: Verify on the live deployment

**User Story:** As the operator, I want to confirm the fix on the deployed stack, so that the demo works end to end.

#### Acceptance Criteria

1. AFTER deploying the fix, a `curl --http1.1 -N` request to a dribble mock with a multi-KB-per-chunk body SHALL show chunks arriving spread across the dribble duration (verified with a timestamping reader), NOT a single burst.
2. A response body exceeding 6MB SHALL be delivered in full (confirming end-to-end streaming, which the buffered path cannot do).
