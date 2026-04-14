package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.infra.aws.MockNestApplication
import org.junit.jupiter.api.Nested
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
 * Bug Condition Exploration Tests — Property 1: Profile Cross-Contamination
 *
 * These tests PROVE the bugs exist on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bug exists.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * Bug Condition (Cross-Contamination):
 *   On unfixed code, all beans use @Profile("!async"). When booting with
 *   @ActiveProfiles("runtime"), GenerationPrimingHook is also present because
 *   "runtime" is not "async", so @Profile("!async") matches. Similarly, when
 *   booting with @ActiveProfiles("generation"), RuntimePrimingHook, MockNestConfig,
 *   RuntimeLambdaHandler, AdminRequestUseCase, and ClientRequestUseCase are all
 *   present because "generation" is not "async".
 *
 * **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8, 2.4, 2.5, 2.7, 2.11, 2.12, 2.13**
 */
class SnapStartBugConditionExplorationTest {

    /**
     * Runtime profile context test — verifies bean isolation for the runtime Lambda.
     *
     * On FIXED code: RuntimePrimingHook, MockNestConfig, RuntimeLambdaHandler,
     * AdminRequestUseCase, ClientRequestUseCase should be present, and
     * GenerationPrimingHook should be absent.
     *
     * On UNFIXED code: GenerationPrimingHook will also be present (cross-contamination),
     * causing the "absent" assertion to FAIL — proving the bug exists.
     */
    @Nested
    @SpringBootTest(
        classes = [MockNestApplication::class, SharedTestConfig::class],
        webEnvironment = NONE,
        properties = ["spring.main.allow-bean-definition-overriding=true"],
    )
    @ActiveProfiles("runtime")
    inner class RuntimeProfileBeanIsolation {

        @Autowired
        private lateinit var applicationContext: ApplicationContext

        @ParameterizedTest(name = "Bean ''{0}'' MUST be present in runtime profile")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.snapstart.SnapStartBugConditionExplorationTest#runtimeExpectedPresentBeans")
        fun `Given runtime profile When context loads Then expected beans are present`(beanName: String) {
            assertTrue(
                applicationContext.containsBean(beanName),
                "Bean '$beanName' must be present in @ActiveProfiles(\"runtime\") context"
            )
        }

        @ParameterizedTest(name = "Bean ''{0}'' MUST be absent in runtime profile")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.snapstart.SnapStartBugConditionExplorationTest#runtimeExpectedAbsentBeans")
        fun `Given runtime profile When context loads Then cross-contaminating beans are absent`(beanName: String) {
            assertFalse(
                applicationContext.containsBean(beanName),
                "Bean '$beanName' must be absent in @ActiveProfiles(\"runtime\") context — " +
                    "cross-contamination via @Profile(\"!async\")"
            )
        }
    }

    /**
     * Generation profile context test — verifies bean isolation for the generation Lambda.
     *
     * On FIXED code: GenerationPrimingHook should be present, and RuntimePrimingHook,
     * MockNestConfig, RuntimeLambdaHandler should be absent.
     *
     * On UNFIXED code: RuntimePrimingHook, MockNestConfig, RuntimeLambdaHandler will
     * also be present (cross-contamination), causing the "absent" assertions to FAIL —
     * proving the bug exists.
     */
    @Nested
    @SpringBootTest(
        classes = [MockNestApplication::class, SharedTestConfig::class],
        webEnvironment = NONE,
        properties = ["spring.main.allow-bean-definition-overriding=true"],
    )
    @ActiveProfiles("generation")
    inner class GenerationProfileBeanIsolation {

        @Autowired
        private lateinit var applicationContext: ApplicationContext

        @ParameterizedTest(name = "Bean ''{0}'' MUST be present in generation profile")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.snapstart.SnapStartBugConditionExplorationTest#generationExpectedPresentBeans")
        fun `Given generation profile When context loads Then expected beans are present`(beanName: String) {
            assertTrue(
                applicationContext.containsBean(beanName),
                "Bean '$beanName' must be present in @ActiveProfiles(\"generation\") context"
            )
        }

        @ParameterizedTest(name = "Bean ''{0}'' MUST be absent in generation profile")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.snapstart.SnapStartBugConditionExplorationTest#generationExpectedAbsentBeans")
        fun `Given generation profile When context loads Then runtime-only beans are absent`(beanName: String) {
            assertFalse(
                applicationContext.containsBean(beanName),
                "Bean '$beanName' must be absent in @ActiveProfiles(\"generation\") context — " +
                    "cross-contamination via @Profile(\"!async\")"
            )
        }
    }

    /**
     * Shared TestConfiguration — provides mock beans for AWS infrastructure dependencies
     * that are not available in the test environment (S3, SQS, Bedrock).
     */
    @TestConfiguration
    class SharedTestConfig {
        @Bean
        @Primary
        fun objectStorage(): ObjectStorageInterface = mockk(relaxed = true)

        @Bean
        fun sqsPublisher(): SqsPublisherInterface = mockk(relaxed = true)

        @Bean
        @Primary
        fun s3Client(): S3Client = mockk(relaxed = true)

        @Bean
        @Primary
        fun bedrockRuntimeClient(): BedrockRuntimeClient = mockk(relaxed = true)
    }

    companion object {
        /**
         * Beans that MUST be present when @ActiveProfiles("runtime") is active.
         */
        @JvmStatic
        fun runtimeExpectedPresentBeans() = listOf(
            "runtimePrimingHook",
            "mockNestConfig",
            "runtimeLambdaHandler",
            "adminRequestUseCase",
            "clientRequestUseCase",
        )

        /**
         * Beans that MUST be absent when @ActiveProfiles("runtime") is active.
         * On unfixed code, these will be present due to @Profile("!async") cross-contamination.
         */
        @JvmStatic
        fun runtimeExpectedAbsentBeans() = listOf(
            "generationPrimingHook",
        )

        /**
         * Beans that MUST be present when @ActiveProfiles("generation") is active.
         */
        @JvmStatic
        fun generationExpectedPresentBeans() = listOf(
            "generationPrimingHook",
        )

        /**
         * Beans that MUST be absent when @ActiveProfiles("generation") is active.
         * On unfixed code, these will be present due to @Profile("!async") cross-contamination.
         */
        @JvmStatic
        fun generationExpectedAbsentBeans() = listOf(
            "runtimePrimingHook",
            "mockNestConfig",
            "runtimeLambdaHandler",
        )
    }
}
