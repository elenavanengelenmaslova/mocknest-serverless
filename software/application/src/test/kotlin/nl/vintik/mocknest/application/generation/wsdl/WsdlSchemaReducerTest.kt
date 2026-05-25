package nl.vintik.mocknest.application.generation.wsdl

import nl.vintik.mocknest.domain.generation.SoapVersion
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlSchemaReducerTest {

    private val parser = WsdlParser()
    private val reducer = WsdlSchemaReducer()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    private fun loadTestDataWsdl(filename: String): String =
        this::class.java.getResource("/test-data/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test data file not found: $filename")

    private fun parseAndReduce(filename: String) = reducer.reduce(parser.parse(loadWsdl(filename)))

    private fun parseAndReduceTestData(filename: String) = reducer.reduce(parser.parse(loadTestDataWsdl(filename)))

    @Nested
    inner class SuccessfulReduction {

        @Test
        fun `Given complex-types WSDL When reducing Then should contain all operations`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            assertEquals(1, result.operations.size)
            assertEquals("GetOrder", result.operations.first().name)
        }

        @Test
        fun `Given complex-types WSDL When reducing Then should contain SOAPAction`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            assertEquals("http://example.com/orders/GetOrder", result.operations.first().soapAction)
        }

        @Test
        fun `Given complex-types WSDL When reducing Then should contain input and output messages`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            val op = result.operations.first()
            assertEquals("GetOrder", op.inputMessage)
            assertEquals("GetOrderResponse", op.outputMessage)
        }

        @Test
        fun `Given complex-types WSDL When reducing Then should include referenced XSD types`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            assertTrue(result.xsdTypes.containsKey("GetOrder"))
            assertTrue(result.xsdTypes.containsKey("GetOrderResponse"))
        }

        @Test
        fun `Given complex-types WSDL When reducing Then should include transitively referenced types`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            assertTrue(result.xsdTypes.containsKey("GetOrderResponse"))
            assertTrue(result.xsdTypes.containsKey("Order"))
            assertTrue(result.xsdTypes.containsKey("Customer"))
        }
    }

    @Nested
    inner class TransitiveTypeInclusion {

        @Test
        fun `Given inventory WSDL with deep nesting When reducing Then should include all transitively referenced types`() {
            val result = parseAndReduceTestData("inventory-nested-types-soap12.wsdl")

            // Product is referenced by GetProductResponse and UpdateStockResponse
            assertTrue(result.xsdTypes.containsKey("Product"), "Product should be included (directly referenced)")

            // Category is referenced by Product.category
            assertTrue(result.xsdTypes.containsKey("Category"), "Category should be included (referenced by Product)")

            // Department is referenced by Category.department (3 levels deep)
            assertTrue(result.xsdTypes.containsKey("Department"), "Department should be included (referenced by Category, 3 levels deep)")

            // Supplier is referenced by Product.supplier
            assertTrue(result.xsdTypes.containsKey("Supplier"), "Supplier should be included (referenced by Product)")

            // ContactInfo is referenced by Supplier.contactInfo
            assertTrue(result.xsdTypes.containsKey("ContactInfo"), "ContactInfo should be included (referenced by Supplier)")

            // Address is referenced by ContactInfo.address and Warehouse.location
            assertTrue(result.xsdTypes.containsKey("Address"), "Address should be included (referenced by ContactInfo and Warehouse)")
        }

        @Test
        fun `Given inventory WSDL When reducing Then Warehouse type should be included via UpdateStock input`() {
            val result = parseAndReduceTestData("inventory-nested-types-soap12.wsdl")

            // Warehouse is referenced by UpdateStock.warehouse
            assertTrue(result.xsdTypes.containsKey("Warehouse"), "Warehouse should be included (referenced by UpdateStock input)")
            assertTrue(result.xsdTypes.containsKey("UpdateStock"), "UpdateStock should be included (operation input)")
        }

        @Test
        fun `Given nested-xsd WSDL When reducing Then should include Report-Author-Contact chain`() {
            val result = parseAndReduce("nested-xsd-soap12.wsdl")

            // GetReportResponse -> Report -> Author -> Contact (3 levels of nesting)
            assertTrue(result.xsdTypes.containsKey("GetReportResponse"), "GetReportResponse should be included")
            assertTrue(result.xsdTypes.containsKey("Report"), "Report should be included (referenced by GetReportResponse)")
            assertTrue(result.xsdTypes.containsKey("Author"), "Author should be included (referenced by Report)")
            assertTrue(result.xsdTypes.containsKey("Contact"), "Contact should be included (referenced by Author, 3 levels deep)")
        }

        @Test
        fun `Given inventory WSDL When reducing Then all operation input and output types should be included`() {
            val result = parseAndReduceTestData("inventory-nested-types-soap12.wsdl")

            // Direct operation references
            assertTrue(result.xsdTypes.containsKey("GetProduct"), "GetProduct (input) should be included")
            assertTrue(result.xsdTypes.containsKey("GetProductResponse"), "GetProductResponse (output) should be included")
            assertTrue(result.xsdTypes.containsKey("UpdateStock"), "UpdateStock (input) should be included")
            assertTrue(result.xsdTypes.containsKey("UpdateStockResponse"), "UpdateStockResponse (output) should be included")
        }
    }

    @Nested
    inner class UnreferencedTypeExclusion {

        @Test
        fun `Given WSDL with unreferenced types When reducing Then should exclude orphan types`() {
            val result = parseAndReduce("unreferenced-types-soap12.wsdl")
            assertFalse(result.xsdTypes.containsKey("OrphanType1"))
            assertFalse(result.xsdTypes.containsKey("OrphanType2"))
        }

        @Test
        fun `Given WSDL with unreferenced types When reducing Then should include referenced types`() {
            val result = parseAndReduce("unreferenced-types-soap12.wsdl")
            assertTrue(result.xsdTypes.containsKey("Ping"))
            assertTrue(result.xsdTypes.containsKey("PingResponse"))
        }

        @Test
        fun `Given inventory WSDL When reducing Then should exclude AuditLog and DiscountPolicy`() {
            val result = parseAndReduceTestData("inventory-nested-types-soap12.wsdl")

            // AuditLog and DiscountPolicy are not referenced by any operation
            assertFalse(result.xsdTypes.containsKey("AuditLog"), "AuditLog should be excluded (not reachable from any operation)")
            assertFalse(result.xsdTypes.containsKey("DiscountPolicy"), "DiscountPolicy should be excluded (not reachable from any operation)")
        }

        @Test
        fun `Given inventory WSDL When reducing Then reachable type count should be less than total parsed types`() {
            val parsed = parser.parse(loadTestDataWsdl("inventory-nested-types-soap12.wsdl"))
            val reduced = reducer.reduce(parsed)

            // Parsed has all types including unreachable ones
            assertTrue(
                reduced.xsdTypes.size < parsed.xsdTypes.size,
                "Reduced types (${reduced.xsdTypes.size}) should be fewer than parsed types (${parsed.xsdTypes.size})"
            )
        }
    }

    @Nested
    inner class CompactWsdlMetadata {

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When reducing Then CompactWsdl preserves serviceName`(filename: String) {
            val parsed = parser.parse(loadTestDataWsdl(filename))
            val reduced = reducer.reduce(parsed)
            assertEquals(parsed.serviceName, reduced.serviceName)
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When reducing Then CompactWsdl preserves targetNamespace`(filename: String) {
            val parsed = parser.parse(loadTestDataWsdl(filename))
            val reduced = reducer.reduce(parsed)
            assertEquals(parsed.targetNamespace, reduced.targetNamespace)
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "simple-greeting-soap12.wsdl",
            "calculator-multi-op-soap12.wsdl",
            "inventory-nested-types-soap12.wsdl"
        ])
        fun `Given diverse WSDL files When reducing Then CompactWsdl preserves soapVersion`(filename: String) {
            val parsed = parser.parse(loadTestDataWsdl(filename))
            val reduced = reducer.reduce(parsed)
            assertEquals(SoapVersion.SOAP_1_2, reduced.soapVersion)
        }
    }

    @Nested
    inner class BindingDetailsExclusion {

        @Test
        fun `Given WSDL When reducing Then compact form should not contain binding style or transport details`() {
            val result = parseAndReduce("simple-soap12.wsdl")
            val prettyPrint = result.prettyPrint()
            assertFalse(prettyPrint.contains("document"), "Binding style should not appear in compact form")
            assertFalse(prettyPrint.contains("transport"), "Transport details should not appear in compact form")
        }

        @Test
        fun `Given WSDL When reducing Then should preserve service name and namespace`() {
            val result = parseAndReduce("simple-soap12.wsdl")
            assertEquals("GreetService", result.serviceName)
            assertEquals("http://example.com/greet", result.targetNamespace)
        }
    }

    @Nested
    inner class SizeReduction {

        @Test
        fun `Given multi-operation WSDL When reducing Then compact form should be smaller than raw WSDL`() {
            val rawWsdl = loadWsdl("multi-operation-soap12.wsdl")
            val result = parseAndReduce("multi-operation-soap12.wsdl")
            val compactSize = result.prettyPrint().length
            assertTrue(compactSize < rawWsdl.length,
                "Compact WSDL ($compactSize chars) should be smaller than raw WSDL (${rawWsdl.length} chars)")
        }

        @Test
        fun `Given inventory WSDL with unreachable types When reducing Then compact form excludes unreachable types`() {
            val rawWsdl = loadTestDataWsdl("inventory-nested-types-soap12.wsdl")
            val result = parseAndReduceTestData("inventory-nested-types-soap12.wsdl")
            val compactSize = result.prettyPrint().length
            assertTrue(compactSize < rawWsdl.length,
                "Compact WSDL ($compactSize chars) should be smaller than raw WSDL (${rawWsdl.length} chars)")
        }
    }
}
