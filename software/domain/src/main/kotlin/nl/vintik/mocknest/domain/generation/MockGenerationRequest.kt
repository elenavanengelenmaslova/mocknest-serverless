package nl.vintik.mocknest.domain.generation

import java.util.*

/**
 * Request for generating mocks from API specification + natural language enhancement.
 */
data class SpecWithDescriptionRequest(
    val jobId: String = UUID.randomUUID().toString(),
    val namespace: MockNamespace,
    val specificationContent: String? = null,
    val specificationUrl: String? = null,
    val format: SpecificationFormat,
    val description: String,
    val options: GenerationOptions = GenerationOptions.default()
 ) {
    init {
        require(jobId.isNotBlank()) { "Job ID cannot be blank" }
        require(specificationContent?.isNotBlank()?: false || specificationUrl?.isNotBlank()?: false) { "One of Specification content or URL must be present" }
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
    val enableValidation: Boolean = true
) {
    companion object {
        fun default() = GenerationOptions()
    }
}