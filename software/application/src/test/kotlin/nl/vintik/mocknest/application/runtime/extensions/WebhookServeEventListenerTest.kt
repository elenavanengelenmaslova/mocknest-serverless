package nl.vintik.mocknest.application.runtime.extensions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [redactUrl] — Fix 1.2.
 *
 * Verifies that query strings and fragments are stripped from URLs before logging,
 * preventing PII (tokens, tenant IDs) from being written to CloudWatch Logs.
 */
class WebhookServeEventListenerTest {

    @Test
    fun `Given URL with query string When redactUrl called Then query string is stripped`() {
        val url = "https://api.example.com/hook?token=secret123&tenant=acme"
        assertEquals("https://api.example.com/hook", redactUrl(url))
    }

    @Test
    fun `Given URL with fragment When redactUrl called Then fragment is stripped`() {
        val url = "https://api.example.com/hook#section"
        assertEquals("https://api.example.com/hook", redactUrl(url))
    }

    @Test
    fun `Given URL with both query string and fragment When redactUrl called Then both are stripped`() {
        val url = "https://api.example.com/hook?token=secret#section"
        assertEquals("https://api.example.com/hook", redactUrl(url))
    }

    @Test
    fun `Given plain URL with no query string or fragment When redactUrl called Then URL is unchanged`() {
        val url = "https://api.example.com/hook"
        assertEquals("https://api.example.com/hook", redactUrl(url))
    }

    @Test
    fun `Given URL with port When redactUrl called Then port is preserved and query string stripped`() {
        val url = "https://api.example.com:8443/hook?token=secret"
        assertEquals("https://api.example.com:8443/hook", redactUrl(url))
    }

    @Test
    fun `Given URL with path segments When redactUrl called Then full path is preserved`() {
        val url = "https://api.example.com/v1/webhooks/callback?sig=abc123"
        assertEquals("https://api.example.com/v1/webhooks/callback", redactUrl(url))
    }

    @Test
    fun `Given URL with only query string and no path When redactUrl called Then query string is stripped`() {
        val url = "https://api.example.com?token=secret"
        assertEquals("https://api.example.com", redactUrl(url))
    }
}
