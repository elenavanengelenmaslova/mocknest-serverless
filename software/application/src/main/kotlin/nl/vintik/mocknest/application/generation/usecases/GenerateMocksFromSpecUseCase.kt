package nl.vintik.mocknest.application.generation.usecases

import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.domain.generation.*
import java.time.Instant

/**
 * Use case for generating mocks from API specifications only.
 * Follows clean architecture principles with dependency inversion.
 */
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