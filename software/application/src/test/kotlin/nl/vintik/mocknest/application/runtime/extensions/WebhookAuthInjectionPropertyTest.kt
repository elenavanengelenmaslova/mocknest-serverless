package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Property 4: Auth header injection from original request
 *
 * For any webhook configured with auth.type="header" and auth.value.source="original_request_header",
 * the outbound webhook request SHALL contain the header named by auth.inject.name with the value
 * of the header named by auth.value.headerName from the incoming trigger request, and that value
 * SHALL NOT appear in any log line.
 *
 * Validates: Requirements 3.4, 3.8, 6.1
 */
class WebhookAuthInjectionPropertyTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val mapper = jacksonObjectMapper()
    private val capturedLogMessages = CopyOnWriteArrayList<String>()

    private lateinit var testAppender: TestLogAppender

    @BeforeEach
    fun setUp() {
        capturedLogMessages.clear()
        testAppender = TestLogAppender(capturedLogMessages)
        testAppender.start()
        // Attach to the WebhookServeEventListener logger
        val logger = LoggerFactory.getLogger("nl.vintik.mocknest.application.runtime.extensions") as? Logger
        logger?.addAppender(testAppender)
    }

    @AfterEach
    fun tearDown() {
        val logger = LoggerFactory.getLogger("nl.vintik.mocknest.application.runtime.extensions") as? Logger
        logger?.detachAppender(testAppender)
        testAppender.stop()
        clearAllMocks()
    }

    class TestLogAppender(
        private val messages: CopyOnWriteArrayList<String>,
    ) : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }

    data class AuthInjectionScenario(
        val description: String,
        val incomingHeaderName: String,
        val incomingHeaderValue: String,
        val injectName: String,
        val expectedOutboundHeaderValue: String,
    )

    companion object {
        @JvmStatic
        fun authInjectionScenarios(): Stream<String> = Stream.of(
            "01-x-api-key-to-x-api-key.json",
            "02-authorization-to-authorization.json",
            "03-x-api-key-to-different-name.json",
            "04-short-value.json",
            "05-uuid-value.json",
            "06-long-jwt-value.json",
            "07-special-chars-value.json",
            "08-basic-auth-value.json",
            "09-custom-header-name.json",
            "10-alphanumeric-key.json",
        )
    }

    private fun loadScenario(filename: String): AuthInjectionScenario {
        val resource = this::class.java.getResource("/test-data/webhook/auth-injection/$filename")
            ?: throw IllegalArgumentException("Test data not found: $filename")
        return mapper.readValue(resource.readText())
    }

    private fun buildServeEvent(
        incomingHeaderName: String,
        incomingHeaderValue: String,
        injectName: String,
        id: UUID = UUID.randomUUID(),
    ): ServeEvent {
        val httpHeaders = HttpHeaders(listOf(HttpHeader(incomingHeaderName, incomingHeaderValue)))
        val request = mockk<LoggedRequest>(relaxed = true)
        every { request.headers } returns httpHeaders
        every { request.getHeader(any()) } answers {
            val name = firstArg<String>()
            if (name.equals(incomingHeaderName, ignoreCase = true)) incomingHeaderValue else null
        }

        val params = Parameters.from(mapOf(
            "url" to "https://callback.example.com/hook",
            "method" to "POST",
            "body" to """{"event":"triggered"}""",
            "auth" to mapOf(
                "type" to "header",
                "inject" to mapOf("name" to injectName),
                "value" to mapOf(
                    "source" to "original_request_header",
                    "headerName" to incomingHeaderName,
                ),
            ),
        ))
        val listenerDef = mockk<ServeEventListenerDefinition>()
        every { listenerDef.name } returns "mocknest-webhook"
        every { listenerDef.parameters } returns params

        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns id
        every { serveEvent.request } returns request
        every { serveEvent.serveEventListeners } returns listOf(listenerDef)
        return serveEvent
    }

    @ParameterizedTest(name = "Property 4 — Auth injection: {0}")
    @MethodSource("authInjectionScenarios")
    fun `Given original_request_header auth config When beforeResponseSent called Then header is injected with correct value`(filename: String) {
        val scenario = loadScenario(filename)
        val config = WebhookConfig(
            selfUrl = "https://api.example.com/prod",
            sensitiveHeaders = setOf(scenario.incomingHeaderName.lowercase()),
            webhookTimeoutMs = 10_000L,
        )
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val id = UUID.randomUUID()
        val serveEvent = buildServeEvent(
            incomingHeaderName = scenario.incomingHeaderName,
            incomingHeaderValue = scenario.incomingHeaderValue,
            injectName = scenario.injectName,
            id = id,
        )

        // Capture the header in afterMatch
        listener.afterMatch(serveEvent, Parameters.empty())

        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
        listener.beforeResponseSent(serveEvent, Parameters.empty())

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }

        // Property 4a: outbound request contains the injected header with correct value
        val outboundValue = slot.captured.headers[scenario.injectName]
        assertNotNull(outboundValue, "Outbound header '${scenario.injectName}' should be present")
        assertEquals(scenario.expectedOutboundHeaderValue, outboundValue)
    }

    @ParameterizedTest(name = "Property 4 — Auth value not in logs: {0}")
    @MethodSource("authInjectionScenarios")
    fun `Given sensitive auth header value When beforeResponseSent called Then value does not appear in any log output`(filename: String) {
        val scenario = loadScenario(filename)
        val config = WebhookConfig(
            selfUrl = "https://api.example.com/prod",
            sensitiveHeaders = setOf(scenario.incomingHeaderName.lowercase()),
            webhookTimeoutMs = 10_000L,
        )
        val listener = WebhookServeEventListener(mockHttpClient, config)
        val id = UUID.randomUUID()
        val serveEvent = buildServeEvent(
            incomingHeaderName = scenario.incomingHeaderName,
            incomingHeaderValue = scenario.incomingHeaderValue,
            injectName = scenario.injectName,
            id = id,
        )

        capturedLogMessages.clear()
        listener.afterMatch(serveEvent, Parameters.empty())

        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
        listener.beforeResponseSent(serveEvent, Parameters.empty())

        // Property 4b: the sensitive value must NOT appear in any log line
        val sensitiveValue = scenario.incomingHeaderValue
        val loggedMessages = capturedLogMessages.toList()
        for (message in loggedMessages) {
            assertFalse(
                message.contains(sensitiveValue),
                "Sensitive value should not appear in log message: '$message'",
            )
        }
    }
}
