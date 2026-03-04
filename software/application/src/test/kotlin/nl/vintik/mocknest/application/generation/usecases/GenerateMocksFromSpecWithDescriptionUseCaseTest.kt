package nl.vintik.mocknest.application.generation.usecases

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant

class GenerateMocksFromSpecWithDescriptionUseCaseTest {

    private val mockAgent: MockGenerationFunctionalAgent = mockk(relaxed = true)
    private val useCase = GenerateMocksFromSpecWithDescriptionUseCase(mockAgent)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given valid spec with description request When executing use case Then should return successful result`() = runTest {
        // Given
        val request = SpecWithDescriptionRequest(
            jobId = "test-job-123",
            namespace = MockNamespace(apiName = "test-namespace"),
            specificationContent = """{"openapi": "3.0.0"}""",
            format = SpecificationFormat.OPENAPI_3,
            description = "Generate mocks for user API",
            options = GenerationOptions.default()
        )

        val expectedMocks = listOf(
            GeneratedMock(
                id = "mock-1",
                name = "Get User",
                namespace = MockNamespace(apiName = "test-namespace"),
                wireMockMapping = """{"request": {"method": "GET", "url": "/users/1"}}""",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "User API: Generate mocks for user API",
                    endpoint = EndpointInfo(
                        method = HttpMethod.GET,
                        path = "/users/1",
                        statusCode = 200,
                        contentType = "application/json"
                    )
                ),
                generatedAt = Instant.now()
            )
        )

        val expectedResult = GenerationResult.success("test-job-123", expectedMocks)

        coEvery { mockAgent.generateFromSpecWithDescription(request) } returns expectedResult

        // When
        val result = useCase.execute(request)

        // Then
        assertTrue(result.success)
        assertEquals("test-job-123", result.jobId)
        assertEquals(1, result.mocksGenerated)
        assertEquals(1, result.mocks.size)
        assertNull(result.error)

        coVerify(exactly = 1) { mockAgent.generateFromSpecWithDescription(request) }
    }

    @Test
    fun `Given agent throws exception When executing use case Then should return failure result`() = runTest {
        // Given
        val request = SpecWithDescriptionRequest(
            jobId = "test-job-456",
            namespace = MockNamespace(apiName = "test-namespace"),
            specificationContent = """{"openapi": "3.0.0"}""",
            format = SpecificationFormat.OPENAPI_3,
            description = "Generate mocks for user API"
        )

        coEvery { mockAgent.generateFromSpecWithDescription(request) } throws RuntimeException("AI service unavailable")

        // When
        val result = useCase.execute(request)

        // Then
        assertFalse(result.success)
        assertEquals("test-job-456", result.jobId)
        assertEquals(0, result.mocksGenerated)
        assertEquals(0, result.mocks.size)
        assertEquals("AI service unavailable", result.error)

        coVerify(exactly = 1) { mockAgent.generateFromSpecWithDescription(request) }
    }

    @Test
    fun `Given spec URL instead of content When executing use case Then should process successfully`() = runTest {
        // Given
        val request = SpecWithDescriptionRequest(
            jobId = "test-job-789",
            namespace = MockNamespace(apiName = "test-namespace"),
            specificationUrl = "https://api.example.com/openapi.json",
            format = SpecificationFormat.OPENAPI_3,
            description = "Generate mocks for payment API"
        )

        val expectedResult = GenerationResult.success("test-job-789", emptyList())

        coEvery { mockAgent.generateFromSpecWithDescription(request) } returns expectedResult

        // When
        val result = useCase.execute(request)

        // Then
        assertTrue(result.success)
        assertEquals("test-job-789", result.jobId)
        assertEquals(0, result.mocksGenerated)

        coVerify(exactly = 1) { mockAgent.generateFromSpecWithDescription(request) }
    }

    @Test
    fun `Given validation disabled in options When executing use case Then should process without validation`() = runTest {
        // Given
        val request = SpecWithDescriptionRequest(
            jobId = "test-job-999",
            namespace = MockNamespace(apiName = "test-namespace"),
            specificationContent = """{"openapi": "3.0.0"}""",
            format = SpecificationFormat.OPENAPI_3,
            description = "Generate mocks without validation",
            options = GenerationOptions(enableValidation = false)
        )

        val expectedResult = GenerationResult.success("test-job-999", emptyList())

        coEvery { mockAgent.generateFromSpecWithDescription(request) } returns expectedResult

        // When
        val result = useCase.execute(request)

        // Then
        assertTrue(result.success)
        coVerify(exactly = 1) { mockAgent.generateFromSpecWithDescription(request) }
    }
}
