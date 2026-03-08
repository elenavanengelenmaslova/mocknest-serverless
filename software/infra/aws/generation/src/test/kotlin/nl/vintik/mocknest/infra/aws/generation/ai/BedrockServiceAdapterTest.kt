package nl.vintik.mocknest.infra.aws.generation.ai

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class BedrockServiceAdapterTest {

    private val bedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val modelConfiguration: ModelConfiguration = mockk(relaxed = true)
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)
    private val adapter = BedrockServiceAdapter(bedrockClient, modelConfiguration, promptBuilder)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class StrategyExecution {

        @Test
        fun `Given strategy When running strategy Then should create GraphAIAgent and run it`() = runBlocking {
            // Given
            val strategy = strategy<String, String>("test-strategy") {
                edge(nodeStart forwardTo nodeFinish)
            }
            
            val input = "test input"
            
            // We need to mock the modelConfiguration as it's used inside runStrategy
            every { modelConfiguration.getModel() } returns mockk(relaxed = true)

            // When
            val output = adapter.runStrategy(strategy, input)

            // Then
            assertEquals(input, output)
        }
    }

    @Nested
    inner class MockCreation {

        @Test
        fun `Given mapping and namespace When creating generated mock Then should include displayName in ID`() {
            // Given
            val mapping = """
            {
              "request": {
                "method": "POST",
                "url": "/test-client/petstore/pet"
              },
              "response": {
                "status": 201
              }
            }
            """.trimIndent()
            val namespace = MockNamespace(apiName = "petstore", client = "test-client")

            // When
            val mappingJson = mapper.readTree(mapping)
            val mock = adapter.createGeneratedMock(mappingJson, namespace, SourceType.SPEC_WITH_DESCRIPTION, "ref", 1)

            // Then
            assertTrue(mock.id.contains("test-client-petstore"))
            assertTrue(mock.id.startsWith("ai-generated-test-client-petstore-post--test-client-petstore-pet-1"))
            assertEquals(HttpMethod.POST, mock.metadata.endpoint.method)
            assertEquals("/test-client/petstore/pet", mock.metadata.endpoint.path)
        }

        @Test
        fun `Given markdown response with explanation When parsing model response Then should extract correct JSON`() {
            // Given - Model response with markdown blocks and some chatty text
            val response = """
            I have generated the mock for you.
            [Note: I used the petstore namespace]
            
            ```json
            [
              {
                "request": {
                  "method": "GET",
                  "url": "/pet/1"
                },
                "response": {
                  "status": 200,
                  "body": "{\"id\": 1, \"name\": \"dog\"}"
                }
              }
            ]
            ```
            
            Let me know if you need anything else!
            """.trimIndent()
            val namespace = MockNamespace(apiName = "petstore")

            // When
            val mocks = adapter.parseModelResponse(response, namespace, SourceType.SPEC_WITH_DESCRIPTION, "ref")

            // Then
            assertEquals(1, mocks.size, "Should extract exactly one mock despite chatty text")
            assertEquals("GET", mocks[0].metadata.endpoint.method.name(), "Mock method should be GET")
            assertEquals("/pet/1", mocks[0].metadata.endpoint.path, "Mock path should be /pet/1")
        }
    }
}
