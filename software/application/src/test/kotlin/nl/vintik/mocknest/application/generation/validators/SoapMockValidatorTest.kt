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
import org.junit.jupiter.api.assertNotNull
import nl.vintik.mocknest.domain.core.HttpMethod
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

    // -------------------------------------------------------------------------
    // Preservation Property Tests: Raw Path and Other Validation Rules
    // -------------------------------------------------------------------------
    // **Property 2: Preservation** - Raw Path and Other Validation Rules
    // **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    //
    // IMPORTANT: Follow observation-first methodology
    // Observe behavior on UNFIXED code for raw WSDL paths and other validation rules
    // Write property-based tests capturing observed behavior patterns
    //
    // EXPECTED OUTCOME: Tests PASS on unfixed code (confirms baseline behavior to preserve)
    // -------------------------------------------------------------------------

    @Nested
    inner class PreservationTests {

        @Test
        fun `Given raw WSDL path When validating Then should accept as valid (backward compatibility)`() = runTest {
            // Property: Raw WSDL paths must continue to validate successfully
            // This is the baseline behavior that must be preserved after the fix
            val spec = soap12Specification() // Uses /greet as the WSDL path
            
            // Using raw WSDL path: /greet (no namespace prefix)
            // Expected behavior: This should be ACCEPTED as valid (current behavior)
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

            val mock = createMock("raw-path-soap12", mapping)
            val result = validator.validate(mock, spec)

            // This should PASS on unfixed code - raw paths are currently accepted
            assertTrue(
                result.isValid,
                "Raw WSDL path '/greet' should be accepted as valid (backward compatibility). " +
                "Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given raw WSDL path for SOAP 1_1 When validating Then should accept as valid`() = runTest {
            // Test backward compatibility with SOAP 1.1
            val spec = soap11Specification() // Uses /hello as the WSDL path
            
            // Using raw WSDL path: /hello (no namespace prefix)
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

            val mock = createMock("raw-path-soap11", mapping)
            val result = validator.validate(mock, spec)

            // This should PASS on unfixed code - raw paths are currently accepted
            assertTrue(
                result.isValid,
                "Raw WSDL path '/hello' should be accepted as valid for SOAP 1.1 (backward compatibility). " +
                "Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given raw WSDL path with multiple endpoints When validating Then should accept as valid`() = runTest {
            // Test backward compatibility with multiple endpoints
            val spec = APISpecification(
                format = SpecificationFormat.WSDL,
                version = "1.0",
                title = "MultiService",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/calculator.asmx",
                        method = HttpMethod.POST,
                        operationId = "Add",
                        summary = "Add operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/calculator/Add")
                    ),
                    EndpointDefinition(
                        path = "/weather.asmx",
                        method = HttpMethod.POST,
                        operationId = "GetWeather",
                        summary = "Get weather operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/weather/GetWeather")
                    )
                ),
                schemas = emptyMap(),
                metadata = mapOf(
                    "soapVersion" to "SOAP_1_2",
                    "targetNamespace" to "http://example.com/multi"
                ),
                rawContent = loadWsdl("simple-soap12.wsdl")
            )
            
            // Test raw path for first endpoint
            val mapping1 = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/calculator.asmx",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/calculator/Add\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/multi\"><soapenv:Body><tns:AddResponse><tns:result>42</tns:result></tns:AddResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val mock1 = createMock("raw-path-calculator", mapping1)
            val result1 = validator.validate(mock1, spec)

            assertTrue(
                result1.isValid,
                "Raw WSDL path '/calculator.asmx' should be accepted (backward compatibility). " +
                "Errors: ${result1.errors}"
            )

            // Test raw path for second endpoint
            val mapping2 = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/weather.asmx",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/weather/GetWeather\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/multi\"><soapenv:Body><tns:GetWeatherResponse><tns:forecast>Sunny</tns:forecast></tns:GetWeatherResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val mock2 = createMock("raw-path-weather", mapping2)
            val result2 = validator.validate(mock2, spec)

            assertTrue(
                result2.isValid,
                "Raw WSDL path '/weather.asmx' should be accepted (backward compatibility). " +
                "Errors: ${result2.errors}"
            )
        }

        @Test
        fun `Given valid POST method When validating Then should accept (method validation preserved)`() = runTest {
            // Property: POST method validation must continue to work unchanged
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

            val result = validator.validate(createMock("valid-post", mapping), spec)

            assertTrue(result.isValid, "Valid POST method should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given invalid GET method When validating Then should reject (method validation preserved)`() = runTest {
            // Property: Method validation must continue to reject non-POST methods
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

            val result = validator.validate(createMock("invalid-get", mapping), spec)

            assertFalse(result.isValid, "GET method should be rejected")
            assertTrue(
                result.errors.any { it.contains("Request method must be POST") },
                "Should have method validation error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given valid SOAPAction When validating Then should accept (SOAPAction validation preserved)`() = runTest {
            // Property: SOAPAction validation must continue to work unchanged
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

            val result = validator.validate(createMock("valid-soapaction", mapping), spec)

            assertTrue(result.isValid, "Valid SOAPAction should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given invalid SOAPAction When validating Then should reject (SOAPAction validation preserved)`() = runTest {
            // Property: SOAPAction validation must continue to reject invalid actions
            val spec = soap11Specification()
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/hello",
                    "headers": {
                      "SOAPAction": { "equalTo": "http://example.com/hello/InvalidOperation" }
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

            assertFalse(result.isValid, "Invalid SOAPAction should be rejected")
            assertTrue(
                result.errors.any { it.contains("does not match any operation in the WSDL") },
                "Should have SOAPAction validation error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given well-formed XML When validating Then should accept (XML validation preserved)`() = runTest {
            // Property: XML well-formedness validation must continue to work unchanged
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

            val result = validator.validate(createMock("valid-xml", mapping), spec)

            assertTrue(result.isValid, "Well-formed XML should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given malformed XML When validating Then should reject (XML validation preserved)`() = runTest {
            // Property: XML validation must continue to reject malformed XML
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
                    "body": "not xml at all"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("malformed-xml", mapping), spec)

            assertFalse(result.isValid, "Malformed XML should be rejected")
            assertTrue(
                result.errors.any { it.contains("not well-formed XML") },
                "Should have XML validation error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given correct SOAP envelope namespace When validating Then should accept (namespace validation preserved)`() = runTest {
            // Property: SOAP envelope namespace validation must continue to work unchanged
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

            val result = validator.validate(createMock("valid-envelope-ns", mapping), spec)

            assertTrue(result.isValid, "Correct SOAP envelope namespace should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given wrong SOAP envelope namespace When validating Then should reject (namespace validation preserved)`() = runTest {
            // Property: Namespace validation must continue to reject wrong envelope namespaces
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

            assertFalse(result.isValid, "Wrong SOAP envelope namespace should be rejected")
            assertTrue(
                result.errors.any { it.contains("SOAP Envelope element missing or has wrong namespace") },
                "Should have envelope namespace validation error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given correct Content-Type When validating Then should accept (Content-Type validation preserved)`() = runTest {
            // Property: Content-Type validation must continue to work unchanged
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

            val result = validator.validate(createMock("valid-content-type", mapping), spec)

            assertTrue(result.isValid, "Correct Content-Type should be accepted. Errors: ${result.errors}")
        }

        @Test
        fun `Given wrong Content-Type When validating Then should reject (Content-Type validation preserved)`() = runTest {
            // Property: Content-Type validation must continue to reject wrong content types
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
                    "headers": { "Content-Type": "application/json" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"http://example.com/hello\"><soapenv:Body><tns:SayHelloResponse/></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val result = validator.validate(createMock("wrong-content-type", mapping), spec)

            assertFalse(result.isValid, "Wrong Content-Type should be rejected")
            assertTrue(
                result.errors.any { it.contains("Content-Type header") && it.contains("does not match SOAP version") },
                "Should have Content-Type validation error. Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given non-SOAP specification When validating Then should pass through unchanged`() = runTest {
            // Property: Non-SOAP mocks must continue to use existing validation logic
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

            val result = validator.validate(createMock("non-soap", mapping), spec)

            assertTrue(result.isValid, "Non-SOAP spec should pass through without SOAP validation")
        }
    }

    // -------------------------------------------------------------------------
    // Bug Condition Exploration: Missing URL/Path Validation (Bug 3)
    // -------------------------------------------------------------------------
    // **Property 1: Bug Condition** - Missing URL/Path Validation
    // **Validates: Requirements 3.1**
    //
    // CRITICAL: This test MUST FAIL on unfixed code - failure confirms the bug exists
    // DO NOT attempt to fix the test or the code when it fails
    // NOTE: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    // GOAL: Surface counterexamples that demonstrate validator accepts wrong URL paths
    //
    // Bug Description: SoapMockValidator validates SOAPAction but not the request URL/path.
    // When a mock has the correct SOAPAction header but an incorrect urlPath, the validation
    // passes even though the mock will never match requests to the correct endpoint path.
    //
    // EXPECTED OUTCOME on UNFIXED code: Test FAILS because validator incorrectly accepts
    // mocks with wrong URL paths as long as SOAPAction is correct.
    //
    // EXPECTED OUTCOME on FIXED code: Test PASSES because validator now checks URL path
    // and rejects mocks with incorrect paths.
    // -------------------------------------------------------------------------

    @Nested
    @Tag("bug-condition-exploration")
    @Tag("property-based-test")
    inner class MissingUrlValidationBugCondition {

        @Test
        fun `Given correct SOAPAction but wrong urlPath When validating Then should fail validation`() = runTest {
            // This test encodes the EXPECTED behavior: mocks with wrong URL paths should be REJECTED
            // On UNFIXED code, this test will FAIL because the validator only checks SOAPAction
            // On FIXED code, this test will PASS because the validator checks both SOAPAction and URL path
            
            val spec = soap11Specification() // Uses /hello as the WSDL path
            
            // Mock has correct SOAPAction but WRONG urlPath
            // Expected behavior: This should be REJECTED (validation should fail)
            // Buggy behavior: This will be ACCEPTED (validation passes despite wrong path)
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/WrongService",
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

            val mock = createMock("wrong-path-correct-action", mapping)
            val result = validator.validate(mock, spec)

            // EXPECTED BEHAVIOR: Mock with wrong URL path should be REJECTED
            // This assertion will FAIL on unfixed code (bug exists), confirming the bug
            // This assertion will PASS on fixed code (bug fixed), confirming the fix works
            assertFalse(
                result.isValid,
                "Mock with urlPath '/WrongService' should fail validation for spec with path '/hello'. " +
                "Bug: Validator only checks SOAPAction, not URL path."
            )
            
            // Verify the error message mentions the URL path mismatch
            assertTrue(
                result.errors.any { it.contains("URL path") && it.contains("/WrongService") },
                "Error should mention wrong URL path. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given correct action but wrong urlPath for SOAP 1_2 When validating Then should fail validation`() = runTest {
            // Test with SOAP 1.2 to verify the bug affects both SOAP versions
            val spec = soap12Specification() // Uses /greet as the WSDL path
            
            // Mock has correct action in Content-Type but WRONG urlPath
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/CalculatorService",
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

            val mock = createMock("wrong-path-correct-action-soap12", mapping)
            val result = validator.validate(mock, spec)

            // EXPECTED BEHAVIOR: Mock with wrong URL path should be REJECTED for SOAP 1.2 too
            assertFalse(
                result.isValid,
                "Mock with urlPath '/CalculatorService' should fail validation for spec with path '/greet'. " +
                "Bug affects SOAP 1.2 as well."
            )
            
            assertTrue(
                result.errors.any { it.contains("URL path") && it.contains("/CalculatorService") },
                "Error should mention wrong URL path. Actual errors: ${result.errors}"
            )
        }

        @Test
        fun `Given multiple endpoints and wrong urlPath When validating Then should fail with clear error`() = runTest {
            // Test with multiple endpoints to verify error message lists all valid paths
            val spec = APISpecification(
                format = SpecificationFormat.WSDL,
                version = "1.0",
                title = "MultiService",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/calculator.asmx",
                        method = HttpMethod.POST,
                        operationId = "Add",
                        summary = "Add operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/calculator/Add")
                    ),
                    EndpointDefinition(
                        path = "/weather.asmx",
                        method = HttpMethod.POST,
                        operationId = "GetWeather",
                        summary = "Get weather operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/weather/GetWeather")
                    )
                ),
                schemas = emptyMap(),
                metadata = mapOf(
                    "soapVersion" to "SOAP_1_2",
                    "targetNamespace" to "http://example.com/multi"
                ),
                rawContent = loadWsdl("simple-soap12.wsdl")
            )
            
            // Mock has correct action but path that doesn't match any endpoint
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/nonexistent.asmx",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/calculator/Add\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/multi\"><soapenv:Body><tns:AddResponse><tns:result>42</tns:result></tns:AddResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val mock = createMock("wrong-path-multi-endpoints", mapping)
            val result = validator.validate(mock, spec)

            // EXPECTED BEHAVIOR: Mock with wrong URL path should be REJECTED
            assertFalse(
                result.isValid,
                "Mock with urlPath '/nonexistent.asmx' should fail validation. " +
                "Expected paths: /calculator.asmx or /weather.asmx"
            )
            
            // Verify error message lists the expected paths
            val error = result.errors.find { it.contains("URL path") }
            assertNotNull(error, "Should have URL path error. Actual errors: ${result.errors}")
            assertTrue(
                error.contains("/calculator.asmx") || error.contains("/weather.asmx"),
                "Error should list valid endpoint paths. Error: $error"
            )
        }

        @Test
        fun `Given url matcher instead of urlPath When validating Then should skip URL validation`() = runTest {
            // Edge case: When using url matcher patterns instead of urlPath, validation should skip URL check
            // This is acceptable because url matchers can use regex patterns that are hard to validate
            val spec = soap12Specification()
            
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "url": { "matches": "/greet.*" },
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/greet/Greet\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soap:Body><tns:GreetResponse><tns:greeting>Hello</tns:greeting></tns:GreetResponse></soap:Body></soap:Envelope>"
                  }
                }
            """.trimIndent()

            val mock = createMock("url-matcher-pattern", mapping)
            val result = validator.validate(mock, spec)

            // URL matchers are skipped from validation (acceptable behavior)
            // The validation should pass because all other rules are satisfied
            // and URL validation is skipped when url is a matcher object
            assertTrue(
                result.isValid,
                "Mocks with url matchers should pass validation (URL check is skipped for matcher objects). " +
                "Errors: ${result.errors}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Bug Condition Exploration: Namespace-Prefixed Path Rejection
    // -------------------------------------------------------------------------
    // **Property 1: Bug Condition** - Namespace-Prefixed Path Rejection
    // **Validates: Requirements 1.2, 2.2**
    //
    // CRITICAL: This test MUST FAIL on unfixed code - failure confirms the bug exists
    // DO NOT attempt to fix the test or the code when it fails
    // NOTE: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    // GOAL: Surface counterexamples that demonstrate the bug exists
    //
    // EXPECTED OUTCOME: Test FAILS with error "Request URL path '/test-namespace/calculator.asmx' does not match any endpoint path in the WSDL"
    // -------------------------------------------------------------------------

    @Nested
    inner class NamespacePrefixedPaths {

        @Test
        fun `Given namespace-prefixed path When validating Then should accept as valid`() = runTest {
            // This test encodes the EXPECTED behavior: namespace-prefixed paths should be accepted
            // On UNFIXED code, this test will FAIL because the validator only accepts raw WSDL paths
            val spec = soap12Specification() // Uses /greet as the WSDL path
            
            // Using namespace-prefixed path: /test-namespace/greet
            // Expected behavior: This should be ACCEPTED as valid
            // Current (buggy) behavior: This will be REJECTED
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/test-namespace/greet",
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

            val mock = createMock("namespace-prefixed-path", mapping)
            val result = validator.validate(mock, spec)

            // EXPECTED BEHAVIOR: namespace-prefixed paths should be accepted
            // This assertion will FAIL on unfixed code, confirming the bug exists
            assertTrue(
                result.isValid,
                "Namespace-prefixed path '/test-namespace/greet' should be accepted as valid. " +
                "Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given namespace-prefixed path for SOAP 1_1 When validating Then should accept as valid`() = runTest {
            // Test with SOAP 1.1 to verify the bug affects both SOAP versions
            val spec = soap11Specification() // Uses /hello as the WSDL path
            
            // Using namespace-prefixed path: /test-namespace/hello
            val mapping = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/test-namespace/hello",
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

            val mock = createMock("namespace-prefixed-path-soap11", mapping)
            val result = validator.validate(mock, spec)

            // EXPECTED BEHAVIOR: namespace-prefixed paths should be accepted for SOAP 1.1 too
            assertTrue(
                result.isValid,
                "Namespace-prefixed path '/test-namespace/hello' should be accepted as valid for SOAP 1.1. " +
                "Errors: ${result.errors}"
            )
        }

        @Test
        fun `Given namespace-prefixed path with multiple endpoints When validating Then should accept as valid`() = runTest {
            // Test with a specification that has multiple endpoints to verify the bug affects all paths
            val spec = APISpecification(
                format = SpecificationFormat.WSDL,
                version = "1.0",
                title = "MultiService",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/calculator.asmx",
                        method = HttpMethod.POST,
                        operationId = "Add",
                        summary = "Add operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/calculator/Add")
                    ),
                    EndpointDefinition(
                        path = "/weather.asmx",
                        method = HttpMethod.POST,
                        operationId = "GetWeather",
                        summary = "Get weather operation",
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(
                            200 to ResponseDefinition(
                                statusCode = 200,
                                description = "SOAP response",
                                schema = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        ),
                        metadata = mapOf("soapAction" to "http://example.com/weather/GetWeather")
                    )
                ),
                schemas = emptyMap(),
                metadata = mapOf(
                    "soapVersion" to "SOAP_1_2",
                    "targetNamespace" to "http://example.com/multi"
                ),
                rawContent = loadWsdl("simple-soap12.wsdl")
            )
            
            // Test namespace-prefixed path for first endpoint
            val mapping1 = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/test-namespace/calculator.asmx",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/calculator/Add\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/multi\"><soapenv:Body><tns:AddResponse><tns:result>42</tns:result></tns:AddResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val mock1 = createMock("namespace-prefixed-calculator", mapping1)
            val result1 = validator.validate(mock1, spec)

            assertTrue(
                result1.isValid,
                "Namespace-prefixed path '/test-namespace/calculator.asmx' should be accepted. " +
                "Errors: ${result1.errors}"
            )

            // Test namespace-prefixed path for second endpoint
            val mapping2 = """
                {
                  "request": {
                    "method": "POST",
                    "urlPath": "/test-namespace/weather.asmx",
                    "headers": {
                      "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/weather/GetWeather\"" }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                    "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/multi\"><soapenv:Body><tns:GetWeatherResponse><tns:forecast>Sunny</tns:forecast></tns:GetWeatherResponse></soapenv:Body></soapenv:Envelope>"
                  }
                }
            """.trimIndent()

            val mock2 = createMock("namespace-prefixed-weather", mapping2)
            val result2 = validator.validate(mock2, spec)

            assertTrue(
                result2.isValid,
                "Namespace-prefixed path '/test-namespace/weather.asmx' should be accepted. " +
                "Errors: ${result2.errors}"
            )
        }
    }
}
