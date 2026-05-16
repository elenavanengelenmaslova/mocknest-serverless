package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.github.tomakehurst.wiremock.WireMockServer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import nl.vintik.mocknest.infra.aws.runtime.streaming.S3ResponseStreamer
import org.crac.Core
import org.crac.Resource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.stream.Stream

/**
 * Property-based tests for streaming routing preservation (Property 6).
 *
 * Verifies that [StreamingRuntimeLambdaHandler] routes to the same use case
 * as the current [RuntimeLambdaHandler] for all routing branches:
 * - /__admin/health -> GetRuntimeHealth
 * - /__admin/... -> HandleAdminRequest
 * - /mocknest/... -> HandleClientRequest
 * - All other paths -> 404 response
 *
 * Feature: response-streaming, Property 6: Routing Preservation Under New Interface
 *
 * **Validates: Requirements 1.7, 2.8**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("Feature: response-streaming, Property 6: Routing Preservation Under New Interface")
class StreamingRoutingPropertyTest : KoinTest {

    private val mockHandleClientRequest: HandleClientRequest = mockk(relaxed = true)
    private val mockHandleAdminRequest: HandleAdminRequest = mockk(relaxed = true)
    private val mockGetRuntimeHealth: GetRuntimeHealth = mockk(relaxed = true)
    private val mockPrimingHook: RuntimePrimingHook = mockk(relaxed = true)
    private val mockReloadHook: RuntimeMappingReloadHook = mockk(relaxed = true)
    private val mockWireMockServer: WireMockServer = mockk(relaxed = true)
    private val mockS3ResponseStreamer: S3ResponseStreamer = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: StreamingRuntimeLambdaHandler

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
            single { mockWireMockServer }
            single { mockS3ResponseStreamer }
        }))
        handler = StreamingRuntimeLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
        unmockkAll()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockHandleClientRequest, mockHandleAdminRequest, mockGetRuntimeHealth, mockWireMockServer)
    }

    @ParameterizedTest(name = "Health path {0} delegates to GetRuntimeHealth")
    @MethodSource("healthCheckPaths")
    fun `Given health check path When routing Then delegates to GetRuntimeHealth`(
        path: String,
        httpMethod: String
    ) {
        val input = createRequestInputStream(path, httpMethod)
        val output = ByteArrayOutputStream()
        val healthResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"status":"healthy"}"""
        )
        every { mockGetRuntimeHealth.invoke() } returns healthResponse

        handler.handleRequest(input, output, mockContext)

        verify(exactly = 1) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
        assertStreamingResponseStatusCode(output, 200)
    }

    @ParameterizedTest(name = "Admin path {0} delegates to HandleAdminRequest")
    @MethodSource("adminPaths")
    fun `Given admin path When routing Then delegates to HandleAdminRequest`(
        path: String,
        httpMethod: String,
        expectedAdminPath: String
    ) {
        val input = createRequestInputStream(path, httpMethod)
        val output = ByteArrayOutputStream()
        val adminResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"mappings":[]}"""
        )
        every { mockHandleAdminRequest.invoke(any(), any()) } returns adminResponse

        handler.handleRequest(input, output, mockContext)

        verify(exactly = 1) { mockHandleAdminRequest.invoke(eq(expectedAdminPath), any()) }
        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
        assertStreamingResponseStatusCode(output, 200)
    }

    @ParameterizedTest(name = "Client path {0} delegates to HandleClientRequest")
    @MethodSource("clientPaths")
    fun `Given client path When routing Then delegates to HandleClientRequest`(
        path: String,
        httpMethod: String
    ) {
        val input = createRequestInputStream(path, httpMethod)
        val output = ByteArrayOutputStream()
        val clientResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            """{"result":"ok"}"""
        )
        every { mockHandleClientRequest.invoke(any()) } returns clientResponse
        // WireMock returns empty serve events so chunked dribble delay is not detected
        every { mockWireMockServer.allServeEvents } returns emptyList()

        handler.handleRequest(input, output, mockContext)

        verify(exactly = 1) { mockHandleClientRequest.invoke(any()) }
        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        assertStreamingResponseStatusCode(output, 200)
    }

    @ParameterizedTest(name = "Unknown path {0} returns 404")
    @MethodSource("unknownPaths")
    fun `Given unknown path When routing Then returns 404 without delegating`(
        path: String,
        httpMethod: String
    ) {
        val input = createRequestInputStream(path, httpMethod)
        val output = ByteArrayOutputStream()

        handler.handleRequest(input, output, mockContext)

        verify(exactly = 0) { mockGetRuntimeHealth.invoke() }
        verify(exactly = 0) { mockHandleAdminRequest.invoke(any(), any()) }
        verify(exactly = 0) { mockHandleClientRequest.invoke(any()) }
        assertStreamingResponseStatusCode(output, 404)
        val body = extractStreamingResponseBody(output)
        assertTrue(body.contains("not found"), "Expected 404 body to contain 'not found', got: $body")
    }

    /**
     * Creates a ByteArrayInputStream containing a valid API Gateway proxy request JSON
     * for the given path and HTTP method.
     */
    private fun createRequestInputStream(path: String, httpMethod: String): ByteArrayInputStream {
        val json = """
            {
                "httpMethod": "$httpMethod",
                "path": "$path",
                "headers": {"Accept": "application/json"},
                "multiValueHeaders": {"Accept": ["application/json"]},
                "queryStringParameters": null,
                "multiValueQueryStringParameters": null,
                "body": null,
                "isBase64Encoded": false
            }
        """.trimIndent()
        return ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
    }

    /**
     * Extracts the status code from a streaming protocol response.
     * The format is: metadata JSON + 8 null bytes + body.
     */
    private fun assertStreamingResponseStatusCode(output: ByteArrayOutputStream, expectedStatusCode: Int) {
        val bytes = output.toByteArray()
        val metadataEnd = findNullDelimiterIndex(bytes)
        assertTrue(metadataEnd > 0, "Expected to find null byte delimiter in streaming response")
        val metadataJson = String(bytes, 0, metadataEnd, Charsets.UTF_8)
        assertTrue(
            metadataJson.contains("\"statusCode\":$expectedStatusCode"),
            "Expected status code $expectedStatusCode in metadata, got: $metadataJson"
        )
    }

    /**
     * Extracts the body from a streaming protocol response (after the 8 null byte delimiter).
     */
    private fun extractStreamingResponseBody(output: ByteArrayOutputStream): String {
        val bytes = output.toByteArray()
        val delimiterIndex = findNullDelimiterIndex(bytes)
        if (delimiterIndex < 0) return ""
        val bodyStart = delimiterIndex + 8
        if (bodyStart >= bytes.size) return ""
        return String(bytes, bodyStart, bytes.size - bodyStart, Charsets.UTF_8)
    }

    /**
     * Finds the index of the 8 null byte delimiter in the streaming response bytes.
     */
    private fun findNullDelimiterIndex(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 8) {
            if (bytes[i] == 0.toByte() &&
                bytes[i + 1] == 0.toByte() &&
                bytes[i + 2] == 0.toByte() &&
                bytes[i + 3] == 0.toByte() &&
                bytes[i + 4] == 0.toByte() &&
                bytes[i + 5] == 0.toByte() &&
                bytes[i + 6] == 0.toByte() &&
                bytes[i + 7] == 0.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    companion object {
        @JvmStatic
        fun healthCheckPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/__admin/health", "GET"),
            Arguments.of("/__admin/health", "HEAD")
        )

        @JvmStatic
        fun adminPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/__admin/mappings", "GET", "mappings"),
            Arguments.of("/__admin/requests", "GET", "requests"),
            Arguments.of("/__admin/settings", "POST", "settings"),
            Arguments.of("/__admin/scenarios", "GET", "scenarios"),
            Arguments.of("/__admin/near-misses", "GET", "near-misses"),
            Arguments.of("/__admin/", "GET", ""),
            Arguments.of("/__admin/health/check", "GET", "health/check")
        )

        @JvmStatic
        fun clientPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/mocknest/api/users", "GET"),
            Arguments.of("/mocknest/api/orders/123", "GET"),
            Arguments.of("/mocknest/graphql", "POST"),
            Arguments.of("/mocknest/soap/service", "POST"),
            Arguments.of("/mocknest/", "GET"),
            Arguments.of("/mocknest/a/b/c/d", "GET")
        )

        @JvmStatic
        fun unknownPaths(): Stream<Arguments> = Stream.of(
            Arguments.of("/unknown", "GET"),
            Arguments.of("/", "GET"),
            Arguments.of("/other/path", "GET"),
            Arguments.of("/random", "GET"),
            Arguments.of("/api/users", "GET"),
            Arguments.of("/other", "POST")
        )
    }
}
