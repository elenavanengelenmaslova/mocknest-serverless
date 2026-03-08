package nl.vintik.mocknest.application.runtime.usecases

import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.Response
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.domain.core.HttpRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders

class HandleRequestTest {

    @Test
    fun `Should forward request correctly`() {
        val httpRequest = HttpRequest(
            method = HttpMethod.POST,
            path = "test/path",
            headers = mapOf("X-Custom" to "Value"),
            queryParameters = mapOf("q" to "search name"),
            body = "{\"key\":\"value\"}"
        )

        val mockResponse = mockk<Response>()
        every { mockResponse.status } returns 201
        every { mockResponse.bodyAsString } returns "{\"result\":\"ok\"}"
        every { mockResponse.headers } returns WireMockHttpHeaders(
            HttpHeader("X-Response", "RespValue"),
            HttpHeader("Matched-Stub-Id", "some-uuid")
        )

        val result = forwardToDirectCallHttpServer("test", httpRequest) { wireMockRequest ->
            assertEquals("POST", wireMockRequest.method.name)
            assertTrue(wireMockRequest.absoluteUrl.contains("test/path"))
            assertTrue(wireMockRequest.absoluteUrl.contains("q=search+name"))
            assertEquals("Value", wireMockRequest.getHeader("X-Custom"))
            assertEquals("{\"key\":\"value\"}", wireMockRequest.bodyAsString)
            mockResponse
        }

        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals("{\"result\":\"ok\"}", result.body)
        assertEquals("RespValue", result.headers?.getFirst("X-Response"))
        // Matched-Stub-Id should be filtered out
        assertTrue(result.headers?.containsKey("Matched-Stub-Id") == false)
        // Default content type should be added if missing
        assertTrue(result.headers?.getFirst("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun `Should handle response with content type header`() {
        val httpRequest = HttpRequest(HttpMethod.GET, emptyMap(), "/test", emptyMap(), null)
        val mockResponse = mockk<Response>()
        every { mockResponse.status } returns 200
        every { mockResponse.bodyAsString } returns "ok"
        every { mockResponse.headers } returns WireMockHttpHeaders(
            HttpHeader("Content-Type", "text/plain")
        )

        val result = forwardToDirectCallHttpServer("test", httpRequest) { mockResponse }

        assertEquals("text/plain", result.headers?.getFirst("Content-Type"))
    }
}
