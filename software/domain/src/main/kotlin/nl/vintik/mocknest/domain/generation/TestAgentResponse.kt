package nl.vintik.mocknest.domain.generation

import kotlinx.serialization.Serializable

/**
 * Simple test response from Koog agent
 */
@Serializable
data class TestAgentResponse(
    val success: Boolean,
    val message: String,
    val bedrockResponse: String? = null,
    val error: String? = null
)
