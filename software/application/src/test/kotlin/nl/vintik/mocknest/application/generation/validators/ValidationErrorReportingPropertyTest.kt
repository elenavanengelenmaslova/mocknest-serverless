package nl.vintik.mocknest.application.generation.validators

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based test for validation error reporting.
 *
 * **Validates: Requirements 5.8**
 *
 * Property 11: Validation Error Reporting
 * For any invalid GraphQL mock, the validator must return a non-empty error list
 * with contextual error messages that identify the specific validation failure.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-11")
class ValidationErrorReportingPropertyTest {

    private val validator = GraphQLMockValidator()

    private fun loadMock(filename: String): JsonObject {
        val content = this::class.java.getResource("/graphql/mocks/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
        return Json.parseToJsonElement(content).jsonObject
    }

    private fun buildSpecification(testJson: JsonObject): APISpecification {
        val specJson = testJson["specification"]!!.jsonObject
        val operations = specJson["operations"]!!.jsonArray

        val endpoints = operations.map { opElement ->
            val op = opElement.jsonObject
            val operationId = op["operationId"]!!.jsonPrimitive.content
            val variablesJson = op["variables"]?.jsonObject ?: JsonObject(emptyMap())
            val requiredVars = op["requiredVariables"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val responseFieldsJson = op["responseFields"]?.jsonObject ?: JsonObject(emptyMap())
            val requiredResponseFields = op["requiredResponseFields"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            EndpointDefinition(
                path = "/graphql",
                method = HttpMethod.POST,
                operationId = operationId,
                summary = operationId,
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
                                    "variables" to buildVariablesSchema(variablesJson, requiredVars)
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
                                "data" to buildResponseDataSchema(responseFieldsJson, requiredResponseFields)
                            )
                        )
                    )
                )
            )
        }

