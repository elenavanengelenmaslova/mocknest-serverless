package nl.vintik.mocknest.infra.aws.runtime.streaming

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Unit tests for [ChunkedResponseWriter.writeChunkedFromStream] and [ChunkedResponseWriter.calculateStreamChunkSize].
 *
 * Tests data integrity across various sizes, bounded memory usage (1MB buffer),
 * flush behavior (one per chunk), and calculateStreamChunkSize edge cases.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
class ChunkedResponseWriterStreamTest {

    private val writer = ChunkedResponseWriter()

    @Nested
    inner class DataIntegrity {

        @Test
        fun `Given 1KB input stream When writeChunkedFromStream Then output matches input byte-for-byte`() = runTest {
            // Given
            val data = ByteArray(1024) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 3, 0L, output)

            // Then
            assertArrayEquals(data, output.toByteArray())
        }

        @Test
        fun `Given 5MB input stream When writeChunkedFromStream Then output matches input byte-for-byte`() = runTest {
            // Given
            val size = 5 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 5, 0L, output)

            // Then
            assertArrayEquals(data, output.toByteArray())
        }

        @Test
        fun `Given 10MB input stream When writeChunkedFromStream Then output matches input byte-for-byte`() = runTest {
            // Given
            val size = 10 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 10, 0L, output)

            // Then
            assertArrayEquals(data, output.toByteArray())
        }

        @Test
        fun `Given 1KB input stream with delays When writeChunkedFromStream Then output matches input byte-for-byte`() = runTest {
            // Given
            val data = ByteArray(1024) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 4, 1000L, output)

            // Then
            assertArrayEquals(data, output.toByteArray())
        }
    }

    @Nested
    inner class BoundedMemoryUsage {

        @Test
        fun `Given 5MB input stream When writeChunkedFromStream Then no single read exceeds 1MB`() = runTest {
            // Given
            val size = 5 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val trackingInput = ReadSizeTrackingInputStream(ByteArrayInputStream(data))
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(trackingInput, data.size.toLong(), 5, 0L, output)

            // Then
            assertTrue(trackingInput.maxReadSize <= ChunkedResponseWriter.STREAM_BUFFER_SIZE) {
                "Maximum read size was ${trackingInput.maxReadSize} bytes, " +
                    "which exceeds STREAM_BUFFER_SIZE of ${ChunkedResponseWriter.STREAM_BUFFER_SIZE} bytes"
            }
            // Also verify data integrity
            assertArrayEquals(data, output.toByteArray())
        }

        @Test
        fun `Given 10MB input stream When writeChunkedFromStream Then no single read exceeds 1MB`() = runTest {
            // Given
            val size = 10 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val trackingInput = ReadSizeTrackingInputStream(ByteArrayInputStream(data))
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(trackingInput, data.size.toLong(), 3, 0L, output)

            // Then
            assertTrue(trackingInput.maxReadSize <= ChunkedResponseWriter.STREAM_BUFFER_SIZE) {
                "Maximum read size was ${trackingInput.maxReadSize} bytes, " +
                    "which exceeds STREAM_BUFFER_SIZE of ${ChunkedResponseWriter.STREAM_BUFFER_SIZE} bytes"
            }
            assertArrayEquals(data, output.toByteArray())
        }

        @Test
        fun `Given input stream smaller than buffer When writeChunkedFromStream Then read size does not exceed body size`() = runTest {
            // Given
            val size = 512 // 512 bytes, well under 1MB
            val data = ByteArray(size) { (it % 256).toByte() }
            val trackingInput = ReadSizeTrackingInputStream(ByteArrayInputStream(data))
            val output = ByteArrayOutputStream()

            // When
            writer.writeChunkedFromStream(trackingInput, data.size.toLong(), 2, 0L, output)

            // Then
            assertTrue(trackingInput.maxReadSize <= size) {
                "Maximum read size was ${trackingInput.maxReadSize} bytes, " +
                    "which exceeds body size of $size bytes"
            }
            assertArrayEquals(data, output.toByteArray())
        }
    }

    @Nested
    inner class FlushBehavior {

        @Test
        fun `Given 1KB input and 3 chunks When writeChunkedFromStream Then flush is called 3 times`() = runTest {
            // Given
            val data = ByteArray(1024) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val flushTrackingOutput = FlushTrackingOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 3, 0L, flushTrackingOutput)

            // Then
            assertEquals(3, flushTrackingOutput.flushCount)
        }

        @Test
        fun `Given 5MB input and 5 chunks When writeChunkedFromStream Then flush is called 5 times`() = runTest {
            // Given
            val size = 5 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val flushTrackingOutput = FlushTrackingOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 5, 0L, flushTrackingOutput)

            // Then
            assertEquals(5, flushTrackingOutput.flushCount)
        }

        @Test
        fun `Given 10MB input and 10 chunks When writeChunkedFromStream Then flush is called 10 times`() = runTest {
            // Given
            val size = 10 * 1024 * 1024
            val data = ByteArray(size) { (it % 256).toByte() }
            val input = ByteArrayInputStream(data)
            val flushTrackingOutput = FlushTrackingOutputStream()

            // When
            writer.writeChunkedFromStream(input, data.size.toLong(), 10, 0L, flushTrackingOutput)

            // Then
            assertEquals(10, flushTrackingOutput.flushCount)
        }
    }

    @Nested
    inner class CalculateStreamChunkSize {

        @Test
        fun `Given 0 body size and 5 chunks When calculateStreamChunkSize Then returns 0`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(0L, 5)

            // Then
            assertEquals(0L, result)
        }

        @Test
        fun `Given 10 bytes and 1 chunk When calculateStreamChunkSize Then returns 10`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(10L, 1)

            // Then
            assertEquals(10L, result)
        }

        @Test
        fun `Given 10 bytes and 3 chunks When calculateStreamChunkSize Then returns 4 using ceiling division`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(10L, 3)

            // Then — ceiling(10/3) = 4
            assertEquals(4L, result)
        }

        @Test
        fun `Given 5 bytes and 10 chunks When calculateStreamChunkSize Then returns 1 as minimum chunk size`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(5L, 10)

            // Then — ceiling(5/10) = 1
            assertEquals(1L, result)
        }

        @Test
        fun `Given positive body size and 0 chunks When calculateStreamChunkSize Then returns 0`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(100L, 0)

            // Then
            assertEquals(0L, result)
        }

        @Test
        fun `Given positive body size and negative chunks When calculateStreamChunkSize Then returns 0`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(100L, -1)

            // Then
            assertEquals(0L, result)
        }

        @Test
        fun `Given evenly divisible body size When calculateStreamChunkSize Then returns exact division`() {
            // Given / When
            val result = writer.calculateStreamChunkSize(100L, 5)

            // Then — 100/5 = 20 exactly
            assertEquals(20L, result)
        }
    }

    /**
     * Custom InputStream wrapper that tracks the maximum number of bytes
     * requested in any single read() call, verifying bounded memory usage.
     */
    private class ReadSizeTrackingInputStream(private val delegate: InputStream) : InputStream() {
        var maxReadSize = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len > maxReadSize) {
                maxReadSize = len
            }
            return delegate.read(b, off, len)
        }
    }

    /**
     * Custom OutputStream that tracks flush calls for verification.
     */
    private class FlushTrackingOutputStream : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        var flushCount = 0
            private set

        override fun write(b: Int) {
            buffer.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            buffer.write(b, off, len)
        }

        override fun flush() {
            flushCount++
            buffer.flush()
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }
}
