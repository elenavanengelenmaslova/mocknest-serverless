package nl.vintik.mocknest.infra.aws.generation.ai.config

import ai.koog.prompt.executor.clients.bedrock.BedrockModel
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.withInferenceProfile
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Configuration for Bedrock model selection with automatic inference prefix fallback.
 * 
 * Maps environment variable model names to Koog BedrockModels constants and applies
 * inference profile prefixes with intelligent fallback strategy.
 * 
 * The model name is read from the BEDROCK_MODEL_NAME environment variable,
 * which is set by the SAM template's BedrockModelName parameter.
 * Defaults to "AmazonNovaPro", which is the officially supported model.
 * 
 * Uses InferencePrefixResolver to determine candidate inference prefixes based on
 * deployment region and inference mode. Implements retry logic with fallback:
 * 1. Try each candidate prefix in order
 * 2. Retry on retryable errors (model not found, access denied, not enabled)
 * 3. Skip retry on non-retryable errors (throttling, validation errors)
 * 4. Cache successful prefix for subsequent invocations
 * 5. Fall back to no prefix if all candidates fail
 * 
 * If an invalid model name is provided, logs a warning and falls back to
 * BedrockModels.AmazonNovaPro.
 */
@Component
class ModelConfiguration(
    @param:Value($$"${bedrock.model.name}")
    private val modelName: String,
    private val prefixResolver: InferencePrefixResolver
) {
    
    /**
     * Cached inference prefix after first successful invocation.
     * Null until a successful prefix is found.
     */
    private var cachedPrefix: String? = null
    
    /**
     * Get the LLModel for the configured model name with optimal inference prefix.
     * 
     * Implements intelligent fallback strategy:
     * 1. If prefix is cached, use it immediately
     * 2. Try each candidate prefix from resolver in order
     * 3. Retry on retryable errors (model not found, access denied, not enabled)
     * 4. Propagate non-retryable errors immediately
     * 5. Cache successful prefix for subsequent calls
     * 6. Fall back to no prefix if all candidates fail
     * 
     * @return The LLModel corresponding to the configured model name with inference profile prefix
     * @throws ModelConfigurationException if all attempts fail
     */
    fun getModel(): LLModel {
        val baseModel = mapModelNameToLLModel(modelName)
        
        // Use cached prefix if available
        cachedPrefix?.let {
            logger.debug { "Using cached inference prefix: $it" }
            return baseModel.withInferenceProfile(it)
        }
        
        // Try candidate prefixes in order
        val candidates = prefixResolver.getCandidatePrefixes()
        logger.debug { "Attempting inference prefixes in order: $candidates" }
        
        for (prefix in candidates) {
            runCatching {
                logger.debug { "Attempting inference prefix: $prefix" }
                val model = baseModel.withInferenceProfile(prefix)
                // Cache successful prefix
                cachedPrefix = prefix
                logger.info { "Successfully configured model $modelName with inference prefix: $prefix" }
                return model
            }.onFailure { exception ->
                if (isRetryableError(exception)) {
                    logger.debug(exception) { "Prefix $prefix failed with retryable error, trying next candidate" }
                } else {
                    logger.error(exception) { "Non-retryable error with prefix $prefix, propagating immediately" }
                    throw exception
                }
            }
        }
        
        // Final fallback: try without prefix
        runCatching {
            logger.debug { "All candidate prefixes failed, attempting model without prefix" }
            val model = baseModel
            logger.info { "Successfully configured model $modelName without inference prefix" }
            return model
        }.onFailure { exception ->
            val errorMessage = "Failed to configure model $modelName in region ${prefixResolver.deployRegion}. " +
                    "Attempted prefixes: $candidates. Error: ${exception.message}"
            logger.error(exception) { errorMessage }
            throw ModelConfigurationException(errorMessage, exception)
        }
        
        // This should never be reached, but Kotlin requires a return
        throw ModelConfigurationException(
            "Failed to configure model $modelName after all attempts in region ${prefixResolver.deployRegion}"
        )
    }
    
    /**
     * Check if an error is retryable (should try next prefix).
     * 
     * Retryable errors indicate the model is not available with the current prefix:
     * - Model not found
     * - Access denied
     * - Access not enabled
     * 
     * Non-retryable errors indicate a genuine service or configuration issue:
     * - Throttling
     * - Validation errors
     * - Internal server errors
     * 
     * @param exception The exception to check
     * @return true if the error is retryable, false otherwise
     */
    private fun isRetryableError(exception: Throwable): Boolean {
        val message = exception.message?.lowercase() ?: return false
        return message.contains("not found") ||
                message.contains("access denied") ||
                message.contains("not enabled") ||
                message.contains("does not exist")
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
     * Get the successfully cached inference prefix (if any).
     * 
     * @return The cached prefix, or null if not yet cached
     */
    fun getCachedPrefix(): String? = cachedPrefix
    
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
