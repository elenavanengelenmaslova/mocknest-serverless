package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test for Bug 5: Missing Top-Level XSD Elements
 *
 * **Bug Condition (C)**: WsdlParser only captures named complexType definitions,
 * missing top-level xsd:element declarations with inline complexType.
 *
 * **Property 1: Bug Condition** - Missing Top-Level XSD Elements
 * For any WSDL input containing top-level <xsd:element name="..."> declarations
 * with inline complexType, the UNFIXED WsdlParser would only process
 * <xsd:complexType name="..."> children, causing top-level elements to be missing
 * from ParsedWsdl.xsdTypes.
 *
 * **CRITICAL**: This test was written AFTER the bug was fixed. On UNFIXED code,
 * this test would have FAILED because:
 * - extractXsdTypes() only looked for <xsd:complexType name="..."> children
 * - Top-level <xsd:element> declarations were completely ignored
 * - xsdTypes map would be empty for document-literal WSDLs
 * - Message parts referencing elements like "tns:GetOrder" would have no type info
 *
 * **Expected Behavior**: After fix, WsdlParser captures both:
 * 1. Named complexType definitions: <xsd:complexType name="...">
 * 2. Top-level element declarations: <xsd:element name="..."> with inline complexType
 *
 * **Test Strategy**: Load document-literal-soap12.wsdl which uses top-level elements
 * and verify they are captured in xsdTypes map with correct fields.
 *
 * **Counterexample Documentation** (what would have failed on unfixed code):
 * - "GetOrder element with inline complexType not captured in xsdTypes"
 * - "GetOrderResponse element with inline complexType not captured in xsdTypes"
 * - "xsdTypes map empty despite WSDL containing request/response schemas"
 * - "AI prompt would lack schema information, generating incorrect mocks"
 */
class MissingTopLevelXsdElementsBugTest {

    private val parser = WsdlParser()

    private fun loadWsdl(filename: String): String {
        return this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw WsdlParsingException("Test WSDL file not found: $filename")
    }

    @Test
    fun `Given document-literal WSDL with top-level xsd elements When parsing Then should capture element types in xsdTypes map`() {
        // ARRANGE: Load WSDL with top-level <xsd:element> declarations
        // This WSDL has:
        // - <xsd:element name="GetOrder"> with inline complexType
        // - <xsd:element name="GetOrderResponse"> with inline complexType
        // - NO named <xsd:complexType name="..."> definitions
        val wsdlContent = loadWsdl("document-literal-soap12.wsdl")

        // ACT: Parse the WSDL
        val result = parser.parse(wsdlContent)

        // ASSERT: Verify top-level elements are captured
        // **ON UNFIXED CODE**: This assertion would FAIL
        // - result.xsdTypes would be EMPTY
        // - extractXsdTypes() only processed <xsd:complexType name="...">
        // - Top-level <xsd:element> declarations were ignored
        assertTrue(
            result.xsdTypes.containsKey("GetOrder"),
            "UNFIXED CODE FAILURE: GetOrder element not captured. " +
                "extractXsdTypes() only looked for named complexType definitions. " +
                "Found types: ${result.xsdTypes.keys}"
        )

        // **ON UNFIXED CODE**: This assertion would FAIL
        assertTrue(
            result.xsdTypes.containsKey("GetOrderResponse"),
            "UNFIXED CODE FAILURE: GetOrderResponse element not captured. " +
                "extractXsdTypes() only looked for named complexType definitions. " +
                "Found types: ${result.xsdTypes.keys}"
        )

        // **ON UNFIXED CODE**: xsdTypes.size would be 0
        assertTrue(
            result.xsdTypes.size >= 2,
            "UNFIXED CODE FAILURE: xsdTypes map empty or incomplete. " +
                "Document-literal WSDLs use top-level elements, not named complexTypes. " +
                "Found ${result.xsdTypes.size} types: ${result.xsdTypes.keys}"
        )
    }

