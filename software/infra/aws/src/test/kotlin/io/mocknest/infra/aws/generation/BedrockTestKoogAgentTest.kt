package io.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mocknest.domain.generation.TestAgentRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BedrockTestKoogAgentTest {
    
    private val bedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val agent = BedrockTestKoogAgent(bedrockClient)
    private val objectMapper = ObjectMapper()
    
    @Test
    fun `Given valid instructions When executing agent Then should return success response`() = runTest {
        // Given
        val request = TestAgentRequest(
            instructions = "Tell me a joke about serverless",
            context = mapOf("user" to "developer")
        )
        
        // Claude 3 response format on Bedrock: {"content": [{"text": "...", "type": "text"}], ...}
        val mockBedrockResponseJson = mapOf(
            "content" to listOf(
                mapOf(
                    "text" to "Why did the serverless function go to therapy? It had too many cold starts!",
                    "type" to "text"
                )
            )
        )
        val mockBedrockResponseBytes = objectMapper.writeValueAsBytes(mockBedrockResponseJson)
        
        coEvery { 
            bedrockClient.invokeModel(any<InvokeModelRequest>()) 
        } returns InvokeModelResponse {
            body = mockBedrockResponseBytes
            contentType = "application/json"
        }
        
        // When
        val response = agent.execute(request)
        
        // Then
        assertTrue(response.success, "Response should be successful")
        assertEquals("Successfully processed request through Koog and Bedrock", response.message)
        assertTrue(response.bedrockResponse?.contains("serverless") == true, "Response should contain 'serverless'")
        
        coVerify(exactly = 1) { 
            bedrockClient.invokeModel(any<InvokeModelRequest>()) 
        }
    }
    
    @Test
    fun `Given Bedrock failure When executing agent Then should return error response`() = runTest {
        // Given
        val request = TestAgentRequest(
            instructions = "Test instruction"
        )
        
        coEvery { 
            bedrockClient.invokeModel(any<InvokeModelRequest>()) 
        } throws RuntimeException("Bedrock service unavailable")
        
        // When
        val response = agent.execute(request)
        
        // Then
        assertTrue(!response.success, "Response should not be successful")
        assertEquals("Failed to process request", response.message)
        assertTrue(response.error?.contains("Bedrock service unavailable") == true, "Error should contain message")
    }
}
