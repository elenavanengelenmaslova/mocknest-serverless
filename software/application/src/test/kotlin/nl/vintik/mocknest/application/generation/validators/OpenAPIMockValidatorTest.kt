package nl.vintik.mocknest.application.generation.validators

import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
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
                sourceType = SourceType.SPECIFICATION,
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
                      "id": "user-123",
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
        fun `Given mock with undefined status code When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = loadTestData("invalid-status-code-mock.json")
            val mock = createGeneratedMock("invalid-status-code-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Status code 404 not defined in specification") },
                "Should report undefined status code 404"
            )
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
    }
}
