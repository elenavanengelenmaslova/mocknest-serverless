package nl.vintik.mocknest.application.runtime.extensions

/*
 * WebhookServeEventListener
 *
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 * VALIDATED ASSUMPTIONS (from WebhookServeEventListenerPrototypeTest — all 5 tests PASS)
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 *
 * ── Assumption 1: Redaction timing ── FAILED — DESIGN REVISED ────────────────────────────
 *
 * ServeEvent, LoggedRequest, and HttpHeaders are all IMMUTABLE — headers cannot be mutated.
 * The journal write happens BEFORE BEFORE_RESPONSE_SENT listeners fire.
 *
 * REVISED DESIGN: Redaction is applied at READ TIME via RedactSensitiveHeadersFilter
 * (AdminRequestFilterV2). afterMatch() captures original sensitive header values in a
 * side-channel ConcurrentHashMap<UUID, Map<String, String>> for auth injection.
 *
 * ── Assumption 2: Name collision ── FAILED — DESIGN REVISED ─────────────────────────────
 *
 * The built-in Webhooks extension is ALWAYS added last, overwriting any custom listener
 * named "webhook". Use "mocknest-webhook" as the listener name instead.
 *
 * ── Assumption 3: Webhook parameters in beforeResponseSent ── CONFIRMED ───────────────────
 *
 * serveEvent.serveEventListeners in beforeResponseSent() contains the stub's
 * serveEventListeners definitions including their parameters.
 *
 * ── Assumption 4: Original request access ── CONFIRMED WITH REVISED APPROACH ───────────────
 *
 * afterMatch() captures original header values in a side-channel map keyed by ServeEvent ID.
 * beforeResponseSent() reads from the map for auth injection, then cleans up.
 *
 * ── Bonus: Synchronous dispatch ── CONFIRMED ─────────────────────────────────────────────
 *
 * Synchronous blocking HTTP calls in beforeResponseSent() complete before the response is
 * returned to the caller, satisfying Requirements 1.1 and 7.1.
 *
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 * LISTENER NAME: "mocknest-webhook" (not "webhook" — avoids collision with built-in)
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 */

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

private const val LISTENER_NAME = "mocknest-webhook"
private const val REDACTED = "[REDACTED]"

