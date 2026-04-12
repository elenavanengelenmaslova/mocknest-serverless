package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketRequest
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

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
 * - Exercises WireMock engine (create/remove non-persistent stub — no S3 writes)
 * - Uses graceful degradation for non-critical failures
 */
@Component
@Profile("!async")
class RuntimePrimingHook(
    private val healthCheckUseCase: GetRuntimeHealth,
    private val s3Client: S3Client,
    @param:Value($$"${storage.bucket.name}") private val bucketName: String,
    private val wireMockServer: WireMockServer
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
        
        // Initialize S3 client connections with timeout protection
        runCatching {
            withTimeout(5000.milliseconds) { // 5 second timeout to prevent hanging snapshot creation
                s3Client.headBucket(HeadBucketRequest {
                    bucket = bucketName
                })
            }
            logger.info { "S3 client primed successfully for bucket: $bucketName" }
        }.onFailure { exception ->
            logger.warn(exception) { "S3 client priming failed for bucket: $bucketName - continuing with snapshot creation" }
        }
        
        // Exercise WireMock engine comprehensively
        runCatching {
            exerciseWireMock()
            logger.info { "WireMock engine primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "WireMock engine priming failed - continuing with snapshot creation" }
        }
        
        logger.info { "Runtime function priming completed" }
    }
    
    /**
     * Exercise WireMock engine to warm up stub matching and routing code paths.
     *
     * Uses a non-persistent, body-free stub so no data is written to S3:
     * - persistent(false): WireMock skips ObjectStorageMappingsSource.save/remove → no mappings/ write
     * - no response body:  NormalizeMappingBodyFilter is a no-op → no __files/ write
     * - no stubRequest():  S3RequestJournalStore.add() is never triggered → no requests/ write
     *
     * Exercises: WireMock stub creation, in-memory matching tree update, stub removal.
     *
     * @throws Exception if any step fails (caught by caller's runCatching)
     */
    private fun exerciseWireMock() {
        val testMappingId = UUID.randomUUID()
        val testPath = "/__snapstart_priming_test/$testMappingId"

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(testPath))
                .withId(testMappingId)
                .persistent(false)
                .willReturn(WireMock.aResponse().withStatus(200))
        )
        logger.debug { "Created non-persistent test mapping: $testMappingId" }

        wireMockServer.removeStubMapping(testMappingId)
        logger.debug { "Removed non-persistent test mapping: $testMappingId" }
    }
    
    /**
     * Detect if running in SnapStart environment.
     * 
     * AWS sets AWS_LAMBDA_INITIALIZATION_TYPE=snap-start during snapshot creation.
     * 
     * @return true if running in SnapStart environment, false otherwise
     */
    private fun isSnapStartEnvironment(): Boolean {
        val initType = System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE")
        logger.debug { "AWS_LAMBDA_INITIALIZATION_TYPE: $initType" }
        return initType == "snap-start"
    }
}
