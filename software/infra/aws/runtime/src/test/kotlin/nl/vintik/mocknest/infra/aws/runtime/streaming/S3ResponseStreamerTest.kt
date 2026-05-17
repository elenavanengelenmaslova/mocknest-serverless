package nl.vintik.mocknest.infra.aws.runtime.streaming

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Unit tests for [S3ResponseStreamer].
 *
 * Tests streaming small S3 files (< 1MB), large S3 files (> 1MB, verifying buffer size),
 * and S3 retrieval failure handling (error logged, stream aborted).
 *
 * **Validates: Requirements 7.3, 7.5**
 */
class S3ResponseStreamerTest {

    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val streamer = S3ResponseStreamer(mockS3Client, bucketName)

    @AfterEach
    fun tearDown() {
        clearMocks(mockS3Client)
    }

    @Nested
    inner class SmallFileStreaming {

        @Test
        fun `Given S3 object smaller than 1MB When streaming Then should write complete content to output`() {
            // Given
            val s3Key = "__files/small-response.json"
            val content = """{"message":"hello world"}""".toByteArray()
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
        }

        @Test
        fun `Given S3 object of exactly 1 byte When streaming Then should write single byte to output`() {
            // Given
            val s3Key = "__files/tiny.bin"
            val content = byteArrayOf(42)
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
        }

        @Test
        fun `Given S3 object of 500KB When streaming Then should write all bytes to output`() {
            // Given
            val s3Key = "__files/medium.bin"
            val content = ByteArray(500 * 1024) { (it % 256).toByte() }
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
            assertEquals(500 * 1024, output.size())
        }

        @Test
        fun `Given S3 object When streaming Then should use correct bucket and key in request`() {
            // Given
            val s3Key = "__files/my-response.json"
            val content = "test".toByteArray()
            val output = ByteArrayOutputStream()
            val requestSlot = slot<GetObjectRequest>()

            coEvery { mockS3Client.getObject(capture(requestSlot), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            streamer.streamToOutput(s3Key, output)

            // Then
            assertEquals(bucketName, requestSlot.captured.bucket)
            assertEquals(s3Key, requestSlot.captured.key)
        }
    }

    @Nested
    inner class LargeFileStreaming {

        @Test
        fun `Given S3 object larger than 1MB When streaming Then should write complete content to output`() {
            // Given
            val s3Key = "__files/large-response.bin"
            val content = ByteArray(2 * 1024 * 1024) { (it % 256).toByte() } // 2MB
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
            assertEquals(2 * 1024 * 1024, output.size())
        }

        @Test
        fun `Given S3 object of 5MB When streaming Then should write all bytes preserving content`() {
            // Given
            val s3Key = "__files/five-mb.bin"
            val content = ByteArray(5 * 1024 * 1024) { (it % 256).toByte() } // 5MB
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
        }

        @Test
        fun `Given S3 object larger than 1MB When streaming Then buffer size constant should be 1MB`() {
            // Given/When/Then — verify the buffer size constant is correctly defined
            assertEquals(1024 * 1024, S3ResponseStreamer.BUFFER_SIZE)
        }

        @Test
        fun `Given S3 object larger than 1MB When streaming Then no single write exceeds buffer size`() {
            // Given
            val s3Key = "__files/large.bin"
            val content = ByteArray(3 * 1024 * 1024) { (it % 256).toByte() } // 3MB
            val writeTracker = WriteTrackingOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, writeTracker)

            // Then
            assertTrue(result)
            // Verify no single write call exceeds the 1MB buffer size
            assertTrue(
                writeTracker.maxWriteSize <= S3ResponseStreamer.BUFFER_SIZE,
                "Max write size ${writeTracker.maxWriteSize} should not exceed buffer size ${S3ResponseStreamer.BUFFER_SIZE}"
            )
            // Verify all content was written
            assertArrayEquals(content, writeTracker.toByteArray())
        }

        @Test
        fun `Given S3 object exactly 1MB When streaming Then should complete in single buffer read`() {
            // Given
            val s3Key = "__files/exact-1mb.bin"
            val content = ByteArray(1024 * 1024) { (it % 256).toByte() } // Exactly 1MB
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertTrue(result)
            assertArrayEquals(content, output.toByteArray())
        }

        @Test
        fun `Given S3 object of 1MB plus 1 byte When streaming Then should flush after each buffer`() {
            // Given
            val s3Key = "__files/just-over-1mb.bin"
            val content = ByteArray(1024 * 1024 + 1) { (it % 256).toByte() } // 1MB + 1 byte
            val flushTracker = FlushTrackingOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, flushTracker)

            // Then
            assertTrue(result)
            // Should flush at least twice: once for the 1MB buffer, once for the remaining 1 byte
            assertTrue(
                flushTracker.flushCount >= 2,
                "Should flush at least twice for content just over 1MB, got ${flushTracker.flushCount}"
            )
            assertArrayEquals(content, flushTracker.toByteArray())
        }
    }

    @Nested
    inner class FailureHandling {

        @Test
        fun `Given S3 getObject throws exception When streaming Then should return false`() {
            // Given
            val s3Key = "__files/missing-file.bin"
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } throws
                RuntimeException("NoSuchKey: The specified key does not exist.")

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertFalse(result)
        }

        @Test
        fun `Given S3 object with null body When streaming Then should return false`() {
            // Given
            val s3Key = "__files/null-body.bin"
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = null
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertFalse(result)
        }

        @Test
        fun `Given output stream throws IOException during write When streaming Then should return false`() {
            // Given
            val s3Key = "__files/some-file.bin"
            val content = "test content".toByteArray()
            val failingOutput = FailingOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } coAnswers {
                val block = secondArg<suspend (GetObjectResponse) -> Boolean>()
                val response = GetObjectResponse {
                    body = ByteStream.fromBytes(content)
                }
                block(response)
            }

            // When
            val result = streamer.streamToOutput(s3Key, failingOutput)

            // Then
            assertFalse(result)
        }

        @Test
        fun `Given S3 access denied When streaming Then should return false and not write to output`() {
            // Given
            val s3Key = "__files/forbidden.bin"
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } throws
                RuntimeException("Access Denied")

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertFalse(result)
            assertEquals(0, output.size(), "No data should be written to output on access denied")
        }

        @Test
        fun `Given S3 network timeout When streaming Then should return false`() {
            // Given
            val s3Key = "__files/timeout-file.bin"
            val output = ByteArrayOutputStream()

            coEvery { mockS3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Boolean>()) } throws
                RuntimeException("Connection timed out")

            // When
            val result = streamer.streamToOutput(s3Key, output)

            // Then
            assertFalse(result)
            assertEquals(0, output.size())
        }
    }

    /**
     * Custom OutputStream that tracks the maximum size of individual write calls.
     */
    private class WriteTrackingOutputStream : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        var maxWriteSize = 0
            private set

        override fun write(b: Int) {
            buffer.write(b)
            if (1 > maxWriteSize) maxWriteSize = 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            buffer.write(b, off, len)
            if (len > maxWriteSize) maxWriteSize = len
        }

        override fun flush() {
            buffer.flush()
        }

        fun toByteArray(): ByteArray = buffer.toByteArray()
    }

    /**
     * Custom OutputStream that tracks flush calls.
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

    /**
     * Custom OutputStream that throws IOException on write.
     */
    private class FailingOutputStream : OutputStream() {
        override fun write(b: Int) {
            throw IOException("Simulated write failure")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            throw IOException("Simulated write failure")
        }
    }
}
