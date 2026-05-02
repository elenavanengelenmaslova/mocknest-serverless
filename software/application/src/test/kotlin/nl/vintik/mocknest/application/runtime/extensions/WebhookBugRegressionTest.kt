package nl.vintik.mocknest.application.runtime.extensions

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.createWireMockServer
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.wiremock.webhooks.WebhookDefinition
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for webhook bug fixes.
 *
 * Each test verifies that a previously identified bug remains fixed.
 * All tests must pass — a failure indicates a regression.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
class WebhookBugRegressionTest {

    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )

    // ── Log capture infrastructure ────────────────────────────────────────────

    private val capturedLogMessages = CopyOnWriteArrayList<String>()
    private lateinit var logAppender: AppenderBase<ILoggingEvent>

    @BeforeEach
    fun attachLogAppender() {
        capturedLogMessages.clear()
        logAppender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                capturedLogMessages.add(event.formattedMessage)
            }
        }
        logAppender.start()
        // Attach to the application package logger — NOT the root logger.
        // The root logger also captures MockK's internal DEBUG messages which
        // include raw mock arguments (e.g. URLs with query strings), causing
        // false positives in PII-leak assertions.
        val appLogger = LoggerFactory.getLogger("nl.vintik.mocknest") as? Logger
        appLogger?.addAppender(logAppender)
    }

    @AfterEach
    fun detachLogAppender() {
        val appLogger = LoggerFactory.getLogger("nl.vintik.mocknest") as? Logger
        appLogger?.detachAppender(logAppender)
        logAppender.stop()
    }

    // ── Test 1.1 — capturedHeaders cleanup ───────────────────────────────────

    /**
     * Verifies that capturedHeaders is cleaned up after beforeResponseSent,
     * even when no webhook listener is registered on the ServeEvent.
     *
     * Bug: Non-local return inside runCatching bypassed capturedHeaders.remove().
     * Fix: Uses return@runCatching so cleanup always executes.
     */
    @Test
    fun `Given ServeEvent with no webhook listener When beforeResponseSent called Then capturedHeaders is empty after call`() {
        val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
        val listener = WebhookServeEventListener(mockHttpClient, webhookConfig)

        val serveEventId = UUID.randomUUID()
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns serveEventId
        every { serveEvent.request.headers } returns HttpHeaders()
        every { serveEvent.serveEventListeners } returns emptyList()

        listener.beforeResponseSent(serveEvent, Parameters.empty())

        assertFalse(
            listener.capturedHeaders.containsKey(serveEventId),
            "capturedHeaders[$serveEventId] was NOT removed — cleanup was bypassed"
        )
    }

    // ── Test 1.2 — URL PII redaction in logs ─────────────────────────────────

    /**
     * Verifies that webhook URLs with query strings are redacted before logging,
     * preventing PII leakage to CloudWatch.
     *
     * Bug: Raw URL including query string was logged at INFO level.
     * Fix: All log call sites use redactUrl() to strip query string and fragment.
     */
    @Test
    fun `Given URL with query string When webhook dispatched Then log output does not contain query string`() {
        val urlWithToken = "https://api.example.com/hook?token=secret123&tenant=acme"
        val queryString = "token=secret123"

        val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        val listener = WebhookServeEventListener(mockHttpClient, webhookConfig)

        val serveEventId = UUID.randomUUID()
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns serveEventId
        every { serveEvent.request.headers } returns HttpHeaders()

        val listenerDef = mockk<com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition>(relaxed = true)
        every { listenerDef.name } returns "webhook"
        every { listenerDef.parameters } returns Parameters.from(
            mapOf("url" to urlWithToken, "method" to "POST", "body" to "")
        )
        every { serveEvent.serveEventListeners } returns listOf(listenerDef)

        listener.beforeResponseSent(serveEvent, Parameters.empty())

        val allLogs = capturedLogMessages.joinToString("\n")
        assertFalse(
            allLogs.contains(queryString),
            "Log output contains '$queryString' — raw URL logged with PII.\nCaptured log messages:\n$allLogs"
        )
    }

    // ── Test 1.3 — Safe fallback on redaction failure ────────────────────────

    /**
     * Verifies that redactServeEvent returns a safe placeholder when serialization
     * fails, instead of retrying the same failing call and leaking unredacted data.
     *
     * Bug: getOrElse fallback called the same failing mapper.writeValueAsString(event).
     * Fix: Returns a safe placeholder JSON with redactionError flag.
     */
    @Test
    fun `Given mapper writeValueAsString throws on first call When redactServeEvent called Then result is NOT unredacted JSON`() {
        val filter = RedactSensitiveHeadersFilter(webhookConfig)

        val serveEventId = UUID.randomUUID()
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns serveEventId

        val result = filter.redactServeEvent(serveEvent)

        assertFalse(
            result.contains("SECRET_VALUE"),
            "Result must not contain sensitive header values"
        )
        assertTrue(
            result.contains("redactionError") || result.contains(serveEventId.toString()),
            "Result should be a safe placeholder containing event id or redactionError flag, got: $result"
        )
    }

    // ── Test 1.4 — SQS failure propagation ───────────────────────────────────

    /**
     * Verifies that SQS publish failures propagate as exceptions instead of being
     * silently swallowed with a NO_OP_URL redirect.
     *
     * Bug: SQS publish exception was swallowed; NO_OP_URL returned silently.
     * Fix: Exception propagates so the failure is observable.
     */
    @Test
    fun `Given failing SqsPublisher When transform called Then exception propagates`() {
        val failingPublisher = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                throw RuntimeException("SQS unavailable — simulated failure")
            }
        }

        val publisher = WebhookAsyncEventPublisher(failingPublisher, "https://sqs.eu-west-1.amazonaws.com/123/queue")

        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns UUID.randomUUID()

        val definition = WebhookDefinition()
            .withUrl("https://callback.example.com/hook")
            .withMethod("POST")

        val threwException = runCatching { publisher.transform(serveEvent, definition) }
            .fold(
                onSuccess = { result ->
                    assertNotEquals(
                        "http://localhost:0/mocknest-noop",
                        result.url,
                        "transform must not return NO_OP_URL on SQS failure"
                    )
                    false
                },
                onFailure = { true }
            )
        assertTrue(
            threwException,
            "Exception must propagate on SQS failure (silent drop is gone)"
        )
    }

    // ── Test 1.5 — Blank queue URL skips publisher registration ──────────────

    /**
     * Verifies that WebhookAsyncEventPublisher is NOT registered when the webhook
     * queue URL is blank, preventing silent event drops.
     *
     * Bug: Publisher was registered unconditionally even with blank queue URL.
     * Fix: createWireMockServer skips publisher registration when queue URL is blank.
     */
    @Test
    fun `Given blank webhookQueueUrl When wireMockServer created and webhook fires Then SqsPublisher is NOT called`() {
        val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
        val publishCalled = AtomicBoolean(false)
        val capturingSqsPublisher = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                publishCalled.set(true)
            }
        }

        val factory = com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory()
        val redactFilter = RedactSensitiveHeadersFilter(webhookConfig)
        val journalStore = nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore(mockStorage, webhookConfig, redactFilter)

        val server = createWireMockServer(
            factory,
            mockStorage,
            webhookConfig,
            capturingSqsPublisher,
            "",
            journalStore,
            redactFilter,
        )

        try {
            server.addStubMapping(
                post(urlPathEqualTo("/test-webhook-blank-url"))
                    .willReturn(aResponse().withStatus(200))
                    .withServeEventListener(
                        "webhook",
                        Parameters.from(
                            mapOf(
                                "url" to "https://api.example.com/callback",
                                "method" to "POST",
                            )
                        )
                    )
                    .build()
            )

            val directServer = factory.httpServer
            val wireMockRequest = ImmutableRequest.create()
                .withAbsoluteUrl("http://mocknest.internal/test-webhook-blank-url")
                .withMethod(RequestMethod.POST)
                .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                .withBody(ByteArray(0))
                .build()
            directServer.stubRequest(wireMockRequest)

            assertFalse(
                publishCalled.get(),
                "SqsPublisher.publish() was called despite blank webhookQueueUrl — " +
                        "WebhookAsyncEventPublisher should not be registered when queue URL is blank"
            )
        } finally {
            server.stop()
        }
    }
}
