package nl.vintik.mocknest.domain.generation

// Request DTOs
data class GenerateFromSpecRequest(
    val namespace: MockNamespace,
    val specification: String? = null,
    val specificationUrl: String? = null,
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
    val specification: String? = null,
    val specificationUrl: String? = null,
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
    val mappings: List<Any>
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

/**
 * Result of a mock generation operation.
 */
data class GenerationResult(
    val jobId: String,
    val success: Boolean,
    val mocksGenerated: Int = 0,
    val mocks: List<GeneratedMock> = emptyList(),
    val error: String? = null
) {
    companion object {
        fun success(jobId: String, mocks: List<GeneratedMock>) =
            GenerationResult(jobId, true, mocks.size, mocks)

        fun failure(jobId: String, error: String) =
            GenerationResult(jobId, false, error = error)
    }
}

/**
 * Result of specification validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList()
) {
    companion object {
        fun valid() = ValidationResult(isValid = true)
        fun invalid(errors: List<ValidationError>) = ValidationResult(isValid = false, errors = errors)
    }
}

/**
 * Validation error in specification.
 */
data class ValidationError(
    val message: String,
    val path: String? = null,
    val line: Int? = null,
    val column: Int? = null
)

/**
 * Validation warning in specification.
 */
data class ValidationWarning(
    val message: String,
    val path: String? = null,
    val line: Int? = null,
    val column: Int? = null
)

/**
 * Basic metadata extracted from specification.
 */
data class SpecificationMetadata(
    val title: String,
    val version: String,
    val format: SpecificationFormat,
    val endpointCount: Int,
    val schemaCount: Int
)
