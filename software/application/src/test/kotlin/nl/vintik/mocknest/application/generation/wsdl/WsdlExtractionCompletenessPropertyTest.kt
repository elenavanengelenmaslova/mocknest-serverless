package nl.vintik.mocknest.application.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Property-2: WSDL Extraction Completeness
 *
 * For ANY valid WSDL document, the parser+reducer pipeline must extract ALL operations,
 * ALL SOAPActions, and ALL referenced XSD types — nothing is omitted.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-2")
class WsdlExtractionCompletenessPropertyTest {

    private val parser = WsdlParser()
    private val reducer = WsdlSchemaReducer()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then all operation names from ParsedWsdl appear in CompactWsdl`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        val parsedOperationNames = parsedWsdl.operations.map { it.name }.toSet()
        val compactOperationNames = compactWsdl.operations.map { it.name }.toSet()

        logger.info { "[$filename] Checking operation names: parsed=$parsedOperationNames, compact=$compactOperationNames" }

        parsedOperationNames.forEach { opName ->
            assertTrue(
                compactOperationNames.contains(opName),
                "[$filename] Operation '$opName' from ParsedWsdl is missing in CompactWsdl.operations"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then all SOAPAction values from ParsedWsdl appear in CompactWsdl`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        val parsedSoapActions = parsedWsdl.operations.map { it.soapAction }.toSet()
        val compactSoapActions = compactWsdl.operations.map { it.soapAction }.toSet()

        logger.info { "[$filename] Checking SOAPActions: parsed=$parsedSoapActions, compact=$compactSoapActions" }

        parsedSoapActions.forEach { soapAction ->
            assertTrue(
                compactSoapActions.contains(soapAction),
                "[$filename] SOAPAction '$soapAction' from ParsedWsdl is missing in CompactWsdl.operations"
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then all XSD types referenced by operations appear in CompactWsdl`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        // Collect all type names directly referenced by operation input/output messages
        val directlyReferencedTypeNames = parsedWsdl.operations
            .flatMap { listOf(it.inputMessageName, it.outputMessageName) }
            .filter { it.isNotBlank() }
            .toSet()

        logger.info { "[$filename] Checking directly referenced XSD types: $directlyReferencedTypeNames" }

        // Each directly referenced type that exists in parsedWsdl.xsdTypes must appear in compactWsdl.xsdTypes
        directlyReferencedTypeNames
            .filter { parsedWsdl.xsdTypes.containsKey(it) }
            .forEach { typeName ->
                assertTrue(
                    compactWsdl.xsdTypes.containsKey(typeName),
                    "[$filename] XSD type '$typeName' referenced by an operation message is missing from CompactWsdl.xsdTypes"
                )
            }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then CompactWsdl operations is non-empty`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        logger.info { "[$filename] Operations count: ${compactWsdl.operations.size}" }

        assertFalse(
            compactWsdl.operations.isEmpty(),
            "[$filename] CompactWsdl.operations must be non-empty"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then CompactWsdl serviceName is non-blank`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        logger.info { "[$filename] Service name: '${compactWsdl.serviceName}'" }

        assertTrue(
            compactWsdl.serviceName.isNotBlank(),
            "[$filename] CompactWsdl.serviceName must be non-blank"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "simple-soap11.wsdl",
        "simple-soap12.wsdl",
        "multi-operation-soap11.wsdl",
        "multi-porttype-soap11.wsdl",
        "complex-types-soap11.wsdl",
        "nested-xsd-soap11.wsdl",
        "multi-operation-soap12.wsdl",
        "large-service.wsdl",
        "calculator-soap11.wsdl",
        "weather-soap12.wsdl"
    ])
    fun `Given any valid WSDL When parsing and reducing Then CompactWsdl targetNamespace is non-blank`(filename: String) {
        val wsdlXml = loadWsdl(filename)
        val parsedWsdl = parser.parse(wsdlXml)
        val compactWsdl = reducer.reduce(parsedWsdl)

        logger.info { "[$filename] Target namespace: '${compactWsdl.targetNamespace}'" }

        assertTrue(
            compactWsdl.targetNamespace.isNotBlank(),
            "[$filename] CompactWsdl.targetNamespace must be non-blank"
        )
    }
}
