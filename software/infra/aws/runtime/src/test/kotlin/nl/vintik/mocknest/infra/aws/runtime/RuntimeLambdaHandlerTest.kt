package nl.vintik.mocknest.infra.aws.runtime

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap

class RuntimeLambdaHandlerTest {

    private val mockHandleClientRequest: HandleClientRequest = mockk(relaxed = true)
    private val mockHandleAdminRequest: HandleAdminRequest = mockk(relaxed = true)
    private val mockGetRuntimeHealth: GetRuntimeHealth = mockk(relaxed = true)
    
    private val handler = RuntimeLambdaHandler(mockHandleClientRequest, mockHandleAdminRequest, mockGetRuntimeHealth)
    private val runtimeRouter = handler.runtimeRouter()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class AdminPathRouting {

        @Test
        fun `Given health endpoint request When routing Then should call GetRuntimeHealth`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/health")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"status": "healthy", "version": "0.2.0"}"""
            )
            
            every { 
                mockGetRuntimeHealth.invoke() 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { mockGetRuntimeHealth.invoke() }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("\"status\": \"healthy\""))
            assert(response.body.contains("\"version\":"))
        }

        @Test
        fun `Given admin mappings request When routing Then should call HandleAdminRequest with correct path`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"mappings": []}"""
            )
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleAdminRequest.invoke(
                    "mappings",
                    match { request ->
                        request.method == HttpMethod.GET &&
                        request.path == "mappings" &&
                        request.headers["Content-Type"] == "application/json"
                    }
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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(mappingBody)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.CREATED,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"id": "test-mapping"}"""
            )
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleAdminRequest.invoke(
                    "mappings",
                    match { request ->
                        request.method == HttpMethod.POST &&
                        request.path == "mappings" &&
                        request.body == mappingBody
                    }
                )
            }
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given admin DELETE request When routing Then should call HandleAdminRequest`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings/test-id")
                .withHttpMethod("DELETE")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleAdminRequest.invoke(
                    "mappings/test-id",
                    match { request ->
                        request.method == HttpMethod.DELETE &&
                        request.path == "mappings/test-id"
                    }
                )
            }
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            
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
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleAdminRequest.invoke(
                    "requests",
                    match { request ->
                        request.queryParameters["limit"] == "10" &&
                        request.queryParameters["since"] == "2024-01-01"
                    }
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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"users": []}"""
            )
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.GET &&
                        request.path == "api/users" &&
                        request.headers["Accept"] == "application/json"
                    }
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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.CREATED,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"id": "123", "name": "John"}"""
            )
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.POST &&
                        request.path == "api/users" &&
                        request.body == requestBody
                    }
                )
            }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given mock request with query parameters When routing Then should pass query parameters`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/products")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(mapOf("category" to "electronics", "limit" to "20"))
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.queryParameters["category"] == "electronics" &&
                        request.queryParameters["limit"] == "20"
                    }
                )
            }
            
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given mock PUT request When routing Then should call HandleClientRequest`() {
            // Given
            val requestBody = """{"name": "Updated Name"}"""
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users/123")
                .withHttpMethod("PUT")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.method == HttpMethod.PUT &&
                        request.path == "api/users/123"
                    }
                )
            }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        }
    }

    @Nested
    inner class GenerationPathIsolation {

        @Test
        fun `Given AI generation path When routing Then should return 404 not found`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            
            assertEquals(404, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("not found"))
        }

        @Test
        fun `Given AI path When routing Then should not invoke generation use cases`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/status")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class UnknownPathHandling {

        @Test
        fun `Given unknown path When routing Then should return 404 not found`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/unknown/path")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/")
                .withHttpMethod("GET")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
            verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
            
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ResponseMapping {

        @Test
        fun `Given response with headers When mapping Then should convert to API Gateway response`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())
            
            val responseHeaders = LinkedMultiValueMap<String, String>().apply {
                add("Content-Type", "application/json")
                add("X-Custom-Header", "custom-value")
            }
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                responseHeaders,
                """{"result": "success"}"""
            )
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("custom-value", response.headers["X-Custom-Header"])
            assertEquals("""{"result": "success"}""", response.body)
        }

        @Test
        fun `Given response without body When mapping Then should return empty body`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings/test-id")
                .withHttpMethod("DELETE")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(HttpStatus.NO_CONTENT)
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            assertEquals(204, response.statusCode)
            assertEquals("", response.body)
        }

        @Test
        fun `Given error response When mapping Then should preserve error status code`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/not-found")
                .withHttpMethod("GET")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.NOT_FOUND,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"error": "Resource not found"}"""
            )
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            assertEquals(404, response.statusCode)
            assertEquals("""{"error": "Resource not found"}""", response.body)
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
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
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
                .withPath("/mocknest/api/test")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(null)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(HttpStatus.CREATED)
            
            every { 
                mockHandleClientRequest.invoke(any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            verify(exactly = 1) { 
                mockHandleClientRequest.invoke(
                    match { request ->
                        request.body == null
                    }
                )
            }
            assertEquals(201, response.statusCode)
        }

        @Test
        fun `Given response with null headers When mapping Then should handle gracefully`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                null,
                """{"result": "success"}"""
            )
            
            every { 
                mockHandleAdminRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = runtimeRouter.apply(event)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("""{"result": "success"}""", response.body)
        }
    }
}
