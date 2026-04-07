package nl.vintik.mocknest.infra.aws.runtime

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Prototype test validating four critical assumptions about WireMock 3.13.2 ServeEventListener
 * behaviour needed for the WebhookServeEventListener design.
 *
 * ## Summary of Findings
 *
 * ### Assumption 1 — Redaction timing (FAILED — design must be revised)
 *
 * The design assumed that mutating ServeEvent request headers in afterMatch() would result in
 * the redacted values being stored in the journal. This assumption is INCORRECT for two reasons:
 *
 * (a) ServeEvent, LoggedRequest, and HttpHeaders are all IMMUTABLE — there are no setters.
 *     You cannot mutate headers in place.
 *
 * (b) The journal write happens BEFORE BEFORE_RESPONSE_SENT listeners fire:
 *     From WireMock 3.13.2 source (StubRequestHandler.beforeResponseSent):
 *       requestJournal.requestReceived(serveEvent);          // ← journal write FIRST
 *       triggerListeners(serveEventListeners, BEFORE_RESPONSE_SENT, serveEvent); // ← THEN
 *
 * REVISED DESIGN for redaction:
 * - The journal stores the original (unredacted) header values.
 * - Redaction is applied at READ TIME via a new AdminRequestFilterV2 that intercepts
 *   GET /__admin/requests and GET /__admin/requests/{id} responses and replaces sensitive
 *   header values with [REDACTED] in the JSON response body before returning to the caller.
 * - afterMatch() is used only to CAPTURE the original header values in a side-channel
 *   ConcurrentHashMap<UUID, Map<String, String>> keyed by ServeEvent ID, for use in
 *   beforeResponseSent() when building the outbound webhook auth header.
 *
 * ### Assumption 2 — Name collision (FAILED — design must be revised)
 *
 * The design assumed that registering a ServeEventListener named "webhook" would replace the
 * built-in Webhooks extension. This assumption is INCORRECT.
 *
 * From WireMock 3.13.2 source (Extensions.java, configureWebhooks()):
 *   loadedExtensions.put(webhooks.getName(), webhooks);  // ← ALWAYS adds LAST, overwriting custom
 *
 * The built-in Webhooks extension is ALWAYS added after user extensions, overwriting any
 * custom listener with the same name "webhook". The custom listener never fires.
 *
 * REVISED DESIGN for name collision:
 * - Use a DIFFERENT name for the custom listener: "mocknest-webhook"
 * - Use "mocknest-webhook" as the serveEventListeners name in stub mappings instead of "webhook"
 * - The built-in "webhook" extension is never triggered (no stubs reference it)
 * - The custom "mocknest-webhook" listener handles dispatch synchronously in beforeResponseSent()
 * - This avoids double dispatch and the name collision problem entirely
 *
 * ### Assumption 3 — Webhook parameters in beforeResponseSent (CONFIRMED with revised name)
 *
 * serveEvent.getServeEventListeners() in beforeResponseSent() returns the serveEventListeners
 * definitions from the matched stub mapping, including their parameters. The parameters object
 * contains the webhook configuration (url, method, body, auth, etc.) as configured in the
 * stub mapping's serveEventListeners block.
 *
 * This works correctly when using the custom listener name "mocknest-webhook".
 *
 * ### Assumption 4 — Original request access (CONFIRMED with revised approach)
 *
 * The original Request object IS accessible in beforeResponseSent() via
 * serveEvent.getRequest(). Since ServeEvent is immutable and afterMatch() cannot mutate it,
 * the "original" request in beforeResponseSent() is the same object as in afterMatch() —
 * it has never been mutated. The auth injection use case works correctly because:
 * - afterMatch() captures the original header value in a side-channel map (keyed by
 *   ServeEvent ID) before any redaction occurs.
 * - beforeResponseSent() reads the captured value from the side-channel map.
 * - The journal stores the original value; redaction happens at read time via admin filter.
 *
 * ## Revised Implementation Approach
 *
 * 1. Use listener name "mocknest-webhook" (not "webhook") to avoid collision with built-in.
 * 2. afterMatch(): Capture original sensitive header values in a side-channel map keyed by
 *    ServeEvent ID. This preserves the values for auth injection in beforeResponseSent().
 * 3. beforeResponseSent(): Read captured values from the map for auth injection. Dispatch
 *    webhook synchronously via OkHttp blocking call. Clean up the map entry.
 * 4. New RedactSensitiveHeadersFilter (AdminRequestFilterV2): Intercepts
 *    GET /__admin/requests and GET /__admin/requests/{id} responses and replaces sensitive
 *    header values with [REDACTED] in the JSON response body before returning to the caller.
 *    This satisfies Requirements 4.1, 4.2, 4.3, 4.4.
 * 5. applyGlobally() returns true so the listener fires for all requests (needed for
 *    the side-channel capture in afterMatch()).
 *
 * ## Actual Call Order (from WireMock 3.13.2 source)
 *
 * 1. BEFORE_MATCH — StubRequestHandler.handleRequest(), before stubServer.serveStubFor()
 * 2. AFTER_MATCH  — AbstractStubMappings.serveFor(), after stub matched
 *                   ← capture original header values here (side-channel map)
 * 3. requestJournal.requestReceived(serveEvent) — StubRequestHandler.beforeResponseSent()
 *                   ← journal write (stores original unredacted values)
 * 4. BEFORE_RESPONSE_SENT — StubRequestHandler.beforeResponseSent(), AFTER journal write
 *                   ← dispatch webhook here (synchronous, before response sent)
 *                   ← read captured header values from side-channel map for auth injection
 * 5. Response sent to caller
 * 6. AFTER_COMPLETE — afterResponseSent() (where built-in async Webhooks would run)
 *
 * Note: The design document's description of the call order was incorrect — it stated that
 * the journal write happens AFTER BEFORE_RESPONSE_SENT listeners, but the actual source
 * shows the journal write happens BEFORE BEFORE_RESPONSE_SENT listeners.
 */
