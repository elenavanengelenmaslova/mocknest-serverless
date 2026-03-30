package nl.vintik.mocknest.infra.aws.generation.wsdl

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

/**
 * Property 1: Dual Input Mode Equivalence
 *
 * For any valid WSDL document, whether provided as inline XML content or fetched from a URL
 * that serves the same XML, the system should produce an equivalent CompactWsdl with the same
 * service name, target namespace, SOAP version, operations, and XSD types.
 *
 * Validates: Requirements 1.1, 1.2, 2.6
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-1")
class DualInputModeEquivalencePropertyTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private lateinit var wsdlParser: WsdlParser
        private lateinit var schemaReducer: WsdlSchemaReducer
        private lateinit var fetcher: WsdlContentFetcher
        private lateinit var parser: WsdlSpecificationParser

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
            wsdlParser = WsdlParser()
            schemaReducer = WsdlSchemaReducer()
            // Bypass SSRF validation for localhost WireMock in tests
            fetcher = WsdlContentFetcher(timeoutMs = 5_000L, urlSafetyValidator = {})
            parser = WsdlSpecificationParser(fetcher, wsdlParser, schemaReducer)
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
        }

        @JvmStatic
        fun wsdlFiles(): Stream<String> = Stream.of(
            "simple-soap11.wsdl",
            "simple-soap12.wsdl",
            "multi-operation-soap11.wsdl",
            "calculator-soap11.wsdl",
            "weather-soap12.wsdl"
        )
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @ParameterizedTest
    @MethodSource("wsdlFiles")
    fun `Property 1 - Dual Input Mode Equivalence`(filename: String) = runTest {
        // Given
        val wsdlContent = loadWsdl(filename)
        val path = "/$filename"

        wireMockServer.stubFor(
            get(urlEqualTo(path))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(wsdlContent)
                )
        )

        val url = "http://localhost:${wireMockServer.port()}$path"

        // When — parse via inline XML path
        val specFromInline = parser.parse(wsdlContent, SpecificationFormat.WSDL)
        // When — parse via URL path (same XML served by WireMock)
        val specFromUrl = parser.parse(url, SpecificationFormat.WSDL)

        // Then — both paths produce equivalent CompactWsdl-derived results
        assertEquals(
            specFromInline.title,
            specFromUrl.title,
            "[$filename] serviceName must be equal for both input modes"
        )
        assertEquals(
            specFromInline.metadata["targetNamespace"],
            specFromUrl.metadata["targetNamespace"],
            "[$filename] targetNamespace must be equal for both input modes"
        )
        assertEquals(
            specFromInline.metadata["soapVersion"],
            specFromUrl.metadata["soapVersion"],
            "[$filename] soapVersion must be equal for both input modes"
        )
        assertEquals(
            specFromInline.endpoints.map { it.operationId }.toSet(),
            specFromUrl.endpoints.map { it.operationId }.toSet(),
            "[$filename] operation names must be equal for both input modes"
        )
        assertEquals(
            specFromInline.schemas.keys,
            specFromUrl.schemas.keys,
            "[$filename] XSD type keys must be equal for both input modes"
        )
    }
}
