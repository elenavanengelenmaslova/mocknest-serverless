package nl.vintik.mocknest.infra.aws.generation.ai.eval

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseOutput
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.JudgeLM
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.core.Example
import dev.dokimos.junit.DatasetSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.domain.generation.GenerationOptions
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SpecWithDescriptionRequest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter
import nl.vintik.mocknest.infra.aws.generation.ai.config.DefaultInferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferenceMode
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators.EndpointCoverageEvaluator
import nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators.PetCountEvaluator
import nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators.SchemaConsistencyEvaluator
import nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators.StatusDistinctnessEvaluator
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * Manual, local-only Bedrock prompt evaluation test.
 *
 * Exercises the real Koog + Bedrock initial generation prompt path against the
 * Petstore OpenAPI specification. Runs N iterations (configurable via
 * `BEDROCK_EVAL_ITERATIONS`, default 1), validates semantic correctness using
 * Dokimos evaluators, captures token usage, and produces a structured eval report.
 *
 * This test is excluded from normal `./gradlew test` runs via the `bedrock-eval`
 * JUnit tag and the `BEDROCK_EVAL_ENABLED` environment variable gate.
 *
 * Run with:
 * ```
 * BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_ITERATIONS=1 \
 *   ./gradlew :software:infra:aws:generation:bedrockEval
 * ```
 */
@Tag("bedrock-eval")
@EnabledIfEnvironmentVariable(named = "BEDROCK_EVAL_ENABLED", matches = "true")
class BedrockPromptEvalTest {

    // --- Manual wiring (no Spring context) ---

    private val region: String = System.getenv("AWS_REGION") ?: "eu-west-1"

    private val tokenUsageStore = TokenUsageStore()

    private val bedrockClient: BedrockRuntimeClient = TokenUsageCapturingClient(
        delegate = BedrockRuntimeClient { this.region = this@BedrockPromptEvalTest.region },
        tokenUsageStore = tokenUsageStore
    )

    private val modelConfiguration = ModelConfiguration(
        modelName = "AmazonNovaPro",
        prefixResolver = DefaultInferencePrefixResolver(
            deployRegion = region,
            inferenceMode = InferenceMode.AUTO
        )
    )

    private val promptBuilder = PromptBuilderService()

    private val aiModelService = BedrockServiceAdapter(
        bedrockClient = bedrockClient,
        modelConfiguration = modelConfiguration,
        promptBuilder = promptBuilder
    )

    private val specificationParser = OpenAPISpecificationParser()

    private val mockValidator = OpenAPIMockValidator()

    private val agent = MockGenerationFunctionalAgent(
        aiModelService = aiModelService,
        specificationParser = specificationParser,
        mockValidator = mockValidator,
        promptBuilder = promptBuilder
    )

    // --- Semantic evaluators ---

    private val petCountEvaluator = PetCountEvaluator(expectedCount = 4)
    private val endpointCoverageEvaluator = EndpointCoverageEvaluator(
        requiredEndpoints = listOf("GET /pet/{petId}", "GET /pet/findByStatus")
    )
    private val schemaConsistencyEvaluator = SchemaConsistencyEvaluator(
        requiredFields = listOf("id", "name", "status")
    )
    private val statusDistinctnessEvaluator = StatusDistinctnessEvaluator()

    // --- LLM-as-a-judge ---

    private val judgeLM = JudgeLM { prompt ->
        runBlocking {
            val request = ConverseRequest {
                modelId = modelConfiguration.getModel().id
                messages = listOf(
                    Message {
                        role = ConversationRole.User
                        content = listOf(ContentBlock.Text(prompt))
                    }
                )
            }
            val response = bedrockClient.converse(request)
            val output = response.output
            if (output is ConverseOutput.Message) {
                output.value.content
                    .filterIsInstance<ContentBlock.Text>()
                    .joinToString("") { it.value }
            } else {
                ""
            }
        }
    }

