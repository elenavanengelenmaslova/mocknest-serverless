package nl.vintik.mocknest.infra.aws.core

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.infra.aws.config.LocalStackTestHelper
import nl.vintik.mocknest.infra.aws.config.TEST_BUCKET_NAME
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import nl.vintik.mocknest.infra.aws.generation.di.generationModule
import nl.vintik.mocknest.infra.aws.generation.health.AwsAIHealthUseCase
import nl.vintik.mocknest.infra.aws.runtime.di.asyncModule
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Property 6: Environment variable configuration mapping
 *
 * Verifies that Koin-resolved beans have configuration values matching the
 * environment variables set at startup. Uses `system-stubs-jupiter` to set
 * environment variables and `koinApplication { }` for isolated Koin instances.
 *
 * **Validates: Requirements 2.5**
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnvVarConfigurationPropertyTest {

    private lateinit var testS3Client: aws.sdk.kotlin.services.s3.S3Client

    @BeforeAll
    fun setupAll() {
        testS3Client = LocalStackTestHelper.createTestS3Client()
        LocalStackTestHelper.ensureTestBucket(testS3Client)
    }

    @AfterAll
    fun tearDownAll() {
        kotlinx.coroutines.runBlocking { testS3Client.close() }
    }

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    data class WebhookEnvConfig(
        val sensitiveHeaders: String,
        val webhookTimeoutMs: String,
        val expectedHeaderCount: Int,
        val description: String,
    )

    data class ModelEnvConfig(
        val modelName: String,
        val inferenceMode: String,
        val description: String,
    )

    companion object {
        @JvmStatic
        fun webhookConfigurations(): Stream<WebhookEnvConfig> = Stream.of(
            WebhookEnvConfig(
                sensitiveHeaders = "Authorization,X-Api-Key",
                webhookTimeoutMs = "5000",
                expectedHeaderCount = 2,
                description = "Standard two-header configuration",
            ),
            WebhookEnvConfig(
                sensitiveHeaders = "Authorization",
                webhookTimeoutMs = "10000",
                expectedHeaderCount = 1,
                description = "Single sensitive header",
            ),
            WebhookEnvConfig(
                sensitiveHeaders = "Authorization,X-Api-Key,X-Custom-Secret,Cookie",
                webhookTimeoutMs = "3000",
                expectedHeaderCount = 4,
                description = "Multiple sensitive headers",
            ),
            WebhookEnvConfig(
                sensitiveHeaders = "",
                webhookTimeoutMs = "1000",
                expectedHeaderCount = 0,
                description = "Empty sensitive headers",
            ),
        )

        @JvmStatic
        fun modelConfigurations(): Stream<ModelEnvConfig> = Stream.of(
            ModelEnvConfig(
                modelName = "AmazonNovaPro",
                inferenceMode = "AUTO",
                description = "Default Nova Pro with AUTO inference",
            ),
            ModelEnvConfig(
                modelName = "AmazonNovaLite",
                inferenceMode = "GLOBAL_ONLY",
                description = "Nova Lite with GLOBAL_ONLY inference",
            ),
            ModelEnvConfig(
                modelName = "AmazonNovaPro",
                inferenceMode = "GEO_ONLY",
                description = "Nova Pro with GEO_ONLY inference",
            ),
        )
    }

    @ParameterizedTest(name = "Webhook config: {0}")
    @MethodSource("webhookConfigurations")
    fun `Given environment variables When asyncModule resolves WebhookConfig Then values match env vars`(
        config: WebhookEnvConfig,
    ) {
        val envVars = EnvironmentVariables()
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", config.sensitiveHeaders)
        envVars.set("MOCKNEST_WEBHOOK_TIMEOUT_MS", config.webhookTimeoutMs)
        envVars.set("AWS_DEFAULT_REGION", TEST_REGION)
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "")
        envVars.setup()

        try {
            val app = koinApplication {
                modules(asyncModule())
            }
            val koin = app.koin

            val webhookConfig = koin.get<WebhookConfig>()
            assertNotNull(webhookConfig, "WebhookConfig should be resolvable")

            // Verify sensitive headers count matches
            assertEquals(
                config.expectedHeaderCount,
                webhookConfig.sensitiveHeaders.size,
                "Sensitive headers count should match for: ${config.description}",
            )

            // Verify timeout matches
            assertEquals(
                config.webhookTimeoutMs.toLong(),
                webhookConfig.webhookTimeoutMs,
                "Webhook timeout should match env var for: ${config.description}",
            )

            logger.info { "✓ ${config.description}: headers=${webhookConfig.sensitiveHeaders.size}, timeout=${webhookConfig.webhookTimeoutMs}ms" }
            app.close()
        } finally {
            envVars.teardown()
        }
    }

    @ParameterizedTest(name = "Model config: {0}")
    @MethodSource("modelConfigurations")
    fun `Given environment variables When generationModule resolves ModelConfiguration Then values match env vars`(
        config: ModelEnvConfig,
    ) {
        val envVars = EnvironmentVariables()
        envVars.set("BEDROCK_MODEL_NAME", config.modelName)
        envVars.set("BEDROCK_INFERENCE_MODE", config.inferenceMode)
        envVars.set("BEDROCK_GENERATION_MAX_RETRIES", "1")
        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.setup()

        try {
            val bedrockOverride = module {
                single { mockk<BedrockRuntimeClient>(relaxed = true) }
            }
            val testCoreModule = module {
                single { mapper }
                single { testS3Client }
            }

            val app = koinApplication {
                allowOverride(true)
                modules(testCoreModule, generationModule(), bedrockOverride)
            }
            val koin = app.koin

            val modelConfig = koin.get<ModelConfiguration>()
            assertNotNull(modelConfig, "ModelConfiguration should be resolvable")

            // Verify model name matches
            assertEquals(
                config.modelName,
                modelConfig.getModelName(),
                "Model name should match env var for: ${config.description}",
            )

            logger.info { "✓ ${config.description}: model=${modelConfig.getModelName()}" }
            app.close()
        } finally {
            envVars.teardown()
        }
    }
}
