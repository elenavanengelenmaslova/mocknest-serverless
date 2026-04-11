package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.extension.ServeEventListenerDefinition
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Synchronous webhook dispatch listener.
 *
 * Registered as a [ServeEventListener] — WireMock calls [beforeResponseSent] before
 * sending the response to the caller, allowing synchronous outbound HTTP dispatch.
 *
 * Also captures original request headers in [beforeResponseSent] for auth injection,
 * storing them in [capturedHeaders] keyed by [ServeEvent.id].
 *
 * Requirements: 1.1–1.6 (webhook-support spec)
 */
class WebhookServeEventListener(
    private val webhookHttpClient: WebhookHttpClientInterface,
    private val webhookConfig: WebhookConfig,
) : ServeEventListener {

    /**
     * Stores original request headers keyed by [ServeEvent.id].
     * Used to pass pre-redaction headers from [beforeResponseSent] to auth injection.
     * Must be cleaned up after each invocation to avoid memory leaks on warm Lambdas.
     */
    internal val capturedHeaders = ConcurrentHashMap<UUID, HttpHeaders>()

    override fun getName(): String = "webhook"

    override fun applyGlobally(): Boolean = true

    /**
     * Called by WireMock before the response is sent to the caller.
     *
     * Fix 1.1: Uses `return@runCatching` for early exits so the `capturedHeaders.remove`
     * cleanup at the end of the method always executes on every code path.
     *
     * Fix 1.2: All log call sites use [redactUrl] to strip query string and fragment
     * before emitting the URL, preventing PII leakage to CloudWatch.
     */
    override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
        // Capture headers before any mutation
        capturedHeaders[serveEvent.id] = serveEvent.request.headers

        runCatching {
            // Check if this stub has a "webhook" serveEventListener configured
            val listeners: List<ServeEventListenerDefinition> = serveEvent.serveEventListeners
            if (listeners.none { it.name == "webhook" }) {
                // Fix 1.1: return@runCatching instead of bare `return` — does NOT exit
                // beforeResponseSent, so capturedHeaders.remove() below still executes.
                return@runCatching
            }

            val webhookParams: Parameters = listeners.first { it.name == "webhook" }.parameters
                ?: Parameters.empty()
            val url = webhookParams.getString("url")
                ?: run {
                    logger.info { "No url in webhook parameters for serveEvent=${serveEvent.id} — skipping dispatch" }
                    // Fix 1.1: return@runCatching for consistent cleanup
                    return@runCatching
                }

            val method = webhookParams.getString("method") ?: "POST"
            val body = webhookParams.getString("body")

            val outboundHeaders = buildOutboundHeaders(serveEvent, webhookParams)

            val request = WebhookRequest(
                url = url,
                method = method,
                headers = outboundHeaders,
                body = body,
            )

            // Fix 1.2: redactUrl strips query string and fragment before logging
            val safeUrl = redactUrl(url)
            logger.info { "Dispatching webhook: url=$safeUrl method=$method serveEvent=${serveEvent.id}" }

            when (val result = webhookHttpClient.send(request)) {
                is WebhookResult.Success ->
                    logger.info { "Webhook delivered: url=$safeUrl status=${result.statusCode}" }
                is WebhookResult.Failure ->
                    logger.warn { "Webhook failed: url=$safeUrl status=${result.statusCode} message=${result.message}" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Webhook dispatch failed for serveEvent=${serveEvent.id}" }
        }

        // Cleanup — always executes now that early exits use return@runCatching (Fix 1.1)
        capturedHeaders.remove(serveEvent.id)
    }

    private fun buildOutboundHeaders(serveEvent: ServeEvent, params: Parameters): Map<String, String> {
        val base = mutableMapOf<String, String>()
        // Add any statically configured headers from params
        @Suppress("UNCHECKED_CAST")
        val configuredHeaders = params.get("headers") as? Map<String, String> ?: emptyMap()
        base.putAll(configuredHeaders)
        return base
    }
}

/**
 * Strips query string and fragment from [url], returning only scheme + host + port + path.
 *
 * Fix 1.2: Used at all log call sites in [WebhookServeEventListener] to prevent PII
 * (tokens, tenant IDs) from being written to CloudWatch Logs.
 *
 * Examples:
 * - `https://api.example.com/hook?token=secret` → `https://api.example.com/hook`
 * - `https://api.example.com:8443/hook#section` → `https://api.example.com:8443/hook`
 * - `https://api.example.com/hook` → `https://api.example.com/hook` (unchanged)
 */
fun redactUrl(url: String): String = runCatching {
    val uri = URI(url)
    URI(uri.scheme, uri.authority, uri.path, null, null).toString()
}.getOrElse {
    url.substringBefore('?').substringBefore('#')
}
