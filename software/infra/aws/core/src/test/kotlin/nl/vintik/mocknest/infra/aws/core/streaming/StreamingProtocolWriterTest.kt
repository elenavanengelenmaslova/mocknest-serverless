package nl.vintik.mocknest.infra.aws.core.streaming

import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [StreamingProtocolWriter].
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.7, 3.8
 */
class StreamingProtocolWriterTest {

    private val writer = StreamingProtocolWriter()

    @Nested
    inner class MetadataSerialization {

        @Test
        fun `Given response with status 100 When writing Then metadata contains statusCode 100`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode(100),
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"statusCode\":100"), "Expected statusCode 100 in metadata: $metadataJson")
        }

        @Test
        fun `Given response with status 200 When writing Then metadata contains statusCode 200`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf("Content-Type" to listOf("application/json")),
                body = "hello"
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"statusCode\":200"), "Expected statusCode 200 in metadata: $metadataJson")
        }

        @Test
        fun `Given response with status 404 When writing Then metadata contains statusCode 404`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.NOT_FOUND,
                headers = mapOf("Content-Type" to listOf("text/html")),
                body = "Not Found"
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"statusCode\":404"), "Expected statusCode 404 in metadata: $metadataJson")
        }

        @Test
        fun `Given response with status 599 When writing Then metadata contains statusCode 599`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode(599),
                headers = emptyMap(),
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"statusCode\":599"), "Expected statusCode 599 in metadata: $metadataJson")
        }

        @Test
        fun `Given response with headers When writing Then metadata contains headers`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf(
                    "Content-Type" to listOf("application/json"),
                    "X-Custom" to listOf("custom-value")
                ),
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"Content-Type\":\"application/json\""), "Expected Content-Type header in metadata")
            assertTrue(metadataJson.contains("\"X-Custom\":\"custom-value\""), "Expected X-Custom header in metadata")
        }

        @Test
        fun `Given response with null headers When writing Then metadata contains empty headers object`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = null,
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"headers\":{}"), "Expected empty headers in metadata: $metadataJson")
        }

        @Test
        fun `Given writeMetadataAndDelimiter with status and headers When writing Then metadata is correct`() {
            val output = ByteArrayOutputStream()

            writer.writeMetadataAndDelimiter(201, mapOf("Location" to "/resource/1"), output)

            val bytes = output.toByteArray()
            val metadataEnd = findNullDelimiterStart(bytes)
            val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
            assertTrue(metadataJson.contains("\"statusCode\":201"), "Expected statusCode 201")
            assertTrue(metadataJson.contains("\"Location\":\"/resource/1\""), "Expected Location header")
        }
    }

    @Nested
    inner class NullDelimiter {

        @Test
        fun `Given any response When writing Then null delimiter is exactly 8 bytes`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "test"
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val nullBytes = bytes.sliceArray(delimiterStart until delimiterStart + 8)
            assertContentEquals(ByteArray(8), nullBytes, "Null delimiter must be exactly 8 zero bytes")
        }

        @Test
        fun `Given response with empty body When writing Then null delimiter is exactly 8 bytes`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val nullBytes = bytes.sliceArray(delimiterStart until delimiterStart + 8)
            assertContentEquals(ByteArray(8), nullBytes, "Null delimiter must be exactly 8 zero bytes")
        }

        @Test
        fun `Given writeMetadataAndDelimiter When writing Then null delimiter is exactly 8 bytes`() {
            val output = ByteArrayOutputStream()

            writer.writeMetadataAndDelimiter(200, emptyMap(), output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val nullBytes = bytes.sliceArray(delimiterStart until delimiterStart + 8)
            assertContentEquals(ByteArray(8), nullBytes, "Null delimiter must be exactly 8 zero bytes")
        }

        @Test
        fun `Given response When writing Then delimiter appears immediately after metadata JSON`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = "body"
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            // Verify the byte before the delimiter is the closing brace of JSON
            val lastMetadataByte = bytes[delimiterStart - 1].toInt().toChar()
            assertEquals('}', lastMetadataByte, "Last metadata byte should be closing brace of JSON object")
        }
    }

    @Nested
    inner class BodyWriting {

        @Test
        fun `Given response with null body When writing Then no bytes after delimiter`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = null
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val afterDelimiter = bytes.size - (delimiterStart + 8)
            assertEquals(0, afterDelimiter, "No body bytes should follow the delimiter for null body")
        }

        @Test
        fun `Given response with empty string body When writing Then no bytes after delimiter`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = ""
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val bodyBytes = bytes.sliceArray(delimiterStart + 8 until bytes.size)
            assertContentEquals(ByteArray(0), bodyBytes, "Empty body should produce no bytes after delimiter")
        }

        @Test
        fun `Given response with small body When writing Then body bytes follow delimiter`() {
            val bodyContent = "Hello, World!"
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = bodyContent
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val bodyBytes = bytes.sliceArray(delimiterStart + 8 until bytes.size)
            val actualBody = String(bodyBytes, Charsets.UTF_8)
            assertEquals(bodyContent, actualBody, "Body content should match after delimiter")
        }

        @Test
        fun `Given response with large body When writing Then all body bytes are written`() {
            val bodyContent = "A".repeat(100_000)
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = bodyContent
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val bodyBytes = bytes.sliceArray(delimiterStart + 8 until bytes.size)
            val actualBody = String(bodyBytes, Charsets.UTF_8)
            assertEquals(bodyContent, actualBody, "Large body content should be fully written")
            assertEquals(100_000, bodyBytes.size, "Body byte count should match")
        }

        @Test
        fun `Given response with unicode body When writing Then body is UTF-8 encoded`() {
            val bodyContent = "Привет мир! 🌍 日本語テスト"
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = bodyContent
            )
            val output = ByteArrayOutputStream()

            writer.write(response, output)

            val bytes = output.toByteArray()
            val delimiterStart = findNullDelimiterStart(bytes)
            val bodyBytes = bytes.sliceArray(delimiterStart + 8 until bytes.size)
            val actualBody = String(bodyBytes, Charsets.UTF_8)
            assertEquals(bodyContent, actualBody, "Unicode body should be correctly encoded as UTF-8")
        }
    }

    @Nested
    inner class MetadataTooLarge {

        @Test
        fun `Given metadata exceeding 16376 bytes When writing Then throws MetadataTooLargeException`() {
            // Create headers large enough to exceed the limit
            val largeHeaders = (1..500).associate { "X-Header-$it" to listOf("V".repeat(50)) }
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = largeHeaders,
                body = "body"
            )
            val output = ByteArrayOutputStream()

            val exception = assertFailsWith<MetadataTooLargeException> {
                writer.write(response, output)
            }

            val message = exception.message
            assertNotNull(message)
            assertTrue(
                message.contains("exceeds the streaming protocol limit"),
                "Exception message should mention the limit: $message"
            )
        }

        @Test
        fun `Given writeMetadataAndDelimiter with oversized metadata When writing Then throws MetadataTooLargeException`() {
            val largeHeaders = (1..500).associate { "X-Header-$it" to "V".repeat(50) }
            val output = ByteArrayOutputStream()

            val exception = assertFailsWith<MetadataTooLargeException> {
                writer.writeMetadataAndDelimiter(200, largeHeaders, output)
            }

            val message = exception.message
            assertNotNull(message)
            assertTrue(
                message.contains("exceeds the streaming protocol limit"),
                "Exception message should mention the limit: $message"
            )
        }

        @Test
        fun `Given metadata just under 16376 bytes When writing Then succeeds without exception`() {
            // Create headers that are large but within the limit
            val headers = (1..100).associate { "H-$it" to listOf("V".repeat(10)) }
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = headers,
                body = "ok"
            )
            val output = ByteArrayOutputStream()

            // Should not throw
            writer.write(response, output)

            val bytes = output.toByteArray()
            assertTrue(bytes.isNotEmpty(), "Output should contain data")
        }
    }

    @Nested
    inner class IoErrorPropagation {

        @Test
        fun `Given OutputStream that throws on write When writing metadata Then IOException propagates`() {
            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = mapOf("Content-Type" to listOf("text/plain")),
                body = "test"
            )
            val failingOutput = object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("Simulated I/O failure")
                }

                override fun write(b: ByteArray) {
                    throw IOException("Simulated I/O failure")
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    throw IOException("Simulated I/O failure")
                }
            }

            assertFailsWith<IOException> {
                writer.write(response, failingOutput)
            }
        }

        @Test
        fun `Given OutputStream that fails on body write When writing Then IOException propagates`() {
            var writeCount = 0
            val failOnBodyOutput = object : OutputStream() {
                override fun write(b: Int) {
                    writeCount++
                    if (writeCount > 2) throw IOException("Body write failure")
                }

                override fun write(b: ByteArray) {
                    writeCount++
                    if (writeCount > 2) throw IOException("Body write failure")
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    writeCount++
                    if (writeCount > 2) throw IOException("Body write failure")
                }
            }

            val response = HttpResponse(
                statusCode = HttpStatusCode.OK,
                headers = emptyMap(),
                body = "test body"
            )

            assertFailsWith<IOException> {
                writer.write(response, failOnBodyOutput)
            }
        }

        @Test
        fun `Given writeMetadataAndDelimiter with failing OutputStream When writing Then IOException propagates`() {
            val failingOutput = object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("Simulated I/O failure")
                }

                override fun write(b: ByteArray) {
                    throw IOException("Simulated I/O failure")
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    throw IOException("Simulated I/O failure")
                }
            }

            assertFailsWith<IOException> {
                writer.writeMetadataAndDelimiter(200, mapOf("Key" to "Value"), failingOutput)
            }
        }
    }

    /**
     * Finds the start index of the 8 null byte delimiter in the byte array.
     * Scans for 8 consecutive zero bytes.
     */
    private fun findNullDelimiterStart(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 8) {
            if (bytes.sliceArray(i until i + 8).all { it == 0.toByte() }) {
                return i
            }
        }
        throw AssertionError("Could not find 8 null byte delimiter in output")
    }
}
