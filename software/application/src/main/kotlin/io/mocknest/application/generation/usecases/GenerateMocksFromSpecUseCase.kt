package io.mocknest.application.generation.usecases

import io.mocknest.application.generation.interfaces.*
import io.mocknest.domain.generation.*
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Use case for generating mocks from API specifications only.
 * Follows clean architecture principles with dependency inversion.
 */
@Component
class GenerateMocksFromSpecUseCase(
    private val specificationParser: SpecificationParserInterface,
    private val mockGenerator: MockGeneratorInterface,
    private val generationStorage: GenerationStorageInterface
) {
    
    suspend fun execute(request: MockGenerationRequest): GenerationResult {
        return try {
            // Create and store job
            val job = GenerationJob(
                id = request.jobId,
                status = JobStatus.IN_PROGRESS,
                request = GenerationJobRequest(
                    type = GenerationType.SPECIFICATION,
                    namespace = request.namespace,
                    specifications = listOf(
                        SpecificationInput(
                            name = request.namespace.displayName(),
                            content = request.specificationContent,
                            format = request.format
                        )
                    ),
                    descriptions = emptyList(),
                    options = request.options
                ),
                results = null,
                createdAt = Instant.now()
            )
            generationStorage.storeJob(job)
            
            // Parse specification
            val specification = specificationParser.parse(request.specificationContent, request.format)
            
            // Generate mocks
            val generatedMocks = mockGenerator.generateFromSpecification(
                specification = specification,
                namespace = request.namespace,
                options = request.options
            )
            
            // Store API specification for future evolution if requested
            if (request.options.storeSpecification) {
                generationStorage.storeSpecification(request.namespace, specification)
            }
            
            // Store generated mocks
            generationStorage.storeGeneratedMocks(generatedMocks, request.jobId)
            
            // Create and store results
            val results = GenerationResults(
                totalGenerated = generatedMocks.size,
                successful = generatedMocks.size,
                failed = 0,
                generatedMocks = generatedMocks,
                errors = emptyList()
            )
            
            generationStorage.storeJobResults(request.jobId, results)
            generationStorage.updateJobStatus(request.jobId, JobStatus.COMPLETED)
            
            GenerationResult.success(request.jobId, generatedMocks.size)
            
        } catch (e: Exception) {
            // Update job status to failed
            generationStorage.updateJobStatus(request.jobId, JobStatus.FAILED, e.message)
            
            GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
        }
    }
}

/**
 * Result of a mock generation operation.
 */
data class GenerationResult(
    val jobId: String,
    val success: Boolean,
    val mocksGenerated: Int = 0,
    val error: String? = null
) {
    companion object {
        fun success(jobId: String, mocksGenerated: Int) = 
            GenerationResult(jobId, true, mocksGenerated)
        
        fun failure(jobId: String, error: String) = 
            GenerationResult(jobId, false, error = error)
    }
}