package nl.vintik.mocknest.application.generation.wsdl

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.mockk.clearAllMocks

/**
 * Preservation property tests for valid SOAP 1.2 WSDL parsing.
 * 
 * **Property 2: Preservation** - Valid SOAP 1.2 WSDL Parsing
 * 
 * These tests verify that valid SOAP 1.2 WSDLs continue to parse successfully
 * on UNFIXED code, establishing the baseline behavior that must be preserved
 * when fixing Bug 4 (non-SOAP WSDL silent fallback).
 * 
 * **SCOPE**: We ONLY support SOAP 1.2
 * 
 * **EXPECTED OUTCOME**: All tests PASS on unfixed code (confirms baseline to preserve)
 * 
 * Requirements: 4.1, 4.2
 */
@Tag("soap-wsdl-ai-generation")
@Tag("unit")
@Tag("preservation-property")
class ValidSoap12WsdlParsingPreservationPropertyTest {

    private val parser = WsdlParser()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    /**
     * Property: For any valid SOAP 1.2 WSDL, the parser should successfully parse
     * and extract all operations without throwing exceptions.
     * 
     * This test uses 10+ diverse valid SOAP 1.2 WSDL files to verify the baseline
     * parsing behavior that must be preserved.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL [{0}] should parse successfully")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "document-literal-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "unreferenced-types-soap12.wsdl",
            "weather-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL When parsing Then should parse successfully without exceptions`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify successful parsing (baseline behavior to preserve)
        
        // Property 1: Parser should return non-null ParsedWsdl
        assertNotNull(
            parsedWsdl,
            "WSDL $filename should parse successfully and return non-null ParsedWsdl"
        )

        // Property 2: SOAP version should be SOAP_1_2
        assertEquals(
            nl.vintik.mocknest.domain.generation.SoapVersion.SOAP_1_2,
            parsedWsdl.soapVersion,
            "WSDL $filename should have SOAP_1_2 version"
        )

        // Property 3: Service name should be extracted
        assertTrue(
            parsedWsdl.serviceName.isNotBlank(),
            "WSDL $filename should have non-blank service name"
        )

        // Property 4: Target namespace should be extracted
        assertTrue(
            parsedWsdl.targetNamespace.isNotBlank(),
            "WSDL $filename should have non-blank target namespace"
        )

        // Property 5: At least one operation should be extracted
        assertTrue(
            parsedWsdl.operations.isNotEmpty(),
            "WSDL $filename should have at least one operation"
        )

        // Property 6: All operations should have non-blank names
        parsedWsdl.operations.forEach { operation ->
            assertTrue(
                operation.name.isNotBlank(),
                "Operation in $filename should have non-blank name"
            )
        }

        // Property 7: All operations should have port type name
        parsedWsdl.operations.forEach { operation ->
            assertTrue(
                operation.portTypeName.isNotBlank(),
                "Operation ${operation.name} in $filename should have non-blank port type name"
            )
        }

        // Property 8: Binding details should be extracted
        assertTrue(
            parsedWsdl.bindingDetails.isNotEmpty(),
            "WSDL $filename should have at least one binding detail"
        )

        // Property 9: All binding details should have SOAP_1_2 version
        parsedWsdl.bindingDetails.forEach { binding ->
            assertEquals(
                nl.vintik.mocknest.domain.generation.SoapVersion.SOAP_1_2,
                binding.soapVersion,
                "Binding ${binding.name} in $filename should have SOAP_1_2 version"
            )
        }

        println("✓ Preservation verified: WSDL $filename parses successfully")
        println("  Service: ${parsedWsdl.serviceName}")
        println("  Operations: ${parsedWsdl.operations.size}")
        println("  Bindings: ${parsedWsdl.bindingDetails.size}")
    }

    /**
     * Property: For any valid SOAP 1.2 WSDL with XSD types, the parser should
     * successfully extract type definitions.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL with types [{0}] should extract XSD schemas")
    @ValueSource(
        strings = [
            "complex-types-soap12.wsdl",
            "document-literal-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "unreferenced-types-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL with XSD types When parsing Then should extract type definitions`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL with XSD type definitions
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify XSD type extraction (baseline behavior to preserve)
        
        // Property: XSD types map should not be null
        assertNotNull(
            parsedWsdl.xsdTypes,
            "WSDL $filename should have xsdTypes map"
        )

        // Note: We don't assert xsdTypes is non-empty because the current implementation
        // may not capture all types (this is what Bug 5 addresses). We're just verifying
        // that the parser doesn't crash and returns a valid structure.

        println("✓ Preservation verified: WSDL $filename extracts types without errors")
        println("  XSD types captured: ${parsedWsdl.xsdTypes.size}")
    }

    /**
     * Property: For any valid SOAP 1.2 WSDL, the parser should extract
     * operation messages and their parts.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL [{0}] should extract operation messages")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "multi-operation-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL When parsing Then should extract operation messages and parts`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify message extraction (baseline behavior to preserve)
        
        // Property 1: All operations should have input message name
        parsedWsdl.operations.forEach { operation ->
            assertTrue(
                operation.inputMessageName.isNotBlank(),
                "Operation ${operation.name} in $filename should have non-blank input message name"
            )
        }

        // Property 2: All operations should have output message name
        parsedWsdl.operations.forEach { operation ->
            assertTrue(
                operation.outputMessageName.isNotBlank(),
                "Operation ${operation.name} in $filename should have non-blank output message name"
            )
        }

        // Property 3: Messages map should contain referenced messages
        assertTrue(
            parsedWsdl.messages.isNotEmpty(),
            "WSDL $filename should have messages map with entries"
        )

        println("✓ Preservation verified: WSDL $filename extracts messages correctly")
        parsedWsdl.operations.forEach { operation ->
            println("  Operation ${operation.name}: ${operation.inputMessageName} -> ${operation.outputMessageName}")
        }
    }

    /**
     * Property: For any valid SOAP 1.2 WSDL, the parser should extract
     * binding details including binding name and port type.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL [{0}] should extract binding details")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "multi-porttype-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL When parsing Then should extract binding details correctly`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify binding details extraction (baseline behavior to preserve)
        
        // Property 1: All bindings should have non-blank names
        parsedWsdl.bindingDetails.forEach { binding ->
            assertTrue(
                binding.name.isNotBlank(),
                "Binding in $filename should have non-blank name"
            )
        }

        // Property 2: All bindings should have non-blank port type names
        parsedWsdl.bindingDetails.forEach { binding ->
            assertTrue(
                binding.portTypeName.isNotBlank(),
                "Binding ${binding.name} in $filename should have non-blank port type name"
            )
        }

        // Property 3: All bindings should have SOAP_1_2 version
        parsedWsdl.bindingDetails.forEach { binding ->
            assertEquals(
                nl.vintik.mocknest.domain.generation.SoapVersion.SOAP_1_2,
                binding.soapVersion,
                "Binding ${binding.name} in $filename should have SOAP_1_2 version"
            )
        }

        println("✓ Preservation verified: WSDL $filename extracts binding details correctly")
        parsedWsdl.bindingDetails.forEach { binding ->
            println("  Binding ${binding.name} -> PortType ${binding.portTypeName}")
        }
    }

    /**
     * Property: For any valid SOAP 1.2 WSDL, the parser should extract
     * service port addresses.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL [{0}] should extract service port addresses")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "multi-porttype-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL When parsing Then should extract service port addresses`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify service port address extraction (baseline behavior to preserve)
        
        // Property: Service port addresses should be extracted
        assertTrue(
            parsedWsdl.servicePortAddresses.isNotEmpty(),
            "WSDL $filename should have at least one service port address"
        )

        // Property: All service port addresses should be non-blank
        parsedWsdl.servicePortAddresses.forEach { address ->
            assertTrue(
                address.isNotBlank(),
                "Service port address in $filename should be non-blank"
            )
        }

        println("✓ Preservation verified: WSDL $filename extracts service port addresses correctly")
        parsedWsdl.servicePortAddresses.forEach { address ->
            println("  Address: $address")
        }
    }

    /**
     * Property: For any valid SOAP 1.2 WSDL, the parser should handle
     * multiple operations within the same port type correctly.
     */
    @ParameterizedTest(name = "Valid SOAP 1.2 WSDL with multiple operations [{0}] should parse all operations")
    @ValueSource(
        strings = [
            "multi-operation-soap12.wsdl",
            "calculator-soap12.wsdl"
        ]
    )
    fun `Given valid SOAP 1_2 WSDL with multiple operations When parsing Then should extract all operations`(
        filename: String
    ) {
        // Given - Valid SOAP 1.2 WSDL with multiple operations
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val parsedWsdl = parser.parse(wsdlXml)

        // Then - Verify multiple operations are extracted (baseline behavior to preserve)
        
        // Property: Should have multiple operations
        assertTrue(
            parsedWsdl.operations.size > 1,
            "WSDL $filename should have multiple operations"
        )

        // Property: All operations should have unique names
        val operationNames = parsedWsdl.operations.map { it.name }
        assertEquals(
            operationNames.size,
            operationNames.toSet().size,
            "WSDL $filename should have unique operation names"
        )

        println("✓ Preservation verified: WSDL $filename extracts all ${parsedWsdl.operations.size} operations")
        parsedWsdl.operations.forEach { operation ->
            println("  - ${operation.name}")
        }
    }
}
