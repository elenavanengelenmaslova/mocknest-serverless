package nl.vintik.mocknest.infra.aws.core.ai

import ai.koog.prompt.executor.clients.bedrock.BedrockInferencePrefixes
import ai.koog.prompt.executor.clients.bedrock.BedrockModel
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Configuration for Bedrock model selection.
 * Maps environment variable model names to Koog BedrockModels constants
 * and applies GLOBAL inference profile prefix for optimal AWS routing.
 * 
 * The model name is read from the BEDROCK_MODEL_NAME environment variable,
 * which is set by the SAM template's BedrockModelName parameter.
 * If the environment variable is not set, defaults to "AmazonNovaPro".
 * 
 * Uses GLOBAL inference profile prefix to allow AWS to route requests
 * to the best available region, which is appropriate for mock data generation.
 * 
 * If an invalid model name is provided, logs a warning and falls back to
 * BedrockModels.AmazonNovaPro with GLOBAL prefix.
 */
@Component
class ModelConfiguration(
    @param:Value($$"${bedrock.model.name}")
    private val modelName: String,
    @param:Value($$"${bedrock.inference.prefix}")
    private val inferenceProfilePrefix: String
) {
    
    /**
     * Get the LLModel for the configured model name with inference profile.
     * Falls back to the default model with configured prefix if mapping fails.
     * 
     * @return The LLModel corresponding to the configured model name with inference profile prefix
     */
    fun getModel(): LLModel {
        return mapModelNameToLLModel(modelName).withInferenceProfile(inferenceProfilePrefix)
    }
    
    /**
     * Apply configured inference profile prefix to the model.
     * This allows AWS to route requests to the best available region.
     * 
     * @param model The base LLModel to configure
     * @return A BedrockModel with inference profile applied
     */
    private fun applyInferenceProfile(model: LLModel): BedrockModel {
        // Create a new BedrockModel wrapping the LLModel with configured prefix
        return BedrockModel(
            model = model,
            modelId = model.id.substringAfter("."),
            inferenceProfilePrefix = inferenceProfilePrefix
        )
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
     * Falls back to the default model if model name is not found.
     * 
     * @param modelName The model name to map (e.g., "AmazonNovaPro")
     * @return The corresponding LLModel
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
            logger.warn(exception) { "Failed to find model: $modelName in BedrockModels, using default AmazonNovaPro" }
        }.getOrElse {
            logger.warn { "Unknown model name: $modelName, using default AmazonNovaPro" }
            BedrockModels.AmazonNovaPro
        }
    }
}
