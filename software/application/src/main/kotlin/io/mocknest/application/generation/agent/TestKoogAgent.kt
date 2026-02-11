package io.mocknest.application.generation.agent

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mocknest.domain.generation.TestAgentRequest
import io.mocknest.domain.generation.TestAgentResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/**
 * Simple Koog-based agent for testing Bedrock integration
 * 
 * This is a minimal implementation to validate the REST API → Koog → Bedrock flow
 * without the complexity of full mock generation logic.
 */
@Component
class TestKoogAgent(
    private val bedrockClient: BedrockRuntimeClient
) {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Execute agent with user instructions
     * Sends instructions to Bedrock and returns the response
     */
    suspend fun execute(request: TestAgentRequest): TestAgentResponse {
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
        // Build Claude request body
        val requestBody = buildJsonObject {
            put("anthropic_version", "bedrock-2023-05-31")
            put("max_tokens", 2000)
            put("messages", buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }
        
        logger.debug { "Invoking Bedrock with prompt: $prompt" }
        
        // Invoke Bedrock
        val request = InvokeModelRequest {
            modelId = "anthropic.claude-3-sonnet-20240229-v1:0"
            contentType = "application/json"
            accept = "application/json"
            body = requestBody.toString().encodeToByteArray()
        }
        
        val response = bedrockClient.invokeModel(request)
        
        // Parse response
        val responseBody = response.body?.decodeToString() ?: throw IllegalStateException("Empty response from Bedrock")
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        
        // Extract content from Claude response
        val content = responseJson["content"]?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw IllegalStateException("No content in Bedrock response")
        
        return content
    }
}
