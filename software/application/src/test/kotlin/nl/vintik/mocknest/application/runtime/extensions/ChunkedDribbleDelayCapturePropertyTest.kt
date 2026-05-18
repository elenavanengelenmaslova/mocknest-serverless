package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Property-based tests for ChunkedDribbleDelayCapture transformer.
 *
 * Uses @ParameterizedTest with @MethodSource to validate universal properties
 * across diverse ResponseDefinition inputs.
 */
class ChunkedDribbleDelayCapturePropertyTest {

    private val serveEvent: ServeEvent = mockk(relaxed = true)
    private val transformer = ChunkedDribbleDelayCapture()

    @BeforeEach
    fun setUp() {
        ChunkedDribbleDelayCapture.clear()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(serveEvent)
        ChunkedDribbleDelayCapture.clear()
    }

    // =========================================================================
    // Property 1: Transformer captures bodyFileName and removes it from ResponseDefinition
    // Validates: Requirements 1.1, 1.2
    // =========================================================================

    /**
     * **Validates: Requirements 1.1, 1.2**
     *
     * For any response definition containing both a valid bodyFileName and a valid
     * chunkedDribbleDelay (numberOfChunks >= 2, totalDuration >= 0), the transformer
     * SHALL store the bodyFileName in the CapturedDribbleConfig thread-local AND return
     * a ResponseDefinition where bodyFileName is null and body is empty.
     */
    @ParameterizedTest
    @MethodSource("bodyFileNameWithDribbleProvider")
    @Tag("Feature: zero-memory-streaming, Property 1: Transformer captures bodyFileName and removes it from ResponseDefinition")
    fun `Given bodyFileName and dribble config When transform Then captures bodyFileName and returns modified ResponseDefinition`(
        testCase: BodyFileNameDribbleTestCase,
    ) {
        // Given
        val responseDefinition = ResponseDefinitionBuilder()
            .withStatus(testCase.statusCode)
            .withBodyFile(testCase.bodyFileName)
            .withChunkedDribbleDelay(testCase.numberOfChunks, testCase.totalDuration)
            .build()

        every { serveEvent.responseDefinition } returns responseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then - thread-local has the original bodyFileName
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNotNull(config, "Config should be stored for bodyFileName=${testCase.bodyFileName}")
        assertEquals(testCase.bodyFileName, config.bodyFileName)
        assertEquals(testCase.numberOfChunks, config.numberOfChunks)
        assertEquals(testCase.totalDuration.toLong(), config.totalDurationMs)

        // Then - returned ResponseDefinition has no bodyFileName and empty body
        assertNull(result.bodyFileName, "Returned ResponseDefinition should have null bodyFileName")
        assertTrue(
            result.body.isNullOrEmpty() || result.body == "",
            "Returned ResponseDefinition should have empty body",
        )
    }

    // =========================================================================
    // Property 2: Transformer preserves response unchanged for inline dribble mocks
    // Validates: Requirements 1.3, 4.2
    // =========================================================================

    /**
     * **Validates: Requirements 1.3, 4.2**
     *
     * For any response definition containing a valid chunkedDribbleDelay but no bodyFileName,
     * the transformer SHALL return the original response definition unchanged AND store only
     * numberOfChunks and totalDurationMs (with bodyFileName = null) in the thread-local.
     */
    @ParameterizedTest
    @MethodSource("inlineDribbleProvider")
    @Tag("Feature: zero-memory-streaming, Property 2: Transformer preserves response unchanged for inline dribble mocks")
    fun `Given inline body with dribble config When transform Then preserves response and stores dribble params only`(
        testCase: InlineDribbleTestCase,
    ) {
        // Given
        val responseDefinition = ResponseDefinitionBuilder()
            .withStatus(testCase.statusCode)
            .withBody(testCase.inlineBody)
            .withChunkedDribbleDelay(testCase.numberOfChunks, testCase.totalDuration)
            .build()

        every { serveEvent.responseDefinition } returns responseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then - returned ResponseDefinition is unchanged (same object)
        assertSame(responseDefinition, result, "Response should be returned unchanged for inline dribble")

        // Then - thread-local has dribble params but no bodyFileName
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNotNull(config, "Config should be stored for inline dribble")
        assertEquals(testCase.numberOfChunks, config.numberOfChunks)
        assertEquals(testCase.totalDuration.toLong(), config.totalDurationMs)
        assertNull(config.bodyFileName, "bodyFileName should be null for inline dribble")
    }

