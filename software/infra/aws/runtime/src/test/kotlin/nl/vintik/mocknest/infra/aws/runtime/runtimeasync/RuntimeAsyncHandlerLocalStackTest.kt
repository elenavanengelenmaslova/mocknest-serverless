package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test for [RuntimeAsyncHandler].
 *
 * Validates:
 * - Invoking RuntimeAsyncHandler directly with an SQS event payload
 * - Asserting the callback mock received the expected request
 *
 * Requirements: 8.1–8.4, 8.6
 */
class RuntimeAsyncHandlerLocalStackTest {

    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )

    @Test
    fun `Given AsyncEvent in SQS record When RuntimeAsyncHandler invoked Then callback receives expected request`() = runBlocking {
        val callbackServer = MockWebServer()
        callbackServer.start()
        callbackServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"received":true}"""))

        try {
            val callbackUrl = callbackServer.url("/callback").toString()
            val event = AsyncEvent(
                actionType = "webhook",
                url = callbackUrl,
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"event":"order.created","orderId":"12345"}""",
                auth = AsyncEventAuth(type = "none"),
            )
            val eventJson = Json.encodeToString(AsyncEvent.serializer(), event)

            val sqsRecord = SQSEvent.SQSMessage().apply {
                messageId = "test-msg-1"
                body = eventJson
            }
            val sqsEvent = SQSEvent().apply { records = listOf(sqsRecord) }

            var capturedRequest: WebhookRequest? = null
            val capturingClient = object : WebhookHttpClientInterface {
                override fun send(request: WebhookRequest): WebhookResult {
                    capturedRequest = request
                    return WebhookResult.Success(200)
                }
            }

            val handler = RuntimeAsyncHandler(capturingClient, webhookConfig, "eu-west-1")
            handler.handle(sqsEvent)

            assertNotNull(capturedRequest, "Expected WebhookHttpClient to be called")
            assertEquals(callbackUrl, capturedRequest.url)
            assertEquals("POST", capturedRequest.method)
            assertEquals("""{"event":"order.created","orderId":"12345"}""", capturedRequest.body)
        } finally {
            callbackServer.shutdown()
        }
    }

    @Test
    fun `Given AsyncEvent with auth none When RuntimeAsyncHandler invoked Then request URL matches callback`() = runBlocking {
        val callbackServer = MockWebServer()
        callbackServer.start()
        callbackServer.enqueue(MockResponse().setResponseCode(200))

        try {
            val callbackUrl = callbackServer.url("/callback").toString()
            val event = AsyncEvent(
                actionType = "webhook",
                url = callbackUrl,
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = "test",
                auth = AsyncEventAuth(type = "none"),
            )
            val eventJson = Json.encodeToString(AsyncEvent.serializer(), event)
            val sqsRecord = SQSEvent.SQSMessage().apply {
                messageId = "test-msg-2"
                body = eventJson
            }
            val sqsEvent = SQSEvent().apply { records = listOf(sqsRecord) }

            var capturedRequest: WebhookRequest? = null
            val capturingClient = object : WebhookHttpClientInterface {
                override fun send(request: WebhookRequest): WebhookResult {
                    capturedRequest = request
                    return WebhookResult.Success(200)
                }
            }

            val handler = RuntimeAsyncHandler(capturingClient, webhookConfig, "eu-west-1")
            handler.handle(sqsEvent)

            assertNotNull(capturedRequest)
            assertEquals(callbackUrl, capturedRequest.url)
        } finally {
            callbackServer.shutdown()
        }
    }
}
