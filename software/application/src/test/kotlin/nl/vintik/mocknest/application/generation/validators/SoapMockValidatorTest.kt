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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SoapMockValidator.
 *
 * All mocks are derived from inline WSDL XML loaded from src/test/resources/wsdl/.
 * Tests cover all 7 SOAP validation rules and multiple-error collection.
 */
@Tag("soap-wsdl-ai-generation")
@Tag("unit")
class SoapMockValidatorTest {

    private val validator = SoapMockValidator()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: error("WSDL test resource not found: $filename")

    /**
     * Builds a minimal SOAP 1.1 APISpecification from the simple-soap11.wsdl.
     * Service: HelloService, targetNamespace: http://example.com/hello
     * Operation: SayHello, soapAction: http://example.com/hello/SayHello
     */
    private fun soap11Specification(): APISpecification = APISpecification(
        format = SpecificationFormat.WSDL,
        version = "1.0",
        title = "HelloService",
        endpoints = listOf(
            EndpointDefinition(
                path = "/hello",
                method = HttpMethod.POST,
                operationId = "SayHello",
                summary = "Say Hello operation",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(
                    200 to ResponseDefinition(
                        statusCode = 200,
                        description = "SOAP response",
                        schema = JsonSchema(type = JsonSchemaType.STRING)
                    )
                ),
                metadata = mapOf("soapAction" to "http://example.com/hello/SayHello")
            )
        ),
        schemas = emptyMap(),
        metadata = mapOf(
            "soapVersion" to "SOAP_1_1",
            "targetNamespace" to "http://example.com/hello"
        ),
        rawContent = loadWsdl("simple-soap11.wsdl")
    )

    /**
     * Builds a minimal SOAP 1.2 APISpecification from the simple-soap12.wsdl.
     * Service: GreetService, targetNamespace: http://example.com/greet
     * Operation: Greet, soapAction: http://example.com/greet/Greet
     */
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
        namespace = MockNamespace("test"),
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

    // -------------------------------------------------------------------------
    // Valid mock tests
    // -------------------------------------------------------------------------

    @Nested
    inner class ValidMocks {

        @Test
        fun `Given valid SOAP 1_1 mock When validating Then all 7 rules pass`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse><tns:greeting>Hello</tns:greeting></tns:SayHelloResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("valid-soap11", mapping), spec)

