package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkedDribbleDelayCaptureTest {

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

    @Test
    fun `Given bodyFileName and dribble When transform Then config has bodyFileName and returns modified ResponseDefinition with empty body`() {
        // Given - use a real ResponseDefinition so ResponseDefinitionBuilder.like() works
        val realResponseDefinition = ResponseDefinitionBuilder()
            .withStatus(200)
            .withBodyFile("large-response.json")
            .withChunkedDribbleDelay(5, 3000)
            .build()

        every { serveEvent.responseDefinition } returns realResponseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNotNull(config)
        assertEquals(5, config.numberOfChunks)
        assertEquals(3000L, config.totalDurationMs)
        assertEquals("large-response.json", config.bodyFileName)

        // Verify the returned ResponseDefinition has empty body and no bodyFileName
        assertNull(result.bodyFileName)
        assertTrue(result.body.isNullOrEmpty() || result.body == "")
    }

    @Test
    fun `Given dribble only without bodyFileName When transform Then config has null bodyFileName and returns original ResponseDefinition`() {
        // Given - use a real ResponseDefinition with inline body and dribble (no bodyFileName)
        val realResponseDefinition = ResponseDefinitionBuilder()
            .withStatus(200)
            .withBody("inline response body")
            .withChunkedDribbleDelay(3, 2000)
            .build()

        every { serveEvent.responseDefinition } returns realResponseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNotNull(config)
        assertEquals(3, config.numberOfChunks)
        assertEquals(2000L, config.totalDurationMs)
        assertNull(config.bodyFileName)

        // Verify the original ResponseDefinition is returned unchanged
        assertEquals(realResponseDefinition, result)
    }

    @Test
    fun `Given bodyFileName only without dribble When transform Then no config stored and returns original ResponseDefinition`() {
        // Given - use a real ResponseDefinition with bodyFileName but no dribble
        val realResponseDefinition = ResponseDefinitionBuilder()
            .withStatus(200)
            .withBodyFile("some-file.json")
            .build()

        every { serveEvent.responseDefinition } returns realResponseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNull(config)

        // Verify the original ResponseDefinition is returned unchanged
        assertEquals(realResponseDefinition, result)
    }

    @Test
    fun `Given neither bodyFileName nor dribble When transform Then no config stored and returns original ResponseDefinition`() {
        // Given - use a real ResponseDefinition with no bodyFileName and no dribble
        val realResponseDefinition = ResponseDefinitionBuilder()
            .withStatus(200)
            .withBody("simple response")
            .build()

        every { serveEvent.responseDefinition } returns realResponseDefinition

        // When
        val result = transformer.transform(serveEvent)

        // Then
        val config = ChunkedDribbleDelayCapture.getAndClear()
        assertNull(config)

        // Verify the original ResponseDefinition is returned unchanged
        assertEquals(realResponseDefinition, result)
    }
}
