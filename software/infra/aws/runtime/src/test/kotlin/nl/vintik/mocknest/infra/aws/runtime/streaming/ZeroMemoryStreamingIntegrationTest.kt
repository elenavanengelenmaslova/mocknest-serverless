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
import org.junit.jupiter.api.Nested
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

private const val TEST_BUCKET_NAME = "zero-memory-streaming-test-bucket"
private const val TEST_REGION = "us-east-1"

/**
 * Integration test for zero-memory S3 streaming with chunked dribble delay.
 *
 * Uses a LocalStack container for S3 and a real WireMock server to validate:
 * - Persistent mock with bodyFileName + chunkedDribbleDelay → handler streams from S3 byte-for-byte
 * - Small body (<1MB) with dribble → correct delivery
 * - Medium body (~5MB) with dribble → correct delivery
 * - Progressive delivery timing (elapsed time >= 80% of totalDuration)
 * - Inline body dribble mock (persistent: false) still works unchanged
 *
 * **Validates: Requirements 7.1, 7.2, 7.3, 7.4**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZeroMemoryStreamingIntegrationTest : KoinTest {

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

        envVars.set("MOCKNEST_S3_BUCKET_NAME", TEST_BUCKET_NAME)
        envVars.set("AWS_REGION", TEST_REGION)
        envVars.set("AWS_DEFAULT_REGION", TEST_REGION)
        envVars.set("MOCKNEST_WEBHOOK_QUEUE_URL", "")
        envVars.set("MOCKNEST_SENSITIVE_HEADERS", "Authorization,X-Api-Key")
        envVars.setup()

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

        mockkStatic(Core::class)
        val mockCracContext: org.crac.Context<Resource> = mockk(relaxed = true)
        every { Core.getGlobalContext() } returns mockCracContext

        val testCoreModule = module {
            single { mapper }
            single { testS3Client }
        }

        KoinBootstrap.init(listOf(testCoreModule, runtimeModule()))
        handler = StreamingRuntimeLambdaHandler()
    }

    @AfterEach
    fun tearDown() {
        val resetInput = createApiGatewayRequest("/__admin/mappings", "DELETE")
        val resetOutput = ByteArrayOutputStream()
        handler.handleRequest(resetInput, resetOutput, mockContext)

        val resetResponse = parseStreamingResponse(resetOutput.toByteArray())
        check(resetResponse.statusCode == 200) {
            "tearDown: DELETE /__admin/mappings failed with status ${resetResponse.statusCode}: ${resetResponse.body}"
        }

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

    @Nested
    inner class S3StreamingWithChunkedDribbleDelay {

        @Test
        fun `Given persistent mock with known body and chunkedDribbleDelay When invoked via handler Then complete body received byte-for-byte`() {
            // Given - register a persistent mock with body + chunkedDribbleDelay
            // The NormalizeMappingBodyFilter will externalize the body to S3 (bodyFileName)
            // The ChunkedDribbleDelayCapture transformer will intercept bodyFileName + dribble
            val expectedBody = "Hello, this is a test body for S3 streaming with chunked dribble delay!"
            val mappingJson = """
                {
                    "request": { "url": "/api/s3-dribble", "method": "GET" },
                    "response": {
                        "status": 200,
                        "body": "${escapeJsonString(expectedBody)}",
                        "headers": { "Content-Type": "text/plain" },
                        "chunkedDribbleDelay": {
                            "numberOfChunks": 3,
                            "totalDuration": 600
                        }
                    }
                }
            """.trimIndent()
            registerMapping(mappingJson)

            // When - invoke the mock endpoint via streaming handler
            val input = createApiGatewayRequest("/mocknest/api/s3-dribble", "GET")
            val output = ByteArrayOutputStream()
            handler.handleRequest(input, output, mockContext)

            // Then - verify complete body received byte-for-byte
            val parsed = parseStreamingResponse(output.toByteArray())
            assertEquals(200, parsed.statusCode)
            assertEquals(expectedBody, parsed.body)
        }

        @Test
        fun `Given small body less than 1MB with chunkedDribbleDelay When invoked Then correct delivery`() {
            // Given - small body (~500KB) with chunkedDribbleDelay on a persistent mock
            val expectedBody = "X".repeat(500 * 1024) // 500KB
            val mappingJson = buildPersistentDribbleMapping(
                url = "/api/small-s3-dribble",
                body = expectedBody,
                numberOfChunks = 5,
                totalDuration = 1000,
            )
            registerMapping(mappingJson)

            // When
            val input = createApiGatewayRequest("/mocknest/api/small-s3-dribble", "GET")
            val output = ByteArrayOutputStream()
            handler.handleRequest(input, output, mockContext)

            // Then - verify correct delivery
            val parsed = parseStreamingResponse(output.toByteArray())
            assertEquals(200, parsed.statusCode)
            assertEquals(expectedBody.length, parsed.body.length)
            assertEquals(expectedBody, parsed.body)
        }

        @Test
        fun `Given medium body around 5MB with chunkedDribbleDelay When invoked Then correct delivery`() {
            // Given - medium body (~5MB) with chunkedDribbleDelay on a persistent mock
            val expectedBody = "Y".repeat(5 * 1024 * 1024) // 5MB
            val mappingJson = buildPersistentDribbleMapping(
                url = "/api/medium-s3-dribble",
                body = expectedBody,
                numberOfChunks = 5,
                totalDuration = 1000,
            )
            registerMapping(mappingJson)

            // When
            val input = createApiGatewayRequest("/mocknest/api/medium-s3-dribble", "GET")
            val output = ByteArrayOutputStream()
            handler.handleRequest(input, output, mockContext)

            // Then - verify correct delivery byte-for-byte
            val parsed = parseStreamingResponse(output.toByteArray())
            assertEquals(200, parsed.statusCode)
            assertEquals(expectedBody.length, parsed.body.length)
            assertEquals(expectedBody, parsed.body)
        }

        @Test
        fun `Given persistent mock with chunkedDribbleDelay When invoked Then delivery timing is progressive`() {
            // Given - persistent mock with body + chunkedDribbleDelay with measurable duration
            // Use 10 chunks with 3000ms total so delay between chunks = 300ms
            // Expected elapsed >= 80% of 3000ms = 2400ms
            val expectedBody = "Z".repeat(10 * 1024) // 10KB - small enough to not dominate timing
            val mappingJson = buildPersistentDribbleMapping(
                url = "/api/timed-s3-dribble",
                body = expectedBody,
                numberOfChunks = 10,
                totalDuration = 3000,
            )
            registerMapping(mappingJson)

            // When - invoke and measure delivery time
            val input = createApiGatewayRequest("/mocknest/api/timed-s3-dribble", "GET")
            val output = ByteArrayOutputStream()
            val startTime = System.currentTimeMillis()
            handler.handleRequest(input, output, mockContext)
            val elapsedTime = System.currentTimeMillis() - startTime

            // Then - verify body is complete
            val parsed = parseStreamingResponse(output.toByteArray())
            assertEquals(200, parsed.statusCode)
            assertEquals(expectedBody, parsed.body)

            // Verify delivery timing is progressive (elapsed time >= 80% of totalDuration)
            val minimumExpectedTime = (3000 * 0.80).toLong() // 2400ms
            logger.info { "S3 dribble delivery time: ${elapsedTime}ms (minimum expected: ${minimumExpectedTime}ms)" }
            assertTrue(
                elapsedTime >= minimumExpectedTime,
                "Delivery time ${elapsedTime}ms should be at least 80% of 3000ms (${minimumExpectedTime}ms)"
            )
        }

        @Test
        fun `Given inline body dribble mock with persistent false When invoked Then still works unchanged`() {
            // Given - inline body mock (persistent: false) with chunkedDribbleDelay
            // This should NOT go through the S3 streaming path
            val sseBody = "data: event1\\ndata: event2\\ndata: event3\\n"
            val expectedBody = "data: event1\ndata: event2\ndata: event3\n"
            val mappingJson = """
                {
                    "persistent": false,
                    "request": { "url": "/api/inline-dribble", "method": "GET" },
                    "response": {
                        "status": 200,
                        "body": "$sseBody",
                        "headers": { "Content-Type": "text/event-stream" },
                        "chunkedDribbleDelay": {
                            "numberOfChunks": 3,
                            "totalDuration": 600
                        }
                    }
                }
            """.trimIndent()
            registerMapping(mappingJson)

            // When
            val input = createApiGatewayRequest("/mocknest/api/inline-dribble", "GET")
            val output = ByteArrayOutputStream()
            handler.handleRequest(input, output, mockContext)

            // Then - verify inline body dribble still works correctly
            val parsed = parseStreamingResponse(output.toByteArray())
            assertEquals(200, parsed.statusCode)
            assertEquals(expectedBody, parsed.body)
        }
    }

    // --- Helper methods ---

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

    private fun buildPersistentDribbleMapping(
        url: String,
        body: String,
        numberOfChunks: Int,
        totalDuration: Int,
    ): String {
        val mapping = mapOf(
            "request" to mapOf("url" to url, "method" to "GET"),
            "response" to mapOf(
                "status" to numberOfChunks.let { 200 },
                "body" to body,
                "headers" to mapOf("Content-Type" to "text/plain"),
                "chunkedDribbleDelay" to mapOf(
                    "numberOfChunks" to numberOfChunks,
                    "totalDuration" to totalDuration,
                ),
            ),
        )
        return mapper.writeValueAsString(mapping)
    }

    private fun createApiGatewayRequest(
        path: String,
        httpMethod: String,
        headers: Map<String, String> = mapOf("Accept" to "application/json"),
        body: String? = null,
        isBase64Encoded: Boolean = false,
        queryParameters: Map<String, String> = emptyMap(),
    ): ByteArrayInputStream {
        val headersJson = headers.entries.joinToString(",") { (k, v) ->
            """"${escapeJsonString(k)}":"${escapeJsonString(v)}""""
        }
        val queryParamsJson = if (queryParameters.isEmpty()) {
            "{}"
        } else {
            queryParameters.entries.joinToString(",", "{", "}") { (k, v) ->
                """"${escapeJsonString(k)}":"${escapeJsonString(v)}""""
            }
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
            "queryStringParameters": $queryParamsJson,
            "body": $bodyJson,
            "isBase64Encoded": $isBase64Encoded
        }"""
        return ByteArrayInputStream(json.toByteArray())
    }

    private fun escapeJsonString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

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
