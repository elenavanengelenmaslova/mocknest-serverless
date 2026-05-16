package nl.vintik.mocknest.infra.aws.core.streaming

import kotlinx.serialization.Serializable

/**
 * Internal serialization model representing the API Gateway proxy request JSON format.
 * Used by [ApiGatewayRequestParser] to deserialize the raw Lambda InputStream.
 */
@Serializable
internal data class ApiGatewayProxyRequest(
    val httpMethod: String? = null,
    val path: String? = null,
    val headers: Map<String, String>? = null,
    val multiValueHeaders: Map<String, List<String>>? = null,
    val queryStringParameters: Map<String, String>? = null,
    val multiValueQueryStringParameters: Map<String, List<String>>? = null,
    val body: String? = null,
    val isBase64Encoded: Boolean = false
)