    // =========================================================================
    // Property 3: Transformer is no-op when no dribble is configured
    // Validates: Requirements 1.4, 5.1
    // =========================================================================

    /**
     * **Validates: Requirements 1.4, 5.1**
     *
     * For any response definition that does not contain a chunkedDribbleDelay, the transformer
     * SHALL return the response definition unchanged AND not store any configuration in the
     * thread-local, regardless of whether bodyFileName is present.
     */
    @ParameterizedTest
    @MethodSource("noDribbleProvider")
    @Tag("Feature: zero-memory-streaming, Property 3: Transformer is no-op when no dribble is configured")
    fun `Given response without chunkedDribbleDelay When transform Then returns unchanged and no thread-local stored`(
        testCase: NoDribbleTestCase,
    ) {
        // Given
        val builder = ResponseDefinitionBuilder().withStatus(testCase.statusCode)

        if (testCase.bodyFileName != null) {
            builder.withBodyFile(testCase.bodyFileName)
        }
        if (testCase.inlineBody != null) {
            builder.withBody(testCase.inlineBody)
        }

        val responseDefinition = builder.build()
        every { serveEvent.responseDefinition } returns responseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then - returned ResponseDefinition is the SAME object as the input
        assertSame(
            responseDefinition,
            result,
            "Response should be returned unchanged when no dribble: ${testCase.description}",
        )

        // Then - no thread-local config stored
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNull(
            config,
            "No config should be stored when no dribble is configured: ${testCase.description}",
        )
    }

    // =========================================================================
    // Test data classes
    // =========================================================================

    data class BodyFileNameDribbleTestCase(
        val bodyFileName: String,
        val numberOfChunks: Int,
        val totalDuration: Int,
        val statusCode: Int,
        val description: String,
    )

    data class InlineDribbleTestCase(
        val inlineBody: String,
        val numberOfChunks: Int,
        val totalDuration: Int,
        val statusCode: Int,
        val description: String,
    )

    data class NoDribbleTestCase(
        val bodyFileName: String?,
        val inlineBody: String?,
        val statusCode: Int,
        val description: String,
    )

