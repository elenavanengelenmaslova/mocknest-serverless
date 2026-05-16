package nl.vintik.mocknest.infra.aws.core.streaming

import nl.vintik.mocknest.domain.core.HttpMethod
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiGatewayRequestParserTest {

    private val parser = ApiGatewayRequestParser()

    private fun jsonToInputStream(json: String): InputStream =
        ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

    @Nested
    inner class FieldExtraction {

        @Test
        fun `Given valid GET request When parsing Then extracts method and path`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/mocknest/api/users"
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(HttpMethod.GET, result.method)
            assertEquals("/mocknest/api/users", result.path)
        }

        @Test
        fun `Given POST request with body When parsing Then extracts body`() {
            val json = """
                {
                    "httpMethod": "POST",
                    "path": "/mocknest/api/users",
                    "body": "{\"name\":\"John\"}"
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(HttpMethod.POST, result.method)
            assertEquals("/mocknest/api/users", result.path)
            assertEquals("{\"name\":\"John\"}", result.body)
        }

        @Test
        fun `Given request with headers When parsing Then extracts headers`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "headers": {
                        "Content-Type": "application/json",
                        "Accept": "text/html"
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals("application/json", result.headers["Content-Type"])
            assertEquals("text/html", result.headers["Accept"])
        }

        @Test
        fun `Given request with query parameters When parsing Then extracts query params`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/search",
                    "queryStringParameters": {
                        "page": "1",
                        "limit": "20"
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals("1", result.queryParameters["page"])
            assertEquals("20", result.queryParameters["limit"])
        }

        @Test
        fun `Given request with null body When parsing Then body is null`() {
            val json = """
                {
                    "httpMethod": "DELETE",
                    "path": "/api/items/42",
                    "body": null
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(HttpMethod.DELETE, result.method)
            assertNull(result.body)
        }

        @Test
        fun `Given request with all HTTP methods When parsing Then resolves correctly`() {
            val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")

            methods.forEach { method ->
                val json = """
                    {
                        "httpMethod": "$method",
                        "path": "/test"
                    }
                """.trimIndent()

                val result = parser.parse(jsonToInputStream(json))
                assertEquals(HttpMethod.resolve(method), result.method)
            }
        }

        @Test
        fun `Given request with case-insensitive method When parsing Then resolves correctly`() {
            val json = """
                {
                    "httpMethod": "get",
                    "path": "/test"
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(HttpMethod.GET, result.method)
        }

        @Test
        fun `Given minimal request with only method and path When parsing Then returns HttpRequest with defaults`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/"
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(HttpMethod.GET, result.method)
            assertEquals("/", result.path)
            assertEquals(emptyMap(), result.headers)
            assertEquals(emptyMap(), result.multiValueHeaders)
            assertEquals(emptyMap(), result.queryParameters)
            assertEquals(emptyMap(), result.multiValueQueryParameters)
            assertNull(result.body)
        }
    }

    @Nested
    inner class Base64BodyDecoding {

        @Test
        fun `Given base64 encoded body When parsing Then decodes body`() {
            val originalBody = "Hello, World!"
            val encodedBody = Base64.getEncoder().encodeToString(originalBody.toByteArray())

            val json = """
                {
                    "httpMethod": "POST",
                    "path": "/api/upload",
                    "body": "$encodedBody",
                    "isBase64Encoded": true
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(originalBody, result.body)
        }

        @Test
        fun `Given non-base64 body with isBase64Encoded false When parsing Then returns body as-is`() {
            val json = """
                {
                    "httpMethod": "POST",
                    "path": "/api/data",
                    "body": "plain text body",
                    "isBase64Encoded": false
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals("plain text body", result.body)
        }

        @Test
        fun `Given base64 encoded JSON body When parsing Then decodes correctly`() {
            val originalBody = """{"key":"value","number":42}"""
            val encodedBody = Base64.getEncoder().encodeToString(originalBody.toByteArray())

            val json = """
                {
                    "httpMethod": "POST",
                    "path": "/api/json",
                    "body": "$encodedBody",
                    "isBase64Encoded": true
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(originalBody, result.body)
        }

        @Test
        fun `Given invalid base64 body with isBase64Encoded true When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "httpMethod": "POST",
                    "path": "/api/upload",
                    "body": "not-valid-base64!!!@@@",
                    "isBase64Encoded": true
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
        }

        @Test
        fun `Given null body with isBase64Encoded true When parsing Then body is null`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "body": null,
                    "isBase64Encoded": true
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertNull(result.body)
        }
    }

    @Nested
    inner class MultiValueHeaderAndQueryParameterMerging {

        @Test
        fun `Given request with multiValueHeaders When parsing Then maps correctly`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "multiValueHeaders": {
                        "Accept": ["application/json", "text/html"],
                        "X-Custom": ["value1", "value2", "value3"]
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(listOf("application/json", "text/html"), result.multiValueHeaders["Accept"])
            assertEquals(listOf("value1", "value2", "value3"), result.multiValueHeaders["X-Custom"])
        }

        @Test
        fun `Given request with multiValueQueryStringParameters When parsing Then maps correctly`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/search",
                    "multiValueQueryStringParameters": {
                        "filter": ["active", "recent"],
                        "page": ["1"]
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(listOf("active", "recent"), result.multiValueQueryParameters["filter"])
            assertEquals(listOf("1"), result.multiValueQueryParameters["page"])
        }

        @Test
        fun `Given request with both single and multi-value headers When parsing Then both are present`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "headers": {
                        "Accept": "application/json",
                        "Host": "example.com"
                    },
                    "multiValueHeaders": {
                        "Accept": ["application/json", "text/html"],
                        "Host": ["example.com"]
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals("application/json", result.headers["Accept"])
            assertEquals(listOf("application/json", "text/html"), result.multiValueHeaders["Accept"])
        }

        @Test
        fun `Given request with both single and multi-value query params When parsing Then both are present`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/search",
                    "queryStringParameters": {
                        "page": "1",
                        "filter": "active"
                    },
                    "multiValueQueryStringParameters": {
                        "page": ["1"],
                        "filter": ["active", "recent"]
                    }
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals("1", result.queryParameters["page"])
            assertEquals(listOf("1"), result.multiValueQueryParameters["page"])
            assertEquals("active", result.queryParameters["filter"])
            assertEquals(listOf("active", "recent"), result.multiValueQueryParameters["filter"])
        }

        @Test
        fun `Given request with null multiValueHeaders When parsing Then defaults to empty map`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "multiValueHeaders": null
                }
            """.trimIndent()

            val result = parser.parse(jsonToInputStream(json))

            assertEquals(emptyMap(), result.multiValueHeaders)
        }
    }

    @Nested
    inner class MalformedJsonHandling {

        @Test
        fun `Given completely invalid JSON When parsing Then throws RequestParseException`() {
            val json = "this is not json at all"

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
            assertNotNull(ex.cause)
        }

        @Test
        fun `Given truncated JSON When parsing Then throws RequestParseException`() {
            val json = """{"httpMethod": "GET", "path":"""

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
        }

        @Test
        fun `Given empty input stream When parsing Then throws RequestParseException`() {
            val json = ""

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
        }

        @Test
        fun `Given invalid HTTP method When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "httpMethod": "INVALID_METHOD",
                    "path": "/api/test"
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
            assertNotNull(ex.cause)
        }

        @Test
        fun `Given JSON array instead of object When parsing Then throws RequestParseException`() {
            val json = """[{"httpMethod": "GET", "path": "/test"}]"""

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            assertNotNull(ex.message)
        }
    }

    @Nested
    inner class MissingRequiredFields {

        @Test
        fun `Given missing httpMethod When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "path": "/api/test"
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            val message = ex.message
            assertNotNull(message)
            assertEquals("Missing required field: httpMethod", message)
        }

        @Test
        fun `Given missing path When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "httpMethod": "GET"
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            val message = ex.message
            assertNotNull(message)
            assertEquals("Missing required field: path", message)
        }

        @Test
        fun `Given null httpMethod When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "httpMethod": null,
                    "path": "/api/test"
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            val message = ex.message
            assertNotNull(message)
            assertEquals("Missing required field: httpMethod", message)
        }

        @Test
        fun `Given null path When parsing Then throws RequestParseException`() {
            val json = """
                {
                    "httpMethod": "GET",
                    "path": null
                }
            """.trimIndent()

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            val message = ex.message
            assertNotNull(message)
            assertEquals("Missing required field: path", message)
        }

        @Test
        fun `Given empty JSON object When parsing Then throws RequestParseException`() {
            val json = "{}"

            val ex = assertFailsWith<RequestParseException> {
                parser.parse(jsonToInputStream(json))
            }

            val message = ex.message
            assertNotNull(message)
            assertEquals("Missing required field: httpMethod", message)
        }
    }
}
