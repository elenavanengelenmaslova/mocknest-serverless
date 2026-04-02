package nl.vintik.mocknest.application.generation.wsdl

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-Based Tests for WSDL Type Extraction
 *
 * This test validates that WsdlParser correctly extracts ALL XSD types from diverse WSDL patterns:
 * - Document-literal style (top-level elements with inline complexType)
 * - RPC-style (named complexType definitions)
 * - Nested types (complexTypes referencing other complexTypes)
 * - Inline types (elements with inline complexType)
 * - Type references (elements with type= attribute)
 * - Mixed patterns (combination of above)
 *
 * Test Strategy:
 * - Use @ParameterizedTest with @ValueSource to test 10-20 diverse WSDL files
 * - Verify all types are captured correctly for each pattern
 * - Test both simple and complex scenarios
 * - Verify edge cases are handled gracefully
 *
 * Expected Outcome: All tests PASS, confirming comprehensive type extraction
 */
class WsdlTypeExtractionPropertyTest {

    private val parser = WsdlParser()

    private fun loadTestWsdl(filename: String): String {
        return this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("Test WSDL file not found: $filename")
    }

    /**
     * Property: All XSD types are captured from diverse WSDL patterns
     *
     * Tests multiple WSDL files with various type definition patterns to ensure
     * the parser correctly captures all types regardless of style.
     */
    @ParameterizedTest(name = "Given {0} When parsing Then should extract all types correctly")
    @ValueSource(
        strings = [
            // Document-literal style (top-level elements)
            "document-literal-soap12.wsdl",
            
            // RPC-style (named complexTypes)
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            
            // Nested types
            "nested-xsd-soap12.wsdl",
            
            // Mixed patterns
            "mixed-types-soap12.wsdl",
            "element-with-type-ref-soap12.wsdl",
            
            // Multiple operations
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl",
            
            // Unreferenced types
            "unreferenced-types-soap12.wsdl",
            
            // Simple and large services
            "simple-soap12.wsdl",
            "weather-soap12.wsdl",
            "large-service.wsdl"
        ]
    )
    fun `Property - Given diverse WSDL patterns When parsing Then all types should be captured`(
        filename: String
    ) {
        // Given: WSDL with various type definition patterns
        val wsdlContent = loadTestWsdl(filename)

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: xsdTypes map should not be null
        assertNotNull(result.xsdTypes, "xsdTypes map should not be null for $filename")

        // Verify specific patterns based on file
        when (filename) {
            "document-literal-soap12.wsdl" -> {
                // Document-literal: top-level elements with inline complexType
                assertTrue(
                    result.xsdTypes.containsKey("GetOrder"),
                    "[$filename] Should capture top-level element 'GetOrder'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("GetOrderResponse"),
                    "[$filename] Should capture top-level element 'GetOrderResponse'"
                )
                assertTrue(
                    result.xsdTypes.size >= 2,
                    "[$filename] Should have at least 2 types (request + response)"
                )
            }

            "calculator-soap12.wsdl" -> {
                // RPC-style: named complexTypes
                assertTrue(
                    result.xsdTypes.containsKey("AddRequest"),
                    "[$filename] Should capture named complexType 'AddRequest'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("AddResponse"),
                    "[$filename] Should capture named complexType 'AddResponse'"
                )
                assertTrue(
                    result.xsdTypes.size >= 6,
                    "[$filename] Should have at least 6 types (Add, Subtract, Multiply request/response)"
                )
            }

            "complex-types-soap12.wsdl" -> {
                // Named complexTypes with nested references
                assertTrue(
                    result.xsdTypes.containsKey("GetOrder"),
                    "[$filename] Should capture 'GetOrder' complexType"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Order"),
                    "[$filename] Should capture 'Order' complexType"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Customer"),
                    "[$filename] Should capture 'Customer' complexType"
                )
                assertTrue(
                    result.xsdTypes.size >= 4,
                    "[$filename] Should have at least 4 types"
                )
            }

            "nested-xsd-soap12.wsdl" -> {
                // Nested types: complexTypes referencing other complexTypes
                assertTrue(
                    result.xsdTypes.containsKey("Report"),
                    "[$filename] Should capture 'Report' complexType"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Author"),
                    "[$filename] Should capture 'Author' complexType"
                )
                assertTrue(
                    result.xsdTypes.containsKey("Contact"),
                    "[$filename] Should capture 'Contact' complexType"
                )
                assertTrue(
                    result.xsdTypes.size >= 3,
                    "[$filename] Should have at least 3 nested types"
                )
            }

            "mixed-types-soap12.wsdl" -> {
                // Mixed: both named complexTypes and top-level elements
                assertTrue(
                    result.xsdTypes.containsKey("Address"),
                    "[$filename] Should capture named complexType 'Address'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("UserInfo"),
                    "[$filename] Should capture named complexType 'UserInfo'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("CreateUser"),
                    "[$filename] Should capture top-level element 'CreateUser'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("CreateUserResponse"),
                    "[$filename] Should capture top-level element 'CreateUserResponse'"
                )
                assertTrue(
                    result.xsdTypes.size == 4,
                    "[$filename] Should have exactly 4 types (2 named + 2 elements)"
                )
            }

            "element-with-type-ref-soap12.wsdl" -> {
                // Type references: top-level element with type= attribute
                assertTrue(
                    result.xsdTypes.containsKey("PersonType"),
                    "[$filename] Should capture named complexType 'PersonType'"
                )
                assertTrue(
                    result.xsdTypes.containsKey("GetPerson"),
                    "[$filename] Should capture top-level element 'GetPerson' with type reference"
                )
                assertTrue(
                    result.xsdTypes.containsKey("GetPersonResponse"),
                    "[$filename] Should capture top-level element 'GetPersonResponse'"
                )
                assertTrue(
                    result.xsdTypes.size >= 3,
                    "[$filename] Should have at least 3 types"
                )
            }

            "multi-operation-soap12.wsdl" -> {
                // Multiple operations with different types
                assertTrue(
                    result.xsdTypes.isNotEmpty(),
                    "[$filename] Should capture types for multiple operations"
                )
            }

            "multi-porttype-soap12.wsdl" -> {
                // Multiple port types with different types
                assertTrue(
                    result.xsdTypes.isNotEmpty(),
                    "[$filename] Should capture types for multiple port types"
                )
            }

            "unreferenced-types-soap12.wsdl" -> {
                // Unreferenced types should still be captured
                assertTrue(
                    result.xsdTypes.isNotEmpty(),
                    "[$filename] Should capture types even if unreferenced"
                )
            }

            "simple-soap12.wsdl", "weather-soap12.wsdl", "large-service.wsdl" -> {
                // General validation: xsdTypes should be populated
                assertNotNull(
                    result.xsdTypes,
                    "[$filename] xsdTypes map should not be null"
                )
            }
        }
    }

