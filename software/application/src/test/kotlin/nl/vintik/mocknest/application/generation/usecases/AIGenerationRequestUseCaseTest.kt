package nl.vintik.mocknest.application.generation.usecases

import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.generation.*
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIGenerationRequestUseCaseTest {

    private val generateFromSpecWithDescriptionUseCase = mockk<GenerateMocksFromSpecWithDescriptionUseCase>()
    private val useCase = AIGenerationRequestUseCase(generateFromSpecWithDescriptionUseCase)

    @Test
    fun `Should handle valid from-spec request`() {
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

        val response = useCase.invoke("/from-spec", httpRequest)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("mappings") == true)
    }

    @Test
    fun `Should return 404 for unknown path`() {
        val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/unknown", emptyMap(), null)
        val response = useCase.invoke("/unknown", httpRequest)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `Should return 500 for failed generation`() {
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

        val response = useCase.invoke("/from-spec", httpRequest)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertTrue(response.body?.contains("FAILED") == true)
        assertTrue(response.body?.contains("Generation failed") == true)
    }

    @Test
    fun `Should handle malformed JSON in request`() {
        val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), "not-json")
        val response = useCase.invoke("/from-spec", httpRequest)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `Should return 404 for wrong method`() {
        val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/from-spec", emptyMap(), null)
        val response = useCase.invoke("/from-spec", httpRequest)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `Should handle null body in generateFromSpecWithDescription`() {
        val httpRequest = HttpRequest(HttpMethod.POST, emptyMap(), "/from-spec", emptyMap(), null)
        val response = useCase.invoke("/from-spec", httpRequest)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }
}
