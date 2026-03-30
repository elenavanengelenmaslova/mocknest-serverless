package nl.vintik.mocknest.infra.aws.generation.wsdl

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.infra.generation.wsdl.WsdlContentFetcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [WsdlSpecificationParser] correctly routes URL input to [WsdlContentFetcherInterface]
 * and inline XML input bypasses the fetcher entirely.
 * Uses [WsdlContentFetcher] (real infra implementation) for URL path tests.
 */
@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class WsdlUrlPathWiringTest {

    private lateinit var wireMockServer: WireMockServer

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
        clearAllMocks()
    }

    @Nested
    inner class UrlPathRouting {

        @Test
        fun `Given HTTP URL content When parsing Then should delegate to content fetcher`() = runTest {
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

            val realFetcher = WsdlContentFetcher(timeoutMs = 5_000L, urlSafetyValidator = {})
            val parser = WsdlSpecificationParser(realFetcher, WsdlParser(), WsdlSchemaReducer())
            val url = "http://localhost:${wireMockServer.port()}/service.wsdl"

            // When
            val spec = parser.parse(url, SpecificationFormat.WSDL)

            // Then — fetcher was called (WireMock received the GET request)
            wireMockServer.verify(getRequestedFor(urlEqualTo("/service.wsdl")))
            assertEquals(SpecificationFormat.WSDL, spec.format)
            assertTrue(spec.endpoints.isNotEmpty())
        }

        @Test
        fun `Given HTTP URL content When parsing Then should produce same result as inline XML`() = runTest {
            // Given
            val wsdlContent = loadWsdl("calculator-soap11.wsdl")
            wireMockServer.stubFor(
                get(urlEqualTo("/calculator.wsdl"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/xml")
                            .withBody(wsdlContent)
                    )
            )

            val realFetcher = WsdlContentFetcher(timeoutMs = 5_000L, urlSafetyValidator = {})
            val parser = WsdlSpecificationParser(realFetcher, WsdlParser(), WsdlSchemaReducer())
            val url = "http://localhost:${wireMockServer.port()}/calculator.wsdl"

            // When — parse via URL path
            val specFromUrl = parser.parse(url, SpecificationFormat.WSDL)
            // Parse via inline XML path
            val specFromInline = parser.parse(wsdlContent, SpecificationFormat.WSDL)

            // Then — both paths produce equivalent results
            assertEquals(specFromInline.title, specFromUrl.title)
            assertEquals(specFromInline.endpoints.size, specFromUrl.endpoints.size)
            assertEquals(
                specFromInline.endpoints.map { it.operationId }.toSet(),
                specFromUrl.endpoints.map { it.operationId }.toSet()
            )
        }
    }

    @Nested
    inner class InlineXmlBypassesFetcher {

        @Test
        fun `Given inline XML content When parsing Then should not call content fetcher`() = runTest {
            // Given
            val mockFetcher: WsdlContentFetcherInterface = mockk(relaxed = true)
            val parser = WsdlSpecificationParser(mockFetcher, WsdlParser(), WsdlSchemaReducer())
            val wsdlXml = loadWsdl("simple-soap11.wsdl")

            // When
            parser.parse(wsdlXml, SpecificationFormat.WSDL)

            // Then — fetcher was never called
            coVerify(exactly = 0) { mockFetcher.fetch(any()) }
        }

        @Test
        fun `Given inline XML starting with xml declaration When parsing Then should not call content fetcher`() =
            runTest {
                // Given
                val mockFetcher: WsdlContentFetcherInterface = mockk(relaxed = true)
                val parser = WsdlSpecificationParser(mockFetcher, WsdlParser(), WsdlSchemaReducer())
                val wsdlXml = loadWsdl("multi-operation-soap11.wsdl")

                // When
                parser.parse(wsdlXml, SpecificationFormat.WSDL)

                // Then
                coVerify(exactly = 0) { mockFetcher.fetch(any()) }
            }
    }
}
