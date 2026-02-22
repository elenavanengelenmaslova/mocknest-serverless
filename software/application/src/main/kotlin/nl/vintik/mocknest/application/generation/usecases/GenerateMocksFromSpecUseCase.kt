package nl.vintik.mocknest.application.generation.usecases

import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.domain.generation.GenerationResult
import nl.vintik.mocknest.domain.generation.MockGenerationRequest

/**
 * Use case for generating mocks from API specifications only.
 * Follows clean architecture principles with dependency inversion.
 */
class GenerateMocksFromSpecUseCase(
    private val mockGenerationAgent: MockGenerationFunctionalAgent
) {
    
    suspend fun execute(request: MockGenerationRequest): GenerationResult {
        return mockGenerationAgent.generateFromSpec(request)
    }
}