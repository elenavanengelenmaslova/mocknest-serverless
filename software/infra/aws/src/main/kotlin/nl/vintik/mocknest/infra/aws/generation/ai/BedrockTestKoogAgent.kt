package nl.vintik.mocknest.infra.aws.generation.ai

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.agent.TestKoogAgent
import nl.vintik.mocknest.domain.generation.TestAgentRequest
import nl.vintik.mocknest.domain.generation.TestAgentResponse

/**
 * Bedrock-based implementation of TestKoogAgent.
 * Provides a minimal implementation to validate the REST API -> Koog -> Bedrock flow.
 */
class BedrockTestKoogAgent(
    private val bedrockClient: BedrockRuntimeClient
) : TestKoogAgent {
    
    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()
    
    override suspend fun execute(request: TestAgentRequest): TestAgentResponse {
        return try {
            logger.info { "Executing Koog agent with instructions: ${request.instructions}" }
            
            // Build prompt for Claude
            val prompt = buildPrompt(request.instructions, request.context)
            
            // Invoke Bedrock
            val bedrockResponse = invokeBedrockModel(prompt)
            
            logger.info { "Received response from Bedrock" }
            
            TestAgentResponse(
                success = true,
                message = "Successfully processed request through Koog and Bedrock",
                bedrockResponse = bedrockResponse
            )
        } catch (e: Exception) {
            logger.error(e) { "Error executing Koog agent" }
            TestAgentResponse(
                success = false,
                message = "Failed to process request",
                error = e.message
            )
        }
    }
    
    private fun buildPrompt(instructions: String, context: Map<String, String>): String {
        val contextStr = if (context.isNotEmpty()) {
            "\n\nContext:\n" + context.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        } else {
            ""
        }
        
        return """
You are a helpful AI assistant integrated with MockNest Serverless.

User Instructions:
$instructions$contextStr

Please respond to the user's instructions in a helpful and concise manner.
        """.trimIndent()
    }
    
    private suspend fun invokeBedrockModel(prompt: String): String {
        // Build Claude request body using Jackson
        val messages = listOf(
            mapOf(
                "role" to "user",
                "content" to prompt
            )
        )
        
        val requestBody = mapOf(
            "anthropic_version" to "bedrock-2023-05-31",
            "max_tokens" to 2000,
            "messages" to messages
        )
        
        logger.debug { "Invoking Bedrock with prompt: $prompt" }
        
        // Invoke Bedrock
        val request = InvokeModelRequest {
            modelId = "anthropic.claude-3-sonnet-20240229-v1:0"
            contentType = "application/json"
            accept = "application/json"
            body = objectMapper.writeValueAsBytes(requestBody)
        }
        
        val response = bedrockClient.invokeModel(request)
        
        // Parse response
        val responseBody = response.body.toString(Charsets.UTF_8)
        val responseJson = objectMapper.readTree(responseBody)
        
        // Extract content from Claude response
        // Claude 3 response format on Bedrock: {"content": [{"text": "...", "type": "text"}], ...}
        val content = responseJson.path("content").path(0).path("text").asText()
        if (content.isNullOrBlank()) {
             throw IllegalStateException("No content in Bedrock response")
        }
        
        return content
    }
}
