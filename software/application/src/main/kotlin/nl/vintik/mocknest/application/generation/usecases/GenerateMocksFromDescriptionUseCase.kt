package nl.vintik.mocknest.application.generation.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.domain.generation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Use case for generating mocks from natural language descriptions only.
 * Uses the MockGenerationFunctionalAgent to handle AI-powered generation.
 */
class GenerateMocksFromDescriptionUseCase(
    private val mockGenerationAgent: MockGenerationFunctionalAgent
) {
    
    suspend fun execute(request: NaturalLanguageRequest): GenerationResult = runCatching {
        // Use Functional Agent to handle natural language generation
        mockGenerationAgent.generateFromDescription(request)
    }.onFailure { e ->
        logger.error(e) { "Execution failed for description: jobId=${request.jobId}" }
    }.getOrElse { e ->
        GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
    }
}