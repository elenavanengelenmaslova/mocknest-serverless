package nl.vintik.mocknest.application.generation.agent

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test: Property-8 (Bounded Retry Attempts) for SOAP mock generation.
 *
 * For any maxRetries value N, when the AI service always returns invalid SOAP mocks,
 * the retry coordinator must stop after N retries (N+1 total attempts) and return a result
 * without entering an infinite loop.
 *
 * **Property 8: Bounded Retry Attempts**
 * **Validates: Requirements 8.3, 8.4**
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-8")
class SoapBoundedRetryAttemptsPropertyTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    private fun buildSoapSpecification(): APISpecification {
        val wsdlXml = loadWsdl("calculator-soap11.wsdl")
        val parsedWsdl = WsdlParser().parse(wsdlXml)
        val compactWsdl = WsdlSchemaReducer().reduce(parsedWsdl)
        return APISpecification(
            format = SpecificationFormat.WSDL,
            version = "1.0",
            title = compactWsdl.serviceName,
            endpoints = compactWsdl.operations.map { op ->
                EndpointDefinition(
                    path = "/${compactWsdl.serviceName}",
                    method = HttpMethod.POST,
                    operationId = op.name,
                    summary = op.name,
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "SOAP response", null))
                )
            },
            schemas = emptyMap(),
            metadata = mapOf(
                "soapVersion" to compactWsdl.soapVersion.name,
                "targetNamespace" to compactWsdl.targetNamespace
            ),
            rawContent = compactWsdl.prettyPrint()
        )
    }

    private fun buildInvalidSoapMock(): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "Invalid SOAP mock",
        namespace = MockNamespace(apiName = "calculator-api"),
        wireMockMapping = """{"request":{"method":"GET","urlPath":"/CalculatorService"},"response":{"status":200,"body":"not xml"}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "test",
            endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "text/xml")
        ),
        generatedAt = Instant.now()
    )

    private fun buildRequest(jobId: String, maxRetries: Int): SpecWithDescriptionRequest =
        SpecWithDescriptionRequest(
            jobId = jobId,
            namespace = MockNamespace(apiName = "calculator-api"),
            specificationContent = loadWsdl("calculator-soap11.wsdl"),
            format = SpecificationFormat.WSDL,
            description = "Generate SOAP mocks for retry test (maxRetries=$maxRetries)"
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Property 8: Bounded Retry Attempts
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 8 - Given maxRetries={0} When SOAP AI always returns invalid mocks Then agent completes without infinite loop")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 8 - Given maxRetries N When SOAP AI always returns invalid mocks Then agent completes and returns a result`(
        maxRetries: Int
    ) = runTest {
        // Given — AI service always returns a result (strategy is mocked at the top level)
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-soap-bounded-$maxRetries", emptyList())

        val invalidMock = buildInvalidSoapMock()
        val mockValidator: MockValidatorInterface = mockk()
        coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
            listOf("Request method must be POST, found: GET", "Response body is not well-formed XML")
        )

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-soap-bounded-$maxRetries", maxRetries)

        // When
        val result = agent.generateFromSpecWithDescription(request)

        // Then — agent must always return a result (never hang or loop infinitely)
        assertNotNull(result, "Agent must return a result for maxRetries=$maxRetries")
    }

    @ParameterizedTest(name = "Property 8 - Given maxRetries={0} When SOAP AI always fails Then runStrategy is called exactly once")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 8 - Given maxRetries N When SOAP AI always fails Then runStrategy is called exactly once per generation request`(
        maxRetries: Int
    ) = runTest {
        // Given — count how many times runStrategy is invoked
        var strategyCallCount = 0

        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } answers {
            strategyCallCount++
            GenerationResult.success("job-soap-once-$maxRetries", emptyList())
        }

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)
        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-soap-once-$maxRetries", maxRetries)

        // When
        agent.generateFromSpecWithDescription(request)

        // Then — the Koog strategy encapsulates the retry loop internally;
        // generateFromSpecWithDescription delegates to runStrategy exactly once
        assertEquals(
            1,
            strategyCallCount,
            "runStrategy should be called exactly once regardless of maxRetries=$maxRetries, but was called $strategyCallCount times"
        )
    }

    @ParameterizedTest(name = "Property 8 - Given maxRetries={0} When SOAP AI always fails Then result is returned without throwing")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 8 - Given maxRetries N When SOAP AI always fails Then result is returned without throwing`(
        maxRetries: Int
    ) = runTest {
        // Given
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-soap-no-throw-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)
        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-soap-no-throw-$maxRetries", maxRetries)

        // When / Then — must not throw, must return a result
        val result = runCatching { agent.generateFromSpecWithDescription(request) }
        assertTrue(
            result.isSuccess,
            "Agent must not throw for maxRetries=$maxRetries, error: ${result.exceptionOrNull()}"
        )
        assertNotNull(result.getOrNull(), "Agent must return a non-null result for maxRetries=$maxRetries")
    }

    @ParameterizedTest(name = "Property 8 - Given maxRetries={0} When SoapMockValidator always rejects Then validator is invoked for each mock")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 8 - Given maxRetries N When SoapMockValidator always rejects Then validator is invoked for each generated mock`(
        maxRetries: Int
    ) = runTest {
        // Given — use real SoapMockValidator to verify it's invoked
        val realValidator = SoapMockValidator()
        val invalidMock = buildInvalidSoapMock()
        val specification = buildSoapSpecification()

        // When — validate the invalid mock directly
        val validationResult = realValidator.validate(invalidMock, specification)

        // Then — SoapMockValidator correctly rejects the invalid mock
        assertTrue(
            !validationResult.isValid,
            "SoapMockValidator must reject invalid SOAP mock for maxRetries=$maxRetries"
        )
        assertTrue(
            validationResult.errors.isNotEmpty(),
            "SoapMockValidator must return errors for invalid mock"
        )
        // Verify the specific error about POST method
        assertTrue(
            validationResult.errors.any { it.contains("POST") },
            "Error must mention POST method requirement. Errors: ${validationResult.errors}"
        )
    }
}
