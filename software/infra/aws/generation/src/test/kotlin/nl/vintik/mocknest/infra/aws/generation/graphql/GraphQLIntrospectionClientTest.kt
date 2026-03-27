package nl.vintik.mocknest.infra.aws.generation.graphql

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.domain.generation.GraphQLIntrospectionException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GraphQLIntrospectionClientTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var client: GraphQLIntrospectionClient
    private lateinit var baseUrl: String

    companion object {
        private val VALID_INTROSPECTION_RESPONSE = """
            {
              "data": {
                "__schema": {
                  "queryType": { "name": "Query" },
                  "mutationType": null,
                  "types": [
                    {
                      "kind": "OBJECT",
                      "name": "Query",
                      "description": null,
                      "fields": [
                        {
                          "name": "hello",
                          "description": null,
                          "args": [],
                          "type": { "kind": "SCALAR", "name": "String", "ofType": null }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
        baseUrl = "http://localhost:${wireMockServer.port()}/graphql"
        client = GraphQLIntrospectionClient()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Nested
    inner class SuccessfulIntrospection {

        @Test
        fun `Given valid endpoint When introspect Then returns introspection JSON`() = runBlocking {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(VALID_INTROSPECTION_RESPONSE)
                    )
            )

            // When
            val result = client.introspect(baseUrl, emptyMap(), 5000)

            // Then
            assertTrue(result.contains("__schema"))
            assertTrue(result.contains("Query"))
        }

        @Test
        fun `Given custom headers When introspect Then forwards headers to endpoint`() = runBlocking {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(VALID_INTROSPECTION_RESPONSE)
                    )
            )

            // When
            client.introspect(
                baseUrl,
                mapOf("Authorization" to "Bearer token123", "X-Custom" to "custom-value"),
                5000
            )

            // Then
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/graphql"))
                    .withHeader("Authorization", equalTo("Bearer token123"))
                    .withHeader("X-Custom", equalTo("custom-value"))
            )
        }

        @Test
        fun `Given valid endpoint When introspect Then sends introspection query in request body`() = runBlocking {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(VALID_INTROSPECTION_RESPONSE)
                    )
            )

            // When
            client.introspect(baseUrl, emptyMap(), 5000)

            // Then
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/graphql"))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(containing("IntrospectionQuery"))
            )
        }
    }

    @Nested
    inner class NetworkFailures {

        @Test
        fun `Given unreachable host When introspect Then throws GraphQLIntrospectionException`() {
            // Given
            val unreachableUrl = "http://192.0.2.1:1/graphql"

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(unreachableUrl, emptyMap(), 500)
                }
            }
            assertTrue(
                exception.message!!.contains("Network failure") || exception.message!!.contains("timeout", ignoreCase = true),
                "Expected network failure or timeout message, got: ${exception.message}"
            )
        }

        @Test
        fun `Given slow endpoint When introspect with short timeout Then throws GraphQLIntrospectionException`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(VALID_INTROSPECTION_RESPONSE)
                            .withFixedDelay(5000)
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 100)
                }
            }
            assertTrue(
                exception.message!!.contains("timeout", ignoreCase = true) || exception.message!!.contains("Network failure"),
                "Expected timeout message, got: ${exception.message}"
            )
        }
    }

    @Nested
    inner class IntrospectionDisabled {

        @Test
        fun `Given introspection disabled When introspect Then throws GraphQLIntrospectionException with disabled message`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"errors":[{"message":"Introspection is not allowed"}]}""")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("Introspection disabled"),
                "Expected introspection disabled message, got: ${exception.message}"
            )
        }
    }

    @Nested
    inner class InvalidResponseFormat {

        @Test
        fun `Given empty body When introspect Then throws GraphQLIntrospectionException with empty response message`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("Empty response"),
                "Expected empty response message, got: ${exception.message}"
            )
        }

        @Test
        fun `Given non-JSON response When introspect Then throws GraphQLIntrospectionException with invalid JSON message`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("<html>Not Found</html>")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("not valid JSON"),
                "Expected invalid JSON message, got: ${exception.message}"
            )
        }

        @Test
        fun `Given JSON without data field When introspect Then throws GraphQLIntrospectionException`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"something":"else"}""")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("missing 'data' field"),
                "Expected missing data field message, got: ${exception.message}"
            )
        }

        @Test
        fun `Given JSON without __schema in data When introspect Then throws GraphQLIntrospectionException`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"data":{"something":"else"}}""")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("missing '__schema'"),
                "Expected missing __schema message, got: ${exception.message}"
            )
        }
    }

    @Nested
    inner class RateLimiting {

        @Test
        fun `Given rate limited endpoint When introspect Then throws GraphQLIntrospectionException with rate limit message`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(429)
                            .withBody("Too Many Requests")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("Rate limited") && exception.message!!.contains("429"),
                "Expected rate limit message with 429, got: ${exception.message}"
            )
        }
    }

    @Nested
    inner class NonSuccessfulHttpStatus {

        @Test
        fun `Given server error endpoint When introspect Then throws GraphQLIntrospectionException with HTTP 500`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("HTTP 500"),
                "Expected HTTP 500 message, got: ${exception.message}"
            )
        }

        @Test
        fun `Given forbidden endpoint When introspect Then throws GraphQLIntrospectionException with HTTP 403`() {
            // Given
            wireMockServer.stubFor(
                post(urlEqualTo("/graphql"))
                    .willReturn(
                        aResponse()
                            .withStatus(403)
                            .withBody("Forbidden")
                    )
            )

            // When / Then
            val exception = assertThrows(GraphQLIntrospectionException::class.java) {
                runBlocking {
                    client.introspect(baseUrl, emptyMap(), 5000)
                }
            }
            assertTrue(
                exception.message!!.contains("HTTP 403"),
                "Expected HTTP 403 message, got: ${exception.message}"
            )
        }
    }
}
