package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.AIAgent
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class MockGenerationFunctionalAgentTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val specificationParser: SpecificationParserInterface = mockk()
    private val mockValidator: MockValidatorInterface = mockk()
    private val mockAgent: AIAgent<String, String> = mockk()
    private lateinit var agent: MockGenerationFunctionalAgent

    @BeforeEach
    fun setup() {
        coEvery { aiModelService.createAgent() } returns mockAgent
        agent = MockGenerationFunctionalAgent(
            aiModelService,
            specificationParser,
            mockValidator
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class `Mock Generation and Validation` {

        private val specification = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0",
            title = "Test API",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/test",
                    method = HttpMethod.GET,
                    operationId = "test",
                    summary = "test",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                )
            ),
            schemas = emptyMap()
        )

        @Test
        fun `Given spec When generating mocks and all are valid Then should return valid mocks without retries`() = runBlocking {
            // Given
            val namespace = MockNamespace(apiName = "test")
            val request = SpecWithDescriptionRequest(
                jobId = "job-123",
                namespace = namespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate mocks"
            )
            val generatedMock = GeneratedMock(
                id = "mock-1",
                name = "Mock 1",
                namespace = namespace,
                wireMockMapping = "{}",
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/test", 200, "application/json"))
            )

            coEvery { specificationParser.parse(any(), any()) } returns specification
            coEvery { aiModelService.generateMockFromSpecWithDescription(mockAgent, any(), any(), any()) } returns listOf(generatedMock)
            coEvery { mockValidator.validate(generatedMock, specification) } returns MockValidationResult.valid()

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)
            assertEquals("mock-1", result.mocks[0].id)
            
            coVerify(exactly = 1) { aiModelService.generateMockFromSpecWithDescription(any(), any(), any(), any()) }
            coVerify(exactly = 0) { aiModelService.correctMocks(any(), any(), any(), any()) }
        }

        @Test
        fun `Given spec When generation returns invalid mock Then should retry once and succeed if corrected`() = runBlocking {
            // Given
            val namespace = MockNamespace(apiName = "test")
            val request = SpecWithDescriptionRequest(
                jobId = "job-123",
                namespace = namespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate mocks"
            )
            val invalidMock = GeneratedMock(
                id = "mock-1",
                name = "Mock 1",
                namespace = namespace,
                wireMockMapping = "{ \"invalid\": true }",
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/test", 200, "application/json"))
            )
            val correctedMock = GeneratedMock(
                id = "mock-1",
                name = "Mock 1",
                namespace = namespace,
                wireMockMapping = "{ \"valid\": true }",
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/test", 200, "application/json"))
            )

            coEvery { specificationParser.parse(any(), any()) } returns specification
            coEvery { aiModelService.generateMockFromSpecWithDescription(mockAgent, any(), any(), any()) } returns listOf(invalidMock)
            
            // First validation fails
            coEvery { mockValidator.validate(invalidMock, specification) } returns MockValidationResult.invalid(listOf("Schema error"))
            
            // Correction call
            coEvery { aiModelService.correctMocks(mockAgent, any(), namespace, specification) } returns listOf(correctedMock)
            
            // Second validation succeeds
            coEvery { mockValidator.validate(correctedMock, specification) } returns MockValidationResult.valid()

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)
            assertEquals("{ \"valid\": true }", result.mocks[0].wireMockMapping)
            
            coVerify(exactly = 1) { aiModelService.generateMockFromSpecWithDescription(any(), any(), any(), any()) }
            coVerify(exactly = 1) { aiModelService.correctMocks(any(), any(), any(), any()) }
            coVerify { mockValidator.validate(invalidMock, specification) }
            coVerify { mockValidator.validate(correctedMock, specification) }
        }

        @Test
        fun `Given spec When generation returns invalid mock and correction also fails after max retries Then should return empty valid list`() = runBlocking {
            // Given
            val namespace = MockNamespace(apiName = "test")
            val request = SpecWithDescriptionRequest(
                jobId = "job-123",
                namespace = namespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate mocks"
            )
            val invalidMock = GeneratedMock(
                id = "mock-1",
                name = "Mock 1",
                namespace = namespace,
                wireMockMapping = "{}",
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/test", 200, "application/json"))
            )

            coEvery { specificationParser.parse(any(), any()) } returns specification
            coEvery { aiModelService.generateMockFromSpecWithDescription(mockAgent, any(), any(), any()) } returns listOf(invalidMock)
            
            // All validations fail
            coEvery { mockValidator.validate(any(), specification) } returns MockValidationResult.invalid(listOf("Still invalid"))
            
            // Correction always returns the same invalid mock
            coEvery { aiModelService.correctMocks(mockAgent, any(), namespace, specification) } returns listOf(invalidMock)

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertTrue(result.mocks.isEmpty())
            
            coVerify(exactly = 1) { aiModelService.generateMockFromSpecWithDescription(any(), any(), any(), any()) }
            coVerify(exactly = 2) { aiModelService.correctMocks(any(), any(), any(), any()) }
        }
    }
}
