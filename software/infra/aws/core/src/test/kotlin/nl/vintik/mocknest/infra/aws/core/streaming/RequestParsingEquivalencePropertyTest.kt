package nl.vintik.mocknest.infra.aws.core.streaming

import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.stream.Stream

/**
 * Property 2: Request Parsing Equivalence
 *
 * For any valid API Gateway proxy request JSON containing an httpMethod (any valid HTTP method),
 * a path (any non-empty string), optional headers, optional multiValueHeaders, optional
 * queryStringParameters, optional multiValueQueryStringParameters, an optional body (plain or
 * base64-encoded), and an isBase64Encoded flag, parsing with ApiGatewayRequestParser SHALL produce
 * an HttpRequest with method, path, decoded body, and merged header/query parameter values identical
 * to what the previous APIGatewayProxyRequestEvent SDK deserialization produced for the same JSON input.
 *
 * **Validates: Requirements 1.2, 2.2, 6.1, 6.2, 6.4, 6.5, 6.6**
 */
@Tag("Feature: response-streaming, Property 2: Request Parsing Equivalence")
class RequestParsingEquivalencePropertyTest {

    private val parser = ApiGatewayRequestParser()

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestParsingTestCases")
    fun `Given API Gateway proxy request JSON When parsed by ApiGatewayRequestParser Then HttpRequest has identical method path body and merged header query values`(
        description: String,
        requestJson: String,
        expectedRequest: HttpRequest,
    ) {
        // Parse the raw JSON input stream
        val input = ByteArrayInputStream(requestJson.toByteArray(Charsets.UTF_8))
        val result = parser.parse(input)

        // Verify method matches
        assertEquals(
            expectedRequest.method,
            result.method,
            "Method mismatch for test case: $description"
        )

        // Verify path matches
        assertEquals(
            expectedRequest.path,
            result.path,
            "Path mismatch for test case: $description"
        )

        // Verify decoded body matches
        assertEquals(
            expectedRequest.body,
            result.body,
            "Body mismatch for test case: $description"
        )

        // Verify headers match
        assertEquals(
            expectedRequest.headers,
            result.headers,
            "Headers mismatch for test case: $description"
        )

        // Verify multi-value headers match
        assertEquals(
            expectedRequest.multiValueHeaders,
            result.multiValueHeaders,
            "Multi-value headers mismatch for test case: $description"
        )

        // Verify query parameters match
        assertEquals(
            expectedRequest.queryParameters,
            result.queryParameters,
            "Query parameters mismatch for test case: $description"
        )

        // Verify multi-value query parameters match
        assertEquals(
            expectedRequest.multiValueQueryParameters,
            result.multiValueQueryParameters,
            "Multi-value query parameters mismatch for test case: $description"
        )
    }

