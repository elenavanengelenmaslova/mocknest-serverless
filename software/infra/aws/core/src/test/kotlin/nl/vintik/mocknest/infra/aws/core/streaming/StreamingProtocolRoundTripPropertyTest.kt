package nl.vintik.mocknest.infra.aws.core.streaming

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.util.stream.Stream

/**
 * Property 1: Streaming Protocol Round-Trip
 *
 * For any HttpResponse with a status code in 100–599, a headers map of 0–50 string-to-string
 * entries (total serialized metadata ≤ 16,376 bytes), and a body of 0 to 10MB, writing the
 * response through StreamingProtocolWriter and then parsing the resulting byte stream SHALL
 * yield a byte-identical body, an identical status code, and identical header name-value pairs.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
@Tag("Feature: response-streaming, Property 1: Streaming Protocol Round-Trip")
class StreamingProtocolRoundTripPropertyTest {

    private val writer = StreamingProtocolWriter()

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTripTestCases")
    fun `Given HttpResponse When written via StreamingProtocolWriter Then parsed stream yields identical body status and headers`(
        description: String,
        response: HttpResponse,
    ) {
        // Write response to byte stream
        val outputStream = ByteArrayOutputStream()
        writer.write(response, outputStream)
        val bytes = outputStream.toByteArray()

        // Find the 8 null byte delimiter
        val delimiterIndex = findNullDelimiter(bytes)
        assertTrue(delimiterIndex >= 0, "8 null byte delimiter not found in output")

        // Parse metadata JSON before the delimiter
        val metadataBytes = bytes.copyOfRange(0, delimiterIndex)
        val metadataJson = Json.parseToJsonElement(String(metadataBytes, Charsets.UTF_8)).jsonObject

        // Extract body bytes after the delimiter
        val bodyStartIndex = delimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE
        val bodyBytes = if (bodyStartIndex < bytes.size) {
            bytes.copyOfRange(bodyStartIndex, bytes.size)
        } else {
            ByteArray(0)
        }

        // Verify status code matches
        val parsedStatusCode = metadataJson["statusCode"]!!.jsonPrimitive.int
        assertEquals(
            response.statusCode.value,
            parsedStatusCode,
            "Status code mismatch for test case: $description"
        )

        // Verify headers match (writer flattens multi-value headers to last value)
        val parsedHeaders = metadataJson["headers"]!!.jsonObject
        val expectedFlatHeaders = response.headers
            ?.flatMap { (name, values) -> values.map { name to it } }
            ?.toMap()
            ?: emptyMap()

        assertEquals(
            expectedFlatHeaders.size,
            parsedHeaders.size,
            "Header count mismatch for test case: $description"
        )
        for ((name, value) in expectedFlatHeaders) {
            assertEquals(
                value,
                parsedHeaders[name]?.jsonPrimitive?.content,
                "Header '$name' value mismatch for test case: $description"
            )
        }

        // Verify body is byte-identical
        val expectedBodyBytes = response.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        assertArrayEquals(
            expectedBodyBytes,
            bodyBytes,
            "Body bytes mismatch for test case: $description"
        )
    }

    /**
     * Finds the index of the first occurrence of 8 consecutive null bytes in the byte array.
     * Returns -1 if not found.
     */
    private fun findNullDelimiter(bytes: ByteArray): Int {
        val delimiterSize = StreamingProtocolWriter.NULL_DELIMITER_SIZE
        for (i in 0..bytes.size - delimiterSize) {
            var found = true
            for (j in 0 until delimiterSize) {
                if (bytes[i + j] != 0.toByte()) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    companion object {
        @JvmStatic
        fun roundTripTestCases(): Stream<Arguments> = Stream.of(
            // 1. Empty body
            Arguments.of(
                "empty body with 200 status",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("text/plain")),
                    body = null
                )
            ),
            // 2. 1-byte body
            Arguments.of(
                "1-byte body",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("application/octet-stream")),
                    body = "X"
                )
            ),
            // 3. 1KB body
            Arguments.of(
                "1KB body",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("text/plain")),
                    body = "A".repeat(1024)
                )
            ),
            // 4. 1MB body
            Arguments.of(
                "1MB body",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("application/json")),
                    body = "B".repeat(1_048_576)
                )
            ),
            // 5. 10MB body
            Arguments.of(
                "10MB body",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Length" to listOf("10485760")),
                    body = "C".repeat(10_485_760)
                )
            ),
            // 6. Unicode body
            Arguments.of(
                "unicode body with emoji and CJK characters",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("text/plain; charset=utf-8")),
                    body = "Hello 世界! 🌍🎉 Ñoño café résumé naïve"
                )
            ),
            // 7. Binary-like body (string with various byte patterns)
            Arguments.of(
                "binary body with special byte patterns",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("application/octet-stream")),
                    body = buildString {
                        for (i in 1..255) {
                            append(i.toChar())
                        }
                    }
                )
            ),
            // 8. Many headers (50)
            Arguments.of(
                "50 headers",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = (1..50).associate { "X-Header-$it" to listOf("value-$it") },
                    body = "body with many headers"
                )
            ),
            // 9. Single header
            Arguments.of(
                "single header",
                HttpResponse(
                    statusCode = HttpStatusCode(301),
                    headers = mapOf("Location" to listOf("https://example.com/new-location")),
                    body = null
                )
            ),
            // 10. Special characters in headers
            Arguments.of(
                "special characters in header values",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf(
                        "X-Special" to listOf("value with spaces & symbols: !@#%^*()"),
                        "X-Quoted" to listOf("\"quoted value\""),
                        "X-Unicode-Header" to listOf("café-résumé")
                    ),
                    body = "special headers test"
                )
            ),
            // 11. Status code 100 (Continue)
            Arguments.of(
                "status code 100",
                HttpResponse(
                    statusCode = HttpStatusCode(100),
                    headers = emptyMap(),
                    body = null
                )
            ),
            // 12. Status code 404 (Not Found)
            Arguments.of(
                "status code 404",
                HttpResponse(
                    statusCode = HttpStatusCode(404),
                    headers = mapOf("Content-Type" to listOf("application/json")),
                    body = """{"error":"not found","message":"Resource does not exist"}"""
                )
            ),
            // 13. Status code 500 (Internal Server Error)
            Arguments.of(
                "status code 500",
                HttpResponse(
                    statusCode = HttpStatusCode(500),
                    headers = mapOf("Content-Type" to listOf("text/plain")),
                    body = "Internal Server Error"
                )
            ),
            // 14. Status code 599 (max valid)
            Arguments.of(
                "status code 599 (max valid)",
                HttpResponse(
                    statusCode = HttpStatusCode(599),
                    headers = mapOf("X-Custom" to listOf("max-status")),
                    body = "max status code response"
                )
            ),
            // 15. Empty headers map
            Arguments.of(
                "empty headers map",
                HttpResponse(
                    statusCode = HttpStatusCode(204),
                    headers = emptyMap(),
                    body = null
                )
            ),
            // 16. Body with null bytes
            Arguments.of(
                "body with null bytes",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf("Content-Type" to listOf("application/octet-stream")),
                    body = "before\u0000null\u0000bytes\u0000after"
                )
            ),
            // 17. Large header values
            Arguments.of(
                "large header values",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = mapOf(
                        "X-Large-Header" to listOf("V".repeat(4096)),
                        "Authorization" to listOf("Bearer ${"t".repeat(2048)}")
                    ),
                    body = "response with large headers"
                )
            ),
            // 18. Null headers (null map)
            Arguments.of(
                "null headers map",
                HttpResponse(
                    statusCode = HttpStatusCode(200),
                    headers = null,
                    body = "response with null headers"
                )
            ),
        )
    }
}
