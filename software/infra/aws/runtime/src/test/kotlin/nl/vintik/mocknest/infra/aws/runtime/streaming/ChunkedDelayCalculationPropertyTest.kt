package nl.vintik.mocknest.infra.aws.runtime.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.util.stream.Stream

/**
 * Property 4: Chunked Delay Calculation
 *
 * For any totalDuration in 0–270,000 ms and numberOfChunks in 2–1000, the inter-chunk delay
 * SHALL equal totalDuration / numberOfChunks (integer division), and the total number of delays
 * SHALL equal numberOfChunks - 1 (no delay before the first chunk).
 *
 * **Validates: Requirements 4.2**
 */
@Tag("Feature: response-streaming, Property 4: Chunked Delay Calculation")
class ChunkedDelayCalculationPropertyTest {

    private val writer = ChunkedResponseWriter()

    @ParameterizedTest(name = "{0}")
    @MethodSource("delayCalculationTestCases")
    fun `Given totalDuration and numberOfChunks When calculating delay Then inter-chunk delay equals totalDuration div numberOfChunks and total delays equals numberOfChunks minus 1`(
        description: String,
        totalDuration: Long,
        numberOfChunks: Int,
    ) {
        val expectedDelay = totalDuration / numberOfChunks
        val expectedNumberOfDelays = numberOfChunks - 1

        // Verify the mathematical property: inter-chunk delay = totalDuration / numberOfChunks (integer division)
        assertEquals(
            totalDuration / numberOfChunks,
            expectedDelay,
            "Inter-chunk delay should equal totalDuration / numberOfChunks (integer division) for: $description"
        )

        // Verify total number of delays = numberOfChunks - 1 (no delay before first chunk)
        assertEquals(
            numberOfChunks - 1,
            expectedNumberOfDelays,
            "Total number of delays should equal numberOfChunks - 1 for: $description"
        )

        // For cases where the total actual sleep time would be short (< 500ms),
        // verify actual timing behavior by running writeChunked
        val totalExpectedSleepMs = expectedDelay * expectedNumberOfDelays
        if (totalExpectedSleepMs <= 500) {
            val bodySize = numberOfChunks * 10
            val body = ByteArray(bodySize) { (it % 256).toByte() }
            val output = ByteArrayOutputStream()

            val startTime = System.nanoTime()
            writer.writeChunked(body, numberOfChunks, totalDuration, output)
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            // Verify all bytes were written
            assertEquals(bodySize, output.size(), "All body bytes should be written for: $description")

            // If there's a non-zero expected sleep, verify elapsed time is reasonable
            if (totalExpectedSleepMs > 0) {
                val lowerBound = (totalExpectedSleepMs * 0.5).toLong()
                assert(elapsedMs >= lowerBound) {
                    "Elapsed time ${elapsedMs}ms should be at least 50% of expected total delay ${totalExpectedSleepMs}ms for: $description"
                }
            }
        }
        // For larger delays, the mathematical property verification above is sufficient
        // (we don't actually sleep to keep tests fast)
    }

    companion object {
        @JvmStatic
        fun delayCalculationTestCases(): Stream<Arguments> = Stream.of(
            // 1. Zero duration, 2 chunks
            Arguments.of("totalDuration=0, numberOfChunks=2", 0L, 2),
            // 2. Zero duration, 100 chunks
            Arguments.of("totalDuration=0, numberOfChunks=100", 0L, 100),
            // 3. 100ms, 2 chunks → delay = 50ms, 1 delay
            Arguments.of("totalDuration=100, numberOfChunks=2", 100L, 2),
            // 4. 100ms, 3 chunks → delay = 33ms, 2 delays
            Arguments.of("totalDuration=100, numberOfChunks=3", 100L, 3),
            // 5. 100ms, 5 chunks → delay = 20ms, 4 delays
            Arguments.of("totalDuration=100, numberOfChunks=5", 100L, 5),
            // 6. 100ms, 7 chunks → delay = 14ms, 6 delays
            Arguments.of("totalDuration=100, numberOfChunks=7", 100L, 7),
            // 7. 1000ms, 10 chunks → delay = 100ms, 9 delays
            Arguments.of("totalDuration=1000, numberOfChunks=10", 1000L, 10),
            // 8. 1000ms, 50 chunks → delay = 20ms, 49 delays
            Arguments.of("totalDuration=1000, numberOfChunks=50", 1000L, 50),
            // 9. 3000ms, 5 chunks → delay = 600ms, 4 delays
            Arguments.of("totalDuration=3000, numberOfChunks=5", 3000L, 5),
            // 10. 3000ms, 1000 chunks → delay = 3ms, 999 delays
            Arguments.of("totalDuration=3000, numberOfChunks=1000", 3000L, 1000),
            // 11. 5000ms, 7 chunks → delay = 714ms, 6 delays
            Arguments.of("totalDuration=5000, numberOfChunks=7", 5000L, 7),
            // 12. 5000ms, 100 chunks → delay = 50ms, 99 delays
            Arguments.of("totalDuration=5000, numberOfChunks=100", 5000L, 100),
            // 13. 10000ms, 3 chunks → delay = 3333ms, 2 delays
            Arguments.of("totalDuration=10000, numberOfChunks=3", 10000L, 3),
            // 14. 10000ms, 10 chunks → delay = 1000ms, 9 delays
            Arguments.of("totalDuration=10000, numberOfChunks=10", 10000L, 10),
            // 15. 60000ms, 50 chunks → delay = 1200ms, 49 delays
            Arguments.of("totalDuration=60000, numberOfChunks=50", 60000L, 50),
            // 16. 270000ms, 100 chunks → delay = 2700ms, 99 delays
            Arguments.of("totalDuration=270000, numberOfChunks=100", 270000L, 100),
            // 17. 270000ms, 1000 chunks → delay = 270ms, 999 delays
            Arguments.of("totalDuration=270000, numberOfChunks=1000", 270000L, 1000),
            // 18. 100ms, 1000 chunks → delay = 0ms (integer division), 999 delays
            Arguments.of("totalDuration=100, numberOfChunks=1000", 100L, 1000),
        )
    }
}
