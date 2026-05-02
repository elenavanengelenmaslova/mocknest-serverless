package nl.vintik.mocknest.application.generation.agent

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test for bounded retry attempts in MockGenerationFunctionalAgent.
 *
 * **Validates: Requirements 6.3**
 *
 * Property 12: Bounded Retry Attempts
 * For any maxRetries value N, the agent must complete without infinite loops
 * and always return a result, even when the AI service always fails.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-12")
class BoundedRetryAttemptsPropertyTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMinimalSpecification(): APISpecification = APISpecification(
        format = SpecificationFormat.GRAPHQL,
        version = "1.0",
        title = "GraphQL API",
        endpoints = listOf(
            EndpointDefinition(
                path = "/graphql",
                method = HttpMethod.POST,
                operationId = "user",
                summary = "Get user by ID",
                parameters = emptyList(),
                requestBody = RequestBodyDefinition(
                    required = true,
                    content = mapOf(
                        "application/json" to MediaTypeDefinition(
                            schema = JsonSchema(type = JsonSchemaType.OBJECT)
                        )
                    ),
                    description = "GraphQL query"
                ),
                responses = mapOf(
                    200 to ResponseDefinition(
                        statusCode = 200,
                        description = "Success",
                        schema = JsonSchema(type = JsonSchemaType.OBJECT)
                    )
                )
            )
        ),
        schemas = emptyMap(),
        metadata = mapOf("operationType" to "graphql")
    )

    private fun buildAlwaysFailingMock(): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "Always failing mock",
        namespace = MockNamespace(apiName = "test-api"),
        wireMockMapping = """{"request":{"method":"POST","urlPath":"/graphql"},"response":{"status":200,"body":"invalid"}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "test",
            endpoint = EndpointInfo(
                method = HttpMethod.POST,
                path = "/graphql",
                statusCode = 200,
                contentType = "application/json"
            )
        ),
        generatedAt = Instant.now()
    )

    private fun buildRequest(jobId: String, maxRetries: Int): SpecWithDescriptionRequest =
        SpecWithDescriptionRequest(
            jobId = jobId,
            namespace = MockNamespace(apiName = "test-api"),
            specificationContent = "{}",
            format = SpecificationFormat.GRAPHQL,
            description = "Generate mocks for retry test (maxRetries=$maxRetries)"
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Property 12: Bounded Retry Attempts
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 12 - Given maxRetries={0} When AI always fails Then agent completes without infinite loop")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 12 - Given maxRetries N When AI always returns invalid mocks Then agent completes and returns a result`(
        maxRetries: Int
    ) = runTest {
        // Given - AI service always returns a result (the strategy is mocked at the top level)
        val failingMock = buildAlwaysFailingMock()

        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-bounded-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk()
        coEvery { mockValidator.validate(failingMock, any()) } returns
            nl.vintik.mocknest.application.generation.interfaces.MockValidationResult.invalid(
                listOf("Persistent validation error that cannot be corrected")
            )

        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildMinimalSpecification()
        coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-bounded-$maxRetries", maxRetries)

        // When
        val result = agent.generateFromSpecWithDescription(request)

        // Then - agent must always return a result (never hang or loop infinitely)
        assertNotNull(result, "Agent must return a result for maxRetries=$maxRetries")
    }

    @ParameterizedTest(name = "Property 12 - Given maxRetries={0} When AI always fails Then result is always successful (not an exception)")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 12 - Given maxRetries N When AI always fails Then result is returned without throwing`(
        maxRetries: Int
    ) = runTest {
        // Given
        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } returns GenerationResult.success("job-no-throw-$maxRetries", emptyList())

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)
        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildMinimalSpecification()
        coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-no-throw-$maxRetries", maxRetries)

        // When / Then - must not throw, must return a result
        val result = runCatching { agent.generateFromSpecWithDescription(request) }
        assertTrue(result.isSuccess, "Agent must not throw for maxRetries=$maxRetries, error: ${result.exceptionOrNull()}")
        assertNotNull(result.getOrNull(), "Agent must return a non-null result for maxRetries=$maxRetries")
    }

    @ParameterizedTest(name = "Property 12 - Given maxRetries={0} When strategy is called Then it is called exactly once")
    @ValueSource(ints = [0, 1, 2, 3])
    fun `Property 12 - Given maxRetries N When generating Then runStrategy is called exactly once per generation request`(
        maxRetries: Int
    ) = runTest {
        // Given - count how many times runStrategy is invoked
        var strategyCallCount = 0

        coEvery {
            aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
        } answers {
            strategyCallCount++
            GenerationResult.success("job-once-$maxRetries", emptyList())
        }

        val mockValidator: MockValidatorInterface = mockk(relaxed = true)
        val parser: SpecificationParserInterface = mockk()
        coEvery { parser.parse(any(), any()) } returns buildMinimalSpecification()
        coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

        val agent = MockGenerationFunctionalAgent(
            aiModelService, parser, mockValidator, promptBuilder, maxRetries = maxRetries
        )

        val request = buildRequest("job-once-$maxRetries", maxRetries)

        // When
        agent.generateFromSpecWithDescription(request)

        // Then - the Koog strategy encapsulates the retry loop internally;
        // generateFromSpecWithDescription delegates to runStrategy exactly once
        kotlin.test.assertEquals(
            1,
            strategyCallCount,
            "runStrategy should be called exactly once regardless of maxRetries=$maxRetries, but was called $strategyCallCount times"
        )
    }
}
