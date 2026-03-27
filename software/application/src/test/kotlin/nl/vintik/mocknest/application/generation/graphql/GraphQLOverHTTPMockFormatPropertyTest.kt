package nl.vintik.mocknest.application.generation.graphql

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based test for GraphQL-over-HTTP mock format.
 *
 * **Validates: Requirements 4.3**
 *
 * Property 8: GraphQL-over-HTTP Mock Format
 * For any generated GraphQL mock, the WireMock mapping must specify POST method,
 * target the /graphql endpoint, and include JSON body matchers for GraphQL operations.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-8")
class GraphQLOverHTTPMockFormatPropertyTest {

    private fun loadMock(filename: String): JsonObject {
        val content = this::class.java.getResource("/graphql/mocks/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
        return Json.parseToJsonElement(content).jsonObject
    }

    @ParameterizedTest(name = "Property 8 - Given {0} When checking request Then method is POST")
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
    fun `Property 8 - Given GraphQL mock When checking request Then method is POST`(filename: String) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val request = mockJson["wireMockMapping"]!!.jsonObject["request"]!!.jsonObject
        val method = request["method"]!!.jsonPrimitive.content

        // Then
        assertEquals("POST", method, "[$filename] GraphQL mock request method should be POST")
    }

    @ParameterizedTest(name = "Property 8 - Given {0} When checking request Then urlPath is /graphql")
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
    fun `Property 8 - Given GraphQL mock When checking request Then urlPath is graphql`(filename: String) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val request = mockJson["wireMockMapping"]!!.jsonObject["request"]!!.jsonObject
        val urlPath = request["urlPath"]!!.jsonPrimitive.content

        // Then
        assertEquals("/graphql", urlPath, "[$filename] GraphQL mock request urlPath should be /graphql")
    }

    @ParameterizedTest(name = "Property 8 - Given {0} When checking request Then bodyPatterns is present and non-empty")
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
    fun `Property 8 - Given GraphQL mock When checking request Then bodyPatterns is present and non-empty`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // When
        val request = mockJson["wireMockMapping"]!!.jsonObject["request"]!!.jsonObject
        val bodyPatterns = request["bodyPatterns"]?.jsonArray

        // Then
        assertTrue(
            bodyPatterns != null && bodyPatterns.isNotEmpty(),
            "[$filename] GraphQL mock request should have non-empty bodyPatterns for operation matching"
        )
    }
}
