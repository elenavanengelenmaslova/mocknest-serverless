package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the AWS SigV4 signing path of [RuntimeAsyncHandler] (auth type `aws_iam`).
 *
 * Static fake credentials are injected via environment variables so
 * [aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider] resolves
 * deterministically and offline (signing is pure crypto — no network calls).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeAsyncHandlerSigningTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)
    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )
    private val handler = RuntimeAsyncHandler(mockHttpClient, webhookConfig, "eu-west-1")

    private val envVars = EnvironmentVariables()

    @BeforeAll
    fun setupAll() {
        // Static creds make DefaultChainCredentialsProvider resolve from env immediately,
        // avoiding any IMDS/profile lookups during tests.
        envVars.set("AWS_ACCESS_KEY_ID", "AKIDEXAMPLE")
        envVars.set("AWS_SECRET_ACCESS_KEY", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
        envVars.set("AWS_DEFAULT_REGION", "eu-west-1")
        envVars.setup()
    }

    @AfterAll
    fun tearDownAll() {
        envVars.teardown()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildSqsEvent(body: String): SQSEvent {
        val record = SQSEvent.SQSMessage().apply {
            messageId = "msg-0"
            this.body = body
        }
        return SQSEvent().apply { records = listOf(record) }
    }

    private fun awsIamEventJson(
        url: String,
        method: String = "POST",
        region: String? = "eu-west-1",
        service: String? = null,
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
            auth = AsyncEventAuth(type = "aws_iam", region = region, service = service),
        )
    )

    @Test
    fun `Given aws_iam auth with explicit region and service When handler invoked Then outbound request carries SigV4 Authorization header`() {
        val json = awsIamEventJson(
            url = "https://abc123.execute-api.eu-west-1.amazonaws.com/prod/hook",
            region = "eu-west-1",
            service = "execute-api",
        )
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(buildSqsEvent(json))

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        val authHeader = slot.captured.headers.keys.firstOrNull { it.equals("Authorization", ignoreCase = true) }
        assertTrue(authHeader != null, "Signed request must include an Authorization header")
        assertTrue(
            slot.captured.headers[authHeader]?.contains("AWS4-HMAC-SHA256") == true,
            "Authorization header must be a SigV4 signature",
        )
    }

    @Test
    fun `Given aws_iam auth with null body When handler invoked Then signing succeeds with empty body`() {
        val json = awsIamEventJson(
            url = "https://abc123.execute-api.eu-west-1.amazonaws.com/prod/hook",
            body = null,
        )
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(buildSqsEvent(json))

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        assertTrue(
            slot.captured.headers.keys.any { it.equals("Authorization", ignoreCase = true) },
            "Signed request must include an Authorization header even with an empty body",
        )
    }

    @ParameterizedTest
    @CsvSource(
        "https://abc.execute-api.eu-west-1.amazonaws.com/hook",
        "https://abc.lambda.eu-west-1.amazonaws.com/hook",
        "https://my-bucket.s3.eu-west-1.amazonaws.com/object",
        "https://example.com/generic-hook",
    )
    fun `Given aws_iam auth with no explicit service When handler invoked Then service is derived from URL and request is signed`(url: String) {
        val json = awsIamEventJson(url = url, service = null)
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(buildSqsEvent(json))

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        assertTrue(
            slot.captured.headers.keys.any { it.equals("Authorization", ignoreCase = true) },
            "Request should be signed using the service derived from the URL",
        )
    }

    @Test
    fun `Given aws_iam auth but invalid HTTP method When signing fails Then handler falls back to unsigned headers`() {
        // An unparseable method makes the signing block throw, exercising the fallback path.
        val originalHeaders = mapOf("Content-Type" to "application/json", "x-trace" to "t-1")
        val json = awsIamEventJson(
            url = "https://abc.execute-api.eu-west-1.amazonaws.com/hook",
            method = "BOGUS_METHOD",
            headers = originalHeaders,
        )
        every { mockHttpClient.send(any()) } returns WebhookResult.Success(200)

        handler.handle(buildSqsEvent(json))

        val slot = slot<WebhookRequest>()
        verify { mockHttpClient.send(capture(slot)) }
        // Fallback returns the original headers unchanged (no Authorization added)
        assertEquals(originalHeaders, slot.captured.headers)
        assertTrue(
            slot.captured.headers.keys.none { it.equals("Authorization", ignoreCase = true) },
            "Fallback path must not add a SigV4 Authorization header",
        )
    }
}