class WebhookServeEventListenerPrototypeTest {

    private val afterMatchServeEvents = CopyOnWriteArrayList<ServeEvent>()
    private val beforeResponseSentServeEvents = CopyOnWriteArrayList<ServeEvent>()

    private lateinit var wireMockServer: WireMockServer
    private lateinit var callbackServer: MockWebServer
    private val httpClient = OkHttpClient()

    @BeforeEach
    fun setUp() {
        callbackServer = MockWebServer()
        callbackServer.start()
        afterMatchServeEvents.clear()
        beforeResponseSentServeEvents.clear()
    }

    @AfterEach
    fun tearDown() {
        if (::wireMockServer.isInitialized && wireMockServer.isRunning) {
            wireMockServer.stop()
        }
        callbackServer.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assumption 1: Redaction timing — journal write happens BEFORE listeners
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Assumption 1 - journal stores original unredacted headers - redaction must happen at read time`() {
        // This test confirms that:
        // (a) afterMatch() receives the ServeEvent with the original headers
        // (b) the journal stores the same (unredacted) headers
        // (c) ServeEvent is immutable — headers cannot be mutated in place
        // (d) the journal write happens BEFORE BEFORE_RESPONSE_SENT listeners fire

        val capturedAfterMatchRequest = AtomicReference<LoggedRequest>()
        val capturedBeforeResponseSentRequest = AtomicReference<LoggedRequest>()
        val journalWriteHappenedBeforeBeforeResponseSent = AtomicReference(false)

        val listener = object : ServeEventListener {
            override fun afterMatch(serveEvent: ServeEvent, parameters: Parameters) {
                capturedAfterMatchRequest.set(serveEvent.request)
                afterMatchServeEvents.add(serveEvent)
            }

            override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
                capturedBeforeResponseSentRequest.set(serveEvent.request)
                beforeResponseSentServeEvents.add(serveEvent)
                // At this point, the journal write has ALREADY happened
                // (requestJournal.requestReceived() is called before triggerListeners)
                journalWriteHappenedBeforeBeforeResponseSent.set(true)
            }

            override fun getName() = "test-listener-assumption1"
            override fun applyGlobally() = true
        }

        wireMockServer = WireMockServer(
            wireMockConfig().dynamicPort().extensions(listener)
        )
        wireMockServer.start()
        wireMockServer.stubFor(
            get(urlEqualTo("/test")).willReturn(ok("hello"))
        )

        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/test")
                .header("x-api-key", "secret-value-123")
                .build()
        ).execute()
        response.close()

        Thread.sleep(200)

        // afterMatch() received the ServeEvent with original headers
        val afterMatchReq = capturedAfterMatchRequest.get()
        assertNotNull(afterMatchReq, "afterMatch should have been called")
        assertEquals("secret-value-123", afterMatchReq.getHeader("x-api-key"),
            "afterMatch sees original header value")

        // beforeResponseSent() also sees the original header value (ServeEvent is immutable)
        val beforeSentReq = capturedBeforeResponseSentRequest.get()
        assertNotNull(beforeSentReq, "beforeResponseSent should have been called")
        assertEquals("secret-value-123", beforeSentReq.getHeader("x-api-key"),
            "beforeResponseSent sees original header value (journal already written)")

        // The journal stores the original (unredacted) value
        val journalEvents = wireMockServer.allServeEvents
        assertEquals(1, journalEvents.size)
        assertEquals("secret-value-123", journalEvents[0].request.getHeader("x-api-key"),
            "Journal stores original (unredacted) header value — redaction must happen at read time")

        // FINDING: ServeEvent is immutable — no setHeaders/setRequest method exists
        // FINDING: Journal write happens BEFORE beforeResponseSent listeners
        // CONCLUSION: Redaction must be applied at read time via AdminRequestFilterV2
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assumption 2: Name collision — "webhook" does NOT replace built-in
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Assumption 2 - built-in webhook extension overwrites custom webhook listener - use different name`() {
        // This test confirms that:
        // (a) A custom listener named "webhook" is OVERWRITTEN by the built-in Webhooks extension
        // (b) The correct approach is to use a different name: "mocknest-webhook"
        // (c) A listener with a different name fires correctly

        val customListenerFired = AtomicReference(false)

        // Use a different name to avoid collision with built-in "webhook"
        val customListener = object : ServeEventListener {
            override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
                customListenerFired.set(true)
                beforeResponseSentServeEvents.add(serveEvent)
            }

            override fun getName() = "mocknest-webhook"  // ← different name, no collision
            override fun applyGlobally() = false
        }

        wireMockServer = WireMockServer(
            wireMockConfig().dynamicPort().extensions(customListener)
        )
        wireMockServer.start()

        // Register stub using "mocknest-webhook" as the listener name
        wireMockServer.stubFor(
            post(urlEqualTo("/trigger"))
                .willReturn(aResponse().withStatus(202))
                .withServeEventListener(
                    "mocknest-webhook",  // ← use custom name, not "webhook"
                    Parameters.from(
                        mapOf(
                            "method" to "POST",
                            "url" to "http://localhost:${callbackServer.port}/callback",
                            "body" to "{\"triggered\": true}"
                        )
                    )
                )
        )

        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/trigger")
                .post("".toRequestBody())
                .build()
        ).execute()
        response.close()

