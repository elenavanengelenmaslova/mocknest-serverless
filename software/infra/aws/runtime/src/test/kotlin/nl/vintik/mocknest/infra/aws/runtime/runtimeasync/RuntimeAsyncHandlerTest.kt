package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RuntimeAsyncHandlerTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )
    private val handler = RuntimeAsyncHandler(mockHttpClient, webhookConfig, "eu-west-1")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildSqsEvent(vararg bodies: String): SQSEvent {
        val records = bodies.mapIndexed { i, body ->
            SQSEvent.SQSMessage().apply {
                messageId = "msg-$i"
                this.body = body
            }
        }
        return SQSEvent().apply { this.records = records }
    }

    private fun asyncEventJson(
        url: String = "https://callback.example.com/hook",
        method: String = "POST",
        headers: Map<String, String> = mapOf("Content-Type" to "application/json"),
        body: String? = """{"event":"triggered"}""",
        authType: String = "none",
        region: String? = null,
        service: String? = null,
    ): String = Json.encodeToString(
        AsyncEvent.serializer(),
        AsyncEvent(
            actionType = "webhook",
            url = url,
            method = method,
            headers = headers,
            body = body,
            auth = AsyncEventAuth(type = authType, region = region, service = service),
        )
    )

    @Test
    fun `Given AsyncEvent with auth type none When handler invoked Then WebhookHttpClient send called with correct URL method headers and body`() {
        val url = "https://callback.example.com/hook"
        val body = """{"event":"triggered"}"""
        val headers = mapOf("Content-Type" to "application/json", "x-correlation-id" to "abc-123")
        val json = asyncEventJson(url = url, method = "POST", headers = headers, body = body, authType = "none")
        val sqsEvent = buildSqsEvent(json)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(sqsEvent)

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        assertEquals(url, slot.captured.url)
        assertEquals("POST", slot.captured.method)
        assertEquals(body, slot.captured.body)
        assertEquals(headers, slot.captured.headers)
    }

    @Test
    fun `Given WebhookResult Failure When handler processes event Then WARN is logged and no exception thrown`() {
        val json = asyncEventJson()
        val sqsEvent = buildSqsEvent(json)
        every { mockHttpClient.send(any()) } returns WebhookResult.Failure(503, "Service Unavailable")

        // Must not throw
        handler.handle(sqsEvent)

        verify { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given malformed JSON record When handler processes event Then ERROR is logged and no exception thrown`() {
        val sqsEvent = buildSqsEvent("not-valid-json{{{")

        // Must not throw — poison-pill protection
        handler.handle(sqsEvent)

        verify(exactly = 0) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given multiple SQS records When handler invoked Then each record is processed`() {
        val json1 = asyncEventJson(url = "https://callback1.example.com/hook")
        val json2 = asyncEventJson(url = "https://callback2.example.com/hook")
        val sqsEvent = buildSqsEvent(json1, json2)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(sqsEvent)

        verify(exactly = 2) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given AsyncEvent with unknown actionType When handler invoked Then no HTTP call is made`() {
        val json = Json.encodeToString(
            AsyncEvent.serializer(),
            AsyncEvent(
                actionType = "unknown-action",
                url = "https://callback.example.com/hook",
                method = "POST",
                headers = emptyMap(),
                body = null,
                auth = AsyncEventAuth(type = "none"),
            )
        )
        val sqsEvent = buildSqsEvent(json)

        handler.handle(sqsEvent)

        verify(exactly = 0) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given AsyncEvent with null body When handler invoked Then WebhookRequest body is null`() {
        val json = asyncEventJson(body = null)
        val sqsEvent = buildSqsEvent(json)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(sqsEvent)

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        assertNotNull(slot.captured)
        assertEquals(null, slot.captured.body)
    }

    @Test
    fun `Given valid JSON but HTTP client throws IOException When handler processes event Then exception propagates (transient failure)`() {
        val json = asyncEventJson()
        val sqsEvent = buildSqsEvent(json)
        every { mockHttpClient.send(any()) } throws IOException("Connection timed out")

        // Transient failure must propagate so SQS can retry
        assertFailsWith<IOException> {
            handler.handle(sqsEvent)
        }
    }

    @Test
    fun `Given valid JSON but HTTP client throws RuntimeException When handler processes event Then exception propagates`() {
        val json = asyncEventJson()
        val sqsEvent = buildSqsEvent(json)
        every { mockHttpClient.send(any()) } throws RuntimeException("Unexpected error")

        assertFailsWith<RuntimeException> {
            handler.handle(sqsEvent)
        }
    }
}
