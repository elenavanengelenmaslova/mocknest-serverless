package nl.vintik.mocknest.application.generation.validators

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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.stream.Stream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-7: Comprehensive SOAP Mock Validation
 *
 * For any generated WireMock mapping and its source CompactWsdl, the SoapMockValidator
 * should enforce all 7 validation rules simultaneously.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-7")
class SoapMockValidatorPropertyTest {

    private val validator = SoapMockValidator()

    companion object {

        private fun loadWsdl(filename: String): String =
            SoapMockValidatorPropertyTest::class.java.getResource("/wsdl/$filename")?.readText()
                ?: error("WSDL test resource not found: $filename")

        private fun soap11Spec(): APISpecification = APISpecification(
            format = SpecificationFormat.WSDL,
            version = "1.0",
            title = "HelloService",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/hello",
                    method = HttpMethod.POST,
                    operationId = "SayHello",
                    summary = "Say Hello",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", JsonSchema(JsonSchemaType.STRING))),
                    metadata = mapOf("soapAction" to "http://example.com/hello/SayHello")
                )
            ),
            schemas = emptyMap(),
            metadata = mapOf("soapVersion" to "SOAP_1_1", "targetNamespace" to "http://example.com/hello"),
            rawContent = loadWsdl("simple-soap11.wsdl")
        )

        private fun soap12Spec(): APISpecification = APISpecification(
            format = SpecificationFormat.WSDL,
            version = "1.0",
            title = "GreetService",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/greet",
                    method = HttpMethod.POST,
                    operationId = "Greet",
                    summary = "Greet",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", JsonSchema(JsonSchemaType.STRING))),
                    metadata = mapOf("soapAction" to "http://example.com/greet/Greet")
                )
            ),
            schemas = emptyMap(),
            metadata = mapOf("soapVersion" to "SOAP_1_2", "targetNamespace" to "http://example.com/greet"),
            rawContent = loadWsdl("simple-soap12.wsdl")
        )

        private fun mock(id: String, mapping: String): GeneratedMock = GeneratedMock(
            id = id,
            name = "Property-7 Mock",
            namespace = MockNamespace("test"),
            wireMockMapping = mapping,
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test",
                endpoint = EndpointInfo(HttpMethod.POST, "/soap", 200, "text/xml")
            ),
            generatedAt = Instant.now()
        )

        data class ValidationCase(
            val description: String,
            val mock: GeneratedMock,
            val spec: APISpecification,
            val expectValid: Boolean,
            val expectedErrorFragment: String? = null
        )

        @JvmStatic
        fun validationCases(): Stream<ValidationCase> = Stream.of(
            // Case 1: Valid SOAP 1.1 mock — all 7 rules pass
            ValidationCase(
                description = "valid SOAP 1.1 mock",
                mock = mock(
                    "valid-soap11",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml; charset=utf-8" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse><tns:greeting>Hello</tns:greeting></tns:SayHelloResponse></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = true
            ),

            // Case 2: Valid SOAP 1.2 mock — all 7 rules pass
            ValidationCase(
                description = "valid SOAP 1.2 mock",
                mock = mock(
                    "valid-soap12",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/greet",
                        "headers": { "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/greet/Greet\"" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse><tns:message>Hi</tns:message></tns:GreetResponse></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap12Spec(),
                expectValid = true
            ),

            // Case 3: Wrong HTTP method (rule 1)
            ValidationCase(
                description = "wrong HTTP method GET instead of POST",
                mock = mock(
                    "wrong-method",
                    """
                    {
                      "request": {
                        "method": "GET",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "Request method must be POST"
            ),

            // Case 4: Invalid SOAPAction (rule 2)
            ValidationCase(
                description = "invalid SOAPAction not matching any operation",
                mock = mock(
                    "invalid-soapaction",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/UnknownOp" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "does not match any operation in the WSDL"
            ),

            // Case 5: Non-XML response body (rule 3)
            ValidationCase(
                description = "non-XML response body",
                mock = mock(
                    "non-xml-body",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "this is plain text, not XML"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "Response body is not well-formed XML"
            ),

            // Case 6: Wrong SOAP envelope namespace (rule 4)
            ValidationCase(
                description = "wrong SOAP envelope namespace (SOAP 1.2 ns in SOAP 1.1 mock)",
                mock = mock(
                    "wrong-envelope-ns",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "SOAP Envelope element missing or has wrong namespace"
            ),

            // Case 7: Missing Body element (rule 5)
            ValidationCase(
                description = "missing SOAP Body element inside Envelope",
                mock = mock(
                    "missing-body",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Header/></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "SOAP Body element missing inside Envelope"
            ),

            // Case 8: Wrong target namespace in body element (rule 6)
            ValidationCase(
                description = "wrong target namespace in body child element",
                mock = mock(
                    "wrong-target-ns",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wrong=\"http://wrong.ns.com\"><soapenv:Body><wrong:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "Response body element namespace does not match WSDL targetNamespace"
            ),

            // Case 9: Wrong Content-Type header (rule 7)
            ValidationCase(
                description = "wrong Content-Type header for SOAP 1.1 (application/soap+xml instead of text/xml)",
                mock = mock(
                    "wrong-content-type",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/soap+xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "Content-Type header"
            ),

            // Case 10: Multiple errors simultaneously (rules 1, 2, 4, 7)
            ValidationCase(
                description = "multiple errors simultaneously: wrong method, invalid SOAPAction, wrong envelope ns, wrong Content-Type",
                mock = mock(
                    "multiple-errors",
                    """
                    {
                      "request": {
                        "method": "DELETE",
                        "urlPath": "/hello",
                        "headers": { "SOAPAction": { "equalTo": "http://example.com/hello/NoSuchOp" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/json" },
                        "body": "<root><data>not a soap envelope</data></root>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap11Spec(),
                expectValid = false,
                expectedErrorFragment = "Request method must be POST"
            ),

            // Case 11: Valid SOAP 1.2 with action in Content-Type
            ValidationCase(
                description = "valid SOAP 1.2 with action parameter in Content-Type header",
                mock = mock(
                    "valid-soap12-action",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/greet",
                        "headers": { "Content-Type": { "equalTo": "application/soap+xml; charset=utf-8; action=\"Greet\"" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/soap+xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap12Spec(),
                expectValid = true
            ),

            // Case 12: SOAP 1.2 with wrong Content-Type (text/xml instead of application/soap+xml)
            ValidationCase(
                description = "SOAP 1.2 mock with text/xml Content-Type instead of application/soap+xml",
                mock = mock(
                    "soap12-wrong-content-type",
                    """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/greet",
                        "headers": { "Content-Type": { "equalTo": "application/soap+xml; action=\"Greet\"" } }
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "text/xml" },
                        "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
                      }
                    }
                    """.trimIndent()
                ),
                spec = soap12Spec(),
                expectValid = false,
                expectedErrorFragment = "Content-Type header"
            )
        )
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("validationCases")
    fun `Property-7 Comprehensive SOAP Mock Validation`(case: ValidationCase) = runTest {
        val result = validator.validate(case.mock, case.spec)

        if (case.expectValid) {
            assertTrue(
                result.isValid,
                "Expected valid for '${case.description}' but got errors: ${result.errors}"
            )
            assertTrue(result.errors.isEmpty(), "Expected no errors for '${case.description}'")
        } else {
            assertFalse(
                result.isValid,
                "Expected invalid for '${case.description}' but validation passed"
            )
            assertTrue(
                result.errors.isNotEmpty(),
                "Expected non-empty errors for '${case.description}'"
            )
            case.expectedErrorFragment?.let { fragment ->
                assertTrue(
                    result.errors.any { it.contains(fragment) },
                    "Expected error containing '$fragment' for '${case.description}'. Actual errors: ${result.errors}"
                )
            }
        }
    }
}
