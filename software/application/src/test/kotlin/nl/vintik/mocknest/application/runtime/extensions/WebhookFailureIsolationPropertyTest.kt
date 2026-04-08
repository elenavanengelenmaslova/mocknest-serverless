package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream

/**
 * Property 2: Failure isolation
 *
 * For any mock response paired with a webhook that fails (non-2xx, network error, or timeout),
 * beforeResponseSent() SHALL NOT throw and SHALL return normally.
 *
 * Validates: Requirements 1.3, 7.3
 */
class WebhookFailureIsolationPropertyTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val mapper = jacksonObjectMapper()

    private val config = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization"),
        webhookTimeoutMs = 10_000L,
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    data class FailureScenario(
        val description: String,
        val webhookFailureType: String,
        val statusCode: Int?,
        val message: String,
    )

    companion object {
        @JvmStatic
        fun failureScenarios(): Stream<String> = Stream.of(
            "01-non-2xx-503.json",
            "02-non-2xx-404.json",
            "03-non-2xx-500.json",
            "04-non-2xx-401.json",
            "05-non-2xx-429.json",
            "06-non-2xx-400.json",
            "07-network-error-connection-refused.json",
            "08-network-error-unknown-host.json",
            "09-timeout.json",
            "10-non-2xx-502.json",
            "11-network-error-io-exception.json",
            "12-non-2xx-503-with-body.json",
        )
    }

    private fun loadScenario(filename: String): FailureScenario {
        val resource = this::class.java.getResource("/test-data/webhook/failure-isolation/$filename")
            ?: throw IllegalArgumentException("Test data not found: $filename")
        return mapper.readValue(resource.readText())
    }

    private fun buildServeEventWithWebhookListener(): ServeEvent {
        val httpHeaders = HttpHeaders(listOf(HttpHeader("content-type", "application/json")))
        val request = mockk<LoggedRequest>(relaxed = true)
        every { request.headers } returns httpHeaders
        every { request.getHeader(any()) } returns null

        val params = Parameters.from(mapOf(
            "url" to "https://callback.example.com/hook",
            "method" to "POST",
            "body" to """{"event":"triggered"}""",
        ))
        val listenerDef = mockk<ServeEventListenerDefinition>()
        every { listenerDef.name } returns "mocknest-webhook"
        every { listenerDef.parameters } returns params

        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns UUID.randomUUID()
        every { serveEvent.request } returns request
        every { serveEvent.serveEventListeners } returns listOf(listenerDef)
        return serveEvent
    }

    @ParameterizedTest(name = "Property 2 — Failure isolation: {0}")
    @MethodSource("failureScenarios")
    fun `Given webhook failure When beforeResponseSent called Then no exception is thrown and returns normally`(filename: String) {
        val scenario = loadScenario(filename)
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val serveEvent = buildServeEventWithWebhookListener()

        // Configure mock to return the specified failure
        val failure = WebhookResult.Failure(scenario.statusCode, scenario.message)
        every { mockHttpClient.send(any()) } returns failure

        // Property 2: beforeResponseSent() must not throw regardless of failure type
        listener.beforeResponseSent(serveEvent, Parameters.empty())

        // Verify the client was called (webhook was attempted)
        verify { mockHttpClient.send(any()) }
    }

    @ParameterizedTest(name = "Property 2 — Failure isolation (exception thrown): {0}")
    @MethodSource("failureScenarios")
    fun `Given webhook client throws exception When beforeResponseSent called Then no exception propagates`(filename: String) {
        val scenario = loadScenario(filename)
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val serveEvent = buildServeEventWithWebhookListener()

        // Configure mock to throw an exception (simulates network-level failure)
        every { mockHttpClient.send(any()) } throws RuntimeException(scenario.message)

        // Property 2: must not throw even when client throws
        listener.beforeResponseSent(serveEvent, Parameters.empty())
    }
}
