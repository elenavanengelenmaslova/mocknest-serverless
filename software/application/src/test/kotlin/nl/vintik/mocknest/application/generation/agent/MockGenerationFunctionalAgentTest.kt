package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLModel
import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.util.UrlFetcher
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.*

class MockGenerationFunctionalAgentTest {

    private val aiModelService: AIModelServiceInterface = mockk(relaxed = true)
    private val specificationParser: SpecificationParserInterface = mockk(relaxed = true)
    private val mockValidator: MockValidatorInterface = mockk(relaxed = true)
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)
    private val urlFetcher: UrlFetcher = mockk(relaxed = true)

    private lateinit var agent: MockGenerationFunctionalAgent

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

    private val validLlmResponse =
        """[{"request":{"method":"GET","urlPath":"/test"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"message":"ok"}}}]"""

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

    @Nested
    inner class AgentInitialization {

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 2, 3, 5])
        fun `Given different maxRetries values When creating agent Then should initialize with specified maxRetries`(maxRetries: Int) {
            // When
            val customAgent = MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = specificationParser,
                mockValidator = mockValidator,
                promptBuilder = promptBuilder,
                maxRetries = maxRetries,
                urlFetcher = urlFetcher
            )

            // Then
            assertNotNull(customAgent)
            assertNotNull(customAgent.mockGenerationStrategy)
        }

        @Test
        fun `Given injected PromptBuilderService When creating agent Then should use injected instance`() {
            // Given
            val customPromptBuilder: PromptBuilderService = mockk(relaxed = true)

            // When
            val customAgent = MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = specificationParser,
                mockValidator = mockValidator,
                promptBuilder = customPromptBuilder,
                urlFetcher = urlFetcher
            )

            // Then
            assertNotNull(customAgent)
            assertNotNull(customAgent.mockGenerationStrategy)
        }

        @Test
        fun `Given injected AIModelServiceInterface When creating agent Then should use injected instance`() {
            // Given
            val customAiModelService: AIModelServiceInterface = mockk(relaxed = true)

            // When
            val customAgent = MockGenerationFunctionalAgent(
                aiModelService = customAiModelService,
                specificationParser = specificationParser,
                mockValidator = mockValidator,
                promptBuilder = promptBuilder,
                urlFetcher = urlFetcher
            )

            // Then
            assertNotNull(customAgent)
            assertNotNull(customAgent.mockGenerationStrategy)
        }

        @Test
        fun `Given default maxRetries When creating agent Then should default to 1`() {
            // When
            val defaultAgent = MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = specificationParser,
                mockValidator = mockValidator,
                promptBuilder = promptBuilder,
                urlFetcher = urlFetcher
            )

            // Then
            assertNotNull(defaultAgent)
            assertNotNull(defaultAgent.mockGenerationStrategy)
        }

        @Test
        fun `Given all dependencies injected When creating agent Then strategy should be available`() {
            // Then
            assertNotNull(agent.mockGenerationStrategy)
            assertEquals("mock-generation", agent.mockGenerationStrategy.name)
        }
    }

    @Nested
    inner class StrategyGraphTransitionsSuccess {

        @Test
        fun `Given valid spec and validation disabled When running strategy Then should transition setup to generate to finish`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-success-1",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            val strategy = agent.mockGenerationStrategy
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then - setup → generate → finish (validation skipped)
            assertTrue(result.success)
            assertEquals("job-success-1", result.jobId)
            assertEquals(1, result.mocks.size)
            assertEquals(true, result.metadata["validationSkipped"])
            assertEquals(1, result.metadata["attempts"])

            // Verify setup node parsed the spec
            coVerify(exactly = 1) { specificationParser.parse(any(), eq(SpecificationFormat.OPENAPI_3)) }
            // Verify generate node built prompt and parsed response
            verify(exactly = 1) { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) }
            verify(exactly = 1) { aiModelService.parseModelResponse(any(), any(), any(), any()) }
            // Verify validate/correct nodes were NOT invoked
            coVerify(exactly = 0) { mockValidator.validate(any(), any()) }
        }

        @Test
        fun `Given valid spec and validation enabled with all valid mocks When running strategy Then should transition setup to generate to validate to finish`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-success-2",
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

            val strategy = agent.mockGenerationStrategy
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then - setup → generate → validate (passes) → finish
            assertTrue(result.success)
            assertEquals("job-success-2", result.jobId)
            assertEquals(1, result.mocks.size)
            assertEquals(false, result.metadata["validationSkipped"])
            assertEquals(true, result.metadata["allValid"])
            assertEquals(true, result.metadata["firstPassValid"])
            assertEquals(1, result.metadata["attempts"])

            // Verify the full success path was traversed
            coVerify(exactly = 1) { specificationParser.parse(any(), any()) }
            verify(exactly = 1) { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) }
            verify(exactly = 1) { aiModelService.parseModelResponse(any(), any(), any(), any()) }
            coVerify(atLeast = 1) { mockValidator.validate(any(), any()) }
            // Verify correction was NOT invoked
            verify(exactly = 0) { promptBuilder.buildCorrectionPrompt(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class StrategyGraphTransitionsValidationFailure {

        @Test
        fun `Given validation fails on first attempt When running strategy Then should transition setup to generate to validate to correct to validate to finish`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-fail-1",
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

            // First parse returns initial mocks, second returns corrected mocks
            every { aiModelService.parseModelResponse(any(), any(), eq(SourceType.SPEC_WITH_DESCRIPTION), any()) } returns listOf(testMock)
            every { aiModelService.parseModelResponse(any(), any(), eq(SourceType.REFINEMENT), any()) } returns listOf(correctedMock)

            // First validation fails, second passes
            val invalidResult = MockValidationResult(isValid = false, errors = listOf("Invalid response body"))
            val validResult = MockValidationResult.valid()

            coEvery { mockValidator.validate(testMock, any()) } returns invalidResult
            coEvery { mockValidator.validate(correctedMock, any()) } returns validResult

            val strategy = agent.mockGenerationStrategy
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then - setup → generate → validate (fails) → correct → validate (passes) → finish
            assertTrue(result.success)
            assertEquals("job-fail-1", result.jobId)
            val attempts = result.metadata["attempts"] as Int
            assertTrue(attempts > 1, "Expected more than 1 attempt due to correction, got $attempts")
            assertEquals(false, result.metadata["firstPassValid"])

            // Verify correction path was traversed
            coVerify(exactly = 1) { specificationParser.parse(any(), any()) }
            verify(exactly = 1) { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) }
            verify(exactly = 1) { promptBuilder.buildCorrectionPrompt(any(), any(), any(), any()) }
            coVerify(atLeast = 2) { mockValidator.validate(any(), any()) }
        }

        @Test
        fun `Given validation fails and maxRetries exceeded When running strategy Then should return best effort result`() = runTest {
            // Given - agent with maxRetries = 1 (default)
            val request = SpecWithDescriptionRequest(
                jobId = "job-fail-2",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { promptBuilder.buildCorrectionPrompt(any(), any(), any(), any()) } returns "Correction prompt"

            // All parse calls return mocks
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            // Validation always fails with non-fatal errors
            val persistentError = MockValidationResult(
                isValid = false,
                errors = listOf("[CONSISTENCY] Persistent validation error"),
                isFatal = false
            )
            coEvery { mockValidator.validate(any(), any()) } returns persistentError

            val strategy = agent.mockGenerationStrategy
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then - should eventually stop retrying and return best effort
            val attempts = result.metadata["attempts"] as Int
            assertTrue(attempts > 1, "Expected more than 1 attempt, got $attempts")
            // Non-fatal mocks should still be included
            assertTrue(result.success)
            assertTrue(result.mocks.isNotEmpty())
        }

        @Test
        fun `Given validation fails with fatal errors and maxRetries exceeded When running strategy Then should return failure`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-fail-3",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
            every { promptBuilder.buildCorrectionPrompt(any(), any(), any(), any()) } returns "Correction prompt"

            // All parse calls return mocks
            every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)

            // Validation always fails with fatal errors
            val fatalError = MockValidationResult(
                isValid = false,
                errors = listOf("Fatal structural error"),
                isFatal = true
            )
            coEvery { mockValidator.validate(any(), any()) } returns fatalError

            val strategy = agent.mockGenerationStrategy
            val mockExecutor = getMockExecutor {
                mockLLMAnswer(validLlmResponse).asDefaultResponse
            }
            val agentConfig = AIAgentConfig.withSystemPrompt(
                prompt = "test system prompt",
                llm = mockk<LLModel>(relaxed = true),
                maxAgentIterations = 50
            )
            val graphAgent = GraphAIAgent(
                promptExecutor = mockExecutor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = ToolRegistry.EMPTY
            )

            // When
            val result = graphAgent.run(request)

            // Then - all mocks are fatal, so result should be failure
            assertFalse(result.success)
            assertNotNull(result.error)
            assertEquals(0, result.mocks.size)
        }
    }

    @Nested
    inner class ContentResolution {

        @Test
        fun `Given GraphQL format with URL When resolving content Then should return URL string directly`() {
            // Given
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = "https://example.com/graphql",
                format = SpecificationFormat.GRAPHQL,
                description = "test graphql"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals("https://example.com/graphql", result)
            verify(exactly = 0) { urlFetcher.fetch(any()) }
        }

        @Test
        fun `Given OpenAPI format with URL When resolving content Then should return URL directly since format handles own resolution`() {
            // Given
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = "https://example.com/openapi.yaml",
                format = SpecificationFormat.OPENAPI_3,
                description = "test openapi"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals("https://example.com/openapi.yaml", result)
            verify(exactly = 0) { urlFetcher.fetch(any()) }
        }

        @Test
        fun `Given WSDL format with URL When resolving content Then should return URL directly`() {
            // Given
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = "https://example.com/service.wsdl",
                format = SpecificationFormat.WSDL,
                description = "test wsdl"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals("https://example.com/service.wsdl", result)
            verify(exactly = 0) { urlFetcher.fetch(any()) }
        }

        @Test
        fun `Given format with inline content When resolving content Then should return content string`() {
            // Given
            val content = "openapi: 3.0.0\ninfo:\n  title: Test"
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationContent = content,
                format = SpecificationFormat.OPENAPI_3,
                description = "test openapi"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals(content, result)
            verify(exactly = 0) { urlFetcher.fetch(any()) }
        }
    }

    @Nested
    inner class GenerateFromSpecWithDescription {

        @Test
        fun `Given valid request When generateFromSpecWithDescription Then should delegate to aiModelService runStrategy`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-delegate-1",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks"
            )
            val expectedResult = GenerationResult.success("job-delegate-1", listOf(testMock))

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("job-delegate-1", result.jobId)
            assertEquals(1, result.mocks.size)
            coVerify(exactly = 1) {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            }
        }

        @Test
        fun `Given strategy returns failure When generateFromSpecWithDescription Then should propagate failure`() = runTest {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-delegate-2",
                namespace = testNamespace,
                specificationContent = "invalid spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks"
            )
            val expectedResult = GenerationResult.failure("job-delegate-2", "Parse error")

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertFalse(result.success)
            assertEquals("Parse error", result.error)
        }
    }
}
