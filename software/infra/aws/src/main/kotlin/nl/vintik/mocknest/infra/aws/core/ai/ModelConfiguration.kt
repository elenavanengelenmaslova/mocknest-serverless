package nl.vintik.mocknest.infra.aws.core.ai

import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Configuration for Bedrock model selection.
 * Maps environment variable model names to Koog BedrockModels constants.
 * 
 * The model name is read from the BEDROCK_MODEL_NAME environment variable,
 * which is set by the SAM template's BedrockModelName parameter.
 * If the environment variable is not set, defaults to "AnthropicClaude35SonnetV2".
 * 
 * If an invalid model name is provided, logs a warning and falls back to
 * BedrockModels.AnthropicClaude35SonnetV2.
 */
@Component
class ModelConfiguration(
    @Value("\${bedrock.model.name:AnthropicClaude35SonnetV2}")
    private val modelName: String
) {
    
    /**
     * Get the LLModel for the configured model name.
     * Falls back to Claude 3.5 Sonnet v2 if mapping fails.
     * 
     * @return The LLModel corresponding to the configured model name
     */
    fun getBedrockModel(): LLModel {
        return runCatching {
            mapModelNameToLLModel(modelName)
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to map model name '$modelName' to BedrockModel, using default AnthropicClaude35SonnetV2" }
        }.getOrDefault(BedrockModels.AnthropicClaude35SonnetV2)
    }
    
    /**
     * Get the model name for logging and debugging.
     * 
     * @return The configured model name
     */
    fun getModelName(): String = modelName
    
    /**
     * Maps a model name string to the corresponding LLModel from BedrockModels.
     * Uses Kotlin reflection to look up BedrockModels properties by name.
     * 
     * @param modelName The model name to map (e.g., "AnthropicClaude35SonnetV2")
     * @return The corresponding LLModel
     * @throws IllegalStateException if the model name is not found or is not an LLModel
     */
    private fun mapModelNameToLLModel(modelName: String): LLModel {
        return runCatching {
            // Use Kotlin reflection to look up BedrockModels property by name
            val property = BedrockModels::class.members
                .firstOrNull { it.name == modelName }
                ?: error("Model name not found in BedrockModels: $modelName")
            
            // Call the property to get its value
            val value = property.call(BedrockModels)
            
            // Verify it's an LLModel
            value as? LLModel
                ?: error("Property $modelName in BedrockModels is not an LLModel")
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to find model: $modelName in BedrockModels" }
        }.getOrElse {
            logger.warn { "Unknown model name: $modelName, using default AnthropicClaude35SonnetV2" }
            BedrockModels.AnthropicClaude35SonnetV2
        }
    }
}
