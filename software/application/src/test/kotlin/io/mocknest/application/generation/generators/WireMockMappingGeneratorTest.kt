package io.mocknest.application.generation.generators

import io.mocknest.application.generation.interfaces.TestDataGeneratorInterface
import org.springframework.http.HttpMethod
import io.mocknest.domain.generation.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireMockMappingGeneratorTest {
    
    private val mockTestDataGenerator: TestDataGeneratorInterface = mockk(relaxed = true)
    private val generator = WireMockMappingGenerator(mockTestDataGenerator)
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `Given simple GET endpoint When generating mock Then should create valid WireMock mapping`() = runTest {
        // Given
        val namespace = MockNamespace(apiName = "test-api")
        val endpoint = EndpointDefinition(
            path = "/users",
            method = HttpMethod.GET,
            operationId = "getUsers",
            summary = "Get all users",
            parameters = emptyList(),
            requestBody = null,
            responses = mapOf(
                200 to ResponseDefinition(
                    statusCode = 200,
                    description = "Success",
                    schema = JsonSchema(
                        type = JsonSchemaType.ARRAY,
                        items = JsonSchema(JsonSchemaType.OBJECT)
                    )
                )
            )
        )
        
        coEvery { mockTestDataGenerator.generateForSchema(any()) } returns listOf(
            mapOf("id" to 1, "name" to "John Doe")
        )
        
        // When
        val result = generator.generateFromEndpoint(endpoint, namespace)
        
        // Then
        assertEquals("getUsers-200", result.id)
        assertEquals("Get all users", result.name)
        assertEquals(namespace, result.namespace)
        assertTrue(result.wireMockMapping.contains("\"method\":\"GET\""))
        assertTrue(result.wireMockMapping.contains("\"url\":\"/users\""))
        assertTrue(result.wireMockMapping.contains("\"status\":200"))
        
        coVerify { mockTestDataGenerator.generateForSchema(any()) }
    }
    
    @Test
    fun `Given POST endpoint with request body When generating mock Then should include body matching`() = runTest {
        // Given
        val namespace = MockNamespace(apiName = "test-api")
        val endpoint = EndpointDefinition(
            path = "/users",
            method = HttpMethod.POST,
            operationId = "createUser",
            summary = "Create user",
            parameters = emptyList(),
            requestBody = RequestBodyDefinition(
                required = true,
                content = mapOf(
                    "application/json" to MediaTypeDefinition(
                        schema = JsonSchema(JsonSchemaType.OBJECT)
                    )
                )
            ),
            responses = mapOf(
                201 to ResponseDefinition(
                    statusCode = 201,
                    description = "Created",
                    schema = JsonSchema(JsonSchemaType.OBJECT)
                )
            )
        )
        
        coEvery { mockTestDataGenerator.generateForSchema(any()) } returns mapOf(
            "id" to 1, "name" to "John Doe"
        )
        
        // When
        val result = generator.generateFromEndpoint(endpoint, namespace)
        
        // Then
        assertEquals("createUser-201", result.id)
        assertTrue(result.wireMockMapping.contains("\"method\":\"POST\""))
        assertTrue(result.wireMockMapping.contains("\"status\":201"))
        assertTrue(result.wireMockMapping.contains("bodyPatterns"))
        assertTrue(result.wireMockMapping.contains("Content-Type"))
        
        coVerify { mockTestDataGenerator.generateForSchema(any()) }
    }
    
    @Test
    fun `Given endpoint with path parameters When generating mock Then should convert to WireMock pattern`() = runTest {
        // Given
        val namespace = MockNamespace(apiName = "test-api")
        val endpoint = EndpointDefinition(
            path = "/users/{id}",
            method = HttpMethod.GET,
            operationId = "getUser",
            summary = "Get user by ID",
            parameters = listOf(
                ParameterDefinition(
                    name = "id",
                    location = ParameterLocation.PATH,
                    required = true,
                    schema = JsonSchema(JsonSchemaType.INTEGER)
                )
            ),
            requestBody = null,
            responses = mapOf(
                200 to ResponseDefinition(
                    statusCode = 200,
                    description = "Success",
                    schema = JsonSchema(JsonSchemaType.OBJECT)
                )
            )
        )
        
        coEvery { mockTestDataGenerator.generateForSchema(any()) } returns mapOf(
            "id" to 1, "name" to "John Doe"
        )
        
        // When
        val result = generator.generateFromEndpoint(endpoint, namespace)
        
        // Then
        assertTrue(result.wireMockMapping.contains("urlPattern"))
        assertTrue(result.wireMockMapping.contains("/users/([^/]+)"))
        
        coVerify { mockTestDataGenerator.generateForSchema(any()) }
    }
    
    @Test
    fun `Given specification with multiple endpoints When generating mocks Then should create mock for each endpoint`() = runTest {
        // Given
        val namespace = MockNamespace(apiName = "test-api")
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
                    responses = mapOf(200 to ResponseDefinition(200, "Success", null))
                ),
                EndpointDefinition(
                    path = "/users",
                    method = HttpMethod.POST,
                    operationId = "createUser",
                    summary = "Create user",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(201 to ResponseDefinition(201, "Created", null))
                )
            ),
            schemas = emptyMap()
        )
        
        coEvery { mockTestDataGenerator.generateForSchema(any()) } returns mapOf("result" to "success")
        
        // When
        val result = generator.generateFromSpecification(specification, namespace)
        
        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "getUsers-200" })
        assertTrue(result.any { it.id == "createUser-201" })
        
        coVerify(exactly = 2) { mockTestDataGenerator.generateForSchema(any()) }
    }
    
    @Test
    fun `Given valid WireMock mapping When validating Then should return valid result`() = runTest {
        // Given
        val validMapping = """
        {
          "request": {
            "method": "GET",
            "url": "/test"
          },
          "response": {
            "status": 200,
            "body": "test"
          }
        }
        """.trimIndent()
        
        // When
        val result = generator.validateWireMockMapping(validMapping)
        
        // Then
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `Given invalid WireMock mapping When validating Then should return invalid result with errors`() = runTest {
        // Given
        val invalidMapping = """
        {
          "request": {
            "method": "GET"
          }
        }
        """.trimIndent()
        
        // When
        val result = generator.validateWireMockMapping(invalidMapping)
        
        // Then
        assertTrue(!result.isValid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("Missing required field") })
    }
}