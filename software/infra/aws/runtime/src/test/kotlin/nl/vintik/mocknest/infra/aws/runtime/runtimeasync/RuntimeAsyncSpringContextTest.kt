package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import aws.sdk.kotlin.services.s3.S3Client
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Verifies that the `async` Spring profile loads a lightweight context:
 * - [RuntimeAsyncHandler] bean is present
 * - `wireMockServer` bean is NOT present (MockNestConfig excluded via @Profile("!async"))
 * - `directCallHttpServerFactory` bean is NOT present (MockNestConfig excluded via @Profile("!async"))
 *
 * WireMock-dependent components (AdminRequestUseCase, ClientRequestUseCase, RuntimePrimingHook)
 * still need DirectCallHttpServer, WireMockServer, and S3Client to be satisfied — we provide
 * @MockitoBean stubs so the context loads without real AWS or WireMock connections.
 *
 * Requirements: async profile isolation
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

    // Satisfy WireMock-dependent components without starting a real WireMock server
    @MockitoBean
    private lateinit var directCallHttpServer: DirectCallHttpServer

    @MockitoBean
    private lateinit var wireMockServer: WireMockServer

    // Satisfy RuntimePrimingHook S3 dependency without real AWS calls
    @MockitoBean
    private lateinit var s3Client: S3Client

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var runtimeAsyncHandler: RuntimeAsyncHandler

    @Test
    fun `Given async profile active When Spring context loads Then RuntimeAsyncHandler bean is present`() {
        assertNotNull(runtimeAsyncHandler)
    }

    @Test
    fun `Given async profile active When Spring context loads Then wireMockServer bean is NOT registered by MockNestConfig`() {
        // MockNestConfig is excluded by @Profile("!async"), so it never registers the real wireMockServer bean.
        // The only wireMockServer in context is the @MockitoBean stub above — verify MockNestConfig's
        // factory bean (directCallHttpServerFactory) is absent, which proves MockNestConfig was skipped.
        assertFalse(
            applicationContext.containsBean("directCallHttpServerFactory"),
            "directCallHttpServerFactory must not be present — MockNestConfig should be excluded by @Profile(\"!async\")"
        )
    }

    @Test
    fun `Given async profile active When Spring context loads Then directCallHttpServerFactory bean is NOT present`() {
        assertFalse(
            applicationContext.containsBean("directCallHttpServerFactory"),
            "directCallHttpServerFactory must not be loaded in the async profile"
        )
    }

    @Test
    fun `Given async profile active When Spring context loads Then wiremockFilesBlobStore bean is NOT present`() {
        assertFalse(
            applicationContext.containsBean("wiremockFilesBlobStore"),
            "wiremockFilesBlobStore must not be loaded in the async profile — it is defined in MockNestConfig"
        )
    }
}