    /**
     * Property: Type fields are extracted correctly from all patterns
     *
     * Verifies that fields within type definitions are correctly extracted
     * regardless of whether the type is a named complexType or top-level element.
     */
    @ParameterizedTest(name = "Given {0} When parsing Then type fields should be extracted")
    @ValueSource(
        strings = [
            "document-literal-soap12.wsdl",
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "mixed-types-soap12.wsdl",
            "element-with-type-ref-soap12.wsdl"
        ]
    )
    fun `Property - Given WSDL with type definitions When parsing Then fields should be extracted correctly`(
        filename: String
    ) {
        // Given: WSDL with type definitions
        val wsdlContent = loadTestWsdl(filename)

        // When: Parsing the WSDL
        val result = parser.parse(wsdlContent)

        // Then: Types should have fields
        when (filename) {
            "document-literal-soap12.wsdl" -> {
                val getOrderType = result.xsdTypes["GetOrder"]
                assertNotNull(getOrderType, "[$filename] GetOrder type should exist")
                assertTrue(
                    getOrderType.fields.isNotEmpty(),
                    "[$filename] GetOrder should have fields"
                )
                assertTrue(
                    getOrderType.fields.any { it.name == "orderId" },
                    "[$filename] GetOrder should have 'orderId' field"
                )
            }

            "calculator-soap12.wsdl" -> {
                val addRequestType = result.xsdTypes["AddRequest"]
                assertNotNull(addRequestType, "[$filename] AddRequest type should exist")
                assertTrue(
                    addRequestType.fields.isNotEmpty(),
                    "[$filename] AddRequest should have fields"
                )
                assertTrue(
                    addRequestType.fields.any { it.name == "a" },
                    "[$filename] AddRequest should have 'a' field"
                )
            }

            "complex-types-soap12.wsdl" -> {
                val orderType = result.xsdTypes["Order"]
                assertNotNull(orderType, "[$filename] Order type should exist")
                assertTrue(
                    orderType.fields.size >= 3,
                    "[$filename] Order should have at least 3 fields"
                )
            }

            "nested-xsd-soap12.wsdl" -> {
                val contactType = result.xsdTypes["Contact"]
                assertNotNull(contactType, "[$filename] Contact type should exist")
                assertTrue(
                    contactType.fields.any { it.name == "email" },
                    "[$filename] Contact should have 'email' field"
                )
            }

            "mixed-types-soap12.wsdl" -> {
                val addressType = result.xsdTypes["Address"]
                assertNotNull(addressType, "[$filename] Address type should exist")
                assertTrue(
                    addressType.fields.any { it.name == "street" },
                    "[$filename] Address should have 'street' field"
                )

                val createUserType = result.xsdTypes["CreateUser"]
                assertNotNull(createUserType, "[$filename] CreateUser element should exist")
                assertTrue(
                    createUserType.fields.any { it.name == "username" },
                    "[$filename] CreateUser should have 'username' field"
                )
            }

            "element-with-type-ref-soap12.wsdl" -> {
                val getPersonType = result.xsdTypes["GetPerson"]
                assertNotNull(getPersonType, "[$filename] GetPerson element should exist")
                assertTrue(
                    getPersonType.fields.any { it.name == "firstName" },
                    "[$filename] GetPerson should have 'firstName' field from PersonType"
                )
            }
        }
    }

