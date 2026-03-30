package nl.vintik.mocknest.application.generation.validators

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based test: Property-9 (WireMock Mapping Compatibility) for SOAP mocks.
 *
 * For any generated SOAP mock, the WireMock mapping JSON must be structurally valid
 * and compatible with the existing persistence model:
 * - Contains `request` and `response` at the top level
 * - `request.method` is `POST`
 * - `response` contains `body` or `bodyFileName`
 * - `"persistent": true` at the top level
 *
 * **Property 9: WireMock Mapping Compatibility**
 * **Validates: Requirements 10.1, 10.2**
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-9")
class SoapWireMockMappingCompatibilityPropertyTest {

    private fun loadMock(filename: String): JsonObject {
        val content = this::class.java.getResource("/soap/mocks/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
        return Json.parseToJsonElement(content).jsonObject
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 9: WireMock Mapping Compatibility — structural validity
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 9 - Given {0} When checking structure Then has request and response keys")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "07-valid-soap11-with-body-filename.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking structure Then has required request and response keys`(
        filename: String
    ) {
        // Given
        val mockJson = loadMock(filename)

        // Then — top-level keys
        assertTrue(
            mockJson.containsKey("request"),
            "[$filename] WireMock mapping must contain 'request' key"
        )
        assertTrue(
            mockJson["request"] is JsonObject,
            "[$filename] WireMock mapping 'request' must be a JSON object"
        )
        assertTrue(
            mockJson.containsKey("response"),
            "[$filename] WireMock mapping must contain 'response' key"
        )
        assertTrue(
            mockJson["response"] is JsonObject,
            "[$filename] WireMock mapping 'response' must be a JSON object"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking request method Then method is POST")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "07-valid-soap11-with-body-filename.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking request method Then method is POST`(filename: String) {
        // Given
        val mockJson = loadMock(filename)
        val request = mockJson["request"]!!.jsonObject

        // Then — SOAP always uses POST
        assertTrue(
            request.containsKey("method"),
            "[$filename] WireMock request must contain 'method' key"
        )
        assertEquals(
            "POST",
            request["method"]?.jsonPrimitive?.content,
            "[$filename] SOAP WireMock request method must be POST"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking response Then has body or bodyFileName")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "07-valid-soap11-with-body-filename.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking response Then has body or bodyFileName`(filename: String) {
        // Given
        val mockJson = loadMock(filename)
        val response = mockJson["response"]!!.jsonObject

        // Then — response must have a body
        val hasBody = response.containsKey("body")
        val hasBodyFileName = response.containsKey("bodyFileName")
        val hasJsonBody = response.containsKey("jsonBody")
        assertTrue(
            hasBody || hasBodyFileName || hasJsonBody,
            "[$filename] WireMock response must contain 'body', 'bodyFileName', or 'jsonBody'"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking persistence Then persistent is true")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "07-valid-soap11-with-body-filename.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking persistence Then persistent is true at top level`(filename: String) {
        // Given
        val mockJson = loadMock(filename)

        // Then — persistent must be true for WireMock persistence compatibility
        assertTrue(
            mockJson.containsKey("persistent"),
            "[$filename] WireMock mapping must contain 'persistent' key at top level"
        )
        val persistentValue = mockJson["persistent"]
        assertTrue(
            persistentValue is JsonPrimitive && persistentValue.content == "true",
            "[$filename] WireMock mapping 'persistent' must be true, found: $persistentValue"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking response status Then status is valid HTTP code")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking response status Then status is valid HTTP code`(filename: String) {
        // Given
        val mockJson = loadMock(filename)
        val response = mockJson["response"]!!.jsonObject

        // Then — response status must be a valid HTTP status code
        val status = response["status"]
        assertNotNull(status){"[$filename] WireMock response must contain 'status' key"}
        val statusInt = status.jsonPrimitive.content.toInt()
        assertTrue(
            statusInt in 100..599,
            "[$filename] WireMock response status $statusInt should be a valid HTTP status code"
        )
    }

    @ParameterizedTest(name = "Property 9 - Given {0} When checking request URL Then has urlPath or url")
    @ValueSource(strings = [
        "01-valid-soap11-add-mock.json",
        "02-valid-soap11-subtract-mock.json",
        "03-valid-soap11-multiply-mock.json",
        "04-valid-soap11-divide-mock.json",
        "05-valid-soap12-get-weather-mock.json",
        "06-valid-soap12-get-forecast-mock.json",
        "07-valid-soap11-with-body-filename.json",
        "08-valid-soap11-hello-service-mock.json",
        "09-valid-soap12-greet-service-mock.json",
        "10-valid-soap11-multi-operation-mock.json"
    ])
    fun `Property 9 - Given SOAP mock When checking request URL Then has urlPath or url`(filename: String) {
        // Given
        val mockJson = loadMock(filename)
        val request = mockJson["request"]!!.jsonObject

        // Then — request must have a URL matcher
        val hasUrlPath = request.containsKey("urlPath")
        val hasUrl = request.containsKey("url")
        val hasUrlPattern = request.containsKey("urlPattern")
        assertTrue(
            hasUrlPath || hasUrl || hasUrlPattern,
            "[$filename] WireMock request must contain 'urlPath', 'url', or 'urlPattern'"
        )
    }
}
