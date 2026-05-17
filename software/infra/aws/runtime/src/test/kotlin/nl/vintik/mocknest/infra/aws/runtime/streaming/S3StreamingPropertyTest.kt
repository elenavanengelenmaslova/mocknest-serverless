package nl.vintik.mocknest.infra.aws.runtime.streaming

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.stream.Stream

/**
 * Property 5: S3 Streaming With Bounded Buffer
 *
 * For any S3 object of size 1 byte to 50MB, streaming the object to an OutputStream
 * via the S3 streaming adapter SHALL write the complete object content byte-for-byte
 * to the output, and no single read buffer allocation SHALL exceed 1MB (1,048,576 bytes).
 *
 * **Validates: Requirements 7.3**
 */
@Tag("Feature: response-streaming, Property 5: S3 Streaming With Bounded Buffer")
class S3StreamingPropertyTest {

    private val s3Client = mockk<S3Client>()
    private val streamer = S3ResponseStreamer(s3Client, "test-bucket")

    @AfterEach
    fun tearDown() {
        clearMocks(s3Client)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("s3StreamingTestCases")
    fun `Given S3 object When streaming to output Then complete content is written byte-for-byte`(
        description: String,
        objectSize: Int,
    ) {
        // Given — generate deterministic content with repeating byte pattern
        val content = ByteArray(objectSize) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()

        coEvery {
            s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>())
        } coAnswers {
            val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
            handler(GetObjectResponse {
                body = ByteStream.fromBytes(content)
            })
        }

        // When
        val success = streamer.streamToOutput("test-key", output)

        // Then — complete content written byte-for-byte
        assertTrue(success, "Streaming should succeed for: $description")
        assertArrayEquals(
            content,
            output.toByteArray(),
            "Streamed content must be byte-identical to original for: $description"
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("s3StreamingTestCases")
    fun `Given S3 object When streaming Then no single write exceeds 1MB buffer`(
        description: String,
        objectSize: Int,
    ) {
        // Given — generate deterministic content
        val content = ByteArray(objectSize) { (it % 256).toByte() }
        val writeTracker = WriteTrackingOutputStream()

        coEvery {
            s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>())
        } coAnswers {
            val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
            handler(GetObjectResponse {
                body = ByteStream.fromBytes(content)
            })
        }

        // When
        val success = streamer.streamToOutput("test-key", writeTracker)

        // Then — no single write call exceeds 1MB
        assertTrue(success, "Streaming should succeed for: $description")
        assertTrue(
            writeTracker.maxWriteSize <= S3ResponseStreamer.BUFFER_SIZE,
            "No single write should exceed ${S3ResponseStreamer.BUFFER_SIZE} bytes (1MB), " +
                "but max write was ${writeTracker.maxWriteSize} bytes for: $description"
        )
        // Also verify total bytes written equals object size
        assertTrue(
            writeTracker.totalBytesWritten == objectSize.toLong(),
            "Total bytes written (${writeTracker.totalBytesWritten}) must equal object size ($objectSize) for: $description"
        )
    }

    /**
     * OutputStream wrapper that tracks the maximum size of individual write calls
     * to verify the bounded buffer constraint.
     */
    private class WriteTrackingOutputStream : OutputStream() {
        var maxWriteSize: Int = 0
            private set
        var totalBytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            if (1 > maxWriteSize) maxWriteSize = 1
            totalBytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len > maxWriteSize) maxWriteSize = len
            totalBytesWritten += len
        }

        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }
    }

    companion object {
        @JvmStatic
        fun s3StreamingTestCases(): Stream<Arguments> = Stream.of(
            // 1. Minimal: 1 byte
            Arguments.of("1 byte", 1),
            // 2. Small: 100 bytes
            Arguments.of("100 bytes", 100),
            // 3. 1KB
            Arguments.of("1KB", 1024),
            // 4. 100KB
            Arguments.of("100KB", 100 * 1024),
            // 5. Exactly 1MB (buffer boundary)
            Arguments.of("1MB", 1024 * 1024),
            // 6. 5MB (multiple full buffers)
            Arguments.of("5MB", 5 * 1024 * 1024),
            // 7. 10MB
            Arguments.of("10MB", 10 * 1024 * 1024),
            // 8. 25MB
            Arguments.of("25MB", 25 * 1024 * 1024),
            // 9. 50MB
            Arguments.of("50MB", 50 * 1024 * 1024),
            // 10. 1MB + 1 byte (just over buffer boundary)
            Arguments.of("1MB + 1 byte", 1024 * 1024 + 1),
        )
    }
}
