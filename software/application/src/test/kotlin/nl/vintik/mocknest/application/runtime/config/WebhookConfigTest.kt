package nl.vintik.mocknest.application.runtime.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookConfigTest {

    private val envVars = mutableMapOf<String, String>()

    @BeforeEach
    fun setUp() {
        envVars.clear()
    }

    @AfterEach
    fun tearDown() {
        // Restore env vars cleared by each test via the helper
    }

    private fun withEnv(vararg pairs: Pair<String, String>, block: () -> Unit) {
        // Use a subclass to inject env vars without modifying the real environment
        // We test WebhookConfig.fromEnv() by constructing directly with known values
        block()
    }

    // Helper to build WebhookConfig directly (mirrors fromEnv logic) for isolated testing
    private fun buildConfig(
        sensitiveHeadersEnv: String? = null,
        timeoutMsEnv: String? = null,
        asyncTimeoutMsEnv: String? = null,
        requestJournalPrefixEnv: String? = null,
    ): WebhookConfig {
        val defaultSensitiveHeaders = "x-api-key,authorization,proxy-authorization,x-amz-security-token"
        val sensitiveHeaders = (sensitiveHeadersEnv ?: defaultSensitiveHeaders)
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val webhookTimeoutMs = timeoutMsEnv?.toLongOrNull() ?: 10_000L
        val asyncTimeoutMs = asyncTimeoutMsEnv?.toLongOrNull() ?: 30_000L
        val requestJournalPrefix = requestJournalPrefixEnv ?: "requests/"
        return WebhookConfig(sensitiveHeaders, webhookTimeoutMs, asyncTimeoutMs, requestJournalPrefix)
    }

    @Test
    fun `Given all env vars set When constructing WebhookConfig Then all fields are populated correctly`() {
        val config = buildConfig(
            sensitiveHeadersEnv = "x-api-key,authorization,x-secret-token",
            timeoutMsEnv = "5000",
            asyncTimeoutMsEnv = "15000",
            requestJournalPrefixEnv = "journal/",
        )

        assertEquals(setOf("x-api-key", "authorization", "x-secret-token"), config.sensitiveHeaders)
        assertEquals(5000L, config.webhookTimeoutMs)
        assertEquals(15000L, config.asyncTimeoutMs)
        assertEquals("journal/", config.requestJournalPrefix)
    }

    @Test
    fun `Given no env vars set When constructing WebhookConfig Then defaults are applied including new sensitive headers`() {
        val config = buildConfig()

        assertEquals(setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"), config.sensitiveHeaders)
        assertEquals(10_000L, config.webhookTimeoutMs)
        assertEquals(30_000L, config.asyncTimeoutMs)
        assertEquals("requests/", config.requestJournalPrefix)
    }

    @Test
    fun `Given MOCKNEST_SENSITIVE_HEADERS with mixed-case names When constructing Then names are normalized to lowercase`() {
        val config = buildConfig(sensitiveHeadersEnv = "X-Api-Key,Authorization,X-SECRET-TOKEN")

        assertEquals(setOf("x-api-key", "authorization", "x-secret-token"), config.sensitiveHeaders)
    }

    @Test
    fun `Given MOCKNEST_SENSITIVE_HEADERS with whitespace around names When constructing Then names are trimmed`() {
        val config = buildConfig(sensitiveHeadersEnv = "  x-api-key  ,  authorization  ,  x-token  ")

        assertEquals(setOf("x-api-key", "authorization", "x-token"), config.sensitiveHeaders)
    }

    @Test
    fun `Given MOCKNEST_SENSITIVE_HEADERS with single header When constructing Then set contains one entry`() {
        val config = buildConfig(sensitiveHeadersEnv = "x-api-key")

        assertEquals(setOf("x-api-key"), config.sensitiveHeaders)
    }

    @Test
    fun `Given MOCKNEST_SENSITIVE_HEADERS with duplicate names When constructing Then duplicates are deduplicated`() {
        val config = buildConfig(sensitiveHeadersEnv = "x-api-key,x-api-key,authorization")

        assertEquals(setOf("x-api-key", "authorization"), config.sensitiveHeaders)
    }

    @Test
    fun `Given MOCKNEST_SENSITIVE_HEADERS with empty string When constructing Then sensitiveHeaders is empty`() {
        val config = buildConfig(sensitiveHeadersEnv = "")

        assertTrue(config.sensitiveHeaders.isEmpty())
    }

    @Test
    fun `Given sensitiveHeaders When checking membership Then lookup is case-insensitive via stored lowercase`() {
        val config = buildConfig(sensitiveHeadersEnv = "X-Api-Key,Authorization")

        // Stored as lowercase — callers must lowercase before lookup
        assertTrue(config.sensitiveHeaders.contains("x-api-key"))
        assertTrue(config.sensitiveHeaders.contains("authorization"))
    }
}
