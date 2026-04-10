package nl.vintik.mocknest.application.runtime.extensions

import kotlinx.serialization.Serializable

data class WebhookRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
    val timeoutMs: Long,
)

sealed class WebhookResult {
    data class Success(val statusCode: Int) : WebhookResult()
    data class Failure(val statusCode: Int?, val message: String) : WebhookResult()
}

sealed class WebhookAuthConfig {
    object None : WebhookAuthConfig()

    data class AwsIam(
        val region: String? = null,
        val service: String? = null,
    ) : WebhookAuthConfig()
}

@Serializable
data class AsyncEvent(
    val actionType: String,
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
    val auth: AsyncEventAuth,
)

@Serializable
data class AsyncEventAuth(
    val type: String,
    val region: String? = null,
    val service: String? = null,
)
