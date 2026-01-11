package io.mocknest.domain.generation

import java.util.*

/**
 * Request for generating mocks from API specifications.
 */
data class MockGenerationRequest(
    val jobId: String = UUID.randomUUID().toString(),
    val namespace: MockNamespace,
    val specificationContent: String,
    val format: SpecificationFormat,
    val options: GenerationOptions = GenerationOptions.default()
) {
    init {
        require(jobId.isNotBlank()) { "Job ID cannot be blank" }
        require(specificationContent.isNotBlank()) { "Specification content cannot be blank" }
    }
}

/**
 * Request for generating mocks from natural language descriptions.
 */
data class NaturalLanguageRequest(
    val jobId: String = UUID.randomUUID().toString(),
    val namespace: MockNamespace,
    val description: String,
    val useExistingSpec: Boolean = false,    // Use stored API spec as context
    val context: Map<String, String> = emptyMap(),
    val options: GenerationOptions = GenerationOptions.default()
) {
    init {
        require(jobId.isNotBlank()) { "Job ID cannot be blank" }
        require(description.isNotBlank()) { "Description cannot be blank" }
    }
}

/**
 * Request for generating mocks from API specification + natural language enhancement.
 */
data class SpecWithDescriptionRequest(
    val jobId: String = UUID.randomUUID().toString(),
    val namespace: MockNamespace,
    val specificationContent: String,
    val format: SpecificationFormat,
    val description: String,
    val options: GenerationOptions = GenerationOptions.default()
) {
    init {
        require(jobId.isNotBlank()) { "Job ID cannot be blank" }
        require(specificationContent.isNotBlank()) { "Specification content cannot be blank" }
        require(description.isNotBlank()) { "Description cannot be blank" }
    }
}

/**
 * Supported API specification formats.
 */
enum class SpecificationFormat {
    OPENAPI_3, SWAGGER_2, GRAPHQL, WSDL
}

/**
 * Options for controlling mock generation behavior.
 */
data class GenerationOptions(
    val includeExamples: Boolean = true,
    val generateErrorCases: Boolean = true,
    val realisticData: Boolean = true,
    val storeSpecification: Boolean = true,  // Store API spec for future use
    val enhanceExisting: Boolean = false,    // Enhance existing mocks vs create new
    val preserveCustomizations: Boolean = true
) {
    companion object {
        fun default() = GenerationOptions()
    }
}