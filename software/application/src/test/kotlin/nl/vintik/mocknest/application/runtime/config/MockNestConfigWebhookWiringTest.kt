package nl.vintik.mocknest.application.runtime.config

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

/**
 * Unit tests for MockNestConfig webhook wiring.
 *
 * Validates that WebhookAsyncEventPublisher is registered and active in the WireMock server
 * by verifying behavioral evidence: a stub with serveEventListeners triggers sqsPublisher.publish().
 *
 * Validates: Requirements 1.1, 2.1
 */
class MockNestConfigWebhookWiringTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val config = MockNestConfig()

    @Test
    fun `Given Spring context loads When wireMockServer bean created Then WebhookAsyncEventPublisher is registered with name webhook`() {
        val webhookConfig = WebhookConfig(
            sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
            webhookTimeoutMs = 10_000L,
            asyncTimeoutMs = 30_000L,
            requestJournalPrefix = "requests/",
        )
        val publishCalled = AtomicBoolean(false)
        val capturingSqsPublisher = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                publishCalled.set(true)
            }
        }

        val factory = config.directCallHttpServerFactory()
        val server = config.wireMockServer(factory, mockStorage, webhookConfig, capturingSqsPublisher, "test-queue-url")

        try {
            server.addStubMapping(
                post(urlPathEqualTo("/test-webhook"))
                    .willReturn(aResponse().withStatus(200))
                    .withServeEventListener(
                        "webhook",
                        Parameters.from(mapOf(
                            "url" to "https://api.example.com/callback",
                            "method" to "POST",
                            "body" to """{"event":"test"}""",
                        ))
                    )
                    .build()
            )

            val directServer = config.directCallHttpServer(factory)
            val wireMockRequest = ImmutableRequest.create()
                .withAbsoluteUrl("http://mocknest.internal/test-webhook")
                .withMethod(RequestMethod.POST)
                .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                .withBody(ByteArray(0))
                .build()
            directServer.stubRequest(wireMockRequest)

            // Wait for async webhook processing
            Thread.sleep(1000)

            assertTrue(publishCalled.get(), "Expected sqsPublisher.publish() to be called")
        } finally {
            server.stop()
        }
    }
}
