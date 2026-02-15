package nl.vintik.mocknest.application.generation.interfaces

import nl.vintik.mocknest.domain.generation.*

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
