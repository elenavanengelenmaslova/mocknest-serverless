package nl.vintik.mocknest.application.generation.validators

import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based test for comprehensive mock validation.
 *
 * Property 10: Comprehensive Mock Validation
 * Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 *
 * For each test data file, verifies that:
 * - Valid mocks pass validation with no errors
 * - Invalid mocks fail validation with the expected error messages
 * - All validation rules (operation existence, argument types, required fields,
 *   scalar types, enum values, list/object structures) are checked correctly
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-10")
class GraphQLMockValidatorPropertyTest {

    private val validator = GraphQLMockValidator()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    companion object {
        private val testDataFiles = listOf(
            "01-valid-query-mock.json",
            "02-valid-mutation-mock.json",
            "03-valid-enum-mock.json",
            "04-valid-errors-response-mock.json",
            "05-invalid-operation-not-found.json",
            "06-invalid-argument-type-mismatch.json",
            "07-invalid-missing-required-fields.json",
            "08-invalid-enum-value.json",
            "09-invalid-wrong-response-format.json",
            "10-invalid-type-incompatibility.json",
            "11-invalid-list-structure-mismatch.json",
            "12-invalid-missing-required-argument.json",
            "13-valid-list-response-mock.json",
            "14-invalid-unexpected-argument.json",
            "15-valid-both-data-and-errors.json"
        )

        @JvmStatic
        fun mockValidationTestCases(): List<MockValidationTestCase> {
            return testDataFiles.map { filename ->
                val content = object {}::class.java.getResource("/graphql/mocks/$filename")?.readText()
                    ?: throw IllegalArgumentException("Test data file not found: $filename")
                val json = Json.parseToJsonElement(content).jsonObject
                MockValidationTestCase(
                    name = filename.removeSuffix(".json"),
                    filename = filename,
                    rawJson = json
                )
            }
        }
    }

    data class MockValidationTestCase(
        val name: String,
        val filename: String,
        val rawJson: JsonObject
    ) {
        override fun toString(): String = name
    }

    // -------------------------------------------------------------------------
    // Test data parsing helpers
    // -------------------------------------------------------------------------

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
        return JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = properties,
            required = required
        )
    }

    private fun buildResponseDataSchema(fieldsJson: JsonObject, required: List<String>): JsonSchema {
        val properties = fieldsJson.entries.associate { (name, typeElement) ->
            name to parseFieldSchema(typeElement)
        }
        return JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = properties,
            required = required
        )
    }

    private fun parseFieldSchema(element: JsonElement): JsonSchema {
        return when {
            element is JsonPrimitive -> {
                val typeName = element.content
                when (typeName) {
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
            name = "Property Test Mock - $mockId",
            namespace = MockNamespace("property-test"),
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

    // -------------------------------------------------------------------------
    // Property 10: Comprehensive Mock Validation
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "Property 10 - Validation outcome: {0}")
    @MethodSource("mockValidationTestCases")
    fun `Property 10 - Given mock test case When validating Then should produce expected validation outcome`(
        testCase: MockValidationTestCase
    ) = runTest {
        // Given
        val testJson = testCase.rawJson
        val expectedValid = testJson["expectedValid"]!!.jsonPrimitive.boolean
        val expectedErrors = testJson["expectedErrors"]!!.jsonArray.map { it.jsonPrimitive.content }

        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, testCase.name)

        // When
        val result = validator.validate(mock, specification)

        // Then - verify correct validation outcome
        if (expectedValid) {
            assertTrue(
                result.isValid,
                "Mock '${testCase.name}' should be valid but got errors: ${result.errors}"
            )
            assertTrue(
                result.errors.isEmpty(),
                "Mock '${testCase.name}' should have no errors but got: ${result.errors}"
            )
        } else {
            assertFalse(
                result.isValid,
                "Mock '${testCase.name}' should be invalid"
            )
            assertTrue(
                result.errors.isNotEmpty(),
                "Mock '${testCase.name}' should have at least one error"
            )
        }

        // Verify each expected error message is present
        expectedErrors.forEach { expectedError ->
            assertTrue(
                result.errors.any { it.contains(expectedError) },
                "Mock '${testCase.name}' should contain error '$expectedError'. Actual errors: ${result.errors}"
            )
        }
    }

    @ParameterizedTest(name = "Property 10 - Valid mocks have no errors: {0}")
    @MethodSource("mockValidationTestCases")
    fun `Property 10 - Given valid mock When validating Then errors list should be empty`(
        testCase: MockValidationTestCase
    ) = runTest {
        // Given
        val testJson = testCase.rawJson
        val expectedValid = testJson["expectedValid"]!!.jsonPrimitive.boolean
        if (!expectedValid) return@runTest // Only test valid cases in this property

        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, testCase.name)

        // When
        val result = validator.validate(mock, specification)

        // Then - valid mocks must have empty error list
        assertEquals(
            emptyList<String>(),
            result.errors,
            "Valid mock '${testCase.name}' must have empty error list"
        )
    }

    @ParameterizedTest(name = "Property 10 - Invalid mocks have errors: {0}")
    @MethodSource("mockValidationTestCases")
    fun `Property 10 - Given invalid mock When validating Then errors list should be non-empty`(
        testCase: MockValidationTestCase
    ) = runTest {
        // Given
        val testJson = testCase.rawJson
        val expectedValid = testJson["expectedValid"]!!.jsonPrimitive.boolean
        if (expectedValid) return@runTest // Only test invalid cases in this property

        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, testCase.name)

        // When
        val result = validator.validate(mock, specification)

        // Then - invalid mocks must have non-empty error list
        assertTrue(
            result.errors.isNotEmpty(),
            "Invalid mock '${testCase.name}' must have at least one error"
        )
        assertFalse(
            result.isValid,
            "Invalid mock '${testCase.name}' must not be marked as valid"
        )
    }

    @ParameterizedTest(name = "Property 10 - Validation result consistency: {0}")
    @MethodSource("mockValidationTestCases")
    fun `Property 10 - Given any mock When validating Then isValid and errors should be consistent`(
        testCase: MockValidationTestCase
    ) = runTest {
        // Given
        val testJson = testCase.rawJson
        val specification = buildSpecification(testJson)
        val mock = buildGeneratedMock(testJson, testCase.name)

        // When
        val result = validator.validate(mock, specification)

        // Then - isValid and errors must be consistent (invariant)
        if (result.isValid) {
            assertTrue(
                result.errors.isEmpty(),
                "If isValid=true, errors must be empty for '${testCase.name}'"
            )
        } else {
            assertTrue(
                result.errors.isNotEmpty(),
                "If isValid=false, errors must be non-empty for '${testCase.name}'"
            )
        }
    }
}
