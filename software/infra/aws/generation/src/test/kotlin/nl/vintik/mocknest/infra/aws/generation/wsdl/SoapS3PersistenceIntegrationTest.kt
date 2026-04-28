package nl.vintik.mocknest.infra.aws.generation.wsdl

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test: inline WSDL XML → parsing → SoapMockValidator.
 *
 * Tests the complete generation flow without S3 persistence:
 * inline WSDL XML → WsdlSpecificationParser → WsdlSchemaReducer → APISpecification
 *   → AI generation (simulated) → SoapMockValidator
 *
 * Validates: Requirements 10.1, 10.2, 12.9
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
class SoapS3PersistenceIntegrationTest {

    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()
    private val soapValidator = SoapMockValidator()
    private val specParser = WsdlSpecificationParser(
        contentFetcher = mockk(relaxed = true),
        wsdlParser = wsdlParser,
        schemaReducer = schemaReducer
    )

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    private fun buildValidSoap12Mock(
        namespace: MockNamespace,
        operationName: String = "Add"
    ): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "SOAP 1.2 $operationName mock",
        namespace = namespace,
        wireMockMapping = """
            {
              "request": {
                "method": "POST",
                "urlPath": "/calculator-service",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/calculator-service/$operationName\"" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body><${operationName}Response xmlns=\"http://example.com/calculator-service\"><result>42</result></${operationName}Response></soap:Body></soap:Envelope>"
              },
              "persistent": true
            }
        """.trimIndent(),
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "CalculatorService: test",
            endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "application/soap+xml")
        ),
        generatedAt = Instant.now()
    )

    @Test
    fun `Given inline WSDL XML When running complete flow Then generated mocks pass validation`() = runTest {
        // Given — parse inline WSDL XML
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")
        val namespace = MockNamespace(apiName = "calculator-api")

        val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
        assertEquals(SpecificationFormat.WSDL, spec.format)
        assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints from WSDL operations")

        // Simulate AI generation — produce valid SOAP mocks
        val generatedMocks = spec.endpoints.map { endpoint ->
            buildValidSoap12Mock(namespace, endpoint.operationId ?: "Add")
        }
        assertTrue(generatedMocks.isNotEmpty(), "Should have generated mocks")

        // Validate each mock with SoapMockValidator
        generatedMocks.forEach { mock ->
            val validationResult = soapValidator.validate(mock, spec)
            assertTrue(
                validationResult.isValid,
                "Mock ${mock.name} should pass SoapMockValidator. Errors: ${validationResult.errors}"
            )
        }

        // Verify WireMock mapping structure
        generatedMocks.forEach { mock ->
            assertTrue(mock.wireMockMapping.contains("\"method\""), "Should contain 'method' field")
            assertTrue(mock.wireMockMapping.contains("\"response\""), "Should contain 'response' field")
            assertTrue(mock.wireMockMapping.contains("\"request\""), "Should contain 'request' field")
        }
    }

    @Test
    fun `Given inline WSDL XML When parsing spec Then specification has correct format and endpoints`() = runTest {
        // Given
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")

        // When
        val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)

        // Then
        assertEquals(SpecificationFormat.WSDL, spec.format)
        assertEquals("CalculatorService", spec.title)
        assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints")
        spec.endpoints.forEach { endpoint ->
            assertEquals(HttpMethod.POST, endpoint.method, "All SOAP endpoints should use POST")
            assertNotNull(endpoint.operationId, "Each endpoint should have an operationId")
        }
    }

    @Test
    fun `Given SOAP mock When validated Then WireMock mapping structure is correct`() = runTest {
        // Given
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")
        val namespace = MockNamespace(apiName = "calculator-api")

        val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
        val mock = buildValidSoap12Mock(namespace, "Add")

        // When
        val validationResult = soapValidator.validate(mock, spec)

        // Then — validation passes and WireMock structure is intact
        assertTrue(
            validationResult.isValid,
            "Mock should pass SoapMockValidator. Errors: ${validationResult.errors}"
        )
        assertTrue(mock.wireMockMapping.contains("\"method\""))
        assertTrue(mock.wireMockMapping.contains("\"request\""))
        assertTrue(mock.wireMockMapping.contains("\"response\""))
    }

    @Test
    fun `Given multiple SOAP operations When all mocks generated Then all pass validation`() = runTest {
        // Given — calculator has Add, Subtract, Multiply operations
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")
        val namespace = MockNamespace(apiName = "calculator-api")

        val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)
        val operationNames = spec.endpoints.mapNotNull { it.operationId }
        assertTrue(operationNames.isNotEmpty(), "Calculator WSDL should have operations")

        val mocks = operationNames.map { opName -> buildValidSoap12Mock(namespace, opName) }

        // When / Then — all mocks pass validation
        mocks.forEach { mock ->
            val result = soapValidator.validate(mock, spec)
            assertTrue(result.isValid, "Mock '${mock.name}' should be valid. Errors: ${result.errors}")
        }
        assertEquals(operationNames.size, mocks.size)
    }

    @Test
    fun `Given invalid SOAP mock When validated Then should fail validation`() = runTest {
        // Given
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")
        val namespace = MockNamespace(apiName = "calculator-api")
        val spec = specParser.parse(wsdlXml, SpecificationFormat.WSDL)

        val invalidMock = GeneratedMock(
            id = UUID.randomUUID().toString(),
            name = "Invalid SOAP mock",
            namespace = namespace,
            wireMockMapping = """{"request":{"method":"GET","urlPath":"/CalculatorService"},"response":{"status":200}}""",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "CalculatorService: test",
                endpoint = EndpointInfo(HttpMethod.GET, "/CalculatorService", 200, "text/xml")
            ),
            generatedAt = Instant.now()
        )

        // When
        val validationResult = soapValidator.validate(invalidMock, spec)

        // Then
        assertFalse(validationResult.isValid, "Invalid mock should fail validation")
        assertTrue(validationResult.errors.isNotEmpty(), "Should report validation errors")
    }
}