            assertTrue(result.isValid, "Valid SOAP 1.1 mock should pass. Errors: ${result.errors}")
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given valid SOAP 1_2 mock When validating Then all 7 rules pass`() = runTest {
            val spec = soap12Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/greet",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/greet/Greet\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse><tns:message>Hello</tns:message></tns:GreetResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("valid-soap12", mapping), spec)

            assertTrue(result.isValid, "Valid SOAP 1.2 mock should pass. Errors: ${result.errors}")
            assertTrue(result.errors.isEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // Rule 1: Request method must be POST
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule1RequestMethod {

        @Test
        fun `Given non-POST method When validating Then rule 1 fails with expected message`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-method", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it == "Request method must be POST, found: GET" },
                "Expected rule 1 error message. Actual errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 1b: Request URL/path matches endpoint path
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule1bUrlPath {

        @Test
        fun `Given correct SOAPAction but wrong urlPath When validating Then rule 1b fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/wrong-path",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse><tns:greeting>Hello</tns:greeting></tns:SayHelloResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-path", mapping), spec)

            assertFalse(result.isValid, "Mock with wrong URL path should fail validation")
            assertTrue(
                result.errors.any { it.contains("URL path") && it.contains("/wrong-path") },
                "Error should mention wrong URL path. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given correct urlPath When validating Then rule 1b passes`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse><tns:greeting>Hello</tns:greeting></tns:SayHelloResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("correct-path", mapping), spec)

            assertTrue(result.isValid, "Mock with correct URL path should pass. Errors: ${result.errors}")
        }
    }

    // -------------------------------------------------------------------------
    // Rule 2: SOAPAction references a valid operation
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule2SoapAction {

        @Test
        fun `Given invalid SOAPAction When validating Then rule 2 fails with operation name in message`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/NonExistentOperation" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("invalid-soapaction", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("does not match any operation in the WSDL") },
                "Expected rule 2 error. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given invalid SOAP 1_2 action in Content-Type When validating Then rule 2 fails`() = runTest {
            val spec = soap12Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/greet",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"UnknownOp\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("invalid-soap12-action", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("does not match any operation in the WSDL") },
                "Expected rule 2 error for SOAP 1.2. Actual errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 3: Response body is well-formed XML
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule3WellFormedXml {

        @Test
        fun `Given non-XML response body When validating Then rule 3 fails with parse error message`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "this is not xml at all"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("non-xml-body", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.startsWith("Response body is not well-formed XML") },
                "Expected rule 3 error. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given malformed XML response body When validating Then rule 3 fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<unclosed>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("malformed-xml-body", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.startsWith("Response body is not well-formed XML") },
                "Expected rule 3 error for malformed XML. Actual errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 4: SOAP Envelope with correct namespace
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule4EnvelopeNamespace {

        @Test
        fun `Given wrong envelope namespace When validating Then rule 4 fails with expected vs found namespaces`() = runTest {
            val spec = soap11Specification()
            // Using SOAP 1.2 namespace in a SOAP 1.1 mock
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-envelope-ns", mapping), spec)

            assertFalse(result.isValid)
            val error = result.errors.find { it.contains("SOAP Envelope element missing or has wrong namespace") }
            assertTrue(error != null, "Expected rule 4 error. Actual errors: ${result.errors}")
            assertTrue(
                error.contains("http://schemas.xmlsoap.org/soap/envelope/"),
                "Error should contain expected namespace. Error: $error"
            )
        }

        @Test
        fun `Given non-Envelope root element When validating Then rule 4 fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<root><data>hello</data></root>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("non-envelope-root", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("SOAP Envelope element missing or has wrong namespace") },
                "Expected rule 4 error. Actual errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 5: Envelope contains Body element
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule5BodyElement {

        @Test
        fun `Given missing Body element When validating Then rule 5 fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Header/></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("missing-body", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it == "SOAP Body element missing inside Envelope" },
                "Expected rule 5 error. Actual errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 6: Body element uses correct target namespace
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule6TargetNamespace {

        @Test
        fun `Given wrong target namespace in body element When validating Then rule 6 fails`() = runTest {
            val spec = soap11Specification()
            // Body child uses wrong namespace
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wrong=\"http://wrong.namespace.com\"><soapenv:Body><wrong:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-target-ns", mapping), spec)

            assertFalse(result.isValid)
            val error = result.errors.find { it.contains("Response body element namespace does not match WSDL targetNamespace") }
            assertTrue(error != null, "Expected rule 6 error. Actual errors: ${result.errors}")
            assertTrue(
                error.contains("http://example.com/hello"),
                "Error should contain expected target namespace. Error: $error"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 7: Content-Type header matches SOAP version
    // -------------------------------------------------------------------------

    @Nested
    inner class Rule7ContentType {

        @Test
        fun `Given wrong Content-Type for SOAP 1_1 When validating Then rule 7 fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-content-type-soap11", mapping), spec)

            assertFalse(result.isValid)
            val error = result.errors.find { it.contains("Content-Type header") && it.contains("does not match SOAP version") }
            assertTrue(error != null, "Expected rule 7 error. Actual errors: ${result.errors}")
            assertTrue(
                error.contains("text/xml"),
                "Error should mention expected content type. Error: $error"
            )
        }

        @Test
        fun `Given wrong Content-Type for SOAP 1_2 When validating Then rule 7 fails`() = runTest {
            val spec = soap12Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/greet",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/greet/Greet\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-content-type-soap12", mapping), spec)

            assertFalse(result.isValid)
            val error = result.errors.find { it.contains("Content-Type header") && it.contains("does not match SOAP version") }
            assertTrue(error != null, "Expected rule 7 error. Actual errors: ${result.errors}")
            assertTrue(
                error.contains("application/soap+xml"),
                "Error should mention expected content type. Error: $error"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Multiple errors collected in a single result
    // -------------------------------------------------------------------------

    @Nested
    inner class MultipleErrors {

        @Test
        fun `Given mock violating multiple rules When validating Then all errors returned together`() = runTest {
            val spec = soap11Specification()
            // Violates: rule 1 (GET), rule 2 (invalid SOAPAction), rule 4 (wrong namespace), rule 7 (wrong Content-Type)
            val mapping = """
                {
                  "request": {
                    "method": "GET",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/NonExistent" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "body": "<root><data>not a soap envelope</data></root>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("multiple-errors", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(result.errors.size >= 3, "Expected at least 3 errors. Actual: ${result.errors}")
            assertTrue(
                result.errors.any { it.contains("Request method must be POST") },
                "Should include rule 1 error. Errors: ${result.errors}"
            )
            assertTrue(
                result.errors.any { it.contains("does not match any operation in the WSDL") },
                "Should include rule 2 error. Errors: ${result.errors}"
            )
            assertTrue(
                result.errors.any { it.contains("SOAP Envelope element missing or has wrong namespace") },
                "Should include rule 4 error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given mock with wrong method and non-XML body When validating Then both errors returned`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "PUT",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "plain text response"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("method-and-xml-errors", mapping), spec)

            assertFalse(result.isValid)
            assertEquals(2, result.errors.size, "Expected exactly 2 errors. Actual: ${result.errors}")
            assertTrue(result.errors.any { it.contains("Request method must be POST, found: PUT") })
            assertTrue(result.errors.any { it.startsWith("Response body is not well-formed XML") })
        }
    }

    // -------------------------------------------------------------------------
    // Non-WSDL format passthrough
    // -------------------------------------------------------------------------

    @Nested
    inner class NonWsdlFormat {

        @Test
        fun `Given non-WSDL specification When validating Then returns valid without checking`() = runTest {
            val spec = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "3.0.0",
                title = "REST API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/test",
                        method = HttpMethod.GET,
                        operationId = "getTest",
                        summary = null,
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(200, "OK", JsonSchema(type = JsonSchemaType.STRING))
                        )
                    )
                ),
                schemas = emptyMap()
            )
            val mapping = """{"request":{"method":"GET"},"response":{"status":200}}"""

            val result = validator.validate(createMock("non-wsdl", mapping), spec)

            assertTrue(result.isValid, "Non-WSDL spec should pass through without validation")
        }
    }

    // -------------------------------------------------------------------------
    // Coverage gap: matcher value patterns (contains, matches, JsonArray, JsonNull)
    // -------------------------------------------------------------------------

    @Nested
    inner class MatcherValuePatterns {

        @Test
        fun `Given SOAPAction with contains matcher When validating Then should match operation`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "headers": {
                      "SOAPAction": { "contains": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("contains-matcher", mapping), spec)

            assertTrue(result.isValid, "contains matcher should match operation. Errors: ${result.errors}")
        }

        @Test
        fun `Given SOAPAction with matches regex matcher When validating Then should report invalid action`() = runTest {
            // The 'matches' matcher extracts the regex pattern string (e.g. ".*SayHello.*")
            // which does not match the operation name via string comparison — this is expected behavior
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "headers": {
                      "SOAPAction": { "matches": ".*SayHello.*" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("matches-matcher", mapping), spec)

            // The regex pattern ".*SayHello.*" is extracted as a literal string and does not
            // match the operation name "SayHello" via string comparison — validator reports invalid
            assertFalse(result.isValid, "Regex pattern string should not match operation name via string comparison")
            assertTrue(result.errors.any { it.contains("SOAPAction") })
        }
    }

    // -------------------------------------------------------------------------
    // Coverage gap: Content-Type header absent in response
    // -------------------------------------------------------------------------

    @Nested
    inner class ContentTypeAbsent {

        @Test
        fun `Given response with no headers section When validating Then Content-Type validation fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("no-headers", mapping), spec)

            // No headers section → Content-Type is required, validation must fail
            assertFalse(result.isValid, "Missing headers section should produce a Content-Type validation failure")
            assertTrue(result.errors.any { it.contains("Content-Type") }, "Error should mention Content-Type. Errors: ${result.errors}")
        }

        @Test
        fun `Given response headers without Content-Type When validating Then Content-Type validation fails`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "X-Custom": "value" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("no-content-type", mapping), spec)

            // No Content-Type entry → validation must fail
            assertFalse(result.isValid, "Missing Content-Type header should produce a validation failure")
            assertTrue(result.errors.any { it.contains("Content-Type") }, "Error should mention Content-Type. Errors: ${result.errors}")
        }
    }

    // -------------------------------------------------------------------------
    // Coverage gap: Body element with no child elements (rule 6 skipped)
    // -------------------------------------------------------------------------

    @Nested
    inner class BodyWithNoChildren {

        @Test
        fun `Given SOAP Body with no child elements When validating Then rule 6 is skipped`() = runTest {
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/SayHello" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "text/xml" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("empty-body", mapping), spec)

            // Empty Body → rule 6 (target namespace check) is skipped, other rules pass
            assertTrue(result.isValid, "Empty SOAP Body should skip rule 6. Errors: ${result.errors}")
        }
    }

    // -------------------------------------------------------------------------
    // Coverage gap: missing request/response sections
    // -------------------------------------------------------------------------

    @Nested
    inner class MissingMappingSections {

        @Test
        fun `Given mapping with no request section When validating Then returns invalid with descriptive error`() =
            runTest {
                val spec = soap11Specification()
                val mapping = """{"response":{"status":200,"body":"<x/>"}}"""

                val result = validator.validate(createMock("no-request", mapping), spec)

                assertFalse(result.isValid)
                assertTrue(result.errors.any { it.contains("Missing request section") })
            }

        @Test
        fun `Given mapping with no response section When validating Then returns invalid with descriptive error`() =
            runTest {
                val spec = soap11Specification()
                val mapping = """{"request":{"method":"POST"}}"""

                val result = validator.validate(createMock("no-response", mapping), spec)

                assertFalse(result.isValid)
                assertTrue(result.errors.any { it.contains("Missing response section") })
            }

        @Test
        fun `Given completely invalid JSON mapping When validating Then returns invalid with error`() = runTest {
            val spec = soap11Specification()
            val mapping = "not-valid-json"

            val result = validator.validate(createMock("invalid-json", mapping), spec)

            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }
    }
}
