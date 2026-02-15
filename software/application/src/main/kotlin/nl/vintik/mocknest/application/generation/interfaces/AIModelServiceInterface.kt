package nl.vintik.mocknest.application.generation.interfaces

import nl.vintik.mocknest.domain.generation.*

/**
 * Abstraction for AI model services (hides Bedrock implementation).
 * This interface provides clean architecture separation between application logic
 * and cloud-specific AI service implementations.
 */
interface AIModelServiceInterface {
    
    /**
     * Generate mocks from natural language description only.
     * Uses AI to interpret the description and create appropriate WireMock mappings.
     */
    suspend fun generateMockFromDescription(
        description: String,
        namespace: MockNamespace,
        context: Map<String, String> = emptyMap()
    ): List<GeneratedMock>
    
    /**
     * Generate mocks from API specification enhanced with natural language.
     * Combines structured specification parsing with AI-powered enhancement.
     */
    suspend fun generateMockFromSpecWithDescription(
        specification: APISpecification,
        description: String,
        namespace: MockNamespace
    ): List<GeneratedMock>
    
    /**
     * Refine an existing mock based on natural language instructions.
     * Used for iterative improvement of generated mocks.
     */
    suspend fun refineMock(
        existingMock: GeneratedMock,
        refinementRequest: String
    ): GeneratedMock
    
    /**
     * Enhance response realism using AI.
     * Improves the quality and realism of mock response data.
     */
    suspend fun enhanceResponseRealism(
        mockResponse: String,
        schema: JsonSchema,
        context: Map<String, String> = emptyMap()
    ): String
    
    /**
     * Check if the AI model service is available and healthy.
     */
    suspend fun isHealthy(): Boolean
    
    /**
     * Get information about the AI model service capabilities.
     */
    fun getCapabilities(): AIServiceCapabilities
}

/**
 * Capabilities of an AI model service.
 */
data class AIServiceCapabilities(
    val supportsNaturalLanguageGeneration: Boolean,
    val supportsSpecEnhancement: Boolean,
    val supportsMockRefinement: Boolean,
    val supportsResponseEnhancement: Boolean,
    val maxInputTokens: Int,
    val maxOutputTokens: Int,
    val supportedLanguages: Set<String>
)