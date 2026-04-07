package nl.vintik.mocknest.infra.aws.runtime.webhook

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class WebhookHttpClient(
    private val webhookConfig: WebhookConfig,
) : WebhookHttpClientInterface {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(webhookConfig.webhookTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override fun send(request: WebhookRequest): WebhookResult {
        val body = request.body
            ?.toRequestBody("application/json".toMediaTypeOrNull())
            ?: "".toRequestBody(null)

        val okRequest = Request.Builder()
            .url(request.url)
            .method(request.method, if (request.method == "GET" || request.method == "HEAD") null else body)
            .apply { request.headers.forEach { (name, value) -> addHeader(name, value) } }
            .build()

        return runCatching {
            httpClient.newCall(okRequest).execute().use { response ->
                val statusCode = response.code
                logger.info { "Webhook response: url=${request.url} method=${request.method} status=$statusCode" }
                if (response.isSuccessful) {
                    WebhookResult.Success(statusCode)
                } else {
                    WebhookResult.Failure(statusCode, "Non-2xx response: $statusCode")
                }
            }
        }.getOrElse { e ->
            when (e) {
                is IOException -> {
                    logger.warn(e) { "Webhook IO error: url=${request.url} method=${request.method}" }
                    WebhookResult.Failure(null, e.message ?: "IO error")
                }
                else -> {
                    logger.warn(e) { "Webhook unexpected error: url=${request.url} method=${request.method}" }
                    WebhookResult.Failure(null, e.message ?: "Unexpected error")
                }
            }
        }
    }
}
