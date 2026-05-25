package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    private fun loadTestDataWsdl(filename: String): String =
        this::class.java.getResource("/test-data/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test data file not found: $filename")

    @Nested
    inner class ParsedWsdlFieldExtraction {

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains non-blank serviceName`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertTrue(result.serviceName.isNotBlank(), "serviceName should not be blank for $filename")
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains non-blank targetNamespace`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertTrue(result.targetNamespace.isNotBlank(), "targetNamespace should not be blank for $filename")
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains SOAP_1_2 soapVersion`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertEquals(SoapVersion.SOAP_1_2, result.soapVersion, "soapVersion should be SOAP_1_2 for $filename")
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains at least one operation with non-blank name and soapAction`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertTrue(result.operations.isNotEmpty(), "operations should not be empty for $filename")
            result.operations.forEach { op ->
                assertTrue(op.name.isNotBlank(), "operation name should not be blank for $filename")
                assertTrue(op.soapAction.isNotBlank(), "soapAction should not be blank for operation ${op.name} in $filename")
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains at least one message entry`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertTrue(result.messages.isNotEmpty(), "messages should not be empty for $filename")
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When parsing Then ParsedWsdl contains at least one xsdType entry`(filename: String) {
            val result = parser.parse(loadTestDataWsdl(filename))
            assertTrue(result.xsdTypes.isNotEmpty(), "xsdTypes should not be empty for $filename")
        }

        @Test
        fun `Given simple greeting WSDL When parsing Then should extract expected service name`() {
            val result = parser.parse(loadTestDataWsdl("simple-greeting-soap12.wsdl"))
            assertEquals("GreetingService", result.serviceName)
            assertEquals("http://example.com/greeting", result.targetNamespace)
            assertEquals(1, result.operations.size)
            assertEquals("SayHello", result.operations.first().name)
        }

        @Test
        fun `Given calculator WSDL When parsing Then should extract all four operations`() {
            val result = parser.parse(loadTestDataWsdl("calculator-multi-op-soap12.wsdl"))
            assertEquals("CalculatorService", result.serviceName)
            assertEquals(4, result.operations.size)
            val opNames = result.operations.map { it.name }.toSet()
            assertTrue(opNames.containsAll(setOf("Add", "Subtract", "Multiply", "Divide")))
        }

        @Test
        fun `Given inventory WSDL When parsing Then should extract multiple messages`() {
            val result = parser.parse(loadTestDataWsdl("inventory-nested-types-soap12.wsdl"))
            assertEquals("InventoryService", result.serviceName)
            assertEquals(4, result.messages.size)
            assertTrue(result.messages.containsKey("GetProductRequest"))
            assertTrue(result.messages.containsKey("GetProductResponse"))
            assertTrue(result.messages.containsKey("UpdateStockRequest"))
            assertTrue(result.messages.containsKey("UpdateStockResponse"))
        }

        @Test
        fun `Given inventory WSDL When parsing Then should extract all xsd types including nested`() {
            val result = parser.parse(loadTestDataWsdl("inventory-nested-types-soap12.wsdl"))
            // Should include all defined types (reachable and unreachable)
            assertTrue(result.xsdTypes.containsKey("Product"))
            assertTrue(result.xsdTypes.containsKey("Category"))
            assertTrue(result.xsdTypes.containsKey("Department"))
            assertTrue(result.xsdTypes.containsKey("Supplier"))
            assertTrue(result.xsdTypes.containsKey("ContactInfo"))
            assertTrue(result.xsdTypes.containsKey("Address"))
            assertTrue(result.xsdTypes.containsKey("Warehouse"))
            // Unreachable types are still parsed (reduction happens in WsdlSchemaReducer)
            assertTrue(result.xsdTypes.containsKey("AuditLog"))
            assertTrue(result.xsdTypes.containsKey("DiscountPolicy"))
        }
    }

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
            val result = parser.parse(loadWsdl("element-with-type-ref-soap12.wsdl"))
            val getPersonType = result.xsdTypes["GetPerson"]
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
            val result = parser.parse(loadWsdl("mixed-types-soap12.wsdl"))
            assertTrue(
                result.xsdTypes.containsKey("Address"),
                "Should capture named complexType 'Address'. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("UserInfo"),
                "Should capture named complexType 'UserInfo'. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("CreateUser"),
                "Should capture top-level element 'CreateUser'. Found types: ${result.xsdTypes.keys}"
            )
            assertTrue(
                result.xsdTypes.containsKey("CreateUserResponse"),
                "Should capture top-level element 'CreateUserResponse'. Found types: ${result.xsdTypes.keys}"
            )
            assertEquals(4, result.xsdTypes.size, "Should have 4 types total (2 named + 2 elements)")
        }

        @Test
        fun `Given top-level element with inline complexType When parsing Then should extract fields correctly`() {
            val result = parser.parse(loadWsdl("mixed-types-soap12.wsdl"))
            val createUserType = result.xsdTypes["CreateUser"]
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
            val wsdl = loadWsdl("empty-element-soap12.wsdl")
            val result = parser.parse(wsdl)
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
            val wsdl = loadWsdl("missing-type-ref-soap12.wsdl")
            val result = parser.parse(wsdl)
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
    inner class MalformedXmlAndMissingElements {

        @Test
        @Timeout(2)
        fun `Given malformed XML When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("malformed.wsdl"))
            }
            assertTrue(ex.message?.contains("Malformed XML") == true || ex.message?.contains("line") == true)
        }

        @Test
        @Timeout(2)
        fun `Given malformed XML with unclosed tags When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadTestDataWsdl("malformed-unclosed-tag.wsdl"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Malformed XML") || message.contains("line"),
                "Exception should indicate malformed XML, got: $message"
            )
        }

        @Test
        @Timeout(2)
        fun `Given XML without wsdl definitions root When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val xml = """<?xml version="1.0"?><root><child/></root>"""
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(xml)
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("definitions"))
        }

        @Test
        @Timeout(2)
        fun `Given WSDL without targetNamespace When parsing Then should throw WsdlParsingException within 2 seconds`() {
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
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("targetNamespace"))
        }

        @Test
        @Timeout(2)
        fun `Given WSDL missing portType with no operations When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadTestDataWsdl("missing-porttype.wsdl"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("no operations") || message.contains("No SOAP binding") || message.contains("SOAP 1.2"),
                "Exception should indicate missing operations or binding issue, got: $message"
            )
        }

        @Test
        @Timeout(2)
        fun `Given WSDL with no operations When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse(loadWsdl("invalid-no-operations.wsdl"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("SOAP 1.2") == true,
                "Exception should mention SOAP 1.2 requirement, got: $message"
            )
        }

        @Test
        @Timeout(2)
        fun `Given completely invalid content When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse("this is not XML at all { } [ ]")
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Malformed XML"),
                "Exception should indicate malformed XML, got: $message"
            )
        }

        @Test
        @Timeout(2)
        fun `Given empty string When parsing Then should throw WsdlParsingException within 2 seconds`() {
            val ex = assertFailsWith<WsdlParsingException> {
                parser.parse("")
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Malformed XML"),
                "Exception should indicate malformed XML, got: $message"
            )
        }
    }

    @Nested
    inner class PerOperationBindingResolution {

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings and different service addresses When parsing Then operationBindings map should be populated`() {
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")
            val result = parser.parse(wsdl)
            assertTrue(result.operationBindings.isNotEmpty(), "operationBindings should be populated")
            assertEquals(2, result.operationBindings.size, "Should have 2 operation bindings")
        }

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings When parsing Then each operation should map to correct binding`() {
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")
            val result = parser.parse(wsdl)
            val getUserBinding = result.operationBindings["UserPortType#GetUser"]
            val getProductBinding = result.operationBindings["ProductPortType#GetProduct"]
            assertNotNull(getUserBinding, "GetUser operation should have binding")
            assertNotNull(getProductBinding, "GetProduct operation should have binding")
        }

        @Test
        fun `Given WSDL with multiple SOAP 1_2 bindings When parsing Then each binding should have correct service address`() {
            val wsdl = loadWsdl("multi-porttype-soap12.wsdl")
            val result = parser.parse(wsdl)
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
            val wsdl = loadWsdl("simple-soap12.wsdl")
            val result = parser.parse(wsdl)
            assertTrue(result.operationBindings.isNotEmpty(), "operationBindings should be populated even for single binding")
        }
    }
}
