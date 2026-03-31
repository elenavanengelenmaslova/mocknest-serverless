package nl.vintik.mocknest.application.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Property-3: Schema Size Reduction
 *
 * For ANY valid WSDL document with multiple operations, the character count of
 * CompactWsdl.prettyPrint() must be strictly less than the character count of the raw WSDL XML.
 *
 * Validates: Requirement 4.7
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-3")
class WsdlSchemaSizeReductionPropertyTest {

    private val parser = WsdlParser()
    private val reducer = WsdlSchemaReducer()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @ParameterizedTest
    @ValueSource(strings = [
        "multi-operation-soap12.wsdl",
        "multi-porttype-soap12.wsdl",
        "complex-types-soap12.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap12.wsdl",
        "weather-soap12.wsdl",
        "nested-xsd-soap12.wsdl"
    ])
    fun `Given a multi-operation WSDL When reducing to CompactWsdl Then prettyPrint length is less than raw WSDL length`(filename: String) {
        val rawWsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(rawWsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)
        val prettyPrinted = compactWsdl.prettyPrint()

        val rawLength = rawWsdlXml.length
        val compactLength = prettyPrinted.length

        logger.info {
            "[$filename] Size reduction: $rawLength -> $compactLength chars " +
                "(${((rawLength - compactLength) * 100.0 / rawLength).toInt()}% reduction)"
        }

        assertTrue(
            compactLength < rawLength,
            "[$filename] CompactWsdl.prettyPrint() length ($compactLength) must be less than raw WSDL length ($rawLength)"
        )
    }
}
