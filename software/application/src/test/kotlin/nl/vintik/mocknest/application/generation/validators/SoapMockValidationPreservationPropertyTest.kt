package nl.vintik.mocknest.application.generation.validators

import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.JsonSchema
import nl.vintik.mocknest.domain.generation.JsonSchemaType
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.ResponseDefinition
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-Based Preservation Tests for SoapMockValidator.
 *
 * **Property 2: Preservation** - Valid Mock Validation
 * **Validates: Requirements 3.1, 3.2**
 *
 * IMPORTANT: Follow observation-first methodology
 * - Observe behavior on UNFIXED code for correctly formed SOAP mocks (should pass validation)
 * - Observe behavior on UNFIXED code for mocks with incorrect SOAPAction (should fail validation)
 * - Write property-based tests capturing observed validation behavior
 * - Test with 10+ diverse mock configurations (valid and invalid SOAPAction scenarios)
 *
 * EXPECTED OUTCOME: Tests PASS on unfixed code (confirms baseline validation to preserve)
 *
 * This test suite ensures that after implementing the URL path validation fix (Bug 3),
 * all existing SOAP validation rules continue to work correctly without regressions.
 */
@Tag("soap-wsdl-ai-generation")
@Tag("unit")
@Tag("property-based-test")
@Tag("preservation-test")
class SoapMockValidationPreservationPropertyTest {

    private val validator = SoapMockValidator()


    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    private fun soap12Specification(): APISpecification = APISpecification(
        format = SpecificationFormat.WSDL,
        version = "1.0",
        title = "GreetService",
        endpoints = listOf(
            EndpointDefinition(
                path = "/greet",
                method = HttpMethod.POST,
                operationId = "Greet",
                summary = "Greet operation",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(
                    200 to ResponseDefinition(
                        statusCode = 200,
                        description = "SOAP response",
                        schema = JsonSchema(type = JsonSchemaType.STRING)
                    )
                ),
                metadata = mapOf("soapAction" to "http://example.com/greet/Greet")
            )
        ),
        schemas = emptyMap(),
        metadata = mapOf(
            "soapVersion" to "SOAP_1_2",
            "targetNamespace" to "http://example.com/greet"
        ),
        rawContent = loadWsdl("simple-soap12.wsdl")
    )


    private fun createMock(id: String, wireMockMapping: String): GeneratedMock = GeneratedMock(
        id = id,
        name = "Test SOAP Mock",
        namespace = MockNamespace("test-namespace"),
        wireMockMapping = wireMockMapping,
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "test-wsdl",
            endpoint = EndpointInfo(
                method = HttpMethod.POST,
                path = "/soap",
                statusCode = 200,
                contentType = "text/xml"
            )
        ),
        generatedAt = Instant.now()
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("validSoapMockScenarios")
    fun `Given valid SOAP mock configurations When validating Then should pass validation`(
        scenarioName: String,
        soapVersion: String,
        wireMockMapping: String,
        expectedValid: Boolean
    ) = runTest {
        val spec = soap12Specification()

        val mock = createMock("valid-mock-$scenarioName", wireMockMapping)
        val result = validator.validate(mock, spec)

        assertTrue(
            result.isValid,
            "Scenario '$scenarioName' should pass validation. Errors: ${result.errors}"
        )
        assertTrue(
            result.errors.isEmpty(),
            "Scenario '$scenarioName' should have no errors. Errors: ${result.errors}"
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSoapActionScenarios")
    fun `Given invalid SOAP mock configurations When validating Then should fail validation`(
        scenarioName: String,
        soapVersion: String,
        wireMockMapping: String,
        expectedValid: Boolean
    ) = runTest {
        val spec = soap12Specification()

        val mock = createMock("invalid-mock-$scenarioName", wireMockMapping)
        val result = validator.validate(mock, spec)

        assertFalse(
            result.isValid,
            "Scenario '$scenarioName' should fail validation"
        )
        assertTrue(
            result.errors.isNotEmpty(),
            "Scenario '$scenarioName' should have validation errors"
        )
    }


    companion object {
        @JvmStatic
        fun validSoapMockScenarios() = SoapMockValidationPreservationTestData.validSoapMockScenarios()

        @JvmStatic
        fun invalidSoapActionScenarios() = SoapMockValidationPreservationTestData.invalidSoapActionScenarios()
    }
}
