package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import io.mockk.mockk
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.infra.aws.MockNestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Verifies that the `async` Spring profile loads a lightweight context with NO WireMock/S3
 * dependencies — no @MockitoBean stubs needed because all WireMock-dependent components
 * are now guarded with @Profile("!async").
 *
 * Asserts:
 * - [RuntimeAsyncHandler] bean IS present
 * - `directCallHttpServerFactory` is NOT present (MockNestConfig excluded)
 * - `wiremockFilesBlobStore` is NOT present (MockNestConfig excluded)
 * - `adminRequestUseCase` is NOT present (AdminRequestUseCase excluded)
 * - `clientRequestUseCase` is NOT present (ClientRequestUseCase excluded)
 * - `runtimeLambdaHandler` is NOT present (RuntimeLambdaHandler excluded)
 * - `runtimePrimingHook` is NOT present (RuntimePrimingHook excluded)
 */
@SpringBootTest(
    classes = [MockNestApplication::class, RuntimeAsyncSpringContextTest.TestConfig::class],
    webEnvironment = NONE,
)
@ActiveProfiles("async")
class RuntimeAsyncSpringContextTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun webhookConfig(): WebhookConfig = WebhookConfig(
            sensitiveHeaders = setOf("x-api-key", "authorization"),
            webhookTimeoutMs = 10_000L,
            asyncTimeoutMs = 30_000L,
            requestJournalPrefix = "requests/",
        )

        @Bean
        fun sqsPublisher(): SqsPublisherInterface = mockk(relaxed = true)
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var runtimeAsyncHandler: RuntimeAsyncHandler

    @Test
    fun `Given async profile active When Spring context loads Then RuntimeAsyncHandler bean is present`() {
        assertNotNull(runtimeAsyncHandler)
    }

    @Test
    fun `Given async profile active When Spring context loads Then MockNestConfig beans are absent`() {
        assertFalse(applicationContext.containsBean("directCallHttpServerFactory"),
            "directCallHttpServerFactory must not be present — MockNestConfig excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("wiremockFilesBlobStore"),
            "wiremockFilesBlobStore must not be present — MockNestConfig excluded by @Profile(!async)")
    }

    @Test
    fun `Given async profile active When Spring context loads Then WireMock-dependent use cases are absent`() {
        assertFalse(applicationContext.containsBean("adminRequestUseCase"),
            "adminRequestUseCase must not be present — excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("clientRequestUseCase"),
            "clientRequestUseCase must not be present — excluded by @Profile(!async)")
    }

    @Test
    fun `Given async profile active When Spring context loads Then runtime-only infra beans are absent`() {
        assertFalse(applicationContext.containsBean("runtimeLambdaHandler"),
            "runtimeLambdaHandler must not be present — excluded by @Profile(!async)")
        assertFalse(applicationContext.containsBean("runtimePrimingHook"),
            "runtimePrimingHook must not be present — excluded by @Profile(!async)")
    }
}
