package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Priming hook for Generation function SnapStart optimization.
 * 
 * Executes initialization code during SnapStart snapshot creation to warm up
 * resources before the first invocation, reducing cold start times.
 * 
 * This component:
 * - Detects SnapStart environment using AWS_LAMBDA_INITIALIZATION_TYPE
 * - Warms up AI health check endpoint
 * - Initializes S3 client connections
 * - Initializes Bedrock client
 * - Validates AI model configuration
 * - Uses graceful degradation for non-critical failures
 */
@Component
class GenerationPrimingHook(
    private val aiHealthUseCase: GetAIHealth,
    private val s3Client: S3Client,
    private val bedrockClient: BedrockRuntimeClient,
    private val modelConfig: ModelConfiguration
) {
    
    /**
     * Triggered when Spring application context is fully initialized.
     * Only executes priming logic in SnapStart environments.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (isSnapStartEnvironment()) {
            logger.info { "SnapStart detected - executing generation priming hook" }
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
        logger.info { "Starting generation function priming" }
        
        // Warm up AI health check endpoint
        runCatching {
            aiHealthUseCase.invoke()
            logger.info { "AI health check primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "AI health check priming failed - continuing with snapshot creation" }
        }
        
        // Initialize S3 client connections
        runCatching {
            s3Client.listBuckets()
            logger.info { "S3 client primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "S3 client priming failed - continuing with snapshot creation" }
        }
        
        // Initialize Bedrock client (just log initialization, don't invoke model)
        runCatching {
            logger.info { "Bedrock client initialized for model: ${modelConfig.getModelName()}" }
        }.onFailure { exception ->
            logger.warn(exception) { "Bedrock client initialization logging failed - continuing with snapshot creation" }
        }
        
        // Validate AI model configuration
        runCatching {
            val modelName = modelConfig.getModelName()
            val prefix = modelConfig.getConfiguredPrefix()
            val isSupported = modelConfig.isOfficiallySupported()
            
            logger.info { 
                "AI model configuration validated: model=$modelName, prefix=$prefix, officiallySupported=$isSupported" 
            }
        }.onFailure { exception ->
            logger.warn(exception) { "Model configuration validation failed - continuing with snapshot creation" }
        }
        
        logger.info { "Generation function priming completed" }
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