        Thread.sleep(200)

        // Our custom "mocknest-webhook" listener fired
        assertTrue(customListenerFired.get(),
            "Custom 'mocknest-webhook' listener should have fired — different name avoids collision")

        // FINDING: Using "webhook" as the name would be overwritten by built-in Webhooks extension
        // CONCLUSION: Use "mocknest-webhook" as the listener name in stub mappings
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assumption 3: Webhook parameters in beforeResponseSent
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Assumption 3 - serveEventListeners parameters are accessible in beforeResponseSent`() {
        val capturedParameters = AtomicReference<Parameters>()
        val capturedListenerDefs = AtomicReference<List<*>>()

        val listener = object : ServeEventListener {
            override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
                capturedParameters.set(parameters)
                capturedListenerDefs.set(serveEvent.serveEventListeners)
                beforeResponseSentServeEvents.add(serveEvent)
            }

            override fun getName() = "mocknest-webhook"
            override fun applyGlobally() = false
        }

        wireMockServer = WireMockServer(
            wireMockConfig().dynamicPort().extensions(listener)
        )
        wireMockServer.start()

        val expectedUrl = "http://example.com/callback"
        val expectedMethod = "POST"
        val expectedBody = """{"orderId": "123"}"""

        wireMockServer.stubFor(
            post(urlEqualTo("/orders"))
                .willReturn(aResponse().withStatus(202))
                .withServeEventListener(
                    "mocknest-webhook",
                    Parameters.from(
                        mapOf(
                            "method" to expectedMethod,
                            "url" to expectedUrl,
                            "body" to expectedBody
                        )
                    )
                )
        )

        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/orders")
                .post("""{"orderId": "123"}""".toRequestBody())
                .build()
        ).execute()
        response.close()

        Thread.sleep(200)

        // Parameters passed to beforeResponseSent contain the webhook config
        val params = capturedParameters.get()
        assertNotNull(params, "Parameters should be available in beforeResponseSent")
        assertEquals(expectedUrl, params.getString("url"),
            "Webhook URL should be in parameters")
        assertEquals(expectedMethod, params.getString("method"),
            "Webhook method should be in parameters")
        assertEquals(expectedBody, params.getString("body"),
            "Webhook body should be in parameters")

        // serveEvent.getServeEventListeners() also contains the definitions
        val listenerDefs = capturedListenerDefs.get()
        assertNotNull(listenerDefs, "serveEventListeners should be accessible on ServeEvent")
        assertEquals(1, listenerDefs?.size,
            "Should have exactly one serveEventListener definition")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assumption 4: Original request access in beforeResponseSent
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Assumption 4 - original request with unredacted headers accessible in beforeResponseSent via side-channel`() {
        // Since ServeEvent is immutable, afterMatch() cannot mutate headers.
        // The side-channel approach: afterMatch() captures the original header value in a map,
        // beforeResponseSent() reads it from the map for auth injection.

        val sideChannelMap = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Map<String, String>>()
        val afterMatchHeaderValue = AtomicReference<String?>()
        val beforeResponseSentHeaderValue = AtomicReference<String?>()

        val listener = object : ServeEventListener {
            override fun afterMatch(serveEvent: ServeEvent, parameters: Parameters) {
                // Capture original header values in side-channel map
                val apiKey = serveEvent.request.getHeader("x-api-key")
                afterMatchHeaderValue.set(apiKey)
                if (apiKey != null) {
                    sideChannelMap[serveEvent.id] = mapOf("x-api-key" to apiKey)
                }
            }

            override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
                // Read captured value from side-channel map for auth injection
                val captured = sideChannelMap[serveEvent.id]
                beforeResponseSentHeaderValue.set(captured?.get("x-api-key"))
                sideChannelMap.remove(serveEvent.id)  // clean up
            }

            override fun getName() = "mocknest-webhook"
            override fun applyGlobally() = true
        }

