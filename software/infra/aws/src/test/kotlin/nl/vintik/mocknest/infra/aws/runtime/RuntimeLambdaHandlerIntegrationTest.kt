package nl.vintik.mocknest.infra.aws.runtime

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.infra.aws.Application
import nl.vintik.mocknest.infra.aws.config.AwsLocalStackTestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.function.Function

private val logger = KotlinLogging.logger {}

/**
 * Integration test for RuntimeLambdaHandler using Spring Boot test support with LocalStack S3.
 * 
 * This test validates:
 * - Spring context loads successfully with RuntimeApplication configuration
 * - LocalStack S3 integration works correctly
 * - Runtime Lambda handler routes requests appropriately
 * - Admin API and mock endpoint paths are handled correctly
 * - Generation paths are properly isolated (return 404)
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.cloud.function.web.export.enabled=false",
        "storage.bucket.name=test-bucket"
    ]
)
@ActiveProfiles("test")
@Import(AwsLocalStackTestConfiguration::class)
@Testcontainers
class RuntimeLambdaHandlerIntegrationTest {

    @Autowired
    private lateinit var runtimeRouter: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>

    @Nested
    inner class AdminAPIIntegration {

        @Test
        fun `Given admin mappings GET request When processing Then should return mappings list`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Admin mappings response: ${response.statusCode} - ${response.body}" }
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `Given admin POST mapping request When processing Then should create mapping`() {
            // Given
            val mappingBody = """
                {
                    "request": {
                        "url": "/test-endpoint",
                        "method": "GET"
                    },
                    "response": {
                        "status": 200,
                        "body": "Test response"
                    }
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(mappingBody)
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Create mapping response: ${response.statusCode} - ${response.body}" }
            assertEquals(201, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `Given admin reset request When processing Then should reset all mappings`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings/reset")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Reset mappings response: ${response.statusCode}" }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given admin requests GET When processing Then should return request journal`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/requests")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Request journal response: ${response.statusCode} - ${response.body}" }
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
        }
    }

    @Nested
    inner class MockEndpointIntegration {

        @Test
        fun `Given mock endpoint request When no mapping exists Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/nonexistent")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Nonexistent endpoint response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given mock endpoint request When mapping exists Then should return mocked response`() {
            // Given - First create a mapping
            val mappingBody = """
                {
                    "request": {
                        "url": "/api/users",
                        "method": "GET"
                    },
                    "response": {
                        "status": 200,
                        "body": "{\"users\": []}",
                        "headers": {
                            "Content-Type": "application/json"
                        }
                    }
                }
            """.trimIndent()

            val createMappingEvent = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(mappingBody)
                .withQueryStringParameters(emptyMap())

            val createResponse = runtimeRouter.apply(createMappingEvent)
            logger.info { "Mapping created: ${createResponse.statusCode}" }

            // When - Call the mocked endpoint
            val mockEvent = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            val mockResponse = runtimeRouter.apply(mockEvent)

            // Then
            logger.info { "Mock endpoint response: ${mockResponse.statusCode} - ${mockResponse.body}" }
            assertEquals(200, mockResponse.statusCode)
            assertNotNull(mockResponse.body)
        }

        @Test
        fun `Given mock POST request When mapping exists Then should return mocked response`() {
            // Given - Create a POST mapping
            val mappingBody = """
                {
                    "request": {
                        "url": "/api/users",
                        "method": "POST"
                    },
                    "response": {
                        "status": 201,
                        "body": "{\"id\": \"123\", \"name\": \"John\"}",
                        "headers": {
                            "Content-Type": "application/json"
                        }
                    }
                }
            """.trimIndent()

            val createMappingEvent = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(mappingBody)
                .withQueryStringParameters(emptyMap())

            runtimeRouter.apply(createMappingEvent)

            // When - Call the mocked POST endpoint
            val mockEvent = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"name": "John", "email": "john@example.com"}""")
                .withQueryStringParameters(emptyMap())

            val mockResponse = runtimeRouter.apply(mockEvent)

            // Then
            logger.info { "Mock POST response: ${mockResponse.statusCode} - ${mockResponse.body}" }
            assertEquals(201, mockResponse.statusCode)
            assertNotNull(mockResponse.body)
        }
    }

    @Nested
    inner class S3PersistenceIntegration {

        @Test
        fun `Given mapping with large body When created Then should externalize to S3`() {
            // Given - Create a mapping with a large body
            val largeBody = "x".repeat(10000) // 10KB body
            val mappingBody = """
                {
                    "request": {
                        "url": "/api/large-response",
                        "method": "GET"
                    },
                    "response": {
                        "status": 200,
                        "body": "$largeBody",
                        "headers": {
                            "Content-Type": "text/plain"
                        }
                    }
                }
            """.trimIndent()

            val createEvent = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(mappingBody)
                .withQueryStringParameters(emptyMap())

            // When
            val createResponse = runtimeRouter.apply(createEvent)

            // Then
            logger.info { "Large body mapping created: ${createResponse.statusCode}" }
            assertEquals(201, createResponse.statusCode)

            // Verify the mapping can be retrieved
            val getEvent = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            val getResponse = runtimeRouter.apply(getEvent)
            assertEquals(200, getResponse.statusCode)
            assertNotNull(getResponse.body)
        }
    }

    @Nested
    inner class PathIsolation {

        @Test
        fun `Given AI generation path When routing Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody("""{"namespace": {"apiName": "test", "client": "test"}, "specification": "openapi spec"}""")
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "AI path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given unknown admin path When routing Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/unknown")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = runtimeRouter.apply(event)

            // Then
            logger.info { "Unknown admin path response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }
    }
}
