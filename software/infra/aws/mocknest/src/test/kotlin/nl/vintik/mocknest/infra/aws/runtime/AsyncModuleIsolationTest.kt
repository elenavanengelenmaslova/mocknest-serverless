package nl.vintik.mocknest.infra.aws.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.infra.aws.runtime.di.asyncModule
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncHandler
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncPrimingHook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Koin module verification tests for asyncModule().
 *
 * Replaces the previous `RuntimeAsyncSpringContextTest` and
 * `AsyncProfileIsolationPreservationTest` Spring context tests.
 *
 * Validates:
 * - asyncModule() resolves all dependencies correctly
 * - Runtime-specific beans (WireMock, S3 storage, admin/client use cases) are NOT present
 *   in the async module — verifying profile isolation
 *
 * Requirements: 8.5
 */
class AsyncModuleIsolationTest {

    private val envVars = EnvironmentVariables()

    @BeforeEach
    fun setup() {
        envVars.set("AWS_DEFAULT_REGION", "us-east-1")
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "https://sqs.us-east-1.amazonaws.com/123456789/test-queue")
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", "Authorization,X-Api-Key")
        envVars.set("MOCKNEST_WEBHOOK_TIMEOUT_MS", "5000")
        envVars.setup()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        envVars.teardown()
    }

    @Nested
    inner class AsyncModuleResolution {

        @Test
        fun `Given asyncModule When started Then should resolve all dependencies`() {
            // Given / When
            val koinApp = startKoin {
                modules(asyncModule())
            }
            val koin = koinApp.koin

            // Then — all async beans should resolve
            val webhookConfig = koin.get<WebhookConfig>()
            assertNotNull(webhookConfig, "WebhookConfig should be resolvable")
            logger.info { "✓ WebhookConfig resolved" }

            val webhookHttpClient = koin.get<WebhookHttpClientInterface>()
            assertNotNull(webhookHttpClient, "WebhookHttpClientInterface should be resolvable")
            logger.info { "✓ WebhookHttpClientInterface resolved" }

            val asyncHandler = koin.get<RuntimeAsyncHandler>()
            assertNotNull(asyncHandler, "RuntimeAsyncHandler should be resolvable")
            logger.info { "✓ RuntimeAsyncHandler resolved" }

            val primingHook = koin.get<RuntimeAsyncPrimingHook>()
            assertNotNull(primingHook, "RuntimeAsyncPrimingHook should be resolvable")
            logger.info { "✓ RuntimeAsyncPrimingHook resolved" }
        }
    }

    @Nested
    inner class AsyncModuleIsolation {

        @Test
        fun `Given asyncModule When started Then runtime-specific beans should NOT be present`() {
            // Given / When
            val koinApp = startKoin {
                modules(asyncModule())
            }
            val koin = koinApp.koin

            // Then — runtime-specific beans should NOT be resolvable
            val runtimeBeanTypes = listOf(
                "ObjectStorageInterface" to nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface::class,
                "HandleAdminRequest" to nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest::class,
                "HandleClientRequest" to nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest::class,
                "GetRuntimeHealth" to nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth::class,
            )

            for ((name, type) in runtimeBeanTypes) {
                val result = runCatching { koin.get<Any>(type) }
                assertTrue(result.isFailure, "$name should NOT be resolvable in asyncModule — isolation violation")
                logger.info { "✓ $name correctly absent from asyncModule" }
            }
        }

        @Test
        fun `Given asyncModule When started Then generation-specific beans should NOT be present`() {
            // Given / When
            val koinApp = startKoin {
                modules(asyncModule())
            }
            val koin = koinApp.koin

            // Then — generation-specific beans should NOT be resolvable
            val generationBeanTypes = listOf(
                "HandleAIGenerationRequest" to nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest::class,
                "GetAIHealth" to nl.vintik.mocknest.application.generation.usecases.GetAIHealth::class,
            )

            for ((name, type) in generationBeanTypes) {
                val result = runCatching { koin.get<Any>(type) }
                assertTrue(result.isFailure, "$name should NOT be resolvable in asyncModule — isolation violation")
                logger.info { "✓ $name correctly absent from asyncModule" }
            }
        }
    }
}
