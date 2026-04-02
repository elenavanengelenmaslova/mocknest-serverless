package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test for Bug 4: Non-SOAP WSDL Silent Fallback
 *
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
 * **DO NOT attempt to fix the test or the code when it fails**
 *
 * Bug Condition: isBugCondition_Bug4(input) where WSDL contains only HTTP bindings or SOAP 1.1
 * Expected Behavior: Parser throws WsdlParsingException with clear message
 *
 * **SCOPE**: We ONLY support SOAP 1.2 - reject all other protocols and SOAP versions
 *
 * This test encodes the expected behavior - it will validate the fix when it passes after implementation.
 */
class NonSoapWsdlSilentFallbackBugTest {

    private val parser = WsdlParser()

    @Test
    fun `Given WSDL with only HTTP bindings When parsing Then should throw WsdlParsingException`() {
        // Given: WSDL with only HTTP bindings (no SOAP namespace)
        val httpOnlyWsdl = this::class.java.getResource("/wsdl/http-only-binding.wsdl")?.readText()
            ?: throw IllegalStateException("Test WSDL file not found")

        // When: Parsing the HTTP-only WSDL
        // Then: Should throw WsdlParsingException with clear message about non-SOAP bindings
        val ex = assertFailsWith<WsdlParsingException> {
            parser.parse(httpOnlyWsdl)
        }

        // Verify the exception message clearly indicates non-SOAP bindings are not supported
        val message = ex.message
        assertTrue(
            message?.contains("non-SOAP", ignoreCase = true) == true ||
                message?.contains("not supported", ignoreCase = true) == true,
            "Exception message should clearly indicate non-SOAP bindings are not supported. " +
                "Actual message: $message"
        )
    }

    @Test
    fun `Given WSDL with SOAP 1_1 bindings When parsing Then should throw WsdlParsingException`() {
        // Given: WSDL with SOAP 1.1 bindings
        val soap11Wsdl = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                         xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                         xmlns:tns="http://example.com/soap11"
                         xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                         targetNamespace="http://example.com/soap11"
                         name="Soap11Service">

                <types>
                    <xsd:schema targetNamespace="http://example.com/soap11">
                        <xsd:element name="GetDataRequest">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="id" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                        <xsd:element name="GetDataResponse">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="data" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:schema>
                </types>

                <message name="GetDataRequestMessage">
                    <part name="parameters" element="tns:GetDataRequest"/>
                </message>

                <message name="GetDataResponseMessage">
                    <part name="parameters" element="tns:GetDataResponse"/>
                </message>

                <portType name="Soap11PortType">
                    <operation name="GetData">
                        <input message="tns:GetDataRequestMessage"/>
                        <output message="tns:GetDataResponseMessage"/>
                    </operation>
                </portType>

                <binding name="Soap11Binding" type="tns:Soap11PortType">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                    <operation name="GetData">
                        <soap:operation soapAction="GetData"/>
                        <input>
                            <soap:body use="literal"/>
                        </input>
                        <output>
                            <soap:body use="literal"/>
                        </output>
                    </operation>
                </binding>

                <service name="Soap11Service">
                    <port name="Soap11Port" binding="tns:Soap11Binding">
                        <soap:address location="http://example.com/soap11"/>
                    </port>
                </service>

            </definitions>
        """.trimIndent()

        // When: Parsing the SOAP 1.1 WSDL
        // Then: Should throw WsdlParsingException with clear message about SOAP 1.2 only support
        val ex = assertFailsWith<WsdlParsingException> {
            parser.parse(soap11Wsdl)
        }

        // Verify the exception message clearly indicates only SOAP 1.2 is supported
        val message = ex.message
        assertTrue(
            message?.contains("SOAP 1.2 bindings", ignoreCase = true) == true ||
                message?.contains("not supported", ignoreCase = true) == true,
            "Exception message should clearly indicate only SOAP 1.2 bindings are supported. " +
                "Actual message: $message"
        )
    }

    @Test
    fun `Given WSDL with no bindings When parsing Then should throw WsdlParsingException`() {
        // Given: WSDL with no binding elements at all
        val noBindingsWsdl = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                         xmlns:tns="http://example.com/nobindings"
                         xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                         targetNamespace="http://example.com/nobindings"
                         name="NoBindingsService">

                <types>
                    <xsd:schema targetNamespace="http://example.com/nobindings">
                        <xsd:element name="GetDataRequest">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="id" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:schema>
                </types>

                <message name="GetDataRequestMessage">
                    <part name="parameters" element="tns:GetDataRequest"/>
                </message>

                <portType name="NoBindingsPortType">
                    <operation name="GetData">
                        <input message="tns:GetDataRequestMessage"/>
                    </operation>
                </portType>

                <service name="NoBindingsService">
                </service>

            </definitions>
        """.trimIndent()

        // When: Parsing the WSDL with no bindings
        // Then: Should throw WsdlParsingException
        val ex = assertFailsWith<WsdlParsingException> {
            parser.parse(noBindingsWsdl)
        }

        // Verify the exception message indicates missing SOAP bindings
        val message = ex.message
        assertTrue(
            message?.contains("SOAP", ignoreCase = true) == true ||
                message?.contains("binding", ignoreCase = true) == true,
            "Exception message should indicate missing SOAP bindings. " +
                "Actual message: $message"
        )
    }
}
