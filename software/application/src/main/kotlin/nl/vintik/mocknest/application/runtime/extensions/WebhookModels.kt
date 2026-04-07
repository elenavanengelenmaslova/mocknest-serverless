package nl.vintik.mocknest.application.runtime.extensions

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

    data class Header(
        val injectName: String,
        val valueSource: HeaderValueSource,
    ) : WebhookAuthConfig()

    // Future: data class AwsIam(val region: String? = null) : WebhookAuthConfig()
}

sealed class HeaderValueSource {
    // v1 — implemented
    data class OriginalRequestHeader(val headerName: String) : HeaderValueSource()

    // Future — not implemented in v1:
    // data class Static(val value: String) : HeaderValueSource()
    // data class SecretRef(val secretRef: String) : HeaderValueSource()
    // data class EnvVar(val envVar: String) : HeaderValueSource()
}
