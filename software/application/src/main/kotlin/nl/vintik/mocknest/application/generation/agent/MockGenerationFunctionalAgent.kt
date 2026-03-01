package nl.vintik.mocknest.application.generation.agent

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Koog Functional Agent for AI-powered mock generation.
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
    private val mockValidator: MockValidatorInterface,
    private val promptBuilder: PromptBuilderService
) {
    
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

        // Build prompt for AI generation
        val prompt = promptBuilder.buildSpecWithDescriptionPrompt(
            specification = specification,
            description = request.description,
            namespace = request.namespace
        )

        // Use AI service to enhance generation with natural language context
        val enhancedMocks = aiModelService.generateMockFromSpecWithDescription(
            agent = aiModelService.createAgent(),
            specification = specification,
            description = request.description,
            namespace = request.namespace,
            prompt = prompt
        )

        // Validate and correct mocks
        val finalMocks = validateAndCorrectMocks(
            enhancedMocks,
            request.namespace,
            specification,
            request.jobId
        )

        GenerationResult.success(request.jobId, finalMocks)

    }.onFailure { e ->
        logger.error(e) { "Generation from spec with description failed for jobId: ${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }

    private fun downloadSpecification(url: String): String = runCatching {
        URI(url).toURL().readText()
    }.onFailure { e ->
        logger.error(e) { "Failed to download specification from URL: $url" }
    }.getOrElse { e ->
        require(false) { "Failed to download specification from URL: $url. Error: ${e.message}" }
        ""
    }

    private suspend fun validateAndCorrectMocks(
        mocks: List<GeneratedMock>,
        namespace: MockNamespace,
        specification: APISpecification?,
        jobId: String
    ): List<GeneratedMock> {
        if (specification == null) return mocks

        val maxRetries = 2
        var currentMocks = mocks

        for (retry in 1..maxRetries) {
            val validationResults = currentMocks.map { mock ->
                mock to mockValidator.validate(mock, specification)
            }

            val validMocks = validationResults.filter { it.second.isValid }.map { it.first }.toMutableList()
            val invalidMocks = validationResults.filter { !it.second.isValid }

            if (invalidMocks.isEmpty()) {
                logger.info { "Job $jobId: All ${currentMocks.size} mocks are valid" }
                return validMocks
            }

            logger.warn { "Job $jobId: ${invalidMocks.size} mocks failed validation. Attempting correction $retry/$maxRetries" }

            val correctionInput = invalidMocks.map { (mock, result) -> mock to result.errors }
            
            // Build correction prompt
            val correctionPrompt = promptBuilder.buildCorrectionPrompt(
                invalidMocks = correctionInput,
                namespace = namespace,
                specification = specification
            )
            
            val correctedMocks = aiModelService.correctMocks(
                agent = aiModelService.createAgent(),
                invalidMocks = correctionInput,
                namespace = namespace,
                specification = specification,
                prompt = correctionPrompt
            )

            if (correctedMocks.isEmpty()) {
                logger.warn { "Job $jobId: Correction returned no mocks. Returning currently valid mocks." }
                return validMocks
            }

            currentMocks = validMocks + correctedMocks
        }

        // Final validation pass
        return currentMocks.map { mock ->
            mock to mockValidator.validate(mock, specification)
        }.filter { it.second.isValid }.map { it.first }
    }
}