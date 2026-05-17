package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * WireMock extension that captures chunkedDribbleDelay configuration from the
 * matched stub's ResponseDefinition. Stores it in a thread-local so the
 * streaming handler can read it after routeRequest() completes.
 *
 * This avoids using wireMockServer.allServeEvents (global journal) which
 * returns the wrong event when multiple mappings exist.
 */
class ChunkedDribbleDelayCapture : ResponseDefinitionTransformerV2 {

    override fun getName(): String = "chunked-dribble-delay-capture"

    override fun applyGlobally(): Boolean = true

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        val responseDefinition = serveEvent.responseDefinition
        val chunkedDribbleDelay = responseDefinition?.chunkedDribbleDelay

        if (chunkedDribbleDelay != null) {
            val numberOfChunks = chunkedDribbleDelay.numberOfChunks
            val totalDuration = chunkedDribbleDelay.totalDuration.toLong()

            if (numberOfChunks >= 2 && totalDuration >= 0) {
                logger.debug { "Captured chunkedDribbleDelay: numberOfChunks=$numberOfChunks, totalDuration=$totalDuration" }
                capturedConfig.set(CapturedDribbleConfig(numberOfChunks, totalDuration))
            } else {
                capturedConfig.remove()
            }
        } else {
            capturedConfig.remove()
        }

        // Return the response definition unchanged — we only capture, don't modify
        return responseDefinition
    }

    companion object {
        private val capturedConfig = ThreadLocal<CapturedDribbleConfig?>()

        /**
         * Gets and clears the captured dribble config for the current request.
         * Returns null if no chunkedDribbleDelay was configured on the matched stub.
         */
        fun getAndClear(): CapturedDribbleConfig? {
            val config = capturedConfig.get()
            capturedConfig.remove()
            return config
        }

        /**
         * Clears any captured config (call at start of request to ensure clean state).
         */
        fun clear() {
            capturedConfig.remove()
        }
    }
}

data class CapturedDribbleConfig(
    val numberOfChunks: Int,
    val totalDurationMs: Long,
)
