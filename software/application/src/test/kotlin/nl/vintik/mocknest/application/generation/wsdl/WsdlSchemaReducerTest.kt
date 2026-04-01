package nl.vintik.mocknest.application.generation.wsdl

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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

    private fun parseAndReduce(filename: String) = reducer.reduce(parser.parse(loadWsdl(filename)))

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
            // GetOrder and GetOrderResponse are directly referenced
            assertTrue(result.xsdTypes.containsKey("GetOrder"))
            assertTrue(result.xsdTypes.containsKey("GetOrderResponse"))
        }

        @Test
        fun `Given complex-types WSDL When reducing Then should include transitively referenced types`() {
            val result = parseAndReduce("complex-types-soap12.wsdl")
            // Order is referenced by GetOrderResponse.order field
            // Customer is referenced by Order.customer field
            assertTrue(result.xsdTypes.containsKey("GetOrderResponse"))
            assertTrue(result.xsdTypes.containsKey("Order"))
            assertTrue(result.xsdTypes.containsKey("Customer"))
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
    }

    @Nested
    inner class BindingDetailsExclusion {

        @Test
        fun `Given WSDL When reducing Then compact form should not contain binding style or transport details`() {
            val result = parseAndReduce("simple-soap12.wsdl")
            // Binding details (style, transport) are excluded from the compact form
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
    }
}
