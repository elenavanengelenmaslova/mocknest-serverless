package nl.vintik.mocknest.infra.aws.runtime.streaming

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.stream.Stream

/**
 * Property-based tests for [ChunkedResponseWriter.writeChunkedFromStream].
 *
 * Covers:
 * - Property 6: Bounded memory during InputStream-based streaming
 * - Property 7: Streaming round-trip preserves body content byte-for-byte
 * - Property 8: Chunked delivery distributes bytes across configured number of chunks
 */
class ChunkedResponseWriterPropertyTest {

    private val writer = ChunkedResponseWriter()

    // ========================================================================
    // Property 6: Bounded memory during InputStream-based streaming
    // ========================================================================

    /**
     * Property 6: Bounded memory during InputStream-based streaming
     *
     * For any input stream of size S (where S > 0), the writeChunkedFromStream method
     * SHALL never hold more than 1MB (1,048,576 bytes) of response body content in memory
     * at any time, regardless of the total body size or number of chunks configured.
     *
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Tag("Feature: zero-memory-streaming, Property 6: Bounded memory during InputStream-based streaming")
    @ParameterizedTest(name = "Property 6: size={0} bytes, chunks={1}")
    @MethodSource("boundedMemoryTestCases")
    fun `Given InputStream of varying size When writeChunkedFromStream Then buffer never exceeds STREAM_BUFFER_SIZE`(
        size: Int,
        numberOfChunks: Int,
    ) = runTest {
        // Given — create a BoundedReadInputStream that tracks max read size
        val data = ByteArray(size) { (it % 256).toByte() }
        val boundedInput = BoundedReadInputStream(ByteArrayInputStream(data))
        val output = ByteArrayOutputStream()

        // When
        writer.writeChunkedFromStream(boundedInput, data.size.toLong(), numberOfChunks, 0L, output)

        // Then — no single read request exceeds STREAM_BUFFER_SIZE (1MB)
        assertTrue(boundedInput.maxReadSize <= ChunkedResponseWriter.STREAM_BUFFER_SIZE) {
            "Maximum read size was ${boundedInput.maxReadSize} bytes, " +
                "which exceeds STREAM_BUFFER_SIZE of ${ChunkedResponseWriter.STREAM_BUFFER_SIZE} bytes " +
                "(input size=$size, numberOfChunks=$numberOfChunks)"
        }

        // Also verify data integrity: output bytes match input bytes
        assertArrayEquals(data, output.toByteArray()) {
            "Output bytes must match input bytes for size=$size, numberOfChunks=$numberOfChunks"
        }
    }

    /**
     * Property 6 (large sizes): Bounded memory during InputStream-based streaming
     *
     * For the 50MB test case, uses a ByteCountingOutputStream to avoid OOM in tests
     * while still verifying the bounded read property.
     *
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Tag("Feature: zero-memory-streaming, Property 6: Bounded memory during InputStream-based streaming")
    @ParameterizedTest(name = "Property 6 (large): size={0} bytes, chunks={1}")
    @MethodSource("boundedMemoryLargeTestCases")
    fun `Given large InputStream When writeChunkedFromStream Then buffer never exceeds STREAM_BUFFER_SIZE without full output comparison`(
        size: Int,
        numberOfChunks: Int,
    ) = runTest {
        // Given — for large sizes, use a counting output to avoid OOM in tests
        val data = ByteArray(size) { (it % 256).toByte() }
        val boundedInput = BoundedReadInputStream(ByteArrayInputStream(data))
        val countingOutput = ByteCountingOutputStream()

        // When
        writer.writeChunkedFromStream(boundedInput, data.size.toLong(), numberOfChunks, 0L, countingOutput)

        // Then — no single read request exceeds STREAM_BUFFER_SIZE (1MB)
        assertTrue(boundedInput.maxReadSize <= ChunkedResponseWriter.STREAM_BUFFER_SIZE) {
            "Maximum read size was ${boundedInput.maxReadSize} bytes, " +
                "which exceeds STREAM_BUFFER_SIZE of ${ChunkedResponseWriter.STREAM_BUFFER_SIZE} bytes " +
                "(input size=$size, numberOfChunks=$numberOfChunks)"
        }

        // Verify total bytes written matches input size (data integrity without storing full output)
        assertEquals(size.toLong(), countingOutput.totalBytesWritten) {
            "Total bytes written (${countingOutput.totalBytesWritten}) must equal input size ($size)"
        }
    }

    // ========================================================================
    // Property 7: Streaming round-trip preserves body content byte-for-byte
    // ========================================================================

    /**
     * Property 7: Streaming round-trip preserves body content byte-for-byte
     *
     * For any body content, streaming it through writeChunkedFromStream with any valid
     * numberOfChunks and totalDurationMs SHALL produce output that matches the original
     * content byte-for-byte when all chunks are concatenated.
     *
     * **Validates: Requirements 7.2, 7.4**
     */
    @Tag("Feature: zero-memory-streaming, Property 7: Streaming round-trip preserves body content byte-for-byte")
    @ParameterizedTest(name = "Property 7: {0}")
    @MethodSource("roundTripTestCases")
    fun `Given random body content When writeChunkedFromStream Then output matches input byte-for-byte`(
        description: String,
        bodySize: Int,
        numberOfChunks: Int,
    ) = runTest {
        // Given
        val data = ByteArray(bodySize) { (it % 256).toByte() }
        val input = ByteArrayInputStream(data)
        val output = ByteArrayOutputStream()

        // When
        writer.writeChunkedFromStream(input, data.size.toLong(), numberOfChunks, 0L, output)

        // Then — output matches input byte-for-byte
        assertArrayEquals(
            data,
            output.toByteArray(),
            "Streamed output must be byte-identical to original input for: $description"
        )
    }

