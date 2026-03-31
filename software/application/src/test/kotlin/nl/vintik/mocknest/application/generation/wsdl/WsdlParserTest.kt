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
        fun `Given SOAP 1_1 WSDL When parsing Then should extract service name`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertEquals("HelloService", result.serviceName)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract targetNamespace`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertEquals("http://example.com/hello", result.targetNamespace)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should detect SOAP_1_1 version`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertEquals(SoapVersion.SOAP_1_1, result.soapVersion)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract port types`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertEquals(1, result.portTypes.size)
            assertEquals("HelloPortType", result.portTypes.first().name)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract operations`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertEquals(1, result.operations.size)
            val op = result.operations.first()
            assertEquals("SayHello", op.name)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract SOAPAction`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            val op = result.operations.first()
            assertEquals("http://example.com/hello/SayHello", op.soapAction)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract input and output message names`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            val op = result.operations.first()
            assertEquals("SayHello", op.inputMessageName)
            assertEquals("SayHelloResponse", op.outputMessageName)
        }

        @Test
        fun `Given SOAP 1_1 WSDL When parsing Then should extract service port address`() {
            val result = parser.parse(loadWsdl("simple-soap11.wsdl"))
            assertTrue(result.servicePortAddresses.isNotEmpty())
            assertTrue(result.servicePortAddresses.first().contains("example.com"))
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
        fun `Given WSDL with both SOAP versions When parsing Then should throw WsdlParsingException`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("mixed-version.wsdl"))
            }
            assertTrue(
                ex.message?.contains("Mixed SOAP 1.1 and SOAP 1.2") == true,
                "Exception should mention mixed SOAP versions, got: ${ex.message}"
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
            val result = parser.parse(loadWsdl("document-literal-soap11.wsdl"))
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
            val result = parser.parse(loadWsdl("document-literal-soap11.wsdl"))
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
            assertTrue(ex.message?.contains("no operations") == true || ex.message?.contains("operations") == true)
        }
    }
}
