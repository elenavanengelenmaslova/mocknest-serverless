package io.mocknest.application.generation.agent

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mocknest.domain.generation.TestAgentRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestKoogAgentTest {
    
    private val bedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val agent = TestKoogAgent(bedrockClient)
    
    @Test
    fun `Given valid instructions When executing agent Then should return success response`() = runTest {
        // Given
        val request = TestAgentRequest(
            instructions = "Tell me a joke about serverless",
            context = mapOf("user" to "developer")
        )
        
        val mockBedrockResponse = buildJsonObject {
            put("content", buildJsonObject {
                put("text", "Why did the serverless function go to therapy? It had too many cold starts!")
            })
        }.toString()
        
        coEvery { 
            bedrockClient.invokeModel(any<InvokeModelRequest>()) 
        } returns InvokeModelResponse {
            body = ByteStream.fromString(mockBedrockResponse)
        }
        
        // When
        val response = agent.execute(request)
        
        // Then
        assertTrue(response.success)
        assertEquals("Successfully processed request through Koog and Bedrock", response.message)
        assertTrue(response.bedrockResponse?.contains("serverless") == true)
        
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
        assertTrue(!response.success)
        assertEquals("Failed to process request", response.message)
        assertTrue(response.error?.contains("Bedrock service unavailable") == true)
    }
}