class WebhookServeEventListener(
    private val webhookHttpClient: WebhookHttpClientInterface,
    private val webhookConfig: WebhookConfig,
) : ServeEventListener {

    // Side-channel map: ServeEvent ID → captured sensitive header values (pre-redaction)
    // Used to pass original header values from afterMatch() to beforeResponseSent()
    private val capturedHeaders = ConcurrentHashMap<UUID, Map<String, String>>()

    override fun getName(): String = LISTENER_NAME

    override fun applyGlobally(): Boolean = true

    /**
     * Captures original sensitive header values in the side-channel map before any
     * redaction occurs. The journal write happens after this, storing original values;
     * redaction at read time is handled by RedactSensitiveHeadersFilter.
     */
    override fun afterMatch(serveEvent: ServeEvent, parameters: Parameters) {
        runCatching {
            val request = serveEvent.request
            val sensitiveValues = mutableMapOf<String, String>()
            request.headers.all().forEach { header ->
                val nameLower = header.key().lowercase()
                if (nameLower in webhookConfig.sensitiveHeaders) {
                    sensitiveValues[nameLower] = header.values().firstOrNull() ?: ""
                }
            }
            if (sensitiveValues.isNotEmpty()) {
                capturedHeaders[serveEvent.id] = sensitiveValues
            }
        }.onFailure { e ->
            logger.error(e) { "afterMatch failed for serveEvent=${serveEvent.id}" }
        }
    }

    /**
     * Dispatches the webhook synchronously before the response is returned to the caller.
     * This ensures completion before Lambda freezes (Requirements 1.1, 7.1).
     */
    override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
        runCatching {
            val listenerDef = serveEvent.serveEventListeners
                .firstOrNull { it.name == LISTENER_NAME }
                ?: run {
                    logger.info {
                        "beforeResponseSent: no $LISTENER_NAME listener on serveEvent=${serveEvent.id} " +
                            "stub=${serveEvent.stubMapping?.id} " +
                            "availableListeners=${serveEvent.serveEventListeners.map { it.name }}"
                    }
                    return
                }

            logger.info { "Webhook listener detected on serveEvent=${serveEvent.id} stub=${serveEvent.stubMapping?.id}" }

            val params = listenerDef.parameters
            val rawUrl = params.getString("url") ?: run {
                logger.warn { "Webhook missing 'url' parameter for serveEvent=${serveEvent.id}" }
                return
            }
            val method = params.getString("method") ?: "POST"
            val body = params.get("body") as? String

            val url = rawUrl
            val authConfig = parseAuthConfig(params)
            val outboundHeaders = buildOutboundHeaders(authConfig, serveEvent.id, serveEvent)

            val injectedHeaderNames = outboundHeaders.keys.joinToString()
            val authDescription = when (authConfig) {
                is WebhookAuthConfig.None -> "none"
                is WebhookAuthConfig.Header -> "header(inject=${authConfig.injectName}, source=${authConfig.valueSource::class.simpleName})"
            }
            logger.info {
                "Dispatching webhook: serveEvent=${serveEvent.id} method=$method url=$url " +
                    "auth=$authDescription injectedHeaders=[${injectedHeaderNames}]"
            }

            val request = WebhookRequest(
                url = url,
                method = method,
                headers = outboundHeaders,
                body = body,
                timeoutMs = webhookConfig.webhookTimeoutMs,
            )

            val startMs = System.currentTimeMillis()
            when (val result = webhookHttpClient.send(request)) {
                is WebhookResult.Success -> logger.info {
                    "Webhook delivered: serveEvent=${serveEvent.id} url=$url status=${result.statusCode} elapsedMs=${System.currentTimeMillis() - startMs}"
                }
                is WebhookResult.Failure -> logger.warn {
                    "Webhook failed: serveEvent=${serveEvent.id} url=$url status=${result.statusCode} message=${result.message} elapsedMs=${System.currentTimeMillis() - startMs}"
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "beforeResponseSent failed for serveEvent=${serveEvent.id}" }
        }.also {
            capturedHeaders.remove(serveEvent.id)
        }
    }

    private fun parseAuthConfig(params: Parameters): WebhookAuthConfig {
        val auth = params.get("auth") as? Map<*, *> ?: return WebhookAuthConfig.None
        return when (val type = (auth["type"] as? String)?.lowercase()) {
            null, "none" -> WebhookAuthConfig.None
            "header" -> {
                val inject = auth["inject"] as? Map<*, *>
                val injectName = inject?.get("name") as? String
                val value = auth["value"] as? Map<*, *>
                val source = (value?.get("source") as? String)?.lowercase()
                val headerName = value?.get("headerName") as? String

                if (injectName != null && source == "original_request_header" && headerName != null) {
                    WebhookAuthConfig.Header(
                        injectName = injectName,
                        valueSource = HeaderValueSource.OriginalRequestHeader(headerName = headerName),
                    )
                } else {
                    logger.warn { "Incomplete header auth config: injectName=$injectName source=$source headerName=$headerName" }
                    WebhookAuthConfig.None
                }
            }
            else -> {
                logger.warn { "Unknown auth type '$type'; defaulting to None" }
                WebhookAuthConfig.None
            }
        }
    }

    private fun buildOutboundHeaders(
        authConfig: WebhookAuthConfig,
        serveEventId: UUID,
        serveEvent: ServeEvent,
    ): Map<String, String> {
        return when (authConfig) {
            is WebhookAuthConfig.None -> {
                logger.info { "Auth config: none — no headers injected for serveEvent=$serveEventId" }
                emptyMap()
            }
            is WebhookAuthConfig.Header -> {
                when (val valueSource = authConfig.valueSource) {
                    is HeaderValueSource.OriginalRequestHeader -> {
                        val headerNameLower = valueSource.headerName.lowercase()
                        val fromSideChannel = capturedHeaders[serveEventId]?.get(headerNameLower)
                        val value = fromSideChannel
                            ?: serveEvent.request.getHeader(valueSource.headerName)
                        logger.info {
                            "Auth header injection: injectName=${authConfig.injectName} " +
                                "sourceHeader=${valueSource.headerName} " +
                                "foundInSideChannel=${fromSideChannel != null} " +
                                "valuePresent=${value != null}"
                        }
                        if (value != null) {
                            mapOf(authConfig.injectName to value)
                        } else {
                            logger.warn { "Auth header '${valueSource.headerName}' not found in request for serveEvent=$serveEventId" }
                            emptyMap()
                        }
                    }
                }
            }
        }
    }
}
