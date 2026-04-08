package nl.vintik.mocknest.application.runtime.config

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for MockNestConfig webhook wiring.
 *
 * Validates that WebhookServeEventListener is registered and active in the WireMock server
 * by verifying behavioral evidence: a stub with serveEventListeners triggers webhookHttpClient.send().
 *
 * Validates: Requirements 1.1
 */
class MockNestConfigWebhookWiringTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val mockWebhookHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)

    private val config = MockNestConfig()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given Spring context loads When wireMockServer bean created Then WebhookServeEventListener is registered with name mocknest-webhook`() {
        val webhookConfig = WebhookConfig(
            sensitiveHeaders = setOf("x-api-key", "authorization"),
            webhookTimeoutMs = 10_000L,
        )
        val factory = config.directCallHttpServerFactory()
        val server = config.wireMockServer(factory, mockStorage, webhookConfig, mockWebhookHttpClient)

        try {
            // Register a stub with serveEventListeners using the "mocknest-webhook" name
            server.addStubMapping(
                post(urlPathEqualTo("/test-webhook"))
                    .willReturn(aResponse().withStatus(200))
                    .withServeEventListener(
                        "mocknest-webhook",
                        Parameters.from(
                            mapOf(
                                "url" to "https://api.example.com/callback",
                                "method" to "POST",
                            )
                        )
                    )
                    .build()
            )

            every { mockWebhookHttpClient.send(any()) } returns WebhookResult.Success(200)

            // Fire a request via DirectCallHttpServer
            val directServer = config.directCallHttpServer(factory)
            val wireMockRequest = ImmutableRequest.create()
                .withAbsoluteUrl("http://mocknest.internal/test-webhook")
                .withMethod(RequestMethod.POST)
                .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                .withBody(ByteArray(0))
                .build()
            directServer.stubRequest(wireMockRequest)

            // Behavioral verification: WebhookServeEventListener fired and called webhookHttpClient.send()
            verify { mockWebhookHttpClient.send(any()) }
        } finally {
            server.stop()
        }
    }
}
