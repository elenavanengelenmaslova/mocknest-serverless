package nl.vintik.mocknest.infra.aws.generation.ai

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import io.mockk.clearAllMocks
import io.mockk.mockk
import nl.vintik.mocknest.domain.generation.*
import nl.vintik.mocknest.infra.aws.core.ai.ModelConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class BedrockServiceAdapterTest {

    private val bedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val modelConfiguration: ModelConfiguration = mockk(relaxed = true)
    private val adapter = BedrockServiceAdapter(bedrockClient, modelConfiguration)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class PromptBuilding {

        @Test
        fun `Given specification and namespace When building spec with description prompt Then should include displayName and client in prompt`() {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/users",
                        method = HttpMethod.GET,
                        operationId = "getUsers",
                        summary = "Get users",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                    )
                ),
                schemas = emptyMap()
            )
            val description = "Add more realism"
            val namespace = MockNamespace(apiName = "petstore", client = "test-client")

            // When
            val prompt = adapter.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then
            assertTrue(prompt.contains("API Name: petstore"))
            assertTrue(prompt.contains("- Client: test-client"))
            assertTrue(prompt.contains("IMPORTANT: All mock URLs must be prefixed with /test-client/petstore"))
            assertTrue(prompt.contains("Enhancement Description: Add more realism"))
            assertTrue(prompt.contains("Prefer `jsonBody` over `body`"))
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
            val mock = adapter.createGeneratedMock(mapping, namespace, SourceType.SPEC_WITH_DESCRIPTION, "ref", 1)

            // Then
            assertTrue(mock.id.contains("test-client-petstore"))
            assertTrue(mock.id.startsWith("ai-generated-test-client-petstore-post--test-client-petstore-pet-1"))
            assertTrue(mock.metadata.endpoint.method == HttpMethod.POST)
            assertTrue(mock.metadata.endpoint.path == "/test-client/petstore/pet")
        }

        @Test
        fun `Given invalid mocks When building correction prompt Then should include errors and mappings`() {
            // Given
            val namespace = MockNamespace(apiName = "petstore", client = "test-client")
            val invalidMock = GeneratedMock(
                id = "mock-1",
                name = "Mock 1",
                namespace = namespace,
                wireMockMapping = "{\"request\":{\"url\":\"/old\"}}",
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/old", 200, "application/json"))
            )
            val errors = listOf("Url should be /new", "Body missing")
            val invalidMocks = listOf(invalidMock to errors)

            // When
            val prompt = adapter.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then
            assertTrue(prompt.contains("Mock ID: mock-1"))
            assertTrue(prompt.contains("{\"request\":{\"url\":\"/old\"}}"))
            assertTrue(prompt.contains("Url should be /new"))
            assertTrue(prompt.contains("Body missing"))
            assertTrue(prompt.contains("JSON array"))
        }
    }
}
