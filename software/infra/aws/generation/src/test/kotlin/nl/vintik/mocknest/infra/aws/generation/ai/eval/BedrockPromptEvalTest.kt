package nl.vintik.mocknest.infra.aws.generation.ai.eval

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.evaluators.LLMJudgeEvaluator
import dev.dokimos.koog.asJudge
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.GenerationOptions
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SpecWithDescriptionRequest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter
import nl.vintik.mocknest.infra.aws.generation.ai.config.DefaultInferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferenceMode
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * Multi-protocol Bedrock prompt evaluation test.
 *
 * Exercises the real Koog + Bedrock generation prompt path against REST (OpenAPI),
 * GraphQL, and SOAP (WSDL) specifications. Reads scenarios from a dataset JSON,
 * runs each scenario with retry 0 (generation-only) and retry 1 (generation +
 * validation/correction), captures token usage, and produces a per-protocol
 * summary table.
 *
 * This test is excluded from normal `./gradlew test` runs via the `bedrock-eval`
 * JUnit tag and the `BEDROCK_EVAL_ENABLED` environment variable gate.
 *
 * Run with:
 * ```
 * BEDROCK_EVAL_ENABLED=true \
 *   ./gradlew :software:infra:aws:generation:bedrockEval
 * ```
 */
