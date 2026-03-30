package nl.vintik.mocknest.application.generation.graphql

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based test for GraphQL response format compliance.
 *
 * **Validates: Requirements 4.4**
 *
 * Property 9: GraphQL Response Format Compliance
 * For any generated GraphQL mock, the response body must comply with the
 * GraphQL specification by containing a "data" field and/or an "errors" field.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-9")
class GraphQLResponseFormatCompliancePropertyTest {

    private fun loadMock(filename: String): JsonObject {
        val content = this::class.java.getResource("/graphql/mocks/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
        return Json.parseToJsonElement(content).jsonObject
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking response Then jsonBody contains data or errors")
    @ValueSource(strings = [
        "01-valid-query-mock.json",
        "02-valid-mutation-mock.json",
        "03-valid-enum-mock.json",
        "04-valid-errors-response-mock.json",
        "13-valid-list-response-mock.json",
        "15-valid-both-data-and-errors.json",
        "05-invalid-operation-not-found.json",
        "06-invalid-argument-type-mismatch.json",
        "07-invalid-missing-required-fields.json",
        "08-invalid-enum-value.json",
        "10-invalid-type-incompatibility.json",
        "11-invalid-list-structure-mismatch.json",
        "12-invalid-missing-required-argument.json",
        "14-invalid-unexpected-argument.json"
    ])
    fun `Property 9 - Given GraphQL mock When checking response Then jsonBody contains data or errors field`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val response = mockJson["wireMockMapping"]!!.jsonObject["response"]!!.jsonObject
        val jsonBody = response["jsonBody"]!!.jsonObject
        val hasData = jsonBody.containsKey("data")
        val hasErrors = jsonBody.containsKey("errors")

        // Then
        assertTrue(
            hasData || hasErrors,
            "[$filename] GraphQL response jsonBody must contain 'data' and/or 'errors' field per GraphQL spec"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given valid mock {0} When checking response Then data field is a JSON object")
    @ValueSource(strings = [
        "01-valid-query-mock.json",
        "02-valid-mutation-mock.json",
        "03-valid-enum-mock.json",
        "13-valid-list-response-mock.json"
    ])
    fun `Property 9 - Given valid mock with data When checking response Then data field is a JSON object`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val response = mockJson["wireMockMapping"]!!.jsonObject["response"]!!.jsonObject
        val jsonBody = response["jsonBody"]!!.jsonObject
        val dataField = jsonBody["data"]

        // Then
        assertTrue(
            dataField is JsonObject,
            "[$filename] GraphQL response 'data' field should be a JSON object"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given mock with errors {0} When checking Then errors field is a JSON array")
    @ValueSource(strings = [
        "04-valid-errors-response-mock.json",
        "15-valid-both-data-and-errors.json"
    ])
    fun `Property 9 - Given mock with errors When checking response Then errors field is a JSON array`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val response = mockJson["wireMockMapping"]!!.jsonObject["response"]!!.jsonObject
        val jsonBody = response["jsonBody"]!!.jsonObject
        val errorsField = jsonBody["errors"]

        // Then
        assertTrue(
            errorsField is JsonArray,
            "[$filename] GraphQL response 'errors' field should be a JSON array"
        )
        assertTrue(
            errorsField.jsonArray.isNotEmpty(),
            "[$filename] GraphQL response 'errors' array should not be empty"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given all mocks {0} When checking response Then HTTP status is 200")
    @ValueSource(strings = [
        "01-valid-query-mock.json",
        "02-valid-mutation-mock.json",
        "03-valid-enum-mock.json",
        "04-valid-errors-response-mock.json",
        "05-invalid-operation-not-found.json",
        "06-invalid-argument-type-mismatch.json",
        "07-invalid-missing-required-fields.json",
        "08-invalid-enum-value.json",
        "10-invalid-type-incompatibility.json",
        "11-invalid-list-structure-mismatch.json",
        "12-invalid-missing-required-argument.json",
        "13-valid-list-response-mock.json",
        "14-invalid-unexpected-argument.json",
        "15-valid-both-data-and-errors.json"
    ])
    fun `Property 9 - Given GraphQL mock When checking response Then HTTP status is 200 per GraphQL convention`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val mockJsonMappingResponse = mockJson["wireMockMapping"]?.jsonObject["response"]
        assertNotNull(mockJsonMappingResponse)
        val response = mockJsonMappingResponse.jsonObject
        val status = response["status"]
        assertNotNull(status)
        val statusInt = status.jsonPrimitive.int

        // Then - GraphQL always returns HTTP 200, errors are in the response body
        assertEquals(200,statusInt, "[$filename] GraphQL mock response HTTP status should be 200 (errors go in response body, not HTTP status)")
    }
}
