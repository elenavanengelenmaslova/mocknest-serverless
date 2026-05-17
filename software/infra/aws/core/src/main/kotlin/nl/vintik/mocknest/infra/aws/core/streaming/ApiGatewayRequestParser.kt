package nl.vintik.mocknest.infra.aws.core.streaming

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpRequest
import java.io.InputStream
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Parses the API Gateway proxy request JSON from a raw Lambda InputStream.
 * Replaces SDK-based APIGatewayProxyRequestEvent deserialization.
 */
class ApiGatewayRequestParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses the raw InputStream into an HttpRequest.
     * Handles base64-encoded bodies, multi-value headers, and multi-value query parameters.
     *
     * @throws RequestParseException if the JSON is malformed or missing required fields
     */
    fun parse(input: InputStream): HttpRequest {
        val rawJson = readInputStream(input)
        val proxyRequest = deserialize(rawJson)
        return toHttpRequest(proxyRequest)
    }

    private fun readInputStream(input: InputStream): String =
        runCatching {
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { e ->
            logger.error(e) { "Failed to read input stream" }
            throw RequestParseException("Failed to read input stream", e)
        }

    private fun deserialize(rawJson: String): ApiGatewayProxyRequest =
        runCatching {
            json.decodeFromString<ApiGatewayProxyRequest>(rawJson)
        }.getOrElse { e ->
            logger.error(e) { "Failed to parse API Gateway request JSON" }
            throw RequestParseException("Malformed JSON in API Gateway request", e)
        }

    private fun toHttpRequest(proxyRequest: ApiGatewayProxyRequest): HttpRequest {
        val httpMethod = proxyRequest.httpMethod
            ?: throw RequestParseException("Missing required field: httpMethod")

        val path = proxyRequest.path
            ?: throw RequestParseException("Missing required field: path")

        val method = resolveMethod(httpMethod)
        val body = decodeBody(proxyRequest.body, proxyRequest.isBase64Encoded)

        val headers = proxyRequest.headers ?: emptyMap()
        val multiValueHeaders = proxyRequest.multiValueHeaders ?: emptyMap()
        val queryParameters = proxyRequest.queryStringParameters ?: emptyMap()
        val multiValueQueryParameters = proxyRequest.multiValueQueryStringParameters ?: emptyMap()

        return HttpRequest(
            method = method,
            path = path,
            headers = headers,
            multiValueHeaders = multiValueHeaders,
            queryParameters = queryParameters,
            multiValueQueryParameters = multiValueQueryParameters,
            body = body
        )
    }

    private fun resolveMethod(httpMethod: String): HttpMethod =
        runCatching {
            HttpMethod.resolve(httpMethod)
        }.getOrElse { e ->
            throw RequestParseException("Invalid HTTP method: $httpMethod", e)
        }

    private fun decodeBody(body: String?, isBase64Encoded: Boolean): String? {
        if (body == null) return null
        if (!isBase64Encoded) return body

        return runCatching {
            String(Base64.getDecoder().decode(body), Charsets.UTF_8)
        }.getOrElse { e ->
            logger.error(e) { "Failed to decode base64-encoded body" }
            throw RequestParseException("Failed to decode base64-encoded body", e)
        }
    }
}
