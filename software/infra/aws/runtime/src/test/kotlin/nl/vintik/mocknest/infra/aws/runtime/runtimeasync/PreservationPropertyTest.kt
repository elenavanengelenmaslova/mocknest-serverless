package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Preservation Property Tests — Task 2 (infra/runtime module)
 *
 * These tests verify that non-buggy inputs produce the SAME observable behavior
 * before and after the fix. They MUST PASS on UNFIXED code to confirm the baseline.
 *
 * Property 7: For all X where none of the six bug conditions hold,
 *   F(X) = F'(X) — same HTTP dispatch outcome for well-formed events,
 *   same poison-pill skip behavior for malformed JSON.
 *
 * Validates: Requirements 3.4, 3.5
 */
class PreservationPropertyTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )
    private val handler = RuntimeAsyncHandler(mockHttpClient, webhookConfig, "eu-west-1")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildSqsEvent(vararg bodies: String): SQSEvent {
        val records = bodies.mapIndexed { i, body ->
            SQSEvent.SQSMessage().apply {
                messageId = "msg-preservation-$i"
                this.body = body
            }
        }
        return SQSEvent().apply { this.records = records }
    }

    private fun validAsyncEventJson(
        url: String = "https://callback.example.com/hook",
        method: String = "POST",
        headers: Map<String, String> = mapOf("Content-Type" to "application/json"),
        body: String? = """{"event":"triggered"}""",
    ): String = Json.encodeToString(
        AsyncEvent.serializer(),
        AsyncEvent(
            actionType = "webhook",
            url = url,
            method = method,
            headers = headers,
            body = body,
            auth = AsyncEventAuth(type = "none"),
        )
    )

    // ── Requirement 3.4 — Well-formed SQS message dispatches without error ────

    /**
     * Preservation 3.4: RuntimeAsyncHandler receiving a well-formed SQS message MUST
     * continue to deserialise and dispatch the webhook without error.
     *
     * Non-buggy input: valid JSON, HTTP client returns success (no transient failure).
     * This is NOT isTransientDeliveryFailureCondition — the HTTP call succeeds.
     *
     * Validates: Requirements 3.4
     */
    @Nested
    inner class WellFormedEventPreservation {

        @ParameterizedTest(name = "url={0}")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.runtimeasync.PreservationPropertyTest#validCallbackUrls")
        fun `Given well-formed AsyncEvent When handleRecord called Then webhook is dispatched without error`(
            callbackUrl: String,
        ) {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(validAsyncEventJson(url = callbackUrl))

            // Must not throw — Preservation 3.4
            handler.handle(sqsEvent)

            verify(exactly = 1) { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given well-formed AsyncEvent with POST method When handleRecord called Then HTTP client is invoked`() {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(validAsyncEventJson(method = "POST"))

            handler.handle(sqsEvent)

            verify(exactly = 1) { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given well-formed AsyncEvent with null body When handleRecord called Then dispatches without error`() {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(validAsyncEventJson(body = null))

            handler.handle(sqsEvent)

            verify(exactly = 1) { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given well-formed AsyncEvent and HTTP 200 response When handleRecord called Then no exception thrown`() {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(validAsyncEventJson())

            // Must not throw
            handler.handle(sqsEvent)
        }

        @Test
        fun `Given well-formed AsyncEvent and HTTP 4xx response When handleRecord called Then no exception thrown`() {
            // 4xx is a WebhookResult.Failure — not a transient failure, should not rethrow
            every { mockHttpClient.send(any()) } returns WebhookResult.Failure(404, "Not Found")
            val sqsEvent = buildSqsEvent(validAsyncEventJson())

            // Must not throw — 4xx is a permanent failure, not a transient one
            handler.handle(sqsEvent)
        }

        @Test
        fun `Given multiple well-formed SQS records When handle called Then each record is dispatched`() {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(
                validAsyncEventJson(url = "https://callback1.example.com/hook"),
                validAsyncEventJson(url = "https://callback2.example.com/hook"),
                validAsyncEventJson(url = "https://callback3.example.com/hook"),
            )

            handler.handle(sqsEvent)

            verify(exactly = 3) { mockHttpClient.send(any()) }
        }
    }

    // ── Requirement 3.5 — Malformed JSON (poison-pill) is skipped ────────────

    /**
     * Preservation 3.5: RuntimeAsyncHandler receiving a malformed JSON body (poison pill)
     * MUST continue to log the error and skip the message without rethrowing.
     *
     * Non-buggy input: malformed JSON that cannot be deserialised.
     * This is NOT isTransientDeliveryFailureCondition — JSON parsing fails, not HTTP.
     *
     * Validates: Requirements 3.5
     */
    @Nested
    inner class PoisonPillPreservation {

        @ParameterizedTest(name = "body={0}")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.runtimeasync.PreservationPropertyTest#malformedJsonBodies")
        fun `Given malformed JSON SQS record When handleRecord called Then no exception thrown and HTTP client not called`(
            malformedBody: String,
        ) {
            val sqsEvent = buildSqsEvent(malformedBody)

            // Must not throw — poison-pill skip (Preservation 3.5)
            handler.handle(sqsEvent)

            verify(exactly = 0) { mockHttpClient.send(any()) }
        }

        @Test
        fun `Given mix of valid and malformed SQS records When handle called Then valid records dispatched and malformed skipped`() {
            every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)
            val sqsEvent = buildSqsEvent(
                validAsyncEventJson(url = "https://callback1.example.com/hook"),
                "not-valid-json{{{",
                validAsyncEventJson(url = "https://callback2.example.com/hook"),
            )

            // Must not throw — malformed record is skipped
            handler.handle(sqsEvent)

            // Only the two valid records should be dispatched
            verify(exactly = 2) { mockHttpClient.send(any()) }
        }
    }

    companion object {
        @JvmStatic
        fun validCallbackUrls() = listOf(
            "https://callback.example.com/hook",
            "https://api.example.com/webhook",
            "https://service.internal/events",
            "http://localhost:8080/callback",
            "https://hooks.example.org/v1/events",
        )

        @JvmStatic
        fun malformedJsonBodies() = listOf(
            "not-valid-json{{{",
            "",
            "null",
            "[]",
            """{"actionType":"webhook"}""", // missing required fields
            "plain text",
            "{{{broken",
        )
    }
}
