package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.infra.aws.MockNestApplication
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Spring integration test for the `async` profile.
 *
 * Verifies two things:
 *
 * 1. Context isolation — the `async` profile loads WITHOUT any WireMock, S3, or
 *    DirectCallHttpServer beans. No @MockitoBean stubs are used; if any excluded
 *    component leaks through, the context will fail to start and the test will fail.
 *
 * 2. End-to-end dispatch — [RuntimeAsyncHandler] correctly processes an SQS event
 *    and makes the outbound HTTP call to a real [MockWebServer], proving the full
 *    wiring from Spring context → handler → HTTP client works.
 *
 * The only test override is [SqsPublisherInterface] (not needed by RuntimeAsync but
 * present in the classpath scan) — replaced with a no-op stub.
 */
@SpringBootTest(
    classes = [MockNestApplication::class, RuntimeAsyncSpringContextTest.TestConfig::class],
    webEnvironment = NONE,
)
@ActiveProfiles("async")
class RuntimeAsyncSpringContextTest {

    /**
     * Minimal test config — only overrides SqsPublisherInterface to avoid a real SQS client
     * being created. WebhookConfig is intentionally NOT overridden here; it must come from
     * WebhookInfraConfig to prove that bean is correctly provided in the async profile.
     */
    @TestConfiguration
    class TestConfig {
        @Bean
        fun sqsPublisher(): SqsPublisherInterface = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                // no-op — SQS publishing is not exercised in the async Lambda context
            }
        }
    }

    companion object {
        private lateinit var callbackServer: MockWebServer

        @BeforeAll
        @JvmStatic
        fun startCallbackServer() {
            callbackServer = MockWebServer()
            callbackServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stopCallbackServer() {
            callbackServer.shutdown()
        }
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var runtimeAsyncHandler: RuntimeAsyncHandler

    // ── Context isolation assertions ──────────────────────────────────────────

    @Test
    fun `Given async profile When context loads Then RuntimeAsyncHandler is present`() {
        assertNotNull(runtimeAsyncHandler)
    }

    @Test
    fun `Given async profile When context loads Then WebhookConfig is provided by WebhookInfraConfig without manual override`() {
        // If WebhookInfraConfig does not provide WebhookConfig, the context would have failed
        // to start (RuntimeAsyncHandler depends on it via WebhookHttpClient). Reaching here
        // proves the bean is correctly wired.
        assertNotNull(applicationContext.getBean("webhookConfig"))
    }

    @Test
    fun `Given async profile When context loads Then WireMock and S3 beans are absent`() {
        assertFalse(applicationContext.containsBean("directCallHttpServerFactory"),
            "directCallHttpServerFactory must be absent — MockNestConfig excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("wiremockFilesBlobStore"),
            "wiremockFilesBlobStore must be absent — MockNestConfig excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("wireMockServer"),
            "wireMockServer must be absent — MockNestConfig excluded by @Profile(!async)")
    }

    @Test
    fun `Given async profile When context loads Then runtime request-handling beans are absent`() {
        assertFalse(applicationContext.containsBean("adminRequestUseCase"),
            "adminRequestUseCase must be absent — excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("clientRequestUseCase"),
            "clientRequestUseCase must be absent — excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("runtimeLambdaHandler"),
            "runtimeLambdaHandler must be absent — excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("runtimePrimingHook"),
            "runtimePrimingHook must be absent — excluded by @Profile(!async)")
    }

    // ── End-to-end dispatch assertion ─────────────────────────────────────────

    @Test
    fun `Given async profile and SQS event with none auth When handler processes event Then outbound HTTP call reaches callback server`() {
        val callbackUrl = callbackServer.url("/webhook-callback").toString()
        callbackServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"received":true}"""))

        val event = AsyncEvent(
            actionType = "webhook",
            url = callbackUrl,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"orderId":"42"}""",
            auth = AsyncEventAuth(type = "none"),
        )
        val sqsRecord = SQSEvent.SQSMessage().apply {
            messageId = "test-msg-1"
            body = Json.encodeToString(AsyncEvent.serializer(), event)
        }
        val sqsEvent = SQSEvent().apply { records = listOf(sqsRecord) }

        runtimeAsyncHandler.handle(sqsEvent)

        // Assert the callback server received exactly one request with the correct payload
        assertEquals(1, callbackServer.requestCount,
            "RuntimeAsyncHandler must make exactly one outbound HTTP call")
        val received = callbackServer.takeRequest()
        assertEquals("POST", received.method)
        assertEquals("/webhook-callback", received.path)
        assertEquals("""{"orderId":"42"}""", received.body.readUtf8())
        assert(received.getHeader("Content-Type")?.startsWith("application/json") == true,
            { "Content-Type header must start with application/json" })
    }
}
