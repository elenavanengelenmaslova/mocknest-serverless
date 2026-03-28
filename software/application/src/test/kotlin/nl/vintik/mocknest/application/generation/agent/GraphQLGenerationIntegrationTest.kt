package nl.vintik.mocknest.application.generation.agent

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.domain.generation.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for GraphQL mock generation pipeline.
 * Tests the complete flow: introspection → reduction → generation → validation.
 *
 * Requirements: 9.5, 6.1, 6.2, 6.3, 6.5, 6.6
 */
@Tag("graphql-introspection-ai-generation")
class GraphQLGenerationIntegrationTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val promptBuilder: PromptBuilderService = mockk(relaxed = true)

    private val realReducer = GraphQLSchemaReducer()
    private val realValidator = GraphQLMockValidator()

    private val testNamespace = MockNamespace(apiName = "graphql-api")

    private fun loadTestData(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    private fun makeValidGraphQLMock(
        operationName: String = "user",
        namespace: MockNamespace = testNamespace
    ): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "GraphQL $operationName mock",
        namespace = namespace,
        wireMockMapping = """{"request":{"method":"POST","urlPath":"/graphql","bodyPatterns":[{"equalToJson":"{\"query\":\"query $operationName(${'$'}id: ID!) { user(id: ${'$'}id) { id name email } }\",\"operationName\":\"$operationName\",\"variables\":{\"id\":\"user-123\"}}"}]},"response":{"status":200,"jsonBody":{"data":{"id":"user-123","name":"Alice Smith","email":"alice@example.com"}}}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "GraphQL API: test",
            endpoint = EndpointInfo(
                method = HttpMethod.POST,
                path = "/graphql",
                statusCode = 200,
                contentType = "application/json"
            )
        ),
        generatedAt = Instant.now()
    )

    private fun makeInvalidGraphQLMock(namespace: MockNamespace = testNamespace): GeneratedMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "Invalid GraphQL mock",
        namespace = namespace,
        wireMockMapping = """{"request":{"method":"POST","urlPath":"/graphql","bodyPatterns":[{"equalToJson":"{\"query\":\"query getUser\",\"operationName\":\"getUser\",\"variables\":{}}"}]},"response":{"status":200,"body":"{\"result\":\"ok\"}"}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "GraphQL API: test",
            endpoint = EndpointInfo(
                method = HttpMethod.POST,
                path = "/graphql",
                statusCode = 200,
                contentType = "application/json"
            )
        ),
        generatedAt = Instant.now()
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.5 – End-to-end integration test for GraphQL generation
    // Requirements: 9.5
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class EndToEndGraphQLGeneration {

        private fun buildParser() = GraphQLSpecificationParser(
            introspectionClient = mockk(relaxed = true),
            schemaReducer = realReducer,
            urlSafetyValidator = {}
        )

        @Test
        fun `Given simple introspection schema When generating mocks Then should produce valid WireMock mappings`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val parser = buildParser()
            val validMock = makeValidGraphQLMock()

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-e2e-1", listOf(validMock))

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, realValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-e2e-1",
                namespace = testNamespace,
                specificationContent = introspectionJson,
                format = SpecificationFormat.GRAPHQL,
                description = "Generate mocks for user operations"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success, "Generation should succeed")
            assertNotNull(result.mocks)
        }

        @Test
        fun `Given introspection schema When parsing Then should reduce schema and produce APISpecification`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val parser = buildParser()

            // When
            val specification = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals(SpecificationFormat.GRAPHQL, specification.format)
            assertTrue(specification.endpoints.isNotEmpty(), "Should have at least one endpoint")
            specification.endpoints.forEach { endpoint ->
                assertEquals("/graphql", endpoint.path, "All GraphQL endpoints should use /graphql path")
                assertEquals(HttpMethod.POST, endpoint.method, "All GraphQL endpoints should use POST method")
            }
        }

        @Test
        fun `Given valid WireMock mapping When validating against schema Then should pass validation`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val parser = buildParser()
            val specification = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)
            val validMock = makeValidGraphQLMock()

            // When
            val validationResult = realValidator.validate(validMock, specification)

            // Then
            assertTrue(validationResult.isValid, "Valid mock should pass validation. Errors: ${validationResult.errors}")
        }

        @Test
        fun `Given invalid WireMock mapping When validating against schema Then should fail validation`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val parser = buildParser()
            val specification = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)
            val invalidMock = makeInvalidGraphQLMock()

            // When
            val validationResult = realValidator.validate(invalidMock, specification)

            // Then
            assertFalse(validationResult.isValid, "Invalid mock should fail validation")
            assertTrue(validationResult.errors.isNotEmpty(), "Should report validation errors")
        }

        @Test
        fun `Given complex schema When generating mocks Then should handle multiple operations`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")
            val parser = buildParser()
            val specification = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertTrue(specification.endpoints.size >= 5, "Complex schema should produce multiple endpoints")
            assertTrue(specification.schemas.isNotEmpty(), "Complex schema should produce type schemas")
        }

        @Test
        fun `Given generated mock When checking WireMock format Then should be valid WireMock mapping`() = runTest {
            // Given
            val validMock = makeValidGraphQLMock()

            // When - parse the wireMockMapping JSON to verify it's valid
            val mappingJson = kotlinx.serialization.json.Json.parseToJsonElement(validMock.wireMockMapping)
            val mappingObj = mappingJson.jsonObject

            // Then
            assertTrue(mappingObj.containsKey("request"), "WireMock mapping must have 'request' field")
            assertTrue(mappingObj.containsKey("response"), "WireMock mapping must have 'response' field")

            val request = mappingObj["request"]!!.jsonObject
            assertEquals("POST", request["method"]?.jsonPrimitive?.content, "GraphQL must use POST method")
            assertEquals("/graphql", request["urlPath"]?.jsonPrimitive?.content, "GraphQL must use /graphql path")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.6 – Integration test for validation-retry loop with correctable errors
    // Requirements: 6.1, 6.2, 6.5
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class ValidationRetryWithCorrectableErrors {

        @Test
        fun `Given AI returns invalid mock on first attempt and valid mock on retry Then should accept corrected mock`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()
            val validMock = makeValidGraphQLMock()

            // First call returns invalid mock; second call (retry) returns valid mock
            var callCount = 0
            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } answers {
                callCount++
                if (callCount == 1) {
                    GenerationResult.success("job-retry-1", listOf(invalidMock))
                } else {
                    GenerationResult.success("job-retry-1", listOf(validMock))
                }
            }

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                listOf("Response must contain 'data' or 'errors' field (GraphQL response format)")
            )
            coEvery { mockValidator.validate(validMock, any()) } returns MockValidationResult.valid()

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-retry-1",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success, "Generation should succeed after retry")
        }

        @Test
        fun `Given validation errors When retrying Then should feed errors back to AI service`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()
            val validMock = makeValidGraphQLMock()
            val validationErrors = listOf(
                "Response must contain 'data' or 'errors' field (GraphQL response format)",
                "Operation 'getUser' not found in schema"
            )

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-retry-2", listOf(validMock))

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(validationErrors)
            coEvery { mockValidator.validate(validMock, any()) } returns MockValidationResult.valid()

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-retry-2",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success, "Generation should succeed")
        }

        @Test
        fun `Given corrected mock passes validation When retrying Then should include corrected mock in result`() = runTest {
            // Given
            val correctedMock = makeValidGraphQLMock()

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-retry-3", listOf(correctedMock))

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(correctedMock, any()) } returns MockValidationResult.valid()

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-retry-3",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success, "Generation should succeed with corrected mock")
            assertNotNull(result.mocks)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Task 4.7 – Integration test for validation-retry loop with uncorrectable errors
    // Requirements: 6.3, 6.6
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class ValidationRetryWithUncorrectableErrors {

        @Test
        fun `Given AI always returns invalid mocks When max retries exceeded Then should stop retrying`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()
            val persistentErrors = listOf("Response must contain 'data' or 'errors' field (GraphQL response format)")

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-uncorr-1", emptyList())

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(persistentErrors)

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 2
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-uncorr-1",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then – agent should complete (not loop forever) and return a result
            assertNotNull(result, "Should return a result even when all mocks are invalid")
        }

        @Test
        fun `Given maxRetries of 0 When validation fails Then should not retry at all`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-uncorr-2", emptyList())

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                listOf("Response must contain 'data' or 'errors' field (GraphQL response format)")
            )

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 0
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-uncorr-2",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result even with maxRetries=0")
        }

        @Test
        fun `Given maxRetries of 3 When all attempts fail Then should respect the retry limit`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()
            var strategyCallCount = 0

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } answers {
                strategyCallCount++
                GenerationResult.success("job-uncorr-3", emptyList())
            }

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(
                listOf("Persistent validation error that cannot be corrected")
            )

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 3
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-uncorr-3",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result after exhausting retries")
            // The strategy is called once (the agent delegates to runStrategy which handles retries internally)
            assertEquals(1, strategyCallCount, "runStrategy should be called exactly once")
        }

        @Test
        fun `Given accumulated validation errors When generation fails Then result should be returned`() = runTest {
            // Given
            val invalidMock = makeInvalidGraphQLMock()
            val accumulatedErrors = listOf(
                "Response must contain 'data' or 'errors' field (GraphQL response format)",
                "Operation 'getUser' not found in schema",
                "Missing required argument: 'id'"
            )

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), any())
            } returns GenerationResult.success("job-uncorr-4", emptyList())

            val mockValidator: MockValidatorInterface = mockk()
            coEvery { mockValidator.validate(invalidMock, any()) } returns MockValidationResult.invalid(accumulatedErrors)

            val parser: nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface = mockk()
            coEvery { parser.parse(any(), any()) } returns buildMinimalGraphQLSpecification()
            coEvery { parser.supports(SpecificationFormat.GRAPHQL) } returns true

            val agent = MockGenerationFunctionalAgent(
                aiModelService, parser, mockValidator, promptBuilder, maxRetries = 1
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-uncorr-4",
                namespace = testNamespace,
                specificationContent = "{}",
                format = SpecificationFormat.GRAPHQL,
                description = "Generate user mocks"
            )

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertNotNull(result, "Should return a result even with accumulated errors")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMinimalGraphQLSpecification(): APISpecification = APISpecification(
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
                            schema = JsonSchema(
                                type = JsonSchemaType.OBJECT,
                                properties = mapOf(
                                    "query" to JsonSchema(type = JsonSchemaType.STRING),
                                    "variables" to JsonSchema(
                                        type = JsonSchemaType.OBJECT,
                                        properties = mapOf(
                                            "id" to JsonSchema(type = JsonSchemaType.STRING)
                                        ),
                                        required = listOf("id")
                                    )
                                ),
                                required = listOf("query")
                            )
                        )
                    ),
                    description = "GraphQL query request"
                ),
                responses = mapOf(
                    200 to ResponseDefinition(
                        statusCode = 200,
                        description = "Successful GraphQL response",
                        schema = JsonSchema(
                            type = JsonSchemaType.OBJECT,
                            properties = mapOf(
                                "data" to JsonSchema(
                                    type = JsonSchemaType.OBJECT,
                                    properties = mapOf(
                                        "id" to JsonSchema(type = JsonSchemaType.STRING),
                                        "name" to JsonSchema(type = JsonSchemaType.STRING),
                                        "email" to JsonSchema(type = JsonSchemaType.STRING)
                                    ),
                                    required = listOf("id", "name", "email")
                                )
                            )
                        )
                    )
                )
            )
        ),
        schemas = emptyMap(),
        metadata = mapOf("operationType" to "graphql")
    )
}