    private val llmJudgeEvaluator: LLMJudgeEvaluator = LLMJudgeEvaluator.builder()
        .name("Faithfulness")
        .criteria(
            "Evaluate whether the generated WireMock mocks are a faithful and complete " +
                "representation of the requested mock scenario. The request asked for 4 pets " +
                "with different statuses, mocking GET /pet/{petId} and GET /pet/findByStatus " +
                "endpoints from the Petstore API. Score 1.0 if the output fully satisfies " +
                "the request, 0.0 if it does not."
        )
        .evaluationParams(listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
        .judge(judgeLM)
        .threshold(0.7)
        .build()

    // --- Test resources ---

    private val petstoreSpec: String by lazy {
        checkNotNull(
            javaClass.getResourceAsStream("/eval/petstore-openapi-3.0.yaml")
        ) { "Petstore OpenAPI spec not found on classpath" }.use { it.bufferedReader().readText() }
    }

    // --- Report builder ---

    private val reportBuilder = EvalReportBuilder()

    // --- Main eval test ---

    @ParameterizedTest
    @DatasetSource("classpath:eval/petstore-eval-dataset.json")
    fun `Evaluate Bedrock prompt quality for Petstore spec`(example: Example) {
        val iterationCount = parseIterationCount(System.getenv("BEDROCK_EVAL_ITERATIONS"))
        val results = mutableListOf<IterationResult>()

        logger.info {
            "Starting Bedrock prompt eval: $iterationCount iteration(s), " +
                "model=${modelConfiguration.getModelName()}, region=$region"
        }

        val totalDurationMs = measureTimeMillis {
            for (i in 1..iterationCount) {
                logger.info { "--- Iteration #$i of $iterationCount ---" }
                tokenUsageStore.clear()

                val iterationResult = runSingleIteration(i, example.input())
                results.add(iterationResult)

                if (iterationResult.success) {
                    logger.info {
                        "Iteration #$i: SUCCESS — ${iterationResult.mockCount} mock(s), " +
                            "semantic=${iterationResult.semanticScore?.passed}, " +
                            "tokens=${iterationResult.tokenUsage.totalTokens}, " +
                            "cost=${"$"}${"%.4f".format(iterationResult.estimatedCost)}"
                    }
                } else {
                    logger.warn { "Iteration #$i: FAILED — ${iterationResult.errorMessage}" }
                }
            }
        }

        // Aggregate metrics
        val successRate = calculateSuccessRate(results)
        val semanticSuccessRate = calculateSemanticSuccessRate(results)

        val totalTokenUsage = TokenUsageRecord(
            inputTokens = results.sumOf { it.tokenUsage.inputTokens },
            outputTokens = results.sumOf { it.tokenUsage.outputTokens },
            totalTokens = results.sumOf { it.tokenUsage.totalTokens }
        )
        val totalEstimatedCost = results.sumOf { it.estimatedCost }

        // Build and log report
        val report = reportBuilder.buildReport(
            modelName = modelConfiguration.getModelName(),
            region = region,
            iterationCount = iterationCount,
            results = results,
            totalDurationMs = totalDurationMs,
            totalTokenUsage = totalTokenUsage,
            totalEstimatedCost = totalEstimatedCost
        )
        logger.info { "\n$report" }

        // Assertions
        val successThreshold = 100.0
        val semanticThreshold = 100.0
        assertThreshold(successRate, successThreshold, "Success Rate")
        assertThreshold(semanticSuccessRate, semanticThreshold, "Semantic Success Rate")
    }

    // --- Iteration execution ---

    private fun runSingleIteration(iterationNumber: Int, description: String): IterationResult {
        return runCatching {
            val request = SpecWithDescriptionRequest(
                namespace = MockNamespace(apiName = "petstore-eval"),
                specificationContent = petstoreSpec,
                format = SpecificationFormat.OPENAPI_3,
                description = description,
                options = GenerationOptions(enableValidation = false)
            )

            val generationResult = runBlocking {
                agent.generateFromSpecWithDescription(request)
            }

            // Capture token usage for this iteration
            val records = tokenUsageStore.getRecords()
            val iterationTokenUsage = TokenUsageRecord(
                inputTokens = records.sumOf { it.inputTokens },
                outputTokens = records.sumOf { it.outputTokens },
                totalTokens = records.sumOf { it.totalTokens }
            )
            val iterationCost = CostCalculator.calculateCost(
                inputTokens = iterationTokenUsage.inputTokens,
                outputTokens = iterationTokenUsage.outputTokens
            )

            if (!generationResult.success) {
                return IterationResult(
                    iterationNumber = iterationNumber,
                    success = false,
                    errorMessage = generationResult.error ?: "Generation failed",
                    tokenUsage = iterationTokenUsage,
                    estimatedCost = iterationCost
                )
            }

            val mocks = generationResult.mocks
            val mockIds = mocks.map { it.id }
            val endpointPaths = mocks.map { "${it.metadata.endpoint.method} ${it.metadata.endpoint.path}" }

            // Serialize mocks to JSON for evaluators
            val mappingsJson = "[${mocks.joinToString(",") { it.wireMockMapping }}]"

            // Run semantic evaluators
            val semanticScore = runSemanticEvaluators(description, mappingsJson)

            IterationResult(
                iterationNumber = iterationNumber,
                success = true,
                mockCount = mocks.size,
                mockIds = mockIds,
                endpointPaths = endpointPaths,
                semanticScore = semanticScore,
                tokenUsage = iterationTokenUsage,
                estimatedCost = iterationCost
            )
        }.getOrElse { e ->
            logger.error(e) { "Iteration #$iterationNumber failed with exception" }

            // Still capture any token usage that occurred before the failure
            val records = tokenUsageStore.getRecords()
            val iterationTokenUsage = TokenUsageRecord(
                inputTokens = records.sumOf { it.inputTokens },
                outputTokens = records.sumOf { it.outputTokens },
                totalTokens = records.sumOf { it.totalTokens }
            )
            val iterationCost = CostCalculator.calculateCost(
                inputTokens = iterationTokenUsage.inputTokens,
                outputTokens = iterationTokenUsage.outputTokens
            )

            IterationResult(
                iterationNumber = iterationNumber,
                success = false,
                errorMessage = e.message ?: "Unknown error",
                tokenUsage = iterationTokenUsage,
                estimatedCost = iterationCost
            )
        }
    }

    // --- Semantic evaluation ---

    private fun runSemanticEvaluators(input: String, actualOutput: String): SemanticScore {
        val testCase = EvalTestCase.builder()
            .input(input)
            .actualOutput(actualOutput)
            .expectedOutput("")
            .build()

        val petCountResult = petCountEvaluator.evaluate(testCase)
        val endpointResult = endpointCoverageEvaluator.evaluate(testCase)
        val schemaResult = schemaConsistencyEvaluator.evaluate(testCase)
        val statusResult = statusDistinctnessEvaluator.evaluate(testCase)

        logger.info {
            "Semantic results: petCount=${petCountResult.success()}, " +
                "endpoints=${endpointResult.success()}, " +
                "schema=${schemaResult.success()}, " +
                "statusDistinct=${statusResult.success()}"
        }

        // LLM-as-a-judge (graceful failure)
        val llmJudgeScore = runCatching {
            val judgeResult = llmJudgeEvaluator.evaluate(testCase)
            logger.info { "LLM judge score: ${judgeResult.score()} (threshold: ${judgeResult.threshold()})" }
            judgeResult.score()
        }.onFailure { e ->
            logger.warn(e) { "LLM-as-a-judge evaluation failed, recording null score" }
        }.getOrNull()

        val allProgrammaticPassed = petCountResult.success() &&
            endpointResult.success() &&
            schemaResult.success() &&
            statusResult.success()

        return SemanticScore(
            petCountCorrect = petCountResult.success(),
            endpointsCovered = endpointResult.success(),
            schemaConsistent = schemaResult.success(),
            statusesDistinct = statusResult.success(),
            llmJudgeScore = llmJudgeScore,
            passed = allProgrammaticPassed
        )
    }
}
