package nl.vintik.mocknest.infra.aws.generation.ai

import io.mockk.clearAllMocks
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant

/**
 * Property 2: Preservation - Prompt Content Equivalence
 * 
 * These tests verify that prompt content remains identical after moving
 * prompt building logic from infrastructure layer to application layer.
 * 
 * METHODOLOGY: Observation-first approach
 * 1. Tests were run on UNFIXED code to observe actual behavior
 * 2. Tests verify key structural elements are present
 * 3. After refactoring, verify prompts contain same elements
 * 
 * IMPORTANT: These tests should PASS both before and after refactoring,
 * confirming that prompt content is preserved.
 */
class PromptContentPreservationTest {

    private val promptBuilder = PromptBuilderService()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class SystemPromptPreservation {

        @Test
        fun `Given agent creation When loading system prompt Then should document exact expected content`() {
            // Given - system prompt is loaded from template

            // When
            val systemPrompt = promptBuilder.loadSystemPrompt()

            // Then - Document the exact system prompt from current implementation
            // This is the baseline that must be preserved after refactoring:
            // "You are an expert API mock generator.
            //  You generate WireMock JSON mappings based on user instructions and specifications."
            
            // Verify system prompt contains expected content
            assertTrue(systemPrompt.contains("You are an expert API mock generator"))
            assertTrue(systemPrompt.contains("WireMock JSON mappings"))
        }
    }

    @Nested
    inner class SpecWithDescriptionPromptPreservation {

        @Test
        fun `Given spec with namespace without client When building prompt Then should contain all required elements`() {
            // Given
            val specification = createTestSpecification()
            val description = "Add realistic error responses"
            val namespace = MockNamespace(apiName = "petstore")

            // When
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then - Verify all key structural elements are present
            assertTrue(prompt.contains("You are an expert API mock generator"))
            assertTrue(prompt.contains("API Specification Summary:"))
            assertTrue(prompt.contains("- Title: Pet Store API"))
            assertTrue(prompt.contains("- Version: 1.0.0"))
            assertTrue(prompt.contains("- Endpoints: 2"))
            assertTrue(prompt.contains("GET /pets (Returns: OBJECT)"))
            assertTrue(prompt.contains("POST /pets (Returns: OBJECT)"))
            assertTrue(prompt.contains("Namespace:"))
            assertTrue(prompt.contains("- API Name: petstore"))
            assertTrue(!prompt.contains("- Client:"), "Should not contain client line when client is null")
            assertTrue(prompt.contains("Enhancement Description: Add realistic error responses"))
            assertTrue(prompt.contains("IMPORTANT: All mock URLs must be prefixed with /petstore"))
            assertTrue(prompt.contains("Prefer `jsonBody` over `body`"))
            assertTrue(prompt.contains("Return only a JSON array"))
        }

        @Test
        fun `Given spec with namespace with client When building prompt Then should include client`() {
            // Given
            val specification = createTestSpecification()
            val description = "Add authentication scenarios"
            val namespace = MockNamespace(apiName = "petstore", client = "acme-corp")

            // When
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then - Verify client is included
            assertTrue(prompt.contains("- API Name: petstore"))
            assertTrue(prompt.contains("- Client: acme-corp"))
            assertTrue(prompt.contains("/acme-corp/petstore"))
        }

        @Test
        fun `Given spec with many endpoints When building prompt Then should show all endpoints`() {
            // Given
            val specification = createSpecificationWithManyEndpoints()
            val description = "Test"
            val namespace = MockNamespace(apiName = "api")

            // When
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then - Verify all endpoints shown
            assertTrue(prompt.contains("- Endpoints: 10"))
            assertTrue(prompt.contains("GET /endpoint1"))
            assertTrue(prompt.contains("POST /endpoint2"))
            assertTrue(prompt.contains("PUT /endpoint3"))
            assertTrue(prompt.contains("DELETE /endpoint4"))
            assertTrue(prompt.contains("PATCH /endpoint5"))
            assertTrue(prompt.contains("GET /endpoint6"))
            assertTrue(prompt.contains("PATCH /endpoint10"))
        }
    }

    @Nested
    inner class CorrectionPromptPreservation {

        @Test
        fun `Given invalid mocks without specification When building correction prompt Then should contain all required elements`() {
            // Given
            val namespace = MockNamespace(apiName = "petstore", client = "test-client")
            val invalidMock = GeneratedMock(
                id = "mock-1",
                name = "Get Pet",
                namespace = namespace,
                wireMockMapping = """{"request":{"method":"GET","url":"/pet/123"},"response":{"status":200}}""",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "test",
                    endpoint = EndpointInfo(HttpMethod.GET, "/pet/123", 200, "application/json")
                ),
                generatedAt = Instant.now()
            )
            val errors = listOf("URL should be prefixed", "Missing Content-Type")
            val invalidMocks = listOf(invalidMock to errors)

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then - Verify all key elements present
            assertTrue(prompt.contains("You are an expert API mock generator"))
            assertTrue(prompt.contains("failed validation"))
            assertTrue(!prompt.contains("API Specification Context:"), "Should not have spec context when spec is null")
            assertTrue(prompt.contains("Namespace:"))
            assertTrue(prompt.contains("- API Name: petstore"))
            assertTrue(prompt.contains("- Client: test-client"))
            assertTrue(prompt.contains("Mock ID: mock-1"))
            assertTrue(prompt.contains("Current Mapping:"))
            assertTrue(prompt.contains("""{"request":{"method":"GET","url":"/pet/123"}"""))
            assertTrue(prompt.contains("Validation Errors:"))
            assertTrue(prompt.contains("URL should be prefixed"))
            assertTrue(prompt.contains("Missing Content-Type"))
            assertTrue(prompt.contains("/test-client/petstore"))
            assertTrue(prompt.contains("Return only a JSON array"))
        }

