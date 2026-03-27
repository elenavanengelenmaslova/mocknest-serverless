package nl.vintik.mocknest.infra.aws.generation.graphql

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.domain.generation.GraphQLIntrospectionException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val logger = KotlinLogging.logger {}

/**
 * OkHttp-based implementation of GraphQL introspection client.
 * Executes the standard GraphQL introspection query against a live endpoint.
 */
class GraphQLIntrospectionClient : GraphQLIntrospectionClientInterface {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val INTROSPECTION_QUERY = """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                types {
                  kind
                  name
                  description
                  fields(includeDeprecated: false) {
                    name
                    description
                    args {
                      name
                      description
                      type {
                        kind
                        name
                        ofType {
                          kind
                          name
                          ofType {
                            kind
                            name
                          }
                        }
                      }
                    }
                    type {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                        }
                      }
                    }
                  }
                  inputFields {
                    name
                    description
                    type {
                      kind
                      name
                      ofType {
                        kind
                        name
                      }
                    }
                  }
                  enumValues {
                    name
                    description
                  }
                }
              }
            }
        """.trimIndent()
    }

    override suspend fun introspect(
        endpointUrl: String,
        headers: Map<String, String>,
        timeoutMs: Long
    ): String {
        logger.info { "Executing GraphQL introspection query: endpoint=$endpointUrl" }

        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val requestBody = """{"query":${Json.encodeToString(INTROSPECTION_QUERY)}}"""
            .toRequestBody(JSON_MEDIA_TYPE)

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        val request = requestBuilder.build()

        return runCatching {
            val responseBody = client.newCall(request).executeAsync()
            parseIntrospectionResponse(responseBody, endpointUrl)
        }.onFailure { exception ->
            when (exception) {
                is GraphQLIntrospectionException -> logger.error(exception) {
                    "GraphQL introspection failed: endpoint=$endpointUrl"
                }
                else -> logger.error(exception) {
                    "Unexpected error during GraphQL introspection: endpoint=$endpointUrl"
                }
            }
        }.getOrElse { exception ->
            when (exception) {
                is GraphQLIntrospectionException -> throw exception
                is java.net.UnknownHostException ->
                    throw GraphQLIntrospectionException("Network failure: endpoint unreachable - ${exception.message}", exception)
                is java.net.SocketTimeoutException ->
                    throw GraphQLIntrospectionException("Request timeout after ${timeoutMs}ms: endpoint=$endpointUrl", exception)
                is javax.net.ssl.SSLException ->
                    throw GraphQLIntrospectionException("SSL/TLS error connecting to endpoint: ${exception.message}", exception)
                is IOException ->
                    throw GraphQLIntrospectionException("Network failure: ${exception.message}", exception)
                else -> throw GraphQLIntrospectionException("Introspection failed: ${exception.message}", exception)
            }
        }
    }

    private fun parseIntrospectionResponse(responseBody: String, endpointUrl: String): String {
        if (responseBody.isBlank()) {
            throw GraphQLIntrospectionException("Empty response from endpoint: $endpointUrl")
        }

        val jsonElement = runCatching {
            Json.parseToJsonElement(responseBody).jsonObject
        }.getOrElse {
            throw GraphQLIntrospectionException("Invalid GraphQL response structure: response is not valid JSON")
        }

        // Check for HTTP-level errors embedded in GraphQL errors field
        val errorsElement = jsonElement["errors"]
        if (errorsElement != null) {
            val firstError = runCatching {
                errorsElement.jsonArray.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
            }.getOrNull()

            if (firstError != null && firstError.contains("introspection", ignoreCase = true)) {
                throw GraphQLIntrospectionException("Introspection disabled on endpoint: $firstError")
            }
        }

        // Validate the response contains schema data
        val dataElement = jsonElement["data"]
        if (dataElement == null) {
            throw GraphQLIntrospectionException("Invalid GraphQL response structure: missing 'data' field")
        }

        val schemaElement = runCatching {
            dataElement.jsonObject["__schema"]
        }.getOrNull()

        if (schemaElement == null) {
            throw GraphQLIntrospectionException("Invalid GraphQL response structure: missing '__schema' in data")
        }

        logger.info { "GraphQL introspection successful: endpoint=$endpointUrl, response size=${responseBody.length} bytes" }
        return responseBody
    }
}

/**
 * Suspending extension to execute OkHttp call asynchronously.
 */
private suspend fun Call.executeAsync(): String = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                when {
                    resp.code == 429 ->
                        continuation.resumeWithException(
                            GraphQLIntrospectionException("Rate limited by endpoint (HTTP 429)")
                        )
                    !resp.isSuccessful ->
                        continuation.resumeWithException(
                            GraphQLIntrospectionException("Endpoint returned HTTP ${resp.code}: ${resp.message}")
                        )
                    else -> {
                        val body = resp.body.string()
                        continuation.resume(body)
                    }
                }
            }
        }
    })

    continuation.invokeOnCancellation { cancel() }
}
