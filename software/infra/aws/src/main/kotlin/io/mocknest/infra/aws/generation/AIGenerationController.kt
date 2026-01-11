package io.mocknest.infra.aws.generation

import io.mocknest.application.generation.usecases.*
import io.mocknest.domain.generation.*
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import javax.validation.Valid

/**
 * REST controller for AI-powered mock generation endpoints.
 * Provides the /ai/generation/* API for generating mocks from specifications and descriptions.
 */
@RestController
@RequestMapping("/ai/generation")
@ConditionalOnProperty(name = ["ai.enabled"], havingValue = "true")
class AIGenerationController(
    private val generateFromSpecUseCase: GenerateMocksFromSpecUseCase,
    private val generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase,
    private val generateFromDescriptionUseCase: GenerateMocksFromDescriptionUseCase,
    private val generationStorageInterface: io.mocknest.application.generation.interfaces.GenerationStorageInterface
) {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Generate mocks from API specification only.
     */
    @PostMapping("/from-spec")
    suspend fun generateFromSpec(@Valid @RequestBody request: GenerateFromSpecRequest): ResponseEntity<GenerationResponse> {
        logger.info { "Generating mocks from specification for namespace: ${request.namespace.displayName()}" }
        
        return try {
            val mockRequest = MockGenerationRequest(
                namespace = request.namespace,
                specificationContent = request.specification,
                format = request.format,
                options = request.options
            )
            
            val result = generateFromSpecUseCase.execute(mockRequest)
            
            if (result.success) {
                ResponseEntity.ok(GenerationResponse(
                    jobId = result.jobId,
                    namespace = request.namespace.toPrefix(),
                    status = "COMPLETED",
                    mocksGenerated = result.mocksGenerated,
                    estimatedCompletion = null
                ))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenerationResponse(
                        jobId = result.jobId,
                        namespace = request.namespace.toPrefix(),
                        status = "FAILED",
                        error = result.error
                    ))
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate mocks from specification" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenerationResponse(
                    jobId = "error-${System.currentTimeMillis()}",
                    namespace = request.namespace.toPrefix(),
                    status = "FAILED",
                    error = e.message
                ))
        }
    }
    
    /**
     * Generate mocks from natural language description.
     */
    @PostMapping("/from-description")
    suspend fun generateFromDescription(@Valid @RequestBody request: GenerateFromDescriptionRequest): ResponseEntity<GenerationResponse> {
        logger.info { "Generating mocks from description for namespace: ${request.namespace.displayName()}" }
        
        return try {
            val nlRequest = NaturalLanguageRequest(
                namespace = request.namespace,
                description = request.description,
                useExistingSpec = request.useExistingSpec,
                context = request.context,
                options = request.options
            )
            
            val result = generateFromDescriptionUseCase.execute(nlRequest)
            
            if (result.success) {
                ResponseEntity.ok(GenerationResponse(
                    jobId = result.jobId,
                    namespace = request.namespace.toPrefix(),
                    status = "COMPLETED",
                    mocksGenerated = result.mocksGenerated
                ))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenerationResponse(
                        jobId = result.jobId,
                        namespace = request.namespace.toPrefix(),
                        status = "FAILED",
                        error = result.error
                    ))
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate mocks from description" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenerationResponse(
                    jobId = "error-${System.currentTimeMillis()}",
                    namespace = request.namespace.toPrefix(),
                    status = "FAILED",
                    error = e.message
                ))
        }
    }
    
    /**
     * Generate mocks from API specification enhanced with natural language.
     */
    @PostMapping("/from-spec-with-description")
    suspend fun generateFromSpecWithDescription(@Valid @RequestBody request: GenerateFromSpecWithDescriptionRequest): ResponseEntity<GenerationResponse> {
        logger.info { "Generating enhanced mocks from spec + description for namespace: ${request.namespace.displayName()}" }
        
        return try {
            val specWithDescRequest = SpecWithDescriptionRequest(
                namespace = request.namespace,
                specificationContent = request.specification,
                format = request.format,
                description = request.description,
                options = request.options
            )
            
            val result = generateFromSpecWithDescriptionUseCase.execute(specWithDescRequest)
            
            if (result.success) {
                ResponseEntity.ok(GenerationResponse(
                    jobId = result.jobId,
                    namespace = request.namespace.toPrefix(),
                    status = "COMPLETED",
                    mocksGenerated = result.mocksGenerated
                ))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenerationResponse(
                        jobId = result.jobId,
                        namespace = request.namespace.toPrefix(),
                        status = "FAILED",
                        error = result.error
                    ))
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate enhanced mocks" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GenerationResponse(
                    jobId = "error-${System.currentTimeMillis()}",
                    namespace = request.namespace.toPrefix(),
                    status = "FAILED",
                    error = e.message
                ))
        }
    }
    
    /**
     * Retrieve generated mocks for a job.
     */
    @GetMapping("/jobs/{jobId}/mocks")
    suspend fun getGeneratedMocks(@PathVariable jobId: String): ResponseEntity<MocksResponse> {
        logger.debug { "Retrieving generated mocks for job: $jobId" }
        
        return try {
            val job = generationStorageInterface.getJob(jobId)
            if (job == null) {
                return ResponseEntity.notFound().build()
            }
            
            val mocks = generationStorageInterface.getGeneratedMocks(jobId)
            
            ResponseEntity.ok(MocksResponse(
                jobId = jobId,
                status = job.status.name,
                mocks = mocks.map { mock ->
                    MockResponse(
                        id = mock.id,
                        name = mock.name,
                        wireMockMapping = mock.wireMockMapping,
                        metadata = MockMetadataResponse(
                            sourceType = mock.metadata.sourceType.name,
                            endpoint = EndpointResponse(
                                method = mock.metadata.endpoint.method.name,
                                path = mock.metadata.endpoint.path,
                                statusCode = mock.metadata.endpoint.statusCode,
                                contentType = mock.metadata.endpoint.contentType
                            ),
                            tags = mock.metadata.tags.toList()
                        ),
                        generatedAt = mock.generatedAt.toString()
                    )
                },
                totalMocks = mocks.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve generated mocks for job: $jobId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
    
    /**
     * Get job status and basic information.
     */
    @GetMapping("/jobs/{jobId}/status")
    suspend fun getJobStatus(@PathVariable jobId: String): ResponseEntity<JobStatusResponse> {
        logger.debug { "Retrieving job status: $jobId" }
        
        return try {
            val job = generationStorageInterface.getJob(jobId)
            if (job == null) {
                return ResponseEntity.notFound().build()
            }
            
            ResponseEntity.ok(JobStatusResponse(
                jobId = jobId,
                status = job.status.name,
                createdAt = job.createdAt.toString(),
                completedAt = job.completedAt?.toString(),
                error = job.error,
                mocksGenerated = job.results?.totalGenerated ?: 0
            ))
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve job status: $jobId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
    
    /**
     * Health check for AI generation service.
     */
    @GetMapping("/health")
    suspend fun health(): ResponseEntity<HealthResponse> {
        return try {
            val storageHealthy = generationStorageInterface.isHealthy()
            
            ResponseEntity.ok(HealthResponse(
                status = if (storageHealthy) "healthy" else "degraded",
                services = mapOf(
                    "storage" to if (storageHealthy) "healthy" else "unhealthy",
                    "ai-generation" to "healthy"
                ),
                timestamp = Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error(e) { "Health check failed" }
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(HealthResponse(
                    status = "unhealthy",
                    error = e.message,
                    timestamp = Instant.now().toString()
                ))
        }
    }
}

// Request DTOs
data class GenerateFromSpecRequest(
    val namespace: MockNamespace,
    val specification: String,
    val format: SpecificationFormat,
    val options: GenerationOptions = GenerationOptions.default()
)

data class GenerateFromDescriptionRequest(
    val namespace: MockNamespace,
    val description: String,
    val useExistingSpec: Boolean = false,
    val context: Map<String, String> = emptyMap(),
    val options: GenerationOptions = GenerationOptions.default()
)

data class GenerateFromSpecWithDescriptionRequest(
    val namespace: MockNamespace,
    val specification: String,
    val format: SpecificationFormat,
    val description: String,
    val options: GenerationOptions = GenerationOptions.default()
)

// Response DTOs
data class GenerationResponse(
    val jobId: String,
    val namespace: String,
    val status: String,
    val mocksGenerated: Int = 0,
    val estimatedCompletion: String? = null,
    val error: String? = null
)

data class MocksResponse(
    val jobId: String,
    val status: String,
    val mocks: List<MockResponse>,
    val totalMocks: Int
)

data class MockResponse(
    val id: String,
    val name: String,
    val wireMockMapping: String,
    val metadata: MockMetadataResponse,
    val generatedAt: String
)

data class MockMetadataResponse(
    val sourceType: String,
    val endpoint: EndpointResponse,
    val tags: List<String>
)

data class EndpointResponse(
    val method: String,
    val path: String,
    val statusCode: Int,
    val contentType: String
)

data class JobStatusResponse(
    val jobId: String,
    val status: String,
    val createdAt: String,
    val completedAt: String? = null,
    val error: String? = null,
    val mocksGenerated: Int = 0
)

data class HealthResponse(
    val status: String,
    val services: Map<String, String> = emptyMap(),
    val error: String? = null,
    val timestamp: String
)