package nl.vintik.mocknest.domain.core

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertEquals

class CoreModelsTest {

    @Test
    fun `Should create valid HttpRequest`() {
        val request = HttpRequest(
            method = HttpMethod.GET,
            headers = mapOf("X-Test" to "Value"),
            path = "/test",
            queryParameters = mapOf("q" to "search"),
            body = "body"
        )
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("/test", request.path)
        assertEquals("body", request.body)
    }

    @Test
    fun `Should create valid HttpResponse`() {
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("Content-Type", "application/json")
        val response = HttpResponse(
            statusCode = HttpStatus.OK,
            headers = headers,
            body = "{}"
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("{}", response.body)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
    }
}
