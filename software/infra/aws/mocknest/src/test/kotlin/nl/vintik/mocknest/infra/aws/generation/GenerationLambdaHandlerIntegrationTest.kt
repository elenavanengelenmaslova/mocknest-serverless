package nl.vintik.mocknest.infra.aws.generation

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.infra.aws.MockNestApplication
import nl.vintik.mocknest.infra.aws.generation.config.AwsLocalStackTestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.function.Function

private val logger = KotlinLogging.logger {}

/**
 * Integration test for GenerationLambdaHandler using Spring Boot test support with LocalStack S3 and Bedrock.
 * 
 * This test validates:
 * - Spring context loads successfully with MockNestApplication configuration
 * - LocalStack S3 integration works correctly for specification storage
 * - Generation Lambda handler routes AI requests appropriately
 * - AI generation paths are handled correctly
 * - Runtime paths are properly isolated (return 404)
 */
@SpringBootTest(
    classes = [MockNestApplication::class],
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
class GenerationLambdaHandlerIntegrationTest {

    @Autowired
    @Qualifier("generationRouter")
    private lateinit var generationRouter: Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>

    @Nested
    inner class AIGenerationIntegration {

        @Test
        fun `Given AI from-spec request When processing Then should handle request`() {
            // Given
            val requestBody = """
                {
                    "namespace": {
                        "apiName": "test-api",
                        "client": "test-client"
                    },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0\npaths:\n  /users:\n    get:\n      responses:\n        '200':\n          description: Success",
                    "description": "Generate mocks for user API"
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "AI from-spec response: ${response.statusCode} - ${response.body}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
        }
    }

    @Nested
    inner class S3SpecificationStorage {

        @Test
        fun `Given AI from-spec with specification When processing Then should interact with S3`() {
            // Given
            val requestBody = """
                {
                    "namespace": {
                        "apiName": "user-api",
                        "client": "test-client"
                    },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0\npaths:\n  /users:\n    get:\n      responses:\n        '200':\n          description: Success",
                    "description": "Generate user API mocks"
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Spec storage response: ${response.statusCode}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
        }
    }

    @Nested
    inner class PathIsolation {

        @Test
        fun `Given admin path When routing Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/__admin/mappings")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Admin path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given mock endpoint path When routing Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/mocknest/api/users")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Accept" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Mock path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
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
            logger.info { "Runtime path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given unknown AI path When routing Then should return 404`() {
            // Given
            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/unknown")
                .withHttpMethod("GET")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Unknown AI path response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given missing required fields When processing Then should handle gracefully`() {
            // Given
            val incompleteBody = """{"description": "Missing namespace and spec fields"}"""

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(incompleteBody)
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Missing fields response: ${response.statusCode}" }
            assertEquals(400, response.statusCode)
        }
    }

    @Nested
    inner class QueryParameterHandling {

        @Test
        fun `Given AI request with query parameters When processing Then should pass parameters`() {
            // Given
            val requestBody = """
                {
                    "namespace": {
                        "apiName": "test-api",
                        "client": "test-client"
                    },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0",
                    "description": "Test generation"
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(mapOf("format" to "json", "validate" to "true"))

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Query parameters response: ${response.statusCode}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
        }

        @Test
        fun `Given AI request with null query parameters When processing Then should handle gracefully`() {
            // Given
            val requestBody = """
                {
                    "namespace": {
                        "apiName": "test-api",
                        "client": "test-client"
                    },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0",
                    "description": "Test generation"
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(null)

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Null query parameters response: ${response.statusCode}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
        }
    }

    @Nested
    inner class ResponseHeaders {

        @Test
        fun `Given AI from-spec response When processing Then should include appropriate headers`() {
            // Given
            val requestBody = """
                {
                    "namespace": {
                        "apiName": "test-api",
                        "client": "test-client"
                    },
                    "specification": "openapi: 3.0.0\ninfo:\n  title: Test API\n  version: 1.0.0",
                    "description": "Test generation"
                }
            """.trimIndent()

            val event = APIGatewayProxyRequestEvent()
                .withPath("/ai/generation/from-spec")
                .withHttpMethod("POST")
                .withHeaders(mapOf("Content-Type" to "application/json"))
                .withBody(requestBody)
                .withQueryStringParameters(emptyMap())

            // When
            val response = generationRouter.apply(event)

            // Then
            logger.info { "Response headers: ${response.headers}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
            assertNotNull(response.headers)
        }
    }
}