    @Test
    fun `Given document-literal WSDL with top-level xsd elements When parsing Then element fields should be preserved`() {
        // ARRANGE: Load WSDL with inline complexType in top-level element
        val wsdlContent = loadWsdl("document-literal-soap12.wsdl")

        // ACT: Parse the WSDL
        val result = parser.parse(wsdlContent)

        // ASSERT: Verify GetOrder element fields are captured
        val getOrderType = result.xsdTypes["GetOrder"]

        // **ON UNFIXED CODE**: getOrderType would be NULL
        // - Top-level elements were not processed at all
        // - Only named complexType definitions were captured
        assertNotNull(
            getOrderType,
            "UNFIXED CODE FAILURE: GetOrder type not found. " +
                "extractXsdTypes() did not process top-level <xsd:element> declarations. " +
                "Available types: ${result.xsdTypes.keys}"
        )

        // **ON UNFIXED CODE**: This would not execute (null assertion above would fail)
        // But if it did, fields would be empty because element wasn't captured
        assertTrue(
            getOrderType.fields.isNotEmpty(),
            "UNFIXED CODE FAILURE: GetOrder fields not captured. " +
                "Inline complexType within top-level element was ignored. " +
                "Found ${getOrderType.fields.size} fields"
        )

        // Verify specific fields from inline complexType
        assertTrue(
            getOrderType.fields.any { it.name == "orderId" },
            "UNFIXED CODE FAILURE: orderId field missing. " +
                "Fields from inline complexType in top-level element were not extracted. " +
                "Found fields: ${getOrderType.fields.map { it.name }}"
        )

        assertTrue(
            getOrderType.fields.any { it.name == "customerId" },
            "UNFIXED CODE FAILURE: customerId field missing. " +
                "Fields from inline complexType in top-level element were not extracted. " +
                "Found fields: ${getOrderType.fields.map { it.name }}"
        )
    }

    @Test
    fun `Given document-literal WSDL with top-level xsd elements When parsing Then response element fields should be preserved`() {
        // ARRANGE: Load WSDL with response element
        val wsdlContent = loadWsdl("document-literal-soap12.wsdl")

        // ACT: Parse the WSDL
        val result = parser.parse(wsdlContent)

        // ASSERT: Verify GetOrderResponse element fields are captured
        val responseType = result.xsdTypes["GetOrderResponse"]

        // **ON UNFIXED CODE**: responseType would be NULL
        assertNotNull(
            responseType,
            "UNFIXED CODE FAILURE: GetOrderResponse type not found. " +
                "extractXsdTypes() did not process top-level <xsd:element> declarations. " +
                "Available types: ${result.xsdTypes.keys}"
        )

        // Verify response fields from inline complexType
        assertTrue(
            responseType.fields.any { it.name == "status" },
            "UNFIXED CODE FAILURE: status field missing from response. " +
                "Fields from inline complexType in top-level element were not extracted. " +
                "Found fields: ${responseType.fields.map { it.name }}"
        )

        assertTrue(
            responseType.fields.any { it.name == "total" },
            "UNFIXED CODE FAILURE: total field missing from response. " +
                "Fields from inline complexType in top-level element were not extracted. " +
                "Found fields: ${responseType.fields.map { it.name }}"
        )
    }

    @Test
    fun `Given document-literal WSDL When parsing Then should capture all element types for AI prompt generation`() {
        // ARRANGE: Load document-literal WSDL
        val wsdlContent = loadWsdl("document-literal-soap12.wsdl")

        // ACT: Parse the WSDL
        val result = parser.parse(wsdlContent)

        // ASSERT: Verify xsdTypes is not empty
        // **ON UNFIXED CODE**: This would FAIL - xsdTypes would be empty
        // This is critical because:
        // - CompactWsdl would have empty xsdTypes
        // - AI prompt would lack schema information
        // - Generated mocks would have incorrect request/response structures
        assertTrue(
            result.xsdTypes.isNotEmpty(),
            "UNFIXED CODE FAILURE: xsdTypes map is empty. " +
                "Document-literal WSDLs use top-level elements, not named complexTypes. " +
                "Without type information, AI cannot generate correct mock structures. " +
                "This would cause incorrect mock generation for document-literal SOAP services."
        )

        // Verify we have both request and response types
        val hasRequestType = result.xsdTypes.containsKey("GetOrder")
        val hasResponseType = result.xsdTypes.containsKey("GetOrderResponse")

        assertTrue(
            hasRequestType && hasResponseType,
            "UNFIXED CODE FAILURE: Missing request or response types. " +
                "Request type present: $hasRequestType, Response type present: $hasResponseType. " +
                "AI prompt generation requires both request and response schemas. " +
                "Found types: ${result.xsdTypes.keys}"
        )
    }
}
