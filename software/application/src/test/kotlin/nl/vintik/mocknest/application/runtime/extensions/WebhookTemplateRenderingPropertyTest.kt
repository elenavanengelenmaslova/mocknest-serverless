package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.extension.Parameters
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
import kotlin.test.assertEquals

/**
 * Property 3: Template rendering fidelity
 *
 * For any triggering HTTP request, and for any webhook definition containing already-rendered
 * values (as WireMock's template engine would produce), the values passed to
 * webhookHttpClient.send() SHALL match the expected rendered values.
 *
 * Note: WireMock's template engine renders values before passing them to the listener.
 * This test verifies that WebhookServeEventListener passes those values through unchanged.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class WebhookTemplateRenderingPropertyTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val mapper = jacksonObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.ALWAYS)
    }

    private val config = WebhookConfig(
        selfUrl = "https://api.example.com/prod",
        sensitiveHeaders = setOf("x-api-key", "authorization"),
        webhookTimeoutMs = 10_000L,
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class WebhookParameters(
        val url: String,
        val method: String,
        val body: String?,
    )

    data class TemplateRenderingScenario(
        val description: String,
        val webhookParameters: WebhookParameters,
        val expectedSentValues: WebhookParameters,
    )

    companion object {
        @JvmStatic
        fun templateScenarios(): Stream<String> = Stream.of(
            "01-simple-body.json",
            "02-rendered-order-id.json",
            "03-rendered-correlation-id.json",
            "04-get-method.json",
            "05-put-method.json",
            "06-self-url-placeholder.json",
            "07-complex-json-body.json",
            "08-empty-body.json",
            "09-url-with-path-params.json",
            "10-delete-method.json",
            "11-xml-body.json",
            "12-url-with-query-params.json",
        )
    }

    private fun loadScenario(filename: String): TemplateRenderingScenario {
        val resource = this::class.java.getResource("/test-data/webhook/template-rendering/$filename")
            ?: throw IllegalArgumentException("Test data not found: $filename")
        return mapper.readValue(resource.readText())
    }

    private fun buildServeEvent(
        params: WebhookParameters,
        id: UUID = UUID.randomUUID(),
    ): ServeEvent {
        val request = mockk<LoggedRequest>(relaxed = true)
        every { request.headers } returns HttpHeaders.noHeaders()
        every { request.getHeader(any()) } returns null

        val paramsMap = mutableMapOf<String, Any>(
            "url" to params.url,
            "method" to params.method,
        )
        params.body?.let { paramsMap["body"] = it }

        val wiremockParams = Parameters.from(paramsMap)
        val listenerDef = mockk<ServeEventListenerDefinition>()
        every { listenerDef.name } returns "mocknest-webhook"
        every { listenerDef.parameters } returns wiremockParams

        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns id
        every { serveEvent.request } returns request
        every { serveEvent.serveEventListeners } returns listOf(listenerDef)
        return serveEvent
    }

    @ParameterizedTest(name = "Property 3 — Template rendering fidelity: {0}")
    @MethodSource("templateScenarios")
    fun `Given already-rendered webhook parameters When beforeResponseSent called Then values are passed to client unchanged`(filename: String) {
        val scenario = loadScenario(filename)
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val serveEvent = buildServeEvent(scenario.webhookParameters)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        listener.beforeResponseSent(serveEvent, Parameters.empty())

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }

        assertEquals(
            scenario.expectedSentValues.url,
            slot.captured.url,
            "URL should match expected rendered value",
        )
        assertEquals(
            scenario.expectedSentValues.method,
            slot.captured.method,
            "Method should match expected rendered value",
        )
        assertEquals(
            scenario.expectedSentValues.body,
            slot.captured.body,
            "Body should match expected rendered value",
        )
    }
}
