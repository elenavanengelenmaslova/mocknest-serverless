package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
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
 * Spring integration test for the `generation` profile.
 *
 * Verifies that booting the full Spring context with `@ActiveProfiles("generation")`
 * activates exactly the generation-specific beans (GenerationPrimingHook, Bedrock/AI
 * components) and does NOT activate runtime-specific beans (WireMock server, runtime
 * priming hook, mapping reload hook, use cases).
 *
 * **Validates: Requirements 2.5, 2.7, 2.12**
 */
@SpringBootTest(
    classes = [MockNestApplication::class, GenerationProfileSpringContextTest.TestConfig::class],
    webEnvironment = NONE,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
@ActiveProfiles("generation")
class GenerationProfileSpringContextTest {

    /**
     * Provides mock beans for AWS infrastructure dependencies that are not
     * available in the test environment (S3, Bedrock).
     */
    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun s3Client(): S3Client = mockk(relaxed = true)

        @Bean
        @Primary
        fun bedrockRuntimeClient(): BedrockRuntimeClient = mockk(relaxed = true)
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @ParameterizedTest(name = "Bean ''{0}'' MUST be present in generation profile")
    @MethodSource("generationExpectedPresentBeans")
    fun `Given generation profile When context loads Then expected bean is present`(beanName: String) {
        assertTrue(
            applicationContext.containsBean(beanName),
            "Bean '$beanName' must be present in @ActiveProfiles(\"generation\") context"
        )
    }

    @ParameterizedTest(name = "Bean ''{0}'' MUST be absent in generation profile")
    @MethodSource("generationExpectedAbsentBeans")
    fun `Given generation profile When context loads Then runtime bean is absent`(beanName: String) {
        assertFalse(
            applicationContext.containsBean(beanName),
            "Bean '$beanName' must be absent in @ActiveProfiles(\"generation\") context"
        )
    }

    companion object {
        /**
         * Beans that MUST be present when @ActiveProfiles("generation") is active.
         */
        @JvmStatic
        fun generationExpectedPresentBeans() = listOf(
            "generationPrimingHook",
        )

        /**
         * Beans that MUST be absent when @ActiveProfiles("generation") is active.
         * Uses string bean names because runtime module classes are not on the
         * generation module's classpath.
         */
        @JvmStatic
        fun generationExpectedAbsentBeans() = listOf(
            "runtimePrimingHook",
            "mockNestConfig",
            "wireMockServer",
            "directCallHttpServer",
            "runtimeLambdaHandler",
            "runtimeMappingReloadHook",
        )
    }
}
