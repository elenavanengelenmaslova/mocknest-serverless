package nl.vintik.mocknest.application.generation.interfaces

import ai.koog.agents.core.agent.AIAgent
import nl.vintik.mocknest.domain.generation.*

/**
 * Abstraction for AI model services (hides Bedrock implementation).
 * This interface provides clean architecture separation between application logic
 * and cloud-specific AI service implementations.
 */
interface AIModelServiceInterface {

    /**
     * Create an AI agent instance for the current generation job.
     */
    fun createAgent(): AIAgent<String, String>

    /**
     * Generate mocks from API specification enhanced with natural language.
     * Combines structured specification parsing with AI-powered enhancement.
     */
    suspend fun generateMockFromSpecWithDescription(
        agent: AIAgent<String, String>,
        specification: APISpecification,
        description: String,
        namespace: MockNamespace
    ): List<GeneratedMock>

    /**
     * Attempt to correct invalid mocks based on validation errors.
     * Uses AI to fix the mocks while maintaining the context of the original generation.
     */
    suspend fun correctMocks(
        agent: AIAgent<String, String>,
        invalidMocks: List<Pair<GeneratedMock, List<String>>>,
        namespace: MockNamespace,
        specification: APISpecification? = null
    ): List<GeneratedMock>
}
