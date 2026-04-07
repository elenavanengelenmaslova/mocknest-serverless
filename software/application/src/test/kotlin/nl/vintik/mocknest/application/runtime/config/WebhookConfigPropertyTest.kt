package nl.vintik.mocknest.application.runtime.config

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for WebhookConfig sensitive header parsing.
 *
 * Property: for any valid comma-separated header name list, all names appear in
 * sensitiveHeaders as lowercase trimmed strings, with duplicates removed.
 *
 * Validates: Requirements 4.3
 */
class WebhookConfigPropertyTest {

    private fun parseHeaders(sensitiveHeadersEnv: String): Set<String> =
        sensitiveHeadersEnv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    @ParameterizedTest(name = "[{index}] input=''{0}'' expected=''{1}''")
    @CsvFileSource(
        resources = ["/test-data/webhook/sensitive-header-configs.csv"],
        numLinesToSkip = 1,
    )
    fun `Given diverse MOCKNEST_SENSITIVE_HEADERS values When parsing Then all names are lowercase trimmed and deduplicated`(
        sensitiveHeadersEnv: String,
        expectedHeadersCsv: String,
    ) {
        val config = WebhookConfig(
            selfUrl = null,
            sensitiveHeaders = parseHeaders(sensitiveHeadersEnv),
            webhookTimeoutMs = 10_000L,
        )

        val expected = expectedHeadersCsv.split(";").toSet()

        // Property: every expected header is present in sensitiveHeaders
        expected.forEach { header ->
            assertTrue(
                config.sensitiveHeaders.contains(header),
                "Expected '$header' to be in sensitiveHeaders but was not. Got: ${config.sensitiveHeaders}",
            )
        }

        // Property: sensitiveHeaders contains no extra entries beyond expected
        assertEquals(
            expected,
            config.sensitiveHeaders,
            "sensitiveHeaders should exactly match expected set",
        )

        // Property: all stored values are lowercase
        config.sensitiveHeaders.forEach { header ->
            assertEquals(
                header.lowercase(),
                header,
                "Header '$header' should be stored in lowercase",
            )
        }

        // Property: all stored values are trimmed (no leading/trailing whitespace)
        config.sensitiveHeaders.forEach { header ->
            assertEquals(
                header.trim(),
                header,
                "Header '$header' should be trimmed",
            )
        }
    }
}
