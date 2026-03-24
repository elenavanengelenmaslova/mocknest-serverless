package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Priming hook for Runtime function SnapStart optimization.
 * 
 * Executes initialization code during SnapStart snapshot creation to warm up
 * resources before the first invocation, reducing cold start times.
 * 
 * This component:
 * - Detects SnapStart environment using AWS_LAMBDA_INITIALIZATION_TYPE
 * - Warms up health check endpoint
 * - Initializes S3 client connections
 * - Uses graceful degradation for non-critical failures
 */
@Component
open class RuntimePrimingHook(
    private val healthCheckUseCase: GetRuntimeHealth,
    private val s3Client: S3Client
) {
    
    /**
     * Triggered when Spring application context is fully initialized.
     * Only executes priming logic in SnapStart environments.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (isSnapStartEnvironment()) {
            logger.info { "SnapStart detected - executing runtime priming hook" }
            runBlocking {
                prime()
            }
        } else {
            logger.debug { "Not a SnapStart environment - skipping priming hook" }
        }
    }
    
    /**
     * Execute priming logic to warm up resources during snapshot creation.
     * 
     * All operations are wrapped in runCatching to ensure non-critical failures
     * don't prevent snapshot creation.
     */
    suspend fun prime() {
        logger.info { "Starting runtime function priming" }
        
        // Warm up health check endpoint
        runCatching {
            healthCheckUseCase.invoke()
            logger.info { "Health check primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Health check priming failed - continuing with snapshot creation" }
        }
        
        // Initialize S3 client connections
        runCatching {
            s3Client.listBuckets()
            logger.info { "S3 client primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "S3 client priming failed - continuing with snapshot creation" }
        }
        
        logger.info { "Runtime function priming completed" }
    }
    
    /**
     * Detect if running in SnapStart environment.
     * 
     * AWS sets AWS_LAMBDA_INITIALIZATION_TYPE=snap-start during snapshot creation.
     * 
     * @return true if running in SnapStart environment, false otherwise
     */
    protected open fun isSnapStartEnvironment(): Boolean {
        val initType = System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE")
        logger.debug { "AWS_LAMBDA_INITIALIZATION_TYPE: $initType" }
        return initType == "snap-start"
    }
}
