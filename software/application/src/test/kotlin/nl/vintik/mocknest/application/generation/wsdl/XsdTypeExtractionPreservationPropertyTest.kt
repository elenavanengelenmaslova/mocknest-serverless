package nl.vintik.mocknest.application.generation.wsdl

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 2: Preservation - Named ComplexType Extraction
 *
 * This test validates that the UNFIXED WsdlParser correctly extracts named complexType definitions
 * and nested schema elements. These tests MUST PASS on unfixed code to confirm baseline behavior
 * that must be preserved when fixing Bug 5 (missing top-level XSD elements).
 *
 * Test Strategy:
 * - Observe behavior on UNFIXED code for WSDLs with named complexType definitions
 * - Observe behavior on UNFIXED code for WSDLs with nested schema elements
 * - Verify all named complexTypes are captured correctly
 * - Verify nested type references are extracted
 *
 * Expected Outcome: All tests PASS on unfixed code (confirms baseline to preserve)
 */
class XsdTypeExtractionPreservationPropertyTest {

    private val parser = WsdlParser()

    /**
     * Property: Named complexType definitions are extracted correctly
     *
     * Tests multiple WSDL files with various named complexType patterns to ensure
     * the parser correctly captures all type definitions.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl",
            "unreferenced-types-soap12.wsdl",
            "weather-soap12.wsdl",
            "simple-soap12.wsdl",
            "large-service.wsdl"
        ]
    )
    fun `Given WSDL with named complexType definitions When parsing Then should extract all types correctly`(
        filename: String
    ) {
        // Given: WSDL with named complexType definitions
        val wsdlContent = loadTestWsdl(filename)

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: Named complexTypes should be extracted
        // Note: We don't assert specific counts because different WSDLs have different structures
        // We verify that xsdTypes map is populated (may be empty for simple WSDLs without types)
        assertNotNull(result.xsdTypes, "xsdTypes map should not be null for $filename")

        // For WSDLs known to have named complexTypes, verify they are captured
        when (filename) {
            "calculator-soap12.wsdl" -> {
                assertTrue(
                    result.xsdTypes.containsKey("AddRequest"),
                    "Should extract AddRequest complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.containsKey("AddResponse"),
                    "Should extract AddResponse complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.size >= 6,
                    "Should extract at least 6 complexTypes from $filename (Add, Subtract, Multiply request/response)"
                )
            }

            "complex-types-soap12.wsdl" -> {
                assertTrue(
                    result.xsdTypes.containsKey("GetOrder"),
                    "Should extract GetOrder complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Order"),
                    "Should extract Order complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Customer"),
                    "Should extract Customer complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.size >= 4,
                    "Should extract at least 4 complexTypes from $filename"
                )
            }

            "nested-xsd-soap12.wsdl" -> {
                assertTrue(
                    result.xsdTypes.containsKey("Report"),
                    "Should extract Report complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Author"),
                    "Should extract Author complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Contact"),
                    "Should extract Contact complexType from $filename"
                )
                assertTrue(
                    result.xsdTypes.size >= 3,
                    "Should extract at least 3 nested complexTypes from $filename"
                )
            }

            "unreferenced-types-soap12.wsdl" -> {
                // This WSDL has unreferenced types that should still be extracted
                assertTrue(
                    result.xsdTypes.isNotEmpty(),
                    "Should extract complexTypes even if unreferenced from $filename"
                )
            }
        }
    }

    /**
     * Property: Nested schema elements are extracted correctly
     *
     * Verifies that complexTypes with nested type references (e.g., Customer type
     * containing nested Address type) are correctly extracted with all fields.
     */
    @Test
    fun `Given WSDL with nested schema elements When parsing Then should extract nested types`() {
        // Given: WSDL with nested schema elements
        val wsdlContent = loadTestWsdl("nested-xsd-soap12.wsdl")

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: Nested types should be extracted
        val reportType = result.xsdTypes["Report"]
        assertNotNull(reportType, "Report type should be extracted")
        assertTrue(
            reportType.fields.any { it.name == "author" },
            "Report should have author field"
        )

        val authorType = result.xsdTypes["Author"]
        assertNotNull(authorType, "Author type should be extracted")
        assertTrue(
            authorType.fields.any { it.name == "contact" },
            "Author should have contact field"
        )

        val contactType = result.xsdTypes["Contact"]
        assertNotNull(contactType, "Contact type should be extracted")
        assertTrue(
            contactType.fields.any { it.name == "email" },
            "Contact should have email field"
        )
        assertTrue(
            contactType.fields.any { it.name == "phone" },
            "Contact should have phone field"
        )
    }

