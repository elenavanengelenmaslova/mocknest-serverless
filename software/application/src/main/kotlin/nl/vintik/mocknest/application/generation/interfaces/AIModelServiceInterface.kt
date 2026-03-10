package nl.vintik.mocknest.application.generation.interfaces

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SourceType

/**
 * Abstraction for AI model services (hides Bedrock implementation).
 * This interface provides clean architecture separation between application logic
 * and cloud-specific AI service implementations.
 */
interface AIModelServiceInterface {

    /**
     * Runs a Koog strategy with the provided input.
     */
    suspend fun <Input, Output> runStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>,
        input: Input
    ): Output

    /**
     * Parse the raw model response into a list of GeneratedMock objects.
     */
    fun parseModelResponse(
        response: String,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock>

    /**
     * Get the configured model name.
     */
    fun getModelName(): String

    /**
     * Get the configured inference prefix (if any).
     */
    fun getConfiguredPrefix(): String?
}
