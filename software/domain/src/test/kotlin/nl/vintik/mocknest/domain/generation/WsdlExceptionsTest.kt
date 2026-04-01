package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlExceptionsTest {

    @Test
    fun `Given message only When creating WsdlParsingException Then should carry message correctly`() {
        val exception = WsdlParsingException("Malformed XML at line 5: unexpected token")

        assertEquals("Malformed XML at line 5: unexpected token", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating WsdlParsingException Then should carry both correctly`() {
        val cause = IllegalStateException("underlying parse error")
        val exception = WsdlParsingException("Failed to parse WSDL", cause)

        assertEquals("Failed to parse WSDL", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given WsdlParsingException When checking type Then should be RuntimeException`() {
        val exception = WsdlParsingException("error")

        assertNotNull(exception as? RuntimeException)
    }

    @Test
    fun `Given message only When creating WsdlFetchException Then should carry message correctly`() {
        val exception = WsdlFetchException("HTTP 404 fetching WSDL from https://example.com/service.wsdl")

        assertEquals("HTTP 404 fetching WSDL from https://example.com/service.wsdl", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating WsdlFetchException Then should carry both correctly`() {
        val cause = RuntimeException("connection refused")
        val exception = WsdlFetchException("Network failure fetching WSDL: connection refused", cause)

        assertEquals("Network failure fetching WSDL: connection refused", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given WsdlFetchException When checking type Then should be RuntimeException`() {
        val exception = WsdlFetchException("error")

        assertNotNull(exception as? RuntimeException)
    }
}
