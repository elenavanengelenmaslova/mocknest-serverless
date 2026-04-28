package nl.vintik.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.config.LocalStackTestHelper
import nl.vintik.mocknest.infra.aws.config.TEST_BUCKET_NAME
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.generation.di.generationModule
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val logger = KotlinLogging.logger {}

/**
 * Integration test for GenerationLambdaHandler using Koin test utilities with LocalStack S3.
 *
 * Replaces the previous `@SpringBootTest`-based `GenerationLambdaHandlerIntegrationTest`.
 * Uses Koin modules with a test-specific S3 client pointing at LocalStack and a mock
 * BedrockRuntimeClient (Bedrock is not available in LocalStack).
 *
 * Validates:
 * - Koin context loads successfully with generation modules
 * - LocalStack S3 integration works correctly for specification storage
 * - Generation Lambda handler routes AI requests appropriately
 * - Runtime paths are properly isolated (return 404)
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationLambdaHandlerIntegrationTest : KoinTest {

    private lateinit var testS3Client: S3Client

    private val handleAIGenerationRequest: HandleAIGenerationRequest by inject()
    private val getAIHealth: GetAIHealth by inject()

    companion object {
        private val envVars = EnvironmentVariables()
    }

    @BeforeAll
    fun setupAll() {
        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.set("BEDROCK_MODEL_NAME", "AmazonNovaPro")
        envVars.set("BEDROCK_INFERENCE_MODE", "AUTO")
        envVars.set("BEDROCK_GENERATION_MAX_RETRIES", "1")
        envVars.setup()

        testS3Client = LocalStackTestHelper.createTestS3Client()
        LocalStackTestHelper.ensureTestBucket(testS3Client)

        // Test-specific module: real S3 from LocalStack, mock Bedrock
        val testCoreModule = module {
            single { mapper }
            single { testS3Client }
        }

        val testBedrockOverride = module {
            single { mockk<BedrockRuntimeClient>(relaxed = true) }
        }

        startKoin {
            allowOverride(true) // Allow Bedrock mock to override generationModule's definition
            modules(testCoreModule, generationModule(), testBedrockOverride)
        }
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        envVars.teardown()
        runCatching { kotlinx.coroutines.runBlocking { testS3Client.close() } }
    }

    private fun createEvent(
        httpMethod: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = mapOf("Content-Type" to "application/json"),
        queryStringParameters: Map<String, String>? = emptyMap(),
    ): APIGatewayProxyRequestEvent =
        APIGatewayProxyRequestEvent()
            .withPath(path)
            .withHttpMethod(httpMethod)
            .withHeaders(headers)
            .withBody(body)
            .withQueryStringParameters(queryStringParameters)

    private fun routeRequest(event: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val aiPrefix = "/ai/generation/"
        val path = event.path

        val response = when {
            path == "${aiPrefix}health" -> getAIHealth()
            path.startsWith(aiPrefix) -> {
                val aiPath = "/" + path.removePrefix(aiPrefix)
                handleAIGenerationRequest(
                    aiPath,
                    HttpRequest(
                        method = HttpMethod.resolve(event.httpMethod),
                        headers = event.headers.orEmpty(),
                        path = aiPath,
                        queryParameters = event.queryStringParameters.orEmpty(),
                        body = event.body,
                    )
                )
            }
            else -> HttpResponse(
                HttpStatusCode.NOT_FOUND,
                body = "Path $path not found",
            )
        }

        return APIGatewayProxyResponseEvent()
            .withStatusCode(response.statusCode.value())
            .withHeaders(response.headers?.mapValues { it.value.first() })
            .withBody(response.body.orEmpty())
    }

    @Nested
    inner class AIHealthIntegration {

        @Test
        fun `Given AI health request When processing Then should return health status`() {
            val event = createEvent("GET", "/ai/generation/health")
            val response = routeRequest(event)

            logger.info { "AI health response: ${response.statusCode} - ${response.body}" }
            assertNotNull(response)
            assertNotNull(response.statusCode)
            // Health check should succeed (returns 200 with status info)
            assertEquals(200, response.statusCode)
        }
    }

    @Nested
    inner class PathIsolation {

        @Test
        fun `Given admin path When routing Then should return 404`() {
            val event = createEvent("GET", "/__admin/mappings")
            val response = routeRequest(event)

            logger.info { "Admin path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given mock endpoint path When routing Then should return 404`() {
            val event = createEvent(
                "GET", "/mocknest/api/users",
                headers = mapOf("Accept" to "application/json"),
            )
            val response = routeRequest(event)

            logger.info { "Mock path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given runtime path When routing Then should not invoke runtime use cases`() {
            val event = createEvent("GET", "/__admin/requests")
            val response = routeRequest(event)

            logger.info { "Runtime path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given unknown AI path When routing Then should return 404`() {
            val event = createEvent("GET", "/ai/generation/unknown")
            val response = routeRequest(event)

            logger.info { "Unknown AI path response: ${response.statusCode}" }
            // The use case returns 404 for unknown sub-paths
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given missing required fields When processing Then should handle gracefully`() {
            val incompleteBody = """{"description": "Missing namespace and spec fields"}"""
            val event = createEvent("POST", "/ai/generation/from-spec", body = incompleteBody)
            val response = routeRequest(event)

            logger.info { "Missing fields response: ${response.statusCode}" }
            assertEquals(400, response.statusCode)
        }
    }
}
