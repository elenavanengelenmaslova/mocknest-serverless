package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.Context
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.streaming.StreamingProtocolWriter
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamingGenerationLambdaHandlerTest : KoinTest {

    private val mockHandleAIGenerationRequest: HandleAIGenerationRequest = mockk(relaxed = true)
    private val mockGetAIHealth: GetAIHealth = mockk(relaxed = true)
    private val mockPrimingHook: GenerationPrimingHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: StreamingGenerationLambdaHandler

    @BeforeAll
    fun setUp() {
        KoinBootstrap.init(listOf(module {
            single<HandleAIGenerationRequest> { mockHandleAIGenerationRequest }
            single<GetAIHealth> { mockGetAIHealth }
            single { mockPrimingHook }
        }))
        handler = StreamingGenerationLambdaHandler()
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
    inner class Routing {

        @Test
        fun `Given AI health request When routing Then should call GetAIHealth`() {
            // Given
            val input = createApiGatewayRequest("/ai/generation/health", "GET")
            val output = ByteArrayOutputStream()

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status":"healthy"}"""
            )
            every { mockGetAIHealth.invoke() } returns expectedResponse

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 1) { mockGetAIHealth.invoke() }
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(200, metadata["statusCode"]!!.jsonPrimitive.int)
            assertEquals("""{"status":"healthy"}""", body)
        }

        @Test
        fun `Given AI generation request When routing Then should call HandleAIGenerationRequest with correct path`() {
            // Given
            val requestBody = """{"spec":"openapi spec"}"""
            val input = createApiGatewayRequest("/ai/generation/from-spec", "POST", body = requestBody)
            val output = ByteArrayOutputStream()

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status":"generated"}"""
            )
            every { mockHandleAIGenerationRequest.invoke(any(), any()) } returns expectedResponse

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 1) {
                mockHandleAIGenerationRequest.invoke(
                    "/from-spec",
                    match { request ->
                        request.method == HttpMethod.POST &&
                            request.path == "/from-spec" &&
                            request.body == requestBody
                    }
                )
            }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(200, metadata["statusCode"]!!.jsonPrimitive.int)
            assertEquals("""{"status":"generated"}""", body)
        }

        @Test
        fun `Given unknown path When routing Then should return 404 streaming response`() {
            // Given
            val input = createApiGatewayRequest("/unknown/path", "GET")
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 0) { mockGetAIHealth.invoke() }
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(404, metadata["statusCode"]!!.jsonPrimitive.int)
            assertNotNull(body)
            assertTrue(body.contains("/unknown/path"))
            assertTrue(body.contains("not found"))
        }

        @Test
        fun `Given admin path When routing Then should return 404 not found`() {
            // Given
            val input = createApiGatewayRequest("/__admin/mappings", "GET")
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }
            verify(exactly = 0) { mockGetAIHealth.invoke() }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(404, metadata["statusCode"]!!.jsonPrimitive.int)
            assertTrue(body.contains("not found"))
        }

        @Test
        fun `Given mocknest path When routing Then should return 404 not found`() {
            // Given
            val input = createApiGatewayRequest("/mocknest/api/users", "GET")
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }
            verify(exactly = 0) { mockGetAIHealth.invoke() }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(404, metadata["statusCode"]!!.jsonPrimitive.int)
            assertTrue(body.contains("not found"))
        }
    }

    @Nested
    inner class ParseFailure {

        @Test
        fun `Given malformed JSON input When handling request Then should return 400 streaming response`() {
            // Given
            val input = ByteArrayInputStream("not valid json {{{".toByteArray())
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            verify(exactly = 0) { mockGetAIHealth.invoke() }
            verify(exactly = 0) { mockHandleAIGenerationRequest.invoke(any(), any()) }

            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(400, metadata["statusCode"]!!.jsonPrimitive.int)
            assertNotNull(body)
            assertTrue(body.contains("error"))
        }

        @Test
        fun `Given JSON missing httpMethod When handling request Then should return 400 streaming response`() {
            // Given
            val json = """{"path":"/ai/generation/health"}"""
            val input = ByteArrayInputStream(json.toByteArray())
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(400, metadata["statusCode"]!!.jsonPrimitive.int)
            assertTrue(body.contains("error"))
        }

        @Test
        fun `Given JSON missing path When handling request Then should return 400 streaming response`() {
            // Given
            val json = """{"httpMethod":"GET"}"""
            val input = ByteArrayInputStream(json.toByteArray())
            val output = ByteArrayOutputStream()

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(400, metadata["statusCode"]!!.jsonPrimitive.int)
            assertTrue(body.contains("error"))
        }
    }

    @Nested
    inner class SnapStartInitialization {

        @Test
        fun `Given handler instantiation When Koin is initialized Then priming hook should be invoked`() {
            // The priming hook is called in the companion object init block.
            // Since we set up Koin with a mock priming hook before creating the handler,
            // we verify the mock was called during setUp.
            verify(exactly = 1) { mockPrimingHook.onApplicationReady() }
        }
    }

    @Nested
    inner class StreamingProtocol {

        @Test
        fun `Given successful response When writing Then should follow streaming protocol format`() {
            // Given
            val input = createApiGatewayRequest("/ai/generation/health", "GET")
            val output = ByteArrayOutputStream()

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf("Content-Type" to listOf("application/json")),
                """{"status":"healthy"}"""
            )
            every { mockGetAIHealth.invoke() } returns expectedResponse

            // When
            handler.handleRequest(input, output, mockContext)

            // Then - verify streaming protocol: metadata JSON + 8 null bytes + body
            val bytes = output.toByteArray()
            val nullDelimiterIndex = findNullDelimiter(bytes)
            assertTrue(nullDelimiterIndex >= 0, "Should contain 8 null byte delimiter")

            // Verify metadata is valid JSON before the delimiter
            val metadataBytes = bytes.copyOfRange(0, nullDelimiterIndex)
            val metadataJson = String(metadataBytes, Charsets.UTF_8)
            val metadata = Json.parseToJsonElement(metadataJson).jsonObject
            assertEquals(200, metadata["statusCode"]!!.jsonPrimitive.int)

            // Verify body after the delimiter
            val bodyBytes = bytes.copyOfRange(
                nullDelimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE,
                bytes.size
            )
            val body = String(bodyBytes, Charsets.UTF_8)
            assertEquals("""{"status":"healthy"}""", body)
        }

        @Test
        fun `Given response with headers When writing Then should include headers in metadata`() {
            // Given
            val input = createApiGatewayRequest("/ai/generation/from-spec", "POST", body = """{"spec":"test"}""")
            val output = ByteArrayOutputStream()

            val expectedResponse = HttpResponse(
                HttpStatusCode.OK,
                mapOf(
                    "Content-Type" to listOf("application/json"),
                    "X-Generation-Id" to listOf("gen-123")
                ),
                """{"result":"success"}"""
            )
            every { mockHandleAIGenerationRequest.invoke(any(), any()) } returns expectedResponse

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            val (metadata, body) = parseStreamingResponse(output.toByteArray())
            assertEquals(200, metadata["statusCode"]!!.jsonPrimitive.int)

            val headers = metadata["headers"]!!.jsonObject
            assertEquals("application/json", headers["Content-Type"]!!.jsonPrimitive.content)
            assertEquals("gen-123", headers["X-Generation-Id"]!!.jsonPrimitive.content)
            assertEquals("""{"result":"success"}""", body)
        }

        @Test
        fun `Given response with no body When writing Then should write metadata and delimiter only`() {
            // Given
            val input = createApiGatewayRequest("/ai/generation/from-spec", "DELETE")
            val output = ByteArrayOutputStream()

            val expectedResponse = HttpResponse(HttpStatusCode(204))
            every { mockHandleAIGenerationRequest.invoke(any(), any()) } returns expectedResponse

            // When
            handler.handleRequest(input, output, mockContext)

            // Then
            val bytes = output.toByteArray()
            val nullDelimiterIndex = findNullDelimiter(bytes)
            assertTrue(nullDelimiterIndex >= 0, "Should contain 8 null byte delimiter")

            // Body after delimiter should be empty
            val bodyBytes = bytes.copyOfRange(
                nullDelimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE,
                bytes.size
            )
            assertEquals(0, bodyBytes.size)
        }
    }

    // --- Helper functions ---

    private fun createApiGatewayRequest(
        path: String,
        httpMethod: String,
        body: String? = null,
        queryParams: Map<String, String>? = null,
        headers: Map<String, String>? = mapOf("Accept" to "application/json")
    ): ByteArrayInputStream {
        val jsonParts = mutableListOf<String>()
        jsonParts.add(""""httpMethod":"$httpMethod"""")
        jsonParts.add(""""path":"$path"""")

        headers?.let {
            val headersJson = it.entries.joinToString(",") { (k, v) -> """"$k":"$v"""" }
            jsonParts.add(""""headers":{$headersJson}""")
        }

        queryParams?.let {
            val paramsJson = it.entries.joinToString(",") { (k, v) -> """"$k":"$v"""" }
            jsonParts.add(""""queryStringParameters":{$paramsJson}""")
        }

        body?.let {
            val escapedBody = it.replace("\"", "\\\"")
            jsonParts.add(""""body":"$escapedBody"""")
        }

        jsonParts.add(""""isBase64Encoded":false""")

        val json = "{${jsonParts.joinToString(",")}}"
        return ByteArrayInputStream(json.toByteArray())
    }

    /**
     * Parses a streaming protocol response into metadata JSON object and body string.
     */
    private fun parseStreamingResponse(bytes: ByteArray): Pair<JsonObject, String> {
        val nullDelimiterIndex = findNullDelimiter(bytes)
        check(nullDelimiterIndex >= 0) { "No null delimiter found in streaming response" }

        val metadataBytes = bytes.copyOfRange(0, nullDelimiterIndex)
        val metadataJson = Json.parseToJsonElement(String(metadataBytes, Charsets.UTF_8)).jsonObject

        val bodyBytes = bytes.copyOfRange(
            nullDelimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE,
            bytes.size
        )
        val body = String(bodyBytes, Charsets.UTF_8)

        return metadataJson to body
    }

    /**
     * Finds the index of the 8 null byte delimiter in the byte array.
     * Returns -1 if not found.
     */
    private fun findNullDelimiter(bytes: ByteArray): Int {
        val nullDelimiter = ByteArray(StreamingProtocolWriter.NULL_DELIMITER_SIZE)
        for (i in 0..bytes.size - StreamingProtocolWriter.NULL_DELIMITER_SIZE) {
            if (bytes.copyOfRange(i, i + StreamingProtocolWriter.NULL_DELIMITER_SIZE)
                    .contentEquals(nullDelimiter)
            ) {
                return i
            }
        }
        return -1
    }
}
