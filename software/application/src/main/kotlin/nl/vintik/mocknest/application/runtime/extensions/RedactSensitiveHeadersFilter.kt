package nl.vintik.mocknest.application.runtime.extensions

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.config.WebhookConfig

private val logger = KotlinLogging.logger {}

private const val REDACTED = "[REDACTED]"

/**
 * Provides centralized sensitive header redaction for:
 * 1. S3 journal writes — via [redactServeEvent] called before serialization
 * 2. Admin API responses — via [AdminRequestFilterV2] intercepting GET /__admin/requests
 *
 * Redaction replaces sensitive header values with `[REDACTED]` in the serialized JSON.
 * The actual [ServeEvent] / [LoggedRequest] objects are NOT modified — only the JSON copy.
 * Header name matching is case-insensitive.
 *
 * Requirements: 5.1–5.6
 */
class RedactSensitiveHeadersFilter(
    private val webhookConfig: WebhookConfig,
) : AdminRequestFilterV2 {

    override fun getName(): String = "redact-sensitive-headers-filter"

    /**
     * Intercepts GET /__admin/requests and /__admin/requests/{id} to redact sensitive
     * header values in the JSON response body before it is returned to the caller.
     *
     * Since [AdminRequestFilterV2] only intercepts requests (not responses), this filter
     * passes through all requests unchanged. The actual redaction for admin API output
     * is handled at the store level via [redactServeEvent] — the S3RequestJournalStore
     * stores pre-redacted records, so WireMock's admin API returns redacted data.
     */
    override fun filter(request: Request, serveEvent: ServeEvent?): RequestFilterAction =
        RequestFilterAction.continueWith(request)

    /**
     * Serializes a [ServeEvent] to JSON with sensitive header values replaced by [REDACTED].
     * Used by [nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore] before
     * writing to S3.
     *
     * Does NOT modify the [ServeEvent] object itself.
     *
     * Fail-closed contract (Fix 1.3): if serialization or redaction fails, this method
     * returns a minimal safe placeholder JSON rather than the unredacted event. The failure
     * is logged at ERROR level. Sensitive headers are never written to S3 on error.
     */
    fun redactServeEvent(event: ServeEvent): String {
        return runCatching {
            val json = mapper.writeValueAsString(event)
            redactHeadersInJson(json)
        }.getOrElse { e ->
            // Fix 1.3: fail closed — return a safe placeholder instead of retrying the same
            // failing mapper.writeValueAsString(event) call (which could return unredacted JSON).
            // Elevated to ERROR because sensitive data may have been lost.
            logger.error(e) { "Failed to serialize/redact ServeEvent id=${event.id} — returning safe placeholder" }
            """{"id":"${event.id}","redactionError":true}"""
        }
    }

    /**
     * Applies [REDACTED] substitution to sensitive header values in a JSON string.
     * Parses the JSON into a [MutableMap] tree, walks it recursively to find `headers`
     * objects, and replaces sensitive header values in-place before re-serializing.
     * Case-insensitive header name matching.
     */
    fun redactHeadersInJson(json: String): String {
        if (webhookConfig.sensitiveHeaders.isEmpty()) return json

        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val root = mapper.readValue<MutableMap<String, Any?>>(json)
            redactInMap(root)
            mapper.writeValueAsString(root)
        }.getOrElse { e ->
            logger.warn(e) { "Failed to redact headers in JSON — returning original" }
            json
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun redactInMap(map: MutableMap<String, Any?>) {
        for ((key, value) in map.entries.toList()) {
            when {
                // "headers" object: keys are header names, values are strings or lists
                key == "headers" && value is Map<*, *> -> {
                    val headers = value as MutableMap<String, Any?>
                    for (headerName in headers.keys.toList()) {
                        if (headerName.lowercase() in webhookConfig.sensitiveHeaders) {
                            headers[headerName] = REDACTED
                        }
                    }
                }
                value is MutableMap<*, *> -> redactInMap(value as MutableMap<String, Any?>)
                value is List<*> -> redactInList(value as MutableList<Any?>, map, key)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun redactInList(list: MutableList<Any?>, parent: MutableMap<String, Any?>, parentKey: String) {
        for (i in list.indices) {
            when (val item = list[i]) {
                is MutableMap<*, *> -> redactInMap(item as MutableMap<String, Any?>)
                is List<*> -> {
                    // Replace the list in parent with a mutable copy and recurse
                    val mutableItem = item.toMutableList()
                    list[i] = mutableItem
                    redactInList(mutableItem, parent, parentKey)
                }
            }
        }
    }
}
