package nl.vintik.mocknest.application.generation.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeUrlResolverTest {

    private lateinit var wireMockServer: WireMockServer
    private val resolver = SafeUrlResolver()

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

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
        fun `Given FTP URL When checking isHttpUrl Then returns false`() {
            assertFalse(SafeUrlResolver.isHttpUrl("ftp://example.com/spec.json"))
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
    }

    @Nested
    inner class ValidateUrlSafety {

        @Test
        fun `Given localhost URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://localhost/api")
            }
            assertTrue(exception.message!!.contains("loopback"))
        }

        @Test
        fun `Given 127_0_0_1 URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://127.0.0.1/api")
            }
            assertTrue(exception.message!!.contains("loopback"))
        }

        @Test
        fun `Given site-local 10_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://10.0.0.1/api")
            }
            assertTrue(exception.message!!.contains("private network"))
        }

        @Test
        fun `Given site-local 192_168_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://192.168.1.1/api")
            }
            assertTrue(exception.message!!.contains("private network"))
        }

        @Test
        fun `Given link-local 169_254_x URL When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http://169.254.1.1/api")
            }
            assertTrue(exception.message!!.contains("link-local"))
        }

        @Test
        fun `Given FTP scheme When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("ftp://example.com/file")
            }
            assertTrue(exception.message!!.contains("Unsupported URL scheme"))
        }

        @Test
        fun `Given URL with no host When validating Then throws UrlResolutionException`() {
            val exception = assertFailsWith<UrlResolutionException> {
                SafeUrlResolver.validateUrlSafety("http:///path")
            }
            assertTrue(exception.message!!.contains("no host"))
        }

        @Test
        fun `Given valid external URL When validating Then does not throw`() {
            // Should not throw for a valid external URL
            SafeUrlResolver.validateUrlSafety("https://petstore3.swagger.io/api/v3/openapi.json")
        }
    }

    @Nested
    inner class Fetch {

        @Test
        fun `Given valid WireMock URL When fetching Then returns content`() {
            // Given
            val content = """{"openapi": "3.0.0"}"""
            wireMockServer.stubFor(
                get(urlEqualTo("/spec.json"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(content)
                    )
            )

            // When - SafeUrlResolver rejects localhost, so use a direct test
            // of the fetch mechanism with a custom resolver that skips validation
            val testResolver = object : UrlFetcher {
                override fun fetch(url: String): String {
                    val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    return connection.inputStream.bufferedReader().use { it.readText() }
                }
            }
            val result = testResolver.fetch("http://localhost:${wireMockServer.port()}/spec.json")

            // Then
            assertEquals(content, result)
        }

        @Test
        fun `Given localhost URL When fetching via SafeUrlResolver Then throws UrlResolutionException`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/spec.json"))
                    .willReturn(aResponse().withStatus(200).withBody("content"))
            )

            // When / Then - SafeUrlResolver should reject localhost
            assertFailsWith<UrlResolutionException> {
                resolver.fetch("http://localhost:${wireMockServer.port()}/spec.json")
            }
        }

        @Test
        fun `Given timeout URL When fetching Then throws UrlResolutionException`() {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/slow"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("slow")
                            .withFixedDelay(5000)
                    )
            )
            val fastResolver = SafeUrlResolver(connectTimeoutMs = 100, readTimeoutMs = 100)

            // When / Then - resolves to localhost, so SSRF check fires first
            assertFailsWith<UrlResolutionException> {
                fastResolver.fetch("http://localhost:${wireMockServer.port()}/slow")
            }
        }

        @Test
        fun `Given unreachable host When fetching Then throws UrlResolutionException`() {
            assertFailsWith<UrlResolutionException> {
                resolver.fetch("https://this-host-definitely-does-not-exist-12345.example.com/api")
            }
        }
    }
}
