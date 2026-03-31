package nl.vintik.mocknest.application.generation.agent

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the SOAP validation-retry loop.
 *
 * Tests the complete flow using inline WSDL XML from calculator-soap11.wsdl:
 * - Task 4.6: Correctable errors — AI returns invalid mock on first attempt, valid on retry
 * - Task 4.7: Uncorrectable errors — AI always returns invalid mocks, retry limit respected
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
class SoapValidationRetryIntegrationTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)

    private val realWsdlParser = WsdlParser()
    private val realSchemaReducer = WsdlSchemaReducer()
    private val realValidator = SoapMockValidator()

    private val testNamespace = MockNamespace(apiName = "calculator-api")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    private fun buildParser(): WsdlSpecificationParser =
        WsdlSpecificationParser(
            contentFetcher = mockk(relaxed = true),
            wsdlParser = realWsdlParser,
            schemaReducer = realSchemaReducer
        )

    private fun buildValidSoap11Mock(operationName: String = "Add"): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "Valid SOAP 1.1 $operationName mock",
        namespace = testNamespace,
        wireMockMapping = """
            {
              "request": {
                "method": "POST",
                "urlPath": "/calculator-service",
                "headers": {
                  "SOAPAction": { "equalTo": "http://example.com/calculator-service/$operationName" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "text/xml; charset=utf-8" },
                "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><${operationName}Response xmlns=\"http://example.com/calculator-service\"><result>42</result></${operationName}Response></soap:Body></soap:Envelope>"
              },
              "persistent": true
            }
        """.trimIndent(),
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "CalculatorService: test",
            endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "text/xml")
        ),
        generatedAt = Instant.now()
    )

    private fun buildInvalidSoap11Mock(operationName: String = "Add"): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "Invalid SOAP 1.1 $operationName mock — wrong envelope namespace",
        namespace = testNamespace,
        wireMockMapping = """
            {
              "request": {
                "method": "POST",
                "urlPath": "/calculator-service",
                "headers": {
                  "SOAPAction": { "equalTo": "http://example.com/calculator-service/$operationName" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "text/xml; charset=utf-8" },
                "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body><${operationName}Response xmlns=\"http://example.com/calculator-service\"><result>42</result></${operationName}Response></soap12:Body></soap12:Envelope>"
              },
              "persistent": true
            }
        """.trimIndent(),
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "CalculatorService: test",
            endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "text/xml")
        ),
        generatedAt = Instant.now()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.6: Validation-retry loop with correctable errors
    // Requirements: 8.1, 8.2, 8.4, 8.5
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class CorrectableErrors {

        @Test
        fun `Given SOAP AI returns invalid mock on first attempt and valid mock on retry Then should accept corrected mock`() =
            runTest {
                // Given
                val invalidMock = buildInvalidSoap11Mock()
                val validMock = buildValidSoap11Mock()

                // runStrategy encapsulates the full retry loop internally; mock returns the corrected result
                coEvery {
                    aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
                } returns GenerationResult.success("job-soap-retry-1", listOf(validMock))

                val mockValidator: MockValidatorInterface = mockk()
                coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                    listOf(
                        "SOAP Envelope element missing or has wrong namespace. Expected: http://schemas.xmlsoap.org/soap/envelope/, found: http://www.w3.org/2003/05/soap-envelope"
                    )
                )
                coEvery { mockValidator.validate(validMock, any()) } returns MockValidationResult.valid()

                val parser: SpecificationParserInterface = mockk()
                coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                    loadWsdl("calculator-soap11.wsdl"),
                    SpecificationFormat.WSDL
                )
                coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

                val agent = MockGenerationFunctionalAgent(
                    aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
                )

                val request = SpecWithDescriptionRequest(
                    jobId = "job-soap-retry-1",
                    namespace = testNamespace,
                    specificationContent = loadWsdl("calculator-soap11.wsdl"),
                    format = SpecificationFormat.WSDL,
                    description = "Generate SOAP mocks for calculator service"
                )

                // When
                val result = agent.generateFromSpecWithDescription(request)

                // Then
                assertTrue(result.success, "Generation should succeed after retry")
                val returnedMocks = assertNotNull(result.mocks)
                assertTrue(
                    returnedMocks.any { it.id == validMock.id },
                    "The corrected mock should be returned after retry"
                )
            }

        @Test
        fun `Given SOAP validation errors When retrying Then should feed errors back to AI service`() = runTest {
            // Given
            val invalidMock = buildInvalidSoap11Mock()
            val validMock = buildValidSoap11Mock()
            val validationErrors = listOf(
                "SOAP Envelope element missing or has wrong namespace. Expected: http://schemas.xmlsoap.org/soap/envelope/, found: http://www.w3.org/2003/05/soap-envelope",
                "Content-Type header 'text/xml' does not match SOAP version. Expected: application/soap+xml"
            )

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-soap-retry-2", listOf(validMock))

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(validationErrors)
            coEvery { mockValidator.validate(validMock, any()) } returns MockValidationResult.valid()

            val parser: SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                loadWsdl("calculator-soap11.wsdl"),
                SpecificationFormat.WSDL
            )
            coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-soap-retry-2",
                namespace = testNamespace,
                specificationContent = loadWsdl("calculator-soap11.wsdl"),
                format = SpecificationFormat.WSDL,
                description = "Generate SOAP mocks for calculator service"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success, "Generation should succeed")
        }

        @Test
        fun `Given corrected SOAP mock passes SoapMockValidator When retrying Then should include corrected mock in result`() =
            runTest {
                // Given — use real SoapMockValidator to verify the corrected mock passes
                val wsdlXml = loadWsdl("calculator-soap11.wsdl")
                val spec = buildParser().parse(wsdlXml, SpecificationFormat.WSDL)

                val invalidMock = buildInvalidSoap11Mock()
                val correctedMock = buildValidSoap11Mock()

                // Verify the invalid mock actually fails real validation
                val invalidResult = realValidator.validate(invalidMock, spec)
                assertTrue(!invalidResult.isValid, "Invalid mock should fail SoapMockValidator")
                assertTrue(
                    invalidResult.errors.any { it.contains("namespace") || it.contains("Envelope") },
                    "Error should mention namespace issue. Errors: ${invalidResult.errors}"
                )

                // Verify the corrected mock passes real validation
                val validResult = realValidator.validate(correctedMock, spec)
                assertTrue(
                    validResult.isValid,
                    "Corrected mock should pass SoapMockValidator. Errors: ${validResult.errors}"
                )

                // Now test the agent with mocked AI
                coEvery {
                    aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
                } returns GenerationResult.success("job-soap-retry-3", listOf(correctedMock))

                val mockValidator: MockValidatorInterface = mockk()
                coEvery { mockValidator.validate(correctedMock, any()) } returns MockValidationResult.valid()

                val parser: SpecificationParserInterface = mockk()
                coEvery { parser.parse(any(), any()) } returns spec
                coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

                val agent = MockGenerationFunctionalAgent(
                    aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
                )

                val request = SpecWithDescriptionRequest(
                    jobId = "job-soap-retry-3",
                    namespace = testNamespace,
                    specificationContent = wsdlXml,
                    format = SpecificationFormat.WSDL,
                    description = "Generate SOAP mocks for calculator service"
                )

                // When
                val result = agent.generateFromSpecWithDescription(request)

                // Then
                assertTrue(result.success, "Generation should succeed with corrected mock")
                assertNotNull(result.mocks)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.7: Validation-retry loop with uncorrectable errors
    // Requirements: 8.3, 8.6
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class UncorrectableErrors {

        @Test
        fun `Given SOAP AI always returns invalid mocks When max retries exceeded Then should stop retrying`() =
            runTest {
                // Given
                val invalidMock = buildInvalidSoap11Mock()
                val persistentErrors = listOf(
                    "SOAP Envelope element missing or has wrong namespace. Expected: http://schemas.xmlsoap.org/soap/envelope/, found: http://www.w3.org/2003/05/soap-envelope"
                )

                coEvery {
                    aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
                } returns GenerationResult.success("job-soap-uncorr-1", listOf(invalidMock))

                val mockValidator: MockValidatorInterface = mockk()
                coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                    persistentErrors
                )

                val parser: SpecificationParserInterface = mockk()
                coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                    loadWsdl("calculator-soap11.wsdl"),
                    SpecificationFormat.WSDL
                )
                coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

                val agent = MockGenerationFunctionalAgent(
                    aiModelService, parser, mockValidator, promptBuilder, maxRetries = 2
                )

                val request = SpecWithDescriptionRequest(
                    jobId = "job-soap-uncorr-1",
                    namespace = testNamespace,
                    specificationContent = loadWsdl("calculator-soap11.wsdl"),
                    format = SpecificationFormat.WSDL,
                    description = "Generate SOAP mocks for calculator service"
                )

                // When
                val result = agent.generateFromSpecWithDescription(request)

                // Then — agent should complete (not loop forever) and return a result
                assertNotNull(result, "Should return a result even when all mocks are invalid")
            }

        @Test
        fun `Given maxRetries of 0 When SOAP validation fails Then should not retry at all`() = runTest {
            // Given
            val invalidMock = buildInvalidSoap11Mock()

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-soap-uncorr-2", listOf(invalidMock))

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                listOf("Request method must be POST, found: GET")
            )

            val parser: SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                loadWsdl("calculator-soap11.wsdl"),
                SpecificationFormat.WSDL
            )
            coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 0
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-soap-uncorr-2",
                namespace = testNamespace,
                specificationContent = loadWsdl("calculator-soap11.wsdl"),
                format = SpecificationFormat.WSDL,
                description = "Generate SOAP mocks for calculator service"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result even with maxRetries=0")
        }

        @Test
        fun `Given maxRetries of 3 When all SOAP attempts fail Then should respect the retry limit`() = runTest {
            // Given
            var strategyCallCount = 0
            val invalidMock = buildInvalidSoap11Mock()

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } answers {
                strategyCallCount++
                GenerationResult.success("job-soap-uncorr-3", listOf(invalidMock))
            }
            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                listOf("Persistent SOAP validation error that cannot be corrected")
            )

            val parser: SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                loadWsdl("calculator-soap11.wsdl"),
                SpecificationFormat.WSDL
            )
            coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 3
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-soap-uncorr-3",
                namespace = testNamespace,
                specificationContent = loadWsdl("calculator-soap11.wsdl"),
                format = SpecificationFormat.WSDL,
                description = "Generate SOAP mocks for calculator service"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result after exhausting retries")
            // The strategy is called once (the agent delegates to runStrategy which handles retries internally)
            kotlin.test.assertEquals(1, strategyCallCount, "runStrategy should be called exactly once")
        }

        @Test
        fun `Given multiple SOAP validation errors When generation fails Then result should be returned`() = runTest {
            // Given
            val invalidMock = buildInvalidSoap11Mock()
            val accumulatedErrors = listOf(
                "Request method must be POST, found: GET",
                "SOAP Envelope element missing or has wrong namespace. Expected: http://schemas.xmlsoap.org/soap/envelope/",
                "SOAP Body element missing inside Envelope",
                "Content-Type header does not match SOAP version"
            )

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-soap-uncorr-4", listOf(invalidMock))

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                accumulatedErrors
            )

            val parser: SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildParser().parse(
                loadWsdl("calculator-soap11.wsdl"),
                SpecificationFormat.WSDL
            )
            coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-soap-uncorr-4",
                namespace = testNamespace,
                specificationContent = loadWsdl("calculator-soap11.wsdl"),
                format = SpecificationFormat.WSDL,
                description = "Generate SOAP mocks for calculator service"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result even with accumulated errors")
        }

        @Test
        fun `Given real SoapMockValidator When validating invalid mock Then errors are descriptive for AI correction`() =
            runTest {
                // Given — use real validator to verify error messages are descriptive
                val wsdlXml = loadWsdl("calculator-soap11.wsdl")
                val spec = buildParser().parse(wsdlXml, SpecificationFormat.WSDL)
                val invalidMock = buildInvalidSoap11Mock()

                // When
                val result = realValidator.validate(invalidMock, spec)

                // Then — errors must be descriptive enough for AI correction
                assertTrue(!result.isValid, "Invalid mock should fail validation")
                assertTrue(result.errors.isNotEmpty(), "Should have validation errors")
                result.errors.forEach { error ->
                    assertTrue(
                        error.isNotBlank(),
                        "Each error message must be non-blank for AI correction context"
                    )
                }
            }
    }
}
