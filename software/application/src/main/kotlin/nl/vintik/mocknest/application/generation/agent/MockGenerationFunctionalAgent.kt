package nl.vintik.mocknest.application.generation.agent

import ai.koog.agents.core.agent.AIAgent
import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.domain.generation.*
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val mockValidator: MockValidatorInterface
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

        // Create AI agent for this job
        val agent = aiModelService.createAgent()

        // Use AI service to enhance generation with natural language context
        val enhancedMocks = aiModelService.generateMockFromSpecWithDescription(
            agent = agent,
            specification = specification,
            description = request.description,
            namespace = request.namespace
        )

        // Validate and correct mocks
        val finalMocks = validateAndCorrectMocks(
            agent,
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
        agent: AIAgent<String, String>,
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
            val correctedMocks = aiModelService.correctMocks(agent, correctionInput, namespace, specification)

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