    // ========================================================================
    // Property 8: Chunked delivery distributes bytes across configured number of chunks
    // ========================================================================

    /**
     * Property 8: Chunked delivery distributes bytes across configured number of chunks
     *
     * For any body of size S > 0 and configured numberOfChunks N >= 2, the
     * writeChunkedFromStream method SHALL deliver the body in at most N write-flush
     * cycles, with each cycle writing approximately S/N bytes (±1 byte for remainder
     * distribution).
     *
     * **Validates: Requirements 7.3**
     */
    @Tag("Feature: zero-memory-streaming, Property 8: Chunked delivery distributes bytes across configured number of chunks")
    @ParameterizedTest(name = "Property 8: {0}")
    @MethodSource("chunkDistributionTestCases")
    fun `Given body size and chunk count When writeChunkedFromStream Then flush count equals numberOfChunks`(
        description: String,
        bodySize: Int,
        numberOfChunks: Int,
    ) = runTest {
        // Given
        val data = ByteArray(bodySize) { (it % 256).toByte() }
        val input = ByteArrayInputStream(data)
        val flushCountingOutput = FlushCountingOutputStream()

        // When
        writer.writeChunkedFromStream(input, data.size.toLong(), numberOfChunks, 0L, flushCountingOutput)

        // Then — for normal cases where body >= numberOfChunks, flush count equals numberOfChunks
        if (bodySize >= numberOfChunks) {
            assertEquals(
                numberOfChunks,
                flushCountingOutput.flushCount,
                "Flush count must equal numberOfChunks ($numberOfChunks) for: $description"
            )
        } else {
            // Edge case: body smaller than chunk count — flush count may be less than numberOfChunks
            // because the loop terminates when all bytes are written
            assertTrue(
                flushCountingOutput.flushCount <= numberOfChunks,
                "Flush count (${flushCountingOutput.flushCount}) must be at most numberOfChunks ($numberOfChunks) for: $description"
            )
            assertTrue(
                flushCountingOutput.flushCount >= 1,
                "Flush count must be at least 1 for non-empty body for: $description"
            )
        }

        // Also verify all bytes were written
        assertEquals(
            bodySize.toLong(),
            flushCountingOutput.totalBytesWritten,
            "Total bytes written must equal body size for: $description"
        )
    }

    // ========================================================================
    // Helper classes
    // ========================================================================

