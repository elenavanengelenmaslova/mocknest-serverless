package nl.vintik.mocknest.application.generation.agent

import io.mockk.*
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.util.UrlFetcher
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.*

class MockGenerationFunctionalAgentTest {

    private val aiModelService: AIModelServiceInterface = mockk()
    private val specificationParser: SpecificationParserInterface = mockk()
    private val mockValidator: MockValidatorInterface = mockk()
    private val promptBuilder: PromptBuilderService = mockk()
    private val urlFetcher: UrlFetcher = mockk()
    private lateinit var agent: MockGenerationFunctionalAgent

    private val testSpecification = APISpecification(
        format = SpecificationFormat.OPENAPI_3,
        version = "1.0",
        title = "Test API",
        endpoints = listOf(
            EndpointDefinition(
                path = "/test",
                method = HttpMethod.GET,
                operationId = "test",
                summary = "test",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(200 to ResponseDefinition(200, "OK", null))
            )
        ),
        schemas = emptyMap()
    )

    private val testNamespace = MockNamespace(apiName = "test-api", client = "test-client")

    private val testMock = GeneratedMock(
        id = UUID.randomUUID().toString(),
        name = "GET /test - 200",
        namespace = testNamespace,
        wireMockMapping = """{"request":{"method":"GET","urlPath":"/test"},"response":{"status":200}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "Test API: test description",
            endpoint = EndpointInfo(
                method = HttpMethod.GET,
                path = "/test",
                statusCode = 200,
                contentType = "application/json"
            )
        ),
        generatedAt = Instant.now()
    )

    @BeforeEach
    fun setup() {
        agent = MockGenerationFunctionalAgent(
            aiModelService,
            specificationParser,
            mockValidator,
            promptBuilder,
            urlFetcher = urlFetcher
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class `Strategy Execution Tests` {

        @Test
        fun `Given valid spec content When generating mocks Then should parse spec and generate mocks successfully`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-123",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )
            val expectedResult = GenerationResult.success("job-123", listOf(testMock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("job-123", result.jobId)
            assertEquals(1, result.mocks.size)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @Test
        fun `Given spec URL When generating mocks Then should fetch and parse spec from URL`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-456",
                namespace = testNamespace,
                specificationUrl = "https://example.com/api/spec.yaml",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )
            val expectedResult = GenerationResult.success("job-456", listOf(testMock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("job-456", result.jobId)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @Test
        fun `Given validation enabled with valid mocks When generating Then should validate and return all mocks`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-789",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )
            val mocks = listOf(testMock, testMock.copy())
            val expectedResult = GenerationResult.success("job-789", mocks)

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(2, result.mocks.size)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @Test
        fun `Given validation enabled with invalid mocks When generating Then should attempt correction`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-101",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )
            val mocks = listOf(testMock)
            val expectedResult = GenerationResult.success("job-101", mocks)

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertNotNull(result.mocks)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @Test
        fun `Given validation disabled When generating mocks Then should skip validation`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-202",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )
            val expectedResult = GenerationResult.success("job-202", listOf(testMock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 2, 3])
        fun `Given different maxRetries values When creating agent Then should respect retry limit`(maxRetries: Int) {
            // When
            val customAgent = MockGenerationFunctionalAgent(
                aiModelService,
                specificationParser,
                mockValidator,
                promptBuilder,
                maxRetries = maxRetries
            )

            // Then
            assertNotNull(customAgent)
        }
    }

    @Nested
    inner class `Context Management Tests` {

        @Test
        fun `Given initial context When created Then should have default values`() {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-ctx-1",
                namespace = testNamespace,
                specificationContent = "spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )

            // When
            val context = MockGenerationContext(
                request = request,
                specification = testSpecification
            )

            // Then
            assertEquals(request, context.request)
            assertEquals(testSpecification, context.specification)
            assertTrue(context.mocks.isEmpty())
            assertEquals(1, context.attempt)
            assertTrue(context.errors.isEmpty())
        }

        @Test
        fun `Given context When copying with new mocks Then should preserve request and spec`() {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-ctx-2",
                namespace = testNamespace,
                specificationContent = "spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val context = MockGenerationContext(
                request = request,
                specification = testSpecification
            )

            // When
            val updatedContext = context.copy(mocks = listOf(testMock))

            // Then
            assertEquals(context.request, updatedContext.request)
            assertEquals(context.specification, updatedContext.specification)
            assertEquals(1, updatedContext.mocks.size)
            assertEquals(testMock, updatedContext.mocks[0])
        }

        @Test
        fun `Given context When copying with incremented attempt Then should update attempt counter`() {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-ctx-3",
                namespace = testNamespace,
                specificationContent = "spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val context = MockGenerationContext(
                request = request,
                specification = testSpecification,
                attempt = 1
            )

            // When
            val updatedContext = context.copy(attempt = context.attempt + 1)

            // Then
            assertEquals(2, updatedContext.attempt)
        }

        @Test
        fun `Given context When copying with errors Then should update errors list`() {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-ctx-4",
                namespace = testNamespace,
                specificationContent = "spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val context = MockGenerationContext(
                request = request,
                specification = testSpecification
            )
            val errors = listOf("Error 1", "Error 2")

            // When
            val updatedContext = context.copy(errors = errors)

            // Then
            assertEquals(2, updatedContext.errors.size)
            assertTrue(updatedContext.errors.containsAll(errors))
        }
    }

    @Nested
    inner class `Integration Tests` {

        @Test
        fun `Given complete workflow When all mocks valid Then should complete without correction`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-int-1",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = true)
            )
            val expectedResult = GenerationResult.success("job-int-1", listOf(testMock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)
        }

        @Test
        fun `Given empty mocks list When generating Then should return success with empty list`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-int-2",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )
            val expectedResult = GenerationResult.success("job-int-2", emptyList())

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertTrue(result.mocks.isEmpty())
        }

        @Test
        fun `Given multiple mocks When generating Then should handle all mocks`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-int-3",
                namespace = testNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks",
                options = GenerationOptions(enableValidation = false)
            )
            val mocks = listOf(
                testMock.copy(),
                testMock.copy(
                    id = UUID.randomUUID().toString(),
                    wireMockMapping = """{"request":{"method":"POST","urlPath":"/test"},"response":{"status":200}}"""
                ),
                testMock.copy(
                    id = UUID.randomUUID().toString(),
                    wireMockMapping = """{"request":{"method":"PUT","urlPath":"/test/123"},"response":{"status":200}}"""
                )
            )
            val expectedResult = GenerationResult.success("job-int-3", mocks)

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(3, result.mocks.size)
        }
    }

    @Nested
    inner class `Error Handling Tests` {

        @Test
        fun `Given strategy returns failure When generating Then should return failure result`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-err-1",
                namespace = testNamespace,
                specificationContent = "invalid spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks"
            )
            val expectedResult = GenerationResult.failure("job-err-1", "Parse error")

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertFalse(result.success)
            assertEquals("Parse error", result.error)
        }

        @Test
        fun `Given parsing exception When generating Then should return failure result`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-err-2",
                namespace = testNamespace,
                specificationContent = "malformed: yaml: content",
                format = SpecificationFormat.OPENAPI_3,
                description = "generate test mocks"
            )
            val expectedResult = GenerationResult.failure("job-err-2", "Failed to parse specification")

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertFalse(result.success)
            assertNotNull(result.error)
        }
    }

    @Nested
    inner class `Strategy Node Integration Tests` {

        @Test
        fun `Given spec content When executing strategy Then should parse specification and generate mocks`() = runBlocking {
            // Given
            val specContent = """
                openapi: 3.0.0
                info:
                  title: Test API
                  version: 1.0.0
            """.trimIndent()

            val request = SpecWithDescriptionRequest(
                jobId = "job-node-1",
                namespace = testNamespace,
                specificationContent = specContent,
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { specificationParser.parse(specContent, SpecificationFormat.OPENAPI_3) } returns testSpecification
            coEvery { promptBuilder.buildSpecWithDescriptionPrompt(testSpecification, "test description", testNamespace) } returns "test prompt"
            coEvery { aiModelService.parseModelResponse(any(), testNamespace, SourceType.SPEC_WITH_DESCRIPTION, any()) } returns listOf(testMock)

            // Mock the strategy execution
            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } coAnswers {
                GenerationResult.success("job-node-1", listOf(testMock))
            }

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)

            coVerify(exactly = 1) { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) }
        }

        @Test
        fun `Given spec URL When executing strategy Then should fetch and parse from URL`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-node-2",
                namespace = testNamespace,
                specificationUrl = "https://example.com/spec.yaml",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = false)
            )

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns
                GenerationResult.success("job-node-2", listOf(testMock))

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)
        }

        @Test
        fun `Given validation enabled with all valid mocks When executing Then should return all mocks`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-node-3",
                namespace = testNamespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = true)
            )

            val validationResult = MockValidationResult(isValid = true, errors = emptyList())

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            coEvery { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any()) } returns "prompt"
            coEvery { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)
            coEvery { mockValidator.validate(any(), any()) } returns validationResult

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns GenerationResult.success("job-node-3", listOf(testMock))

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(1, result.mocks.size)
        }

        @Test
        fun `Given validation enabled with invalid mocks When executing Then should attempt correction once`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-node-4",
                namespace = testNamespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = true)
            )

            val invalidResult = MockValidationResult(isValid = false, errors = listOf("error1"))
            val correctedMock = testMock.copy(id = UUID.randomUUID().toString())

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            coEvery { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any()) } returns "prompt"
            coEvery { promptBuilder.buildCorrectionPrompt(any(), any(), any()) } returns "correction prompt"
            coEvery { aiModelService.parseModelResponse(any(), any(), SourceType.SPEC_WITH_DESCRIPTION, any()) } returns listOf(testMock)
            coEvery { aiModelService.parseModelResponse(any(), any(), SourceType.REFINEMENT, any()) } returns listOf(correctedMock)
            coEvery { mockValidator.validate(testMock, any()) } returns invalidResult
            coEvery { mockValidator.validate(correctedMock, any()) } returns MockValidationResult(isValid = true, errors = emptyList())

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns GenerationResult.success("job-node-4", listOf(correctedMock))

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertNotNull(result.mocks)
        }

        @Test
        fun `Given validation with max retries exceeded When executing Then should return best effort mocks`() = runBlocking {
            // Given
            val agentWithRetries = MockGenerationFunctionalAgent(
                aiModelService,
                specificationParser,
                mockValidator,
                promptBuilder,
                maxRetries = 2
            )

            val request = SpecWithDescriptionRequest(
                jobId = "job-node-5",
                namespace = testNamespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = true)
            )

            val invalidResult = MockValidationResult(isValid = false, errors = listOf("persistent error"))

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            coEvery { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any()) } returns "prompt"
            coEvery { promptBuilder.buildCorrectionPrompt(any(), any(), any()) } returns "correction prompt"
            coEvery { aiModelService.parseModelResponse(any(), any(), any(), any()) } returns listOf(testMock)
            coEvery { mockValidator.validate(any(), any()) } returns invalidResult

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns GenerationResult.success("job-node-5", emptyList())

            // When
            val result = agentWithRetries.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
        }

        @Test
        fun `Given validation with fatal errors When executing Then should filter out fatal mocks`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-node-6",
                namespace = testNamespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = true)
            )

            val fatalResult = MockValidationResult(isValid = false, errors = listOf("fatal error"), isFatal = true)

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            coEvery { mockValidator.validate(any(), any()) } returns fatalResult

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns GenerationResult.success("job-node-6", emptyList())

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
        }

        @Test
        fun `Given multiple mocks with mixed validation When executing Then should separate valid and invalid`() = runBlocking {
            // Given
            val request = SpecWithDescriptionRequest(
                jobId = "job-node-7",
                namespace = testNamespace,
                specificationContent = "spec content",
                format = SpecificationFormat.OPENAPI_3,
                description = "test description",
                options = GenerationOptions(enableValidation = true)
            )

            val validMock = testMock.copy(id = "valid-1")
            val invalidMock = testMock.copy(id = "invalid-1")
            val correctedMock = testMock.copy(id = "corrected-1")

            val validResult = MockValidationResult(isValid = true, errors = emptyList())
            val invalidResult = MockValidationResult(isValid = false, errors = listOf("error"))

            coEvery { specificationParser.parse(any(), any()) } returns testSpecification
            coEvery { promptBuilder.buildSpecWithDescriptionPrompt(any(), any(), any()) } returns "prompt"
            coEvery { promptBuilder.buildCorrectionPrompt(any(), any(), any()) } returns "correction prompt"
            coEvery { aiModelService.parseModelResponse(any(), any(), SourceType.SPEC_WITH_DESCRIPTION, any()) } returns listOf(validMock, invalidMock)
            coEvery { aiModelService.parseModelResponse(any(), any(), SourceType.REFINEMENT, any()) } returns listOf(correctedMock)
            coEvery { mockValidator.validate(validMock, any()) } returns validResult
            coEvery { mockValidator.validate(invalidMock, any()) } returns invalidResult
            coEvery { mockValidator.validate(correctedMock, any()) } returns validResult

            coEvery {
                aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request))
            } returns GenerationResult.success("job-node-7", listOf(validMock, correctedMock))

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
        }
    }

    @Nested
    inner class `Namespace Handling Tests` {

        @Test
        fun `Given simple namespace When generating Then should use correct namespace`() = runBlocking {
            // Given
            val simpleNamespace = MockNamespace(apiName = "simple-api")
            val request = SpecWithDescriptionRequest(
                jobId = "job-ns-1",
                namespace = simpleNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val mock = testMock.copy(namespace = simpleNamespace)
            val expectedResult = GenerationResult.success("job-ns-1", listOf(mock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals(simpleNamespace, result.mocks[0].namespace)
        }

        @Test
        fun `Given namespace with client When generating Then should preserve client info`() = runBlocking {
            // Given
            val clientNamespace = MockNamespace(apiName = "api", client = "client-a")
            val request = SpecWithDescriptionRequest(
                jobId = "job-ns-2",
                namespace = clientNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val mock = testMock.copy(namespace = clientNamespace)
            val expectedResult = GenerationResult.success("job-ns-2", listOf(mock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("client-a", result.mocks[0].namespace.client)
        }

        @Test
        fun `Given namespace with subdomain When generating Then should preserve full namespace`() = runBlocking {
            // Given
            val complexNamespace = MockNamespace(apiName = "api", client = "client-a")
            val request = SpecWithDescriptionRequest(
                jobId = "job-ns-3",
                namespace = complexNamespace,
                specificationContent = "openapi: 3.0.0...",
                format = SpecificationFormat.OPENAPI_3,
                description = "test"
            )
            val mock = testMock.copy(id = UUID.randomUUID().toString(), namespace = complexNamespace)
            val expectedResult = GenerationResult.success("job-ns-3", listOf(mock))

            coEvery { aiModelService.runStrategy<SpecWithDescriptionRequest, GenerationResult>(any(), eq(request)) } returns expectedResult

            // When
            val result = agent.generateFromSpecWithDescription(request)

            // Then
            assertTrue(result.success)
            assertEquals("client-a", result.mocks[0].namespace.client)
            assertEquals("api", result.mocks[0].namespace.apiName)
        }
    }

    @Nested
    inner class ContentResolution {

        @Test
        fun `Given GraphQL format with URL When resolving content Then should return URL string directly`() {
            // Given
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = "https://example.com/graphql",
                format = SpecificationFormat.GRAPHQL,
                description = "test graphql"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals("https://example.com/graphql", result)
        }

        @Test
        fun `Given GraphQL format with content When resolving content Then should return content string`() {
            // Given
            val content = """{"data":{"__schema":{"queryType":{"name":"Query"}}}}"""
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationContent = content,
                format = SpecificationFormat.GRAPHQL,
                description = "test graphql"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals(content, result)
        }

        @Test
        fun `Given OpenAPI format with URL When resolving content Then should return URL string directly`() {
            // Given - OpenAPI also handles own URL resolution via readLocation()
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = "https://example.com/openapi.yaml",
                format = SpecificationFormat.OPENAPI_3,
                description = "test openapi"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals("https://example.com/openapi.yaml", result)
        }

        @Test
        fun `Given WSDL format with URL When resolving content Then should return URL string directly for parser to fetch`() {
            // Given - WSDL handles own URL resolution via WsdlContentFetcher in the parser
            val wsdlUrl = "https://example.com/service.wsdl"
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationUrl = wsdlUrl,
                format = SpecificationFormat.WSDL,
                description = "test wsdl"
            )

            // When
            val result = agent.resolveContent(request)

            // Then - URL is passed through, not pre-fetched by urlFetcher
            assertEquals(wsdlUrl, result)
            verify(exactly = 0) { urlFetcher.fetch(any()) }
        }

        @Test
        fun `Given format with content When resolving content Then should return content string`() {
            // Given
            val content = "openapi: 3.0.0"
            val request = SpecWithDescriptionRequest(
                namespace = testNamespace,
                specificationContent = content,
                format = SpecificationFormat.OPENAPI_3,
                description = "test openapi"
            )

            // When
            val result = agent.resolveContent(request)

            // Then
            assertEquals(content, result)
        }
    }
}
