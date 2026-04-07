package nl.vintik.mocknest.application.runtime.extensions

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebhookModelsTest {

    @AfterEach
    fun tearDown() {
        // No mocks to clear — pure data model tests
    }

    @Test
    fun `Given valid fields When constructing WebhookRequest Then all properties are accessible`() {
        val headers = mapOf("Content-Type" to "application/json", "x-correlation-id" to "abc-123")
        val request = WebhookRequest(
            url = "https://example.com/callback",
            method = "POST",
            headers = headers,
            body = """{"event":"order.created"}""",
            timeoutMs = 5000L,
        )

        assertEquals("https://example.com/callback", request.url)
        assertEquals("POST", request.method)
        assertEquals(headers, request.headers)
        assertEquals("""{"event":"order.created"}""", request.body)
        assertEquals(5000L, request.timeoutMs)
    }

    @Test
    fun `Given WebhookRequest with null body When constructing Then body is null`() {
        val request = WebhookRequest(
            url = "https://example.com/callback",
            method = "GET",
            headers = emptyMap(),
            body = null,
            timeoutMs = 10000L,
        )

        assertNull(request.body)
    }

    @Test
    fun `Given Success result When checking type Then is Success subtype`() {
        val result: WebhookResult = WebhookResult.Success(statusCode = 200)

        assertIs<WebhookResult.Success>(result)
        assertEquals(200, result.statusCode)
    }

    @Test
    fun `Given Failure result with non-null statusCode When checking fields Then statusCode and message are preserved`() {
        val result: WebhookResult = WebhookResult.Failure(statusCode = 503, message = "Service Unavailable")

        assertIs<WebhookResult.Failure>(result)
        assertEquals(503, result.statusCode)
        assertEquals("Service Unavailable", result.message)
    }

    @Test
    fun `Given Failure result with null statusCode When checking message Then message is preserved`() {
        val result: WebhookResult = WebhookResult.Failure(statusCode = null, message = "Connection refused")

        assertIs<WebhookResult.Failure>(result)
        assertNull(result.statusCode)
        assertEquals("Connection refused", result.message)
    }

    @Test
    fun `Given None auth config When checking type Then is None subtype`() {
        val config: WebhookAuthConfig = WebhookAuthConfig.None

        assertIs<WebhookAuthConfig.None>(config)
    }

    @Test
    fun `Given Header auth config with OriginalRequestHeader When accessing fields Then injectName and headerName are accessible`() {
        val valueSource = HeaderValueSource.OriginalRequestHeader(headerName = "x-api-key")
        val config: WebhookAuthConfig = WebhookAuthConfig.Header(
            injectName = "x-api-key",
            valueSource = valueSource,
        )

        assertIs<WebhookAuthConfig.Header>(config)
        assertEquals("x-api-key", config.injectName)
        val source = config.valueSource
        assertIs<HeaderValueSource.OriginalRequestHeader>(source)
        assertEquals("x-api-key", source.headerName)
    }

    @Test
    fun `Given Header auth config with different inject and source names When accessing fields Then both names are preserved`() {
        val valueSource = HeaderValueSource.OriginalRequestHeader(headerName = "authorization")
        val config = WebhookAuthConfig.Header(
            injectName = "x-forwarded-auth",
            valueSource = valueSource,
        )

        assertEquals("x-forwarded-auth", config.injectName)
        val source = config.valueSource
        assertIs<HeaderValueSource.OriginalRequestHeader>(source)
        assertEquals("authorization", source.headerName)
    }
}
