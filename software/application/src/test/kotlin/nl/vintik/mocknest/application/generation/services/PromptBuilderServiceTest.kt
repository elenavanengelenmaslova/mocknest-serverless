package nl.vintik.mocknest.application.generation.services

import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant

class PromptBuilderServiceTest {

    private val promptBuilder = PromptBuilderService()

    @AfterEach
    fun tearDown() {
        // No mocks to clear in this test
    }

    @Nested
    inner class SystemPromptLoading {

        @Test
        fun `Given system prompt template exists When loading system prompt Then should return prompt text`() {
            val systemPrompt = promptBuilder.loadSystemPrompt()

            assertNotNull(systemPrompt)
            assertTrue(systemPrompt.contains("You are an expert API mock generator"))
            assertTrue(systemPrompt.contains("WireMock JSON mappings"))
        }

        @Test
        fun `Given system prompt template When loading Then should not contain placeholders`() {
            val systemPrompt = promptBuilder.loadSystemPrompt()

            assertFalse(systemPrompt.contains("{{"))
            assertFalse(systemPrompt.contains("}}"))
        }
    }

    @Nested
    inner class SpecWithDescriptionPromptBuilding {

        @Test
        fun `Given valid specification and description When building prompt Then should inject all parameters`() {
            val specification = createTestSpecification()
            val namespace = MockNamespace("petstore", null)
            val description = "Add error cases for invalid pet IDs"

            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            assertTrue(prompt.contains("Petstore API"))
            assertTrue(prompt.contains("1.0.0"))
            assertTrue(prompt.contains("3"))
            assertTrue(prompt.contains("GET /pets (Returns: STRING)"))
            assertTrue(prompt.contains("petstore"))
            assertTrue(prompt.contains("Add error cases for invalid pet IDs"))
        }

        @Test
        fun `Given namespace with client When building prompt Then should include client section`() {
            val specification = createTestSpecification()
            val namespace = MockNamespace("petstore", "mobile-app")
            val description = "Test description"

            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            assertTrue(prompt.contains("- Client: mobile-app"))
        }

        @Test
        fun `Given namespace without client When building prompt Then should omit client section`() {
            val specification = createTestSpecification()
            val namespace = MockNamespace("petstore", null)
            val description = "Test description"

            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            assertFalse(prompt.contains("- Client:"))
        }

        @Test
        fun `Given specification with many endpoints When building prompt Then should include all endpoints`() {
            val stringSchema = JsonSchema(type = JsonSchemaType.STRING)
            val successResponse = ResponseDefinition(
                statusCode = 200,
                description = "Success",
                schema = stringSchema
            )
            
            val endpoints = (1..10).map { i ->
                EndpointDefinition(
                    method = HttpMethod.GET,
                    path = "/endpoint$i",
                    operationId = "endpoint$i",
                    summary = "Endpoint $i",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to successResponse)
                )
            }
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                title = "Large API",
                version = "1.0.0",
                schemas = emptyMap(),
                endpoints = endpoints
            )
            val namespace = MockNamespace("largeapi", null)
            val description = "Test"

            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            assertTrue(prompt.contains("GET /endpoint1 (Returns: STRING)"))
            assertTrue(prompt.contains("GET /endpoint5 (Returns: STRING)"))
            assertTrue(prompt.contains("GET /endpoint10 (Returns: STRING)"))
        }

        @Test
        fun `Given prompt template When building Then should not contain unreplaced placeholders`() {
            val specification = createTestSpecification()
            val namespace = MockNamespace("petstore", null)
            val description = "Test"

            val prompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            assertFalse(prompt.contains("{{SPEC_TITLE}}"))
            assertFalse(prompt.contains("{{SPEC_VERSION}}"))
            assertFalse(prompt.contains("{{ENDPOINT_COUNT}}"))
            assertFalse(prompt.contains("{{KEY_ENDPOINTS}}"))
            assertFalse(prompt.contains("{{API_NAME}}"))
            assertFalse(prompt.contains("{{DESCRIPTION}}"))
            assertFalse(prompt.contains("{{NAMESPACE}}"))
            assertFalse(prompt.contains("{{WIREMOCK_SCHEMA}}"))
        }
    }

    @Nested
    inner class CorrectionPromptBuilding {

        @Test
        fun `Given invalid mocks with specification When building correction prompt Then should include spec context`() {
            val specification = createTestSpecification()
            val namespace = MockNamespace("petstore", null)
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Invalid URL pattern")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, specification)

            assertTrue(prompt.contains("API Specification Context"))
            assertTrue(prompt.contains("Petstore API"))
            assertTrue(prompt.contains("1.0.0"))
            assertTrue(prompt.contains("3"))
        }

        @Test
        fun `Given invalid mocks without specification When building correction prompt Then should omit spec context`() {
            val namespace = MockNamespace("petstore", null)
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Invalid URL pattern")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            assertFalse(prompt.contains("API Specification Context"))
        }

        @Test
        fun `Given namespace with client When building correction prompt Then should include client section`() {
            val namespace = MockNamespace("petstore", "mobile-app")
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Invalid URL pattern")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            assertTrue(prompt.contains("- Client: mobile-app"))
        }

        @Test
        fun `Given namespace without client When building correction prompt Then should omit client section`() {
            val namespace = MockNamespace("petstore", null)
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Invalid URL pattern")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            assertFalse(prompt.contains("- Client:"))
        }

        @Test
        fun `Given multiple invalid mocks When building correction prompt Then should include all mocks with errors`() {
            val namespace = MockNamespace("petstore", null)
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Error 1", "Error 2"),
                createTestMock("mock-2", namespace) to listOf("Error 3")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            assertTrue(prompt.contains("Mock ID: mock-1"))
            assertTrue(prompt.contains("Error 1"))
            assertTrue(prompt.contains("Error 2"))
            assertTrue(prompt.contains("Mock ID: mock-2"))
            assertTrue(prompt.contains("Error 3"))
        }

        @Test
        fun `Given correction prompt template When building Then should not contain unreplaced placeholders`() {
            val namespace = MockNamespace("petstore", null)
            val invalidMocks = listOf(
                createTestMock("mock-1", namespace) to listOf("Invalid URL pattern")
            )

            val prompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            assertFalse(prompt.contains("{{SPEC_CONTEXT}}"))
            assertFalse(prompt.contains("{{API_NAME}}"))
            assertFalse(prompt.contains("{{MOCKS_WITH_ERRORS}}"))
            assertFalse(prompt.contains("{{NAMESPACE}}"))
        }
    }

    @Nested
    inner class TemplateLoadingErrors {

        @Test
        fun `Given non-existent template When loading Then should throw IllegalStateException`() {
            val service = PromptBuilderService()
            // Use reflection to call private method for testing
            val method = service.javaClass.getDeclaredMethod("loadTemplate", String::class.java)
            method.isAccessible = true
            
            val exception = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                method.invoke(service, "/prompts/non-existent.txt")
            }

            val cause = exception.cause
            assertNotNull(cause)
            assertTrue(cause is IllegalStateException)
            assertTrue(cause?.message?.contains("Template not found") == true)
        }
    }

    // Helper methods

    private fun createTestSpecification(): APISpecification {
        val stringSchema = JsonSchema(type = JsonSchemaType.STRING)
        val successResponse = ResponseDefinition(
            statusCode = 200,
            description = "Success",
            schema = stringSchema
        )
        
        return APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            title = "Petstore API",
            version = "1.0.0",
            schemas = emptyMap(),
            endpoints = listOf(
                EndpointDefinition(
                    method = HttpMethod.GET,
                    path = "/pets",
                    operationId = "listPets",
                    summary = "List all pets",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to successResponse)
                ),
                EndpointDefinition(
                    method = HttpMethod.GET,
                    path = "/pets/{petId}",
                    operationId = "getPet",
                    summary = "Get pet by ID",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to successResponse)
                ),
                EndpointDefinition(
                    method = HttpMethod.POST,
                    path = "/pets",
                    operationId = "createPet",
                    summary = "Create a pet",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to successResponse)
                )
            )
        )
    }

    private fun createTestMock(id: String, namespace: MockNamespace): GeneratedMock {
        return GeneratedMock(
            id = id,
            name = "Test Mock",
            namespace = namespace,
            wireMockMapping = """{"request": {"method": "GET", "url": "/test"}, "response": {"status": 200}}""",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "Test",
                endpoint = EndpointInfo(
                    method = HttpMethod.GET,
                    path = "/test",
                    statusCode = 200,
                    contentType = "application/json"
                ),
                tags = emptySet()
            ),
            generatedAt = Instant.now()
        )
    }
}