    /**
     * Property: ComplexType fields are extracted correctly
     *
     * Verifies that fields within complexType definitions are correctly extracted
     * with proper names and types.
     */
    @Test
    fun `Given WSDL with complexType fields When parsing Then should extract all fields correctly`() {
        // Given: WSDL with complexType containing multiple fields
        val wsdlContent = loadTestWsdl("complex-types-soap12.wsdl")

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: Fields should be extracted correctly
        val orderType = result.xsdTypes["Order"]
        assertNotNull(orderType, "Order type should be extracted")
        assertTrue(
            orderType.fields.size >= 3,
            "Order should have at least 3 fields (id, status, customer)"
        )
        assertTrue(
            orderType.fields.any { it.name == "id" },
            "Order should have id field"
        )
        assertTrue(
            orderType.fields.any { it.name == "status" },
            "Order should have status field"
        )
        assertTrue(
            orderType.fields.any { it.name == "customer" },
            "Order should have customer field"
        )

        val customerType = result.xsdTypes["Customer"]
        assertNotNull(customerType, "Customer type should be extracted")
        assertTrue(
            customerType.fields.size >= 2,
            "Customer should have at least 2 fields (name, email)"
        )
        assertTrue(
            customerType.fields.any { it.name == "name" },
            "Customer should have name field"
        )
        assertTrue(
            customerType.fields.any { it.name == "email" },
            "Customer should have email field"
        )
    }

    /**
     * Property: Unreferenced types are still extracted
     *
     * Verifies that complexType definitions are extracted even if they are not
     * referenced by any message or operation.
     */
    @Test
    fun `Given WSDL with unreferenced types When parsing Then should extract all types including unreferenced`() {
        // Given: WSDL with unreferenced complexType definitions
        val wsdlContent = loadTestWsdl("unreferenced-types-soap12.wsdl")

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: All types should be extracted, including unreferenced ones
        assertNotNull(result.xsdTypes, "xsdTypes map should not be null")
        assertTrue(
            result.xsdTypes.isNotEmpty(),
            "Should extract types even if unreferenced"
        )
    }

    /**
     * Property: Multiple operations with different types are handled correctly
     *
     * Verifies that WSDLs with multiple operations, each with their own request/response
     * types, have all types extracted correctly.
     */
    @Test
    fun `Given WSDL with multiple operations When parsing Then should extract all operation types`() {
        // Given: WSDL with multiple operations
        val wsdlContent = loadTestWsdl("calculator-soap12.wsdl")

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: All operation types should be extracted
        assertTrue(
            result.xsdTypes.containsKey("AddRequest"),
            "Should extract AddRequest type"
        )
        assertTrue(
            result.xsdTypes.containsKey("AddResponse"),
            "Should extract AddResponse type"
        )
        assertTrue(
            result.xsdTypes.containsKey("SubtractRequest"),
            "Should extract SubtractRequest type"
        )
        assertTrue(
            result.xsdTypes.containsKey("SubtractResponse"),
            "Should extract SubtractResponse type"
        )
        assertTrue(
            result.xsdTypes.containsKey("MultiplyRequest"),
            "Should extract MultiplyRequest type"
        )
        assertTrue(
            result.xsdTypes.containsKey("MultiplyResponse"),
            "Should extract MultiplyResponse type"
        )
    }

    /**
     * Property: Type field types are preserved correctly
     *
     * Verifies that field type references (e.g., xsd:string, xsd:double, tns:CustomType)
     * are correctly preserved in the extracted type definitions.
     */
    @Test
    fun `Given WSDL with various field types When parsing Then should preserve field type references`() {
        // Given: WSDL with various field types
        val wsdlContent = loadTestWsdl("calculator-soap12.wsdl")

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: Field types should be preserved
        val addRequest = result.xsdTypes["AddRequest"]
        assertNotNull(addRequest, "AddRequest type should be extracted")
        val aField = addRequest.fields.find { it.name == "a" }
        assertNotNull(aField, "AddRequest should have 'a' field")
        assertTrue(
            aField.type.contains("double"),
            "Field 'a' should have double type, got: ${aField.type}"
        )
    }

    private fun loadTestWsdl(filename: String): String {
        return this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("Test WSDL file not found: $filename")
    }
}
