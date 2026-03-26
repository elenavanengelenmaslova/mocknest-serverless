package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import java.util.UUID

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
 * - Initializes Bedrock client reference (no model invocation to avoid costs)
 * - Validates AI model configuration
 * - Exercises OpenAPI specification parser
 * - Exercises prompt builder service (loads templates)
 * - Exercises mock validator
 * - Uses graceful degradation for non-critical failures
 */
@Component
open class GenerationPrimingHook(
    private val aiHealthUseCase: GetAIHealth,
    private val s3Client: S3Client,
    @param:Value($$"${storage.bucket.name}") private val bucketName: String,
    private val bedrockClient: BedrockRuntimeClient,
    private val modelConfig: ModelConfiguration,
    private val specificationParser: OpenAPISpecificationParser,
    private val promptBuilderService: PromptBuilderService,
    private val mockValidator: OpenAPIMockValidator
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
            s3Client.headBucket(HeadBucketRequest {
                bucket = bucketName
            })
            logger.info { "S3 client primed successfully for bucket: $bucketName" }
        }.onFailure { exception ->
            logger.warn(exception) { "S3 client priming failed for bucket: $bucketName - continuing with snapshot creation" }
        }
        
        // Initialize Bedrock client reference (no model invocation to avoid costs during snapshot creation)
        runCatching {
            logger.info { "Bedrock client reference initialized for model: ${modelConfig.getModelName()}" }
        }.onFailure { exception ->
            logger.warn(exception) { "Bedrock client reference initialization failed - continuing with snapshot creation" }
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
        
        // Exercise OpenAPI specification parser
        runCatching {
            exerciseSpecificationParser()
            logger.info { "OpenAPI specification parser primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Specification parser priming failed - continuing with snapshot creation" }
        }
        
        // Exercise prompt builder service
        runCatching {
            exercisePromptBuilder()
            logger.info { "Prompt builder service primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Prompt builder priming failed - continuing with snapshot creation" }
        }
        
        // Exercise mock validator
        runCatching {
            exerciseMockValidator()
            logger.info { "Mock validator primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "Mock validator priming failed - continuing with snapshot creation" }
        }
        
        logger.info { "Generation function priming completed" }
    }
    
    /**
     * Exercise OpenAPI specification parser to warm up parsing logic.
     * Parses a minimal test OpenAPI specification.
     */
    private suspend fun exerciseSpecificationParser() {
        val minimalSpec = """
        {
          "openapi": "3.0.0",
          "info": {
            "title": "SnapStart Priming Test API",
            "version": "1.0.0"
          },
          "paths": {
            "/test": {
              "get": {
                "responses": {
                  "200": {
                    "description": "Success",
                    "content": {
                      "application/json": {
                        "schema": {
                          "type": "object",
                          "properties": {
                            "message": {
                              "type": "string"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent()
        
        // Parse the specification to warm up the parser
        specificationParser.parse(minimalSpec, SpecificationFormat.OPENAPI_3)
        logger.debug { "Parsed test OpenAPI specification" }
    }
    
    /**
     * Exercise prompt builder service to warm up template loading.
     * Loads prompt templates from classpath resources.
     */
    private fun exercisePromptBuilder() {
        // Load system prompt template to warm up template loading
        promptBuilderService.loadSystemPrompt()
        logger.debug { "Loaded system prompt template" }
    }
    
    /**
     * Exercise mock validator to warm up validation logic.
     * Validates a test mock against a test specification.
     */
    private suspend fun exerciseMockValidator() {
        // Create a minimal test specification
        val testSpec = specificationParser.parse(
            """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "responses": {
                      "200": {
                        "description": "Success",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "status": {
                                  "type": "string"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
            SpecificationFormat.OPENAPI_3
        )
        
        // Create a test mock
        val testMock = GeneratedMock(
            id = "snapstart-test-${UUID.randomUUID()}",
            name = "SnapStart Priming Test Mock",
            namespace = MockNamespace(apiName = "test-api", client = null),
            wireMockMapping = """
            {
              "request": {
                "method": "GET",
                "url": "/test"
              },
              "response": {
                "status": 200,
                "jsonBody": {
                  "status": "ok"
                }
              }
            }
            """.trimIndent(),
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "SnapStart priming test",
                endpoint = EndpointInfo(
                    method = HttpMethod.GET,
                    path = "/test",
                    statusCode = 200,
                    contentType = "application/json"
                )
            )
        )
        
        // Validate the test mock to warm up the validator
        mockValidator.validate(testMock, testSpec)
        logger.debug { "Validated test mock against specification" }
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
