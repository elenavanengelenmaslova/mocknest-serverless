package nl.vintik.mocknest.domain.core

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

/**
 * Property tests for header map single-value conversion.
 *
 * **Validates: Requirements 9.3**
 */
class HeaderMapPropertyTest {

    /**
     * Property 2: Header map single-value conversion
     *
     * For any Map<String, List<String>> where every value list is non-empty,
     * converting to a single-value map via mapValues { it.value.first() }
     * produces a Map<String, String> with the same keys and the first value
     * from each list.
     *
     * **Validates: Requirements 9.3**
     */
    @ParameterizedTest
    @MethodSource("headerMaps")
    fun `Given multi-value header map When converting to single-value Then preserves keys and first values`(
        headers: Map<String, List<String>>
    ) {
        val singleValueMap: Map<String, String> = headers.mapValues { it.value.first() }

        assertEquals(headers.keys, singleValueMap.keys)
        headers.forEach { (key, values) ->
            assertEquals(values.first(), singleValueMap[key])
        }
    }

    companion object {
        @JvmStatic
        fun headerMaps(): List<Map<String, List<String>>> = listOf(
            // Single header, single value
            mapOf("Content-Type" to listOf("application/json")),

            // Single header, multiple values
            mapOf("Accept" to listOf("text/html", "application/json", "text/plain")),

            // Multiple headers, single values each
            mapOf(
                "Content-Type" to listOf("application/json"),
                "Authorization" to listOf("Bearer token123"),
                "X-Request-Id" to listOf("abc-def-ghi")
            ),

            // Multiple headers, mixed single and multi values
            mapOf(
                "Content-Type" to listOf("application/json"),
                "Accept" to listOf("text/html", "application/json"),
                "Cache-Control" to listOf("no-cache", "no-store", "must-revalidate")
            ),

            // Headers with empty string values
            mapOf(
                "X-Empty" to listOf(""),
                "X-Normal" to listOf("value")
            ),

            // Headers with special characters
            mapOf(
                "X-Custom-Header" to listOf("value with spaces"),
                "X-Unicode" to listOf("héllo wörld"),
                "X-Symbols" to listOf("a=b&c=d")
            ),

            // Single header with many values
            mapOf(
                "Set-Cookie" to listOf("session=abc", "theme=dark", "lang=en", "tz=UTC")
            ),

            // Headers with duplicate-looking keys (different case — Map treats as different keys)
            mapOf(
                "x-header" to listOf("lower"),
                "X-Header" to listOf("upper"),
                "X-HEADER" to listOf("all-caps")
            ),

            // Large number of headers
            (1..20).associate { "X-Header-$it" to listOf("value-$it", "alt-value-$it") },

            // Headers with very long values
            mapOf(
                "X-Long" to listOf("a".repeat(1000), "b".repeat(500))
            )
        )
    }
}
