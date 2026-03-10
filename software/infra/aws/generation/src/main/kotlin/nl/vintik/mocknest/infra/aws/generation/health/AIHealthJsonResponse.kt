package nl.vintik.mocknest.infra.aws.generation.health

import com.fasterxml.jackson.annotation.JsonProperty
import nl.vintik.mocknest.domain.generation.AIHealth
import nl.vintik.mocknest.domain.generation.AIModelHealth

/**
 * JSON response wrapper for AI health.
 * 
 * Converts domain models to JSON-serializable format for HTTP responses.
 */
data class AIHealthJsonResponse(
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("timestamp") val timestamp: String,
    @param:JsonProperty("region") val region: String,
    @param:JsonProperty("version") val version: String,
    @param:JsonProperty("ai") val ai: AIModelHealthJson
) {
    companion object {
        fun from(health: AIHealth): AIHealthJsonResponse {
            return AIHealthJsonResponse(
                status = health.status,
                timestamp = health.timestamp.toString(),
                region = health.region,
                version = health.version,
                ai = AIModelHealthJson.from(health.ai)
            )
        }
    }
}

/**
 * JSON response wrapper for AI model health.
 */
data class AIModelHealthJson(
    @param:JsonProperty("modelName") val modelName: String,
    @param:JsonProperty("inferencePrefix") val inferencePrefix: String?,
    @param:JsonProperty("inferenceMode") val inferenceMode: String,
    @param:JsonProperty("lastInvocationSuccess") val lastInvocationSuccess: Boolean?,
    @param:JsonProperty("officiallySupported") val officiallySupported: Boolean
) {
    companion object {
        fun from(ai: AIModelHealth): AIModelHealthJson {
            return AIModelHealthJson(
                modelName = ai.modelName,
                inferencePrefix = ai.inferencePrefix,
                inferenceMode = ai.inferenceMode,
                lastInvocationSuccess = ai.lastInvocationSuccess,
                officiallySupported = ai.officiallySupported
            )
        }
    }
}