package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

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
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertFalse

/**
 * Preservation Property Test — Async Profile Isolation
 *
 * Verifies that `@ActiveProfiles("async")` context does NOT contain runtime or generation
 * beans. This MUST PASS on UNFIXED code to confirm the baseline behavior to preserve.
 *
 * Uses `@ParameterizedTest` with `@MethodSource` to check each bean name individually,
 * providing clear failure messages per bean.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8**
 */
@SpringBootTest(
    classes = [MockNestApplication::class, AsyncProfileIsolationPreservationTest.TestConfig::class],
    webEnvironment = NONE,
)
@ActiveProfiles("async")
class AsyncProfileIsolationPreservationTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun sqsPublisher(): SqsPublisherInterface = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                // no-op — SQS publishing is not exercised in the async Lambda context
            }
        }
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @ParameterizedTest(name = "Given async profile When context loads Then bean ''{0}'' is absent")
    @MethodSource("beansAbsentInAsyncProfile")
    fun `Given async profile When context loads Then runtime and generation beans are absent`(beanName: String) {
        assertFalse(
            applicationContext.containsBean(beanName),
            "Preservation: bean '$beanName' must be absent in async profile — " +
                "async Lambda must remain isolated from runtime and generation components"
        )
    }

    companion object {
        @JvmStatic
        fun beansAbsentInAsyncProfile() = listOf(
            "runtimePrimingHook",
            "generationPrimingHook",
            "mockNestConfig",
            "runtimeLambdaHandler",
            "adminRequestUseCase",
            "clientRequestUseCase",
            "runtimeMappingReloadHook",
        )
    }
}
