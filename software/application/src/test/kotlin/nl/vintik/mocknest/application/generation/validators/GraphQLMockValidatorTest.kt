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
 * Comprehensive tests for GraphQLMockValidator.
 * 
 * Tests cover:
 * - Valid mock validation
 * - Missing operations
 * - Argument type mismatches
 * - Missing required fields
 * - Invalid enum values
 * - Type incompatibilities
 * - Error message formatting with context
 */
class GraphQLMockValidatorTest {

    private val validator = GraphQLMockValidator()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createTestSpecification(): APISpecification {
        return APISpecification(
            format = SpecificationFormat.GRAPHQL,
            version = "1.0.0",
            title = "Test GraphQL API",
            endpoints = listOf(
                // Query: getUser(id: String!): User
                EndpointDefinition(
                    path = "/graphql",
                    method = HttpMethod.POST,
                    operationId = "getUser",
                    summary = "Get user by ID",
                    parameters = emptyList(),
                    requestBody = RequestBodyDefinition(
                        required = true,
                        content = mapOf(
                            "application/json" to MediaTypeDefinition(
                                schema = JsonSchema(
                                    type = JsonSchemaType.OBJECT,
                                    properties = mapOf(
                                        "query" to JsonSchema(type = JsonSchemaType.STRING),
                                        "operationName" to JsonSchema(type = JsonSchemaType.STRING),
                                        "variables" to JsonSchema(
                                            type = JsonSchemaType.OBJECT,
                                            properties = mapOf(
                                                "id" to JsonSchema(type = JsonSchemaType.STRING)
                                            ),
                                            required = listOf("id")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    responses = mapOf(
                        200 to ResponseDefinition(
                            statusCode = 200,
                            description = "Successful response",
                            schema = JsonSchema(
                                type = JsonSchemaType.OBJECT,
                                properties = mapOf(
                                    "data" to JsonSchema(
                                        type = JsonSchemaType.OBJECT,
                                        properties = mapOf(
                                            "id" to JsonSchema(type = JsonSchemaType.STRING),
                                            "name" to JsonSchema(type = JsonSchemaType.STRING),
                                            "email" to JsonSchema(type = JsonSchemaType.STRING),
                                            "status" to JsonSchema(
                                                type = JsonSchemaType.STRING,
                                                enum = listOf("ACTIVE", "INACTIVE", "PENDING")
                                            )
                                        ),
                                        required = listOf("id", "name", "email", "status")
                                    )
                                )
                            )
                        )
                    )
                ),
                // Mutation: createUser(name: String!, email: String!, age: Int): User
                EndpointDefinition(
                    path = "/graphql",
                    method = HttpMethod.POST,
                    operationId = "createUser",
                    summary = "Create new user",
                    parameters = emptyList(),
                    requestBody = RequestBodyDefinition(
                        required = true,
                        content = mapOf(
                            "application/json" to MediaTypeDefinition(
                                schema = JsonSchema(
                                    type = JsonSchemaType.OBJECT,
                                    properties = mapOf(
                                        "query" to JsonSchema(type = JsonSchemaType.STRING),
                                        "operationName" to JsonSchema(type = JsonSchemaType.STRING),
                                        "variables" to JsonSchema(
                                            type = JsonSchemaType.OBJECT,
                                            properties = mapOf(
                                                "name" to JsonSchema(type = JsonSchemaType.STRING),
                                                "email" to JsonSchema(type = JsonSchemaType.STRING),
                                                "age" to JsonSchema(type = JsonSchemaType.INTEGER)
                                            ),
                                            required = listOf("name", "email")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    responses = mapOf(
                        200 to ResponseDefinition(
                            statusCode = 200,
                            description = "User created",
                            schema = JsonSchema(
                                type = JsonSchemaType.OBJECT,
                                properties = mapOf(
                                    "data" to JsonSchema(
                                        type = JsonSchemaType.OBJECT,
                                        properties = mapOf(
                                            "id" to JsonSchema(type = JsonSchemaType.STRING),
                                            "name" to JsonSchema(type = JsonSchemaType.STRING),
                                            "email" to JsonSchema(type = JsonSchemaType.STRING)
                                        ),
                                        required = listOf("id", "name", "email")
                                    )
                                )
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
            name = "Test GraphQL Mock",
            namespace = MockNamespace("test"),
            wireMockMapping = wireMockMapping,
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test-graphql-schema.graphql",
                endpoint = EndpointInfo(
                    method = HttpMethod.POST,
                    path = "/graphql",
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
        fun `Given valid GraphQL mock When validating Then should return valid result`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("valid-graphql-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock should be valid. Errors: ${result.errors}")
            assertTrue(result.errors.isEmpty(), "Should have no errors")
        }

        @Test
        fun `Given valid mutation mock When validating Then should pass`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"mutation createUser(${'$'}name: String!, ${'$'}email: String!, ${'$'}age: Int) { createUser(name: ${'$'}name, email: ${'$'}email, age: ${'$'}age) { id name email } }\",\"operationName\":\"createUser\",\"variables\":{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"age\":25}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "456",
                        "name": "Jane",
                        "email": "jane@example.com"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("valid-mutation-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mutation mock should be valid. Errors: ${result.errors}")
            assertEquals(0, result.errors.size)
        }

        @Test
        fun `Given mock with GraphQL errors field When validating Then should pass`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"invalid\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "errors": [
                        {
                          "message": "User not found",
                          "path": ["user"]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("error-response-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock with errors field should be valid. Errors: ${result.errors}")
        }
    }

    @Nested
    inner class MissingOperations {

        @Test
        fun `Given mock with non-existent operation When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query nonExistentOperation { data }\",\"operationName\":\"nonExistentOperation\",\"variables\":{}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {}
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-operation-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid, "Mock should be invalid")
            assertTrue(
                result.errors.any { it.contains("Operation 'nonExistentOperation' not found in schema") },
                "Should report missing operation. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with operation name extracted from query When validating Then should detect missing operation`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query unknownQuery { data }\",\"variables\":{}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {}
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("extracted-operation-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Operation 'unknownQuery' not found in schema") },
                "Should detect missing operation from query string"
            )
        }
    }

    @Nested
    inner class ArgumentTypeMismatches {

        @Test
        fun `Given mock with wrong argument type When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":123}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("wrong-arg-type-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Argument 'id' must be a string") },
                "Should report argument type mismatch. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with missing required argument When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"mutation createUser(${'$'}name: String!, ${'$'}email: String!) { createUser(name: ${'$'}name, email: ${'$'}email) { id name email } }\",\"operationName\":\"createUser\",\"variables\":{\"name\":\"Jane\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "456",
                        "name": "Jane",
                        "email": "jane@example.com"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-arg-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing required argument: 'email'") },
                "Should report missing required argument"
            )
        }

        @Test
        fun `Given mock with unexpected argument When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!, ${'$'}extra: String) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\",\"extra\":\"unexpected\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("unexpected-arg-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Unexpected argument: 'extra' not defined in schema") },
                "Should report unexpected argument"
            )
        }

        @Test
        fun `Given mock with integer argument type mismatch When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"mutation createUser(${'$'}name: String!, ${'$'}email: String!, ${'$'}age: Int) { createUser(name: ${'$'}name, email: ${'$'}email, age: ${'$'}age) { id name email } }\",\"operationName\":\"createUser\",\"variables\":{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"age\":\"not-an-integer\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "456",
                        "name": "Jane",
                        "email": "jane@example.com"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("int-type-mismatch-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Argument 'age' must be an integer") },
                "Should report integer type mismatch"
            )
        }
    }

    @Nested
    inner class MissingRequiredFields {

        @Test
        fun `Given mock missing required response field When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("missing-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Missing required field: 'data.email'") },
                "Should report missing required field 'email'. Actual errors: ${result.errors}"
            )
            assertTrue(
                result.errors.any { it.contains("Missing required field: 'data.status'") },
                "Should report missing required field 'status'. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock missing multiple required fields When validating Then should return all errors`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123"
                      }
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
            assertTrue(result.errors.any { it.contains("Missing required field: 'data.name'") })
            assertTrue(result.errors.any { it.contains("Missing required field: 'data.email'") })
            assertTrue(result.errors.any { it.contains("Missing required field: 'data.status'") })
        }
    }

    @Nested
    inner class InvalidEnumValues {

        @Test
        fun `Given mock with invalid enum value When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "INVALID_STATUS"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("invalid-enum-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { 
                    it.contains("Field 'data.status' has invalid enum value: 'INVALID_STATUS'") &&
                    it.contains("Valid values: ACTIVE, INACTIVE, PENDING")
                },
                "Should report invalid enum value with valid options. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with valid enum value When validating Then should pass`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "PENDING"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("valid-enum-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock with valid enum should pass. Errors: ${result.errors}")
        }
    }

    @Nested
    inner class TypeIncompatibilities {

        @Test
        fun `Given mock with wrong field type When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": 123,
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("wrong-type-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Field 'data.id' must be a string") },
                "Should report type incompatibility. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with object instead of string When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": {"first": "John", "last": "Doe"},
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("object-instead-string-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Field 'data.name' must be a string") },
                "Should report object when string expected"
            )
        }

        @Test
        fun `Given mock with array instead of object When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": [
                        {"id": "123"}
                      ]
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("array-instead-object-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Field 'data' must be an object") },
                "Should report array when object expected"
            )
        }
    }

    @Nested
    inner class ErrorMessageFormatting {

        @Test
        fun `Given validation errors When formatting Then should include context`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":123}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": 123,
                        "name": "John Doe",
                        "status": "INVALID_STATUS"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("context-error-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            
            // Check that errors include field paths for context
            assertTrue(
                result.errors.any { it.contains("Argument 'id'") },
                "Argument errors should include argument name"
            )
            assertTrue(
                result.errors.any { it.contains("Field 'data.id'") },
                "Field errors should include full field path"
            )
            assertTrue(
                result.errors.any { it.contains("Field 'data.status'") && it.contains("INVALID_STATUS") },
                "Enum errors should include field path and invalid value"
            )
            assertTrue(
                result.errors.any { it.contains("Missing required field: 'data.email'") },
                "Missing field errors should include full field path"
            )
        }

        @Test
        fun `Given multiple validation errors When validating Then should return all errors with context`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"mutation createUser(${'$'}name: String!, ${'$'}email: String!) { createUser(name: ${'$'}name, email: ${'$'}email) { id name email } }\",\"operationName\":\"createUser\",\"variables\":{\"name\":123}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": 456
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("multiple-errors-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.size >= 3, "Should have multiple errors")
            
            // Verify each error has proper context
            assertTrue(result.errors.any { it.contains("Argument 'name' must be a string") })
            assertTrue(result.errors.any { it.contains("Missing required argument: 'email'") })
            assertTrue(result.errors.any { it.contains("Missing required field: 'data.name'") })
            assertTrue(result.errors.any { it.contains("Missing required field: 'data.email'") })
            assertTrue(result.errors.any { it.contains("Field 'data.id' must be a string") })
        }
    }

    @Nested
    inner class GraphQLResponseFormat {

        @Test
        fun `Given mock without data or errors field When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "result": {
                        "id": "123"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("invalid-format-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Response must contain 'data' or 'errors' field (GraphQL response format)") },
                "Should report invalid GraphQL response format. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with errors field as non-array When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"invalid\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "errors": "This should be an array"
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("errors-not-array-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Response 'errors' field must be an array") },
                "Should report errors field must be array"
            )
        }

        @Test
        fun `Given mock with both data and errors When validating Then should pass`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      },
                      "errors": [
                        {
                          "message": "Deprecated field used",
                          "extensions": {
                            "code": "DEPRECATED"
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("data-and-errors-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Mock with both data and errors should be valid. Errors: ${result.errors}")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `Given non-GraphQL specification When validating Then should return valid`() = runTest {
            // Given
            val specification = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "REST API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/api/users",
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
            val mockJson = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/api/users"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": []
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("rest-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Non-GraphQL specs should be skipped")
        }

        @Test
        fun `Given mock with malformed JSON When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123"
                        "name": "Missing comma"
                      }
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
        fun `Given mock without body patterns When validating Then should return error`() = runTest {
            // Given
            val specification = createTestSpecification()
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql"
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {}
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("no-body-patterns-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Failed to extract GraphQL operation") },
                "Should report failure to extract operation"
            )
        }

        @Test
        fun `Given mock with matchesJsonPath instead of equalToJson When validating Then should fail to extract operation`() = runTest {
            // Given
            val specification = createTestSpecification()
            // matchesJsonPath contains JSONPath expressions, not a full JSON body,
            // so the validator cannot extract the GraphQL operation from it
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "matchesJsonPath": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "jsonBody": {
                      "data": {
                        "id": "123",
                        "name": "John Doe",
                        "email": "john@example.com",
                        "status": "ACTIVE"
                      }
                    }
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("matches-json-path-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            // matchesJsonPath is not supported for GraphQL operation extraction
            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("Failed to extract GraphQL operation") },
                "Should report failure to extract operation from matchesJsonPath. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with body field instead of jsonBody When validating Then should validate it`() = runTest {
            // Given
            val specification = createTestSpecification()
            // The 'body' field in WireMock is a raw string; the validator parses it as JSON
            val mockJson = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/graphql",
                    "bodyPatterns": [
                      {
                        "equalToJson": "{\"query\":\"query getUser(${'$'}id: String!) { user(id: ${'$'}id) { id name email status } }\",\"operationName\":\"getUser\",\"variables\":{\"id\":\"123\"}}"
                      }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "body": "{\"data\":{\"id\":\"123\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"status\":\"ACTIVE\"}}"
                  }
                }
            """.trimIndent()
            val mock = createGeneratedMock("body-field-mock", mockJson)

            // When
            val result = validator.validate(mock, specification)

            // Then
            assertTrue(result.isValid, "Should handle body field. Errors: ${result.errors}")
        }
    }
}
