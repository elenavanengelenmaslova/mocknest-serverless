package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.github.tomakehurst.wiremock.WireMockServer
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.runtime.extensions.CapturedDribbleConfig
import nl.vintik.mocknest.application.runtime.extensions.ChunkedDribbleDelayCapture
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.streaming.StreamingProtocolWriter
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
import java.io.InputStream
import java.util.stream.Stream

/**
 * Property-based tests for [StreamingRuntimeLambdaHandler] S3 routing behavior.
 *
 * **Property 4: Handler routes to S3 streaming when bodyFileName is present**
 *
 * For any CapturedDribbleConfig containing a non-null bodyFileName, the handler SHALL
 * use the S3ResponseStreamer to stream the response body rather than using the in-memory
 * body bytes, and SHALL write metadata+delimiter before the chunked body content.
 *
 * **Validates: Requirements 2.1, 2.2**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("Feature: zero-memory-streaming, Property 4: Handler routes to S3 streaming when bodyFileName is present")
class StreamingRuntimeLambdaHandlerPropertyTest : KoinTest {

    private val mockHandleClientRequest: HandleClientRequest = mockk(relaxed = true)
    private val mockHandleAdminRequest: HandleAdminRequest = mockk(relaxed = true)
    private val mockGetRuntimeHealth: GetRuntimeHealth = mockk(relaxed = true)
    private val mockWireMockServer: WireMockServer = mockk(relaxed = true)
    private val mockS3ResponseStreamer: S3ResponseStreamer = mockk(relaxed = true)
    private val mockPrimingHook: RuntimePrimingHook = mockk(relaxed = true)
    private val mockReloadHook: RuntimeMappingReloadHook = mockk(relaxed = true)
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
            single<WireMockServer> { mockWireMockServer }
            single { mockS3ResponseStreamer }
            single { mockPrimingHook }
            single { mockReloadHook }
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
        clearMocks(
            mockHandleClientRequest,
            mockHandleAdminRequest,
            mockGetRuntimeHealth,
            mockWireMockServer,
            mockS3ResponseStreamer,
        )
        ChunkedDribbleDelayCapture.clear()
    }

    // =========================================================================
    // Property 4: Handler routes to S3 streaming when bodyFileName is present
    // Validates: Requirements 2.1, 2.2
    // =========================================================================

    /**
     * **Validates: Requirements 2.1, 2.2**
     *
     * For any CapturedDribbleConfig containing a non-null bodyFileName, the handler SHALL:
     * 1. Call S3ResponseStreamer.getContentLength with the correct S3 key (__files/{bodyFileName})
     * 2. Call S3ResponseStreamer.streamWithConsumer with the correct S3 key
     * 3. Write metadata+delimiter before the body content in the output
     */
    @ParameterizedTest(name = "Property 4: bodyFileName={0}, chunks={1}, durationMs={2}")
    @MethodSource("s3RoutingTestCases")
    @Tag("Feature: zero-memory-streaming, Property 4: Handler routes to S3 streaming when bodyFileName is present")
    fun `Given CapturedDribbleConfig with bodyFileName When handling client request Then routes to S3 streaming with metadata before body`(
        bodyFileName: String,
        numberOfChunks: Int,
        totalDurationMs: Long,
    ) {
        // Given
        val input = createApiGatewayRequest("/mocknest/api/resource", "GET")
        val output = ByteArrayOutputStream()
        val s3Key = "__files/$bodyFileName"
        val bodyContent = "s3-body-content-for-$bodyFileName"
        val bodyBytes = bodyContent.toByteArray(Charsets.UTF_8)
        val clientResponse = HttpResponse(
            HttpStatusCode.OK,
            mapOf("Content-Type" to listOf("application/json")),
            "", // empty body because transformer intercepted bodyFileName
        )

        every { mockHandleClientRequest.invoke(any()) } answers {
            ChunkedDribbleDelayCapture.setForTest(
                CapturedDribbleConfig(
                    numberOfChunks = numberOfChunks,
                    totalDurationMs = totalDurationMs,
                    bodyFileName = bodyFileName,
                )
            )
            clientResponse
        }

        coEvery { mockS3ResponseStreamer.getContentLength(s3Key) } returns bodyBytes.size.toLong()
        coEvery {
            mockS3ResponseStreamer.streamWithConsumer(s3Key, any())
        } coAnswers {
            val consumer = secondArg<suspend (InputStream, Long) -> Unit>()
            consumer(ByteArrayInputStream(bodyBytes), bodyBytes.size.toLong())
            true
        }

        // When
        handler.handleRequest(input, output, mockContext)

        // Then — S3ResponseStreamer is called with correct S3 key
        coVerify(exactly = 1) { mockS3ResponseStreamer.getContentLength(s3Key) }
        coVerify(exactly = 1) { mockS3ResponseStreamer.streamWithConsumer(s3Key, any()) }

        // Then — output contains metadata+delimiter before body content
        val bytes = output.toByteArray()
        val delimiterIndex = findNullDelimiterIndex(bytes)
        assertTrue(delimiterIndex > 0) {
            "Expected null byte delimiter in streaming response for bodyFileName=$bodyFileName"
        }

        // Verify metadata contains correct status code
        val metadataJson = String(bytes, 0, delimiterIndex, Charsets.UTF_8)
        val metadata = Json.parseToJsonElement(metadataJson).jsonObject
        assertEquals(200, metadata["statusCode"]?.jsonPrimitive?.int) {
            "Expected statusCode 200 in metadata for bodyFileName=$bodyFileName"
        }

        // Verify body content follows after delimiter
        val bodyStart = delimiterIndex + StreamingProtocolWriter.NULL_DELIMITER_SIZE
        val outputBody = String(bytes, bodyStart, bytes.size - bodyStart, Charsets.UTF_8)
        assertEquals(bodyContent, outputBody) {
            "Expected S3 body content after delimiter for bodyFileName=$bodyFileName"
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Creates a valid API Gateway proxy request JSON as an InputStream.
     */
    private fun createApiGatewayRequest(
        path: String,
        httpMethod: String,
    ): ByteArrayInputStream {
        val json = """{
            "httpMethod": "$httpMethod",
            "path": "$path",
            "headers": {"Accept": "application/json"},
            "queryStringParameters": {},
            "body": null,
            "isBase64Encoded": false
        }"""
        return ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
    }

    /**
     * Finds the index of the 8 null byte delimiter in the streaming response bytes.
     */
    private fun findNullDelimiterIndex(bytes: ByteArray): Int {
        val size = StreamingProtocolWriter.NULL_DELIMITER_SIZE
        for (i in 0..bytes.size - size) {
            var allNull = true
            for (k in 0 until size) {
                if (bytes[i + k] != 0.toByte()) {
                    allNull = false
                    break
                }
            }
            if (allNull) return i
        }
        return -1
    }

    // =========================================================================
    // Test data provider
    // =========================================================================

    companion object {

        @JvmStatic
        fun s3RoutingTestCases(): Stream<Arguments> = Stream.of(
            // Various bodyFileName values with diverse dribble configs
            // totalDurationMs kept small to avoid test timeouts (routing is the property under test)
            Arguments.of("large-response.json", 5, 0L),
            Arguments.of("payload.xml", 10, 0L),
            Arguments.of("binary-data.bin", 2, 50L),
            Arguments.of("nested/path/response.json", 3, 30L),
            Arguments.of("huge-payload.dat", 100, 0L),
            Arguments.of("response-with-spaces in name.json", 4, 40L),
            Arguments.of("unicode-файл.json", 5, 0L),
            Arguments.of("a.txt", 2, 20L),
            Arguments.of("deep/nested/dir/file.json", 7, 0L),
            Arguments.of("error-response.xml", 3, 30L),
            Arguments.of("image.png", 20, 0L),
            Arguments.of("very-long-filename-that-represents-a-complex-api-response-body.json", 5, 0L),
            Arguments.of("data.csv", 15, 0L),
            Arguments.of("minimal.txt", 2, 0L),
            Arguments.of("response.html", 8, 0L),
        )
    }
}
