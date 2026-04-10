package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * Bug Condition Exploration Tests — Task 1, Test 1.6
 *
 * These tests PROVE the bug exists on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bug exists.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * Validates: Requirements 1.6
 */
class BugConditionExplorationTest {

    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )

    private fun buildSqsEvent(body: String): SQSEvent {
        val record = SQSEvent.SQSMessage().apply {
            messageId = "msg-test-1"
            this.body = body
        }
        return SQSEvent().apply { records = listOf(record) }
    }

    private fun validAsyncEventJson(url: String = "https://callback.example.com/hook"): String =
        Json.encodeToString(
            AsyncEvent.serializer(),
            AsyncEvent(
                actionType = "webhook",
                url = url,
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"event":"triggered"}""",
                auth = AsyncEventAuth(type = "none"),
            )
        )

    // ── Test 1.6 — Transient failure rethrow ─────────────────────────────────

    /**
     * Bug 1.6: Uniform runCatching swallows transient delivery failures.
     *
     * RuntimeAsyncHandler.handleRecord wraps both JSON parsing AND HTTP dispatch in a
     * single runCatching block. When the HTTP client throws IOException (a transient
     * network failure), the exception is caught, logged, and the method returns normally.
     * SQS does not retry the message, and the event is silently lost.
     *
     * The correct behavior: JSON parse errors (poison-pill) should be swallowed, but
     * transient delivery failures (IOException, HTTP 5xx) should be rethrown so SQS
     * retries the message and eventually routes it to the DLQ.
     *
     * EXPECTED TO FAIL on unfixed code: handle() does NOT throw — exception is swallowed.
     *
     * Validates: Requirements 1.6
     */
    @Test
    fun `Given WebhookHttpClient throws IOException When handleRecord called with valid JSON Then exception propagates`() {
        val throwingHttpClient: WebhookHttpClientInterface = mockk()
        every { throwingHttpClient.send(any<WebhookRequest>()) } throws IOException("Connection refused — simulated transient failure")

        val handler = RuntimeAsyncHandler(throwingHttpClient, webhookConfig, "eu-west-1")
        val sqsEvent = buildSqsEvent(validAsyncEventJson())

        // EXPECTED TO FAIL on unfixed code:
        // handle() does NOT throw — the IOException is swallowed by the uniform runCatching
        assertFailsWith<IOException>(
            message = "IOException should propagate from handleRecord for transient delivery failures — Bug 1.6: uniform runCatching swallows all exceptions"
        ) {
            handler.handle(sqsEvent)
        }
    }
}
