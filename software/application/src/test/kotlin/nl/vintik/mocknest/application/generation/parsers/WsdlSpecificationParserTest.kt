package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlSpecificationParserTest {

    private val contentFetcher: WsdlContentFetcherInterface = mockk(relaxed = true)
    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()
    private val parser = WsdlSpecificationParser(contentFetcher, wsdlParser, schemaReducer)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @Nested
    inner class Soap12InlineParsing {

        @Test
        fun `Given SOAP 1_2 inline WSDL XML When parsing Then should return APISpecification with POST endpoints and SOAPAction metadata`() =
            runTest {
                // Given
                val wsdlXml = loadWsdl("simple-soap12.wsdl")

                // When
                val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

                // Then
                assertEquals(SpecificationFormat.WSDL, spec.format)
                assertEquals("GreetService", spec.title)
                assertTrue(spec.endpoints.isNotEmpty())

                val endpoint = spec.endpoints.first()
                assertEquals(HttpMethod.POST, endpoint.method)
                assertNotNull(endpoint.metadata["soapAction"])
                assertEquals("http://example.com/greet/Greet", endpoint.metadata["soapAction"])
                assertEquals("Greet", endpoint.operationId)
            }

        @Test
        fun `Given SOAP 1_2 inline WSDL XML When parsing Then should not call content fetcher`() = runTest {
            // Given
            val wsdlXml = loadWsdl("simple-soap12.wsdl")

            // When
            parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then
            coVerify(exactly = 0) { contentFetcher.fetch(any()) }
        }
    }

    @Nested
    inner class Soap12InlineParsingVersion {

        @Test
        fun `Given SOAP 1_2 inline WSDL XML When parsing Then should propagate SOAP version in APISpecification`() =
            runTest {
                // Given
                val wsdlXml = loadWsdl("simple-soap12.wsdl")

                // When
                val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

                // Then
                assertEquals("SOAP_1_2", spec.metadata["soapVersion"])
                assertEquals("GreetService", spec.title)
                assertTrue(spec.endpoints.isNotEmpty())
                assertEquals(HttpMethod.POST, spec.endpoints.first().method)
            }

        @Test
        fun `Given SOAP 1_2 inline WSDL XML When parsing Then should not call content fetcher`() = runTest {
            // Given
            val wsdlXml = loadWsdl("simple-soap12.wsdl")

            // When
            parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then
            coVerify(exactly = 0) { contentFetcher.fetch(any()) }
        }
    }

    @Nested
    inner class SupportsFormat {

        @Test
        fun `Given WSDL format When checking supports Then should return true`() {
            assertTrue(parser.supports(SpecificationFormat.WSDL))
        }

        @Test
        fun `Given non-WSDL formats When checking supports Then should return false`() {
            assertFalse(parser.supports(SpecificationFormat.OPENAPI_3))
            assertFalse(parser.supports(SpecificationFormat.SWAGGER_2))
            assertFalse(parser.supports(SpecificationFormat.GRAPHQL))
        }
    }

    @Nested
    inner class MalformedXml {

        @Test
        fun `Given malformed inline XML When parsing Then should throw WsdlParsingException`() = runTest {
            // Given
            val malformedXml = loadWsdl("malformed.wsdl")

            // When / Then
            assertFailsWith<WsdlParsingException> {
                parser.parse(malformedXml, SpecificationFormat.WSDL)
            }
        }
    }

    @Nested
    inner class RawContent {

        @Test
        fun `Given valid inline WSDL XML When parsing Then rawContent should equal compactWsdl prettyPrint output`() =
            runTest {
                // Given
                val wsdlXml = loadWsdl("simple-soap12.wsdl")

                // When
                val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

                // Then — derive the expected prettyPrint by running the same pipeline
                val parsedWsdl = wsdlParser.parse(wsdlXml)
                val compactWsdl = schemaReducer.reduce(parsedWsdl)
                assertEquals(compactWsdl.prettyPrint(), spec.rawContent)
            }
    }

    @Nested
    inner class FetcherNotCalled {

        @Test
        fun `Given inline WSDL XML When parsing Then should not call content fetcher`() = runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap12.wsdl")

            // When
            parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then
            coVerify(exactly = 0) { contentFetcher.fetch(any()) }
        }
    }

    @Nested
    inner class ValidateMethod {

        @Test
        fun `Given valid WSDL inline XML When validating Then should return valid result`() = runTest {
            // Given
            val wsdlXml = loadWsdl("simple-soap12.wsdl")

            // When
            val result = parser.validate(wsdlXml, SpecificationFormat.WSDL)

            // Then
            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given malformed WSDL inline XML When validating Then should return invalid result with error`() = runTest {
            // Given
            val malformedXml = loadWsdl("malformed.wsdl")

            // When
            val result = parser.validate(malformedXml, SpecificationFormat.WSDL)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        fun `Given WSDL with no operations When validating Then should return invalid result`() = runTest {
            // Given
            val noOpsXml = loadWsdl("invalid-no-operations.wsdl")

            // When
            val result = parser.validate(noOpsXml, SpecificationFormat.WSDL)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        fun `Given wrong format When validating Then should throw IllegalArgumentException`() = runTest {
            // Given / When / Then
            assertFailsWith<IllegalArgumentException> {
                parser.validate("content", SpecificationFormat.OPENAPI_3)
            }
        }
    }

    @Nested
    inner class ExtractMetadataMethod {

        @Test
        fun `Given valid SOAP 1_2 WSDL When extracting metadata Then should return correct metadata`() = runTest {
            // Given
            val wsdlXml = loadWsdl("simple-soap12.wsdl")

            // When
            val metadata = parser.extractMetadata(wsdlXml, SpecificationFormat.WSDL)

            // Then
            assertEquals("GreetService", metadata.title)
            assertEquals("1.0", metadata.version)
            assertEquals(SpecificationFormat.WSDL, metadata.format)
            assertTrue(metadata.endpointCount > 0)
        }

        @Test
        fun `Given calculator WSDL When extracting metadata Then should return correct operation count`() = runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap12.wsdl")

            // When
            val metadata = parser.extractMetadata(wsdlXml, SpecificationFormat.WSDL)

            // Then
            assertEquals("CalculatorService", metadata.title)
            assertEquals(3, metadata.endpointCount) // Add, Subtract, Multiply
        }

        @Test
        fun `Given WSDL with XSD types When extracting metadata Then should return correct schema count`() = runTest {
            // Given
            val wsdlXml = loadWsdl("complex-types-soap12.wsdl")

            // When
            val metadata = parser.extractMetadata(wsdlXml, SpecificationFormat.WSDL)

            // Then
            assertTrue(metadata.schemaCount > 0)
        }

        @Test
        fun `Given wrong format When extracting metadata Then should throw IllegalArgumentException`() = runTest {
            // Given / When / Then
            assertFailsWith<IllegalArgumentException> {
                parser.extractMetadata("content", SpecificationFormat.GRAPHQL)
            }
        }
    }

    @Nested
    @Tag("bug-condition-exploration")
    inner class MultiplePortTypesMisattribution {

        @Test
        fun `Given WSDL with multiple SOAP 1_2 port types and different service addresses When parsing Then all operations should have correct per-binding service address`() =
            runTest {
                // Given - WSDL with two port types (UserPortType and ProductPortType) with different service addresses
                val wsdlXml = loadWsdl("multi-porttype-soap12.wsdl")

                // When
                val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

                // Then - Bug Condition: All operations get same service address
                // Expected behavior (after fix): Each operation should have its binding-specific service address
                
                // Find GetUser and GetProduct operations
                val getUserEndpoint = spec.endpoints.find { it.operationId == "GetUser" }
                val getProductEndpoint = spec.endpoints.find { it.operationId == "GetProduct" }
                
                assertNotNull(getUserEndpoint, "GetUser operation should be present")
                assertNotNull(getProductEndpoint, "GetProduct operation should be present")

                // EXPECTED BEHAVIOR (this test encodes the correct behavior):
                // GetUser operation should use UserBinding with /multiport/user path
                // GetProduct operation should use ProductBinding with /multiport/product path
                
                // Check GetUser operation
                assertEquals(
                    "/multiport/user",
                    getUserEndpoint.path,
                    "GetUser operation should use UserBinding service address /multiport/user"
                )

                // Check GetProduct operation
                assertEquals(
                    "/multiport/product",
                    getProductEndpoint.path,
                    "GetProduct operation should use ProductBinding service address /multiport/product"
                )

                // COUNTEREXAMPLE DOCUMENTATION:
                // On UNFIXED code, this test will FAIL because:
                // 1. WsdlSpecificationParser.serviceAddressPath() extracts first service address only
                // 2. All operations receive same path regardless of their binding
                // Expected failure: Both operations will have same path (likely /multiport/user - the first one)
            }
    }
}
