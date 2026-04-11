package nl.vintik.mocknest.application.generation.util

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("unit")
class UrlResolutionExceptionTest {

    @Test
    fun `Given message only When creating UrlResolutionException Then should carry message correctly`() {
        val exception = UrlResolutionException("Could not resolve relative URL: ../schemas/user.json")

        assertEquals("Could not resolve relative URL: ../schemas/user.json", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating UrlResolutionException Then should carry both correctly`() {
        val cause = IllegalArgumentException("malformed URL")
        val exception = UrlResolutionException("URL resolution failed", cause)

        assertEquals("URL resolution failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given UrlResolutionException When checking type Then should be RuntimeException`() {
        val exception = UrlResolutionException("error")

        assertNotNull(exception as? RuntimeException)
    }
}
