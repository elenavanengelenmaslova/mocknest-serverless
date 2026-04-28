package nl.vintik.mocknest.application.generation.usecases

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpStatusCode
import kotlin.test.assertEquals

class AIGenerationRequestUseCaseTest {

    private val generateFromSpecWithDescriptionUseCase =
        mockk<GenerateMocksFromSpecWithDescriptionUseCase>(relaxed = true)
    private val useCase = AIGenerationRequestUseCase(generateFromSpecWithDescriptionUseCase)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/ai/generation/from-spec", emptyMap(), body)

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
            assertEquals(true, response.body?.contains("mappings"))
        }
    }

    @Nested
    inner class RoutingAndMethodHandling {

        @Test
        fun `Given unknown path When handling request Then should return 404`() {
            // Given
            val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/unknown", emptyMap(), null)

            // When
            val response = useCase.invoke("/unknown", httpRequest)

            // Then
            assertEquals(HttpStatusCode.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `Given wrong HTTP method When handling from-spec request Then should return 404`() {
            // Given
            val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/from-spec", emptyMap(), null)

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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), "not-json")

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `Given null body When handling from-spec request Then should return 400`() {
            // Given
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), null)

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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), body)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
            assertEquals(true, response.body?.contains("error"))
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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), body)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.BAD_REQUEST, response.statusCode)
            assertEquals(true, response.body?.contains("error"))
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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/ai/generation/from-spec", emptyMap(), body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } returns GenerationResult.failure(
                jobId = "job-123",
                error = "Generation failed"
            )

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals(true, response.body?.contains("FAILED"))
            assertEquals(true, response.body?.contains("Generation failed"))
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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), body)

            coEvery { generateFromSpecWithDescriptionUseCase.execute(any()) } throws RuntimeException("secret internal detail")

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals(true, response.body?.contains("Internal Server Error"))
            assertEquals(false, response.body?.contains("secret internal detail"))
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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), body)

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
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), body)

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
