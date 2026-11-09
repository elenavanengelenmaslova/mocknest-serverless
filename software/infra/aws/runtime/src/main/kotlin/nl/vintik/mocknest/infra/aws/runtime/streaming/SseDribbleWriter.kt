package nl.vintik.mocknest.infra.aws.runtime.streaming

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.lambda.streaming.sse.PacedSseEmitter
import nl.vintik.lambda.streaming.sse.PaddedSseWriter
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Delivers a `text/event-stream` dribble response as padded, paced Server-Sent Events so that
 * small delayed chunks actually trickle to the client through API Gateway REST + the managed Java
 * Lambda streaming runtime.
 *
 * ### Why the hand-rolled chunk/flush loop was not enough
 * The Lambda streaming runtime holds small writes in an internal (~100KB) buffer and only forwards
 * them when the buffer fills or the stream closes. A per-chunk `flush()` between small writes is
 * therefore ignored, so a naive dribble arrives as a single terminal burst. The `streaming-sse`
 * [PaddedSseWriter] inflates each SSE event past that buffer threshold with ignorable SSE comment
 * padding, and [PacedSseEmitter] applies the inter-chunk delay — together they make small delayed
 * chunks reach the client progressively. See the module README for the padding/size trade-off
 * (the padding inflates the wire body, e.g. a ~15KB answer can become several MB).
 *
 * ### Scope
 * This path is used ONLY for `text/event-stream` bodies — padding is SSE-comment-based and is only
 * ignorable by SSE clients. Non-SSE dribble bodies stay on [ChunkedResponseWriter]'s
 * progressive-by-size delivery (no per-chunk pacing); see [StreamingRuntimeLambdaHandler].
 *
 * ### Pacing
 * The per-event delay is derived from the dribble config as `totalDurationMs / numberOfChunks`
 * (matching [ChunkedResponseWriter]'s existing semantics) and clamped to the emitter's supported
 * range. The emitter pauses only *between* events (never before the first, never after the last).
 *
 * The [sleep] function is injected (defaulting to the emitter's own [Thread.sleep]) so tests can
 * drive pacing with coroutine virtual time and assert the schedule with zero wall-clock delay.
 */
class SseDribbleWriter(
    private val headPadBytes: Int = PaddedSseWriter.DEFAULT_HEAD_PAD_BYTES,
    private val tailPadBytes: Int = PaddedSseWriter.DEFAULT_TAIL_PAD_BYTES,
    private val headPadEvents: Int = PaddedSseWriter.DEFAULT_HEAD_PAD_EVENTS,
) {

    /**
     * Writes an in-memory body as [numberOfChunks] padded SSE events paced over [totalDurationMs],
     * followed by the terminal `event: end` / `data: [DONE]` event.
     *
     * @param sleep inter-event pause function; defaults to [Thread.sleep]. Tests inject a
     *   coroutine-backed sleeper for virtual time.
     */
    fun writeSseChunked(
        body: ByteArray,
        numberOfChunks: Int,
        totalDurationMs: Long,
        output: OutputStream,
        sleep: (Long) -> Unit = Thread::sleep,
    ) {
        val emitter = newEmitter(totalDurationMs, numberOfChunks, sleep)
        val chunkSizes = chunkHelper.calculateChunkSizes(body.size, numberOfChunks)

        var offset = 0
        chunkSizes.forEach { chunkSize ->
            val text = if (chunkSize > 0) {
                String(body, offset, chunkSize, Charsets.UTF_8).also { offset += chunkSize }
            } else {
                ""
            }
            emitter.emit(output, text)
        }
        emitter.end(output)
        logger.debug { "SSE dribble (in-memory) complete: events=${emitter.eventsWritten()}" }
    }

    /**
     * Streams an S3-backed body as [numberOfChunks] padded SSE events paced over [totalDurationMs],
     * reading at most [ChunkedResponseWriter.STREAM_BUFFER_SIZE] (1MB) at a time so the whole object
     * is never materialized in memory (preserves the zero-memory-streaming guarantee).
     *
     * @param sleep inter-event pause function; defaults to [Thread.sleep].
     * @throws PrematureEofException if [input] ends before [bodySize] bytes are read.
     */
    fun writeSseChunkedFromStream(
        input: InputStream,
        bodySize: Long,
        numberOfChunks: Int,
        totalDurationMs: Long,
        output: OutputStream,
        sleep: (Long) -> Unit = Thread::sleep,
    ) {
        val emitter = newEmitter(totalDurationMs, numberOfChunks, sleep)
        val targetChunkBytes = chunkHelper.calculateStreamChunkSize(bodySize, numberOfChunks)
        val bufferSize = minOf(
            maxOf(targetChunkBytes, 1L),
            ChunkedResponseWriter.STREAM_BUFFER_SIZE.toLong(),
        ).toInt()
        val buffer = ByteArray(bufferSize)

        var totalBytesRead = 0L
        while (totalBytesRead < bodySize) {
            // Read exactly one chunk (up to targetChunkBytes) into a fresh builder, emitting it as
            // a single SSE event. The read loop stays bounded by the 1MB buffer.
            val chunkText = StringBuilder()
            var chunkBytesRead = 0L
            while (chunkBytesRead < targetChunkBytes && totalBytesRead < bodySize) {
                val toRead = minOf(
                    buffer.size.toLong(),
                    targetChunkBytes - chunkBytesRead,
                    bodySize - totalBytesRead,
                ).toInt()
                if (toRead <= 0) break

                val bytesRead = input.read(buffer, 0, toRead)
                if (bytesRead == -1) {
                    throw PrematureEofException(
                        "InputStream ended prematurely: expected $bodySize bytes but only received $totalBytesRead bytes"
                    )
                }
                chunkText.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                chunkBytesRead += bytesRead
                totalBytesRead += bytesRead
            }
            emitter.emit(output, chunkText.toString())
        }
        emitter.end(output)
        logger.debug { "SSE dribble (S3) complete: events=${emitter.eventsWritten()}" }
    }

    private fun newEmitter(
        totalDurationMs: Long,
        numberOfChunks: Int,
        sleep: (Long) -> Unit,
    ): PacedSseEmitter {
        val writer = PaddedSseWriter(headPadBytes, tailPadBytes, headPadEvents)
        val delayMillis = perEventDelayMillis(totalDurationMs, numberOfChunks, writer.hasPadding())
        return PacedSseEmitter(writer, delayMillis, sleep)
    }

    /**
     * Maps the dribble config onto the emitter's per-event delay.
     *
     * - `totalDurationMs / numberOfChunks` is the inter-chunk gap (same as [ChunkedResponseWriter]).
     * - A zero/negative gap becomes [PacedSseEmitter.NO_DELAY] (no pacing).
     * - The gap is clamped to `[MIN_DELAY_MILLIS, MAX_DELAY_MILLIS]` because the emitter rejects
     *   out-of-range delays at construction.
     * - When the writer carries no padding (all pad tiers zero) pacing would silently do nothing,
     *   so we force [PacedSseEmitter.NO_DELAY] to avoid the emitter's guard rejecting a delay.
     */
    internal fun perEventDelayMillis(totalDurationMs: Long, numberOfChunks: Int, hasPadding: Boolean): Int {
        if (!hasPadding || numberOfChunks <= 0) return PacedSseEmitter.NO_DELAY
        val gap = totalDurationMs / numberOfChunks
        return when {
            gap <= 0 -> PacedSseEmitter.NO_DELAY
            gap < PacedSseEmitter.MIN_DELAY_MILLIS -> PacedSseEmitter.MIN_DELAY_MILLIS
            gap > PacedSseEmitter.MAX_DELAY_MILLIS -> PacedSseEmitter.MAX_DELAY_MILLIS
            else -> gap.toInt()
        }
    }

    private companion object {
        private val chunkHelper = ChunkedResponseWriter()
    }
}
