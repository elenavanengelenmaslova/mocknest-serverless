package nl.vintik.mocknest.infra.aws.generation.wsdl

import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SoapVersion
import nl.vintik.mocknest.domain.generation.WsdlParsingException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Property 5: SOAP Version Detection Correctness
 *
 * For any WSDL document, if the document's binding namespace is
 * `http://schemas.xmlsoap.org/wsdl/soap/` then the detected SoapVersion should be SOAP_1_1,
 * and if the binding namespace is `http://schemas.xmlsoap.org/wsdl/soap12/` then the detected
 * SoapVersion should be SOAP_1_2.
 *
 * Validates: Requirements 9.1, 9.2, 9.5
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-5")
class SoapVersionDetectionPropertyTest {

    private val wsdlParser = WsdlParser()
    private val schemaReducer = WsdlSchemaReducer()

    data class VersionTestCase(val filename: String, val expectedVersion: SoapVersion)

    companion object {
        @JvmStatic
        fun versionTestCases(): Stream<VersionTestCase> = Stream.of(
            VersionTestCase("simple-soap12.wsdl", SoapVersion.SOAP_1_2),
            VersionTestCase("multi-operation-soap12.wsdl", SoapVersion.SOAP_1_2),
            VersionTestCase("calculator-soap12.wsdl", SoapVersion.SOAP_1_2),
            VersionTestCase("weather-soap12.wsdl", SoapVersion.SOAP_1_2)
        )
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

    @Test
    fun `Property 5 - Mixed SOAP versions should select SOAP 1_2`() {
        val wsdlXml = loadWsdl("mixed-version.wsdl")
        val parsedWsdl = wsdlParser.parse(wsdlXml)
        val compactWsdl = schemaReducer.reduce(parsedWsdl)
        assertEquals(
            SoapVersion.SOAP_1_2,
            compactWsdl.soapVersion,
            "Mixed SOAP version WSDL should select SOAP 1.2"
        )
    }

    @Test
    fun `Property 5 - SOAP 1_1 only WSDL should be rejected`() {
        val wsdlXml = loadWsdl("simple-soap11.wsdl")
        val ex = assertFailsWith<WsdlParsingException> {
            wsdlParser.parse(wsdlXml)
        }
        assertTrue(
            ex.message?.contains("Only SOAP 1.2 bindings are supported") == true,
            "Exception should say only SOAP 1.2 bindings are supported, got: ${ex.message}"
        )
    }

    @ParameterizedTest
    @MethodSource("versionTestCases")
    fun `Property 5 - SOAP Version Detection Correctness`(testCase: VersionTestCase) {
        // Given
        val wsdlXml = loadWsdl(testCase.filename)

        // When
        val parsedWsdl = wsdlParser.parse(wsdlXml)
        val compactWsdl = schemaReducer.reduce(parsedWsdl)

        // Then
        assertEquals(
            testCase.expectedVersion,
            compactWsdl.soapVersion,
            "[${testCase.filename}] Expected SOAP version ${testCase.expectedVersion} but got ${compactWsdl.soapVersion}"
        )
    }
}
