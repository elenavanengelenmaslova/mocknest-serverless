package nl.vintik.mocknest.infra.aws.runtime.streaming

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.util.stream.Stream

/**
 * Property 3: Chunked Delivery Preserves Body
 *
 * For any response body of 1 to 100,000 bytes and any numberOfChunks in 2–1000
 * (where numberOfChunks ≤ body size), splitting the body via
 * ChunkedResponseWriter.calculateChunkSizes and concatenating the resulting chunks
 * SHALL produce a byte sequence identical to the original body, with exactly
 * numberOfChunks chunks produced.
 *
 * **Validates: Requirements 4.1, 4.6**
 */
@Tag("Feature: response-streaming, Property 3: Chunked Delivery Preserves Body")
class ChunkedDeliveryPropertyTest {

    private val writer = ChunkedResponseWriter()

    @ParameterizedTest(name = "{0}")
    @MethodSource("chunkedDeliveryTestCases")
    fun `Given body and numberOfChunks When writeChunked Then concatenated output equals original body`(
        description: String,
        bodySize: Int,
        numberOfChunks: Int,
    ) = runTest {
        // Given — generate deterministic body with repeating byte pattern
        val body = ByteArray(bodySize) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()

        // When — write chunked with totalDuration=0 to avoid delays in tests
        writer.writeChunked(body, numberOfChunks, 0, output)

        // Then — concatenating all chunks produces byte-identical body
        assertArrayEquals(
            body,
            output.toByteArray(),
            "Concatenated chunks must equal original body for: $description"
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chunkedDeliveryTestCases")
    fun `Given body and numberOfChunks When calculateChunkSizes Then exactly numberOfChunks entries produced`(
        description: String,
        bodySize: Int,
        numberOfChunks: Int,
    ) {
        // When
        val chunkSizes = writer.calculateChunkSizes(bodySize, numberOfChunks)

        // Then — exactly numberOfChunks chunks produced
        assertEquals(
            numberOfChunks,
            chunkSizes.size,
            "Must produce exactly $numberOfChunks chunks for: $description"
        )

        // And — sum of chunk sizes equals body size (no data loss)
        assertEquals(
            bodySize,
            chunkSizes.sum(),
            "Sum of chunk sizes must equal body size for: $description"
        )
    }

    companion object {
        @JvmStatic
        fun chunkedDeliveryTestCases(): Stream<Arguments> = Stream.of(
            // 1. Minimal: 1 byte / 2 chunks (edge: more chunks than bytes)
            Arguments.of("1 byte / 2 chunks", 1, 2),
            // 2. Small body, few chunks
            Arguments.of("10 bytes / 3 chunks", 10, 3),
            // 3. Medium body, moderate chunks
            Arguments.of("100 bytes / 10 chunks", 100, 10),
            // 4. Larger body with remainder
            Arguments.of("1000 bytes / 7 chunks", 1000, 7),
            // 5. Large body, many chunks
            Arguments.of("10000 bytes / 100 chunks", 10000, 100),
            // 6. Very large body, many chunks
            Arguments.of("100000 bytes / 1000 chunks", 100000, 1000),
            // 7. Equal split: body size equals chunk count
            Arguments.of("5 bytes / 5 chunks", 5, 5),
            // 8. Small body with remainder
            Arguments.of("3 bytes / 2 chunks", 3, 2),
            // 9. Power of 2 body, many chunks
            Arguments.of("1024 bytes / 512 chunks", 1024, 512),
            // 10. Prime-ish body size with prime-ish chunk count
            Arguments.of("999 bytes / 13 chunks", 999, 13),
            // 11. Large body, few chunks
            Arguments.of("50000 bytes / 3 chunks", 50000, 3),
            // 12. Small body, moderate chunks
            Arguments.of("7 bytes / 4 chunks", 7, 4),
            // 13. Body size equals chunk count (1 byte per chunk)
            Arguments.of("256 bytes / 256 chunks", 256, 256),
            // 14. Edge: 1 byte / 1000 chunks (far more chunks than bytes)
            Arguments.of("1 byte / 1000 chunks (edge)", 1, 1000),
            // 15. Large body, minimal chunks
            Arguments.of("100000 bytes / 2 chunks", 100000, 2),
        )
    }
}
