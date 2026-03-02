package nl.vintik.mocknest.application.generation.agent

import ai.koog.prompt.message.Message
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.forwardTo
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Context for the mock generation process.
 */
data class MockGenerationContext(
    val request: SpecWithDescriptionRequest,
    val specification: APISpecification,
    val mocks: List<GeneratedMock> = emptyList(),
    val attempt: Int = 1,
    val errors: List<String> = emptyList()
)

/**
 * Koog Functional Agent for AI-powered mock generation.
...
 */
class MockGenerationFunctionalAgent(
    private val aiModelService: AIModelServiceInterface,
    private val specificationParser: SpecificationParserInterface,
    private val mockValidator: MockValidatorInterface,
    private val promptBuilder: PromptBuilderService
) {
    
    private val mockGenerationStrategy = strategy<SpecWithDescriptionRequest, GenerationResult>("mock-generation") {
        
        // Node 1: Setup and Parse Specification
        val setupNode by node<SpecWithDescriptionRequest, MockGenerationContext>("setup") { request ->
            val url = request.specificationUrl
            val content = if (url != null) {
                runCatching { URI(url).toURL().readText() }.getOrThrow()
            } else {
                request.specificationContent!!
            }
            val specification = specificationParser.parse(content, request.format)
            MockGenerationContext(request, specification)
        }

        // Node 2: Initial Mock Generation
        val generateNode by node<MockGenerationContext, MockGenerationContext>("generate") { ctx ->
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(
                ctx.specification, ctx.request.description, ctx.request.namespace
            )
            val response = llm.writeSession {
                appendPrompt { user(prompt) }
                requestLLM() 
            }
            val textResponse = (response as? Message.Assistant)?.content ?: ""
            val mocks = aiModelService.parseModelResponse(
                textResponse, 
                ctx.request.namespace, 
                SourceType.SPEC_WITH_DESCRIPTION, 
                "${ctx.specification.title}: ${ctx.request.description}"
            )
            ctx.copy(mocks = mocks)
        }

        // Node 3: Validation
        val validateNode by node<MockGenerationContext, MockGenerationContext>("validate") { ctx ->
            val validationResults = ctx.mocks.map { it to mockValidator.validate(it, ctx.specification) }
            val errors = validationResults.flatMap { it.second.errors }
            ctx.copy(errors = errors)
        }

        // Node 4: AI-Powered Correction
        val correctNode by node<MockGenerationContext, MockGenerationContext>("correct") { ctx ->
            val invalidInput = ctx.mocks.map { it to mockValidator.validate(it, ctx.specification) }
                .filter { !it.second.isValid }
                .map { it.first to it.second.errors }
                
            val correctionPrompt = promptBuilder.buildCorrectionPrompt(
                invalidMocks = invalidInput,
                namespace = ctx.request.namespace,
                specification = ctx.specification
            )
            val response = llm.writeSession {
                appendPrompt { user(correctionPrompt) }
                requestLLM()
            }
            val textResponse = (response as? Message.Assistant)?.content ?: ""
            val correctedMocks = aiModelService.parseModelResponse(
                textResponse, 
                ctx.request.namespace, 
                SourceType.REFINEMENT, 
                "Correction for ${ctx.request.namespace.displayName()}"
            )
            
            val validMocks = ctx.mocks.filter { mockValidator.validate(it, ctx.specification).isValid }
            ctx.copy(mocks = validMocks + correctedMocks, attempt = ctx.attempt + 1)
        }

        // Transitions
        edge(nodeStart forwardTo setupNode)
        edge(setupNode forwardTo generateNode)
        
        // If validation is disabled, go straight to finish
        edge(generateNode forwardTo nodeFinish onCondition { ctx -> !ctx.request.options.enableValidation }
            transformed { ctx -> GenerationResult.success(ctx.request.jobId, ctx.mocks) }
        )
        
        // Otherwise, go to validateNode
        edge(generateNode forwardTo validateNode onCondition { ctx -> ctx.request.options.enableValidation })
        
        edge(validateNode forwardTo nodeFinish 
            onCondition { ctx -> ctx.errors.isEmpty() || ctx.attempt > 2 }
            transformed { ctx -> GenerationResult.success(ctx.request.jobId, ctx.mocks.filter { m -> mockValidator.validate(m, ctx.specification).isValid }) }
        )
        
        edge(validateNode forwardTo correctNode onCondition { ctx -> ctx.errors.isNotEmpty() && ctx.attempt <= 2 })
        edge(correctNode forwardTo validateNode)
    }

    /**
     * Generate mocks from API specification enhanced with natural language.
     */
    suspend fun generateFromSpecWithDescription(request: SpecWithDescriptionRequest): GenerationResult {
        logger.info { "Starting mock generation strategy for jobId: ${request.jobId}" }
        return aiModelService.runStrategy(mockGenerationStrategy, request)
    }
}