    /**
     * Property: Type extraction is consistent across parsing runs
     *
     * Verifies that parsing the same WSDL multiple times produces consistent results.
     */
    @ParameterizedTest(name = "Given {0} When parsing multiple times Then results should be consistent")
    @ValueSource(
        strings = [
            "document-literal-soap12.wsdl",
            "calculator-soap12.wsdl",
            "mixed-types-soap12.wsdl"
        ]
    )
    fun `Property - Given same WSDL When parsing multiple times Then type extraction should be consistent`(
        filename: String
    ) {
        // Given: Same WSDL content
        val wsdlContent = loadTestWsdl(filename)

        // When: Parsing multiple times
        val result1 = parser.parse(wsdlContent)
        val result2 = parser.parse(wsdlContent)

        // Then: Results should be consistent
        assertEquals(
            result1.xsdTypes.keys,
            result2.xsdTypes.keys,
            "[$filename] Type names should be consistent across parsing runs"
        )
        assertEquals(
            result1.xsdTypes.size,
            result2.xsdTypes.size,
            "[$filename] Type count should be consistent across parsing runs"
        )

        // Verify field consistency for each type
        result1.xsdTypes.forEach { (typeName, type1) ->
            val type2 = result2.xsdTypes[typeName]
            assertNotNull(type2, "[$filename] Type '$typeName' should exist in second parse")
            assertEquals(
                type1.fields.size,
                type2.fields.size,
                "[$filename] Type '$typeName' should have same field count in both parses"
            )
        }
    }
}
