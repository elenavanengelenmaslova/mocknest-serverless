package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.wiremock.webhooks.WebhookDefinition
import org.wiremock.webhooks.WebhookTransformer

private val logger = KotlinLogging.logger {}

private const val NO_OP_URL = "http://localhost:0/mocknest-noop"

/**
 * WebhookAsyncEventPublisher intercepts WireMock's built-in "webhook" dispatch.
 *
 * Registered as a [WebhookTransformer] — WireMock's built-in [Webhooks] extension calls
 * [transform] on all registered transformers before executing the outbound HTTP call.
 *
 * In [transform]:
 * 1. Extracts the webhook URL, method, headers, body, and auth from the [WebhookDefinition]
 * 2. Publishes an [AsyncEvent] to SQS
 * 3. Redirects the definition to a no-op URL so the built-in HTTP call is harmless
 *
 * Note: [transform] runs BEFORE WireMock's template engine resolves expressions, so
 * template expressions (e.g. `{{originalRequest.body}}`) are forwarded as-is to the
 * [AsyncEvent]. Template resolution is a future enhancement.
 *
 * Also implements [ServeEventListener] for completeness, but [afterComplete] is not the
 * primary dispatch path — [transform] is.
 */
class WebhookAsyncEventPublisher(
    private val sqsPublisher: SqsPublisherInterface,
    private val queueUrl: String,
) : WebhookTransformer, ServeEventListener {

    override fun getName(): String = "webhook"

    override fun applyGlobally(): Boolean = false

    // ── WebhookTransformer ────────────────────────────────────────────────────

    /**
     * Called by WireMock's built-in Webhooks extension before the outbound HTTP call.
     * Publishes an [AsyncEvent] to SQS and redirects the definition to a no-op URL.
     */
    override fun transform(serveEvent: ServeEvent, webhookDefinition: WebhookDefinition): WebhookDefinition {
        logger.info { "WebhookTransformer intercepting webhook for serveEvent=${serveEvent.id}" }

        val url = webhookDefinition.url
            ?: run {
                logger.info { "No url in webhook definition for serveEvent=${serveEvent.id} — skipping SQS publish" }
                return webhookDefinition.withUrl(NO_OP_URL)
            }

        val method = webhookDefinition.method ?: "POST"
        val headers = webhookDefinition.headers
            ?.all()
            ?.associate { header -> header.key() to header.firstValue() }
            ?: emptyMap()
        val body = webhookDefinition.body
        val auth = extractAuthFromDefinition(webhookDefinition)
        val event = AsyncEvent(
            actionType = "webhook",
            url = url,
            method = method,
            headers = headers,
            body = body,
            auth = auth,
        )

        val json = Json.encodeToString(event)
        runBlocking { sqsPublisher.publish(queueUrl, json) }
        logger.info { "AsyncEvent published to SQS for serveEvent=${serveEvent.id} url=$url method=$method" }

        return webhookDefinition.withUrl(NO_OP_URL)
    }

    // ── ServeEventListener ────────────────────────────────────────────────────

    /**
     * Not the primary dispatch path — [transform] handles SQS publishing.
     * This is kept for completeness and future use.
     */
    override fun afterComplete(serveEvent: ServeEvent, parameters: Parameters) {
        // SQS publishing is handled in transform() — nothing to do here
    }

    private fun extractAuthFromDefinition(definition: WebhookDefinition): AsyncEventAuth {
        val extraParams = definition.extraParameters ?: return AsyncEventAuth(type = "none")
        val auth = extraParams.get("auth") as? Map<*, *> ?: return AsyncEventAuth(type = "none")
        return when ((auth["type"] as? String)?.lowercase()) {
            "aws_iam" -> AsyncEventAuth(
                type = "aws_iam",
                region = auth["region"] as? String,
                service = auth["service"] as? String,
            )
            else -> AsyncEventAuth(type = "none")
        }
    }
}
