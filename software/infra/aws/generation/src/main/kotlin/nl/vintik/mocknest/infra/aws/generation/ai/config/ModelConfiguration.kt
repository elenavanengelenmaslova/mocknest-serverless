package nl.vintik.mocknest.infra.aws.generation.ai.config

import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Configuration for Bedrock model selection with inference prefix.
 * 
 * Maps environment variable model names to Koog BedrockModels constants and applies
 * inference profile prefixes based on deployment region and inference mode.
 * 
 * The model name is read from the BEDROCK_MODEL_NAME environment variable,
 * which is set by the SAM template's BedrockModelName parameter.
 * Defaults to "AmazonNovaPro", which is the officially supported model.
 * 
 * Uses InferencePrefixResolver to determine the appropriate inference prefix.
 * For AUTO mode, uses geo-specific prefix first (which Nova models require).
 * 
 * If an invalid model name is provided, logs a warning and falls back to
 * BedrockModels.AmazonNovaPro.
 * 
 * Koog 0.8.0 review: BedrockModels constants, reflection-based lookup, and
 * withInferenceProfile extension are all unchanged in 0.8.0. No adaptation needed.
 */
@Component
class ModelConfiguration(
    @param:Value("\${bedrock.model.name}")
    private val modelName: String,
    private val prefixResolver: InferencePrefixResolver
) {
    
    /**
     * Get the LLModel for the configured model name with inference prefix.
     * 
     * Uses the first candidate prefix from the resolver. For AUTO mode,
     * this will be the geo-specific prefix (eu, us, etc.) which Nova models require.
     * 
     * @return The LLModel corresponding to the configured model name with inference profile prefix
     */
    fun getModel(): LLModel {
        val baseModel = mapModelNameToLLModel(modelName)
        val candidates = prefixResolver.getCandidatePrefixes()
        
        // Use the first candidate prefix (geo prefix for AUTO mode)
        val prefix = candidates.firstOrNull()
        
        return if (prefix != null) {
            logger.info { "Configuring model $modelName with inference prefix: $prefix" }
            baseModel.withInferenceProfile(prefix)
        } else {
            logger.info { "Configuring model $modelName without inference prefix" }
            baseModel
        }
    }
    
    /**
     * Get the model name for logging and health checks.
     * 
     * @return The configured model name
     */
    fun getModelName(): String = modelName
    
    /**
     * Check if the current model is officially supported.
     * 
     * @return true if the model is AmazonNovaPro (officially supported), false otherwise
     */
    fun isOfficiallySupported(): Boolean = modelName == "AmazonNovaPro"
    
    /**
     * Get the configured inference prefix.
     * 
     * @return The first candidate prefix from the resolver
     */
    fun getConfiguredPrefix(): String? = prefixResolver.getCandidatePrefixes().firstOrNull()
    
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
