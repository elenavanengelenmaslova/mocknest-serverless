package io.mocknest.application.generation.interfaces

import io.mocknest.domain.generation.APISpecification
import io.mocknest.domain.generation.SpecificationFormat

/**
 * Abstraction for parsing different API specification formats.
 * Implementations handle format-specific parsing logic.
 */
interface SpecificationParserInterface {
    
    /**
     * Parse specification content into domain model.
     */
    suspend fun parse(content: String, format: SpecificationFormat): APISpecification
    
    /**
     * Check if this parser supports the given format.
     */
    fun supports(format: SpecificationFormat): Boolean
    
    /**
     * Validate specification content without full parsing.
     */
    suspend fun validate(content: String, format: SpecificationFormat): ValidationResult
    
    /**
     * Extract basic metadata from specification without full parsing.
     */
    suspend fun extractMetadata(content: String, format: SpecificationFormat): SpecificationMetadata
}

/**
 * Composite parser that delegates to format-specific parsers.
 */
interface CompositeSpecificationParser : SpecificationParserInterface {
    
    /**
     * Register a format-specific parser.
     */
    fun registerParser(parser: SpecificationParserInterface)
    
    /**
     * Get all supported formats.
     */
    fun getSupportedFormats(): Set<SpecificationFormat>
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