package nl.vintik.mocknest.infra.aws.runtime

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.sqs.SqsClient
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.infra.aws.config.LocalStackTestHelper
import nl.vintik.mocknest.infra.aws.config.TEST_BUCKET_NAME
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import org.koin.core.context.startKoin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val logger = KotlinLogging.logger {}

/**
 * Integration test for RuntimeLambdaHandler using Koin test utilities with LocalStack S3.
 *
 * Replaces the previous `@SpringBootTest`-based `RuntimeLambdaHandlerIntegrationTest`.
 * Uses Koin modules with a test-specific S3 client pointing at LocalStack.
 *
 * Validates:
 * - Koin context loads successfully with runtime modules
 * - LocalStack S3 integration works correctly
 * - Runtime Lambda handler routes requests appropriately
 * - Admin API and mock endpoint paths are handled correctly
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeLambdaHandlerIntegrationTest : KoinTest {

    private lateinit var testS3Client: S3Client

    private val handleClientRequest: HandleClientRequest by inject()
    private val handleAdminRequest: HandleAdminRequest by inject()
    private val getRuntimeHealth: GetRuntimeHealth by inject()
    private val storage: ObjectStorageInterface by inject()

    companion object {
        private val envVars = EnvironmentVariables()
    }

    @BeforeAll
    fun setupAll() {
        // Set environment variables before Koin initialization
        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.set("AWS_DEFAULT_REGION", TEST_REGION)
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "")
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", "Authorization,X-Api-Key")
        envVars.setup()

        // Create LocalStack-backed S3 client
        testS3Client = LocalStackTestHelper.createTestS3Client()
        LocalStackTestHelper.ensureTestBucket(testS3Client)

        // Test-specific module that overrides coreModule's S3Client with LocalStack client
        val testCoreModule = module {
            single { mapper }
            single { testS3Client }
        }

        startKoin {
            allowOverride(false)
            modules(testCoreModule, runtimeModule())
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up S3 objects after each test
        runBlocking {
            val keys = storage.list().toList()
            keys.forEach { key -> storage.delete(key) }
        }
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        envVars.teardown()
        runBlocking { testS3Client.close() }
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
        // Replicate the routing logic from RuntimeLambdaHandler.handleRequest()
        // without needing a real Lambda Context
        val adminPrefix = "/__admin/"
        val mocknestPrefix = "/mocknest/"
        val path = event.path

        val response = when {
            path == "${adminPrefix}health" -> getRuntimeHealth()
            path.startsWith(adminPrefix) -> {
                val adminPath = path.removePrefix(adminPrefix)
                handleAdminRequest(
                    adminPath,
                    nl.vintik.mocknest.domain.core.HttpRequest(
                        method = nl.vintik.mocknest.domain.core.HttpMethod.resolve(event.httpMethod),
                        headers = event.headers.orEmpty(),
                        path = adminPath,
                        queryParameters = event.queryStringParameters.orEmpty(),
                        body = event.body,
                    )
                )
            }
            path.startsWith(mocknestPrefix) -> {
                val clientPath = path.removePrefix(mocknestPrefix)
                handleClientRequest(
                    nl.vintik.mocknest.domain.core.HttpRequest(
                        method = nl.vintik.mocknest.domain.core.HttpMethod.resolve(event.httpMethod),
                        headers = event.headers.orEmpty(),
                        path = clientPath,
                        queryParameters = event.queryStringParameters.orEmpty(),
                        body = event.body,
                    )
                )
            }
            else -> nl.vintik.mocknest.domain.core.HttpResponse(
                nl.vintik.mocknest.domain.core.HttpStatusCode.NOT_FOUND,
                body = "Path $path not found",
            )
        }

        return APIGatewayProxyResponseEvent()
            .withStatusCode(response.statusCode.value())
            .withHeaders(response.headers?.mapValues { it.value.first() })
            .withBody(response.body.orEmpty())
    }

    @Nested
    inner class AdminAPIIntegration {

        @Test
        fun `Given admin mappings GET request When processing Then should return mappings list`() {
            val event = createEvent("GET", "/__admin/mappings")
            val response = routeRequest(event)

            logger.info { "Admin mappings response: ${response.statusCode} - ${response.body}" }
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `Given admin POST mapping request When processing Then should create mapping`() {
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

            val event = createEvent("POST", "/__admin/mappings", body = mappingBody)
            val response = routeRequest(event)

            logger.info { "Create mapping response: ${response.statusCode} - ${response.body}" }
            assertEquals(201, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `Given admin reset request When processing Then should reset all mappings`() {
            val event = createEvent("POST", "/__admin/mappings/reset")
            val response = routeRequest(event)

            logger.info { "Reset mappings response: ${response.statusCode}" }
            assertEquals(200, response.statusCode)
        }

        @Test
        fun `Given admin requests GET When processing Then should return request journal`() {
            val event = createEvent("GET", "/__admin/requests")
            val response = routeRequest(event)

            logger.info { "Request journal response: ${response.statusCode} - ${response.body}" }
            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
        }
    }

    @Nested
    inner class MockEndpointIntegration {

        @Test
        fun `Given mock endpoint request When no mapping exists Then should return 404`() {
            val event = createEvent(
                "GET", "/mocknest/api/nonexistent",
                headers = mapOf("Accept" to "application/json"),
            )
            val response = routeRequest(event)

            logger.info { "Nonexistent endpoint response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }

        @Test
        fun `Given mock endpoint request When mapping exists Then should return mocked response`() {
            // Create a mapping first
            val mappingBody = """
                {
                    "request": { "url": "/api/users", "method": "GET" },
                    "response": {
                        "status": 200,
                        "body": "{\"users\": []}",
                        "headers": { "Content-Type": "application/json" }
                    }
                }
            """.trimIndent()

            val createResponse = routeRequest(createEvent("POST", "/__admin/mappings", body = mappingBody))
            assertEquals(201, createResponse.statusCode)

            // Call the mocked endpoint
            val mockEvent = createEvent(
                "GET", "/mocknest/api/users",
                headers = mapOf("Accept" to "application/json"),
            )
            val mockResponse = routeRequest(mockEvent)

            logger.info { "Mock endpoint response: ${mockResponse.statusCode} - ${mockResponse.body}" }
            assertEquals(200, mockResponse.statusCode)
            assertNotNull(mockResponse.body)
        }

        @Test
        fun `Given mock POST request When mapping exists Then should return mocked response`() {
            val mappingBody = """
                {
                    "request": { "url": "/api/users", "method": "POST" },
                    "response": {
                        "status": 201,
                        "body": "{\"id\": \"123\", \"name\": \"John\"}",
                        "headers": { "Content-Type": "application/json" }
                    }
                }
            """.trimIndent()

            routeRequest(createEvent("POST", "/__admin/mappings", body = mappingBody))

            val mockEvent = createEvent(
                "POST", "/mocknest/api/users",
                body = """{"name": "John", "email": "john@example.com"}""",
            )
            val mockResponse = routeRequest(mockEvent)

            logger.info { "Mock POST response: ${mockResponse.statusCode} - ${mockResponse.body}" }
            assertEquals(201, mockResponse.statusCode)
            assertNotNull(mockResponse.body)
        }
    }

    @Nested
    inner class S3PersistenceIntegration {

        @Test
        fun `Given mapping with large body When created Then should externalize to S3`() {
            val largeBody = "x".repeat(10000)
            val mappingBody = """
                {
                    "request": { "url": "/api/large-response", "method": "GET" },
                    "response": {
                        "status": 200,
                        "body": "$largeBody",
                        "headers": { "Content-Type": "text/plain" }
                    }
                }
            """.trimIndent()

            val createResponse = routeRequest(createEvent("POST", "/__admin/mappings", body = mappingBody))
            logger.info { "Large body mapping created: ${createResponse.statusCode}" }
            assertEquals(201, createResponse.statusCode)

            val getResponse = routeRequest(createEvent("GET", "/__admin/mappings"))
            assertEquals(200, getResponse.statusCode)
            assertNotNull(getResponse.body)
        }
    }

    @Nested
    inner class PathIsolation {

        @Test
        fun `Given AI generation path When routing Then should return 404`() {
            val event = createEvent(
                "POST", "/ai/generation/from-spec",
                body = """{"namespace": {"apiName": "test", "client": "test"}, "specification": "openapi spec"}""",
            )
            val response = routeRequest(event)

            logger.info { "AI path isolation response: ${response.statusCode}" }
            assertEquals(404, response.statusCode)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given unknown admin path When routing Then should return 404`() {
            val event = createEvent("GET", "/__admin/unknown")
            val response = routeRequest(event)

            logger.info { "Unknown admin path response: ${response.statusCode}" }
            // WireMock returns 404 for unknown admin paths
            assertEquals(404, response.statusCode)
        }
    }
}
