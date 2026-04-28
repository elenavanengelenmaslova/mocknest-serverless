package nl.vintik.mocknest.infra.aws.runtime

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import org.crac.Core
import org.crac.Resource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

/**
 * Unit tests for [RuntimeLambdaHandler] routing logic.
 *
 * Tests health check path, admin path, client path, and 404 path.
 * Verifies correct status codes, response body construction, and
 * multi-value header conversion (only first value used in `withHeaders()`).
 *
 * **Validates: Requirements 7.1, 10.1**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeLambdaHandlerTest : KoinTest {

    private val mockHandleClientRequest: HandleClientRequest = mockk(relaxed = true)
    private val mockHandleAdminRequest: HandleAdminRequest = mockk(relaxed = true)
    private val mockGetRuntimeHealth: GetRuntimeHealth = mockk(relaxed = true)
    private val mockPrimingHook: RuntimePrimingHook = mockk(relaxed = true)
    private val mockReloadHook: RuntimeMappingReloadHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)

    private lateinit var handler: RuntimeLambdaHandler

    @BeforeAll
    fun setUp() {
        mockkStatic(Core::class)
        val mockCracContext: org.crac.Context<Resource> = mockk(relaxed = true)
        every { Core.getGlobalContext() } returns mockCracContext

        KoinBootstrap.init(listOf(module {
            single<HandleClientRequest> { mockHandleClientRequest }
            single<HandleAdminRequest> { mockHandleAdminRequest }
            single<GetRuntimeHealth> { mockGetRuntimeHealth }
            single { mockPrimingHook }
            single { mockReloadHook }
        }))
        handler = RuntimeLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
        unmockkAll()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockHandleClientRequest, mockHandleAdminRequest, mockGetRuntimeHealth)
    }

    @Nested
    inner class AdminPathRouting {

        @Test
        fun `Given health endpoint request When routing Then should call GetRuntimeHealth`() {
            // Given
            val event = createEvent("/__admin/health", "GET")
            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status": "healthy", "version": "test-version"}""",
            )
            every { mockGetRuntimeHealth.invoke() } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) { mockGetRuntimeHealth.invoke() }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("\"status\": \"healthy\""))
        }

        @Test
        fun `Given admin mappings request When routing Then should call HandleAdminRequest with correct path`() {
            // Given
            val event = createEvent("/__admin/mappings", "GET", mapOf("Content-Type" to "application/json"))
            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"mappings": []}""",
            )
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAdminRequest.invoke(
                    "mappings",
                    match { request ->
                        request.method == HttpMethod.GET &&
                            request.path == "mappings" &&
                            request.headers["Content-Type"] == "application/json"
                    },
                )
            }
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            assertEquals(200, response.statusCode)
            assertEquals("""{"mappings": []}""", response.body)
        }

        @Test
        fun `Given admin POST request When routing Then should call HandleAdminRequest with body`() {
            // Given
            val mappingBody = """{"request": {"url": "/test"}, "response": {"status": 200}}"""
            val event = createEvent("/__admin/mappings", "POST", body = mappingBody)
            val expectedResponse = HttpResponse(
                HttpStatusCode.CREATED,
                mapOf("Content-Type" to listOf("application/json")),
                """{"id": "test-mapping"}""",
            )
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAdminRequest.invoke(
                    "mappings",
                    match { request ->
                        request.method == HttpMethod.POST &&
                            request.path == "mappings" &&
                            request.body == mappingBody
                    },
                )
            }
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given admin DELETE request When routing Then should call HandleAdminRequest`() {
            // Given
            val event = createEvent("/__admin/mappings/test-id", "DELETE")
            val expectedResponse = HttpResponse(HttpStatusCode.OK)
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAdminRequest.invoke(
                    "mappings/test-id",
                    match { request ->
                        request.method == HttpMethod.DELETE &&
                            request.path == "mappings/test-id"
                    },
                )
            }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given admin request with query parameters When routing Then should pass query parameters`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/requests")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(mapOf("limit" to "10", "since" to "2024-01-01"))
            val expectedResponse = HttpResponse(HttpStatusCode.OK)
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAdminRequest.invoke(
                    "requests",
                    match { request ->
                        request.queryParameters["limit"] == "10" &&
                            request.queryParameters["since"] == "2024-01-01"
                    },
                )
            }
            assertEquals(200, response.statusCode)
        }
    }

    @Nested
    inner class MockPathRouting {

        @Test
        fun `Given mock endpoint request When routing Then should call HandleClientRequest with correct path`() {
            // Given
            val event = createEvent("/mocknest/api/users", "GET", mapOf("Accept" to "application/json"))
            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"users": []}""",
            )
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.GET &&
                            request.path == "api/users" &&
                            request.headers["Accept"] == "application/json"
                    },
                )
            }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            assertEquals(200, response.statusCode)
            assertEquals("""{"users": []}""", response.body)
        }

        @Test
        fun `Given mock POST request When routing Then should call HandleClientRequest with body`() {
            // Given
            val requestBody = """{"name": "John", "email": "john@example.com"}"""
            val event = createEvent("/mocknest/api/users", "POST", body = requestBody)
            val expectedResponse = HttpResponse(
                HttpStatusCode.CREATED,
                mapOf("Content-Type" to listOf("application/json")),
                """{"id": "123", "name": "John"}""",
            )
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.POST &&
                            request.path == "api/users" &&
                            request.body == requestBody
                    },
                )
            }
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given mock PUT request When routing Then should call HandleClientRequest`() {
            // Given
            val requestBody = """{"name": "Updated Name"}"""
            val event = createEvent("/mocknest/api/users/123", "PUT", body = requestBody)
            val expectedResponse = HttpResponse(HttpStatusCode.OK)
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.PUT &&
                            request.path == "api/users/123"
                    },
                )
            }
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
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
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
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given AI generation path When routing Then should return 404 not found`() {
            // Given
            val event = createEvent("/ai/generation/generate", "POST", body = """{"spec": "openapi spec"}""")

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ResponseMapping {

        @Test
        fun `Given response with headers When mapping Then should convert multi-value to single-value using first value`() {
            // Given
            val event = createEvent("/__admin/mappings", "GET")
            val responseHeaders = mapOf(
                "Content-Type" to listOf("application/json", "text/plain"),
                "X-Custom-Header" to listOf("first-value", "second-value"),
            )
            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                responseHeaders,
                """{"result": "success"}""",
            )
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("first-value", response.headers["X-Custom-Header"])
            assertEquals("""{"result": "success"}""", response.body)
        }

        @Test
        fun `Given response without body When mapping Then should return empty body`() {
            // Given
            val event = createEvent("/__admin/mappings/test-id", "DELETE")
            val expectedResponse = HttpResponse(HttpStatusCode(204))
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(204, response.statusCode)
            assertEquals("", response.body)
        }

        @Test
        fun `Given response with null headers When mapping Then should handle gracefully`() {
            // Given
            val event = createEvent("/__admin/mappings", "GET")
            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                null,
                """{"result": "success"}""",
            )
            every { mockHandleAdminRequest.invoke(any(), any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            assertEquals(200, response.statusCode)
            assertNull(response.headers)
            assertEquals("""{"result": "success"}""", response.body)
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `Given request with null query parameters When routing Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/test")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(null)
            val expectedResponse = HttpResponse(HttpStatusCode.OK)
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request -> request.queryParameters.isEmpty() },
                )
            }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given request with null body When routing Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/test")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(null)
                .withQueryStringParameters(emptyMap())
            val expectedResponse = HttpResponse(HttpStatusCode.CREATED)
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request -> request.body == null },
                )
            }
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given request with null headers When routing Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/test")
                .withHttpMethod("GET")
                .withHeaders(null)
                .withQueryStringParameters(emptyMap())
            val expectedResponse = HttpResponse(HttpStatusCode.OK)
            every { mockHandleClientRequest.invoke(any()) } returns expectedResponse

            // When
            val response = handler.handleRequest(event, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleClientRequest.invoke(
                    match { request -> request.headers.isEmpty() },
                )
            }
            assertEquals(200, response.statusCode)
        }
    }

    private fun createEvent(
        path: String,
        httpMethod: String,
        headers: Map<String, String> = mapOf("Accept" to "application/json"),
        body: String? = null,
    ): APIGatewayProxyRequestEvent =
        APIGatewayProxyRequestEvent()
            .withPath(path)
            .withHttpMethod(httpMethod)
            .withHeaders(headers)
            .withBody(body)
            .withQueryStringParameters(emptyMap())
}
