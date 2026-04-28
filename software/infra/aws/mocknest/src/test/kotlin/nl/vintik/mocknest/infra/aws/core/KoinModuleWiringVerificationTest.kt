package nl.vintik.mocknest.infra.aws.core

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.infra.aws.config.LocalStackTestHelper
import nl.vintik.mocknest.infra.aws.config.TEST_BUCKET_NAME
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import nl.vintik.mocknest.infra.aws.generation.di.generationModule
import nl.vintik.mocknest.infra.aws.runtime.di.asyncModule
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.check.checkModules
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertNotNull

private val logger = KotlinLogging.logger {}

/**
 * Koin module wiring verification tests.
 *
 * Uses isolated `koinApplication { }` startup tests to validate that all dependency
 * graphs resolve correctly. This approach is preferred over `verify()` because our
 * modules depend on external resources (S3Client, BedrockRuntimeClient) that need
 * real or mock instances.
 *
 * For asyncModule(), which has no external dependencies, we use direct startup verification.
 * For runtimeModule() and generationModule(), we provide a test coreModule with
 * LocalStack S3 and mock Bedrock clients.
 *
 * Requirements: 8.5
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KoinModuleWiringVerificationTest {

    private lateinit var testS3Client: S3Client

    companion object {
        private val envVars = EnvironmentVariables()
    }

    @BeforeAll
    fun setupAll() {
        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.set("AWS_DEFAULT_REGION", TEST_REGION)
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "")
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", "Authorization,X-Api-Key")
        envVars.set("MOCKNEST_WEBHOOK_TIMEOUT_MS", "5000")
        envVars.set("BEDROCK_MODEL_NAME", "AmazonNovaPro")
        envVars.set("BEDROCK_INFERENCE_MODE", "AUTO")
        envVars.set("BEDROCK_GENERATION_MAX_RETRIES", "1")
        envVars.setup()

        testS3Client = LocalStackTestHelper.createTestS3Client()
        LocalStackTestHelper.ensureTestBucket(testS3Client)
    }

    @AfterAll
    fun tearDownAll() {
        envVars.teardown()
        kotlinx.coroutines.runBlocking { testS3Client.close() }
    }

    @AfterEach
    fun tearDown() {
        // Each test uses isolated koinApplication, but stopKoin in case of leaks
        runCatching { stopKoin() }
    }

    private fun testCoreModule() = module {
        single { mapper }
        single { testS3Client }
    }

    @Nested
    inner class RuntimeModuleWiring {

        @Test
        fun `Given coreModule and runtimeModule When started Then all beans resolve`() {
            // Given / When
            val app = koinApplication {
                modules(testCoreModule(), runtimeModule())
            }

            // Then — verify all key beans resolve without errors
            val koin = app.koin

            assertNotNull(koin.get<nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface>())
            logger.info { "✓ ObjectStorageInterface resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest>())
            logger.info { "✓ HandleAdminRequest resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest>())
            logger.info { "✓ HandleClientRequest resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth>())
            logger.info { "✓ GetRuntimeHealth resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook>())
            logger.info { "✓ RuntimePrimingHook resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook>())
            logger.info { "✓ RuntimeMappingReloadHook resolved" }

            assertNotNull(koin.get<com.github.tomakehurst.wiremock.WireMockServer>())
            logger.info { "✓ WireMockServer resolved" }

            app.close()
            logger.info { "Runtime module wiring verification passed" }
        }
    }

    @Nested
    inner class GenerationModuleWiring {

        @Test
        fun `Given coreModule and generationModule When started Then all beans resolve`() {
            // Given — mock Bedrock since it's not available in LocalStack
            val bedrockOverride = module {
                single { mockk<BedrockRuntimeClient>(relaxed = true) }
            }

            // When
            val app = koinApplication {
                allowOverride(true)
                modules(testCoreModule(), generationModule(), bedrockOverride)
            }

            // Then — verify all key beans resolve without errors
            val koin = app.koin

            assertNotNull(koin.get<nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface>())
            logger.info { "✓ GenerationStorageInterface resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest>())
            logger.info { "✓ HandleAIGenerationRequest resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.generation.usecases.GetAIHealth>())
            logger.info { "✓ GetAIHealth resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.generation.interfaces.CompositeSpecificationParser>())
            logger.info { "✓ CompositeSpecificationParser resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface>())
            logger.info { "✓ MockValidatorInterface resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook>())
            logger.info { "✓ GenerationPrimingHook resolved" }

            app.close()
            logger.info { "Generation module wiring verification passed" }
        }
    }

    @Nested
    inner class AsyncModuleWiring {

        @Test
        fun `Given asyncModule When started Then all beans resolve`() {
            // Given / When — asyncModule has no external dependencies
            val app = koinApplication {
                modules(asyncModule())
            }

            // Then
            val koin = app.koin

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.config.WebhookConfig>())
            logger.info { "✓ WebhookConfig resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface>())
            logger.info { "✓ WebhookHttpClientInterface resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncHandler>())
            logger.info { "✓ RuntimeAsyncHandler resolved" }

            assertNotNull(koin.get<nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncPrimingHook>())
            logger.info { "✓ RuntimeAsyncPrimingHook resolved" }

            app.close()
            logger.info { "Async module wiring verification passed" }
        }
    }
}
