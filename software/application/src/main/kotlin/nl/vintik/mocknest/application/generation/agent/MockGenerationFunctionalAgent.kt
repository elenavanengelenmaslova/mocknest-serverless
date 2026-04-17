package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.util.SafeUrlResolver
import nl.vintik.mocknest.application.generation.util.UrlFetcher
import nl.vintik.mocknest.domain.generation.*

private val logger = KotlinLogging.logger {}

/**
 * Context for the mock generation process.
 */
data class MockGenerationContext(
    val request: SpecWithDescriptionRequest,
    val specification: APISpecification,
    val mocks: List<GeneratedMock> = emptyList(),
    val attempt: Int = 1,
    val errors: List<String> = emptyList(),
    val parseFailure: Boolean = false,
    val firstPassErrors: List<String>? = null,
    val firstPassMocksGenerated: Int = 0,
    val firstPassMocksValid: Int = 0,
)

/**
 * Koog Functional Agent for AI-powered mock generation.
...
 */
class MockGenerationFunctionalAgent(
    private val aiModelService: AIModelServiceInterface,
    private val specificationParser: SpecificationParserInterface,
    private val mockValidator: MockValidatorInterface,
    private val promptBuilder: PromptBuilderService,
    private val maxRetries: Int = 1, // Default to 1 retry (2 attempts total)
    private val urlFetcher: UrlFetcher = SafeUrlResolver()
) {

    private val mockGenerationStrategy = strategy<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {

        // Node 1: Setup and Parse Specification
        val setupNode by node<SpecWithDescriptionRequest, MockGenerationContext>("setup") { request ->
            val content = resolveContent(request)
            val specification = specificationParser.parse(content, request.format)
            MockGenerationContext(request, specification)
        }

        // Node 2: Initial Mock Generation
        val generateNode by node<MockGenerationContext, MockGenerationContext>("generate") { ctx ->
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(
                ctx.specification, ctx.request.description, ctx.request.namespace, ctx.specification.format
            )
            val response = llm.writeSession {
                appendPrompt { user(prompt) }
                requestLLM()
            }
            val textResponse = (response as? Message.Assistant)?.content
            if (textResponse == null) {
                val errorMsg = "Unexpected LLM response type: ${response::class.simpleName}"
                logger.error { "Parse failure for jobId=${ctx.request.jobId}: $errorMsg" }
                return@node ctx.copy(mocks = emptyList(), errors = listOf(errorMsg), parseFailure = true)
            }
            runCatching {
                val mocks = aiModelService.parseModelResponse(
                    textResponse,
                    ctx.request.namespace,
                    SourceType.SPEC_WITH_DESCRIPTION,
                    "${ctx.specification.title}: ${ctx.request.description}"
                )
                ctx.copy(mocks = mocks)
            }.getOrElse { e ->
                logger.error(e) { "Parse failure for jobId=${ctx.request.jobId}: ${e.message}" }
                ctx.copy(mocks = emptyList(), errors = listOf(e.message ?: "Model response parsing failed"), parseFailure = true)
            }
        }

        // Node 3: Validation
        val validateNode by node<MockGenerationContext, MockGenerationContext>("validate") { ctx ->
            logger.info { "Validating ${ctx.mocks.size} mocks for jobId: ${ctx.request.jobId}" }
            val validationResults = ctx.mocks.map { it to mockValidator.validate(it, ctx.specification) }
            val errors = validationResults.flatMap { it.second.errors }
            val validCount = validationResults.count { it.second.isValid }
            if (errors.isEmpty()) {
                logger.info { "All mocks passed validation for jobId: ${ctx.request.jobId}" }
            } else {
                logger.info { "${errors.size} validation errors found for jobId: ${ctx.request.jobId}" }
            }
            // Capture first-pass stats only on the first attempt
            if (ctx.attempt == 1 && ctx.firstPassErrors == null) {
                ctx.copy(
                    errors = errors,
                    firstPassErrors = errors,
                    firstPassMocksGenerated = ctx.mocks.size,
                    firstPassMocksValid = validCount
                )
            } else {
                ctx.copy(errors = errors)
            }
        }

        // Node 4: AI-Powered Correction (handles both validation errors and parse failures)
        val correctNode by node<MockGenerationContext, MockGenerationContext>("correct") { ctx ->
            val correctionPrompt = if (ctx.parseFailure) {
                // Parse failure: use parsing-correction prompt to regenerate
                promptBuilder.buildParsingCorrectionPrompt(
                    parsingError = ctx.errors.joinToString("; "),
                    namespace = ctx.request.namespace,
                    specification = ctx.specification
                )
            } else {
                // Validation failure: use existing correction prompt
                val invalidInput = ctx.mocks.map { it to mockValidator.validate(it, ctx.specification) }
                    .filter { !it.second.isValid }
                    .map { it.first to it.second.errors }
                promptBuilder.buildCorrectionPrompt(
                    invalidMocks = invalidInput,
                    namespace = ctx.request.namespace,
                    specification = ctx.specification,
                    format = ctx.specification.format
                )
            }

            val response = llm.writeSession {
                appendPrompt { user(correctionPrompt) }
                requestLLM()
            }
            val textResponse = (response as? Message.Assistant)?.content
            if (textResponse == null) {
                val errorMsg = "Unexpected LLM response type during correction: ${response::class.simpleName}"
                logger.error { "Parse failure during correction for jobId=${ctx.request.jobId}: $errorMsg" }
                return@node ctx.copy(
                    mocks = if (ctx.parseFailure) emptyList() else ctx.mocks.filter { mockValidator.validate(it, ctx.specification).isValid },
                    errors = listOf(errorMsg),
                    parseFailure = true,
                    attempt = ctx.attempt + 1
                )
            }

            runCatching {
                val correctedMocks = aiModelService.parseModelResponse(
                    textResponse,
                    ctx.request.namespace,
                    SourceType.REFINEMENT,
                    "Correction for ${ctx.request.namespace.displayName()}"
                )
                val validMocks = if (ctx.parseFailure) emptyList() else ctx.mocks.filter { mockValidator.validate(it, ctx.specification).isValid }
                ctx.copy(mocks = validMocks + correctedMocks, attempt = ctx.attempt + 1, parseFailure = false, errors = emptyList())
            }.getOrElse { e ->
                logger.error(e) { "Parse failure during correction for jobId=${ctx.request.jobId}: ${e.message}" }
                val validMocks = if (ctx.parseFailure) emptyList() else ctx.mocks.filter { mockValidator.validate(it, ctx.specification).isValid }
                ctx.copy(
                    mocks = validMocks,
                    errors = listOf(e.message ?: "Correction response parsing failed"),
                    parseFailure = true,
                    attempt = ctx.attempt + 1
                )
            }
        }

        // Transitions
        edge(nodeStart forwardTo setupNode)
        edge(setupNode forwardTo generateNode)

        // If validation is disabled, go straight to finish
        edge(generateNode forwardTo nodeFinish onCondition { ctx -> !ctx.request.options.enableValidation }
                transformed { ctx ->
                    val metadata = mapOf<String, Any>(
                        "totalGenerated" to ctx.mocks.size,
                        "validationSkipped" to true,
                        "attempts" to ctx.attempt
                    )
                    if (ctx.mocks.isEmpty()) GenerationResult.failure(ctx.request.jobId, ctx.errors.firstOrNull() ?: "No mocks generated", metadata)
                    else GenerationResult.success(ctx.request.jobId, ctx.mocks, metadata)
                }
        )

        // Otherwise, go to validateNode
        edge(generateNode forwardTo validateNode onCondition { ctx -> ctx.request.options.enableValidation })

        edge(
            validateNode forwardTo nodeFinish
                onCondition { ctx -> ctx.errors.isEmpty() || ctx.attempt > maxRetries }
                transformed { ctx ->
                    val finalMocks = ctx.mocks.filter { m -> !mockValidator.validate(m, ctx.specification).isFatal }
                    val droppedCount = ctx.mocks.size - finalMocks.size
                    val firstPassWasValid = ctx.firstPassErrors?.isEmpty() ?: true
                    val metadata = mapOf<String, Any>(
                        "totalGenerated" to ctx.mocks.size,
                        "validationSkipped" to false,
                        "attempts" to ctx.attempt,
                        "mocksDropped" to droppedCount,
                        "validationErrors" to ctx.errors,
                        "firstPassValidationErrors" to (ctx.firstPassErrors ?: emptyList()),
                        "allValid" to (ctx.errors.isEmpty() && droppedCount == 0),
                        "firstPassValid" to firstPassWasValid,
                        "firstPassMocksGenerated" to ctx.firstPassMocksGenerated,
                        "firstPassMocksValid" to ctx.firstPassMocksValid
                    )
                    if (finalMocks.isEmpty()) GenerationResult.failure(ctx.request.jobId, ctx.errors.firstOrNull() ?: "No valid mocks generated", metadata)
                    else GenerationResult.success(ctx.request.jobId, finalMocks, metadata)
                }
        )

        edge(validateNode forwardTo correctNode onCondition { ctx -> ctx.errors.isNotEmpty() && ctx.attempt <= maxRetries })
        edge(correctNode forwardTo validateNode)
    }

    /**
     * Resolves the specification content from the request.
     * Formats that handle their own URL resolution (e.g. GraphQL introspection via POST)
     * receive the URL string directly; other formats have their URL content pre-fetched.
     */
    internal fun resolveContent(request: SpecWithDescriptionRequest): String {
        val url = request.specificationUrl
        return when {
            !url.isNullOrBlank() && request.format.handlesOwnUrlResolution -> url
            !url.isNullOrBlank() -> urlFetcher.fetch(url)
            else -> requireNotNull(request.specificationContent) {
                "Specification content is required when no URL is provided"
            }
        }
    }

    /**
     * Generate mocks from API specification enhanced with natural language.
     */
    suspend fun generateFromSpecWithDescription(request: SpecWithDescriptionRequest): GenerationResult {
        logger.info { "Starting mock generation strategy for jobId: ${request.jobId}" }
        return aiModelService.runStrategy(mockGenerationStrategy, request)
    }
}