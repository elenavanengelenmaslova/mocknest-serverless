package nl.vintik.mocknest.application.generation.graphql

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

/**
 * Property-based test for WireMock persistence compatibility.
 *
 * **Validates: Requirements 7.1**
 *
 * Property 13: WireMock Persistence Compatibility
 * For any generated GraphQL mock, the WireMock mapping must be structurally valid
 * WireMock JSON that is compatible with the existing persistence model.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-13")
class WireMockPersistenceCompatibilityPropertyTest {

    private fun loadMock(filename: String): JsonObject {
        val content = this::class.java.getResource("/graphql/mocks/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
        return Json.parseToJsonElement(content).jsonObject
    }

    @ParameterizedTest(name = "Property 13 - Given {0} When checking structure Then has request and response keys")
    @ValueSource(strings = [
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
    ])
    fun `Property 13 - Given GraphQL mock When checking structure Then has required request and response keys`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val wireMockMapping = mockJson["wireMockMapping"]!!.jsonObject

        // Then
        assertTrue(
            wireMockMapping.containsKey("request"),
            "[$filename] WireMock mapping must contain 'request' key"
        )
        assertTrue(
            wireMockMapping["request"] is JsonObject,
            "[$filename] WireMock mapping 'request' must be a JSON object"
        )
        assertTrue(
            wireMockMapping.containsKey("response"),
            "[$filename] WireMock mapping must contain 'response' key"
        )
        assertTrue(
            wireMockMapping["response"] is JsonObject,
            "[$filename] WireMock mapping 'response' must be a JSON object"
        )
    }

    @ParameterizedTest(name = "Property 13 - Given {0} When checking request Then has method and urlPath")
    @ValueSource(strings = [
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
    ])
    fun `Property 13 - Given GraphQL mock When checking request Then has method and urlPath`(filename: String) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val request = mockJson["wireMockMapping"]!!.jsonObject["request"]!!.jsonObject

        // Then
        assertTrue(
            request.containsKey("method"),
            "[$filename] WireMock request must contain 'method' key"
        )
        assertTrue(
            request["method"] is JsonPrimitive,
            "[$filename] WireMock request 'method' must be a string"
        )

        val hasUrlPath = request.containsKey("urlPath")
        val hasUrl = request.containsKey("url")
        assertTrue(
            hasUrlPath || hasUrl,
            "[$filename] WireMock request must contain 'urlPath' or 'url'"
        )
    }

    @ParameterizedTest(name = "Property 13 - Given {0} When checking response Then has status and body")
    @ValueSource(strings = [
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
    ])
    fun `Property 13 - Given GraphQL mock When checking response Then has status and response body`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val response = mockJson["wireMockMapping"]!!.jsonObject["response"]!!.jsonObject

        // Then
        assertTrue(
            response.containsKey("status"),
            "[$filename] WireMock response must contain 'status' key"
        )
        val status = response["status"]!!.jsonPrimitive.int
        assertTrue(
            status in 100..599,
            "[$filename] WireMock response status $status should be a valid HTTP status code"
        )

        val hasJsonBody = response.containsKey("jsonBody")
        val hasBody = response.containsKey("body")
        val hasBase64Body = response.containsKey("base64Body")
        assertTrue(
            hasJsonBody || hasBody || hasBase64Body,
            "[$filename] WireMock response must contain 'jsonBody', 'body', or 'base64Body'"
        )
    }
}
