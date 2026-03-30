package nl.vintik.mocknest.infra.aws.generation.wsdl

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the URL-based WSDL fetching path.
 * Uses WireMock to serve WSDL content and verifies that the URL path produces
 * the same APISpecification as the inline XML path.
 *
 * Validates: Requirements 2.1, 2.6, 12.10
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
class WsdlUrlPathIntegrationTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var fetcher: WsdlContentFetcher
    private lateinit var parser: WsdlSpecificationParser

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
        // Bypass SSRF validation for localhost WireMock in tests
        fetcher = WsdlContentFetcher(timeoutMs = 5_000L, urlSafetyValidator = {})
        parser = WsdlSpecificationParser(fetcher, WsdlParser(), WsdlSchemaReducer())
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `Given WSDL URL When fetching and parsing Then WsdlContentFetcher performs GET and returns XML`() = runTest {
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
        val url = "http://localhost:${wireMockServer.port()}/calculator.wsdl"

        // When
        val spec = parser.parse(url, SpecificationFormat.WSDL)

        // Then — WireMock received the GET request
        wireMockServer.verify(getRequestedFor(urlEqualTo("/calculator.wsdl")))
        assertEquals(SpecificationFormat.WSDL, spec.format)
        assertTrue(spec.endpoints.isNotEmpty())
    }

    @Test
    fun `Given same WSDL content When parsing via URL and inline Then should produce equivalent APISpecification`() =
        runTest {
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
            val url = "http://localhost:${wireMockServer.port()}/calculator.wsdl"

            // When
            val specFromUrl = parser.parse(url, SpecificationFormat.WSDL)
            val specFromInline = parser.parse(wsdlContent, SpecificationFormat.WSDL)

            // Then — both paths produce equivalent results
            assertEquals(specFromInline.title, specFromUrl.title, "Service name must match")
            assertEquals(
                specFromInline.metadata["targetNamespace"],
                specFromUrl.metadata["targetNamespace"],
                "targetNamespace must match"
            )
            assertEquals(
                specFromInline.metadata["soapVersion"],
                specFromUrl.metadata["soapVersion"],
                "soapVersion must match"
            )
            assertEquals(
                specFromInline.endpoints.size,
                specFromUrl.endpoints.size,
                "Endpoint count must match"
            )
            assertEquals(
                specFromInline.endpoints.map { it.operationId }.toSet(),
                specFromUrl.endpoints.map { it.operationId }.toSet(),
                "Operation names must match"
            )
            assertEquals(
                specFromInline.rawContent,
                specFromUrl.rawContent,
                "rawContent (prettyPrint) must match"
            )
        }
}
