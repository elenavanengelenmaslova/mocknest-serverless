package io.mocknest.domain.generation

import java.time.Instant

/**
 * Represents an asynchronous mock generation job.
 */
data class GenerationJob(
    val id: String,
    val status: JobStatus,
    val request: GenerationJobRequest,
    val results: GenerationResults?,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val error: String? = null
) {
    init {
        require(id.isNotBlank()) { "Job ID cannot be blank" }
        require(completedAt == null || completedAt >= createdAt) { 
            "Completion time cannot be before creation time" 
        }
    }
}

/**
 * Request details for a generation job.
 */
data class GenerationJobRequest(
    val type: GenerationType,
    val namespace: MockNamespace,
    val specifications: List<SpecificationInput> = emptyList(),
    val descriptions: List<String> = emptyList(),
    val options: GenerationOptions
) {
    init {
        when (type) {
            GenerationType.SPECIFICATION -> {
                require(specifications.isNotEmpty()) { "Specification generation requires at least one specification" }
                require(descriptions.isEmpty()) { "Specification generation should not have descriptions" }
            }
            GenerationType.NATURAL_LANGUAGE -> {
                require(descriptions.isNotEmpty()) { "Natural language generation requires at least one description" }
                require(specifications.isEmpty()) { "Natural language generation should not have specifications" }
            }
            GenerationType.SPEC_WITH_DESCRIPTION -> {
                require(specifications.isNotEmpty()) { "Spec with description generation requires at least one specification" }
                require(descriptions.isNotEmpty()) { "Spec with description generation requires at least one description" }
            }
            GenerationType.BATCH -> {
                require(specifications.isNotEmpty() || descriptions.isNotEmpty()) { 
                    "Batch generation requires at least one specification or description" 
                }
            }
            GenerationType.EVOLUTION -> {
                require(specifications.isNotEmpty()) { "Evolution generation requires at least one specification" }
            }
        }
    }
}

/**
 * Input specification for generation.
 */
data class SpecificationInput(
    val name: String,
    val content: String,
    val format: SpecificationFormat
) {
    init {
        require(name.isNotBlank()) { "Specification name cannot be blank" }
        require(content.isNotBlank()) { "Specification content cannot be blank" }
    }
}

/**
 * Results of a generation job.
 */
data class GenerationResults(
    val totalGenerated: Int,
    val successful: Int,
    val failed: Int,
    val generatedMocks: List<GeneratedMock>,
    val errors: List<GenerationError> = emptyList()
) {
    init {
        require(totalGenerated >= 0) { "Total generated cannot be negative" }
        require(successful >= 0) { "Successful count cannot be negative" }
        require(failed >= 0) { "Failed count cannot be negative" }
        require(totalGenerated == successful + failed) { 
            "Total generated must equal successful + failed" 
        }
        require(generatedMocks.size == successful) { 
            "Generated mocks count must match successful count" 
        }
    }
}

/**
 * Error that occurred during generation.
 */
data class GenerationError(
    val type: ErrorType,
    val message: String,
    val source: String? = null,
    val details: Map<String, String> = emptyMap()
) {
    init {
        require(message.isNotBlank()) { "Error message cannot be blank" }
    }
}

/**
 * Status of a generation job.
 */
enum class JobStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
}

/**
 * Type of generation being performed.
 */
enum class GenerationType {
    SPECIFICATION,          // Generate from API specification only
    NATURAL_LANGUAGE,       // Generate from natural language only
    SPEC_WITH_DESCRIPTION,  // Generate from spec + natural language
    BATCH,                  // Generate from multiple inputs
    EVOLUTION               // Generate from spec evolution
}

/**
 * Type of error that occurred during generation.
 */
enum class ErrorType {
    SPECIFICATION_PARSING,  // Error parsing API specification
    AI_MODEL_ERROR,        // Error from AI model service
    MOCK_GENERATION,       // Error generating WireMock mapping
    STORAGE_ERROR,         // Error storing results
    VALIDATION_ERROR,      // Error validating input or output
    TIMEOUT_ERROR,         // Generation timed out
    UNKNOWN_ERROR          // Unknown or unexpected error
}