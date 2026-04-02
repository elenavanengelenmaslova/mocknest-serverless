package nl.vintik.mocknest.application.generation.validators

import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

/**
 * Test data for SoapMockValidationPreservationPropertyTest.
 * Contains 10+ diverse SOAP 1.2 mock configurations for property-based testing.
 * 
 * IMPORTANT: We ONLY support SOAP 1.2. All test scenarios use SOAP 1.2 only.
 */
object SoapMockValidationPreservationTestData {

    @JvmStatic
    fun validSoapMockScenarios(): Stream<Arguments> = Stream.of(
        // Scenario 1: SOAP 1.2 with correct action and path
        Arguments.of(
            "SOAP 1.2 - Valid action and path",
            "soap12",
            """
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
            """.trimIndent(),
            true
        ),

        // Scenario 2: SOAP 1.2 with action parameter (no quotes)
        Arguments.of(
            "SOAP 1.2 - Action without quotes",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml; action=http://example.com/greet/Greet" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            true
        ),

        // Scenario 3: SOAP 1.2 with empty Body element (rule 6 skipped)
        Arguments.of(
            "SOAP 1.2 - Empty Body element",
            "soap12",
            """
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
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\"><soapenv:Body></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            true
        ),

        // Scenario 4: SOAP 1.2 with charset in Content-Type
        Arguments.of(
            "SOAP 1.2 - Content-Type with charset and action",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml; charset=utf-8; action=\"http://example.com/greet/Greet\"" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml; charset=utf-8" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            true
        ),

        // Scenario 5: SOAP 1.2 with action using contains matcher
        Arguments.of(
            "SOAP 1.2 - Action with contains matcher",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "contains": "action=\"http://example.com/greet/Greet\"" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            true
        ),

        // Scenario 6: SOAP 1.2 with complex response body
        Arguments.of(
            "SOAP 1.2 - Complex response body",
            "soap12",
            """
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
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse><tns:message>Hello World</tns:message><tns:timestamp>2024-01-01T00:00:00Z</tns:timestamp></tns:GreetResponse></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            true
        )
    )


    @JvmStatic
    fun invalidSoapActionScenarios(): Stream<Arguments> = Stream.of(
        // Scenario 1: SOAP 1.2 with invalid action
        Arguments.of(
            "SOAP 1.2 - Invalid action in Content-Type",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml; action=\"UnknownOperation\"" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 2: SOAP 1.2 with missing action parameter
        Arguments.of(
            "SOAP 1.2 - Missing action parameter in Content-Type",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 3: SOAP 1.2 with non-POST method
        Arguments.of(
            "SOAP 1.2 - Non-POST method",
            "soap12",
            """
            {
              "request": {
                "method": "GET",
                "urlPath": "/greet",
                "headers": {
                  "Content-Type": { "equalTo": "application/soap+xml; action=\"http://example.com/greet/Greet\"" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 4: SOAP 1.2 with malformed XML
        Arguments.of(
            "SOAP 1.2 - Malformed XML body",
            "soap12",
            """
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
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "not xml at all"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 5: SOAP 1.2 with wrong Content-Type (text/xml instead of application/soap+xml)
        Arguments.of(
            "SOAP 1.2 - Wrong Content-Type header",
            "soap12",
            """
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
            """.trimIndent(),
            false
        ),

        // Scenario 6: SOAP 1.2 with missing Body element
        Arguments.of(
            "SOAP 1.2 - Missing Body element",
            "soap12",
            """
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
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\"><soapenv:Header/></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 7: SOAP 1.2 with wrong target namespace
        Arguments.of(
            "SOAP 1.2 - Wrong target namespace in body",
            "soap12",
            """
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
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:wrong=\"http://wrong.namespace.com\"><soapenv:Body><wrong:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 8: SOAP 1.2 with wrong envelope namespace (SOAP 1.1 namespace)
        Arguments.of(
            "SOAP 1.2 - Wrong envelope namespace",
            "soap12",
            """
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
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 9: SOAP 1.2 with missing Content-Type header
        Arguments.of(
            "SOAP 1.2 - Missing Content-Type header",
            "soap12",
            """
            {
              "request": {
                "method": "POST",
                "urlPath": "/greet",
                "headers": {}
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/soap+xml" },
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        ),

        // Scenario 10: SOAP 1.2 with missing response headers
        Arguments.of(
            "SOAP 1.2 - Missing response headers",
            "soap12",
            """
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
                "body": "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tns=\"http://example.com/greet\"><soapenv:Body><tns:GreetResponse/></soapenv:Body></soapenv:Envelope>"
              }
            }
            """.trimIndent(),
            false
        )
    )
}
