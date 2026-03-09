package nl.vintik.mocknest.infra.aws.generation.health

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * AI health check response.
 * 
 * Provides information about the AI features status, deployment region,
 * model configuration, and inference settings.
 */
data class AIHealthResponse(
    @JsonProperty("status") val status: String,
    @JsonProperty("timestamp") val timestamp: String,
    @JsonProperty("region") val region: String,
    @JsonProperty("ai") val ai: AIHealth
)

/**
 * AI health information.
 * 
 * Includes model name, inference prefix, inference mode, last invocation status,
 * and whether the model is officially supported.
 */
data class AIHealth(
    @JsonProperty("modelName") val modelName: String,
    @JsonProperty("inferencePrefix") val inferencePrefix: String?,
    @JsonProperty("inferenceMode") val inferenceMode: String,
    @JsonProperty("lastInvocationSuccess") val lastInvocationSuccess: Boolean?,
    @JsonProperty("officiallySupported") val officiallySupported: Boolean
)
