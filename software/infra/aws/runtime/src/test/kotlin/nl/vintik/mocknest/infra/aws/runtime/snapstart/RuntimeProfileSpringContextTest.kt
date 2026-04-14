package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.infra.aws.MockNestApplication
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spring integration test for the `runtime` profile.
 *
 * Verifies that booting the full Spring context with `@ActiveProfiles("runtime")`
 * activates exactly the runtime-specific beans (WireMock server, priming hook,
 * mapping reload hook, use cases) and does NOT activate generation-specific beans.
 *
 * **Validates: Requirements 2.4, 2.11**
 */
@SpringBootTest(
    classes = [MockNestApplication::class, RuntimeProfileSpringContextTest.TestConfig::class],
    webEnvironment = NONE,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
@ActiveProfiles("runtime")
class RuntimeProfileSpringContextTest {

    /**
     * Provides mock beans for AWS infrastructure dependencies that are not
     * available in the test environment (S3, SQS).
     */
    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun objectStorage(): ObjectStorageInterface = mockk(relaxed = true)

        @Bean
        fun sqsPublisher(): SqsPublisherInterface = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                // no-op — SQS publishing is not exercised in this context test
            }
        }

        @Bean
        @Primary
        fun s3Client(): S3Client = mockk(relaxed = true)
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @ParameterizedTest(name = "Bean ''{0}'' MUST be present in runtime profile")
    @MethodSource("runtimeExpectedPresentBeans")
    fun `Given runtime profile When context loads Then expected bean is present`(beanName: String) {
        assertTrue(
            applicationContext.containsBean(beanName),
            "Bean '$beanName' must be present in @ActiveProfiles(\"runtime\") context"
        )
    }

    @ParameterizedTest(name = "Bean ''{0}'' MUST be absent in runtime profile")
    @MethodSource("runtimeExpectedAbsentBeans")
    fun `Given runtime profile When context loads Then generation bean is absent`(beanName: String) {
        assertFalse(
            applicationContext.containsBean(beanName),
            "Bean '$beanName' must be absent in @ActiveProfiles(\"runtime\") context"
        )
    }

    companion object {
        /**
         * Beans that MUST be present when @ActiveProfiles("runtime") is active.
         */
        @JvmStatic
        fun runtimeExpectedPresentBeans() = listOf(
            "runtimePrimingHook",
            "mockNestConfig",
            "wireMockServer",
            "directCallHttpServer",
            "runtimeLambdaHandler",
            "adminRequestUseCase",
            "clientRequestUseCase",
            "runtimeMappingReloadHook",
        )

        /**
         * Beans that MUST be absent when @ActiveProfiles("runtime") is active.
         */
        @JvmStatic
        fun runtimeExpectedAbsentBeans() = listOf(
            "generationPrimingHook",
        )
    }
}
