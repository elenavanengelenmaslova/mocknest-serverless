package nl.vintik.mocknest.infra.aws.runtime.streaming

import kotlinx.coroutines.test.runTest
import nl.vintik.lambda.streaming.sse.PacedSseEmitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Unit tests for [SseDribbleWriter].
 *
 * Covers: padded SSE events emitted with the configured head/tail padding, the inter-chunk pacing
 * delay applied between events (never before the first, never after the last), the terminal
 * `event: end` / `data: [DONE]` event, byte-for-byte transcript recovery after dropping padding,
 * and the S3 path staying within the bounded 1MB read buffer.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 3.1, 3.2**
 */
class SseDribbleWriterTest {

    // Small, predictable padding tiers so byte counts are easy to assert without allocating ~100KB.
    private val headPadBytes = 64
    private val tailPadBytes = 16
    private val headPadEvents = 2

    private val writer = SseDribbleWriter(
        headPadBytes = headPadBytes,
        tailPadBytes = tailPadBytes,
        headPadEvents = headPadEvents,
    )

    /**
     * Recovers the original payload from a padded SSE body the way a compliant client (or the
     * `sed -n 's/^data: //p'` filter in the module README) does.
     */
    private fun recoverPayload(sseBody: String): String =
        sseBody.split("\n\n")
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith(": ") }
            .filterNot { it.startsWith("event: end") }
            .filter { it.startsWith("data: ") }
            .joinToString("") { it.removePrefix("data: ") }

    @Nested
    inner class InMemoryPadding {

        @Test
        fun `Given SSE body and padding tiers When writeSseChunked Then head events carry head padding and later events carry tail padding`() {
            // Given
            val body = "abcdefghij".toByteArray(Charsets.UTF_8) // 10 bytes
            val output = ByteArrayOutputStream()

            // When — 5 chunks so events span the head tier (first 2) and the tail tier (rest)
            writer.writeSseChunked(body, numberOfChunks = 5, totalDurationMs = 0, output = output, sleep = {})

            // Then — each event's padding comment matches its tier
            val sse = output.toString(Charsets.UTF_8.name())
            val paddingBlocks = sse.split("\n\n")
                .filter { it.startsWith(": ") }
                .map { it.removePrefix(": ").length }
            // 5 data events → 5 padding comments: first 2 at head tier, next 3 at tail tier
            assertEquals(listOf(headPadBytes, headPadBytes, tailPadBytes, tailPadBytes, tailPadBytes), paddingBlocks)
        }

        @Test
        fun `Given SSE body When writeSseChunked Then transcript is recoverable byte-for-byte and stream ends with DONE`() {
            // Given
            val transcript = "the quick brown fox"
            val output = ByteArrayOutputStream()

            // When
            writer.writeSseChunked(transcript.toByteArray(Charsets.UTF_8), numberOfChunks = 4, totalDurationMs = 0, output = output, sleep = {})

            // Then
            val sse = output.toString(Charsets.UTF_8.name())
            assertTrue(sse.contains("event: end\ndata: [DONE]\n\n"), "Terminal end/[DONE] event must be written")
            assertEquals(transcript, recoverPayload(sse))
        }

        @Test
        fun `Given more chunks than body bytes When writeSseChunked Then transcript still recovers and DONE is emitted`() {
            // Given — edge case: 3 chunks for a 1-byte body (empty chunks are valid SSE events)
            val transcript = "x"
            val output = ByteArrayOutputStream()

            // When
            writer.writeSseChunked(transcript.toByteArray(Charsets.UTF_8), numberOfChunks = 3, totalDurationMs = 0, output = output, sleep = {})

            // Then
            val sse = output.toString(Charsets.UTF_8.name())
            assertEquals(transcript, recoverPayload(sse))
            assertTrue(sse.contains("data: [DONE]"))
        }
    }

    @Nested
    inner class Pacing {

        @Test
        fun `Given dribble over virtual time When writeSseChunked Then delay applied between events but not before first nor after last`() = runTest {
            // Given — capture the sleep schedule and advance virtual time via the test dispatcher
            val sleeps = mutableListOf<Long>()
            val body = "0123456789".toByteArray(Charsets.UTF_8)
            val output = ByteArrayOutputStream()
            val numberOfChunks = 5
            val totalDurationMs = 1000L // per-event gap = 1000 / 5 = 200ms

            // When
            writer.writeSseChunked(body, numberOfChunks, totalDurationMs, output) { ms ->
                sleeps.add(ms)
            }

            // Then — exactly (numberOfChunks - 1) gaps, each 200ms; no leading or trailing pause
            assertEquals(numberOfChunks - 1, sleeps.size, "One pause between each pair of events; none before first or after last")
            assertTrue(sleeps.all { it == 200L }, "Each inter-event gap must be totalDuration/numberOfChunks = 200ms; got $sleeps")
        }

        @Test
        fun `Given zero totalDuration When writeSseChunked Then no pacing delay is applied`() {
            // Given
            val sleeps = mutableListOf<Long>()
            val output = ByteArrayOutputStream()

            // When
            writer.writeSseChunked("hello".toByteArray(Charsets.UTF_8), numberOfChunks = 3, totalDurationMs = 0, output = output) { ms ->
                sleeps.add(ms)
            }

            // Then
            assertTrue(sleeps.isEmpty(), "No sleeps expected for zero totalDuration")
        }
    }

    @Nested
    inner class PerEventDelayMapping {

        @Test
        fun `Given gap below the emitter minimum When mapping Then clamps to MIN_DELAY_MILLIS`() {
            // Given — 10ms over 100 chunks = 0ms gap; but a 1ms-ish sub-minimum gap must clamp up
            // 3ms over 2 chunks = 1ms (already the min); use 1ms over 3 chunks → 0 → NO_DELAY instead.
            // Here: 5ms over 3 chunks = 1ms gap (== MIN).
            val delay = writer.perEventDelayMillis(totalDurationMs = 5, numberOfChunks = 3, hasPadding = true)
            assertEquals(PacedSseEmitter.MIN_DELAY_MILLIS, delay)
        }

        @Test
        fun `Given gap above the emitter maximum When mapping Then clamps to MAX_DELAY_MILLIS`() {
            // Given — 60_000ms over 2 chunks = 30_000ms gap, far above MAX (5000ms)
            val delay = writer.perEventDelayMillis(totalDurationMs = 60_000, numberOfChunks = 2, hasPadding = true)
            assertEquals(PacedSseEmitter.MAX_DELAY_MILLIS, delay)
        }

        @Test
        fun `Given no padding When mapping Then forces NO_DELAY so the emitter does not reject the delay`() {
            // Given — a delay with an unpadded writer would silently do nothing; the emitter rejects
            // it at construction, so the mapping must force NO_DELAY.
            val delay = writer.perEventDelayMillis(totalDurationMs = 1000, numberOfChunks = 5, hasPadding = false)
            assertEquals(PacedSseEmitter.NO_DELAY, delay)
        }

        @Test
        fun `Given zero gap When mapping Then returns NO_DELAY`() {
            val delay = writer.perEventDelayMillis(totalDurationMs = 2, numberOfChunks = 5, hasPadding = true)
            assertEquals(PacedSseEmitter.NO_DELAY, delay)
        }
    }

    @Nested
    inner class S3StreamBoundedMemory {

        @Test
        fun `Given large S3 body When writeSseChunkedFromStream Then reads at most the bounded buffer at a time`() = runTest {
            // Given — a 3MB body split into 3 chunks. The read buffer must never exceed 1MB, proving
            // the whole object is not materialized.
            val bodySize = 3 * 1024 * 1024L
            val data = ByteArray(bodySize.toInt()) { 'a'.code.toByte() }
            val tracking = MaxReadTrackingInputStream(ByteArrayInputStream(data))
            val output = ByteArrayOutputStream()

            // When
            writer.writeSseChunkedFromStream(tracking, bodySize, numberOfChunks = 3, totalDurationMs = 0, output = output, sleep = {})

            // Then — no single read requested more than the 1MB bounded buffer
            assertTrue(
                tracking.maxRequestedLen <= ChunkedResponseWriter.STREAM_BUFFER_SIZE,
                "Max single read ${tracking.maxRequestedLen} must stay within the 1MB bounded buffer",
            )
            // And the recovered payload is the full body, terminated by [DONE]
            val sse = output.toString(Charsets.UTF_8.name())
            assertTrue(sse.contains("data: [DONE]"))
            assertEquals(String(data, Charsets.UTF_8), recoverPayload(sse))
        }

        @Test
        fun `Given stream shorter than declared bodySize When writeSseChunkedFromStream Then throws PrematureEofException`() = runTest {
            // Given — declare 100 bytes but only supply 10
            val input = ByteArrayInputStream(ByteArray(10))
            val output = ByteArrayOutputStream()

            // When / Then
            assertThrows<PrematureEofException> {
                writer.writeSseChunkedFromStream(input, bodySize = 100, numberOfChunks = 2, totalDurationMs = 0, output = output, sleep = {})
            }
        }
    }

    /** Wraps an InputStream and records the largest `len` ever requested in a read call. */
    private class MaxReadTrackingInputStream(private val delegate: InputStream) : InputStream() {
        var maxRequestedLen = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len > maxRequestedLen) maxRequestedLen = len
            return delegate.read(b, off, len)
        }
    }
}
