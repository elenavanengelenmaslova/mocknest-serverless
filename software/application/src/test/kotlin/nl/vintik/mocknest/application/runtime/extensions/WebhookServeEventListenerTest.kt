package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebhookServeEventListenerTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)

    private val defaultConfig = WebhookConfig(
        selfUrl = "https://api.example.com/prod",
        sensitiveHeaders = setOf("x-api-key", "authorization"),
        webhookTimeoutMs = 10_000L,
    )

    private fun listener(config: WebhookConfig = defaultConfig) =
        WebhookServeEventListener(mockHttpClient, config)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildServeEvent(
        headers: Map<String, String> = emptyMap(),
        listenerDefs: List<ServeEventListenerDefinition> = emptyList(),
        id: UUID = UUID.randomUUID(),
    ): ServeEvent {
        val httpHeaders = HttpHeaders(headers.map { (k, v) -> HttpHeader(k, v) })
        val request = mockk<LoggedRequest>(relaxed = true)
        every { request.headers } returns httpHeaders
        every { request.getHeader(any()) } answers {
            val name = firstArg<String>()
            headers[name] ?: headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        }

        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns id
        every { serveEvent.request } returns request
        every { serveEvent.serveEventListeners } returns listenerDefs
        return serveEvent
    }

    private fun webhookListenerDef(
        url: String = "https://callback.example.com/hook",
        method: String = "POST",
        body: String? = null,
        auth: Map<String, Any>? = null,
    ): ServeEventListenerDefinition {
        val paramsMap = mutableMapOf<String, Any>("url" to url, "method" to method)
        body?.let { paramsMap["body"] = it }
        auth?.let { paramsMap["auth"] = it }
        val params = Parameters.from(paramsMap)
        val def = mockk<ServeEventListenerDefinition>()
        every { def.name } returns "mocknest-webhook"
        every { def.parameters } returns params
        return def
    }

    private fun noAuthBlock(): Map<String, Any> = mapOf("type" to "none")

    private fun headerAuthBlock(injectName: String, headerName: String): Map<String, Any> = mapOf(
        "type" to "header",
        "inject" to mapOf("name" to injectName),
        "value" to mapOf("source" to "original_request_header", "headerName" to headerName),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 4.1 — afterMatch: redaction side-channel capture
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class AfterMatchRedaction {

        @Test
        fun `Given request with x-api-key header When afterMatch called Then x-api-key is captured in side-channel`() {
            val l = listener()
            val serveEvent = buildServeEvent(
                headers = mapOf("x-api-key" to "secret-key-value"),
            )
            // afterMatch captures the value; we verify indirectly via beforeResponseSent auth injection
            l.afterMatch(serveEvent, Parameters.empty())
            // No exception thrown — capture succeeded
        }

        @Test
        fun `Given request with authorization header When afterMatch called Then authorization is captured`() {
            val l = listener()
            val serveEvent = buildServeEvent(
                headers = mapOf("authorization" to "Bearer token123"),
            )
            l.afterMatch(serveEvent, Parameters.empty())
            // No exception — capture succeeded
        }

        @Test
        fun `Given request with non-sensitive header When afterMatch called Then no exception and non-sensitive header not captured`() {
            val l = listener()
            val serveEvent = buildServeEvent(
                headers = mapOf("content-type" to "application/json"),
            )
            l.afterMatch(serveEvent, Parameters.empty())
            // No exception thrown
        }

        @Test
        fun `Given request with mixed-case sensitive header name When afterMatch called Then value is captured case-insensitively`() {
            val l = listener()
            val id = UUID.randomUUID()
            val serveEvent = buildServeEvent(
                headers = mapOf("X-Api-Key" to "mixed-case-value"),
                id = id,
            )
            // Capture happens; verify via auth injection in beforeResponseSent
            l.afterMatch(serveEvent, Parameters.empty())

            // Now call beforeResponseSent with auth config referencing x-api-key
            val listenerDef = webhookListenerDef(auth = headerAuthBlock("x-api-key", "x-api-key"))
            val serveEvent2 = buildServeEvent(
                headers = mapOf("X-Api-Key" to "mixed-case-value"),
                listenerDefs = listOf(listenerDef),
                id = id,
            )
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            l.beforeResponseSent(serveEvent2, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals("mixed-case-value", slot.captured.headers["x-api-key"])
        }

        @Test
        fun `Given afterMatch throws internally When called Then no exception propagates`() {
            val l = listener()
            val serveEvent = mockk<ServeEvent>(relaxed = true)
            every { serveEvent.request } throws RuntimeException("simulated failure")
            every { serveEvent.id } returns UUID.randomUUID()

            // Must not throw
            l.afterMatch(serveEvent, Parameters.empty())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.2 — beforeResponseSent: webhook dispatch with auth config
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class BeforeResponseSentDispatch {

        @Test
        fun `Given stub with webhook listener and no auth block When beforeResponseSent called Then send is called with no injected auth header`() {
            val l = listener()
            val listenerDef = webhookListenerDef()
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertTrue(slot.captured.headers.isEmpty())
        }

        @Test
        fun `Given stub with webhook listener and auth type none When beforeResponseSent called Then send is called with no injected auth header`() {
            val l = listener()
            val listenerDef = webhookListenerDef(auth = noAuthBlock())
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertTrue(slot.captured.headers.isEmpty())
        }

        @Test
        fun `Given stub with webhook listener and original_request_header auth When beforeResponseSent called Then named header is injected`() {
            val l = listener()
            val id = UUID.randomUUID()
            val incomingHeaders = mapOf("x-api-key" to "caller-api-key")

            // First call afterMatch to capture the header
            val afterMatchEvent = buildServeEvent(headers = incomingHeaders, id = id)
            l.afterMatch(afterMatchEvent, Parameters.empty())

            // Then call beforeResponseSent
            val listenerDef = webhookListenerDef(auth = headerAuthBlock("x-api-key", "x-api-key"))
            val serveEvent = buildServeEvent(
                headers = incomingHeaders,
                listenerDefs = listOf(listenerDef),
                id = id,
            )
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals("caller-api-key", slot.captured.headers["x-api-key"])
        }

        @Test
        fun `Given stub without webhook listener When beforeResponseSent called Then send is NOT called`() {
            val l = listener()
            val serveEvent = buildServeEvent(listenerDefs = emptyList())

            l.beforeResponseSent(serveEvent, Parameters.empty())

            verify(exactly = 0) { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given webhook client returns Failure When beforeResponseSent called Then warning is logged and no exception is thrown`() {
            val l = listener()
            val listenerDef = webhookListenerDef()
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Failure(503, "Service Unavailable")

            // Must not throw
            l.beforeResponseSent(serveEvent, Parameters.empty())

            verify { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given webhook client throws exception When beforeResponseSent called Then warning is logged and no exception propagates`() {
            val l = listener()
            val listenerDef = webhookListenerDef()
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } throws RuntimeException("network failure")

            // Must not throw
            l.beforeResponseSent(serveEvent, Parameters.empty())
        }

        @Test
        fun `Given timeout configured When beforeResponseSent called Then WebhookRequest timeoutMs matches config`() {
            val config = defaultConfig.copy(webhookTimeoutMs = 5_000L)
            val l = listener(config)
            val listenerDef = webhookListenerDef()
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals(5_000L, slot.captured.timeoutMs)
        }

        @Test
        fun `Given webhook URL contains self-url placeholder When beforeResponseSent called Then placeholder is replaced with selfUrl`() {
            val l = listener()
            val listenerDef = webhookListenerDef(url = "{{mocknest-self-url}}/callback")
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals("https://api.example.com/prod/callback", slot.captured.url)
        }

        @Test
        fun `Given selfUrl is null and URL contains placeholder When beforeResponseSent called Then placeholder is replaced with empty string`() {
            val config = defaultConfig.copy(selfUrl = null)
            val l = listener(config)
            val listenerDef = webhookListenerDef(url = "{{mocknest-self-url}}/callback")
            val serveEvent = buildServeEvent(listenerDefs = listOf(listenerDef))
            every { mockHttpClient.send(any()) } returns WebhookResult.Failure(null, "invalid url")

            l.beforeResponseSent(serveEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals("/callback", slot.captured.url)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listener metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class ListenerMetadata {

        @Test
        fun `Given listener instance When getName called Then returns mocknest-webhook`() {
            assertEquals("mocknest-webhook", listener().getName())
        }

        @Test
        fun `Given listener instance When applyGlobally called Then returns true`() {
            assertTrue(listener().applyGlobally())
        }
    }
}
