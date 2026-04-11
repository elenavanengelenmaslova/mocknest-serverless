package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.wiremock.webhooks.WebhookDefinition
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebhookAsyncEventPublisherTest {

    private val capturedMessages = mutableListOf<String>()
    private val capturingSqsPublisher = object : SqsPublisherInterface {
        override suspend fun publish(queueUrl: String, messageBody: String) {
            capturedMessages.add(messageBody)
        }
    }
    private val queueUrl = "https://sqs.eu-west-1.amazonaws.com/123456789/test-queue"
    private val publisher = WebhookAsyncEventPublisher(capturingSqsPublisher, queueUrl)

    @AfterEach
    fun tearDown() {
        capturedMessages.clear()
    }

    private fun buildServeEvent(id: UUID = UUID.randomUUID()): ServeEvent {
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns id
        return serveEvent
    }

    private fun lastPublished(): String = capturedMessages.last()

    // ── WebhookTransformer ────────────────────────────────────────────────────

    @Nested
    inner class TransformBehaviour {

        @Test
        fun `Given any webhook definition When transform called Then URL is redirected to no-op target`() {
            val serveEvent = buildServeEvent()
            val definition = WebhookDefinition()
                .withUrl("https://real-callback.example.com/hook")
                .withMethod("POST")

            val result = publisher.transform(serveEvent, definition)

            assertNotNull(result.url)
            assertEquals("http://localhost:0/mocknest-noop", result.url)
        }

        @Test
        fun `Given webhook definition with URL When transform called Then AsyncEvent is published to SQS`() {
            val serveEvent = buildServeEvent()
            val url = "https://callback.example.com/hook"
            val definition = WebhookDefinition()
                .withUrl(url)
                .withMethod("POST")

            publisher.transform(serveEvent, definition)

            assertTrue(capturedMessages.isNotEmpty(), "Expected SQS publish to be called")
            val event = Json.decodeFromString(AsyncEvent.serializer(), lastPublished())
            assertEquals(url, event.url)
            assertEquals("POST", event.method)
        }

        @Test
        fun `Given webhook definition with body When transform called Then AsyncEvent contains body`() {
            val serveEvent = buildServeEvent()
            val body = """{"orderId":"12345"}"""
            val definition = WebhookDefinition()
                .withUrl("https://callback.example.com/hook")
                .withMethod("POST")
                .withBody(body)

            publisher.transform(serveEvent, definition)

            val event = Json.decodeFromString(AsyncEvent.serializer(), lastPublished())
            assertEquals(body, event.body)
        }

        @Test
        fun `Given webhook definition with template URL When transform called Then URL is still redirected`() {
            val serveEvent = buildServeEvent()
            val definition = WebhookDefinition()
                .withUrl("{{originalRequest.headers.X-Callback-Url}}")
                .withMethod("POST")

            val result = publisher.transform(serveEvent, definition)

            assertEquals("http://localhost:0/mocknest-noop", result.url)
        }

        @Test
        fun `Given SQS publish fails When transform called Then exception propagates`() {
            val failingPublisher = object : SqsPublisherInterface {
                override suspend fun publish(queueUrl: String, messageBody: String) {
                    throw RuntimeException("SQS unavailable")
                }
            }
            val failingInstance = WebhookAsyncEventPublisher(failingPublisher, queueUrl)
            val serveEvent = buildServeEvent()
            val definition = WebhookDefinition()
                .withUrl("https://callback.example.com/hook")
                .withMethod("POST")

            // Fixed behavior: exception propagates so WireMock can observe the failure
            assertFailsWith<RuntimeException> {
                failingInstance.transform(serveEvent, definition)
            }
        }

        @Test
        fun `Given getName called Then returns webhook`() {
            assertEquals("webhook", publisher.getName())
        }

        @Test
        fun `Given applyGlobally called Then returns false`() {
            assertEquals(false, publisher.applyGlobally())
        }
    }

    // ── ServeEventListener (afterComplete) ────────────────────────────────────

    @Nested
    inner class AfterCompleteBehaviour {

        @Test
        fun `Given afterComplete called Then no SQS publish occurs (transform handles it)`() {
            val serveEvent = buildServeEvent()
            val params = Parameters.from(mapOf("url" to "https://callback.example.com/hook"))

            publisher.afterComplete(serveEvent, params)

            assertTrue(capturedMessages.isEmpty(), "afterComplete should not publish — transform handles it")
        }
    }
}
