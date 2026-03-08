package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap

class GenerationLambdaHandlerTest {

    private val mockHandleAIGenerationRequest: HandleAIGenerationRequest = mockk(relaxed = true)
    
    private val handler = GenerationLambdaHandler(mockHandleAIGenerationRequest)
    private val generationRouter = handler.generationRouter()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class AIPathRouting {

        @Test
        fun `Given AI generation request When routing Then should call HandleAIGenerationRequest with correct path`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"status": "generated"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/status")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"status": "ready"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/bulk-generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.ACCEPTED,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"jobId": "job-123"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(mapOf("format" to "json", "validate" to "true"))
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/analyze-traffic")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"analysisId": "analysis-123"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }
            
            assertEquals(404, response.statusCode)
            assertNotNull(response.body)
            assert(response.body.contains("not found"))
        }

        @Test
        fun `Given mock endpoint path When routing Then should return 404 not found`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/requests")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/unknown/path")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/")
                .withHttpMethod("GET")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(emptyMap())
            
            val responseHeaders = LinkedMultiValueMap<String, String>().apply {
                add("Content-Type", "application/json")
                add("X-Generation-Id", "gen-123")
            }
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                responseHeaders,
                """{"result": "success"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("gen-123", response.headers["X-Generation-Id"])
            assertEquals("""{"result": "success"}""", response.body)
        }

        @Test
        fun `Given response without body When mapping Then should return empty body`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/delete")
                .withHttpMethod("DELETE")
                .withHeaders(emptyMap())
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(HttpStatus.NO_CONTENT)
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

            // Then
            assertEquals(204, response.statusCode)
            assertEquals("", response.body)
        }

        @Test
        fun `Given error response When mapping Then should preserve error status code`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/invalid")
                .withHttpMethod("POST")
                .withHeaders(emptyMap())
                .withBody("""{"invalid": "data"}""")
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.BAD_REQUEST,
                LinkedMultiValueMap<String, String>().apply {
                    add("Content-Type", "application/json")
                },
                """{"error": "Invalid specification format"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            
            val expectedResponse = HttpResponse(HttpStatus.OK)
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

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
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/generate")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"spec": "openapi spec"}""")
                .withQueryStringParameters(emptyMap())
            
            val expectedResponse = HttpResponse(
                HttpStatus.OK,
                null,
                """{"result": "success"}"""
            )
            
            every { 
                mockHandleAIGenerationRequest.invoke(any(), any()) 
            } returns expectedResponse

            // When
            val response = generationRouter.apply(event)

            // Then
            assertEquals(200, response.statusCode)
            assertEquals("""{"result": "success"}""", response.body)
        }
    }
}
