package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketRequest
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

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
 * - Exercises WireMock engine comprehensively (create/call/remove mock)
 * - Uses graceful degradation for non-critical failures
 */
@Component
open class RuntimePrimingHook(
    private val healthCheckUseCase: GetRuntimeHealth,
    private val s3Client: S3Client,
    @param:Value($$"${storage.bucket.name}") private val bucketName: String,
    private val wireMockServer: WireMockServer,
    private val directCallHttpServer: DirectCallHttpServer
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
            s3Client.headBucket(HeadBucketRequest {
                bucket = bucketName
            })
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
     * Exercise WireMock engine comprehensively to warm up all critical components.
     * 
     * This method:
     * 1. Creates a test mock with JSON body and query parameters (unique path per run)
     * 2. Verifies the mock was persisted to S3
     * 3. Calls the mock endpoint to verify request matching and response generation
     * 4. Removes the test mock to exercise deletion logic (guaranteed via finally block)
     * 
     * Exercises:
     * - NormalizeMappingBodyFilter (body extraction, S3 storage, mapping modification)
     * - ObjectStorageBlobStore (file storage with Base64 encoding/decoding)
     * - ObjectStorageMappingsSource (save/retrieve/delete operations)
     * - DeleteAllMappingsAndFilesFilter (deletion logic)
     * - Request matching with query parameters
     * - Response body retrieval from storage
     * 
     * @throws Exception if any step fails (caught by caller's runCatching)
     */
    private fun exerciseWireMock() {
        val testMappingId = UUID.randomUUID()
        // Use unique path per run to avoid conflicts between concurrent priming attempts
        val testPath = "/__snapstart_priming_test/$testMappingId"
        var mappingCreated = false
        
        try {
            // 1. Create a test mock mapping with a JSON body via WireMock admin API
            // This exercises:
            // - NormalizeMappingBodyFilter (body extraction, S3 storage, mapping modification)
            // - ObjectStorageBlobStore (file storage with Base64 encoding/decoding)
            // - ObjectStorageMappingsSource (mapping persistence to S3)
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo(testPath))
                    .withId(testMappingId)
                    .withQueryParam("test", WireMock.equalTo("snapstart"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"status":"priming","timestamp":${System.currentTimeMillis()}}""")
                    )
            )
            mappingCreated = true
            logger.debug { "Created test mapping with JSON body: $testMappingId" }
            
            // 2. Verify the mock was stored by retrieving it
            // This exercises:
            // - ObjectStorageMappingsSource read path (prefix-based listing, concurrent streaming)
            // - S3 listPrefix operation with filtering
            // - JSON deserialization of StubMapping objects
            val retrievedMapping = wireMockServer.getStubMapping(testMappingId)
            if (retrievedMapping != null) {
                logger.debug { "Verified test mapping was persisted to S3" }
            }
            
            // 3. Call the mock endpoint to verify request matching and response generation
            // This exercises:
            // - Request matching with query parameters
            // - Response body retrieval from ObjectStorageBlobStore
            // - File extension detection and content-type handling
            val testRequest = com.github.tomakehurst.wiremock.http.ImmutableRequest.create()
                .withMethod(com.github.tomakehurst.wiremock.http.RequestMethod.GET)
                .withAbsoluteUrl("http://localhost$testPath?test=snapstart")
                .build()
            
            val response = directCallHttpServer.stubRequest(testRequest)
            if (response.status == 200) {
                logger.debug { "Invoked test mapping successfully, status: ${response.status}" }
            }
            
        } finally {
            // 4. Always attempt cleanup, even if earlier steps failed
            // This exercises:
            // - DeleteAllMappingsAndFilesFilter deletion logic
            // - ObjectStorageBlobStore file deletion
            // - ObjectStorageMappingsSource mapping removal
            if (mappingCreated) {
                runCatching {
                    wireMockServer.removeStubMapping(testMappingId)
                    logger.debug { "Removed test mapping: $testMappingId" }
                }.onFailure { cleanupException ->
                    logger.warn(cleanupException) { "Failed to remove test mapping: $testMappingId" }
                }
            }
        }
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
