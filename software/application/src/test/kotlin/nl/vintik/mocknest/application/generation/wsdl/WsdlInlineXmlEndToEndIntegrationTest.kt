package nl.vintik.mocknest.application.generation.wsdl

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for the complete inline XML path (no AWS dependencies).
 *
 * Tests the full chain:
 * inline WSDL XML → WsdlSpecificationParser.parse() → WsdlSchemaReducer.reduce()
 *   → APISpecification → prompt construction → SoapMockValidator.validate()
 *
 * Validates: Requirements 1.1, 3.8, 4.8, 12.8
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
class WsdlInlineXmlEndToEndIntegrationTest {

    private val contentFetcher: WsdlContentFetcherInterface = mockk(relaxed = true)
    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()
    private val parser = WsdlSpecificationParser(contentFetcher, wsdlParser, schemaReducer)
    private val promptBuilder = PromptBuilderService()
    private val validator = SoapMockValidator()

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @Test
    fun `Given SOAP 1_1 inline WSDL When running full pipeline Then should produce valid APISpecification`() =
        runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")

            // When — parse inline XML
            val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then — APISpecification is correct
            assertEquals(SpecificationFormat.WSDL, spec.format)
            assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints from WSDL operations")
            spec.endpoints.forEach { endpoint ->
                assertEquals(HttpMethod.POST, endpoint.method, "All SOAP endpoints must use POST")
                assertNotNull(endpoint.metadata["soapAction"], "Each endpoint must have soapAction metadata")
            }
        }

    @Test
    fun `Given SOAP 1_1 inline WSDL When running full pipeline Then rawContent should equal prettyPrint output`() =
        runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")

            // When
            val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then — rawContent is the CompactWsdl prettyPrint output
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            val compactWsdl = schemaReducer.reduce(parsedWsdl)
            assertEquals(
                compactWsdl.prettyPrint(),
                spec.rawContent,
                "rawContent must equal CompactWsdl.prettyPrint()"
            )
        }

    @Test
    fun `Given SOAP 1_1 inline WSDL When running full pipeline Then endpoint count should match WSDL operations`() =
        runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")

            // When
            val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)
            val parsedWsdl = wsdlParser.parse(wsdlXml)

            // Then — endpoint count matches operation count
            assertEquals(
                parsedWsdl.operations.size,
                spec.endpoints.size,
                "Endpoint count must match WSDL operation count"
            )
        }

    @Test
    fun `Given SOAP 1_1 inline WSDL When building prompt Then should produce non-empty prompt`() = runTest {
        // Given
        val wsdlXml = loadWsdl("calculator-soap11.wsdl")
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)
        val namespace = MockNamespace(apiName = "calculator")

        // When — build prompt from specification
        val prompt = promptBuilder.buildSpecWithDescriptionPrompt(
            specification = spec,
            description = "Generate SOAP mocks for the calculator service",
            namespace = namespace
        )

        // Then
        assertTrue(prompt.isNotBlank(), "Prompt must not be blank")
        assertTrue(prompt.contains("calculator", ignoreCase = true), "Prompt should reference the service")
    }

    @Test
    fun `Given SOAP 1_2 inline WSDL When running full pipeline Then should produce valid APISpecification`() =
        runTest {
            // Given
            val wsdlXml = loadWsdl("weather-soap12.wsdl")

            // When
            val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then
            assertEquals(SpecificationFormat.WSDL, spec.format)
            assertEquals("SOAP_1_2", spec.metadata["soapVersion"])
            assertTrue(spec.endpoints.isNotEmpty())
            spec.endpoints.forEach { endpoint ->
                assertEquals(HttpMethod.POST, endpoint.method)
            }
        }

    @Test
    fun `Given SOAP 1_2 inline WSDL When running full pipeline Then rawContent should equal prettyPrint output`() =
        runTest {
            // Given
            val wsdlXml = loadWsdl("weather-soap12.wsdl")

            // When
            val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then
            val parsedWsdl = wsdlParser.parse(wsdlXml)
            val compactWsdl = schemaReducer.reduce(parsedWsdl)
            assertEquals(compactWsdl.prettyPrint(), spec.rawContent)
        }

    @Test
    fun `Given SOAP 1_1 mock When validating against parsed spec Then valid mock should pass`() = runTest {
        // Given
        val wsdlXml = loadWsdl("calculator-soap11.wsdl")
        val spec = parser.parse(wsdlXml, SpecificationFormat.WSDL)
        val targetNamespace = spec.metadata["targetNamespace"] ?: ""
        val firstOperation = spec.endpoints.first()
        val soapAction = firstOperation.metadata["soapAction"] ?: ""

        val validSoapMock = GeneratedMock(
            id = UUID.randomUUID().toString(),
            name = "Calculator SOAP mock",
            namespace = MockNamespace(apiName = "calculator"),
            wireMockMapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/calculator",
                    "headers": {
                      "SOAPAction": { "equalTo": "$soapAction" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": {
                      "Content-Type": "text/xml"
                    },
                    "body": "<?xml version=\"1.0\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><AddResponse xmlns=\"$targetNamespace\"><AddResult>42</AddResult></AddResponse></soap:Body></soap:Envelope>"
                  },
                  "persistent": true
                }
            """.trimIndent(),
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "calculator-soap11.wsdl",
                endpoint = EndpointInfo(
                    method = HttpMethod.POST,
                    path = "/calculator",
                    statusCode = 200,
                    contentType = "text/xml"
                )
            ),
            generatedAt = Instant.now()
        )

        // When
        val result = validator.validate(validSoapMock, spec)

        // Then
        assertTrue(
            result.isValid,
            "Valid SOAP 1.1 mock should pass validation. Errors: ${result.errors}"
        )
    }
}
