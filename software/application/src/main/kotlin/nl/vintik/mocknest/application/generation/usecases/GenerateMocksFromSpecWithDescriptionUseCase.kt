package nl.vintik.mocknest.application.generation.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.domain.generation.GenerationResult
import nl.vintik.mocknest.domain.generation.SpecWithDescriptionRequest

private val logger = KotlinLogging.logger {}

/**
 * Use case for generating mocks from API specification enhanced with natural language.
 * Uses the MockGenerationFunctionalAgent to handle the complex orchestration.
 */
class GenerateMocksFromSpecWithDescriptionUseCase(
    private val mockGenerationAgent: MockGenerationFunctionalAgent
) {
    
    suspend fun execute(request: SpecWithDescriptionRequest): GenerationResult = runCatching {
        // Use Functional Agent to handle spec + description generation
        mockGenerationAgent.generateFromSpecWithDescription(request)
    }.onFailure { e ->
        logger.error(e) { "Execution failed for spec with description: jobId=${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }
}