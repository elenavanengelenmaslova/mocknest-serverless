package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactSensitiveHeadersFilterTest {

    private fun filter(vararg sensitiveHeaders: String) = RedactSensitiveHeadersFilter(
        WebhookConfig(
            sensitiveHeaders = sensitiveHeaders.map { it.lowercase() }.toSet(),
            webhookTimeoutMs = 10_000L,
            asyncTimeoutMs = 30_000L,
            requestJournalPrefix = "requests/",
        )
    )

    private val defaultFilter = filter("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token")

    @Test
    fun `Given response JSON with x-api-key header When filter applied Then value is REDACTED`() {
        val json = """{"headers":{"x-api-key":"secret-key-value","content-type":"application/json"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"), "Expected [REDACTED] in result")
        assertFalse(result.contains("secret-key-value"), "Sensitive value should not appear")
        assertTrue(result.contains("content-type"), "Non-sensitive header should be preserved")
    }

    @Test
    fun `Given response JSON with authorization header When filter applied Then value is REDACTED`() {
        val json = """{"headers":{"authorization":"Bearer token123","content-type":"application/json"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("token123"))
    }

    @Test
    fun `Given response JSON with x-amz-security-token header When filter applied Then value is REDACTED`() {
        val json = """{"headers":{"x-amz-security-token":"AQoXnyc4lcK4w==","content-type":"application/json"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("AQoXnyc4lcK4w=="))
    }

    @Test
    fun `Given response JSON with non-sensitive header When filter applied Then value is unchanged`() {
        val json = """{"headers":{"content-type":"application/json","x-correlation-id":"abc-123"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("application/json"), "Non-sensitive value should be preserved")
        assertTrue(result.contains("abc-123"), "Non-sensitive value should be preserved")
        assertFalse(result.contains("[REDACTED]"), "No redaction should occur for non-sensitive headers")
    }

    @Test
    fun `Given header name in mixed case When filter applied Then still redacted case-insensitively`() {
        val json = """{"headers":{"X-Api-Key":"secret-value","Authorization":"Bearer xyz"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("secret-value"))
        assertFalse(result.contains("Bearer xyz"))
    }

    @Test
    fun `Given proxy-authorization header When filter applied Then value is REDACTED`() {
        val json = """{"headers":{"proxy-authorization":"Basic dXNlcjpwYXNz"}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("dXNlcjpwYXNz"))
    }

    @Test
    fun `Given nested JSON with headers in request object When filter applied Then sensitive headers are redacted`() {
        val json = """{"request":{"headers":{"x-api-key":"my-secret","content-type":"text/plain"}}}"""

        val result = defaultFilter.redactHeadersInJson(json)

        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("my-secret"))
        assertTrue(result.contains("text/plain"))
    }

    @Test
    fun `Given empty sensitive headers config When filter applied Then no redaction occurs`() {
        val emptyFilter = filter()
        val json = """{"headers":{"x-api-key":"secret","authorization":"Bearer token"}}"""

        val result = emptyFilter.redactHeadersInJson(json)

        assertTrue(result.contains("secret"), "No redaction when sensitiveHeaders is empty")
        assertTrue(result.contains("Bearer token"))
    }
}
