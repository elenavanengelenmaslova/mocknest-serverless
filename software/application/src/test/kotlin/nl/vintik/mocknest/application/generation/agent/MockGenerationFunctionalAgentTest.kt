package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.AIAgent
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
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
    private val promptBuilder: PromptBuilderService = mockk()
    private val mockAgent: AIAgent<String, String> = mockk()
    private lateinit var agent: MockGenerationFunctionalAgent

    @BeforeEach
    fun setup() {
        agent = MockGenerationFunctionalAgent(
            aiModelService,
            specificationParser,
            mockValidator,
            promptBuilder
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
        fun `Given spec When generating mocks Then should call runStrategy on AI model service`() = runBlocking {
            // Given
            val namespace = MockNamespace(apiName = "test")
            val request = SpecWithDescriptionRequest(
                jobId = "job-123",
                namespace = namespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate mocks"
            )
            val expectedResult = GenerationResult.success("job-123", emptyList())

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any()) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("job-123", result.jobId)
            
            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }
    }
}
