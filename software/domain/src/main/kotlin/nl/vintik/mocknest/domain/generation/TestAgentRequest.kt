package nl.vintik.mocknest.domain.generation

import kotlinx.serialization.Serializable

/**
 * Simple test request to validate Koog + Bedrock integration
 */
@Serializable
data class TestAgentRequest(
    val instructions: String,
    val context: Map<String, String> = emptyMap()
)
