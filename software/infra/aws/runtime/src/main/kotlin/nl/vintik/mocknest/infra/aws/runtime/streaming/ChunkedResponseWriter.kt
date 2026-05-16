package nl.vintik.mocknest.infra.aws.runtime.streaming

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Splits a response body into chunks with delays between writes,
 * simulating Server-Sent Events streaming behavior.
 *
 * Uses Thread.sleep for delays since this runs in Lambda (not coroutines).
 */
class ChunkedResponseWriter {

    companion object {
        private const val IDLE_TIMEOUT_WARNING_THRESHOLD_MS = 270_000L
    }

    /**
     * Writes the body in chunks with delays.
     *
     * @param body The complete response body bytes
     * @param numberOfChunks Number of chunks to split into (must be >= 2)
     * @param totalDurationMs Total delay in milliseconds distributed between chunks
     * @param output The OutputStream to write chunks to
     */
    fun writeChunked(body: ByteArray, numberOfChunks: Int, totalDurationMs: Long, output: OutputStream) {
        if (totalDurationMs > IDLE_TIMEOUT_WARNING_THRESHOLD_MS) {
            logger.warn {
                "Chunked dribble delay totalDuration ${totalDurationMs}ms exceeds ${IDLE_TIMEOUT_WARNING_THRESHOLD_MS}ms. " +
                    "This may exceed the 5-minute streaming idle timeout and cause the connection to be closed."
            }
        }

        val chunkSizes = calculateChunkSizes(body.size, numberOfChunks)
        val delayBetweenChunks = totalDurationMs / numberOfChunks

        var offset = 0
        chunkSizes.forEachIndexed { index, chunkSize ->
            if (index > 0 && delayBetweenChunks > 0) {
                Thread.sleep(delayBetweenChunks)
            }

            if (chunkSize > 0) {
                output.write(body, offset, chunkSize)
                offset += chunkSize
            }
            output.flush()
        }
    }

    /**
     * Calculates chunk sizes for a given body length and chunk count.
     *
     * Algorithm:
     * - If numberOfChunks <= body size: each chunk is bodySize/numberOfChunks bytes,
     *   remainder goes to last chunk.
     * - If numberOfChunks > body size: one byte per chunk for available bytes,
     *   remaining chunks are empty (size 0).
     *
     * @return List of chunk sizes in bytes, with exactly numberOfChunks entries
     */
    internal fun calculateChunkSizes(bodySize: Int, numberOfChunks: Int): List<Int> {
        if (bodySize == 0) {
            return List(numberOfChunks) { 0 }
        }

        if (numberOfChunks > bodySize) {
            // Edge case: more chunks than bytes — one byte per chunk for available bytes,
            // remaining chunks are empty
            return List(numberOfChunks) { index ->
                if (index < bodySize) 1 else 0
            }
        }

        val baseChunkSize = bodySize / numberOfChunks
        val remainder = bodySize % numberOfChunks

        return List(numberOfChunks) { index ->
            if (index == numberOfChunks - 1) {
                baseChunkSize + remainder
            } else {
                baseChunkSize
            }
        }
    }
}
