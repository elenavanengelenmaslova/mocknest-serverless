package nl.vintik.mocknest.infra.aws.runtime.streaming

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.amazonaws.services.lambda.runtime.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.streaming.StreamingProtocolWriter
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import nl.vintik.mocknest.infra.aws.runtime.function.StreamingRuntimeLambdaHandler
import org.crac.Core
import org.crac.Resource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

private const val TEST_BUCKET_NAME = "streaming-integration-test-bucket"
private const val TEST_REGION = "us-east-1"

/**
 * Integration test for the streaming response flow end-to-end.
 *
 * Uses a LocalStack container for S3 and a real WireMock server to validate:
 * - Register mock → invoke handler → verify streaming response body matches byte-for-byte
 * - Large payload (7MB+) delivery via streaming
 * - SSE mock with chunkedDribbleDelay timing verification
 * - Custom status codes and headers
 * - Error status codes (404, 503)
 *
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamingIntegrationTest : KoinTest {

    private lateinit var testS3Client: S3Client
    private lateinit var handler: StreamingRuntimeLambdaHandler
    private val mockContext: Context = mockk(relaxed = true)

    private val storage: ObjectStorageInterface by inject()

    companion object {
        private val envVars = EnvironmentVariables()

        private val localStack: LocalStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.12.0")
        ).withServices(LocalStackContainer.Service.S3)
            .waitingFor(
                Wait.forHttp("/_localstack/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
    }

    @BeforeAll
    fun setupAll() {
        localStack.start()

        // Set environment variables before Koin initialization
        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.set("AWS_DEFAULT_REGION", TEST_REGION)
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "")
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", "Authorization,X-Api-Key")
        envVars.setup()

        // Create LocalStack-backed S3 client
        val s3Endpoint = localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        testS3Client = S3Client {
            region = TEST_REGION
            endpointUrl = Url.parse(s3Endpoint)
            forcePathStyle = true
            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = localStack.accessKey,
                    secretAccessKey = localStack.secretKey,
                )
            )
        }

        // Create test bucket
        runBlocking {
            testS3Client.runCatching {
                createBucket(CreateBucketRequest { bucket = TEST_BUCKET_NAME })
            }.onFailure { exception ->
                val message = exception.message?.lowercase() ?: ""
                if (!message.contains("bucketalreadyownedbyyou") && !message.contains("bucketalreadyexists")) {
                    throw exception
                }
            }
        }

        // Mock CRaC to avoid SnapStart registration issues in tests
        mockkStatic(Core::class)
        val mockCracContext: org.crac.Context<Resource> = mockk(relaxed = true)
        every { Core.getGlobalContext() } returns mockCracContext

        // Test-specific module that overrides coreModule's S3Client with LocalStack client
        val testCoreModule = module {
            single { mapper }
            single { testS3Client }
        }

        KoinBootstrap.init(listOf(testCoreModule, runtimeModule()))
        handler = StreamingRuntimeLambdaHandler()
    }

    @AfterEach
    fun tearDown() {
        // Clean up all WireMock mappings after each test
        val resetInput = createApiGatewayRequest("/__admin/mappings", "DELETE")
        val resetOutput = ByteArrayOutputStream()
        handler.handleRequest(resetInput, resetOutput, mockContext)

        // Clean up S3 objects
        runBlocking {
            val keys = storage.list().toList()
            keys.forEach { key -> storage.delete(key) }
        }
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
        unmockkAll()
        envVars.teardown()
        runBlocking { testS3Client.close() }
        localStack.stop()
    }

    @Test
    fun `Given mock with 1KB body When invoked via streaming handler Then response body matches byte-for-byte with expected status code`() {
        // Given - register a mock with a 1KB body
        val expectedBody = "A".repeat(1024) // 1KB
        val mappingJson = """
            {
                "request": { "url": "/api/small-response", "method": "GET" },
                "response": {
                    "status": 200,
                    "body": "$expectedBody",
                    "headers": { "Content-Type": "text/plain" }
                }
            }
        """.trimIndent()
        registerMapping(mappingJson)

        // When - invoke the mock endpoint via streaming handler
        val input = createApiGatewayRequest("/mocknest/api/small-response", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify response body matches byte-for-byte
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(200, parsed.statusCode)
        assertEquals(expectedBody, parsed.body)
        assertEquals(1024, parsed.body.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `Given mock with 6MB body When invoked via streaming handler Then response body matches byte-for-byte`() {
        // Given - register a mock with a ~6MB body (just under the old limit)
        val expectedBody = "B".repeat(6 * 1024 * 1024) // 6MB
        val mappingJson = buildLargeBodyMapping("/api/medium-response", 200, expectedBody)
        registerMapping(mappingJson)

        // When - invoke the mock endpoint via streaming handler
        val input = createApiGatewayRequest("/mocknest/api/medium-response", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify response body matches byte-for-byte
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(200, parsed.statusCode)
        assertEquals(expectedBody.length, parsed.body.length)
        assertEquals(expectedBody, parsed.body)
    }

    @Test
    fun `Given mock with 7MB body When invoked via streaming handler Then received byte count equals registered byte count`() {
        // Given - register a mock with a 7MB+ body (exceeds old 6MB limit)
        val bodySize = 7 * 1024 * 1024 // 7MB
        val expectedBody = "C".repeat(bodySize)
        val mappingJson = buildLargeBodyMapping("/api/large-response", 200, expectedBody)
        registerMapping(mappingJson)

        // When - invoke the mock endpoint via streaming handler
        val input = createApiGatewayRequest("/mocknest/api/large-response", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify received byte count equals registered byte count
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(200, parsed.statusCode)
        assertEquals(bodySize, parsed.body.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `Given SSE mock with chunkedDribbleDelay When invoked Then delivery time is at least 80 percent of totalDuration`() {
        // Given - register an SSE mock with chunkedDribbleDelay
        // Use persistent=false to prevent body externalization to S3,
        // which would bypass the chunkedDribbleDelay detection in the handler.
        // Use 10 chunks so that total delay = (10-1)/10 * 3000 = 2700ms >= 80% of 3000ms (2400ms)
        val sseBody = "data: event1\\ndata: event2\\ndata: event3\\ndata: event4\\ndata: event5\\n"
        val expectedBody = "data: event1\ndata: event2\ndata: event3\ndata: event4\ndata: event5\n"
        val mappingJson = """
            {
                "persistent": false,
                "request": { "url": "/api/sse-stream", "method": "GET" },
                "response": {
                    "status": 200,
                    "body": "$sseBody",
                    "headers": { "Content-Type": "text/event-stream" },
                    "chunkedDribbleDelay": {
                        "numberOfChunks": 10,
                        "totalDuration": 3000
                    }
                }
            }
        """.trimIndent()
        registerMapping(mappingJson)

        // When - invoke the mock endpoint and measure delivery time
        val input = createApiGatewayRequest("/mocknest/api/sse-stream", "GET")
        val output = ByteArrayOutputStream()
        val startTime = System.currentTimeMillis()
        handler.handleRequest(input, output, mockContext)
        val elapsedTime = System.currentTimeMillis() - startTime

        // Then - verify body content is complete (chunked delivery should preserve body)
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(200, parsed.statusCode)
        assertEquals(expectedBody, parsed.body)

        // Verify delivery time >= 80% of totalDuration (3000ms)
        // With 10 chunks, actual delay = (10-1) * (3000/10) = 9 * 300 = 2700ms
        val minimumExpectedTime = (3000 * 0.80).toLong() // 2400ms
        logger.info { "SSE delivery time: ${elapsedTime}ms (minimum expected: ${minimumExpectedTime}ms)" }
        assertTrue(
            elapsedTime >= minimumExpectedTime,
            "Delivery time ${elapsedTime}ms should be at least 80% of 3000ms (${minimumExpectedTime}ms)"
        )
    }

    @Test
    fun `Given mock with custom status code and custom headers When invoked Then exact status and headers received`() {
        // Given - register a mock with custom status code and headers
        val mappingJson = """
            {
                "request": { "url": "/api/custom-headers", "method": "GET" },
                "response": {
                    "status": 201,
                    "body": "created",
                    "headers": {
                        "Content-Type": "application/json",
                        "X-Custom-Header": "custom-value",
                        "X-Request-Id": "req-12345"
                    }
                }
            }
        """.trimIndent()
        registerMapping(mappingJson)

        // When - invoke the mock endpoint
        val input = createApiGatewayRequest("/mocknest/api/custom-headers", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify exact status code and custom headers
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(201, parsed.statusCode)
        assertEquals("custom-value", parsed.headers["X-Custom-Header"])
        assertEquals("req-12345", parsed.headers["X-Request-Id"])
        assertEquals("created", parsed.body)
    }

    @Test
    fun `Given 404 mock When invoked Then correct status code and body received`() {
        // Given - register a mock returning 404
        val mappingJson = """
            {
                "request": { "url": "/api/not-found", "method": "GET" },
                "response": {
                    "status": 404,
                    "body": "{\"error\":\"Resource not found\"}",
                    "headers": { "Content-Type": "application/json" }
                }
            }
        """.trimIndent()
        registerMapping(mappingJson)

        // When - invoke the mock endpoint
        val input = createApiGatewayRequest("/mocknest/api/not-found", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify 404 status and body
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(404, parsed.statusCode)
        assertEquals("""{"error":"Resource not found"}""", parsed.body)
    }

    @Test
    fun `Given 503 mock When invoked Then correct status code and body received`() {
        // Given - register a mock returning 503
        val mappingJson = """
            {
                "request": { "url": "/api/service-unavailable", "method": "GET" },
                "response": {
                    "status": 503,
                    "body": "{\"error\":\"Service temporarily unavailable\"}",
                    "headers": { "Content-Type": "application/json" }
                }
            }
        """.trimIndent()
        registerMapping(mappingJson)

        // When - invoke the mock endpoint
        val input = createApiGatewayRequest("/mocknest/api/service-unavailable", "GET")
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        // Then - verify 503 status and body
        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(503, parsed.statusCode)
        assertEquals("""{"error":"Service temporarily unavailable"}""", parsed.body)
    }

    // --- Helper methods ---

    /**
     * Registers a WireMock mapping via the admin API through the streaming handler.
     */
    private fun registerMapping(mappingJson: String) {
        val input = createApiGatewayRequest(
            path = "/__admin/mappings",
            httpMethod = "POST",
            body = mappingJson,
            headers = mapOf("Content-Type" to "application/json"),
        )
        val output = ByteArrayOutputStream()
        handler.handleRequest(input, output, mockContext)

        val parsed = parseStreamingResponse(output.toByteArray())
        assertEquals(201, parsed.statusCode, "Failed to register mapping: ${parsed.body}")
        logger.info { "Mapping registered successfully" }
    }

    /**
     * Builds a mapping JSON for large bodies, escaping the body content properly.
     */
    private fun buildLargeBodyMapping(url: String, status: Int, body: String): String =
        """{"request":{"url":"$url","method":"GET"},"response":{"status":$status,"body":"$body","headers":{"Content-Type":"text/plain"}}}"""

    /**
     * Creates a valid API Gateway proxy request JSON as an InputStream.
     */
    private fun createApiGatewayRequest(
        path: String,
        httpMethod: String,
        headers: Map<String, String> = mapOf("Accept" to "application/json"),
        body: String? = null,
        isBase64Encoded: Boolean = false,
    ): ByteArrayInputStream {
        val headersJson = headers.entries.joinToString(",") { (k, v) ->
            """"${escapeJsonString(k)}":"${escapeJsonString(v)}""""
        }
        val bodyJson = if (body != null) {
            "\"${escapeJsonString(body)}\""
        } else {
            "null"
        }
        val json = """{
            "httpMethod": "$httpMethod",
            "path": "$path",
            "headers": {$headersJson},
            "queryStringParameters": {},
            "body": $bodyJson,
            "isBase64Encoded": $isBase64Encoded
        }"""
        return ByteArrayInputStream(json.toByteArray())
    }

    /**
     * Escapes special characters in a JSON string value.
     */
    private fun escapeJsonString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * Parses a streaming protocol response (metadata + 8 null bytes + body) into components.
     */
    private fun parseStreamingResponse(bytes: ByteArray): ParsedStreamingResponse {
        val delimiterIndex = findNullDelimiter(bytes)
        check(delimiterIndex >= 0) { "No 8 null byte delimiter found in streaming response" }

        val metadataBytes = bytes.copyOfRange(0, delimiterIndex)
        val metadataJson = String(metadataBytes, Charsets.UTF_8)
        val metadata = Json.parseToJsonElement(metadataJson).jsonObject

        val statusCode = metadata["statusCode"]?.jsonPrimitive?.int
            ?: error("Missing statusCode in metadata")

        val headersObj = metadata["headers"]?.jsonObject ?: JsonObject(emptyMap())
        val headers = headersObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }

        val bodyBytes = bytes.copyOfRange(
            delimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE,
            bytes.size,
        )
        val body = String(bodyBytes, Charsets.UTF_8)

        return ParsedStreamingResponse(statusCode, headers, body)
    }

    /**
     * Finds the index of the first occurrence of 8 consecutive null bytes in the byte array.
     */
    private fun findNullDelimiter(bytes: ByteArray): Int {
        val delimiterSize = StreamingProtocolWriter.NULL_DELIMITER_SIZE
        for (i in 0..bytes.size - delimiterSize) {
            var allNull = true
            for (j in 0 until delimiterSize) {
                if (bytes[i + j] != 0.toByte()) {
                    allNull = false
                    break
                }
            }
            if (allNull) return i
        }
        return -1
    }

    private data class ParsedStreamingResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: String,
    )
}