        @Test
        fun `Given invalid mocks with specification When building correction prompt Then should include spec context`() {
            // Given
            val specification = createTestSpecification()
            val namespace = MockNamespace(apiName = "petstore")
            val invalidMock = createTestInvalidMock(namespace)
            val invalidMocks = listOf(invalidMock to listOf("Error"))

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, specification)

            // Then - Verify spec context is included
            assertTrue(prompt.contains("API Specification Context:"))
            assertTrue(prompt.contains("- Title: Pet Store API"))
            assertTrue(prompt.contains("- Version: 1.0.0"))
            assertTrue(prompt.contains("- Endpoints: 2"))
        }

        @Test
        fun `Given multiple invalid mocks When building correction prompt Then should include all with separator`() {
            // Given
            val namespace = MockNamespace(apiName = "api")
            val mock1 = createTestInvalidMock(namespace, "mock-1")
            val mock2 = createTestInvalidMock(namespace, "mock-2")
            val invalidMocks = listOf(
                mock1 to listOf("Error 1"),
                mock2 to listOf("Error 2")
            )

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then - Verify both mocks present with separator
            assertTrue(prompt.contains("Mock ID: mock-1"))
            assertTrue(prompt.contains("Mock ID: mock-2"))
            assertTrue(prompt.contains("---"), "Should have separator between mocks")
        }
    }

    @Nested
    inner class OptionalParameterHandling {

        @Test
        fun `Given namespace without client When building spec prompt Then should omit client line`() {
            // Given
            val specification = createTestSpecification()
            val namespace = MockNamespace(apiName = "api")

            // When
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, "test", namespace)

            // Then
            assertTrue(prompt.contains("- API Name: api"))
            assertTrue(!prompt.contains("- Client:"))
        }

        @Test
        fun `Given namespace with client When building spec prompt Then should include client line`() {
            // Given
            val specification = createTestSpecification()
            val namespace = MockNamespace(apiName = "api", client = "client1")

            // When
            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, "test", namespace)

            // Then
            assertTrue(prompt.contains("- API Name: api"))
            assertTrue(prompt.contains("- Client: client1"))
        }

        @Test
        fun `Given namespace without client When building correction prompt Then should omit client line`() {
            // Given
            val namespace = MockNamespace(apiName = "api")
            val invalidMock = createTestInvalidMock(namespace)
            val invalidMocks = listOf(invalidMock to listOf("Error"))

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then
            assertTrue(prompt.contains("- API Name: api"))
            assertTrue(!prompt.contains("- Client:"))
        }

        @Test
        fun `Given namespace with client When building correction prompt Then should include client line`() {
            // Given
            val namespace = MockNamespace(apiName = "api", client = "client1")
            val invalidMock = createTestInvalidMock(namespace)
            val invalidMocks = listOf(invalidMock to listOf("Error"))

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then
            assertTrue(prompt.contains("- API Name: api"))
            assertTrue(prompt.contains("- Client: client1"))
        }

        @Test
        fun `Given null specification When building correction prompt Then should omit spec context`() {
            // Given
            val namespace = MockNamespace(apiName = "api")
            val invalidMock = createTestInvalidMock(namespace)
            val invalidMocks = listOf(invalidMock to listOf("Error"))

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then
            assertTrue(!prompt.contains("API Specification Context:"))
        }

        @Test
        fun `Given specification When building correction prompt Then should include spec context`() {
            // Given
            val specification = createTestSpecification()
            val namespace = MockNamespace(apiName = "api")
            val invalidMock = createTestInvalidMock(namespace)
            val invalidMocks = listOf(invalidMock to listOf("Error"))

            // When
            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, specification)

            // Then
            assertTrue(prompt.contains("API Specification Context:"))
            assertTrue(prompt.contains("- Title: Pet Store API"))
        }
    }

    // Test data helpers
    private fun createTestSpecification(): APISpecification {
        return APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0.0",
            title = "Pet Store API",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/pets",
                    method = HttpMethod.GET,
                    operationId = "getPets",
                    summary = "List all pets",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                ),
                EndpointDefinition(
                    path = "/pets",
                    method = HttpMethod.POST,
                    operationId = "createPet",
                    summary = "Create a pet",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(201 to ResponseDefinition(201, "Created", null))
                )
            ),
            schemas = emptyMap()
        )
    }

    private fun createSpecificationWithManyEndpoints(): APISpecification {
        val methods = listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)
        val endpoints = (1..10).map { i ->
            EndpointDefinition(
                path = "/endpoint$i",
                method = methods[(i - 1) % methods.size],
                operationId = "operation$i",
                summary = "Operation $i",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(200 to ResponseDefinition(200, "OK", null))
            )
        }
        return APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "2.0.0",
            title = "Large API",
            endpoints = endpoints,
            schemas = emptyMap()
        )
    }

    private fun createTestInvalidMock(namespace: MockNamespace, id: String = "test-mock"): GeneratedMock {
        return GeneratedMock(
            id = id,
            name = "Test Mock",
            namespace = namespace,
            wireMockMapping = """{"request":{"url":"/test"}}""",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test",
                endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
            ),
            generatedAt = Instant.now()
        )
    }
}
