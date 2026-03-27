package nl.vintik.mocknest.application.generation.usecases

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
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
            assertEquals(HttpStatus.OK, response.statusCode)
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
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `Given wrong HTTP method When handling from-spec request Then should return 404`() {
            // Given
            val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/from-spec", emptyMap(), null)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
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
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `Given null body When handling from-spec request Then should return 400`() {
            // Given
            val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), null)

            // When
            val response = useCase.invoke("/from-spec", httpRequest)

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
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
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
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
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
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
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals(true, response.body?.contains("FAILED"))
            assertEquals(true, response.body?.contains("Generation failed"))
        }
    }
}
