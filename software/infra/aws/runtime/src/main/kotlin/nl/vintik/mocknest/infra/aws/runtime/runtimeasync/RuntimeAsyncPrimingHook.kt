package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.AsyncEventAuth
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Lightweight SnapStart priming hook for the RuntimeAsync Lambda.
 *
 * Only active in the `async` Spring profile. Exercises the three components
 * that are lazily initialized on first use:
 *
 * 1. **Kotlinx Serialization** — deserializes a minimal [AsyncEvent] to warm up
 *    class loading and the serialization descriptor cache.
 *
 * 2. **OkHttp SSL/TLS stack** — sends a request to an unreachable address
 *    (`http://localhost:0/noop`) and expects a connection failure. This forces
 *    OkHttp to initialize its connection pool, dispatcher thread pool, and
 *    SSL context so they are captured in the SnapStart snapshot.
 *
 * 3. **AWS credentials provider chain** — resolves credentials via
 *    [DefaultChainCredentialsProvider] to warm up the chain walk (env vars →
 *    container credentials → IMDS). Failures are silently ignored — the chain
 *    will succeed in the real Lambda environment.
 *
 * All operations are wrapped in `runCatching` so a priming failure never
 * prevents snapshot creation.
 */
@Component
@Profile("async")
open class RuntimeAsyncPrimingHook(
    private val webhookHttpClient: WebhookHttpClientInterface,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (isSnapStartEnvironment()) {
            logger.info { "SnapStart detected — executing RuntimeAsync priming hook" }
            prime()
        } else {
            logger.debug { "Not a SnapStart environment — skipping RuntimeAsync priming hook" }
        }
    }

    fun prime() {
        primeJsonDeserialization()
        primeHttpClient()
        primeCredentialsProvider()
        logger.info { "RuntimeAsync priming completed" }
    }

    private fun primeJsonDeserialization() {
        runCatching {
            val minimalEvent = """{"actionType":"webhook","url":"http://localhost/noop","method":"POST","headers":{},"body":null,"auth":{"type":"none"}}"""
            Json.decodeFromString(AsyncEvent.serializer(), minimalEvent)
            logger.debug { "JSON deserialization primed" }
        }.onFailure { e ->
            logger.warn(e) { "JSON deserialization priming failed — continuing" }
        }
    }

    private fun primeHttpClient() {
        runCatching {
            // Send to an unreachable address — connection failure is expected and desired.
            // This forces OkHttp to initialize its SSL context, connection pool, and
            // dispatcher thread pool so they are captured in the SnapStart snapshot.
            webhookHttpClient.send(
                WebhookRequest(
                    url = "http://localhost:0/noop",
                    method = "POST",
                    headers = emptyMap(),
                    body = null,
                )
            )
            logger.debug { "HTTP client primed (connection attempt completed)" }
        }.onFailure { e ->
            // Connection refused / timeout is the expected outcome — still counts as primed
            logger.debug { "HTTP client primed (expected connection failure: ${e.message})" }
        }
    }

    private fun primeCredentialsProvider() {
        runCatching {
            runBlocking {
                withTimeout(1_000) {
                    DefaultChainCredentialsProvider().resolve()
                }
            }
            logger.debug { "AWS credentials provider chain primed" }
        }.onFailure { e ->
            // Will fail outside Lambda — acceptable, chain will succeed in real environment
            logger.debug { "AWS credentials provider priming skipped (${e.message})" }
        }
    }

    protected open fun isSnapStartEnvironment(): Boolean =
        System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE") == "snap-start"
}
