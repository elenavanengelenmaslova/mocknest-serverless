package nl.vintik.mocknest.infra.aws.generation.wsdl

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.WsdlFetchException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.infra.generation.wsdl.WsdlContentFetcher
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlContentFetcherTest {

    private lateinit var wireMockServer: WireMockServer

    // Short timeout for tests to keep them fast
    // Bypass SSRF validation for localhost WireMock in tests
    private val fetcher = WsdlContentFetcher(timeoutMs = 5_000L, urlSafetyValidator = { listOf(java.net.InetAddress.getLoopbackAddress()) })

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun baseUrl() = "http://localhost:${wireMockServer.port()}"

    @Nested
    inner class SuccessfulFetch {

        @Test
        fun `Given valid WSDL URL When fetching Then should return raw WSDL XML`() = runTest {
            // Given
            val wsdlContent = loadWsdl("simple-soap11.wsdl")
            wireMockServer.stubFor(
                get(urlEqualTo("/service.wsdl"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/xml")
                            .withBody(wsdlContent)
                    )
            )

            // When
            val result = fetcher.fetch("${baseUrl()}/service.wsdl")

            // Then
            assertEquals(wsdlContent, result, "Fetched content must exactly match the original WSDL")
        }

        @Test
        fun `Given valid WSDL URL When fetching Then should return content matching original`() = runTest {
            // Given
            val wsdlContent = loadWsdl("calculator-soap11.wsdl")
            wireMockServer.stubFor(
                get(urlEqualTo("/calculator.wsdl"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/xml; charset=utf-8")
                            .withBody(wsdlContent)
                    )
            )

            // When
            val result = fetcher.fetch("${baseUrl()}/calculator.wsdl")

            // Then
            assertEquals(wsdlContent, result, "Fetched content must exactly match the original WSDL")
        }

        @Test
        fun `Given valid WSDL URL When fetching Then should send User-Agent and Accept headers`() = runTest {
            // Given
            val wsdlContent = loadWsdl("simple-soap11.wsdl")
            wireMockServer.stubFor(
                get(urlEqualTo("/headers-check.wsdl"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/xml")
                            .withBody(wsdlContent)
                    )
            )

            // When
            fetcher.fetch("${baseUrl()}/headers-check.wsdl")

            // Then
            wireMockServer.verify(
                getRequestedFor(urlEqualTo("/headers-check.wsdl"))
                    .withHeader("User-Agent", matching("MockNest.*"))
                    .withHeader("Accept", matching(".*text/xml.*"))
            )
        }
    }

    @Nested
    inner class HttpErrorStatus {

        @Test
        fun `Given URL returning 404 When fetching Then should throw WsdlFetchException with status code`() = runTest {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/missing.wsdl"))
                    .willReturn(aResponse().withStatus(404))
            )

            // When / Then
            val ex = assertFailsWith<WsdlFetchException> {
                fetcher.fetch("${baseUrl()}/missing.wsdl")
            }
            assertContains(ex.message ?: "", "404", ignoreCase = true)
        }

        @Test
        fun `Given URL returning 500 When fetching Then should throw WsdlFetchException with status code`() = runTest {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/error.wsdl"))
                    .willReturn(aResponse().withStatus(500))
            )

            // When / Then
            val ex = assertFailsWith<WsdlFetchException> {
                fetcher.fetch("${baseUrl()}/error.wsdl")
            }
            assertContains(ex.message ?: "", "500", ignoreCase = true)
        }

        @Test
        fun `Given URL returning 403 When fetching Then should throw WsdlFetchException with status code`() = runTest {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/forbidden.wsdl"))
                    .willReturn(aResponse().withStatus(403))
            )

            // When / Then
            val ex = assertFailsWith<WsdlFetchException> {
                fetcher.fetch("${baseUrl()}/forbidden.wsdl")
            }
            assertContains(ex.message ?: "", "403", ignoreCase = true)
        }
    }

    @Nested
    inner class UnsafeUrl {

        // Use real URL safety validator for these tests
        private val fetcherWithRealValidator = WsdlContentFetcher(timeoutMs = 5_000L)

        @Test
        fun `Given loopback URL When fetching Then should throw WsdlFetchException before network call`() = runTest {
            // Given - 127.0.0.1 is a loopback address (unsafe)
            val loopbackUrl = "http://127.0.0.1:9999/service.wsdl"

            // When / Then
            val ex = assertFailsWith<WsdlFetchException> {
                fetcherWithRealValidator.fetch(loopbackUrl)
            }
            assertTrue(
                ex.message?.contains("unsafe", ignoreCase = true) == true ||
                    ex.message?.contains("URL", ignoreCase = true) == true,
                "Exception message should indicate unsafe URL, got: ${ex.message}"
            )
        }

        @Test
        fun `Given private IP URL When fetching Then should throw WsdlFetchException before network call`() = runTest {
            // Given - 192.168.1.1 is a private/site-local address (unsafe)
            val privateUrl = "http://192.168.1.1/service.wsdl"

            // When / Then
            assertFailsWith<WsdlFetchException> {
                fetcherWithRealValidator.fetch(privateUrl)
            }
        }

        @Test
        fun `Given 10_x_x_x private IP URL When fetching Then should throw WsdlFetchException`() = runTest {
            // Given - 10.0.0.1 is a private/site-local address (unsafe)
            val privateUrl = "http://10.0.0.1/service.wsdl"

            // When / Then
            assertFailsWith<WsdlFetchException> {
                fetcherWithRealValidator.fetch(privateUrl)
            }
        }
    }

    @Nested
    inner class NonXmlResponse {

        @Test
        fun `Given URL returning non-XML JSON body When fetching Then should throw WsdlFetchException`() = runTest {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/not-wsdl"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "not a WSDL"}""")
                    )
            )

            // When / Then
            val ex = assertFailsWith<WsdlFetchException> {
                fetcher.fetch("${baseUrl()}/not-wsdl")
            }
            assertTrue(
                ex.message?.contains("XML", ignoreCase = true) == true ||
                    ex.message?.contains("valid", ignoreCase = true) == true,
                "Exception message should indicate non-XML content, got: ${ex.message}"
            )
        }

        @Test
        fun `Given URL returning plain text body When fetching Then should throw WsdlFetchException`() = runTest {
            // Given
            wireMockServer.stubFor(
                get(urlEqualTo("/plain-text"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody("This is not XML at all")
                    )
            )

            // When / Then
            assertFailsWith<WsdlFetchException> {
                fetcher.fetch("${baseUrl()}/plain-text")
            }
        }
    }
}
