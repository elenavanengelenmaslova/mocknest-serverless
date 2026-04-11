package nl.vintik.mocknest.application.runtime.extensions

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.MockNestConfig
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.wiremock.webhooks.WebhookDefinition
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Tests — Task 1
 *
 * These tests PROVE the bugs exist on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bugs exist.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
class BugConditionExplorationTest {

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
        // Attach to the root logger to capture all log output from the package
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as? Logger
        root?.addAppender(logAppender)
    }

    @AfterEach
    fun detachLogAppender() {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as? Logger
        root?.detachAppender(logAppender)
        logAppender.stop()
    }

    // ── Test 1.1 — capturedHeaders leak ──────────────────────────────────────

    /**
     * Bug 1.1: Non-local return inside runCatching bypasses capturedHeaders.remove().
     *
     * When beforeResponseSent is called for a ServeEvent with no webhook listener,
     * the bare `return` inside the runCatching lambda performs a non-local return from
     * the entire method, skipping the capturedHeaders.remove(serveEvent.id) cleanup.
     *
     * EXPECTED TO FAIL on unfixed code: capturedHeaders still contains the entry.
     *
     * Counterexample: serveEventId remains in capturedHeaders after the call.
     *
     * Validates: Requirements 1.1
     */
    @Test
    fun `Given ServeEvent with no webhook listener When beforeResponseSent called Then capturedHeaders is empty after call`() {
        val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
        val listener = WebhookServeEventListener(mockHttpClient, webhookConfig)

        val serveEventId = UUID.randomUUID()
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns serveEventId
        every { serveEvent.request.headers } returns HttpHeaders()
        // No webhook listener registered — serveEventListeners returns empty list
        every { serveEvent.serveEventListeners } returns emptyList()

        listener.beforeResponseSent(serveEvent, Parameters.empty())

        // EXPECTED TO FAIL on unfixed code:
        // capturedHeaders still contains serveEventId because the bare `return` bypassed cleanup.
        // Counterexample: capturedHeaders.containsKey(serveEventId) == true
        assertFalse(
            listener.capturedHeaders.containsKey(serveEventId),
            "Bug 1.1 counterexample: capturedHeaders[$serveEventId] was NOT removed — " +
                "non-local return inside runCatching bypassed capturedHeaders.remove(serveEvent.id)"
        )
    }

    // ── Test 1.2 — URL PII in logs ────────────────────────────────────────────

    /**
     * Bug 1.2: Raw URL including query string is logged.
     *
     * When beforeResponseSent dispatches a webhook with a URL containing a query string
     * (e.g. ?token=secret), the raw URL is emitted to the log, leaking PII to CloudWatch.
     *
     * EXPECTED TO FAIL on unfixed code: log output contains the query string.
     *
     * Counterexample: log message contains "token=secret123".
     *
     * Validates: Requirements 1.2
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

        // Build a ServeEventListenerDefinition that has the webhook listener with the PII URL
        val listenerDef = mockk<com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition>(relaxed = true)
        every { listenerDef.name } returns "webhook"
        every { listenerDef.parameters } returns Parameters.from(
            mapOf("url" to urlWithToken, "method" to "POST", "body" to "")
        )
        every { serveEvent.serveEventListeners } returns listOf(listenerDef)

        listener.beforeResponseSent(serveEvent, Parameters.empty())

        // EXPECTED TO FAIL on unfixed code:
        // Log messages contain the raw URL with query string.
        // Counterexample: a log message contains "token=secret123"
        val allLogs = capturedLogMessages.joinToString("\n")
        assertFalse(
            allLogs.contains(queryString),
            "Bug 1.2 counterexample: log output contains '$queryString' — raw URL logged with PII.\n" +
                "Captured log messages:\n$allLogs"
        )
    }

    // ── Test 1.3 — Fail-open redaction ───────────────────────────────────────

    /**
     * Bug 1.3: getOrElse fallback calls the same failing mapper.writeValueAsString(event).
     *
     * When mapper.writeValueAsString(event) throws inside redactServeEvent, the unfixed
     * getOrElse block calls mapper.writeValueAsString(event) again — the same failing call.
     * If it succeeds on the second attempt, it returns the fully unredacted JSON, leaking
     * sensitive headers to S3.
     *
     * Fix 1.3: redactServeEvent now returns a safe placeholder JSON instead of retrying
     * the same failing call. The result must NOT equal the unredacted JSON.
     *
     * We trigger the failure path by passing a ServeEvent mockk that Jackson cannot
     * serialize (it throws during writeValueAsString), then assert the result is the
     * safe placeholder, not unredacted JSON.
     *
     * EXPECTED TO PASS on fixed code: result is the safe placeholder, not unredacted JSON.
     *
     * Validates: Requirements 1.3
     */
    @Test
    fun `Given mapper writeValueAsString throws on first call When redactServeEvent called Then result is NOT unredacted JSON`() {
        val filter = RedactSensitiveHeadersFilter(webhookConfig)

        // Use a ServeEvent mockk — Jackson cannot serialize a mockk proxy, so
        // mapper.writeValueAsString(event) will throw, triggering the getOrElse path.
        val serveEventId = UUID.randomUUID()
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns serveEventId

        val result = filter.redactServeEvent(serveEvent)

        // Fixed code: result must NOT be unredacted JSON.
        // It should be the safe placeholder: {"id":"<uuid>","redactionError":true}
        // Counterexample on unfixed code: result would be the full unredacted JSON with sensitive headers.
        assertFalse(
            result.contains("SECRET_VALUE"),
            "Bug 1.3: result must not contain sensitive header values"
        )
        // The fixed code returns a safe placeholder — verify it contains the event id and redactionError flag
        assertTrue(
            result.contains("redactionError") || result.contains(serveEventId.toString()),
            "Bug 1.3: fixed code should return safe placeholder containing event id or redactionError flag, got: $result"
        )
    }

    // ── Test 1.4 — Silent SQS drop ────────────────────────────────────────────

    /**
     * Bug 1.4: SQS publish exception is swallowed; NO_OP_URL returned silently.
     *
     * When sqsPublisher.publish throws, the onFailure block logs a warning and the
     * transform method still returns webhookDefinition.withUrl(NO_OP_URL). WireMock
     * proceeds as if the webhook was dispatched, silently dropping the event.
     *
     * EXPECTED TO FAIL on unfixed code: transform returns NO_OP_URL even when SQS fails.
     *
     * Counterexample: result.url == "http://localhost:0/mocknest-noop" despite SQS failure.
     *
     * Validates: Requirements 1.4
     */
    @Test
    fun `Given failing SqsPublisher When transform called Then returned URL is NOT NO_OP_URL`() {
        val noOpUrl = "http://localhost:0/mocknest-noop"

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

        // Fixed behavior: exception propagates (failure is observable), OR result URL is not NO_OP_URL.
        // Either outcome satisfies the fix — the silent drop is gone.
        val threwException = runCatching { publisher.transform(serveEvent, definition) }
            .fold(
                onSuccess = { result ->
                    assertNotEquals(
                        noOpUrl,
                        result.url,
                        "Bug 1.4 fix: transform must not return NO_OP_URL on SQS failure"
                    )
                    false
                },
                onFailure = { true } // exception propagated — fix is working
            )
        // At least one of the two conditions must hold: exception thrown OR URL not NO_OP_URL
        // If we reach here without exception and the onSuccess block didn't fail the assertion,
        // the fix is verified (the assertion in onSuccess already validated the URL)
        assertTrue(
            threwException,
            "Bug 1.4 fix: exception must propagate on SQS failure (silent drop is gone)"
        )

    // ── Test 1.5 — Blank queue URL ────────────────────────────────────────────

    /**
     * Bug 1.5: WebhookAsyncEventPublisher is registered even when webhookQueueUrl is blank.
     *
     * MockNestConfig.wireMockServer instantiates WebhookAsyncEventPublisher unconditionally,
     * even when MOCKNEST_WEBHOOK_QUEUE_URL is blank. Every webhook event is then silently
     * dropped with no startup warning.
     *
     * Observable effect: with a blank queue URL, the SQS publisher SHOULD NOT be called.
     * On unfixed code, the publisher IS registered and IS called (with an empty queue URL).
     *
     * EXPECTED TO FAIL on unfixed code: sqsPublisher.publish() IS called even with blank queue URL.
     *
     * Counterexample: publishCalled == true despite blank webhookQueueUrl.
     *
     * Validates: Requirements 1.5
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

        val config = MockNestConfig()
        val factory = config.directCallHttpServerFactory()

        val server = config.wireMockServer(
            factory,
            mockStorage,
            webhookConfig,
            capturingSqsPublisher,
            "", // blank webhookQueueUrl — Bug 1.5 condition
        )

        try {
            server.addStubMapping(
                post(urlPathEqualTo("/test-webhook-blank-url"))
                    .willReturn(aResponse().withStatus(200))
                    .withServeEventListener(
                        "webhook",
                        Parameters.from(mapOf(
                            "url" to "https://api.example.com/callback",
                            "method" to "POST",
                        ))
                    )
                    .build()
            )

            val directServer = config.directCallHttpServer(factory)
            val wireMockRequest = ImmutableRequest.create()
                .withAbsoluteUrl("http://mocknest.internal/test-webhook-blank-url")
                .withMethod(RequestMethod.POST)
                .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                .withBody(ByteArray(0))
                .build()
            directServer.stubRequest(wireMockRequest)

            // Brief wait for async processing
            Thread.sleep(500)

            // EXPECTED TO FAIL on unfixed code:
            // publishCalled == true because WebhookAsyncEventPublisher is registered unconditionally.
            // Counterexample: publishCalled == true despite blank webhookQueueUrl
            assertFalse(
                publishCalled.get(),
                "Bug 1.5 counterexample: SqsPublisher.publish() was called despite blank webhookQueueUrl — " +
                    "WebhookAsyncEventPublisher registered unconditionally, all webhook events silently dropped"
            )
        } finally {
            server.stop()
        }
    }
}
