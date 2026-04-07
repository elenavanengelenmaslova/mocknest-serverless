package nl.vintik.mocknest.application.runtime.config

data class WebhookConfig(
    val selfUrl: String?,
    val sensitiveHeaders: Set<String>,
    val webhookTimeoutMs: Long,
) {
    companion object {
        private const val DEFAULT_SENSITIVE_HEADERS = "x-api-key,authorization"
        private const val DEFAULT_WEBHOOK_TIMEOUT_MS = 10_000L

        fun fromEnv(): WebhookConfig {
            val selfUrl = System.getenv("MOCKNEST_SELF_URL")
            val sensitiveHeaders = (System.getenv("MOCKNEST_SENSITIVE_HEADERS") ?: DEFAULT_SENSITIVE_HEADERS)
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val webhookTimeoutMs = System.getenv("MOCKNEST_WEBHOOK_TIMEOUT_MS")
                ?.toLongOrNull()
                ?: DEFAULT_WEBHOOK_TIMEOUT_MS
            return WebhookConfig(selfUrl, sensitiveHeaders, webhookTimeoutMs)
        }
    }
}
