package nl.vintik.mocknest.domain.core

import org.junit.jupiter.api.Test
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
        val headers = mapOf("Content-Type" to listOf("application/json"))
        val response = HttpResponse(
            statusCode = HttpStatusCode.OK,
            headers = headers,
            body = "{}"
        )
        assertEquals(HttpStatusCode.OK, response.statusCode)
        assertEquals("{}", response.body)
        assertEquals("application/json", response.headers?.get("Content-Type")?.first())
    }
}
