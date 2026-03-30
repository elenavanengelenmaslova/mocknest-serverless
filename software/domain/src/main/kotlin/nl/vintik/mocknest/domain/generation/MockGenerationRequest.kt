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
        val hasContent = specificationContent?.isNotBlank() ?: false
        val hasUrl = specificationUrl?.isNotBlank() ?: false
        require(hasContent || hasUrl) { "Either specification content or URL must be provided" }
        require(!(hasContent && hasUrl)) { "Only one of specification content or URL must be provided, not both" }
        require(description.isNotBlank()) { "Description cannot be blank" }
    }
}

/**
 * Supported API specification formats.
 */
enum class SpecificationFormat(val handlesOwnUrlResolution: Boolean) {
    OPENAPI_3(handlesOwnUrlResolution = true),
    SWAGGER_2(handlesOwnUrlResolution = true),
    GRAPHQL(handlesOwnUrlResolution = true),
    WSDL(handlesOwnUrlResolution = true)
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