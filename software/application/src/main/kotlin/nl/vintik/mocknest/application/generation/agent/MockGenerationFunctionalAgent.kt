package nl.vintik.mocknest.application.generation.agent

import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.domain.generation.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Koog 0.6.0 Functional Agent for AI-powered mock generation.
 * 
 * This agent orchestrates the mock generation process using Koog's functional strategy:
 * - Parses API specifications
 * - Generates WireMock mappings
 * - Handles natural language interpretation
 * - Enhances specifications with AI
 */
class MockGenerationFunctionalAgent(
    private val aiModelService: AIModelServiceInterface,
    private val specificationParser: SpecificationParserInterface,
    private val mockGenerator: MockGeneratorInterface,
    private val generationStorage: GenerationStorageInterface
) {
    
    /**
     * Generate mocks from API specification only.
     */
    suspend fun generateFromSpec(request: MockGenerationRequest): GenerationResult = runCatching {
        val url = request.specificationUrl
        val content = if (url != null) {
            downloadSpecification(url)
        } else {
            requireNotNull(request.specificationContent) { "Missing specification source" }
        }

        // Parse specification
        val specification = specificationParser.parse(content, request.format)

        // Generate mocks
        val generatedMocks = mockGenerator.generateFromSpecification(
            specification = specification,
            namespace = request.namespace,
            options = request.options
        )

        // Store generated mocks
        // generationStorage.storeGeneratedMocks(generatedMocks, request.jobId)

        GenerationResult.success(request.jobId, generatedMocks)

    }.onFailure { e ->
        logger.error(e) { "Generation from spec failed for jobId: ${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }

    /**
     * Generate mocks from natural language description.
     */
    suspend fun generateFromDescription(request: NaturalLanguageRequest): GenerationResult = runCatching {
        // Get existing specification context if requested
        val existingSpec = if (request.useExistingSpec) {
            generationStorage.getSpecification(request.namespace)
        } else null

        val enhancedContext = if (existingSpec != null) {
            request.context + ("existingSpecification" to existingSpec.toString())
        } else request.context

        // Generate mocks using AI
        val generatedMocks = aiModelService.generateMockFromDescription(
            description = request.description,
            namespace = request.namespace,
            context = enhancedContext
        )

        // Store generated mocks
        // generationStorage.storeGeneratedMocks(generatedMocks, request.jobId)

        GenerationResult.success(request.jobId, generatedMocks)

    }.onFailure { e ->
        logger.error(e) { "Generation from description failed for jobId: ${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }

    /**
     * Generate mocks from API specification enhanced with natural language.
     */
    suspend fun generateFromSpecWithDescription(request: SpecWithDescriptionRequest): GenerationResult = runCatching {
        // Parse specification first
        val url = request.specificationUrl
        val content = if (url != null) {
            downloadSpecification(url)
        } else {
            requireNotNull(request.specificationContent) { "Missing specification source" }
        }

        val specification = specificationParser.parse(content, request.format)

        // Use AI service to enhance generation with natural language context
        val enhancedMocks = aiModelService.generateMockFromSpecWithDescription(
            specification = specification,
            description = request.description,
            namespace = request.namespace
        )

        // Store generated mocks
        // generationStorage.storeGeneratedMocks(enhancedMocks, request.jobId)

        GenerationResult.success(request.jobId, enhancedMocks)

    }.onFailure { e ->
        logger.error(e) { "Generation from spec with description failed for jobId: ${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }

    /**
     * Refine an existing mock based on natural language instructions.
     */
    suspend fun refineMock(existingMock: GeneratedMock, refinementRequest: String): GeneratedMock = runCatching {
        // Use AI service to refine the mock
        aiModelService.refineMock(existingMock, refinementRequest)
    }.onFailure { e ->
        logger.warn(e) { "Refinement failed for mock: ${existingMock.id}" }
    }.getOrElse {
        // Return original mock if refinement fails
        existingMock
    }

    private fun downloadSpecification(url: String): String = runCatching {
        URI(url).toURL().readText()
    }.onFailure { e ->
        logger.error(e) { "Failed to download specification from URL: $url" }
    }.getOrElse { e ->
        require(false) { "Failed to download specification from URL: $url. Error: ${e.message}" }
        ""
    }
}