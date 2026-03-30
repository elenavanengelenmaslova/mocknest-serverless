package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class CompactWsdlTest {

    @Nested
    inner class CompactWsdlValidation {

        @Test
        fun `Given valid CompactWsdl When creating Then should succeed`() {
            val wsdl = minimalWsdl()

            assertNotNull(wsdl)
            assertEquals("CalculatorService", wsdl.serviceName)
            assertEquals("http://example.com/calculator", wsdl.targetNamespace)
            assertEquals(SoapVersion.SOAP_1_1, wsdl.soapVersion)
        }

        @Test
        fun `Given blank service name When creating CompactWsdl Then should throw`() {
            assertThrows<IllegalArgumentException> {
                minimalWsdl(serviceName = "")
            }
        }

        @Test
        fun `Given whitespace service name When creating CompactWsdl Then should throw`() {
            assertThrows<IllegalArgumentException> {
                minimalWsdl(serviceName = "   ")
            }
        }

        @Test
        fun `Given blank target namespace When creating CompactWsdl Then should throw`() {
            assertThrows<IllegalArgumentException> {
                minimalWsdl(targetNamespace = "")
            }
        }

        @Test
        fun `Given whitespace target namespace When creating CompactWsdl Then should throw`() {
            assertThrows<IllegalArgumentException> {
                minimalWsdl(targetNamespace = "  ")
            }
        }

        @Test
        fun `Given empty operations When creating CompactWsdl Then should throw`() {
            assertThrows<IllegalArgumentException> {
                minimalWsdl(operations = emptyList())
            }
        }
    }

    @Nested
    inner class WsdlPortTypeValidation {

        @Test
        fun `Given valid port type When creating Then should succeed`() {
            val portType = WsdlPortType("CalculatorPortType")

            assertNotNull(portType)
            assertEquals("CalculatorPortType", portType.name)
        }

        @Test
        fun `Given blank name When creating WsdlPortType Then should throw`() {
            assertThrows<IllegalArgumentException> {
                WsdlPortType("")
            }
        }

        @Test
        fun `Given whitespace name When creating WsdlPortType Then should throw`() {
            assertThrows<IllegalArgumentException> {
                WsdlPortType("  ")
            }
        }
    }

    @Nested
    inner class WsdlOperationValidation {

        @Test
        fun `Given valid operation When creating Then should succeed`() {
            val op = mockOperation()

            assertNotNull(op)
            assertEquals("Add", op.name)
            assertEquals("http://example.com/Add", op.soapAction)
            assertEquals("AddRequest", op.inputMessage)
            assertEquals("AddResponse", op.outputMessage)
            assertEquals("CalculatorPortType", op.portTypeName)
        }

        @Test
        fun `Given blank name When creating WsdlOperation Then should throw`() {
            assertThrows<IllegalArgumentException> {
                mockOperation(name = "")
            }
        }

        @Test
        fun `Given blank input message When creating WsdlOperation Then should throw`() {
            assertThrows<IllegalArgumentException> {
                mockOperation(inputMessage = "")
            }
        }

        @Test
        fun `Given blank output message When creating WsdlOperation Then should throw`() {
            assertThrows<IllegalArgumentException> {
                mockOperation(outputMessage = "")
            }
        }
    }

    @Nested
    inner class WsdlXsdTypeValidation {

        @Test
        fun `Given valid XSD type When creating Then should succeed`() {
            val type = WsdlXsdType("AddRequest", listOf(WsdlXsdField("a", "xsd:int"), WsdlXsdField("b", "xsd:int")))

            assertNotNull(type)
            assertEquals("AddRequest", type.name)
            assertEquals(2, type.fields.size)
        }

        @Test
        fun `Given blank name When creating WsdlXsdType Then should throw`() {
            assertThrows<IllegalArgumentException> {
                WsdlXsdType("", listOf(WsdlXsdField("a", "xsd:int")))
            }
        }

        @Test
        fun `Given empty fields When creating WsdlXsdType Then should succeed`() {
            // Empty fields are allowed — some types may have no fields
            val type = WsdlXsdType("EmptyType", emptyList())
            assertNotNull(type)
        }
    }

    @Nested
    inner class WsdlXsdFieldValidation {

        @Test
        fun `Given valid field When creating Then should succeed`() {
            val field = WsdlXsdField("intA", "xsd:int")

            assertNotNull(field)
            assertEquals("intA", field.name)
            assertEquals("xsd:int", field.type)
        }

        @Test
        fun `Given blank name When creating WsdlXsdField Then should throw`() {
            assertThrows<IllegalArgumentException> {
                WsdlXsdField("", "xsd:int")
            }
        }

        @Test
        fun `Given blank type When creating WsdlXsdField Then should throw`() {
            assertThrows<IllegalArgumentException> {
                WsdlXsdField("intA", "")
            }
        }
    }

    @Nested
    inner class PrettyPrintFormat {

        @Test
        fun `Given CompactWsdl When pretty printing Then should include service name`() {
            val output = minimalWsdl().prettyPrint()

            assertTrue(output.contains("service: CalculatorService"))
        }

        @Test
        fun `Given CompactWsdl When pretty printing Then should include target namespace`() {
            val output = minimalWsdl().prettyPrint()

            assertTrue(output.contains("targetNamespace: http://example.com/calculator"))
        }

        @Test
        fun `Given SOAP 1_1 CompactWsdl When pretty printing Then should include SOAP version`() {
            val output = minimalWsdl(soapVersion = SoapVersion.SOAP_1_1).prettyPrint()

            assertTrue(output.contains("soapVersion: SOAP_1_1"))
        }

        @Test
        fun `Given SOAP 1_2 CompactWsdl When pretty printing Then should include SOAP version`() {
            val output = minimalWsdl(soapVersion = SoapVersion.SOAP_1_2).prettyPrint()

            assertTrue(output.contains("soapVersion: SOAP_1_2"))
        }

        @Test
        fun `Given CompactWsdl with port types When pretty printing Then should include port types`() {
            val wsdl = minimalWsdl(portTypes = listOf(WsdlPortType("CalculatorPortType")))
            val output = wsdl.prettyPrint()

            assertTrue(output.contains("portTypes:"))
            assertTrue(output.contains("CalculatorPortType"))
        }

        @Test
        fun `Given CompactWsdl with operations When pretty printing Then should include all operation details`() {
            val wsdl = minimalWsdl(
                operations = listOf(
                    mockOperation("Add", "http://example.com/Add", "AddRequest", "AddResponse"),
                    mockOperation("Subtract", "http://example.com/Subtract", "SubtractRequest", "SubtractResponse")
                )
            )
            val output = wsdl.prettyPrint()

            assertTrue(output.contains("operations:"))
            assertTrue(output.contains("Add:"))
            assertTrue(output.contains("soapAction: http://example.com/Add"))
            assertTrue(output.contains("input: AddRequest"))
            assertTrue(output.contains("output: AddResponse"))
            assertTrue(output.contains("Subtract:"))
            assertTrue(output.contains("soapAction: http://example.com/Subtract"))
        }

        @Test
        fun `Given CompactWsdl with XSD types When pretty printing Then should include types with fields`() {
            val wsdl = minimalWsdl(
                xsdTypes = mapOf(
                    "AddRequest" to WsdlXsdType(
                        "AddRequest",
                        listOf(WsdlXsdField("intA", "xsd:int"), WsdlXsdField("intB", "xsd:int"))
                    )
                )
            )
            val output = wsdl.prettyPrint()

            assertTrue(output.contains("types:"))
            assertTrue(output.contains("AddRequest:"))
            assertTrue(output.contains("intA: xsd:int"))
            assertTrue(output.contains("intB: xsd:int"))
        }

        @Test
        fun `Given CompactWsdl with no XSD types When pretty printing Then should not include types section`() {
            val output = minimalWsdl(xsdTypes = emptyMap()).prettyPrint()

            assertTrue(!output.contains("types:"))
        }

        @Test
        fun `Given full CompactWsdl When pretty printing Then should include all sections`() {
            val wsdl = CompactWsdl(
                serviceName = "WeatherService",
                targetNamespace = "http://example.com/weather",
                soapVersion = SoapVersion.SOAP_1_2,
                portTypes = listOf(WsdlPortType("WeatherPortType")),
                operations = listOf(
                    mockOperation("GetWeather", "http://example.com/GetWeather", "GetWeatherRequest", "GetWeatherResponse")
                ),
                xsdTypes = mapOf(
                    "GetWeatherRequest" to WsdlXsdType("GetWeatherRequest", listOf(WsdlXsdField("city", "xsd:string")))
                )
            )
            val output = wsdl.prettyPrint()

            assertTrue(output.contains("service: WeatherService"))
            assertTrue(output.contains("targetNamespace: http://example.com/weather"))
            assertTrue(output.contains("soapVersion: SOAP_1_2"))
            assertTrue(output.contains("portTypes:"))
            assertTrue(output.contains("WeatherPortType"))
            assertTrue(output.contains("operations:"))
            assertTrue(output.contains("GetWeather:"))
            assertTrue(output.contains("types:"))
            assertTrue(output.contains("GetWeatherRequest:"))
            assertTrue(output.contains("city: xsd:string"))
        }
    }

    @Nested
    inner class DataClassEquality {

        @Test
        fun `Given two identical CompactWsdl instances When comparing Then should be equal`() {
            val wsdl1 = minimalWsdl()
            val wsdl2 = minimalWsdl()

            assertEquals(wsdl1, wsdl2)
        }

        @Test
        fun `Given two identical WsdlOperation instances When comparing Then should be equal`() {
            val op1 = mockOperation()
            val op2 = mockOperation()

            assertEquals(op1, op2)
        }

        @Test
        fun `Given two identical WsdlPortType instances When comparing Then should be equal`() {
            val pt1 = WsdlPortType("CalculatorPortType")
            val pt2 = WsdlPortType("CalculatorPortType")

            assertEquals(pt1, pt2)
        }

        @Test
        fun `Given two identical WsdlXsdType instances When comparing Then should be equal`() {
            val type1 = WsdlXsdType("AddRequest", listOf(WsdlXsdField("a", "xsd:int")))
            val type2 = WsdlXsdType("AddRequest", listOf(WsdlXsdField("a", "xsd:int")))

            assertEquals(type1, type2)
        }

        @Test
        fun `Given two identical WsdlXsdField instances When comparing Then should be equal`() {
            val field1 = WsdlXsdField("intA", "xsd:int")
            val field2 = WsdlXsdField("intA", "xsd:int")

            assertEquals(field1, field2)
        }
    }

    @Nested
    inner class SoapVersionProperties {

        @Test
        fun `Given SOAP_1_1 When checking envelope namespace Then should return correct value`() {
            assertEquals("http://schemas.xmlsoap.org/soap/envelope/", SoapVersion.SOAP_1_1.envelopeNamespace)
        }

        @Test
        fun `Given SOAP_1_1 When checking content type Then should return text xml`() {
            assertEquals("text/xml", SoapVersion.SOAP_1_1.contentType)
        }

        @Test
        fun `Given SOAP_1_2 When checking envelope namespace Then should return correct value`() {
            assertEquals("http://www.w3.org/2003/05/soap-envelope", SoapVersion.SOAP_1_2.envelopeNamespace)
        }

        @Test
        fun `Given SOAP_1_2 When checking content type Then should return application soap xml`() {
            assertEquals("application/soap+xml", SoapVersion.SOAP_1_2.contentType)
        }
    }

    // Helper methods
    private fun minimalWsdl(
        serviceName: String = "CalculatorService",
        targetNamespace: String = "http://example.com/calculator",
        soapVersion: SoapVersion = SoapVersion.SOAP_1_1,
        portTypes: List<WsdlPortType> = listOf(WsdlPortType("CalculatorPortType")),
        operations: List<WsdlOperation> = listOf(mockOperation()),
        xsdTypes: Map<String, WsdlXsdType> = emptyMap()
    ) = CompactWsdl(
        serviceName = serviceName,
        targetNamespace = targetNamespace,
        soapVersion = soapVersion,
        portTypes = portTypes,
        operations = operations,
        xsdTypes = xsdTypes
    )

    private fun mockOperation(
        name: String = "Add",
        soapAction: String = "http://example.com/Add",
        inputMessage: String = "AddRequest",
        outputMessage: String = "AddResponse",
        portTypeName: String = "CalculatorPortType"
    ) = WsdlOperation(
        name = name,
        soapAction = soapAction,
        inputMessage = inputMessage,
        outputMessage = outputMessage,
        portTypeName = portTypeName
    )
}
