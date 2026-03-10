package nl.vintik.mocknest.domain.generation

import java.time.Instant

/**
 * AI health information.
 * 
 * Represents the health status of the MockNest AI features including
 * model configuration, inference settings, and operational status.
 */
data class AIHealth(
    val status: String,
    val timestamp: Instant,
    val region: String,
    val version: String,
    val ai: AIModelHealth
)

/**
 * AI model health information.
 * 
 * Includes model name, inference prefix, inference mode, last invocation status,
 * and whether the model is officially supported.
 */
data class AIModelHealth(
    val modelName: String,
    val inferencePrefix: String?,
    val inferenceMode: String,
    val lastInvocationSuccess: Boolean?,
    val officiallySupported: Boolean
)