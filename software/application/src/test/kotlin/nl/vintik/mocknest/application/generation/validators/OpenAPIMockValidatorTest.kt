package nl.vintik.mocknest.application.generation.validators

import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for OpenAPIMockValidator.
 * 
 * Tests cover:
 * - Valid mock validation
 * - Invalid request matchers (method, path, query parameters)
 * - Invalid status codes
 * - Schema mismatches (type errors, nested structures)
 * - Missing required fields
 */
class OpenAPIMockValidatorTest {

    private val validator = OpenAPIMockValidator()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/test-data/validators/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    private fun createTestSpecification(): APISpecification {
        return APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "3.0.0",
            title = "Test API",
            endpoints = listOf(
                // GET /api/users/{id} - Returns user details
                EndpointDefinition(
                    path = "/api/users/123",
                    method = HttpMethod.GET,
                    operationId = "getUser",
                    summary = "Get user by ID",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(
                        200 to ResponseDefinition(
                            statusCode = 200,
                            description = "Successful response",
                            schema = JsonSchema(
                                type = JsonSchemaType.OBJECT,
                                properties = mapOf(
                                    "id" to JsonSchema(type = JsonSchemaType.STRING),
                                    "name" to JsonSchema(type = JsonSchemaType.STRING),
                                    "email" to JsonSchema(type = JsonSchemaType.STRING),
                                    "active" to JsonSchema(type = JsonSchemaType.BOOLEAN)
                                ),
                                required = listOf("id", "name", "email", "active")
                            )
                        )
                    )
                ),
                // POST /api/orders - Create order with nested structure
                EndpointDefinition(
                    path = "/api/orders",
                    method = HttpMethod.POST,
                    operationId = "createOrder",
                    summary = "Create new order",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(
                        201 to ResponseDefinition(
                            statusCode = 201,
                            description = "Order created",
                            schema = JsonSchema(
                                type = JsonSchemaType.OBJECT,
                                properties = mapOf(
                                    "orderId" to JsonSchema(type = JsonSchemaType.STRING),
                                    "customer" to JsonSchema(
                                        type = JsonSchemaType.OBJECT,
                                        properties = mapOf(
                                            "id" to JsonSchema(type = JsonSchemaType.STRING),
                                            "name" to JsonSchema(type = JsonSchemaType.STRING)
                                        ),
                                        required = listOf("id", "name")
                                    ),
                                    "items" to JsonSchema(
                                        type = JsonSchemaType.ARRAY,
                                        items = JsonSchema(
                                            type = JsonSchemaType.OBJECT,
                                            properties = mapOf(
                                                "productId" to JsonSchema(type = JsonSchemaType.STRING),
                                                "quantity" to JsonSchema(type = JsonSchemaType.INTEGER)
                                            ),
                                            required = listOf("productId", "quantity")
                                        )
                                    )
                                ),
                                required = listOf("orderId", "customer", "items")
                            )
                        )
                    )
                )
            ),
            schemas = emptyMap()
        )
    }

    private fun createGeneratedMock(
        id: String,
        wireMockMapping: String
    ): GeneratedMock {
        return GeneratedMock(
            id = id,
            name = "Test Mock",
            namespace = MockNamespace("test"),
            wireMockMapping = wireMockMapping,
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test-spec.yaml",
                endpoint = EndpointInfo(
                    method = HttpMethod.GET,
                    path = "/api/users/123",
                    statusCode = 200,
                    contentType = "application/json"
                )
            ),
            generatedAt = Instant.now()
        )
    }

    @Nested
    inner class ValidMockValidation {

        @Test
        fun `Given valid mock When validating Then should return valid result`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("valid-mock.json")
            val mock = createGeneratedMock("valid-mock-1", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock should be valid")
            assertTrue(result.errors.isEmpty(), "Should have no errors")
        }

        @Test
        fun `Given mock with all required fields When validating Then should pass`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "Jane Smith",
                      "email": "jane@example.com",
                      "active": false
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("valid-mock-2", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid)
            assertEquals(0, result.errors.size)
        }
    }

    @Nested
    inner class URLMatcherValidation {

        @Test
        fun `Given mock with urlPathPattern When validating Then should correctly match endpoint`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/{petId}",
                        method = HttpMethod.GET,
                        operationId = "getPet",
                        summary = "Get pet",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPathPattern": "/test/pet/[0-9]+"
                  },
                  "response": {
                    "status": 200
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "regex-mock",
                name = "Regex Mock",
                namespace = MockNamespace("test"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/pet/123", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should match /pet/[0-9]+ to /pet/{petId}. Errors: ${result.errors}")
        }

        @Test
        fun `Given mock with urlPattern When validating Then should correctly match endpoint`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPattern": "/test/api/users/.*"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "Jane",
                      "email": "jane@example.com",
                      "active": true
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("urlpattern-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should match urlPattern: ${result.errors}")
        }
        
        @Test
        fun `Given mock with urlPath When validating Then should correctly match endpoint`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/test/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "Jane",
                      "email": "jane@example.com",
                      "active": true
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("urlpath-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should match urlPath: ${result.errors}")
        }

        @Test
        fun `Given mock with urlPathPattern that matches multiple spec endpoints When validating Then should favor most specific match`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Petstore",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/{petId}",
                        method = HttpMethod.GET,
                        operationId = "getPetById",
                        summary = "Get pet by ID",
                        parameters = listOf(
                            ParameterDefinition("petId", ParameterLocation.PATH, true, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Pet object",
                                schema = JsonSchema(type = JsonSchemaType.OBJECT, properties = mapOf("id" to JsonSchema(JsonSchemaType.INTEGER)))
                            )
                        )
                    ),
                    EndpointDefinition(
                        path = "/pet/findByStatus",
                        method = HttpMethod.GET,
                        operationId = "findPetsByStatus",
                        summary = "Find pets by status",
                        parameters = listOf(
                            ParameterDefinition("status", ParameterLocation.QUERY, true, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Array of pets",
                                schema = JsonSchema(type = JsonSchemaType.ARRAY, items = JsonSchema(type = JsonSchemaType.OBJECT))
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            
            // Mock specifically for findByStatus (returns array)
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/petstore/pet/findByStatus",
                    "queryParameters": {
                      "status": { "equalTo": "available" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      { "id": 1, "name": "Buddy", "status": "available" }
                    ]
                  }
                }
            """.trimIndent()
            
            val mock = GeneratedMock(
                id = "find-by-status-mock",
                name = "Find By Status",
                namespace = MockNamespace("petstore"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/pet/findByStatus", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should match findByStatus and be valid. Errors: ${result.errors}")
        }
    }

    @Nested
    inner class InvalidRequestMatchers {

        @Test
        fun `Given mock with invalid HTTP method When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("invalid-method-mock.json")
            val mock = createGeneratedMock("invalid-method-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid, "Mock should be invalid")
            assertTrue(
                result.errors.any { it.contains("No matching endpoint found") },
                "Should report no matching endpoint for DELETE /api/users/123"
            )
        }

        @Test
        fun `Given mock with invalid URL path When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("invalid-path-mock.json")
            val mock = createGeneratedMock("invalid-path-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("No matching endpoint found") },
                "Should report no matching endpoint for nonexistent path"
            )
        }

        @Test
        fun `Given mock with undefined query parameters When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("invalid-query-params-mock.json")
            val mock = createGeneratedMock("invalid-query-params-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Query parameter 'undefinedParam' not defined") },
                "Should report undefined query parameter"
            )
        }

        @Test
        fun `Given mock with missing request section When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "response": {
                    "status": 200,
                    "jsonBody": {}
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-request-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing request section") },
                "Should report missing request section"
            )
        }

        @Test
        fun `Given mock with missing method When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {}
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-method-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing method") },
                "Should report missing method"
            )
        }

        @Test
        fun `Given mock with missing URL path When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {}
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-url-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing URL path") },
                "Should report missing URL path"
            )
        }
    }

    @Nested
    inner class InvalidStatusCodes {

        @Test
        fun `Given mock with undefined success status code When validating Then should return error`() = runTest {
            // Given — use status 201 which is not defined for GET /api/users/123 (only 200 is)
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": { "method": "GET", "urlPath": "/api/users/123" },
                  "response": { "status": 201, "jsonBody": { "id": "123", "name": "Test", "email": "t@t.com", "active": true } }
                }
            """.trimIndent()
            val mock = createGeneratedMock("invalid-success-status-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then — undefined 2xx status codes are rejected
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Status code 201 not defined in specification") },
                "Should report undefined success status code 201"
            )
        }

        @Test
        fun `Given mock with undefined error status code When validating Then should accept it`() = runTest {
            // Given — 4xx/5xx error codes are accepted even when not in the spec
            val specification = createTestSpecification()
            val mockJson = loadTestData("invalid-status-code-mock.json")
            val mock = createGeneratedMock("error-status-code-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then — error status codes (4xx/5xx) are accepted without validation
            assertTrue(result.isValid, "Error status codes should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given mock with missing status code When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "jsonBody": {
                      "id": "123"
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-status-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing status code") },
                "Should report missing status code"
            )
        }

        @Test
        fun `Given mock with missing response section When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-response-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing response section") },
                "Should report missing response section"
            )
        }
    }

    @Nested
    inner class SchemaMismatches {

        @Test
        fun `Given mock with wrong field type When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("schema-mismatch-type-mock.json")
            val mock = createGeneratedMock("schema-mismatch-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Expected string but got") && it.contains(".id") },
                "Should report type mismatch for id field (expected string, got number)"
            )
            assertTrue(
                result.errors.any { it.contains("Expected boolean but got") && it.contains(".active") },
                "Should report type mismatch for active field (expected boolean, got string)"
            )
        }

        @Test
        fun `Given mock with nested schema mismatch When validating Then should return errors`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("nested-schema-mismatch-mock.json")
            val mock = createGeneratedMock("nested-mismatch-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains(".customer.name") && it.contains("Expected string") },
                "Should report type mismatch in nested customer.name field"
            )
            assertTrue(
                result.errors.any { it.contains(".items[0].quantity") && it.contains("Expected number") },
                "Should report type mismatch in array item quantity field"
            )
        }

        @Test
        fun `Given mock with wrong response body type When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": "This should be an object, not a string"
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("wrong-body-type-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Expected object but got") },
                "Should report type mismatch for response body"
            )
        }

        @Test
        fun `Given mock with array instead of object When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      {"id": "123"}
                    ]
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("array-instead-object-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Expected object but got JsonArray") },
                "Should report array when object expected"
            )
        }
    }

    @Nested
    inner class MissingRequiredFields {

        @Test
        fun `Given mock missing required field When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("missing-required-field-mock.json")
            val mock = createGeneratedMock("missing-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing required property 'email'") },
                "Should report missing required field 'email'"
            )
            assertTrue(
                result.errors.any { it.contains("Missing required property 'active'") },
                "Should report missing required field 'active'"
            )
        }

        @Test
        fun `Given mock missing multiple required fields When validating Then should return all errors`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123"
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("multiple-missing-fields-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertEquals(3, result.errors.size, "Should report 3 missing required fields")
            assertTrue(result.errors.any { it.contains("Missing required property 'name'") })
            assertTrue(result.errors.any { it.contains("Missing required property 'email'") })
            assertTrue(result.errors.any { it.contains("Missing required property 'active'") })
        }

        @Test
        fun `Given mock missing required nested field When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/api/orders"
                  },
                  "response": {
                    "status": 201,
                    "jsonBody": {
                      "orderId": "order-123",
                      "customer": {
                        "id": "cust-456"
                      },
                      "items": []
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-nested-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains(".customer") && it.contains("Missing required property 'name'") },
                "Should report missing required nested field 'customer.name'"
            )
        }
    }

    @Nested
    inner class ConsistencyValidation {

        @Test
        fun `Given query param status=available but response has sold pet When validating Then should return consistency error`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/api/users/123",
                        method = HttpMethod.GET,
                        operationId = "getUser",
                        summary = "Get user by ID",
                        parameters = listOf(
                            ParameterDefinition("active", ParameterLocation.QUERY, true, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Successful response",
                                schema = JsonSchema(
                                    type = JsonSchemaType.OBJECT,
                                    properties = mapOf(
                                        "id" to JsonSchema(type = JsonSchemaType.STRING),
                                        "name" to JsonSchema(type = JsonSchemaType.STRING),
                                        "email" to JsonSchema(type = JsonSchemaType.STRING),
                                        "active" to JsonSchema(type = JsonSchemaType.BOOLEAN)
                                    ),
                                    required = listOf("id", "name", "email", "active")
                                )
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123",
                    "queryParameters": {
                      "active": { "equalTo": "true" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "Jane Smith",
                      "email": "jane@example.com",
                      "active": false
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("consistency-error-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertFalse(result.isFatal, "Consistency errors should not be fatal")
            assertTrue(
                result.errors.any { it.contains("Consistency error") && it.contains("active") && it.contains("true") && it.contains("false") },
                "Should report consistency error for active status mismatch"
            )
        }

        @Test
        fun `Given path parameter in spec and matching mock path When validating Then should check consistency`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/{petId}",
                        method = HttpMethod.GET,
                        operationId = "getPet",
                        summary = "Get pet",
                        parameters = listOf(
                            ParameterDefinition("petId", ParameterLocation.PATH, true, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = JsonSchema(type = JsonSchemaType.OBJECT)
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/pet/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "456",
                      "name": "Doggie"
                    }
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "path-param-mock",
                name = "Path Param Mock",
                namespace = MockNamespace("test"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/pet/123", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertFalse(result.isFatal, "Path consistency errors should not be fatal")
            assertTrue(
                result.errors.any { it.contains("Consistency error") && it.contains("petId") && it.contains("123") && it.contains("456") },
                "Should report consistency error for path parameter mismatch"
            )
        }
        
        @Test
        fun `Given array response with inconsistent items When validating Then should report all consistency errors`() = runTest {
            // Given
             val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/api/users",
                        method = HttpMethod.GET,
                        operationId = "getUsers",
                        summary = "Get users",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = JsonSchema(type = JsonSchemaType.ARRAY, items = JsonSchema(type = JsonSchemaType.OBJECT))
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users",
                    "queryParameters": {
                      "status": { "equalTo": "available" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      { "id": "1", "status": "available" },
                      { "id": "2", "status": "sold" }
                    ]
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "array-consistency-mock",
                name = "Array Consistency Mock",
                namespace = MockNamespace("test"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/api/users", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Consistency error in item [1]") && it.contains("status") && it.contains("available") && it.contains("sold") },
                "Should report consistency error for the second item in array"
            )
        }

        @Test
        fun `Given query parameter matches an array in response When validating Then should not crash and verify containment`() = runTest {
            // Given
             val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/findByTags",
                        method = HttpMethod.GET,
                        operationId = "findByTags",
                        summary = "Find by tags",
                        parameters = listOf(
                            ParameterDefinition("tags", ParameterLocation.QUERY, false, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = JsonSchema(type = JsonSchemaType.ARRAY, items = JsonSchema(type = JsonSchemaType.OBJECT))
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/pet/findByTags",
                    "queryParameters": {
                      "tags": { "equalTo": "tag1" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      { "id": "1", "tags": ["tag1", "tag2"] },
                      { "id": "2", "tags": ["tag2", "tag3"] }
                    ]
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "tags-consistency-mock",
                name = "Tags Consistency Mock",
                namespace = MockNamespace("test"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/pet/findByTags", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            // Item 0 is valid because "tag1" is in ["tag1", "tag2"]
            // Item 1 is invalid because "tag1" is NOT in ["tag2", "tag3"]
            assertTrue(
                result.errors.any { it.contains("Consistency error in item [1]") && it.contains("tags") && it.contains("tag1") },
                "Should report consistency error for the second item (tag1 not found in array)"
            )
            assertFalse(
                result.errors.any { it.contains("item [0]") },
                "First item should be considered valid as it contains the tag"
            )
        }

        @Test
        fun `Given query parameter matches an array of objects in response When validating Then should check recursively`() = runTest {
            // Given
             val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/findByTags",
                        method = HttpMethod.GET,
                        operationId = "findByTags",
                        summary = "Find by tags",
                        parameters = listOf(
                            ParameterDefinition("tags", ParameterLocation.QUERY, false, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = JsonSchema(type = JsonSchemaType.ARRAY, items = JsonSchema(type = JsonSchemaType.OBJECT))
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            // Complex tags: array of objects with 'name' field
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/pet/findByTags",
                    "queryParameters": {
                      "tags": { "equalTo": "new" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      { 
                        "id": "1", 
                        "tags": [
                          { "id": 1, "name": "new" }
                        ] 
                      }
                    ]
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "complex-tags-consistency-mock",
                name = "Complex Tags Consistency Mock",
                namespace = MockNamespace("test"),
                wireMockMapping = mockJson,
                metadata = MockMetadata(SourceType.SPEC_WITH_DESCRIPTION, "ref", EndpointInfo(HttpMethod.GET, "/pet/findByTags", 200, "application/json")),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should match 'new' inside tags array objects. Errors: ${result.errors}")
        }

        @Test
        fun `Given action path like findByStatus When validating Then should NOT extract findByStatus as ID`() = runTest {
            // Given
             val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/pet/findByStatus",
                        method = HttpMethod.GET,
                        operationId = "findByStatus",
                        summary = "Find by status",
                        parameters = listOf(
                            ParameterDefinition("status", ParameterLocation.QUERY, false, JsonSchema(JsonSchemaType.STRING))
                        ),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = JsonSchema(type = JsonSchemaType.ARRAY, items = JsonSchema(type = JsonSchemaType.OBJECT))
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/pet/findByStatus",
                    "queryParameters": {
                      "status": { "equalTo": "available" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": [
                      { "id": "1", "status": "available" }
                    ]
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("findByStatus-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should be valid and NOT report consistency error for 'findByStatus' as ID: ${result.errors}")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `Given mock with malformed JSON When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123"
                      "name": "Missing comma"
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("malformed-json-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Validation error") },
                "Should report validation error for malformed JSON"
            )
        }

        @Test
        fun `Given mock with empty response body When schema is optional Then should pass`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/api/users/123",
                        method = HttpMethod.GET,
                        operationId = "getUser",
                        summary = "Get user",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "Success",
                                schema = null // No schema defined
                            )
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("no-body-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock without body should be valid when schema is optional")
        }

        @Test
        fun `Given mock with url instead of urlPath When validating Then should accept it`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "url": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "John Doe",
                      "email": "john@example.com",
                      "active": true
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("url-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock with 'url' field should be valid")
        }

        @Test
        fun `Given mock with prefixed URL When validating Then should normalize and accept it`() = runTest {
            // Given
            val specification = createTestSpecification()
            val namespace = MockNamespace(apiName = "petstore", client = "test-client")
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "url": "/test-client/petstore/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "id": "123",
                      "name": "John Doe",
                      "email": "john@example.com",
                      "active": true
                    }
                  }
                }
            """.trimIndent()
            val mock = GeneratedMock(
                id = "prefixed-mock",
                name = "Prefixed Mock",
                namespace = namespace,
                wireMockMapping = mockJson,
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "test-spec.yaml",
                    endpoint = EndpointInfo(
                        method = HttpMethod.GET,
                        path = "/test-client/petstore/api/users/123",
                        statusCode = 200,
                        contentType = "application/json"
                    )
                ),
                generatedAt = Instant.now()
            )

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock with prefixed URL should be valid after normalization: ${result.errors}")
        }

        @Test
        fun `Given mock with body instead of jsonBody When validating Then should validate it`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users/123"
                  },
                  "response": {
                    "status": 200,
                    "body": {
                      "id": 123,
                      "name": "John Doe",
                      "email": "john@example.com",
                      "active": true
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("body-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Expected string") && it.contains(".id") },
                "Should validate 'body' field same as 'jsonBody'"
            )
        }

        @Test
        fun `Should handle malformed JSON in preProcessMapping`() = runTest {
            val specification = createTestSpecification()
            val mock = createGeneratedMock("malformed", "{not-json}")
            val result = validator.validate(mock, specification)
            assertTrue(!result.isValid)
            assertTrue(result.errors.any { it.contains("Malformed JSON") })
        }

        @Test
        fun `Should handle empty request or response`() = runTest {
            val specification = createTestSpecification()
            val mock = createGeneratedMock("empty", "{}")
            val result = validator.validate(mock, specification)
            assertTrue(!result.isValid)
            assertTrue(result.errors.any { it.contains("Missing request section") })
        }

        @Test
        fun `Should handle missing method or url`() = runTest {
            val specification = createTestSpecification()
            val mock = createGeneratedMock("no-method", "{\"request\":{},\"response\":{\"status\":200}}")
            val result = validator.validate(mock, specification)
            assertTrue(!result.isValid)
            assertTrue(result.errors.any { it.contains("Missing method") })
            assertTrue(result.errors.any { it.contains("Missing URL path") })
        }

        @Test
        fun `Should handle missing status code`() = runTest {
            val specification = createTestSpecification()
            val mock = createGeneratedMock("no-status", "{\"request\":{\"method\":\"GET\",\"url\":\"/pet/1\"},\"response\":{}}")
            val result = validator.validate(mock, specification)
            assertTrue(!result.isValid)
            assertTrue(result.errors.any { it.contains("Missing status code") })
        }

        @Test
        fun `Should validate BOOLEAN and NUMBER types`() = runTest {
            val specification = createTestSpecification()
            
            // Invalid types for existing pet endpoint
            val mockJson = """
                {"request":{"method":"GET","url":"/api/users/123"},
                 "response":{"status":200,"jsonBody":{"active":"not-boolean","id":"123","name":"John","email":"a@b.com"}}}
            """.trimIndent()
            val result = validator.validate(createGeneratedMock("types", mockJson), specification)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("Expected boolean") })
        }
    }
}
