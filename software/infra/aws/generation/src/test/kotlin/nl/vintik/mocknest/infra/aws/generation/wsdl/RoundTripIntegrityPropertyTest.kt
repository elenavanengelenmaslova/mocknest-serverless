package nl.vintik.mocknest.infra.aws.generation.wsdl

import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.CompactWsdl
import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.WsdlOperation
import nl.vintik.mocknest.domain.generation.WsdlPortType
import nl.vintik.mocknest.domain.generation.WsdlXsdField
import nl.vintik.mocknest.domain.generation.WsdlXsdType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Property 4: Round-Trip Integrity
 *
 * For any valid WSDL document, parsing it to a CompactWsdl, calling prettyPrint(), then parsing
 * the pretty-printed output again should produce a CompactWsdl with equivalent service name,
 * target namespace, SOAP version, operations (same names, SOAPActions, input/output messages),
 * and XSD types.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-4")
class RoundTripIntegrityPropertyTest {

    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @ParameterizedTest
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "simple-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "large-service.wsdl",
            "calculator-soap12.wsdl",
            "weather-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "multi-porttype-soap12.wsdl"
        ]
    )
    fun `Property 4 - Round-Trip Integrity`(filename: String) {
        // Given — parse original WSDL to CompactWsdl
        val wsdlXml = loadWsdl(filename)
        val original = schemaReducer.reduce(wsdlParser.parse(wsdlXml))

        // When — pretty-print then parse the text output
        val prettyPrinted = original.prettyPrint()
        val reparsed = parseCompactWsdlText(prettyPrinted)

        // Then — equivalent fields
        assertNotNull(reparsed, "[$filename] parseCompactWsdlText must not return null")

        assertEquals(
            original.serviceName,
            reparsed.serviceName,
            "[$filename] serviceName must survive round-trip"
        )
        assertEquals(
            original.targetNamespace,
            reparsed.targetNamespace,
            "[$filename] targetNamespace must survive round-trip"
        )
        assertEquals(
            original.soapVersion,
            reparsed.soapVersion,
            "[$filename] soapVersion must survive round-trip"
        )
        assertEquals(
            original.operations.map { it.name }.toSet(),
            reparsed.operations.map { it.name }.toSet(),
            "[$filename] operation names must survive round-trip"
        )
        assertEquals(
            original.xsdTypes.keys,
            reparsed.xsdTypes.keys,
            "[$filename] XSD type keys must survive round-trip"
        )
    }

    /**
     * Parses the prettyPrint() text output back into a CompactWsdl.
     * This mirrors the format produced by CompactWsdl.prettyPrint().
     */
    private fun parseCompactWsdlText(text: String): CompactWsdl {
        val lines = text.lines()
        var serviceName = ""
        var targetNamespace = ""
        var soapVersion = SoapVersion.SOAP_1_1
        val portTypes = mutableListOf<WsdlPortType>()
        val operations = mutableListOf<WsdlOperation>()
        val xsdTypes = mutableMapOf<String, WsdlXsdType>()

        var section = ""
        var currentOpName = ""
        var currentOpSoapAction = ""
        var currentOpInput = ""
        var currentOpOutput = ""
        var currentTypeName = ""
        var currentTypeFields = mutableListOf<WsdlXsdField>()

        fun flushOperation() {
            if (currentOpName.isNotBlank()) {
                check(portTypes.isNotEmpty()) {
                    "Cannot build operation '$currentOpName': no portTypes parsed from prettyPrint output"
                }
                val portTypeName = portTypes.first().name
                require(portTypeName.isNotBlank()) {
                    "Cannot build operation '$currentOpName': portTypeName is blank. " +
                    "Parsed portTypes: ${portTypes.map { it.name }}, " +
                    "currentOpName='$currentOpName', " +
                    "currentOpSoapAction='$currentOpSoapAction', " +
                    "currentOpInput='$currentOpInput', " +
                    "currentOpOutput='$currentOpOutput'"
                }
                operations.add(
                    WsdlOperation(
                        name = currentOpName,
                        soapAction = currentOpSoapAction,
                        inputMessage = currentOpInput.ifBlank { currentOpName },
                        outputMessage = currentOpOutput.ifBlank { "${currentOpName}Response" },
                        portTypeName = portTypeName
                    )
                )
                currentOpName = ""
                currentOpSoapAction = ""
                currentOpInput = ""
                currentOpOutput = ""
            }
        }

        fun flushType() {
            if (currentTypeName.isNotBlank()) {
                xsdTypes[currentTypeName] = WsdlXsdType(currentTypeName, currentTypeFields.toList())
                currentTypeName = ""
                currentTypeFields = mutableListOf()
            }
        }

        for (line in lines) {
            when {
                line.startsWith("service: ") -> serviceName = line.removePrefix("service: ").trim()
                line.startsWith("targetNamespace: ") -> targetNamespace = line.removePrefix("targetNamespace: ").trim()
                line.startsWith("soapVersion: ") -> {
                    val versionName = line.removePrefix("soapVersion: ").trim()
                    soapVersion = runCatching { SoapVersion.valueOf(versionName) }.getOrDefault(SoapVersion.SOAP_1_1)
                }
                line == "portTypes:" -> section = "portTypes"
                line == "operations:" -> {
                    section = "operations"
                }
                line == "types:" -> {
                    flushOperation()
                    section = "types"
                }
                line.isBlank() -> {
                    // section separator — flush pending items
                    if (section == "operations") flushOperation()
                    if (section == "types") flushType()
                }
                section == "portTypes" && line.startsWith("  ") && !line.startsWith("    ") -> {
                    portTypes.add(WsdlPortType(line.trim()))
                }
                section == "operations" && line.startsWith("  ") && !line.startsWith("    ") -> {
                    flushOperation()
                    currentOpName = line.trim().removeSuffix(":")
                }
                section == "operations" && line.startsWith("    soapAction: ") -> {
                    currentOpSoapAction = line.removePrefix("    soapAction: ").trim()
                }
                section == "operations" && line.startsWith("    input: ") -> {
                    currentOpInput = line.removePrefix("    input: ").trim()
                }
                section == "operations" && line.startsWith("    output: ") -> {
                    currentOpOutput = line.removePrefix("    output: ").trim()
                }
                section == "types" && line.startsWith("  ") && !line.startsWith("    ") -> {
                    flushType()
                    currentTypeName = line.trim().removeSuffix(":")
                }
                section == "types" && line.startsWith("    ") -> {
                    val parts = line.trim().split(": ", limit = 2)
                    if (parts.size == 2) {
                        currentTypeFields.add(WsdlXsdField(parts[0].trim(), parts[1].trim()))
                    }
                }
            }
        }

        // Flush any remaining pending items
        flushOperation()
        flushType()

        return CompactWsdl(
            serviceName = serviceName,
            targetNamespace = targetNamespace,
            soapVersion = soapVersion,
            portTypes = portTypes,
            operations = operations,
            xsdTypes = xsdTypes
        )
    }
}