    companion object {
        @JvmStatic
        fun requestParsingTestCases(): Stream<Arguments> = Stream.of(
            // 1. GET with no body
            Arguments.of(
                "GET with no body",
                """{"httpMethod":"GET","path":"/api/users","headers":{"Accept":"application/json"},"multiValueHeaders":{"Accept":["application/json"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.GET,
                    path = "/api/users",
                    headers = mapOf("Accept" to "application/json"),
                    multiValueHeaders = mapOf("Accept" to listOf("application/json")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 2. POST with JSON body
            Arguments.of(
                "POST with JSON body",
                """{"httpMethod":"POST","path":"/api/users","headers":{"Content-Type":"application/json"},"multiValueHeaders":{"Content-Type":["application/json"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"{\"name\":\"John\",\"age\":30}","isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.POST,
                    path = "/api/users",
                    headers = mapOf("Content-Type" to "application/json"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("application/json")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = """{"name":"John","age":30}"""
                )
            ),
            // 3. POST with base64 body
            Arguments.of(
                "POST with base64-encoded body",
                """{"httpMethod":"POST","path":"/api/upload","headers":{"Content-Type":"application/octet-stream"},"multiValueHeaders":{"Content-Type":["application/octet-stream"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"${Base64.getEncoder().encodeToString("Hello, World! Binary data here.".toByteArray())}","isBase64Encoded":true}""",
                HttpRequest(
                    method = HttpMethod.POST,
                    path = "/api/upload",
                    headers = mapOf("Content-Type" to "application/octet-stream"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("application/octet-stream")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = "Hello, World! Binary data here."
                )
            ),
            // 4. PUT with large body
            Arguments.of(
                "PUT with large body",
                buildLargeBodyRequest(),
                HttpRequest(
                    method = HttpMethod.PUT,
                    path = "/api/documents/123",
                    headers = mapOf("Content-Type" to "text/plain"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("text/plain")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = "X".repeat(10_000)
                )
            ),
            // 5. DELETE with empty body
            Arguments.of(
                "DELETE with empty body",
                """{"httpMethod":"DELETE","path":"/api/users/456","headers":{"Authorization":"Bearer token123"},"multiValueHeaders":{"Authorization":["Bearer token123"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.DELETE,
                    path = "/api/users/456",
                    headers = mapOf("Authorization" to "Bearer token123"),
                    multiValueHeaders = mapOf("Authorization" to listOf("Bearer token123")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 6. Request with multi-value headers
            Arguments.of(
                "request with multi-value headers",
                """{"httpMethod":"GET","path":"/api/data","headers":{"Accept":"text/html"},"multiValueHeaders":{"Accept":["application/json","text/html","application/xml"],"Cache-Control":["no-cache","no-store"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.GET,
                    path = "/api/data",
                    headers = mapOf("Accept" to "text/html"),
                    multiValueHeaders = mapOf(
                        "Accept" to listOf("application/json", "text/html", "application/xml"),
                        "Cache-Control" to listOf("no-cache", "no-store")
                    ),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 7. Request with multi-value query params
            Arguments.of(
                "request with multi-value query parameters",
                """{"httpMethod":"GET","path":"/api/search","headers":{},"multiValueHeaders":{},"queryStringParameters":{"q":"kotlin","page":"1"},"multiValueQueryStringParameters":{"q":["kotlin"],"page":["1"],"filter":["active","recent","starred"]},"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.GET,
                    path = "/api/search",
                    headers = emptyMap(),
                    multiValueHeaders = emptyMap(),
                    queryParameters = mapOf("q" to "kotlin", "page" to "1"),
                    multiValueQueryParameters = mapOf(
                        "q" to listOf("kotlin"),
                        "page" to listOf("1"),
                        "filter" to listOf("active", "recent", "starred")
                    ),
                    body = null
                )
            ),
            // 8. Request with special characters in path
            Arguments.of(
                "request with special characters in path",
                """{"httpMethod":"GET","path":"/api/users/john%20doe/profile%3Fdetail","headers":{},"multiValueHeaders":{},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.GET,
                    path = "/api/users/john%20doe/profile%3Fdetail",
                    headers = emptyMap(),
                    multiValueHeaders = emptyMap(),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 9. Request with unicode body
            Arguments.of(
                "request with unicode body",
                """{"httpMethod":"POST","path":"/api/messages","headers":{"Content-Type":"text/plain; charset=utf-8"},"multiValueHeaders":{"Content-Type":["text/plain; charset=utf-8"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"Hello 世界! 🌍🎉 Ñoño café résumé naïve Ω≈ç√∫","isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.POST,
                    path = "/api/messages",
                    headers = mapOf("Content-Type" to "text/plain; charset=utf-8"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("text/plain; charset=utf-8")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = "Hello 世界! 🌍🎉 Ñoño café résumé naïve Ω≈ç√∫"
                )
            ),
            // 10. Minimal request (method + path only)
            Arguments.of(
                "minimal request with method and path only",
                """{"httpMethod":"GET","path":"/"}""",
                HttpRequest(
                    method = HttpMethod.GET,
                    path = "/",
                    headers = emptyMap(),
                    multiValueHeaders = emptyMap(),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 11. Request with all fields populated
            Arguments.of(
                "request with all fields populated",
                """{"httpMethod":"POST","path":"/api/orders","headers":{"Content-Type":"application/json","Authorization":"Bearer abc123","X-Request-Id":"req-001"},"multiValueHeaders":{"Content-Type":["application/json"],"Authorization":["Bearer abc123"],"X-Request-Id":["req-001"],"Accept":["application/json","text/plain"]},"queryStringParameters":{"page":"2","limit":"50"},"multiValueQueryStringParameters":{"page":["2"],"limit":["50"],"sort":["name","date"]},"body":"{\"item\":\"widget\",\"quantity\":5}","isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.POST,
                    path = "/api/orders",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer abc123",
                        "X-Request-Id" to "req-001"
                    ),
                    multiValueHeaders = mapOf(
                        "Content-Type" to listOf("application/json"),
                        "Authorization" to listOf("Bearer abc123"),
                        "X-Request-Id" to listOf("req-001"),
                        "Accept" to listOf("application/json", "text/plain")
                    ),
                    queryParameters = mapOf("page" to "2", "limit" to "50"),
                    multiValueQueryParameters = mapOf(
                        "page" to listOf("2"),
                        "limit" to listOf("50"),
                        "sort" to listOf("name", "date")
                    ),
                    body = """{"item":"widget","quantity":5}"""
                )
            ),
            // 12. PATCH request
            Arguments.of(
                "PATCH request",
                """{"httpMethod":"PATCH","path":"/api/users/789","headers":{"Content-Type":"application/json-patch+json"},"multiValueHeaders":{"Content-Type":["application/json-patch+json"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Jane\"}]","isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.PATCH,
                    path = "/api/users/789",
                    headers = mapOf("Content-Type" to "application/json-patch+json"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("application/json-patch+json")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = """[{"op":"replace","path":"/name","value":"Jane"}]"""
                )
            ),
            // 13. OPTIONS request
            Arguments.of(
                "OPTIONS request",
                """{"httpMethod":"OPTIONS","path":"/api/users","headers":{"Origin":"https://example.com","Access-Control-Request-Method":"POST"},"multiValueHeaders":{"Origin":["https://example.com"],"Access-Control-Request-Method":["POST"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.OPTIONS,
                    path = "/api/users",
                    headers = mapOf(
                        "Origin" to "https://example.com",
                        "Access-Control-Request-Method" to "POST"
                    ),
                    multiValueHeaders = mapOf(
                        "Origin" to listOf("https://example.com"),
                        "Access-Control-Request-Method" to listOf("POST")
                    ),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 14. HEAD request
            Arguments.of(
                "HEAD request",
                """{"httpMethod":"HEAD","path":"/api/health","headers":{},"multiValueHeaders":{},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.HEAD,
                    path = "/api/health",
                    headers = emptyMap(),
                    multiValueHeaders = emptyMap(),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
            // 15. Request with empty string body
            Arguments.of(
                "request with empty string body",
                """{"httpMethod":"POST","path":"/api/empty","headers":{"Content-Type":"text/plain"},"multiValueHeaders":{"Content-Type":["text/plain"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"","isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.POST,
                    path = "/api/empty",
                    headers = mapOf("Content-Type" to "text/plain"),
                    multiValueHeaders = mapOf("Content-Type" to listOf("text/plain")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = ""
                )
            ),
            // 16. TRACE request (additional coverage)
            Arguments.of(
                "TRACE request with path segments",
                """{"httpMethod":"TRACE","path":"/api/debug/trace/info","headers":{"Max-Forwards":"10"},"multiValueHeaders":{"Max-Forwards":["10"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":null,"isBase64Encoded":false}""",
                HttpRequest(
                    method = HttpMethod.TRACE,
                    path = "/api/debug/trace/info",
                    headers = mapOf("Max-Forwards" to "10"),
                    multiValueHeaders = mapOf("Max-Forwards" to listOf("10")),
                    queryParameters = emptyMap(),
                    multiValueQueryParameters = emptyMap(),
                    body = null
                )
            ),
        )

        private fun buildLargeBodyRequest(): String {
            val largeBody = "X".repeat(10_000)
            return """{"httpMethod":"PUT","path":"/api/documents/123","headers":{"Content-Type":"text/plain"},"multiValueHeaders":{"Content-Type":["text/plain"]},"queryStringParameters":null,"multiValueQueryStringParameters":null,"body":"$largeBody","isBase64Encoded":false}"""
        }
    }
}
