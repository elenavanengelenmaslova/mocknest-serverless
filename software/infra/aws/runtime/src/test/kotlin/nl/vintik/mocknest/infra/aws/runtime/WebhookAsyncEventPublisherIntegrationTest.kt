package nl.vintik.mocknest.infra.aws.runtime

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookAsyncEventPublisher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test verifying that [WebhookAsyncEventPublisher] is invoked correctly
 * when a WireMock stub with a "webhook" serveEventListener is matched.
 *
 * Validates:
 * - The in-process SQS stub captures exactly one AsyncEvent with correct url/method/body
 * - The built-in WireMock outbound HTTP call fires at the no-op target (not the real callback)
 * - No duplicate delivery to the real callback URL
 *
 * Requirements: 1.1, 1.4, 2.1, 2.2
 */
class WebhookAsyncEventPublisherIntegrationTest {

    /** In-process SQS stub that captures published messages */
    private val capturedMessages = CopyOnWriteArrayList<String>()
    private val sqsStub = object : SqsPublisherInterface {
        override suspend fun publish(queueUrl: String, messageBody: String) {
            capturedMessages.add(messageBody)
        }
    }

    private val queueUrl = "https://sqs.eu-west-1.amazonaws.com/123456789/test-queue"
    private lateinit var wireMockServer: WireMockServer
    private lateinit var callbackServer: MockWebServer
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    @BeforeEach
    fun setUp() {
        capturedMessages.clear()
        callbackServer = MockWebServer()
        callbackServer.start()
        callbackServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"received":true}"""))

        val asyncPublisher = WebhookAsyncEventPublisher(sqsStub, queueUrl)
        wireMockServer = WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .extensions(asyncPublisher)
        )
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
        callbackServer.shutdown()
    }

    @Test
    fun `Given stub with webhook listener When trigger request fires Then exactly one AsyncEvent is published with correct url method and body`() {
        val callbackUrl = callbackServer.url("/callback").toString()

        // Register trigger stub with webhook serveEventListener
        wireMockServer.addStubMapping(
            post(urlPathEqualTo("/trigger"))
                .willReturn(aResponse().withStatus(202).withBody("""{"triggered":true}"""))
                .withServeEventListener(
                    "webhook",
                    Parameters.from(mapOf(
                        "url" to callbackUrl,
                        "method" to "POST",
                        "body" to """{"event":"order.created"}""",
                    ))
                )
                .build()
        )

        // Fire the trigger request
        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/trigger")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
        ).execute()
        assertEquals(202, response.code)
        response.close()

        // Wait briefly for async webhook processing
        Thread.sleep(500)

        // Assert exactly one AsyncEvent was published
        assertEquals(1, capturedMessages.size, "Expected exactly one AsyncEvent published to SQS")

        val event = Json.decodeFromString(AsyncEvent.serializer(), capturedMessages[0])
        assertEquals("webhook", event.actionType)
        assertEquals(callbackUrl, event.url)
        assertEquals("POST", event.method)
        assertEquals("""{"event":"order.created"}""", event.body)
        assertEquals("none", event.auth.type)
    }

    @Test
    fun `Given stub with webhook listener When trigger fires Then built-in HTTP call does NOT reach real callback URL`() {
        val callbackUrl = callbackServer.url("/callback").toString()

        wireMockServer.addStubMapping(
            post(urlPathEqualTo("/trigger-no-dup"))
                .willReturn(aResponse().withStatus(202))
                .withServeEventListener(
                    "webhook",
                    Parameters.from(mapOf(
                        "url" to callbackUrl,
                        "method" to "POST",
                        "body" to "test",
                    ))
                )
                .build()
        )

        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/trigger-no-dup")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
        ).execute()
        assertEquals(202, response.code)
        response.close()

        Thread.sleep(500)

        // The callback server should have received at most 0 requests
        // (the no-op redirect goes to localhost:0 which fails silently)
        val callbackRequests = callbackServer.requestCount
        assertEquals(0, callbackRequests, "Real callback URL should NOT have been called — no duplicate delivery")
    }

    @Test
    fun `Given stub with aws_iam auth When trigger fires Then AsyncEvent auth type is aws_iam`() {
        val callbackUrl = callbackServer.url("/callback").toString()

        wireMockServer.addStubMapping(
            post(urlPathEqualTo("/trigger-iam"))
                .willReturn(aResponse().withStatus(202))
                .withServeEventListener(
                    "webhook",
                    Parameters.from(mapOf(
                        "url" to callbackUrl,
                        "method" to "POST",
                        "body" to "test",
                        "auth" to mapOf("type" to "aws_iam", "region" to "eu-west-1", "service" to "execute-api"),
                    ))
                )
                .build()
        )

        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/trigger-iam")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
        ).execute()
        assertEquals(202, response.code)
        response.close()

        Thread.sleep(500)

        assertEquals(1, capturedMessages.size)
        val event = Json.decodeFromString(AsyncEvent.serializer(), capturedMessages[0])
        assertEquals("aws_iam", event.auth.type)
        assertEquals("eu-west-1", event.auth.region)
        assertEquals("execute-api", event.auth.service)
    }
}
