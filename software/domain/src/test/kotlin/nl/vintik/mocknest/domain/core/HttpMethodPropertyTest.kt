package nl.vintik.mocknest.domain.core

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

/**
 * Property tests for domain HTTP types.
 *
 * **Validates: Requirements 9.1, 9.2**
 */
class HttpMethodPropertyTest {

    /**
     * Property 1a: HttpMethod round-trip (case-sensitive valueOf)
     *
     * For any valid HTTP method string, converting to HttpMethod via valueOf(s)
     * and back to string via .name produces the original uppercase string.
     *
     * **Validates: Requirements 9.1**
     */
    @ParameterizedTest
    @ValueSource(strings = ["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"])
    fun `Given valid HTTP method string When valueOf and name Then round-trips to original`(method: String) {
        assertEquals(method, HttpMethod.valueOf(method).name)
    }

    /**
     * Property 1a (case-insensitive): HttpMethod resolve is case-insensitive.
     *
     * **Validates: Requirements 9.1**
     */
    @ParameterizedTest
    @ValueSource(strings = ["get", "Head", "post", "Put", "pAtCh", "delete", "Options", "trace"])
    fun `Given mixed-case HTTP method string When resolve Then resolves correctly`(method: String) {
        assertEquals(method.uppercase(), HttpMethod.resolve(method).name)
    }

    /**
     * Property 1b: HttpStatusCode round-trip
     *
     * For any integer in 100..599, constructing HttpStatusCode(n) and calling value()
     * returns n.
     *
     * **Validates: Requirements 9.2**
     */
    @ParameterizedTest
    @MethodSource("validStatusCodes")
    fun `Given valid status code When constructing HttpStatusCode Then value round-trips`(code: Int) {
        assertEquals(code, HttpStatusCode(code).value())
    }

    /**
     * Property 1c: HttpStatusCode rejects invalid codes
     *
     * For any integer outside 100..599, constructing HttpStatusCode(n) throws
     * IllegalArgumentException.
     *
     * **Validates: Requirements 9.2**
     */
    @ParameterizedTest
    @MethodSource("invalidStatusCodes")
    fun `Given invalid status code When constructing HttpStatusCode Then throws IllegalArgumentException`(code: Int) {
        assertThrows<IllegalArgumentException> {
            HttpStatusCode(code)
        }
    }

    companion object {
        @JvmStatic
        fun validStatusCodes(): List<Int> =
            (100..599).toList()

        @JvmStatic
        fun invalidStatusCodes(): List<Int> =
            listOf(0, 1, 50, 99, 600, 601, 999, -1, -100, Int.MIN_VALUE, Int.MAX_VALUE)
    }
}
