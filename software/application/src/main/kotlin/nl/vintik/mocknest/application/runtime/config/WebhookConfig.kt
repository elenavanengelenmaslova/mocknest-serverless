package nl.vintik.mocknest.application.runtime.config

data class WebhookConfig(
    val sensitiveHeaders: Set<String>,
    val webhookTimeoutMs: Long,
    val asyncTimeoutMs: Long = 30_000L,
    val requestJournalPrefix: String = "requests/",
) {
    companion object {
        private const val DEFAULT_SENSITIVE_HEADERS = "x-api-key,authorization,proxy-authorization,x-amz-security-token"
        private const val DEFAULT_WEBHOOK_TIMEOUT_MS = 10_000L
        private const val DEFAULT_ASYNC_TIMEOUT_MS = 30_000L
        private const val DEFAULT_REQUEST_JOURNAL_PREFIX = "requests/"

        fun fromEnv(): WebhookConfig {
            val sensitiveHeaders = (System.getenv("MOCKNEST_SENSITIVE_HEADERS") ?: DEFAULT_SENSITIVE_HEADERS)
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val webhookTimeoutMs = System.getenv("MOCKNEST_WEBHOOK_TIMEOUT_MS")
                ?.toLongOrNull()
                ?: DEFAULT_WEBHOOK_TIMEOUT_MS
            val asyncTimeoutMs = System.getenv("MOCKNEST_WEBHOOK_ASYNC_TIMEOUT_MS")
                ?.toLongOrNull()
                ?: DEFAULT_ASYNC_TIMEOUT_MS
            val requestJournalPrefix = System.getenv("MOCKNEST_REQUEST_JOURNAL_PREFIX")
                ?: DEFAULT_REQUEST_JOURNAL_PREFIX
            return WebhookConfig(sensitiveHeaders, webhookTimeoutMs, asyncTimeoutMs, requestJournalPrefix)
        }
    }
}
