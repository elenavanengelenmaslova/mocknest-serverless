package nl.vintik.mocknest.infra.aws.core.streaming

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.vintik.mocknest.domain.core.HttpResponse
import java.io.OutputStream

/**
 * Writes the API Gateway streaming protocol:
 * 1. Metadata JSON (statusCode + headers)
 * 2. 8 null bytes delimiter
 * 3. Response body bytes
 *
 * Content type for the OutputStream: application/vnd.awslambda.http-integration-response
 */
class StreamingProtocolWriter {

    companion object {
        const val CONTENT_TYPE = "application/vnd.awslambda.http-integration-response"
        const val NULL_DELIMITER_SIZE = 8
        const val MAX_METADATA_SIZE = 16_376 // 16KB - 8 bytes for delimiter
    }

    /**
     * Writes a complete streaming response (metadata + delimiter + body).
     *
     * @throws MetadataTooLargeException if serialized metadata exceeds MAX_METADATA_SIZE
     * @throws java.io.IOException propagated from OutputStream on write failure
     */
    fun write(response: HttpResponse, output: OutputStream) {
        val headers = response.headers
            ?.flatMap { (name, values) -> values.map { name to it } }
            ?.toMap()
            ?: emptyMap()

        val metadataBytes = serializeAndValidateMetadata(response.statusCode.value, headers)

        output.write(metadataBytes)
        output.write(ByteArray(NULL_DELIMITER_SIZE))

        response.body?.let { body ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Writes only metadata + delimiter, leaving the body to be written separately.
     * Used by ChunkedResponseWriter for SSE streaming.
     *
     * @throws MetadataTooLargeException if serialized metadata exceeds MAX_METADATA_SIZE
     * @throws java.io.IOException propagated from OutputStream on write failure
     */
    fun writeMetadataAndDelimiter(statusCode: Int, headers: Map<String, String>, output: OutputStream) {
        val metadataBytes = serializeAndValidateMetadata(statusCode, headers)

        output.write(metadataBytes)
        output.write(ByteArray(NULL_DELIMITER_SIZE))
    }

    private fun serializeAndValidateMetadata(statusCode: Int, headers: Map<String, String>): ByteArray {
        val headersJsonObject = JsonObject(headers.map { (k, v) -> k to JsonPrimitive(v) }.toMap())

        val metadataJson = buildJsonObject {
            put("statusCode", statusCode)
            put("headers", headersJsonObject)
        }

        val metadataBytes = metadataJson.toString().toByteArray(Charsets.UTF_8)

        if (metadataBytes.size > MAX_METADATA_SIZE) {
            throw MetadataTooLargeException(
                "Serialized metadata size ${metadataBytes.size} bytes exceeds the streaming protocol limit of $MAX_METADATA_SIZE bytes"
            )
        }

        return metadataBytes
    }
}