@Tag("bedrock-eval")
@EnabledIfEnvironmentVariable(named = "BEDROCK_EVAL_ENABLED", matches = "true")
@OptIn(kotlin.time.ExperimentalTime::class)
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
        promptBuilder = promptBuilder,
        apiMethod = BedrockAPIMethod.Converse
    )

    // --- LLM-as-a-judge via Koog + Dokimos integration ---

    // Koog 0.8.0 upgrade review:
    //   - Migrated from SingleLLMPromptExecutor to MultiLLMPromptExecutor for consistency
    //     with BedrockServiceAdapter (SingleLLMPromptExecutor deprecated in 0.8.0).
    //   - LLMProvider.Bedrock singleton restored in 0.8.0 (#1800).
    //   - AIAgent constructor for the LLM judge verified unchanged in 0.8.0 — it still
    //     accepts (promptExecutor, llmModel, systemPrompt, toolRegistry).
    //   - Token usage capture via TokenUsageCapturingClient is Koog-independent — it
    //     decorates the AWS SDK BedrockRuntimeClient, not a Koog type.
    //   - measureTimeMillis latency measurement is Koog-independent — it is a Kotlin
    //     stdlib function (kotlin.system), unaffected by the Koog upgrade.
    private val judgeExecutor by lazy {
        val bedrockLLMClient = BedrockLLMClient(bedrockClient, apiMethod = BedrockAPIMethod.Converse)
        MultiLLMPromptExecutor(LLMProvider.Bedrock to bedrockLLMClient)
    }

    private val judgeLM = asJudge { prompt ->
        val judgeAgent = AIAgent(
            promptExecutor = judgeExecutor,
            llmModel = modelConfiguration.getModel(),
            systemPrompt = "You are an evaluation judge. Respond only with a numeric score.",
            toolRegistry = ToolRegistry.EMPTY
        )
        judgeAgent.run(prompt)
    }

    // --- Scenario data class ---

    private data class EvalScenario(
        val input: String,
        val protocol: String,
        val specFile: String,
        val format: String,
        val namespace: String,
        val description: String,
        val semanticCheck: String
    )

    // --- Per-scenario result ---

    private data class ScenarioResult(
        val scenario: EvalScenario,
        val success: Boolean,
        val mockCount: Int = 0,
        val totalGenerated: Int = 0,
        val mocksDropped: Int = 0,
        val firstPassValid: Boolean = true,
        val firstPassMocksGenerated: Int = 0,
        val firstPassMocksValid: Int = 0,
        val allValid: Boolean = true,
        val attempts: Int = 1,
        val validationErrors: List<String> = emptyList(),
        val latencyMs: Long = 0,
        val tokenUsage: TokenUsageRecord = TokenUsageRecord(),
        val generationCost: Double = 0.0,
        val judgeCost: Double = 0.0,
        val estimatedCost: Double = 0.0,
        val semanticPassed: Boolean = false,
        val errorMessage: String? = null
    ) {
        val scenarioPassed: Boolean
            get() = success && allValid && semanticPassed

        /** % of mocks valid on first pass (0.0-1.0) */
        val firstPassValidRate: Double
            get() = if (firstPassMocksGenerated > 0) firstPassMocksValid.toDouble() / firstPassMocksGenerated else 0.0

        /** % of mocks valid after retry (0.0-1.0) */
        val afterRetryValidRate: Double
            get() = if (totalGenerated > 0) mockCount.toDouble() / totalGenerated else 0.0
    }

    // --- Main eval test ---

    @Test
    fun `Given multi-protocol eval dataset When running all scenarios Then summary and detail tables are produced`() {
        val scenarios = loadScenarios()
        val iterationCount = parseIterationCount(System.getenv("BEDROCK_EVAL_ITERATIONS"))
        val results = mutableListOf<ScenarioResult>()

        logger.info {
            "Starting multi-protocol Bedrock prompt eval: ${scenarios.size} scenario(s), " +
                "$iterationCount iteration(s) each, " +
                "model=${modelConfiguration.getModelName()}, region=$region"
        }

        for (scenario in scenarios) {
            logger.info { "=== Scenario: ${scenario.input} (${scenario.protocol}) ===" }

            for (iter in 1..iterationCount) {
                logger.info { "--- Iteration $iter/$iterationCount ---" }

                // Single run with enableValidation=true (retry budget = 1)
                val result = runScenario(scenario)
                results.add(result)
                logScenarioResult(result)
            }
        }

        // Print summary table
        val summaryTable = buildSummaryTable(results)
        logger.info { "\n$summaryTable" }

        // Print scenario detail table
        val detailTable = buildScenarioDetailTable(results)
        logger.info { "\n$detailTable" }
    }

    // --- Token usage aggregation helper ---

    private fun aggregateTokenUsage(): TokenUsageRecord {
        val records = tokenUsageStore.getRecords()
        return TokenUsageRecord(
            inputTokens = records.sumOf { it.inputTokens },
            outputTokens = records.sumOf { it.outputTokens },
            totalTokens = records.sumOf { it.totalTokens }
        )
    }

    // --- Scenario execution ---

    private fun runScenario(scenario: EvalScenario): ScenarioResult {
        tokenUsageStore.clear()

        var latencyMs = 0L

        return runCatching {
            val agent = createAgentForProtocol(scenario.protocol)
            val specContent = loadSpecContent(scenario.specFile)
            val specFormat = parseFormat(scenario.format)

            val request = SpecWithDescriptionRequest(
                namespace = MockNamespace(apiName = scenario.namespace),
                specificationContent = specContent,
                format = specFormat,
                description = scenario.description,
                options = GenerationOptions(enableValidation = true)
            )

            // Set phase to GENERATION before the Bedrock generation call
            tokenUsageStore.currentPhase = TokenPhase.GENERATION

            // Time only the Bedrock call
            var generationResult: nl.vintik.mocknest.domain.generation.GenerationResult? = null
            latencyMs = measureTimeMillis {
                generationResult = runBlocking {
                    agent.generateFromSpecWithDescription(request)
                }
            }

            val result = checkNotNull(generationResult)

            // Compute generation cost from phase-filtered records
            val generationRecords = tokenUsageStore.getGenerationRecords()
            val generationCost = CostCalculator.calculateCost(
                inputTokens = generationRecords.sumOf { it.inputTokens },
                outputTokens = generationRecords.sumOf { it.outputTokens }
            )

            // Capture total token usage
            val tokenUsage = aggregateTokenUsage()

            if (!result.success) {
                return ScenarioResult(
                    scenario = scenario,
                    success = false,
                    latencyMs = latencyMs,
                    tokenUsage = tokenUsage,
                    generationCost = generationCost,
                    judgeCost = 0.0,
                    estimatedCost = generationCost,
                    errorMessage = result.error ?: "Generation failed"
                )
            }

            val mocks = result.mocks
            val mappingsJson = "[${mocks.joinToString(",") { it.wireMockMapping }}]"

            // Extract validation metadata
            val meta = result.metadata
            val totalGenerated = (meta["totalGenerated"] as? Int) ?: mocks.size
            val mocksDropped = (meta["mocksDropped"] as? Int) ?: 0
            val allValid = (meta["allValid"] as? Boolean) ?: true
            val firstPassValid = (meta["firstPassValid"] as? Boolean) ?: true
            val firstPassMocksGenerated = (meta["firstPassMocksGenerated"] as? Int) ?: mocks.size
            val firstPassMocksValid = (meta["firstPassMocksValid"] as? Int) ?: mocks.size
            val attempts = (meta["attempts"] as? Int) ?: 1
            @Suppress("UNCHECKED_CAST")
            val validationErrors = (meta["validationErrors"] as? List<String>) ?: emptyList()

            if (mocksDropped > 0 || validationErrors.isNotEmpty()) {
                logger.warn {
                    "  Validation report for ${scenario.input}: " +
                        "generated=$totalGenerated, returned=${mocks.size}, " +
                        "dropped=$mocksDropped, errors=${validationErrors.size}, " +
                        "firstPassValid=$firstPassValid, attempts=$attempts"
                }
            }

            // Log first-pass validation errors for analysis
            @Suppress("UNCHECKED_CAST")
            val firstPassValidationErrors = (meta["firstPassValidationErrors"] as? List<String>) ?: emptyList()
            if (firstPassValidationErrors.isNotEmpty()) {
                logger.info {
                    "  1st-pass errors for ${scenario.input}: ${firstPassValidationErrors.joinToString(" | ")}"
                }
            }
            // Log final validation errors if any remain
            if (validationErrors.isNotEmpty()) {
                logger.info {
                    "  Final errors for ${scenario.input}: ${validationErrors.joinToString(" | ")}"
                }
            }

            // Set phase to JUDGE before the semantic judge call
            tokenUsageStore.currentPhase = TokenPhase.JUDGE

            // Run LLM judge for semantic check
            val semanticPassed = runSemanticJudge(scenario, mappingsJson)

            // Compute judge cost from phase-filtered records
            val judgeRecords = tokenUsageStore.getJudgeRecords()
            val judgeCost = CostCalculator.calculateCost(
                inputTokens = judgeRecords.sumOf { it.inputTokens },
                outputTokens = judgeRecords.sumOf { it.outputTokens }
            )

            val totalCost = generationCost + judgeCost

            // Recompute total token usage after judge call so it includes both phases
            val tokenUsageWithJudge = aggregateTokenUsage()

            ScenarioResult(
                scenario = scenario,
                success = true,
                mockCount = mocks.size,
                totalGenerated = totalGenerated,
                mocksDropped = mocksDropped,
                firstPassValid = firstPassValid,
                firstPassMocksGenerated = firstPassMocksGenerated,
                firstPassMocksValid = firstPassMocksValid,
                allValid = allValid,
                attempts = attempts,
                validationErrors = validationErrors,
                latencyMs = latencyMs,
                tokenUsage = tokenUsageWithJudge,
                generationCost = generationCost,
                judgeCost = judgeCost,
                estimatedCost = totalCost,
                semanticPassed = semanticPassed
            )
        }.getOrElse { e ->
            logger.error(e) { "Scenario ${scenario.input} failed with exception" }

            // Compute generation and judge costs from phase-filtered records
            val generationRecords = tokenUsageStore.getGenerationRecords()
            val generationCost = CostCalculator.calculateCost(
                inputTokens = generationRecords.sumOf { it.inputTokens },
                outputTokens = generationRecords.sumOf { it.outputTokens }
            )
            val judgeRecords = tokenUsageStore.getJudgeRecords()
            val judgeCost = CostCalculator.calculateCost(
                inputTokens = judgeRecords.sumOf { it.inputTokens },
                outputTokens = judgeRecords.sumOf { it.outputTokens }
            )

            val tokenUsage = aggregateTokenUsage()

            ScenarioResult(
                scenario = scenario,
                success = false,
                latencyMs = latencyMs,
                tokenUsage = tokenUsage,
                generationCost = generationCost,
                judgeCost = judgeCost,
                estimatedCost = generationCost + judgeCost,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    // --- Protocol-specific agent creation ---

    private fun createAgentForProtocol(protocol: String): MockGenerationFunctionalAgent {
        return when (protocol.uppercase()) {
            "REST" -> MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = OpenAPISpecificationParser(),
                mockValidator = OpenAPIMockValidator(),
                promptBuilder = promptBuilder
            )
            "GRAPHQL" -> MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = GraphQLSpecificationParser(
                    mockk(relaxed = true),
                    GraphQLSchemaReducer()
                ),
                mockValidator = GraphQLMockValidator(),
                promptBuilder = promptBuilder
            )
            "SOAP" -> MockGenerationFunctionalAgent(
                aiModelService = aiModelService,
                specificationParser = WsdlSpecificationParser(
                    mockk(relaxed = true),
                    WsdlParser(),
                    WsdlSchemaReducer()
                ),
                mockValidator = SoapMockValidator(),
                promptBuilder = promptBuilder
            )
            else -> error("Unsupported protocol: $protocol")
        }
    }

    // --- Semantic evaluation via LLM judge ---

    private fun runSemanticJudge(scenario: EvalScenario, actualOutput: String): Boolean {
        return runCatching {
            val evaluator = LLMJudgeEvaluator.builder()
                .name("Scenario Semantic Check")
                .criteria(scenario.semanticCheck)
                .evaluationParams(listOf(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT))
                .judge(judgeLM)
                .threshold(0.7)
                .build()

            val testCase = EvalTestCase.builder()
                .input(scenario.description)
                .actualOutput(actualOutput)
                .expectedOutput("")
                .build()

            val result = evaluator.evaluate(testCase)
            logger.info {
                "LLM judge for ${scenario.input}: score=${result.score()}, " +
                    "threshold=${result.threshold()}, passed=${result.success()}, " +
                    "reason=${result.reason()}"
            }
            result.success()
        }.onFailure { e ->
            logger.warn(e) { "LLM judge evaluation failed for ${scenario.input}" }
        }.getOrDefault(false)
    }

    // --- Dataset loading ---

    private fun loadScenarios(): List<EvalScenario> {
        val datasetJson = checkNotNull(
            javaClass.getResourceAsStream("/eval/multi-protocol-eval-dataset.json")
        ) { "Multi-protocol eval dataset not found on classpath" }.use { it.bufferedReader().readText() }

        val root = Json.parseToJsonElement(datasetJson).jsonObject
        val examples = root["examples"]?.jsonArray ?: error("No 'examples' in dataset")

        val allScenarios = examples.map { example ->
            val obj = example.jsonObject
            val input = obj["input"]?.jsonPrimitive?.content ?: error("Missing 'input'")
            val metadata = obj["metadata"]?.jsonObject ?: error("Missing 'metadata'")

            EvalScenario(
                input = input,
                protocol = metadata["protocol"]?.jsonPrimitive?.content ?: error("Missing 'protocol'"),
                specFile = metadata["specFile"]?.jsonPrimitive?.content ?: error("Missing 'specFile'"),
                format = metadata["format"]?.jsonPrimitive?.content ?: error("Missing 'format'"),
                namespace = metadata["namespace"]?.jsonPrimitive?.content ?: error("Missing 'namespace'"),
                description = metadata["description"]?.jsonPrimitive?.content ?: error("Missing 'description'"),
                semanticCheck = metadata["semanticCheck"]?.jsonPrimitive?.content ?: error("Missing 'semanticCheck'")
            )
        }

        return filterScenarios(allScenarios)
    }

    /**
     * When the env var is not set or empty, returns all scenarios unchanged.
     * If the filter is set but matches zero scenarios, fails fast to surface typos.
     */
    private fun filterScenarios(scenarios: List<EvalScenario>): List<EvalScenario> {
        val filter = System.getenv("BEDROCK_EVAL_FILTER")

        if (filter.isNullOrBlank()) {
            logger.info { "BEDROCK_EVAL_FILTER not set — running all ${scenarios.size} scenario(s)" }
            return scenarios
        }

        val filtered = scenarios.filter { it.input.contains(filter, ignoreCase = true) }

        check(filtered.isNotEmpty()) {
            "BEDROCK_EVAL_FILTER='$filter' matched 0 of ${scenarios.size} scenario(s). " +
                "Available inputs: ${scenarios.map { it.input }}"
        }

        logger.info { "BEDROCK_EVAL_FILTER='$filter' matched ${filtered.size} of ${scenarios.size} scenario(s)" }

        return filtered
    }

    private fun loadSpecContent(specFile: String): String {
        return checkNotNull(
            javaClass.getResourceAsStream("/$specFile")
        ) { "Spec file not found on classpath: $specFile" }.use { it.bufferedReader().readText() }
    }

    private fun parseFormat(format: String): SpecificationFormat {
        return when (format.uppercase()) {
            "OPENAPI_3" -> SpecificationFormat.OPENAPI_3
            "GRAPHQL" -> SpecificationFormat.GRAPHQL
            "WSDL" -> SpecificationFormat.WSDL
            else -> error("Unsupported format: $format")
        }
    }

    // --- Logging helpers ---

    private fun logScenarioResult(result: ScenarioResult) {
        if (result.success) {
            logger.info {
                "  ${result.scenario.input}: SUCCESS — " +
                    "${result.mockCount} mock(s), " +
                    "1stPass=${result.firstPassMocksValid}/${result.firstPassMocksGenerated} (${"%.0f".format(result.firstPassValidRate * 100)}%), " +
                    "afterRetry=${result.mockCount}/${result.totalGenerated} (${"%.0f".format(result.afterRetryValidRate * 100)}%), " +
                    "attempts=${result.attempts}, " +
                    "semantic=${result.semanticPassed}, PASS=${result.scenarioPassed}, " +
                    "latency=${result.latencyMs}ms, " +
                    "genCost=${"$"}${"%.4f".format(result.generationCost)}, " +
                    "judgeCost=${"$"}${"%.4f".format(result.judgeCost)}, " +
                    "totalCost=${"$"}${"%.4f".format(result.estimatedCost)}"
            }
        } else {
            logger.warn {
                "  ${result.scenario.input}: FAILED — ${result.errorMessage}"
            }
        }
    }

    // --- Scenario detail table ---

    private fun buildScenarioDetailTable(results: List<ScenarioResult>): String {
        // Extract API spec name from specFile path (e.g., "eval/petstore-openapi-3.0.yaml" → "petstore")
        fun extractSpecName(specFile: String): String {
            val fileName = specFile.substringAfterLast("/")
            return fileName
                .replace(Regex("-openapi-3\\.0"), "")
                .replace(Regex("-graphql-introspection"), "")
                .replace(Regex("-soap12"), "")
                .replace(Regex("\\.(yaml|yml|json|wsdl|graphql|gql)$"), "")
        }

        // Column widths
        val colInput = 35
        val col1stPass = 12
        val colRetry = 12
        val colPass = 6
        val colGenCost = 10
        val colJudgeCost = 10
        val colTotal = 10
        val colLatency = 9
        val colReason = 30

        val totalWidth = colInput + col1stPass + colRetry + colPass + colGenCost + colJudgeCost + colTotal + colLatency + colReason + 19 // 19 for "║ " prefix + 8 "│ " separators + trailing "║"

        return buildString {
            appendLine("╔${"═".repeat(totalWidth)}╗")
            appendLine("║${"SCENARIO DETAIL TABLE".padStart((totalWidth + 21) / 2).padEnd(totalWidth)}║")
            appendLine("╠${"═".repeat(totalWidth)}╣")

            // Header row
            val header = "║ " +
                "Scenario".padEnd(colInput) + "│ " +
                "1st-pass".padEnd(col1stPass) + "│ " +
                "After-retry".padEnd(colRetry) + "│ " +
                "Pass".padEnd(colPass) + "│ " +
                "Gen cost".padEnd(colGenCost) + "│ " +
                "Judge cost".padEnd(colJudgeCost) + "│ " +
                "Total".padEnd(colTotal) + "│ " +
                "Latency".padEnd(colLatency) + "│ " +
                "Failure reason".padEnd(colReason) + "║"
            appendLine(header)
            appendLine("╠${"═".repeat(totalWidth)}╣")

            // Group results by API spec name
            val grouped = results.groupBy { extractSpecName(it.scenario.specFile) }

            for ((specName, specResults) in grouped) {
                // Group header
                appendLine("║ [$specName]".padEnd(totalWidth + 1) + "║")
                appendLine("╠${"─".repeat(totalWidth)}╣")

                for (result in specResults) {
                    val input = result.scenario.input.take(colInput - 1).padEnd(colInput)
                    val firstPass = "${"%.0f".format(result.firstPassValidRate * 100)}%".padEnd(col1stPass)
                    val afterRetry = "${"%.0f".format(result.afterRetryValidRate * 100)}%".padEnd(colRetry)
                    val pass = (if (result.scenarioPassed) "✓" else "✗").padEnd(colPass)
                    val genCost = "$${"%.4f".format(result.generationCost)}".padEnd(colGenCost)
                    val judgeCost = "$${"%.4f".format(result.judgeCost)}".padEnd(colJudgeCost)
                    val totalCost = "$${"%.4f".format(result.estimatedCost)}".padEnd(colTotal)
                    val latency = "${"%.1f".format(result.latencyMs / 1000.0)}s".padEnd(colLatency)

                    val failureReason = if (!result.scenarioPassed) {
                        val reason = when {
                            result.errorMessage != null -> result.errorMessage
                            !result.semanticPassed -> "Semantic check failed"
                            !result.allValid -> "Validation failed"
                            else -> "Unknown"
                        }
                        reason.take(colReason - 1)
                    } else ""
                    val reasonPadded = failureReason.padEnd(colReason)

                    val row = "║ $input│ $firstPass│ $afterRetry│ $pass│ $genCost│ $judgeCost│ $totalCost│ $latency│ $reasonPadded║"
                    appendLine(row)
                }

                appendLine("╠${"─".repeat(totalWidth)}╣")
            }

            // Replace last separator with bottom border
            val current = toString()
            if (current.endsWith("╠${"─".repeat(totalWidth)}╣\n")) {
                deleteRange(length - ("╠${"─".repeat(totalWidth)}╣\n").length, length)
                append("╚${"═".repeat(totalWidth)}╝")
            } else {
                append("╚${"═".repeat(totalWidth)}╝")
            }
        }
    }

    // --- Summary table ---

    private fun buildSummaryTable(results: List<ScenarioResult>): String {
        val protocols = listOf("REST", "GraphQL", "SOAP")

        return buildString {
            appendLine("╔════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗")
            appendLine("║                              MULTI-PROTOCOL BEDROCK PROMPT EVAL SUMMARY                                                  ║")
            appendLine("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣")
            appendLine("║ Model: ${modelConfiguration.getModelName().padEnd(108)}║")
            appendLine("║ Region: ${region.padEnd(107)}║")
            appendLine("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣")
            appendLine("║ Protocol  │ Runs │ 1st-pass valid │ After retry valid │ Scenario pass │ Gen cost │ Judge cost │ Avg cost/run │ Avg latency ║")
            appendLine("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣")

            for (protocol in protocols) {
                val group = results.filter {
                    it.scenario.protocol.equals(protocol, ignoreCase = true)
                }
                if (group.isEmpty()) continue

                val runs = group.size
                val successfulRuns = group.filter { it.success }

                // 1st-pass valid: avg mock-level valid rate across successful runs
                val firstPassRate = if (successfulRuns.isNotEmpty()) {
                    successfulRuns.map { it.firstPassValidRate }.average() * 100.0
                } else 0.0

                // After retry valid: avg mock-level valid rate after retry
                val afterRetryRate = if (successfulRuns.isNotEmpty()) {
                    successfulRuns.map { it.afterRetryValidRate }.average() * 100.0
                } else 0.0

                // Scenario pass: % of ALL runs where generation succeeded + all valid + semantic passed
                val scenarioPassRate = group.count { it.scenarioPassed }.toDouble() / runs * 100.0

                val totalGenCost = group.sumOf { it.generationCost }
                val totalJudgeCost = group.sumOf { it.judgeCost }
                val avgCost = group.sumOf { it.estimatedCost } / runs
                val avgLatency = group.sumOf { it.latencyMs }.toDouble() / runs / 1000.0

                val line = "║ ${protocol.padEnd(9)} │ " +
                    "$runs".padEnd(5) + "│ " +
                    "${"%.0f".format(firstPassRate)}%".padEnd(15) + "│ " +
                    "${"%.0f".format(afterRetryRate)}%".padEnd(18) + "│ " +
                    "${"%.0f".format(scenarioPassRate)}%".padEnd(14) + "│ " +
                    "${"$"}${"%.4f".format(totalGenCost)}".padEnd(9) + "│ " +
                    "${"$"}${"%.4f".format(totalJudgeCost)}".padEnd(11) + "│ " +
                    "${"$"}${"%.4f".format(avgCost)}".padEnd(13) + "│ " +
                    "${"%.1f".format(avgLatency)}s".padEnd(12) + "║"
                appendLine(line)
            }

            // Total cost footer row
            val totalGenCost = results.sumOf { it.generationCost }
            val totalJudgeCost = results.sumOf { it.judgeCost }
            val totalCombinedCost = results.sumOf { it.estimatedCost }
            val totalRuns = results.size

            appendLine("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣")
            val footerLine = "║ TOTAL     │ " +
                "$totalRuns".padEnd(5) + "│ " +
                "".padEnd(15) + "│ " +
                "".padEnd(18) + "│ " +
                "".padEnd(14) + "│ " +
                "${"$"}${"%.4f".format(totalGenCost)}".padEnd(9) + "│ " +
                "${"$"}${"%.4f".format(totalJudgeCost)}".padEnd(11) + "│ " +
                "${"$"}${"%.4f".format(totalCombinedCost)}".padEnd(13) + "│ " +
                "".padEnd(12) + "║"
            appendLine(footerLine)
            appendLine("╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣")
            val totalCostLine = "║ TOTAL EVAL COST: ${"$"}${"%.4f".format(totalCombinedCost)} " +
                "(generation: ${"$"}${"%.4f".format(totalGenCost)}, judge: ${"$"}${"%.4f".format(totalJudgeCost)}, " +
                "runs: $totalRuns)"
            appendLine(totalCostLine.padEnd(121) + "║")

            append("╚════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝")
        }
    }
}
