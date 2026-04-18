package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLModel
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.util.UrlFetcher
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant

/**
 * Tests the MockGenerationFunctionalAgent strategy graph using the Koog agents-test module
 * with mock LLM responses. These tests bypass the aiModelService.runStrategy() abstraction
 * and run the strategy graph directly with a mock LLM executor.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class MockGenerationStrategyTest {

    private val aiModelService: AIModelServiceInterface = mockk(relaxed = true)
    private val specificationParser: SpecificationParserInterface = mockk(relaxed = true)
    private val mockValidator: MockValidatorInterface = mockk(relaxed = true)
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)
    private val urlFetcher: UrlFetcher = mockk(relaxed = true)

    private val testNamespace = MockNamespace(apiName = "test-api", client = "test-client")

    private val testSpecification = APISpecification(
        format = SpecificationFormat.OPENAPI_3,
        version = "1.0",
        title = "Test API",
        endpoints = listOf(
            EndpointDefinition(
                path = "/test",
                method = HttpMethod.GET,
                operationId = "getTest",
                summary = "Get test",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(200 to ResponseDefinition(200, "OK", null))
            )
        ),
        schemas = emptyMap()
    )

    private val testMock = GeneratedMock(
        id = "ai-generated-test-client-test-api-get--test-0",
        name = "AI Generated: GET /test",
        namespace = testNamespace,
        wireMockMapping = """{"request":{"method":"GET","urlPath":"/test"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"message":"ok"}}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "Test API: test description",
            endpoint = EndpointInfo(
                method = HttpMethod.GET,
                path = "/test",
                statusCode = 200,
                contentType = "application/json"
            )
        ),
        generatedAt = Instant.now()
    )

    private val validLlmResponse = """[{"request":{"method":"GET","urlPath":"/test"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"message":"ok"}}}]"""

    private lateinit var agent: MockGenerationFunctionalAgent

    @BeforeEach
    fun setup() {
        agent = MockGenerationFunctionalAgent(
            aiModelService = aiModelService,
            specificationParser = specificationParser,
            mockValidator = mockValidator,
            promptBuilder = promptBuilder,
            maxRetries = 1,
            urlFetcher = urlFetcher
        )
    }

    @AfterEach
    fun tearDown() {
        clearMocks(aiModelService, specificationParser, mockValidator, promptBuilder, urlFetcher)
    }

    /**
     * Extracts the private mockGenerationStrategy field from MockGenerationFunctionalAgent via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractStrategy(): AIAgentGraphStrategy<SpecWithDescriptionRequest, GenerationResult> {
        val strategyField = MockGenerationFunctionalAgent::class.java.getDeclaredField("mockGenerationStrategy")
        strategyField.isAccessible = true
        return strategyField.get(agent) as AIAgentGraphStrategy<SpecWithDescriptionRequest, GenerationResult>
    }

    @Nested
    inner class HappyPathValidationDisabled {

        @Test
        fun `Given valid spec and validation disabled When running strategy Then should return success with validationSkipped`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-1",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks for Test API"
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            val strategy = extractStrategy()
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            assertEquals("job-strategy-1", result.jobId)
            assertEquals(1, result.mocks.size)
            assertEquals(true, result.metadata["validationSkipped"])
            assertEquals(1, result.metadata["attempts"])
            assertEquals(1, result.metadata["totalGenerated"])
        }
    }

    @Nested
    inner class CorrectionPath {

        @Test
        fun `Given validation enabled and first attempt has errors When running strategy Then should correct and return success`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-2",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )

            val correctedMock = testMock.copy(
                id = "ai-generated-corrected-0",
                metadata = testMock.metadata.copy(sourceType = SourceType.REFINEMENT)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { promptBuilder.buildCorrectionPrompt(any(), any(), any(), any()) } returns "Correction prompt"

            // First call returns initial mocks, second call returns corrected mocks
            every { aiModelService.parseModelResponse(any(), any(), eq(SourceType.SPEC_WITH_DESCRIPTION), any()) } returns listOf(testMock)
            every { aiModelService.parseModelResponse(any(), any(), eq(SourceType.REFINEMENT), any()) } returns listOf(correctedMock)

            // First validation fails, second passes
            val invalidResult = MockValidationResult(isValid = false, errors = listOf("Invalid response body"))
            val validResult = MockValidationResult.valid()

            coEvery { mockValidator.validate(testMock, any()) } returns invalidResult
            coEvery { mockValidator.validate(correctedMock, any()) } returns validResult

            val strategy = extractStrategy()
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            val attempts = result.metadata["attempts"] as Int
            assertTrue(attempts > 1, "Expected more than 1 attempt, got $attempts")
            assertEquals(false, result.metadata["firstPassValid"])
        }
    }

    @Nested
    inner class ParseFailurePath {

        @Test
        fun `Given LLM returns non-parseable text When running strategy Then should traverse correction path`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-3",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { promptBuilder.buildParsingCorrectionPrompt(any(), any(), any()) } returns "Parsing correction prompt"

            // First call throws parse exception, second call returns valid mocks
            var parseCallCount = 0
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } answers {
                parseCallCount++
                if (parseCallCount == 1) {
                    throw ModelResponseParsingException("No JSON found in model response")
                }
                listOf(testMock)
            }

            // Validation passes for corrected mocks
            coEvery { mockValidator.validate(any(), any()) } returns MockValidationResult.valid()

            val strategy = extractStrategy()
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            val attempts = result.metadata["attempts"] as Int
            assertTrue(attempts > 1, "Expected more than 1 attempt due to parse failure, got $attempts")
        }
    }

    @Nested
    inner class PromptContentVerification {

        @Test
        fun `Given specification with title and description When running strategy Then prompt should contain spec context`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-4",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate comprehensive test mocks",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification

            // Capture the prompt to verify it contains the expected content
            val expectedPrompt = "Generate mocks for Test API with description: generate comprehensive test mocks for namespace: test-client/test-api"
            every {
                promptBuilder.buildSpecWithDescriptionPrompt(
                    eq(testSpecification),
                    eq("generate comprehensive test mocks"),
                    eq(testNamespace),
                    any()
                )
            } returns expectedPrompt

            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            val strategy = extractStrategy()
            // The mock executor should receive the prompt built by promptBuilder
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse) onRequestContains "Test API"
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            // Verify the promptBuilder was called with the correct specification, description, and namespace
            io.mockk.verify {
                promptBuilder.buildSpecWithDescriptionPrompt(
                    eq(testSpecification),
                    eq("generate comprehensive test mocks"),
                    eq(testNamespace),
                    any()
                )
            }
        }
    }

    @Nested
    inner class MetadataCompleteness {

        @Test
        fun `Given validation disabled When running strategy Then metadata should contain expected keys`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-5a",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            val strategy = extractStrategy()
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            val metadata = result.metadata
            assertTrue(metadata.containsKey("totalGenerated"), "Missing 'totalGenerated' in metadata")
            assertTrue(metadata.containsKey("attempts"), "Missing 'attempts' in metadata")
            assertTrue(metadata.containsKey("validationSkipped"), "Missing 'validationSkipped' in metadata")
            assertEquals(true, metadata["validationSkipped"])
        }

        @Test
        fun `Given validation enabled and all mocks valid When running strategy Then metadata should contain validation keys`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-strategy-5b",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)
            coEvery { mockValidator.validate(any(), any()) } returns MockValidationResult.valid()

            val strategy = extractStrategy()
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                inputType = strategy.inputType,
                outputType = strategy.outputType,
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then
            assertTrue(result.success)
            val metadata = result.metadata
            assertTrue(metadata.containsKey("totalGenerated"), "Missing 'totalGenerated' in metadata")
            assertTrue(metadata.containsKey("attempts"), "Missing 'attempts' in metadata")
            assertTrue(metadata.containsKey("validationSkipped"), "Missing 'validationSkipped' in metadata")
            assertTrue(metadata.containsKey("allValid"), "Missing 'allValid' in metadata")
            assertTrue(metadata.containsKey("firstPassValid"), "Missing 'firstPassValid' in metadata")
            assertTrue(metadata.containsKey("firstPassMocksGenerated"), "Missing 'firstPassMocksGenerated' in metadata")
            assertTrue(metadata.containsKey("firstPassMocksValid"), "Missing 'firstPassMocksValid' in metadata")
            assertTrue(metadata.containsKey("mocksDropped"), "Missing 'mocksDropped' in metadata")
            assertTrue(metadata.containsKey("validationErrors"), "Missing 'validationErrors' in metadata")
            assertEquals(false, metadata["validationSkipped"])
            assertEquals(true, metadata["allValid"])
            assertEquals(true, metadata["firstPassValid"])
        }
    }
}
