package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlParserTest {

    private val parser = WsdlParser()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @Nested
    inner class Soap11Parsing {

        @Test
        fun `Given SOAP 1_1 only WSDL When parsing Then should throw WsdlParsingException`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("simple-soap11.wsdl"))
            }
            assertTrue(
                ex.message?.contains("Only SOAP 1.2 bindings are supported") == true,
                "Exception should say only SOAP 1.2 bindings are supported, got: ${ex.message}"
            )
        }
    }

    @Nested
    inner class Soap12Parsing {

        @Test
        fun `Given SOAP 1_2 WSDL When parsing Then should detect SOAP_1_2 version`() {
            val result = parser.parse(loadWsdl("simple-soap12.wsdl"))
            assertEquals(SoapVersion.SOAP_1_2, result.soapVersion)
        }

        @Test
        fun `Given SOAP 1_2 WSDL When parsing Then should extract service name`() {
            val result = parser.parse(loadWsdl("simple-soap12.wsdl"))
            assertEquals("GreetService", result.serviceName)
        }

        @Test
        fun `Given SOAP 1_2 WSDL When parsing Then should extract operations`() {
            val result = parser.parse(loadWsdl("simple-soap12.wsdl"))
            assertEquals(1, result.operations.size)
            assertEquals("Greet", result.operations.first().name)
        }
    }

    @Nested
    inner class MixedVersionParsing {

        @Test
        fun `Given WSDL with both SOAP versions When parsing Then should select SOAP 1_2 and succeed`() {
            val result = parser.parse(loadWsdl("mixed-version.wsdl"))
            assertEquals(SoapVersion.SOAP_1_2, result.soapVersion)
            assertEquals("MixedService", result.serviceName)
        }

        @Test
        fun `Given WSDL with both SOAP versions When parsing Then service address comes from SOAP 1_2 port`() {
            val result = parser.parse(loadWsdl("mixed-version.wsdl"))
            assertTrue(
                result.servicePortAddresses.all { it.contains("soap12") },
                "Service addresses should come from SOAP 1.2 port. Got: ${result.servicePortAddresses}"
            )
        }

        @Test
        fun `Given WSDL with both SOAP versions When parsing Then operations come from SOAP 1_2 binding`() {
            val result = parser.parse(loadWsdl("mixed-version.wsdl"))
            assertTrue(result.operations.isNotEmpty(), "Should have operations from SOAP 1.2 binding")
            assertEquals("DoSomething", result.operations.first().name)
        }

        @Test
        fun `Given WSDL with both SOAP versions When parsing Then warnings mention mixed versions`() {
            val result = parser.parse(loadWsdl("mixed-version.wsdl"))
            assertTrue(
                result.warnings.any { it.contains("SOAP 1.1") && it.contains("SOAP 1.2") },
                "Should warn about mixed versions. Warnings: ${result.warnings}"
            )
        }
    }

    @Nested
    inner class NonSoapBindingRejection {

        @Test
        fun `Given WSDL with no SOAP binding namespace When parsing Then should throw WsdlParsingException`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("no-binding-namespace.wsdl"))
            }
            assertTrue(
                ex.message?.contains("No SOAP binding namespace") == true,
                "Exception should mention missing SOAP binding namespace, got: ${ex.message}"
            )
        }
    }

    @Nested
    inner class DocumentLiteralElementParsing {

        @Test
        fun `Given document-literal WSDL with top-level xsd elements When parsing Then should capture element types`() {
            val result = parser.parse(loadWsdl("document-literal-soap12.wsdl"))
            assertTrue(
                result.xsdTypes.containsKey("GetOrder"),
                "Should capture top-level xsd:element 'GetOrder' as a type. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("GetOrderResponse"),
                "Should capture top-level xsd:element 'GetOrderResponse' as a type. Found types: ${result.xsdTypes.keys}"
            )
        }

        @Test
        fun `Given document-literal WSDL with top-level xsd elements When parsing Then element fields should be preserved`() {
            val result = parser.parse(loadWsdl("document-literal-soap12.wsdl"))
            val getOrderType = result.xsdTypes["GetOrder"]
            assertNotNull(getOrderType, "GetOrder type must be present")
            assertEquals(2, getOrderType.fields.size, "GetOrder should have 2 fields")
            assertTrue(
                getOrderType.fields.any { it.name == "orderId" },
                "GetOrder should have 'orderId' field"
            )
            assertTrue(
                getOrderType.fields.any { it.name == "customerId" },
                "GetOrder should have 'customerId' field"
            )
        }

        @Test
        fun `Given top-level element with type reference When parsing Then should copy fields from referenced type`() {
            // Given: WSDL with top-level element referencing a named complexType
            val result = parser.parse(loadWsdl("element-with-type-ref-soap12.wsdl"))

            // When: Extracting the top-level element
            val getPersonType = result.xsdTypes["GetPerson"]

            // Then: Element should have fields from referenced PersonType
            assertNotNull(getPersonType, "GetPerson element should be captured")
            assertEquals(3, getPersonType.fields.size, "GetPerson should have 3 fields from PersonType")
            assertTrue(
                getPersonType.fields.any { it.name == "firstName" },
                "GetPerson should have 'firstName' field from PersonType"
            )
            assertTrue(
                getPersonType.fields.any { it.name == "lastName" },
                "GetPerson should have 'lastName' field from PersonType"
            )
            assertTrue(
                getPersonType.fields.any { it.name == "age" },
                "GetPerson should have 'age' field from PersonType"
            )
        }

        @Test
        fun `Given mixed named complexTypes and top-level elements When parsing Then should capture both`() {
            // Given: WSDL with both named complexTypes and top-level elements
            val result = parser.parse(loadWsdl("mixed-types-soap12.wsdl"))

            // Then: Should capture named complexTypes
            assertTrue(
                result.xsdTypes.containsKey("Address"),
                "Should capture named complexType 'Address'. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("UserInfo"),
                "Should capture named complexType 'UserInfo'. Found types: ${result.xsdTypes.keys}"
            )

            // And: Should capture top-level elements
            assertTrue(
                result.xsdTypes.containsKey("CreateUser"),
                "Should capture top-level element 'CreateUser'. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("CreateUserResponse"),
                "Should capture top-level element 'CreateUserResponse'. Found types: ${result.xsdTypes.keys}"
            )

            // And: Should have all 4 types
            assertEquals(4, result.xsdTypes.size, "Should have 4 types total (2 named + 2 elements)")
        }

        @Test
        fun `Given top-level element with inline complexType When parsing Then should extract fields correctly`() {
            // Given: WSDL with top-level element containing inline complexType
            val result = parser.parse(loadWsdl("mixed-types-soap12.wsdl"))

            // When: Extracting the CreateUser element
            val createUserType = result.xsdTypes["CreateUser"]

            // Then: Should have fields from inline complexType
            assertNotNull(createUserType, "CreateUser element should be captured")
            assertEquals(3, createUserType.fields.size, "CreateUser should have 3 fields")
            assertTrue(
                createUserType.fields.any { it.name == "username" },
                "CreateUser should have 'username' field"
            )
            assertTrue(
                createUserType.fields.any { it.name == "email" },
                "CreateUser should have 'email' field"
            )
            assertTrue(
                createUserType.fields.any { it.name == "address" },
                "CreateUser should have 'address' field"
            )
        }

        @Test
        fun `Given empty top-level element When parsing Then should handle gracefully`() {
            // Given: WSDL with empty element (no complexType, no type attribute)
            val wsdl = loadWsdl("empty-element-soap12.wsdl")

            // When: Parsing the WSDL
            val result = parser.parse(wsdl)

            // Then: Should capture Ping element but not EmptyElement (no type info)
            assertTrue(
                result.xsdTypes.containsKey("Ping"),
                "Should capture Ping element with inline complexType"
            )
            assertTrue(
                !result.xsdTypes.containsKey("EmptyElement"),
                "Should not capture EmptyElement (no type information)"
            )
        }

        @Test
        fun `Given top-level element with missing type reference When parsing Then should not capture element`() {
            // Given: WSDL with element referencing non-existent type
            val wsdl = loadWsdl("missing-type-ref-soap12.wsdl")

            // When: Parsing the WSDL
            val result = parser.parse(wsdl)

            // Then: Should capture GoodElement but not BadElement (missing type reference)
            assertTrue(
                result.xsdTypes.containsKey("GoodElement"),
                "Should capture GoodElement with inline complexType"
            )
            assertTrue(
                !result.xsdTypes.containsKey("BadElement"),
                "Should not capture BadElement (type reference not found)"
            )
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given malformed XML When parsing Then should throw WsdlParsingException`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("malformed.wsdl"))
            }
            assertTrue(ex.message?.contains("Malformed XML") == true || ex.message?.contains("line") == true)
        }

        @Test
        fun `Given XML without wsdl definitions root When parsing Then should throw WsdlParsingException`() {
            val xml = """<?xml version="1.0"?><root><child/></root>"""
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(xml)
            }
            assertTrue(ex.message?.contains("definitions") == true)
        }

        @Test
        fun `Given WSDL without targetNamespace When parsing Then should throw WsdlParsingException`() {
            val xml = """<?xml version="1.0"?>
<wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" name="Test">
  <wsdl:portType name="TestPortType">
    <wsdl:operation name="DoIt">
      <wsdl:input message="tns:DoItRequest"/>
      <wsdl:output message="tns:DoItResponse"/>
    </wsdl:operation>
  </wsdl:portType>
</wsdl:definitions>"""
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(xml)
            }
            assertTrue(ex.message?.contains("targetNamespace") == true)
        }

        @Test
        fun `Given WSDL with no operations When parsing Then should throw WsdlParsingException`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("invalid-no-operations.wsdl"))
            }
            // WSDL uses SOAP 1.1 binding, so it fails with "Only SOAP 1.2 is supported"
            assertTrue(
                ex.message?.contains("SOAP 1.2") == true,
                "Exception should mention SOAP 1.2 requirement, got: ${ex.message}"
            )
        }
    }

    @Nested
    inner class PerOperationBindingResolution {

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings and different service addresses When parsing Then operationBindings map should be populated`() {
            // Given
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")

            // When
            val result = parser.parse(wsdl)

            // Then
            assertTrue(result.operationBindings.isNotEmpty(), "operationBindings should be populated")
            assertEquals(2, result.operationBindings.size, "Should have 2 operation bindings")
        }

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings When parsing Then each operation should map to correct binding`() {
            // Given
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")

            // When
            val result = parser.parse(wsdl)

            // Then
            val getUserBinding = result.operationBindings["UserPortType#GetUser"]
            val getProductBinding = result.operationBindings["ProductPortType#GetProduct"]

            assertNotNull(getUserBinding, "GetUser operation should have binding")
            assertNotNull(getProductBinding, "GetProduct operation should have binding")
        }

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings When parsing Then each binding should have correct service address`() {
            // Given
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")

            // When
            val result = parser.parse(wsdl)

            // Then
            val getUserBinding = result.operationBindings["UserPortType#GetUser"]
            val getProductBinding = result.operationBindings["ProductPortType#GetProduct"]

            assertNotNull(getUserBinding)
            assertNotNull(getProductBinding)

            assertEquals(
                "http://example.com/multiport/user",
                getUserBinding.serviceAddress,
                "UserBinding should have /multiport/user service address"
            )
            assertEquals(
                "http://example.com/multiport/product",
                getProductBinding.serviceAddress,
                "ProductBinding should have /multiport/product service address"
            )
        }

        @Test
        fun `Given WSDL with single SOAP 1_2 binding When parsing Then operationBindings should still be populated`() {
            // Given
            val wsdl = loadWsdl("simple-soap12.wsdl")

            // When
            val result = parser.parse(wsdl)

            // Then
            assertTrue(result.operationBindings.isNotEmpty(), "operationBindings should be populated even for single binding")
        }
    }
}
