package io.mocknest.application.generation.usecases

import io.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import io.mocknest.application.generation.interfaces.GenerationStorageInterface
import io.mocknest.domain.generation.*
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Use case for generating mocks from API specification enhanced with natural language.
 * Uses the MockGenerationFunctionalAgent to handle the complex orchestration.
 */
@Component
class GenerateMocksFromSpecWithDescriptionUseCase(
    private val mockGenerationAgent: MockGenerationFunctionalAgent,
    private val generationStorage: GenerationStorageInterface
) {
    
    suspend fun execute(request: SpecWithDescriptionRequest): GenerationResult {
        return try {
            // Create and store job
            val job = GenerationJob(
                id = request.jobId,
                status = JobStatus.IN_PROGRESS,
                request = GenerationJobRequest(
                    type = GenerationType.SPEC_WITH_DESCRIPTION,
                    namespace = request.namespace,
                    specifications = listOf(
                        SpecificationInput(
                            name = request.namespace.displayName(),
                            content = request.specificationContent,
                            format = request.format
                        )
                    ),
                    descriptions = listOf(request.description),
                    options = request.options
                ),
                results = null,
                createdAt = Instant.now()
            )
            generationStorage.storeJob(job)
            
            // Use Functional Agent to handle spec + description generation
            val result = mockGenerationAgent.generateFromSpecWithDescription(request)
            
            if (result.success) {
                // Create and store results
                val mocks = generationStorage.getGeneratedMocks(request.jobId)
                val results = GenerationResults(
                    totalGenerated = result.mocksGenerated,
                    successful = result.mocksGenerated,
                    failed = 0,
                    generatedMocks = mocks,
                    errors = emptyList()
                )
                
                generationStorage.storeJobResults(request.jobId, results)
                generationStorage.updateJobStatus(request.jobId, JobStatus.COMPLETED)
            } else {
                generationStorage.updateJobStatus(request.jobId, JobStatus.FAILED, result.error)
            }
            
            result
            
        } catch (e: Exception) {
            // Update job status to failed
            generationStorage.updateJobStatus(request.jobId, JobStatus.FAILED, e.message)
            
            GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
        }
    }
}