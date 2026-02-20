package nl.vintik.mocknest.infra.aws.generation

import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.domain.generation.TestAgentRequest
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.infra.aws.core.ai.ModelConfiguration
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockTestKoogAgent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BedrockTestKoogAgentTest {
    
    private val bedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val modelConfiguration: ModelConfiguration = mockk(relaxed = true)
    private lateinit var agent: BedrockTestKoogAgent
    private val objectMapper = ObjectMapper()
    
    @BeforeEach
    fun setUp() {
        every { modelConfiguration.getBedrockModel() } returns BedrockModels.AnthropicClaude35SonnetV2
        agent = BedrockTestKoogAgent(modelConfiguration, "eu-west-1", bedrockClient)
    }
    
    @Test
    fun `Given valid instructions When executing agent Then should return success response`() = runTest {
        // Given
        val request = TestAgentRequest(
            instructions = "Tell me a joke about serverless",
            context = mapOf("user" to "developer")
        )
        
        // Claude 3 response format on Bedrock: {"content": [{"text": "...", "type": "text"}], ...}
        val mockBedrockResponseJson = mapOf(
            "id" to "msg_mock_123",
            "type" to "message",
            "role" to "assistant",
            "model" to "claude-3-5-sonnet-20241022",
            "content" to listOf(
                mapOf(
                    "text" to "Why did the serverless function go to therapy? It had too many cold starts!",
                    "type" to "text"
                )
            ),
            "usage" to mapOf(
                "input_tokens" to 10,
                "output_tokens" to 20
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