    /**
     * Custom InputStream wrapper that tracks the maximum `len` parameter passed to
     * `read(byte[], int, int)` and asserts that no single read request exceeds
     * [ChunkedResponseWriter.STREAM_BUFFER_SIZE] (1MB).
     *
     * Wraps a [ByteArrayInputStream] and verifies bounded memory usage.
     */
    private class BoundedReadInputStream(private val delegate: ByteArrayInputStream) : InputStream() {
        var maxReadSize = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len > maxReadSize) {
                maxReadSize = len
            }
            return delegate.read(b, off, len)
        }

        override fun available(): Int = delegate.available()

        override fun close() = delegate.close()
    }

    /**
     * OutputStream that counts total bytes written without storing them in memory.
     * Used for large test cases (50MB) to avoid OOM while still verifying data integrity
     * through byte count comparison.
     */
    private class ByteCountingOutputStream : OutputStream() {
        var totalBytesWritten = 0L
            private set

        override fun write(b: Int) {
            totalBytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            totalBytesWritten += len
        }

        override fun flush() {
            // no-op
        }
    }

    /**
     * OutputStream that counts flush() calls to verify chunk distribution.
     */
    private class FlushCountingOutputStream : OutputStream() {
        var flushCount = 0
            private set
        var totalBytesWritten = 0L
            private set

        override fun write(b: Int) {
            totalBytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            totalBytesWritten += len
        }

        override fun flush() {
            flushCount++
        }
    }

    // ========================================================================
    // Test data providers
    // ========================================================================

    companion object {

        @JvmStatic
        fun boundedMemoryTestCases(): Stream<Arguments> = Stream.of(
            // 1KB (1024 bytes) with various chunk counts
            Arguments.of(1024, 2),
            Arguments.of(1024, 3),
            Arguments.of(1024, 5),
            Arguments.of(1024, 10),
            Arguments.of(1024, 50),
            Arguments.of(1024, 100),
            // 100KB with various chunk counts
            Arguments.of(100 * 1024, 2),
            Arguments.of(100 * 1024, 3),
            Arguments.of(100 * 1024, 5),
            Arguments.of(100 * 1024, 10),
            Arguments.of(100 * 1024, 50),
            Arguments.of(100 * 1024, 100),
            // 1MB (1048576 bytes) with various chunk counts
            Arguments.of(1048576, 2),
            Arguments.of(1048576, 3),
            Arguments.of(1048576, 5),
            Arguments.of(1048576, 10),
            Arguments.of(1048576, 50),
            Arguments.of(1048576, 100),
            // 5MB with various chunk counts
            Arguments.of(5 * 1048576, 2),
            Arguments.of(5 * 1048576, 3),
            Arguments.of(5 * 1048576, 5),
            Arguments.of(5 * 1048576, 10),
            Arguments.of(5 * 1048576, 50),
            Arguments.of(5 * 1048576, 100),
            // 10MB with various chunk counts
            Arguments.of(10 * 1048576, 2),
            Arguments.of(10 * 1048576, 3),
            Arguments.of(10 * 1048576, 5),
            Arguments.of(10 * 1048576, 10),
            Arguments.of(10 * 1048576, 50),
            Arguments.of(10 * 1048576, 100),
        )

        @JvmStatic
        fun boundedMemoryLargeTestCases(): Stream<Arguments> = Stream.of(
            // 50MB with various chunk counts — uses ByteCountingOutputStream to avoid OOM
            Arguments.of(50 * 1048576, 2),
            Arguments.of(50 * 1048576, 3),
            Arguments.of(50 * 1048576, 5),
            Arguments.of(50 * 1048576, 10),
            Arguments.of(50 * 1048576, 50),
            Arguments.of(50 * 1048576, 100),
        )

        @JvmStatic
        fun roundTripTestCases(): Stream<Arguments> = Stream.of(
            // Small bodies
            Arguments.of("1 byte / 2 chunks", 1, 2),
            Arguments.of("100 bytes / 3 chunks", 100, 3),
            Arguments.of("1KB / 5 chunks", 1024, 5),
            // Medium bodies
            Arguments.of("100KB / 4 chunks", 100 * 1024, 4),
            Arguments.of("1MB / 2 chunks", 1024 * 1024, 2),
            Arguments.of("1MB / 10 chunks", 1024 * 1024, 10),
            // Large bodies
            Arguments.of("5MB / 5 chunks", 5 * 1024 * 1024, 5),
            Arguments.of("5MB / 50 chunks", 5 * 1024 * 1024, 50),
            Arguments.of("10MB / 10 chunks", 10 * 1024 * 1024, 10),
            // Edge cases
            Arguments.of("10 bytes / 20 chunks (more chunks than bytes)", 10, 20),
            Arguments.of("3 bytes / 2 chunks (remainder)", 3, 2),
            Arguments.of("7 bytes / 3 chunks (remainder)", 7, 3),
            Arguments.of("1MB + 1 byte / 3 chunks (just over buffer)", 1024 * 1024 + 1, 3),
            Arguments.of("999 bytes / 13 chunks (prime-ish)", 999, 13),
            Arguments.of("256 bytes / 256 chunks (1 byte per chunk)", 256, 256),
        )

        @JvmStatic
        fun chunkDistributionTestCases(): Stream<Arguments> = Stream.of(
            // Small body with various chunk counts
            Arguments.of("100 bytes / 2 chunks", 100, 2),
            Arguments.of("100 bytes / 3 chunks", 100, 3),
            Arguments.of("100 bytes / 5 chunks", 100, 5),
            // Medium body (1MB) with various chunk counts
            Arguments.of("1MB / 2 chunks", 1024 * 1024, 2),
            Arguments.of("1MB / 5 chunks", 1024 * 1024, 5),
            Arguments.of("1MB / 10 chunks", 1024 * 1024, 10),
            Arguments.of("1MB / 50 chunks", 1024 * 1024, 50),
            // Large body (5MB) with various chunk counts
            Arguments.of("5MB / 2 chunks", 5 * 1024 * 1024, 2),
            Arguments.of("5MB / 5 chunks", 5 * 1024 * 1024, 5),
            Arguments.of("5MB / 10 chunks", 5 * 1024 * 1024, 10),
            Arguments.of("5MB / 100 chunks", 5 * 1024 * 1024, 100),
            // Edge case: body smaller than chunk count
            Arguments.of("10 bytes / 20 chunks (body < chunks)", 10, 20),
            // Additional diverse cases
            Arguments.of("1KB / 7 chunks (remainder)", 1024, 7),
            Arguments.of("500KB / 3 chunks", 500 * 1024, 3),
            Arguments.of("2MB / 25 chunks", 2 * 1024 * 1024, 25),
            Arguments.of("10MB / 100 chunks", 10 * 1024 * 1024, 100),
        )
    }
}
