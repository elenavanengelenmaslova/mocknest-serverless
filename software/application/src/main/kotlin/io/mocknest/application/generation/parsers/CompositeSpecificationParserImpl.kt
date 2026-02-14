package io.mocknest.application.generation.parsers

import io.mocknest.application.generation.interfaces.CompositeSpecificationParser
import io.mocknest.application.generation.interfaces.SpecificationParserInterface
import io.mocknest.domain.generation.*

/**
 * Composite parser that delegates to format-specific parsers.
 * Automatically registers available parsers and routes requests to the appropriate one.
 */
class CompositeSpecificationParserImpl(
    parsers: List<SpecificationParserInterface>
) : CompositeSpecificationParser {
    
    private val parserMap = mutableMapOf<SpecificationFormat, SpecificationParserInterface>()
    
    init {
        // Register all available parsers
        parsers.forEach { parser ->
            SpecificationFormat.entries.forEach { format ->
                if (parser.supports(format)) {
                    parserMap[format] = parser
                }
            }
        }
    }
    
    override suspend fun parse(content: String, format: SpecificationFormat): APISpecification {
        val parser = parserMap[format] 
            ?: throw UnsupportedOperationException("No parser available for format: $format")
        
        return parser.parse(content, format)
    }
    
    override fun supports(format: SpecificationFormat): Boolean {
        return parserMap.containsKey(format)
    }
    
    override suspend fun validate(content: String, format: SpecificationFormat): ValidationResult {
        val parser = parserMap[format] 
            ?: return ValidationResult.invalid(listOf(
                ValidationError("No parser available for format: $format")
            ))
        
        return parser.validate(content, format)
    }
    
    override suspend fun extractMetadata(content: String, format: SpecificationFormat): SpecificationMetadata {
        val parser = parserMap[format] 
            ?: throw UnsupportedOperationException("No parser available for format: $format")
        
        return parser.extractMetadata(content, format)
    }
    
    override fun registerParser(parser: SpecificationParserInterface) {
        SpecificationFormat.entries.forEach { format ->
            if (parser.supports(format)) {
                parserMap[format] = parser
            }
        }
    }
    
    override fun getSupportedFormats(): Set<SpecificationFormat> {
        return parserMap.keys.toSet()
    }
}