package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationLambdaHandlerTest : KoinTest {

    private val mockHandleAIGenerationRequest: HandleAIGenerationRequest = mockk(relaxed = true)
    private val mockGetAIHealth: GetAIHealth = mockk(relaxed = true)
    private val mockPrimingHook: GenerationPrimingHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: GenerationLambdaHandler

    @BeforeAll
    fun setUp() {
        KoinBootstrap.init(listOf(module {
            single<HandleAIGenerationRequest> { mockHandleAIGenerationRequest }
            single<GetAIHealth> { mockGetAIHealth }
            single { mockPrimingHook }
        }))
        handler = GenerationLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockHandleAIGenerationRequest, mockGetAIHealth)
    }

    @Nested
    inner class AIPathRouting {

        @Test
        fun `Given AI health request When routing Then should call GetAIHealth`() {
            // Given
            val event = createEvent("/ai/generation/health", "GET")

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status": "healthy", "version": "test-version"}"""
            )

            every { mockGetAIHealth.invoke() } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) { mockGetAIHealth.invoke() }
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("\"status\": \"healthy\""))
            assert(response.body.contains("\"version\":"))
        }

        @Test
        fun `Given AI generation request When routing Then should call HandleAIGenerationRequest with correct path`() {
            // Given
            val event = createEvent("/ai/generation/generate", "POST", body = """{"spec": "openapi spec"}""")

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status": "generated"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/generate",
                    match { request ->
                        request.method == HttpMethod.POST &&
                        request.path == "/generate" &&
                        request.body == """{"spec": "openapi spec"}"""
                    }
                )
            }

            assertEquals(200, response.statusCode)
            assertEquals("""{"status": "generated"}""", response.body)
        }

        @Test
        fun `Given AI status request When routing Then should call HandleAIGenerationRequest`() {
            // Given
            val event = createEvent("/ai/generation/status", "GET")

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status": "ready"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/status",
                    match { request ->
                        request.method == HttpMethod.GET &&
                        request.path == "/status"
                    }
                )
            }

            assertEquals(200, response.statusCode)
            assertEquals("""{"status": "ready"}""", response.body)
        }

        @Test
        fun `Given AI bulk generate request When routing Then should call HandleAIGenerationRequest with body`() {
            // Given
            val requestBody = """{"specs": ["spec1", "spec2"], "descriptions": ["desc1", "desc2"]}"""
            val event = createEvent("/ai/generation/bulk-generate", "POST", body = requestBody)

            val expectedResponse = HttpResponse(
                HttpStatusCode(202),
                mapOf("Content-Type" to listOf("application/json")),
                """{"jobId": "job-123"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/bulk-generate",
                    match { request ->
                        request.method == HttpMethod.POST &&
                        request.path == "/bulk-generate" &&
                        request.body == requestBody
                    }
                )
            }

            assertEquals(202, response.statusCode)
        }

        @Test
        fun `Given AI request with query parameters When routing Then should pass query parameters`() {
            // Given
            val event = createEvent(
                "/ai/generation/generate", "POST",
                body = """{"spec": "openapi spec"}""",
                queryParams = mapOf("format" to "json", "validate" to "true")
            )

            val expectedResponse = HttpResponse(HttpStatusCode.OK)

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/generate",
                    match { request ->
                        request.queryParameters["format"] == "json" &&
                        request.queryParameters["validate"] == "true"
                    }
                )
            }

            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given AI analyze traffic request When routing Then should call HandleAIGenerationRequest`() {
            // Given
            val requestBody = """{"timeframe": "24h", "includeUnmatched": true}"""
            val event = createEvent("/ai/generation/analyze-traffic", "POST", body = requestBody)

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"analysisId": "analysis-123"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/analyze-traffic",
                    match { request ->
                        request.method == HttpMethod.POST &&
                        request.path == "/analyze-traffic" &&
                        request.body == requestBody
                    }
                )
            }

            assertEquals(200, response.statusCode)
        }
    }

    @Nested
    inner class RuntimePathIsolation {

        @Test
        fun `Given admin path When routing Then should return 404 not found`() {
            // Given
            val event = createEvent("/__admin/mappings", "GET")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(404, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("not found"))
        }

        @Test
        fun `Given mock endpoint path When routing Then should return 404 not found`() {
            // Given
            val event = createEvent("/mocknest/api/users", "GET")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(404, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("/mocknest/api/users"))
            assert(response.body.contains("not found"))
        }

        @Test
        fun `Given runtime path When routing Then should not invoke runtime use cases`() {
            // Given
            val event = createEvent("/__admin/requests", "GET")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class UnknownPathHandling {

        @Test
        fun `Given unknown path When routing Then should return 404 not found`() {
            // Given
            val event = createEvent("/unknown/path", "GET")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(404, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("/unknown/path"))
            assert(response.body.contains("not found"))
        }

        @Test
        fun `Given root path When routing Then should return 404 not found`() {
            // Given
            val event = createEvent("/", "GET")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ResponseMapping {

        @Test
        fun `Given response with headers When mapping Then should convert to API Gateway response`() {
            // Given
            val event = createEvent("/ai/generation/generate", "POST", body = """{"spec": "openapi spec"}""")

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf(
                    "Content-Type" to listOf("application/json"),
                    "X-Generation-Id" to listOf("gen-123")
                ),
                """{"result": "success"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("gen-123", response.headers["X-Generation-Id"])
            assertEquals("""{"result": "success"}""", response.body)
        }

        @Test
        fun `Given response without body When mapping Then should return empty body`() {
            // Given
            val event = createEvent("/ai/generation/delete", "DELETE")

            val expectedResponse = HttpResponse(HttpStatusCode(204))

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(204, response.statusCode)
            assertEquals("", response.body)
        }

        @Test
        fun `Given error response When mapping Then should preserve error status code`() {
            // Given
            val event = createEvent("/ai/generation/invalid", "POST", body = """{"invalid": "data"}""")

            val expectedResponse = HttpResponse(
                HttpStatusCode.BAD_REQUEST,
                mapOf("Content-Type" to listOf("application/json")),
                """{"error": "Invalid specification format"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(400, response.statusCode)
            assertEquals("""{"error": "Invalid specification format"}""", response.body)
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `Given request with null query parameters When routing Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(null)

            val expectedResponse = HttpResponse(HttpStatusCode.OK)

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/generate",
                    match { request ->
                        request.queryParameters.isEmpty()
                    }
                )
            }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given request with null body When routing Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/status")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withBody(null)
                .withQueryStringParameters(emptyMap())

            val expectedResponse = HttpResponse(HttpStatusCode.OK)

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/status",
                    match { request ->
                        request.body == null
                    }
                )
            }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given response with null headers When mapping Then should handle gracefully`() {
            // Given
            val event = createEvent("/ai/generation/generate", "POST", body = """{"spec": "openapi spec"}""")

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                null,
                """{"result": "success"}"""
            )

            every {
                mockHandleAIGenerationRequest.invoke(any(), any())
            } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("""{"result": "success"}""", response.body)
        }
    }

    private fun createEvent(
        path: String,
        httpMethod: String,
        body: String? = null,
        queryParams: Map<String, String> = emptyMap()
    ): APIGatewayProxyRequestEvent =
        APIGatewayProxyRequestEvent()
            .withPath(path)
            .withHttpMethod(httpMethod)
            .withHeaders(mapOf("Accept" to "application/json"))
            .withQueryStringParameters(queryParams)
            .apply { if (body != null) withBody(body) }
}