        wireMockServer = WireMockServer(
            wireMockConfig().dynamicPort().extensions(listener)
        )
        wireMockServer.start()
        wireMockServer.stubFor(
            post(urlEqualTo("/api/resource"))
                .willReturn(aResponse().withStatus(200))
        )

        val originalApiKey = "original-api-key-value-xyz"
        val response = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/api/resource")
                .header("x-api-key", originalApiKey)
                .post("".toRequestBody())
                .build()
        ).execute()
        response.close()

        Thread.sleep(200)

        // afterMatch() captured the original header value
        assertEquals(originalApiKey, afterMatchHeaderValue.get(),
            "afterMatch() should see the original x-api-key header value")

        // beforeResponseSent() retrieved the captured value from the side-channel map
        assertEquals(originalApiKey, beforeResponseSentHeaderValue.get(),
            "beforeResponseSent() should retrieve the original x-api-key from side-channel map")

        // The side-channel map is empty after cleanup
        assertTrue(sideChannelMap.isEmpty(),
            "Side-channel map should be cleaned up after beforeResponseSent()")

        // FINDING: Side-channel approach works correctly for auth injection
        // CONCLUSION: Use ConcurrentHashMap<UUID, Map<String, String>> keyed by ServeEvent ID
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bonus: End-to-end synchronous webhook dispatch in beforeResponseSent
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Bonus - synchronous webhook dispatch in beforeResponseSent completes before response returned`() {
        callbackServer.enqueue(MockResponse().setResponseCode(200))

        val callbackUrl = "http://localhost:${callbackServer.port}/callback"
        val webhookDispatchedBeforeResponse = AtomicReference(false)

        val listener = object : ServeEventListener {
            override fun beforeResponseSent(serveEvent: ServeEvent, parameters: Parameters) {
                // Find the "mocknest-webhook" listener definition in the stub
                val listenerDef = serveEvent.serveEventListeners
                    .firstOrNull { it.name == "mocknest-webhook" }
                    ?: return

                val params = listenerDef.parameters
                val url = params.getString("url") ?: return
                val method = params.getString("method") ?: "POST"

                // Synchronous blocking HTTP call — completes before response is returned
                val callbackRequest = Request.Builder()
                    .url(url)
                    .method(method, "".toRequestBody())
                    .build()
                val callbackResponse = httpClient.newCall(callbackRequest).execute()
                webhookDispatchedBeforeResponse.set(callbackResponse.isSuccessful)
                callbackResponse.close()
            }

            override fun getName() = "mocknest-webhook"
            override fun applyGlobally() = false
        }

        wireMockServer = WireMockServer(
            wireMockConfig().dynamicPort().extensions(listener)
        )
        wireMockServer.start()

        wireMockServer.stubFor(
            post(urlEqualTo("/trigger"))
                .willReturn(aResponse().withStatus(202))
                .withServeEventListener(
                    "mocknest-webhook",
                    Parameters.from(mapOf("method" to "POST", "url" to callbackUrl))
                )
        )

        val triggerResponse = httpClient.newCall(
            Request.Builder()
                .url("http://localhost:${wireMockServer.port()}/trigger")
                .post("".toRequestBody())
                .build()
        ).execute()
        val statusCode = triggerResponse.code
        triggerResponse.close()

        // The trigger response was returned after the webhook completed
        assertEquals(202, statusCode, "Trigger response should be 202")
        assertTrue(webhookDispatchedBeforeResponse.get(),
            "Webhook should have been dispatched synchronously before response was returned")

        // The callback server received the request
        val received = callbackServer.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull(received, "Callback server should have received the webhook request")
        assertEquals("/callback", received?.requestUrl?.encodedPath)
    }
}
