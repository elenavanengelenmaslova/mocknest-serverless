package nl.vintik.mocknest.application.generation.usecases

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIGenerationRequestUseCaseTest {

    private val generateFromSpecWithDescriptionUseCase =
        mockk<GenerateMocksFromSpecWithDescriptionUseCase>(relaxed = true)
    private val useCase = AIGenerationRequestUseCase(generateFromSpecWithDescriptionUseCase)

    @AfterEach
    fun tearDown() {
        clearMocks(generateFromSpecWithDescriptionUseCase)
    }

    @Nested
    inner class SuccessfulRequests {

        @Test
        fun `Given valid from-spec request When generating mocks Then should return 200 with mappings`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/ai/generation/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.success(
                jobId = "job-123",
                mocks = listOf(
                    GeneratedMock(
                        id = "m-1",
                        name = "mock",
                        namespace = MockNamespace("test-api"),
                        wireMockMapping = """{"request":{"method":"GET"},"response":{"status":200}}""",
                        metadata = MockMetadata(
                            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                            sourceReference = "ref",
                            endpoint = EndpointInfo(HttpMethod.GET, "/t", 200, "json")
                        )
                    )
                )
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.OK, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("mappings"))
        }

        @Test
        fun `Given valid from-spec request When generating mocks Then should delegate to GenerateMocksFromSpecWithDescriptionUseCase`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "payment-api" },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Payment API",
                    "format": "OPENAPI_3",
                    "description": "Generate payment mocks"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.success(
                jobId = "job-456",
                mocks = emptyList()
            )

            // When
            useCase.invoke("/from-spec", httpRequest)

            // Then
            coVerify(exactly = 1) {
                generateFromSpecWithDescriptionUseCase.execute(match { req ->
                    req.namespace.apiName == "payment-api" &&
                        req.format == SpecificationFormat.OPENAPI_3 &&
                        req.description == "Generate payment mocks" &&
                        req.specificationContent == "openapi: 3.0.0\ninfo:\n  title: Payment API"
                })
            }
        }

        @Test
        fun `Given valid from-spec request with URL When generating mocks Then should delegate with specificationUrl`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "remote-api" },
                    "specificationUrl": "https://example.com/openapi.yaml",
                    "format": "OPENAPI_3",
                    "description": "Generate mocks from remote spec"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.success(
                jobId = "job-789",
                mocks = emptyList()
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.OK, response.statusCode)
            coVerify(exactly = 1) {
                generateFromSpecWithDescriptionUseCase.execute(match { req ->
                    req.specificationUrl == "https://example.com/openapi.yaml" &&
                        req.specificationContent == null
                })
            }
        }

        @Test
        fun `Given successful generation with multiple mocks When handling request Then response contains all mappings`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "multi-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "Generate multiple mocks"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.success(
                jobId = "job-multi",
                mocks = listOf(
                    GeneratedMock(
                        id = "m-1",
                        name = "Get Users",
                        namespace = MockNamespace("multi-api"),
                        wireMockMapping = """{"request":{"method":"GET","url":"/users"},"response":{"status":200}}""",
                        metadata = MockMetadata(
                            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                            sourceReference = "ref",
                            endpoint = EndpointInfo(HttpMethod.GET, "/users", 200, "application/json")
                        )
                    ),
                    GeneratedMock(
                        id = "m-2",
                        name = "Create User",
                        namespace = MockNamespace("multi-api"),
                        wireMockMapping = """{"request":{"method":"POST","url":"/users"},"response":{"status":201}}""",
                        metadata = MockMetadata(
                            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                            sourceReference = "ref",
                            endpoint = EndpointInfo(HttpMethod.POST, "/users", 201, "application/json")
                        )
                    )
                )
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.OK, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("/users"))
            assertTrue(responseBody.contains("GET"))
            assertTrue(responseBody.contains("POST"))
        }
    }

    @Nested
    inner class RoutingAndMethodHandling {

        @Test
        fun `Given unknown path When handling request Then should return 404`() {
            // Given
            val httpRequest = HttpRequest(method = HttpMethod.GET, headers = emptyMap(), path = "/unknown", queryParameters = emptyMap(), body = null)

            // When
            val response = useCase.invoke("/unknown", httpRequest)

            // Then
            assertEquals(HttpStatusCode.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `Given wrong HTTP method When handling from-spec request Then should return 404`() {
            // Given
            val httpRequest = HttpRequest(method = HttpMethod.GET, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = null)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.NOT_FOUND, response.statusCode)
        }
    }

    @Nested
    inner class ClientErrorHandling {

        @Test
        fun `Given malformed JSON When handling from-spec request Then should return 400`() {
            // Given
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = "not-json")

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `Given null body When handling from-spec request Then should return 400`() {
            // Given
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = null)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `Given invalid namespace When handling from-spec request Then should return 400`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "invalid name!@#" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("error"))
        }

        @Test
        fun `Given missing description When handling from-spec request Then should return 400`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": ""
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("error"))
        }

        @Test
        fun `Given missing specification and URL When handling from-spec request Then should return 400`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("error"))
        }
    }

    @Nested
    inner class ServerErrorHandling {

        @Test
        fun `Given failed generation When handling from-spec request Then should return 500 with error details`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/ai/generation/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.failure(
                jobId = "job-123",
                error = "Generation failed"
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("FAILED"))
            assertTrue(responseBody.contains("Generation failed"))
        }

        @Test
        fun `Given agent returns failure result When handling request Then should not delegate further`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.failure(
                jobId = "job-fail",
                error = "Parser could not parse specification"
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("Parser could not parse specification"))
            coVerify(exactly = 1) { generateFromSpecWithDescriptionUseCase.execute(any()) }
        }

        @Test
        fun `Given unexpected RuntimeException When generating Then should return 500 with generic message`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } throws RuntimeException("secret internal detail")

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
            val responseBody = response.body
            assertNotNull(responseBody)
            assertTrue(responseBody.contains("Internal Server Error"))
            assertTrue(!responseBody.contains("secret internal detail"))
        }

        @Test
        fun `Given deep IllegalArgumentException from agent When generating Then should return 500 not 400`() {
            // Given - IllegalArgumentException from deep in the generation pipeline is not a client error
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } throws IllegalArgumentException("some library error")

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then - should be 500 because this is not a request validation error
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
        }

        @Test
        fun `Given AI produces invalid mapping JSON When serializing response Then should return 500 not 400`() {
            // Given
            val body = """
                {
                    "namespace": { "apiName": "test-api" },
                    "specification": "openapi content",
                    "format": "OPENAPI_3",
                    "description": "test description"
                }
            """.trimIndent()
            val httpRequest = HttpRequest(method = HttpMethod.POST, headers = emptyMap(), path = "/from-spec", queryParameters = emptyMap(), body = body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.success(
                jobId = "job-123",
                mocks = listOf(
                    GeneratedMock(
                        id = "m-1",
                        name = "mock",
                        namespace = MockNamespace("test-api"),
                        wireMockMapping = "this is not valid json {{{",
                        metadata = MockMetadata(
                            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                            sourceReference = "ref",
                            endpoint = EndpointInfo(HttpMethod.GET, "/t", 200, "json")
                        )
                    )
                )
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then - invalid AI output JSON should be 500, not 400
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
        }
    }
}