    companion object {

        @JvmStatic
        fun bodyFileNameWithDribbleProvider(): Stream<BodyFileNameDribbleTestCase> = Stream.of(
            BodyFileNameDribbleTestCase("large-response.json", 5, 3000, 200, "JSON file with 5 chunks"),
            BodyFileNameDribbleTestCase("payload.xml", 10, 5000, 200, "XML file with 10 chunks"),
            BodyFileNameDribbleTestCase("binary-data.bin", 2, 1000, 200, "Binary file with minimum chunks"),
            BodyFileNameDribbleTestCase("nested/path/response.json", 3, 2000, 201, "Nested path with 201 status"),
            BodyFileNameDribbleTestCase("huge-payload.dat", 100, 60000, 200, "Large chunk count"),
            BodyFileNameDribbleTestCase("response-with-spaces in name.json", 4, 4000, 200, "Filename with spaces"),
            BodyFileNameDribbleTestCase("unicode-файл.json", 5, 2500, 200, "Unicode filename"),
            BodyFileNameDribbleTestCase("a.txt", 2, 0, 200, "Minimum duration (0ms)"),
            BodyFileNameDribbleTestCase("deep/nested/dir/file.json", 7, 7000, 404, "404 status with dribble"),
            BodyFileNameDribbleTestCase("error-response.xml", 3, 1500, 500, "500 status with dribble"),
            BodyFileNameDribbleTestCase("image.png", 20, 10000, 200, "Image file with many chunks"),
            BodyFileNameDribbleTestCase("very-long-filename-that-represents-a-complex-api-response-body.json", 5, 5000, 200, "Long filename"),
            BodyFileNameDribbleTestCase("response.html", 2, 500, 302, "302 redirect status"),
            BodyFileNameDribbleTestCase("data.csv", 15, 15000, 200, "CSV file with 15 chunks"),
            BodyFileNameDribbleTestCase("minimal.txt", 2, 100, 204, "204 No Content status"),
        )

        @JvmStatic
        fun inlineDribbleProvider(): Stream<InlineDribbleTestCase> = Stream.of(
            InlineDribbleTestCase("{\"status\":\"ok\"}", 3, 2000, 200, "Simple JSON body"),
            InlineDribbleTestCase("<response><status>ok</status></response>", 5, 3000, 200, "XML body"),
            InlineDribbleTestCase("plain text response", 2, 1000, 200, "Plain text body"),
            InlineDribbleTestCase("", 3, 1500, 200, "Empty string body"),
            InlineDribbleTestCase("a", 2, 500, 200, "Single character body"),
            InlineDribbleTestCase("{\"data\":{\"nested\":{\"deep\":true}}}", 4, 4000, 200, "Nested JSON"),
            InlineDribbleTestCase("x".repeat(1000), 10, 5000, 200, "1KB inline body"),
            InlineDribbleTestCase("{\"error\":\"not found\"}", 2, 1000, 404, "404 error response"),
            InlineDribbleTestCase("{\"error\":\"internal\"}", 3, 2000, 500, "500 error response"),
            InlineDribbleTestCase("Line1\nLine2\nLine3", 2, 1000, 200, "Multi-line body"),
            InlineDribbleTestCase("{\"items\":[1,2,3,4,5]}", 5, 2500, 201, "201 with array body"),
            InlineDribbleTestCase("Special chars: <>&\"'", 2, 800, 200, "Special characters"),
            InlineDribbleTestCase("\t\n\r", 2, 600, 200, "Whitespace-only body"),
            InlineDribbleTestCase("Unicode: こんにちは世界", 3, 1500, 200, "Unicode content"),
            InlineDribbleTestCase("{\"key\":\"value\",\"number\":42,\"bool\":true}", 4, 3000, 200, "Mixed JSON types"),
        )

        @JvmStatic
        fun noDribbleProvider(): Stream<NoDribbleTestCase> = Stream.of(
            // With bodyFileName only (various file names)
            NoDribbleTestCase("large-response.json", null, 200, "JSON bodyFileName, no dribble"),
            NoDribbleTestCase("payload.xml", null, 200, "XML bodyFileName, no dribble"),
            NoDribbleTestCase("binary-data.bin", null, 200, "Binary bodyFileName, no dribble"),
            NoDribbleTestCase("nested/path/file.json", null, 201, "Nested path bodyFileName, 201 status"),
            NoDribbleTestCase("image.png", null, 200, "Image bodyFileName, no dribble"),
            NoDribbleTestCase("unicode-файл.json", null, 200, "Unicode bodyFileName, no dribble"),
            // With inline body only (various content)
            NoDribbleTestCase(null, "{\"status\":\"ok\"}", 200, "JSON inline body, no dribble"),
            NoDribbleTestCase(null, "<xml>content</xml>", 200, "XML inline body, no dribble"),
            NoDribbleTestCase(null, "plain text", 200, "Plain text inline body, no dribble"),
            NoDribbleTestCase(null, "x".repeat(5000), 200, "Large inline body, no dribble"),
            NoDribbleTestCase(null, "", 200, "Empty inline body, no dribble"),
            // With various status codes
            NoDribbleTestCase("error.json", null, 404, "404 status with bodyFileName, no dribble"),
            NoDribbleTestCase(null, "{\"error\":\"server\"}", 500, "500 status with inline body, no dribble"),
            NoDribbleTestCase("redirect.html", null, 302, "302 status with bodyFileName, no dribble"),
            NoDribbleTestCase(null, "created", 201, "201 status with inline body, no dribble"),
            // With neither bodyFileName nor inline body (empty response)
            NoDribbleTestCase(null, null, 200, "Empty response, 200 status, no dribble"),
            NoDribbleTestCase(null, null, 204, "Empty response, 204 status, no dribble"),
            NoDribbleTestCase(null, null, 404, "Empty response, 404 status, no dribble"),
            NoDribbleTestCase(null, null, 500, "Empty response, 500 status, no dribble"),
        )
    }
}
