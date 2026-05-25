package nl.vintik.mocknest.application.generation.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SafeUrlResolverTest {

    @Nested
    inner class IsHttpUrl {

        @Test
        fun `Given valid HTTP URL When checking isHttpUrl Then returns true`() {
            assertTrue(SafeUrlResolver.isHttpUrl("http://example.com/spec.json"))
        }

        @Test
        fun `Given valid HTTPS URL When checking isHttpUrl Then returns true`() {
            assertTrue(SafeUrlResolver.isHttpUrl("https://example.com/spec.json"))
        }

        @Test
        fun `Given HTTPS URL with port and path When checking isHttpUrl Then returns true`() {
            assertTrue(SafeUrlResolver.isHttpUrl("https://api.example.com:8443/v2/openapi.yaml"))
        }

        @Test
        fun `Given FTP URL When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("ftp://example.com/spec.json"))
        }

        @Test
        fun `Given file URL When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("file:///etc/passwd"))
        }

        @Test
        fun `Given data URI When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("data:text/plain;base64,SGVsbG8="))
        }

        @Test
        fun `Given plain text When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("openapi: 3.0.0"))
        }

        @Test
        fun `Given empty string When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl(""))
        }

        @Test
        fun `Given URL with whitespace When checking isHttpUrl Then returns true`() {
            assertTrue(SafeUrlResolver.isHttpUrl("  https://example.com/spec.json  "))
        }

        @Test
        fun `Given malformed URI When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("ht tp://not valid"))
        }
    }

    @Nested
    inner class ValidateUrlSafety {

        @Test
        fun `Given localhost URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://localhost/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given 127_0_0_1 URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://127.0.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given site-local 10_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://10.0.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given site-local 192_168_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://192.168.1.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given link-local 169_254_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://169.254.1.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given multicast IPv4 URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://224.0.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given multicast IPv6 URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://[ff02::1]/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given IPv6 ULA URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://[fd12:3456:789a::1]/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given IPv4 CGNAT URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://100.64.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given IPv4 CGNAT upper bound URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://100.127.255.254/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given FTP scheme When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("ftp://example.com/file")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("Unsupported URL scheme"))
        }

        @Test
        fun `Given URL with no host When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http:///path")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("no host"))
        }

        @Test
        fun `Given malformed URI When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("ht tp://not a valid url")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("Invalid URL"))
        }

        @Test
        fun `Given site-local 172_16_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://172.16.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given valid external URL When validating Then does not throw`() {
            SafeUrlResolver.validateUrlSafety("https://8.8.8.8/api/v3/openapi.json")
        }

        @Test
        fun `Given valid external HTTPS URL with path When validating Then does not throw`() {
            SafeUrlResolver.validateUrlSafety("https://1.1.1.1/dns-query")
        }
    }

    @Nested
    inner class ValidateAndResolve {

        @Test
        fun `Given valid external IP URL When validating Then returns resolved addresses`() {
            val addresses = SafeUrlResolver.validateAndResolve("https://8.8.8.8/api")
            assertTrue(addresses.isNotEmpty(), "Should return at least one resolved address")
        }

        @Test
        fun `Given valid external HTTPS URL with port When validating Then returns resolved addresses`() {
            val addresses = SafeUrlResolver.validateAndResolve("https://1.1.1.1:443/dns-query")
            assertTrue(addresses.isNotEmpty(), "Should return at least one resolved address")
        }

        @Test
        fun `Given loopback URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://127.0.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given private IP URL When validating Then throws UrlResolutionException`() {
            assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://192.168.1.1/api")
            }
        }

        @Test
        fun `Given site-local 172_16_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://172.16.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given link-local 169_254_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://169.254.1.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given multicast IPv4 URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://224.0.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given IPv4 CGNAT URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://100.64.0.1/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given IPv6 ULA URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http://[fd12:3456:789a::1]/api")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("unsafe address"))
        }

        @Test
        fun `Given FTP scheme When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("ftp://example.com/file")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("Unsupported URL scheme"))
        }

        @Test
        fun `Given URL with no host When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("http:///path")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("no host"))
        }

        @Test
        fun `Given malformed URI When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateAndResolve("ht tp://not a valid url")
            }
            val message = exception.message
            assertNotNull(message)
            assertTrue(message.contains("Invalid URL"))
        }
    }

    @Nested
    inner class Fetch {

        private lateinit var wireMockServer: WireMockServer
        private val resolver = SafeUrlResolver()

        @BeforeEach
        fun setUp() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
            // Mock the companion object to bypass SSRF validation for localhost WireMock tests
            mockkObject(SafeUrlResolver)
            every { SafeUrlResolver.validateUrlSafety(any()) } returns Unit
        }

        @AfterEach
        fun tearDown() {
            unmockkObject(SafeUrlResolver)
            wireMockServer.stop()
        }

        private fun wireMockUrl(path: String): String =
            "http://localhost:${wireMockServer.port()}$path"

        @Test
        fun `Given valid URL with 200 response When fetching Then returns body content`() {
            // Given
            val expectedContent = """{"openapi": "3.0.0", "info": {"title": "Test API"}}"""
            wireMockServer.stubFor(
                get(urlEqualTo("/spec.json"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(expectedContent)
                    )
            )

            // When
            val result = resolver.fetch(wireMockUrl("/spec.json"))

            // Then
            assertEquals(expectedContent, result)
        }

        @Test
        fun `Given 404 response When fetching Then throws UrlResolutionException with status code`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/not-found"))
                    .willReturn(aResponse().withStatus(404))
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/not-found"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("404"), "Expected message to contain '404', got: $message")
        }

        @Test
        fun `Given 500 response When fetching Then throws UrlResolutionException with status code`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/server-error"))
                    .willReturn(aResponse().withStatus(500))
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/server-error"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("500"), "Expected message to contain '500', got: $message")
        }

        @Test
        fun `Given 403 response When fetching Then throws UrlResolutionException with status code`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/forbidden"))
                    .willReturn(aResponse().withStatus(403))
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/forbidden"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("403"), "Expected message to contain '403', got: $message")
        }

        @Test
        fun `Given 503 response When fetching Then throws UrlResolutionException with status code`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/unavailable"))
                    .willReturn(aResponse().withStatus(503))
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/unavailable"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("503"), "Expected message to contain '503', got: $message")
        }

        @Test
        fun `Given SocketTimeoutException When fetching Then throws UrlResolutionException with Timeout message`() {
            // Given - use a very short timeout and a delayed response
            wireMockServer.stubFor(
                get(urlEqualTo("/slow"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("slow response")
                            .withFixedDelay(5000)
                    )
            )
            val shortTimeoutResolver = SafeUrlResolver(connectTimeoutMs = 100, readTimeoutMs = 100)

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                shortTimeoutResolver.fetch(wireMockUrl("/slow"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("Timeout"), "Expected message to contain 'Timeout', got: $message")
        }

        @Test
        fun `Given UnknownHostException When fetching Then throws UrlResolutionException with Cannot resolve host`() {
            // Given - use a hostname that cannot be resolved
            // Allow real validateUrlSafety to run for this test (it will throw due to DNS failure)
            unmockkObject(SafeUrlResolver)

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch("https://this-host-definitely-does-not-exist-xyz123.example.invalid/api")
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Cannot resolve host"),
                "Expected message to contain 'Cannot resolve host', got: $message"
            )

            // Re-mock for other tests
            mockkObject(SafeUrlResolver)
            every { SafeUrlResolver.validateUrlSafety(any()) } returns Unit
        }

        @Test
        fun `Given ConnectException When fetching Then throws UrlResolutionException with Connection refused`() {
            // Given - connect to a port that is not listening
            val closedPort = 19999

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch("http://localhost:$closedPort/api")
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Connection refused"),
                "Expected message to contain 'Connection refused', got: $message"
            )
        }

        @Test
        fun `Given SSLException When fetching Then throws UrlResolutionException with SSL TLS error`() {
            // Given - connect via HTTPS to a plain HTTP server (WireMock on HTTP port)
            // When / Then - connecting via HTTPS to an HTTP port causes SSL handshake failure
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch("https://localhost:${wireMockServer.port()}/spec.json")
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("SSL/TLS error"),
                "Expected message to contain 'SSL/TLS error', got: $message"
            )
        }

        @Test
        fun `Given general IOException When fetching Then throws UrlResolutionException with Network error`() {
            // Given - WireMock fault injection causes connection reset (IOException)
            wireMockServer.stubFor(
                get(urlEqualTo("/fault"))
                    .willReturn(
                        aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)
                    )
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/fault"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("Network error"),
                "Expected message to contain 'Network error', got: $message"
            )
        }

        @Test
        fun `Given response exceeding maxResponseBytes When fetching Then throws UrlResolutionException with size exceeded`() {
            // Given - use a small maxResponseBytes to test the limit without generating 10MB
            val smallLimitResolver = SafeUrlResolver(maxResponseBytes = 1024) // 1KB limit
            val largeBody = "x".repeat(2048) // 2KB body exceeds 1KB limit
            wireMockServer.stubFor(
                get(urlEqualTo("/large"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(largeBody)
                    )
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                smallLimitResolver.fetch(wireMockUrl("/large"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(
                message.contains("exceeds maximum size") || message.contains("MB"),
                "Expected message to indicate size exceeded, got: $message"
            )
        }

        @Test
        fun `Given response exactly at size limit When fetching Then returns content successfully`() {
            // Given - use a small limit and a body that fits exactly
            val limitResolver = SafeUrlResolver(maxResponseBytes = 1024) // 1KB limit
            val exactBody = "x".repeat(1024) // exactly 1KB
            wireMockServer.stubFor(
                get(urlEqualTo("/exact-limit"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(exactBody)
                    )
            )

            // When
            val result = limitResolver.fetch(wireMockUrl("/exact-limit"))

            // Then
            assertEquals(exactBody, result)
        }

        @Test
        fun `Given 301 redirect When fetching Then throws UrlResolutionException with status code`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/redirect"))
                    .willReturn(
                        aResponse()
                            .withStatus(301)
                            .withHeader("Location", "/new-location")
                    )
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/redirect"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("301"), "Expected message to contain '301', got: $message")
        }

        @Test
        fun `Given 302 redirect When fetching Then throws UrlResolutionException without following redirect`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/temp-redirect"))
                    .willReturn(
                        aResponse()
                            .withStatus(302)
                            .withHeader("Location", "/other-location")
                    )
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/temp-redirect"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("302"), "Expected message to contain '302', got: $message")
        }

        @Test
        fun `Given 307 redirect When fetching Then throws UrlResolutionException without following redirect`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/temp-redirect-307"))
                    .willReturn(
                        aResponse()
                            .withStatus(307)
                            .withHeader("Location", "/preserved-method")
                    )
            )

            // When / Then
            val ex = assertFailsWith<UrlResolutionException> {
                resolver.fetch(wireMockUrl("/temp-redirect-307"))
            }
            val message = ex.message
            assertNotNull(message)
            assertTrue(message.contains("307"), "Expected message to contain '307', got: $message")
        }

        @Test
        fun `Given localhost URL When fetching without SSRF bypass Then throws UrlResolutionException`() {
            // Given - restore real validation
            unmockkObject(SafeUrlResolver)
            wireMockServer.stubFor(
                get(urlEqualTo("/spec.json"))
                    .willReturn(aResponse().withStatus(200).withBody("content"))
            )

            // When / Then - SafeUrlResolver should reject localhost
            assertFailsWith<UrlResolutionException> {
                resolver.fetch("http://localhost:${wireMockServer.port()}/spec.json")
            }

            // Re-mock for other tests
            mockkObject(SafeUrlResolver)
            every { SafeUrlResolver.validateUrlSafety(any()) } returns Unit
        }
    }
}
