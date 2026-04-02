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
