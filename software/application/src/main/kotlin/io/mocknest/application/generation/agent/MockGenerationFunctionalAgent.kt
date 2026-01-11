package io.mocknest.application.generation.agent

import io.mocknest.application.generation.interfaces.*
import io.mocknest.application.generation.usecases.GenerationResult
import io.mocknest.domain.generation.*
import org.springframework.stereotype.Component

/**
 * Koog 0.6.0 Functional Agent for AI-powered mock generation.
 * 
 * This agent orchestrates the mock generation process using Koog's functional strategy:
 * - Parses API specifications
 * - Generates WireMock mappings
 * - Handles natural language interpretation
 * - Enhances specifications with AI
 */
@Component
class MockGenerationFunctionalAgent(
    private val aiModelService: AIModelServiceInterface,
    private val specificationParser: SpecificationParserInterface,
    private val mockGenerator: MockGeneratorInterface,
    private val generationStorage: GenerationStorageInterface
) {
    
    /**
     * Generate mocks from API specification only.
     */
    suspend fun generateFromSpec(request: MockGenerationRequest): GenerationResult {
        return try {
            // Parse specification
            val specification = specificationParser.parse(request.specificationContent, request.format)
            
            // Generate mocks
            val generatedMocks = mockGenerator.generateFromSpecification(
                specification = specification,
                namespace = request.namespace,
                options = request.options
            )
            
            // Store specification if requested
            if (request.options.storeSpecification) {
                generationStorage.storeSpecification(request.namespace, specification)
            }
            
            // Store generated mocks
            generationStorage.storeGeneratedMocks(generatedMocks, request.jobId)
            
            GenerationResult.success(request.jobId, generatedMocks.size)
            
        } catch (e: Exception) {
            GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Generate mocks from natural language description.
     */
    suspend fun generateFromDescription(request: NaturalLanguageRequest): GenerationResult {
        return try {
            // Get existing specification context if requested
            val existingSpec = if (request.useExistingSpec) {
                generationStorage.getSpecification(request.namespace)
            } else null
            
            val enhancedContext = if (existingSpec != null) {
                request.context + ("existingSpecification" to existingSpec.toString())
            } else request.context
            
            // Generate mocks using AI
            val generatedMocks = aiModelService.generateMockFromDescription(
                description = request.description,
                namespace = request.namespace,
                context = enhancedContext
            )
            
            // Store generated mocks
            generationStorage.storeGeneratedMocks(generatedMocks, request.jobId)
            
            GenerationResult.success(request.jobId, generatedMocks.size)
            
        } catch (e: Exception) {
            GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Generate mocks from API specification enhanced with natural language.
     */
    suspend fun generateFromSpecWithDescription(request: SpecWithDescriptionRequest): GenerationResult {
        return try {
            // Parse specification first
            val specification = specificationParser.parse(request.specificationContent, request.format)
            
            // Use AI service to enhance generation with natural language context
            val enhancedMocks = aiModelService.generateMockFromSpecWithDescription(
                specification = specification,
                description = request.description,
                namespace = request.namespace
            )
            
            // Store generated mocks
            generationStorage.storeGeneratedMocks(enhancedMocks, request.jobId)
            
            GenerationResult.success(request.jobId, enhancedMocks.size)
            
        } catch (e: Exception) {
            GenerationResult.failure(request.jobId, e.message ?: "Unknown error occurred")
        }
    }
    
    /**
     * Refine an existing mock based on natural language instructions.
     */
    suspend fun refineMock(existingMock: GeneratedMock, refinementRequest: String): GeneratedMock {
        return try {
            // Use AI service to refine the mock
            aiModelService.refineMock(existingMock, refinementRequest)
        } catch (e: Exception) {
            // Return original mock if refinement fails
            existingMock
        }
    }
}