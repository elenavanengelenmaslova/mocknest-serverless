package nl.vintik.mocknest.infra.aws.generation.ai.eval

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Offline unit tests for [parseIterationCount].
 *
 * Feature: soap-realistic-data-stabilization, Property 2: parseIterationCount behavior
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 *
 * The parser is a pure `String? -> Int` function, so no mocking is required. These tests
 * run in the normal `./gradlew test` flow and never call Bedrock.
 */
class EvalMetricsTest {

    @Nested
    inner class DefaultWhenUnset {

        @Test
        fun `Given BEDROCK_EVAL_ITERATIONS is unset When parsing Then it returns the default of 3`() {
            val result = parseIterationCount(null)

            assertEquals(DEFAULT_EVAL_ITERATIONS, result, "null input should return the default iteration count")
            assertEquals(3, result, "the default iteration count should be 3")
        }
    }

    @Nested
    inner class ValidOverride {

        @ParameterizedTest
        @CsvSource(
            "1, 1",
            "2, 2",
            "3, 3",
            "5, 5",
            "10, 10",
            "100, 100",
            "'  4  ', 4",
            "' 7', 7",
            "'12 ', 12",
        )
        fun `Given a valid positive integer string When parsing Then it returns that value`(
            input: String,
            expected: Int,
        ) {
            val result = parseIterationCount(input)

            assertEquals(expected, result, "input '$input' should parse to $expected")
        }
    }

    @Nested
    inner class BlankOrEmptyRejection {

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
        fun `Given a blank or empty string When parsing Then it throws IllegalArgumentException with a descriptive message`(
            input: String,
        ) {
            val ex = assertFailsWith<IllegalArgumentException> { parseIterationCount(input) }

            val message = ex.message
            assertNotNull(message, "exception message should not be null")
            assertTrue(
                message.contains("BEDROCK_EVAL_ITERATIONS"),
                "message should reference the env var name, got: '$message'",
            )
        }
    }

    @Nested
    inner class NonNumericRejection {

        @ParameterizedTest
        @ValueSource(strings = ["abc", "3.5", "1e3", "five", "3a", "-", "+", "0x10"])
        fun `Given a non-numeric string When parsing Then it throws IllegalArgumentException with a descriptive message`(
            input: String,
        ) {
            val ex = assertFailsWith<IllegalArgumentException> { parseIterationCount(input) }

            val message = ex.message
            assertNotNull(message, "exception message should not be null")
            assertTrue(
                message.contains("valid integer"),
                "message should indicate a valid integer is required, got: '$message'",
            )
        }
    }

    @Nested
    inner class ZeroOrNegativeRejection {

        @ParameterizedTest
        @ValueSource(strings = ["0", "-1", "-5", "-100", " -3 "])
        fun `Given zero or a negative integer When parsing Then it throws IllegalArgumentException with a descriptive message`(
            input: String,
        ) {
            val ex = assertFailsWith<IllegalArgumentException> { parseIterationCount(input) }

            val message = ex.message
            assertNotNull(message, "exception message should not be null")
            assertTrue(
                message.contains("positive integer"),
                "message should indicate a positive integer is required, got: '$message'",
            )
        }
    }
}
