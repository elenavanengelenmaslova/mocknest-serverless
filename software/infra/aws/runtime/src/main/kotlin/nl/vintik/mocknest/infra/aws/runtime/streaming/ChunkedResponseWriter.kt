package nl.vintik.mocknest.infra.aws.runtime.streaming

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Thrown when the InputStream ends before the expected number of bytes have been read.
 * This prevents infinite loops when bodySize is larger than the actual stream content.
 */
class PrematureEofException(message: String) : java.io.IOException(message)

/**
 * Splits a response body into chunks with delays between writes,
 * simulating Server-Sent Events streaming behavior.
 *
 * Uses coroutine [delay] for inter-chunk pauses, enabling virtual time in tests.
 */
class ChunkedResponseWriter {

    companion object {
        private const val IDLE_TIMEOUT_WARNING_THRESHOLD_MS = 270_000L
        const val STREAM_BUFFER_SIZE = 1024 * 1024 // 1MB
    }

    /**
     * Writes the body in chunks with delays.
     *
     * @param body The complete response body bytes
     * @param numberOfChunks Number of chunks to split into (must be >= 2)
     * @param totalDurationMs Total delay in milliseconds distributed between chunks
     * @param output The OutputStream to write chunks to
     */
    suspend fun writeChunked(body: ByteArray, numberOfChunks: Int, totalDurationMs: Long, output: OutputStream) {
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
                delay(delayBetweenChunks)
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

    /**
     * Streams from an InputStream in bounded chunks with delays.
     * Reads at most [STREAM_BUFFER_SIZE] (1MB) at a time, writes to output,
     * then delays before the next chunk.
     *
     * @param input The InputStream to read from (e.g., S3 object stream)
     * @param bodySize Total size of the body in bytes (from S3 HEAD)
     * @param numberOfChunks Number of chunks to deliver
     * @param totalDurationMs Total delay distributed between chunks
     * @param output The OutputStream to write chunks to
     */
    suspend fun writeChunkedFromStream(
        input: InputStream,
        bodySize: Long,
        numberOfChunks: Int,
        totalDurationMs: Long,
        output: OutputStream,
    ) {
        if (totalDurationMs > IDLE_TIMEOUT_WARNING_THRESHOLD_MS) {
            logger.warn {
                "Chunked dribble delay totalDuration ${totalDurationMs}ms exceeds ${IDLE_TIMEOUT_WARNING_THRESHOLD_MS}ms. " +
                    "This may exceed the 5-minute streaming idle timeout."
            }
        }

        val chunkSize = calculateStreamChunkSize(bodySize, numberOfChunks)
        val delayBetweenChunks = totalDurationMs / numberOfChunks
        val bufferSize = minOf(chunkSize, STREAM_BUFFER_SIZE.toLong()).toInt()
        val buffer = ByteArray(bufferSize)

        var totalBytesWritten = 0L
        var chunkIndex = 0

        while (totalBytesWritten < bodySize) {
            if (chunkIndex > 0 && delayBetweenChunks > 0) {
                delay(delayBetweenChunks)
            }

            var chunkBytesWritten = 0L
            val targetChunkBytes = chunkSize

            while (chunkBytesWritten < targetChunkBytes) {
                val toRead = minOf(
                    buffer.size.toLong(),
                    targetChunkBytes - chunkBytesWritten,
                    bodySize - totalBytesWritten
                ).toInt()
                if (toRead <= 0) break

                val bytesRead = input.read(buffer, 0, toRead)
                if (bytesRead == -1) {
                    throw PrematureEofException(
                        "InputStream ended prematurely: expected $bodySize bytes but only received $totalBytesWritten bytes"
                    )
                }

                output.write(buffer, 0, bytesRead)
                chunkBytesWritten += bytesRead
                totalBytesWritten += bytesRead
            }
            output.flush()
            chunkIndex++
        }
    }

    /**
     * Calculates the target chunk size for stream-based chunking using ceiling division.
     *
     * @param bodySize Total body size in bytes
     * @param numberOfChunks Number of chunks to divide into
     * @return Target chunk size in bytes, or 0 if bodySize is 0 or numberOfChunks <= 0
     */
    internal fun calculateStreamChunkSize(bodySize: Long, numberOfChunks: Int): Long {
        if (bodySize == 0L || numberOfChunks <= 0) return 0L
        return (bodySize + numberOfChunks - 1) / numberOfChunks // ceiling division
    }
}
