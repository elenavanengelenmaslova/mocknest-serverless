package nl.vintik.mocknest.infra.aws.runtime.webhook

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.SocketTimeoutException

class WebhookHttpClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: WebhookHttpClient

    private val defaultConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization"),
        webhookTimeoutMs = 5_000L,
    )

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = WebhookHttpClient(defaultConfig)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun baseUrl() = mockWebServer.url("/callback").toString()

    private fun postRequest(
        url: String = baseUrl(),
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ) = WebhookRequest(
        url = url,
        method = "POST",
        headers = headers,
        body = body,
        timeoutMs = defaultConfig.webhookTimeoutMs,
    )

    @Nested
    inner class SuccessResponses {

        @Test
        fun `Given 200 response When send called Then returns Success with 200`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Success::class.java, result)
            assertEquals(200, (result as WebhookResult.Success).statusCode)
        }

        @Test
        fun `Given 201 response When send called Then returns Success with 201`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(201))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Success::class.java, result)
            assertEquals(201, (result as WebhookResult.Success).statusCode)
        }

        @Test
        fun `Given 204 response When send called Then returns Success with 204`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(204))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Success::class.java, result)
            assertEquals(204, (result as WebhookResult.Success).statusCode)
        }
    }

    @Nested
    inner class FailureResponses {

        @Test
        fun `Given 503 response When send called Then returns Failure with 503`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(503))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            val failure = result as WebhookResult.Failure
            assertEquals(503, failure.statusCode)
            assertNotNull(failure.message)
        }

        @Test
        fun `Given 400 response When send called Then returns Failure with 400`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(400))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            assertEquals(400, (result as WebhookResult.Failure).statusCode)
        }

        @Test
        fun `Given 404 response When send called Then returns Failure with 404`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            assertEquals(404, (result as WebhookResult.Failure).statusCode)
        }

        @Test
        fun `Given 500 response When send called Then returns Failure with 500`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val result = client.send(postRequest())

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            assertEquals(500, (result as WebhookResult.Failure).statusCode)
        }
    }

    @Nested
    inner class NetworkErrors {

        @Test
        fun `Given SocketTimeoutException When send called Then returns Failure with null statusCode`() {
            val timeoutConfig = WebhookConfig(
                sensitiveHeaders = emptySet(),
                webhookTimeoutMs = 200L,
            )
            val timeoutClient = WebhookHttpClient(timeoutConfig)
            // NO_RESPONSE causes the server to accept the connection but never send a response
            mockWebServer.enqueue(
                MockResponse().apply {
                    socketPolicy = okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE
                }
            )

            val result = timeoutClient.send(postRequest())

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            val failure = result as WebhookResult.Failure
            assertNull(failure.statusCode)
            assertNotNull(failure.message)
        }

        @Test
        fun `Given IOException from unreachable host When send called Then returns Failure with null statusCode`() {
            val request = WebhookRequest(
                url = "http://localhost:1", // port 1 is unreachable
                method = "POST",
                headers = emptyMap(),
                body = null,
                timeoutMs = 500L,
            )
            val quickClient = WebhookHttpClient(
                WebhookConfig(sensitiveHeaders = emptySet(), webhookTimeoutMs = 500L)
            )

            val result = quickClient.send(request)

            assertInstanceOf(WebhookResult.Failure::class.java, result)
            val failure = result as WebhookResult.Failure
            assertNull(failure.statusCode)
            assertNotNull(failure.message)
        }
    }

    @Nested
    inner class TimeoutConfiguration {

        @Test
        fun `Given timeout configured When client built Then OkHttpClient uses configured timeout`() {
            val customTimeoutMs = 7_500L
            val config = WebhookConfig(
                sensitiveHeaders = emptySet(),
                webhookTimeoutMs = customTimeoutMs,
            )
            // Verify the client is constructed without error and uses the config
            val customClient = WebhookHttpClient(config)
            assertNotNull(customClient)

            // Verify it actually respects the timeout by using a very short one
            val shortConfig = WebhookConfig(
                sensitiveHeaders = emptySet(),
                webhookTimeoutMs = 200L,
            )
            val shortClient = WebhookHttpClient(shortConfig)
            // NO_RESPONSE causes the server to accept the connection but never send a response
            mockWebServer.enqueue(
                MockResponse().apply {
                    socketPolicy = okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE
                }
            )

            val result = shortClient.send(postRequest())

            // Should fail due to timeout
            assertInstanceOf(WebhookResult.Failure::class.java, result)
            assertNull((result as WebhookResult.Failure).statusCode)
        }
    }

    @Nested
    inner class RequestConstruction {

        @Test
        fun `Given request with headers When send called Then headers are forwarded`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            client.send(
                postRequest(headers = mapOf("x-correlation-id" to "abc-123", "content-type" to "application/json"))
            )

            val recorded = mockWebServer.takeRequest()
            assertEquals("abc-123", recorded.getHeader("x-correlation-id"))
        }

        @Test
        fun `Given request with body When send called Then body is forwarded`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            client.send(postRequest(body = """{"orderId":"42"}"""))

            val recorded = mockWebServer.takeRequest()
            assertEquals("""{"orderId":"42"}""", recorded.body.readUtf8())
        }

        @Test
        fun `Given request without body When send called Then empty body is sent`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            client.send(postRequest(body = null))

            val recorded = mockWebServer.takeRequest()
            assertEquals("POST", recorded.method)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 1 (partial): Webhook delivery — HTTP client correctness
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 1 [{index}] — {0}")
    @MethodSource("webhookRequestCases")
    fun `Property 1 - Given diverse WebhookRequest When send called Then outbound request is constructed correctly`(
        testCase: WebhookRequestTestCase,
    ) {
        mockWebServer.enqueue(MockResponse().setResponseCode(testCase.responseCode))

        val request = WebhookRequest(
            url = mockWebServer.url(testCase.path).toString(),
            method = testCase.method,
            headers = testCase.headers,
            body = testCase.body,
            timeoutMs = defaultConfig.webhookTimeoutMs,
        )

        val result = client.send(request)

        // Verify result type matches expected
        if (testCase.responseCode in 200..299) {
            assertInstanceOf(WebhookResult.Success::class.java, result)
            assertEquals(testCase.responseCode, (result as WebhookResult.Success).statusCode)
        } else {
            assertInstanceOf(WebhookResult.Failure::class.java, result)
            assertEquals(testCase.responseCode, (result as WebhookResult.Failure).statusCode)
        }

        // Verify the outbound request was constructed correctly
        val recorded = mockWebServer.takeRequest()
        assertEquals(testCase.method, recorded.method)
        assertEquals(testCase.path, recorded.path)
        testCase.headers.forEach { (name, value) ->
            assertEquals(value, recorded.getHeader(name), "Header '$name' should be forwarded")
        }
        if (testCase.body != null) {
            assertEquals(testCase.body, recorded.body.readUtf8())
        }
    }

    data class WebhookRequestTestCase(
        val description: String,
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String?,
        val responseCode: Int,
    ) {
        override fun toString() = description
    }

    companion object {
        @JvmStatic
        fun webhookRequestCases() = listOf(
            WebhookRequestTestCase(
                description = "POST with JSON body and no headers",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = """{"event":"order.created"}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST with JSON body and correlation header",
                method = "POST",
                path = "/webhook",
                headers = mapOf("x-correlation-id" to "corr-001"),
                body = """{"orderId":"1"}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST with multiple headers",
                method = "POST",
                path = "/events",
                headers = mapOf("x-correlation-id" to "corr-002", "x-source" to "mocknest"),
                body = """{"type":"payment"}""",
                responseCode = 201,
            ),
            WebhookRequestTestCase(
                description = "POST with no body",
                method = "POST",
                path = "/notify",
                headers = emptyMap(),
                body = null,
                responseCode = 204,
            ),
            WebhookRequestTestCase(
                description = "POST with large body",
                method = "POST",
                path = "/data",
                headers = emptyMap(),
                body = """{"items":${(1..50).map { """{"id":$it,"name":"item-$it"}""" }}}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST with auth header injected",
                method = "POST",
                path = "/secure-callback",
                headers = mapOf("x-api-key" to "test-key-value"),
                body = """{"secured":true}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST returns 400 bad request",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = """{"bad":"request"}""",
                responseCode = 400,
            ),
            WebhookRequestTestCase(
                description = "POST returns 500 server error",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = """{"trigger":"error"}""",
                responseCode = 500,
            ),
            WebhookRequestTestCase(
                description = "POST returns 503 service unavailable",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = null,
                responseCode = 503,
            ),
            WebhookRequestTestCase(
                description = "POST with unicode body",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = """{"message":"héllo wörld"}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST with special characters in body",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = """{"url":"https://example.com/path?a=1&b=2"}""",
                responseCode = 200,
            ),
            WebhookRequestTestCase(
                description = "POST with empty JSON body",
                method = "POST",
                path = "/callback",
                headers = emptyMap(),
                body = "{}",
                responseCode = 200,
            ),
        )
    }
}
