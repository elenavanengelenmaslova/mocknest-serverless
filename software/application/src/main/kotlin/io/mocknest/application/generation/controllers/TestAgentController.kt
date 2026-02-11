package io.mocknest.application.generation.controllers

import io.github.oshai.kotlinlogging.KotlinLogging
import io.mocknest.application.generation.agent.TestKoogAgent
import io.mocknest.domain.generation.TestAgentRequest
import io.mocknest.domain.generation.TestAgentResponse
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for testing Koog + Bedrock integration
 * 
 * Provides a simple endpoint to validate the entire stack:
 * REST API → Koog Agent → Bedrock → Response
 */
@RestController
@RequestMapping("/ai/test")
class TestAgentController(
    private val testKoogAgent: TestKoogAgent
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Test endpoint that accepts any instructions and forwards them to Bedrock via Koog
     * 
     * Example request:
     * POST /ai/test/chat
     * {
     *   "instructions": "Tell me a joke about serverless computing",
     *   "context": {
     *     "user": "developer",
     *     "environment": "test"
     *   }
     * }
     */
    @PostMapping("/chat")
    fun chat(@RequestBody request: TestAgentRequest): ResponseEntity<TestAgentResponse> = runBlocking {
        logger.info { "Received test agent request: ${request.instructions}" }
        
        val response = testKoogAgent.execute(request)
        
        if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.internalServerError().body(response)
        }
    }
    
    /**
     * Health check endpoint for the AI test service
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "healthy",
                "service" to "ai-test-agent",
                "message" to "Koog + Bedrock integration ready"
            )
        )
    }
}
