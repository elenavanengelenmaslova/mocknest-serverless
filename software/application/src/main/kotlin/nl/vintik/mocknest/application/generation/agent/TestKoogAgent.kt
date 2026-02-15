package nl.vintik.mocknest.application.generation.agent

import nl.vintik.mocknest.domain.generation.TestAgentRequest
import nl.vintik.mocknest.domain.generation.TestAgentResponse

/**
 * Interface for Koog-based agent for testing Bedrock integration.
 * Implementation resides in infrastructure layer.
 */
interface TestKoogAgent {
    /**
     * Execute agent with user instructions
     * Sends instructions to Bedrock and returns the response
     */
    suspend fun execute(request: TestAgentRequest): TestAgentResponse
}
