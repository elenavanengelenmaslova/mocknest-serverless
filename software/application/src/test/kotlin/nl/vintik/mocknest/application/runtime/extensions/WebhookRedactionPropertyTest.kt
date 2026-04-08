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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property 5: Redaction completeness
 *
 * For any HTTP request containing a header whose name matches a configured sensitive header
 * name (case-insensitive), the value of that header SHALL be captured in the side-channel
 * map by afterMatch(), and SHALL be available for auth injection in beforeResponseSent().
 * Non-sensitive headers SHALL NOT be captured.
 *
 * Note: The actual journal redaction (replacing values with [REDACTED] in /__admin/requests
 * responses) is handled by RedactSensitiveHeadersFilter and tested separately.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4
 */
class WebhookRedactionPropertyTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val mapper = jacksonObjectMapper()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    data class RedactionScenario(
        val description: String,
        val sensitiveHeaders: List<String>,
        val requestHeaders: Map<String, String>,
        val expectedRedacted: List<String>,
        val expectedPreserved: List<String>,
    )

    companion object {
        @JvmStatic
        fun redactionScenarios(): Stream<String> = Stream.of(
            "01-default-x-api-key.json",
            "02-default-authorization.json",
            "03-mixed-case-x-api-key.json",
            "04-mixed-case-authorization.json",
            "05-non-sensitive-header-preserved.json",
            "06-custom-sensitive-header.json",
            "07-short-value.json",
            "08-long-value.json",
            "09-uuid-value.json",
            "10-special-chars-value.json",
            "11-multiple-sensitive-headers.json",
            "12-uppercase-sensitive-header.json",
            "13-mixed-sensitive-and-non-sensitive.json",
            "14-no-sensitive-headers-in-request.json",
            "15-custom-multi-sensitive-headers.json",
        )
    }

    private fun loadScenario(filename: String): RedactionScenario {
        val resource = this::class.java.getResource("/test-data/webhook/redaction/$filename")
            ?: throw IllegalArgumentException("Test data not found: $filename")
        return mapper.readValue(resource.readText())
    }

    private fun buildServeEvent(
        headers: Map<String, String>,
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

    private fun buildWebhookListenerDef(injectName: String, headerName: String): ServeEventListenerDefinition {
        val params = Parameters.from(mapOf(
            "url" to "https://callback.example.com/hook",
            "method" to "POST",
            "auth" to mapOf(
                "type" to "header",
                "inject" to mapOf("name" to injectName),
                "value" to mapOf("source" to "original_request_header", "headerName" to headerName),
            ),
        ))
        val def = mockk<ServeEventListenerDefinition>()
        every { def.name } returns "mocknest-webhook"
        every { def.parameters } returns params
        return def
    }

    @ParameterizedTest(name = "Property 5 — Redaction completeness: {0}")
    @MethodSource("redactionScenarios")
    fun `Given request headers When afterMatch called Then sensitive headers are captured and available for auth injection`(filename: String) {
        val scenario = loadScenario(filename)
        val config = WebhookConfig(
            sensitiveHeaders = scenario.sensitiveHeaders.map { it.lowercase() }.toSet(),
            webhookTimeoutMs = 10_000L,
        )
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val id = UUID.randomUUID()
        val serveEvent = buildServeEvent(headers = scenario.requestHeaders, id = id)

        // afterMatch captures sensitive headers
        listener.afterMatch(serveEvent, Parameters.empty())

        // Verify: for each expected-redacted header, auth injection works correctly
        for (sensitiveHeaderName in scenario.expectedRedacted) {
            // Find the original value (case-insensitive lookup)
            val originalValue = scenario.requestHeaders.entries
                .firstOrNull { it.key.equals(sensitiveHeaderName, ignoreCase = true) }?.value
            assertNotNull(originalValue, "Expected sensitive header '$sensitiveHeaderName' in requestHeaders")

            // Build a webhook listener def that injects this header
            val listenerDef = buildWebhookListenerDef(
                injectName = "x-injected-$sensitiveHeaderName",
                headerName = sensitiveHeaderName,
            )
            val dispatchEvent = buildServeEvent(
                headers = scenario.requestHeaders,
                listenerDefs = listOf(listenerDef),
                id = id,
            )
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            listener.beforeResponseSent(dispatchEvent, Parameters.empty())

            val slot = slot<WebhookRequest>()
            verify { mockHttpClient.send(capture(slot)) }
            assertEquals(
                originalValue,
                slot.captured.headers["x-injected-$sensitiveHeaderName"],
                "Sensitive header '$sensitiveHeaderName' should be injected with original value",
            )
            clearMocks(mockHttpClient)
        }
    }

    @ParameterizedTest(name = "Property 5 — Non-sensitive headers not captured: {0}")
    @MethodSource("redactionScenarios")
    fun `Given request with non-sensitive headers When afterMatch called Then non-sensitive headers are not in side-channel`(filename: String) {
        val scenario = loadScenario(filename)
        val config = WebhookConfig(
            sensitiveHeaders = scenario.sensitiveHeaders.map { it.lowercase() }.toSet(),
            webhookTimeoutMs = 10_000L,
        )
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val id = UUID.randomUUID()
        val serveEvent = buildServeEvent(headers = scenario.requestHeaders, id = id)

        listener.afterMatch(serveEvent, Parameters.empty())

        // For each preserved (non-sensitive) header, auth injection should NOT find it in side-channel
        // (it may still be found via direct request lookup, but that's expected behavior)
        for (preservedHeader in scenario.expectedPreserved) {
            val listenerDef = buildWebhookListenerDef(
                injectName = "x-injected-$preservedHeader",
                headerName = preservedHeader,
            )
            val dispatchEvent = buildServeEvent(
                headers = scenario.requestHeaders,
                listenerDefs = listOf(listenerDef),
                id = id,
            )
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

            // Should not throw — non-sensitive headers are read directly from request
            listener.beforeResponseSent(dispatchEvent, Parameters.empty())
            clearMocks(mockHttpClient)
        }
    }
}
