package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("ai-generation")
@Tag("unit")
class ModelResponseParsingExceptionTest {

    @Test
    fun `Given message only When creating ModelResponseParsingException Then should carry message correctly`() {
        val exception = ModelResponseParsingException("AI response is not valid JSON")

        assertEquals("AI response is not valid JSON", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating ModelResponseParsingException Then should carry both correctly`() {
        val cause = IllegalStateException("unexpected end of input")
        val exception = ModelResponseParsingException("Failed to parse model response", cause)

        assertEquals("Failed to parse model response", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given ModelResponseParsingException When checking type Then should be RuntimeException`() {
        val exception = ModelResponseParsingException("error")

        assertNotNull(exception as? RuntimeException)
    }
}
