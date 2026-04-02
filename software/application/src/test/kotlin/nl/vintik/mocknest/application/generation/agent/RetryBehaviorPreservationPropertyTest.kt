package nl.vintik.mocknest.application.generation.agent

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Preservation Property Test: Test Completion Verification
 *
 * **Property 2: Preservation** - Test Completion Verification
 *
 * **IMPORTANT**: Follow observation-first methodology
 *
 * This test observes and documents the behavior on UNFIXED code that must be preserved:
 * 1. Agent completes for all maxRetries values [0, 1, 2, 3]
 * 2. runStrategy is called exactly once per generation request
 *
 * **EXPECTED OUTCOME**: Tests PASS (this confirms baseline completion to preserve)
 *
 * **Preservation Requirements**:
 * - 6.1: Agent must complete without hanging for all maxRetries values
 * - 6.2: runStrategy must be called exactly once per generation request
 *
 * **After the fix**: These tests must CONTINUE TO PASS, ensuring that:
 * - The agent still completes successfully for all maxRetries values
 * - The delegation to runStrategy remains a single call per request
 * - No regressions are introduced in the agent's completion behavior
 *
 * **Validates: Requirements 6.1, 6.2**
 */
@Tag("soap-wsdl-ai-generation")
@Tag("preservation")
class RetryBehaviorPreservationPropertyTest {

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
        val wsdlXml = loadWsdl("calculator-soap12.wsdl")
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

    private fun buildRequest(jobId: String, maxRetries: Int): SpecWithDescriptionRequest =
        SpecWithDescriptionRequest(
            jobId = jobId,
            namespace = MockNamespace(apiName = "calculator-api"),
            specificationContent = loadWsdl("calculator-soap12.wsdl"),
            format = SpecificationFormat.WSDL,
            description = "Generate SOAP mocks for preservation test (maxRetries=$maxRetries)"
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation Property Tests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * **Preservation Test 1**: Agent completes for all maxRetries values
     *
     * **Observed Behavior on UNFIXED code**:
     * - Agent successfully completes and returns a result for maxRetries = 0, 1, 2, 3
     * - No hanging or infinite loops occur
     * - Result is always non-null
     *
     * **Must Preserve**: After fixing the retry logic exercise bug, the agent must
     * CONTINUE TO complete successfully for all maxRetries values without hanging.
     *
     * **Validates: Requirement 6.1**
     */
    @ParameterizedTest(name = "Preservation - Given maxRetries={0} When agent runs Then agent completes without hanging")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Preservation - Given maxRetries N When agent runs Then agent completes and returns result`(
        maxRetries: Int
    ) = runTest {
        // Given — Mock runStrategy to return success (current pattern on unfixed code)
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-preserve-complete-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-preserve-complete-$maxRetries", maxRetries)

        // When
        val result = agent.generateFromSpecWithDescription(request)

        // Then — Verify agent completes (baseline behavior to preserve)
        assertNotNull(
            result,
            "Agent must complete and return a result for maxRetries=$maxRetries"
        )

        assertEquals(
            "job-preserve-complete-$maxRetries",
            result.jobId,
            "Result must have correct jobId"
        )

        // This test PASSES on unfixed code, documenting the baseline completion behavior.
        // After the fix, this test must CONTINUE TO PASS, ensuring no regression.
    }

    /**
     * **Preservation Test 2**: runStrategy is called exactly once
     *
     * **Observed Behavior on UNFIXED code**:
     * - runStrategy is called exactly once per generation request
     * - This is true regardless of maxRetries value (0, 1, 2, 3)
     * - The agent delegates to runStrategy once and returns the result
     *
     * **Must Preserve**: After fixing the retry logic exercise bug, runStrategy must
     * CONTINUE TO be called exactly once per generation request.
     *
     * **Validates: Requirement 6.2**
     */
    @ParameterizedTest(name = "Preservation - Given maxRetries={0} When agent runs Then runStrategy called exactly once")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Preservation - Given maxRetries N When agent runs Then runStrategy is called exactly once`(
        maxRetries: Int
    ) = runTest {
        // Given — Track runStrategy call count
        var strategyCallCount = 0

        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } answers {
            strategyCallCount++
            GenerationResult.success("job-preserve-once-$maxRetries", emptyList())
        }

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-preserve-once-$maxRetries", maxRetries)

        // When
        agent.generateFromSpecWithDescription(request)

        // Then — Verify runStrategy called exactly once (baseline behavior to preserve)
        assertEquals(
            1,
            strategyCallCount,
            "runStrategy must be called exactly once for maxRetries=$maxRetries, but was called $strategyCallCount times"
        )

        // This test PASSES on unfixed code, documenting the baseline delegation behavior.
        // After the fix, this test must CONTINUE TO PASS, ensuring the agent still
        // delegates to runStrategy exactly once per request.
    }

    /**
     * **Preservation Test 3**: Agent returns result without throwing
     *
     * **Observed Behavior on UNFIXED code**:
     * - Agent never throws exceptions during normal operation
     * - Agent always returns a GenerationResult (success or failure)
     * - This is true for all maxRetries values
     *
     * **Must Preserve**: After fixing the retry logic exercise bug, the agent must
     * CONTINUE TO return results without throwing exceptions.
     *
     * **Validates: Requirement 6.1**
     */
    @ParameterizedTest(name = "Preservation - Given maxRetries={0} When agent runs Then no exception is thrown")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Preservation - Given maxRetries N When agent runs Then result is returned without throwing`(
        maxRetries: Int
    ) = runTest {
        // Given
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-preserve-no-throw-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-preserve-no-throw-$maxRetries", maxRetries)

        // When / Then — Verify no exception is thrown (baseline behavior to preserve)
        val result = runCatching { agent.generateFromSpecWithDescription(request) }

        assertTrue(
            result.isSuccess,
            "Agent must not throw for maxRetries=$maxRetries, error: ${result.exceptionOrNull()}"
        )

        assertNotNull(
            result.getOrNull(),
            "Agent must return a non-null result for maxRetries=$maxRetries"
        )

        // This test PASSES on unfixed code, documenting the baseline error-free behavior.
        // After the fix, this test must CONTINUE TO PASS, ensuring no exceptions are introduced.
    }

    /**
     * **Preservation Test 4**: Agent completes with empty mock list
     *
     * **Observed Behavior on UNFIXED code**:
     * - When runStrategy returns empty list, agent completes successfully
     * - Result contains empty mocks list
     * - No errors or exceptions occur
     *
     * **Must Preserve**: After fixing the retry logic exercise bug, the agent must
     * CONTINUE TO handle empty mock lists correctly.
     *
     * **Validates: Requirement 6.1**
     */
    @ParameterizedTest(name = "Preservation - Given maxRetries={0} When runStrategy returns empty list Then agent completes")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Preservation - Given maxRetries N When runStrategy returns empty list Then agent completes successfully`(
        maxRetries: Int
    ) = runTest {
        // Given — runStrategy returns empty list
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-preserve-empty-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
        coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-preserve-empty-$maxRetries", maxRetries)

        // When
        val result = agent.generateFromSpecWithDescription(request)

        // Then — Verify agent handles empty list correctly (baseline behavior to preserve)
        assertNotNull(result, "Agent must return result even with empty mock list")

        assertTrue(
            result.mocks.isEmpty(),
            "Result must contain empty mocks list for maxRetries=$maxRetries"
        )

        // This test PASSES on unfixed code, documenting the baseline empty-list handling.
        // After the fix, this test must CONTINUE TO PASS, ensuring empty lists are handled correctly.
    }
}