        return APISpecification(
            format = SpecificationFormat.GRAPHQL,
            version = "1.0.0",
            title = "Test GraphQL API",
            endpoints = endpoints,
            schemas = emptyMap()
        )
    }

    private fun buildVariablesSchema(variablesJson: JsonObject, required: List<String>): JsonSchema {
        val properties = variablesJson.entries.associate { (name, typeElement) ->
            name to parseFieldSchema(typeElement)
        }
        return JsonSchema(type = JsonSchemaType.OBJECT, properties = properties, required = required)
    }

    private fun buildResponseDataSchema(fieldsJson: JsonObject, required: List<String>): JsonSchema {
        val properties = fieldsJson.entries.associate { (name, typeElement) ->
            name to parseFieldSchema(typeElement)
        }
        return JsonSchema(type = JsonSchemaType.OBJECT, properties = properties, required = required)
    }

    private fun parseFieldSchema(element: JsonElement): JsonSchema {
        return when {
            element is JsonPrimitive -> {
                when (element.content) {
                    "STRING" -> JsonSchema(type = JsonSchemaType.STRING)
                    "INTEGER" -> JsonSchema(type = JsonSchemaType.INTEGER)
                    "NUMBER" -> JsonSchema(type = JsonSchemaType.NUMBER)
                    "BOOLEAN" -> JsonSchema(type = JsonSchemaType.BOOLEAN)
                    else -> JsonSchema(type = JsonSchemaType.STRING)
                }
            }
            element is JsonObject -> {
                val typeName = element["type"]?.jsonPrimitive?.content ?: "STRING"
                when (typeName) {
                    "STRING" -> {
                        val enumValues = element["enum"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                        JsonSchema(type = JsonSchemaType.STRING, enum = enumValues)
                    }
                    "ARRAY" -> {
                        val itemsJson = element["items"]?.jsonObject ?: JsonObject(emptyMap())
                        val itemProperties = itemsJson.entries.associate { (name, typeEl) ->
                            name to parseFieldSchema(typeEl)
                        }
                        JsonSchema(
                            type = JsonSchemaType.ARRAY,
                            items = JsonSchema(type = JsonSchemaType.OBJECT, properties = itemProperties)
                        )
                    }
                    "INTEGER" -> JsonSchema(type = JsonSchemaType.INTEGER)
                    "NUMBER" -> JsonSchema(type = JsonSchemaType.NUMBER)
                    "BOOLEAN" -> JsonSchema(type = JsonSchemaType.BOOLEAN)
                    else -> JsonSchema(type = JsonSchemaType.STRING)
                }
            }
            else -> JsonSchema(type = JsonSchemaType.STRING)
        }
    }

    private fun buildGeneratedMock(testJson: JsonObject, mockId: String): GeneratedMock {
        val wireMockMappingJson = testJson["wireMockMapping"]!!.jsonObject
        return GeneratedMock(
            id = mockId,
            name = "Validation Error Test - $mockId",
            namespace = MockNamespace("error-reporting-test"),
            wireMockMapping = wireMockMappingJson.toString(),
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test-schema.graphql",
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

    // ─────────────────────────────────────────────────────────────────────────
    // Property 11: Validation Error Reporting
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 11 - Given invalid mock {0} When validating Then error list is non-empty")
    @ValueSource(strings = [
        "05-invalid-operation-not-found.json",
        "06-invalid-argument-type-mismatch.json",
        "07-invalid-missing-required-fields.json",
        "08-invalid-enum-value.json",
        "09-invalid-wrong-response-format.json",
        "10-invalid-type-incompatibility.json",
        "11-invalid-list-structure-mismatch.json",
        "12-invalid-missing-required-argument.json",
        "14-invalid-unexpected-argument.json"
    ])
    fun `Property 11 - Given invalid mock When validating Then returns non-empty error list`(
        filename: String
    ) = runTest {
        // Given
        val testJson = loadMock(filename)
        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, filename.removeSuffix(".json"))

        // When
        val result = validator.validate(mock, specification)

        // Then
        assertFalse(result.isValid, "[$filename] Invalid mock should not pass validation")
        assertTrue(
            result.errors.isNotEmpty(),
            "[$filename] Validator must return non-empty error list for invalid mocks"
        )
    }

    @ParameterizedTest(name = "Property 11 - Given invalid mock {0} When validating Then errors contain expected messages")
    @ValueSource(strings = [
        "05-invalid-operation-not-found.json",
        "06-invalid-argument-type-mismatch.json",
        "07-invalid-missing-required-fields.json",
        "08-invalid-enum-value.json",
        "09-invalid-wrong-response-format.json",
        "10-invalid-type-incompatibility.json",
        "11-invalid-list-structure-mismatch.json",
        "12-invalid-missing-required-argument.json",
        "14-invalid-unexpected-argument.json"
    ])
    fun `Property 11 - Given invalid mock When validating Then errors contain expected context messages`(
        filename: String
    ) = runTest {
        // Given
        val testJson = loadMock(filename)
        val expectedErrors = testJson["expectedErrors"]!!.jsonArray.map { it.jsonPrimitive.content }
        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, filename.removeSuffix(".json"))

        // When
        val result = validator.validate(mock, specification)

        // Then
        expectedErrors.forEach { expectedError ->
            assertTrue(
                result.errors.any { it.contains(expectedError) },
                "[$filename] Validator errors should contain '$expectedError'. Actual errors: ${result.errors}"
            )
        }
    }

    @ParameterizedTest(name = "Property 11 - Given invalid mock {0} When validating Then errors are string messages")
    @ValueSource(strings = [
        "05-invalid-operation-not-found.json",
        "06-invalid-argument-type-mismatch.json",
        "07-invalid-missing-required-fields.json",
        "08-invalid-enum-value.json",
        "09-invalid-wrong-response-format.json",
        "10-invalid-type-incompatibility.json",
        "11-invalid-list-structure-mismatch.json",
        "12-invalid-missing-required-argument.json",
        "14-invalid-unexpected-argument.json"
    ])
    fun `Property 11 - Given invalid mock When validating Then all errors are non-blank strings`(
        filename: String
    ) = runTest {
        // Given
        val testJson = loadMock(filename)
        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, filename.removeSuffix(".json"))

        // When
        val result = validator.validate(mock, specification)

        // Then
        result.errors.forEach { error ->
            assertTrue(
                error.isNotBlank(),
                "[$filename] Each validation error must be a non-blank string"
            )
        }
    }
}
