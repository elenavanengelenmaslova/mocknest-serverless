package nl.vintik.mocknest.infra.aws.generation.wsdl

import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SoapVersion
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

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
            VersionTestCase("simple-soap11.wsdl", SoapVersion.SOAP_1_1),
            VersionTestCase("simple-soap12.wsdl", SoapVersion.SOAP_1_2),
            VersionTestCase("multi-operation-soap11.wsdl", SoapVersion.SOAP_1_1),
            VersionTestCase("multi-operation-soap12.wsdl", SoapVersion.SOAP_1_2),
            VersionTestCase("calculator-soap11.wsdl", SoapVersion.SOAP_1_1),
            VersionTestCase("weather-soap12.wsdl", SoapVersion.SOAP_1_2),
            // mixed-version.wsdl has both SOAP 1.1 and 1.2 bindings; parser defaults to SOAP_1_1
            VersionTestCase("mixed-version.wsdl", SoapVersion.SOAP_1_1)
        )
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL test file not found: $filename")

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
