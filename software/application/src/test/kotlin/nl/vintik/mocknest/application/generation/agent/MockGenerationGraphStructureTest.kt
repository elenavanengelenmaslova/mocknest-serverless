package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.testGraph
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
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

/**
 * Structural tests for the [MockGenerationFunctionalAgent] strategy graph.
 *
 * Unlike [MockGenerationStrategyTest] (which executes the graph and asserts on results),
 * these tests use Koog's graph-testing feature (`testGraph`) to verify the *topology* of the
 * strategy: that the expected nodes exist, that they are reachable from the entry point, and
 * that the unconditional edges connect the right nodes. This catches accidental rewiring of the
 * generate -> validate -> correct loop even when execution-level tests still pass.
 *
 * The graph verification runs when the strategy starts, so each test calls `agent.run(...)`.
 * The MockK collaborators and the mock LLM executor exist only to let that run complete; the
 * assertions themselves are declared inside the `testGraph` block.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class MockGenerationGraphStructureTest {

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

    private val validLlmResponse =
        """[{"request":{"method":"GET","urlPath":"/test"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"jsonBody":{"message":"ok"}}}]"""

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

        coEvery { specificationParser.parse(any(), any()) } returns testSpecification
        every { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any(), any()) } returns "Generate mocks prompt"
        every { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)
        coEvery { mockValidator.validate(any(), any()) } returns MockValidationResult.valid()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(aiModelService, specificationParser, mockValidator, promptBuilder, urlFetcher)
    }

    private fun request() = SpecWithDescriptionRequest(
        jobId = "job-graph-structure",
        namespace = testNamespace,
        specificationContent = "openapi: 3.0.0...",
        format = SpecificationFormat.OPENAPI_3,
        description = "generate test mocks",
        options = GenerationOptions(enableValidation = true)
    )

    /**
     * Builds a functional agent whose strategy captures the given [maxRetries] in its edge
     * conditions. Used by the retry-boundary tests to vary the retry budget.
     */
    private fun agentWith(maxRetries: Int) = MockGenerationFunctionalAgent(
        aiModelService = aiModelService,
        specificationParser = specificationParser,
        mockValidator = mockValidator,
        promptBuilder = promptBuilder,
        maxRetries = maxRetries,
        urlFetcher = urlFetcher
    )

    private fun buildAgent(
        strategyAgent: MockGenerationFunctionalAgent = agent,
        installTesting: GraphAIAgent.FeatureContext.() -> Unit
    ): GraphAIAgent<SpecWithDescriptionRequest, GenerationResult> {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer(validLlmResponse).asDefaultResponse
        }
        val agentConfig = AIAgentConfig.withSystemPrompt(
            prompt = "mock-generation-structure-test",
            llm = mockk<LLModel>(relaxed = true),
            maxAgentIterations = 50
        )
        return GraphAIAgent(
            promptExecutor = mockExecutor,
            agentConfig = agentConfig,
            strategy = strategyAgent.mockGenerationStrategy,
            toolRegistry = ToolRegistry.EMPTY,
            installFeatures = installTesting
        )
    }

    @Test
    fun `Given mock generation strategy When verifying graph Then all expected nodes exist and are reachable`() = runTest {
        val agentUnderTest = buildAgent {
            testGraph<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {
                val start = startNode()
                val finish = finishNode()

                // The four processing nodes defined by the strategy.
                val setup = assertNodeByName<SpecWithDescriptionRequest, MockGenerationContext>("setup")
                val generate = assertNodeByName<MockGenerationContext, MockGenerationContext>("generate")
                val validate = assertNodeByName<MockGenerationContext, MockGenerationContext>("validate")
                val correct = assertNodeByName<MockGenerationContext, MockGenerationContext>("correct")

                // Entry flows into setup, then generation.
                assertReachable(start, setup)
                assertReachable(setup, generate)

                // Validation loop: generate -> validate -> correct -> validate.
                assertReachable(generate, validate)
                assertReachable(validate, correct)
                assertReachable(correct, validate)

                // Every node can ultimately reach the finish node.
                assertReachable(generate, finish)
                assertReachable(validate, finish)
                assertReachable(correct, finish)
            }
        }

        agentUnderTest.run(request())
    }

    @Test
    fun `Given mock generation strategy When verifying unconditional edges Then setup and correct wire deterministically`() = runTest {
        val agentUnderTest = buildAgent {
            testGraph<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {
                val start = startNode()
                val setup = assertNodeByName<SpecWithDescriptionRequest, MockGenerationContext>("setup")
                val generate = assertNodeByName<MockGenerationContext, MockGenerationContext>("generate")
                val validate = assertNodeByName<MockGenerationContext, MockGenerationContext>("validate")
                val correct = assertNodeByName<MockGenerationContext, MockGenerationContext>("correct")

                val finish = finishNode()

                assertEdges {
                    // Unconditional transitions: exactly one outgoing edge.
                    start alwaysGoesTo setup
                    setup alwaysGoesTo generate
                    correct alwaysGoesTo validate

                    // generate is conditional on options.enableValidation. Assert both branches
                    // directly so an intermediate path cannot satisfy the test.
                    generate withOutput generateOutput(enableValidation = true) goesTo validate
                    generate withOutput generateOutput(enableValidation = false) goesTo finish
                }
            }
        }

        agentUnderTest.run(request())
    }

    /**
     * Builds a [MockGenerationContext] describing the state that flows *out* of the validate node,
     * so the validate -> {correct, finish} edge conditions can be evaluated against it.
     * The conditions only read [MockGenerationContext.errors] and [MockGenerationContext.attempt].
     */
    private fun validateOutput(errors: List<String>, attempt: Int) = MockGenerationContext(
        request = request(),
        specification = testSpecification,
        mocks = listOf(testMock),
        attempt = attempt,
        errors = errors
    )

    /**
     * Builds a [MockGenerationContext] describing the state that flows *out* of the generate node.
     * The generate -> {validate, finish} edge conditions only read
     * [MockGenerationContext.request].options.enableValidation.
     */
    private fun generateOutput(enableValidation: Boolean) = MockGenerationContext(
        request = request().copy(options = GenerationOptions(enableValidation = enableValidation)),
        specification = testSpecification,
        mocks = listOf(testMock)
    )

    /**
     * Retry-budget boundary property: for any maxRetries N, while there are validation errors
     * the validate node loops back to `correct` as long as `attempt <= N`, and switches to
     * `finish` as soon as `attempt > N`. Each row exercises the last correcting attempt and the
     * first attempt past the budget for a range of retry budgets, including 0 (never correct).
     */
    @ParameterizedTest(name = "maxRetries={0}, attempt={1} => {2}")
    @CsvSource(
        // maxRetries, attempt, expectedTarget
        "0, 1, finish",   // no retries: first failure already exceeds budget
        "1, 1, correct",  // last correcting attempt for budget 1
        "1, 2, finish",   // first attempt past budget 1
        "2, 2, correct",  // last correcting attempt for budget 2
        "2, 3, finish",   // first attempt past budget 2
        "3, 2, correct",  // strictly inside budget 3 (below the boundary)
        "3, 3, correct",  // last correcting attempt for budget 3
        "3, 4, finish"    // first attempt past budget 3
    )
    fun `Given validation errors When resolving validate edge Then routing respects the retry budget`(
        maxRetries: Int,
        attempt: Int,
        expectedTarget: String
    ) = runTest {
        val agentUnderTest = buildAgent(strategyAgent = agentWith(maxRetries)) {
            testGraph<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {
                val finish = finishNode()
                val validate = assertNodeByName<MockGenerationContext, MockGenerationContext>("validate")
                val correct = assertNodeByName<MockGenerationContext, MockGenerationContext>("correct")

                val output = validateOutput(errors = listOf("boom"), attempt = attempt)
                assertEdges {
                    when (expectedTarget) {
                        "correct" -> validate withOutput output goesTo correct
                        "finish" -> validate withOutput output goesTo finish
                        else -> error("Unexpected expected target: $expectedTarget")
                    }
                }
            }
        }

        agentUnderTest.run(request())
    }

    /**
     * When there are no validation errors, validate always finishes regardless of how many
     * retries are configured or which attempt we are on. The `attempt = 2` rows represent the
     * post-correction success case: correction ran, validation now passes, and even though a
     * retry is still budgeted the empty-error branch wins and routes to finish.
     */
    @ParameterizedTest(name = "maxRetries={0}, attempt={1}")
    @CsvSource(
        // maxRetries, attempt
        "0, 1",   // first attempt, no retries
        "1, 1",   // first attempt, one retry budgeted
        "2, 1",
        "3, 1",
        "2, 2",   // post-correction success while a retry is still available
        "3, 2"
    )
    fun `Given no validation errors When resolving validate edge Then goes straight to finish for any retry budget`(
        maxRetries: Int,
        attempt: Int
    ) = runTest {
        val agentUnderTest = buildAgent(strategyAgent = agentWith(maxRetries)) {
            testGraph<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {
                val finish = finishNode()
                val validate = assertNodeByName<MockGenerationContext, MockGenerationContext>("validate")

                assertEdges {
                    validate withOutput validateOutput(errors = emptyList(), attempt = attempt) goesTo finish
                }
            }
        }

        agentUnderTest.run(request())
    }

}
