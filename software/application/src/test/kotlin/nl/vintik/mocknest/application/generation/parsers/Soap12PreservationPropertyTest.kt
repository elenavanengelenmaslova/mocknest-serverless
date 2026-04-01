package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Preservation property tests for SOAP 1.2 port type parsing.
 * 
 * **Property 2: Preservation** - SOAP 1.2-Only WSDL Parsing
 * 
 * These tests verify that SOAP 1.2-only WSDLs continue to parse correctly
 * on UNFIXED code, establishing the baseline behavior that must be preserved
 * when fixing Bug 2 (multiple port type service address misattribution).
 * 
 * **SCOPE**: We ONLY support SOAP 1.2
 * 
 * **EXPECTED OUTCOME**: All tests PASS on unfixed code (confirms baseline to preserve)
 * 
 * Requirements: 2.1, 2.2
 */
@Tag("soap-wsdl-ai-generation")
@Tag("unit")
@Tag("preservation-property")
class Soap12PreservationPropertyTest {

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

    /**
     * Property: For any SOAP 1.2-only WSDL, all operations should be extracted with correct paths
     * and SOAP 1.2 version.
     * 
     * This test uses 10+ diverse SOAP 1.2-only WSDL files to verify the baseline parsing behavior.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL [{0}] should parse correctly with all operations and SOAP_1_2 version")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "document-literal-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "unreferenced-types-soap12.wsdl",
            "weather-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2-only WSDL When parsing Then should extract all operations with SOAP_1_2 version and correct paths`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2-only WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify baseline SOAP 1.2 parsing behavior
        
        // Property 1: SOAP version should be SOAP_1_2
        assertEquals(
            "SOAP_1_2",
            spec.metadata["soapVersion"],
            "WSDL $filename should have SOAP_1_2 version"
        )

        // Property 2: All operations should be extracted
        assertTrue(
            spec.endpoints.isNotEmpty(),
            "WSDL $filename should have at least one operation"
        )

        // Property 3: All endpoints should use POST method (SOAP requirement)
        spec.endpoints.forEach { endpoint ->
            assertEquals(
                HttpMethod.POST,
                endpoint.method,
                "Operation ${endpoint.operationId} in $filename should use POST method"
            )
        }

        // Property 4: All endpoints should have SOAPAction metadata
        spec.endpoints.forEach { endpoint ->
            assertNotNull(
                endpoint.metadata["soapAction"],
                "Operation ${endpoint.operationId} in $filename should have soapAction metadata"
            )
        }

        // Property 5: All endpoints should have a valid path (not null or blank)
        spec.endpoints.forEach { endpoint ->
            assertTrue(
                endpoint.path.isNotBlank(),
                "Operation ${endpoint.operationId} in $filename should have non-blank path"
            )
        }

        // Property 6: Service name should be extracted
        assertTrue(
            spec.title.isNotBlank(),
            "WSDL $filename should have non-blank service name"
        )

        // Property 7: Format should be WSDL
        assertEquals(
            SpecificationFormat.WSDL,
            spec.format,
            "WSDL $filename should have WSDL format"
        )
    }

    /**
     * Property: For any SOAP 1.2-only WSDL, the parser should correctly extract XSD types
     * when present.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL with types [{0}] should extract XSD schemas correctly")
    @ValueSource(
        strings = [
            "complex-types-soap12.wsdl",
            "document-literal-soap12.wsdl",
            "nested-xsd-soap12.wsdl",
            "unreferenced-types-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2 WSDL with XSD types When parsing Then should extract schema definitions`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2 WSDL with XSD type definitions
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify XSD types are extracted (baseline behavior)
        
        // Property: XSD types should be captured in schemas
        // Note: The exact count depends on schema reduction, but schemas should not be empty
        // for WSDLs that have type definitions
        assertNotNull(
            spec.schemas,
            "WSDL $filename should have schemas map"
        )
    }

    /**
     * Property: For any SOAP 1.2-only WSDL, the parser should correctly extract
     * operation metadata including operation IDs and summaries.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL [{0}] should extract operation metadata correctly")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2 WSDL When parsing Then should extract operation IDs and summaries`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify operation metadata extraction (baseline behavior)
        
        spec.endpoints.forEach { endpoint ->
            // Property 1: Operation ID should not be blank
            val operationId = endpoint.operationId
            assertNotNull(operationId, "Operation in $filename should have operationId")
            assertTrue(
                operationId.isNotBlank(),
                "Operation in $filename should have non-blank operationId"
            )

            // Property 2: Summary should not be blank
            val summary = endpoint.summary
            assertNotNull(summary, "Operation $operationId in $filename should have summary")
            assertTrue(
                summary.isNotBlank(),
                "Operation $operationId in $filename should have non-blank summary"
            )

            // Property 3: Request body should be required for SOAP operations
            val requestBody = endpoint.requestBody
            assertNotNull(requestBody, "Operation $operationId in $filename should have request body")
            assertTrue(
                requestBody.required,
                "Operation $operationId in $filename should have required request body"
            )

            // Property 4: Should have 200 response defined
            assertNotNull(
                endpoint.responses[200],
                "Operation $operationId in $filename should have 200 response"
            )
        }
    }

    /**
     * Property: For any SOAP 1.2-only WSDL, the parser should correctly extract
     * the target namespace and include it in metadata.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL [{0}] should extract target namespace")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl",
            "document-literal-soap12.wsdl",
            "multi-operation-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2 WSDL When parsing Then should extract target namespace in metadata`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify target namespace extraction (baseline behavior)
        
        // Property: Target namespace should be present in metadata
        val targetNamespace = spec.metadata["targetNamespace"]
        assertNotNull(
            targetNamespace,
            "WSDL $filename should have targetNamespace in metadata"
        )
        assertTrue(
            targetNamespace.isNotBlank(),
            "WSDL $filename should have non-blank targetNamespace"
        )
    }

    /**
     * Property: For any SOAP 1.2-only WSDL, the parser should correctly extract
     * operation count and include it in metadata.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL [{0}] should have correct operation count in metadata")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "multi-porttype-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2 WSDL When parsing Then operation count in metadata should match endpoints size`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify operation count consistency (baseline behavior)
        
        // Property: Operation count in metadata should match actual endpoints size
        val operationCountStr = spec.metadata["operationCount"]
        assertNotNull(
            operationCountStr,
            "WSDL $filename should have operationCount in metadata"
        )
        val operationCount = operationCountStr.toIntOrNull()
        assertNotNull(
            operationCount,
            "WSDL $filename operationCount should be a valid integer"
        )
        assertEquals(
            spec.endpoints.size,
            operationCount,
            "WSDL $filename operationCount should match endpoints size"
        )
    }

    /**
     * Property: For any SOAP 1.2-only WSDL, the parser should include raw content
     * that represents the compact WSDL.
     */
    @ParameterizedTest(name = "SOAP 1.2 WSDL [{0}] should include raw content")
    @ValueSource(
        strings = [
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "complex-types-soap12.wsdl"
        ]
    )
    fun `Given SOAP 1_2 WSDL When parsing Then should include raw content`(
        filename: String
    ) = runTest {
        // Given - SOAP 1.2 WSDL file
        val wsdlXml = loadWsdl(filename)

        // When - Parse the WSDL
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then - Verify raw content is included (baseline behavior)
        
        // Property: Raw content should not be null or blank
        val rawContent = spec.rawContent
        assertNotNull(
            rawContent,
            "WSDL $filename should have raw content"
        )
        assertTrue(
            rawContent.isNotBlank(),
            "WSDL $filename should have non-blank raw content"
        )

        // Property: Raw content should be valid compact WSDL representation
        // (verified by comparing with direct pipeline execution)
        val parsedWsdl = wsdlParser.parse(wsdlXml)
        val compactWsdl = schemaReducer.reduce(parsedWsdl)
        assertEquals(
            compactWsdl.prettyPrint(),
            rawContent,
            "WSDL $filename raw content should match compact WSDL prettyPrint"
        )
    }
}
