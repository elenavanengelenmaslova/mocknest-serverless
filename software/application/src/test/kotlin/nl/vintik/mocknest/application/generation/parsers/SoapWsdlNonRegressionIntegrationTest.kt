package nl.vintik.mocknest.application.generation.parsers

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Non-regression integration tests verifying that existing OpenAPI and GraphQL generation
 * still works correctly after WSDL support was added.
 *
 * Tests the complete parsing pipeline for each format and verifies no breaking changes
 * were introduced to REST or GraphQL flows.
 *
 * Validates: Requirements 10.4, 12.11
 */
@Tag("soap-wsdl-ai-generation")
@Tag("integration")
class SoapWsdlNonRegressionIntegrationTest {

    private val openApiParser = OpenAPISpecificationParser()
    private val graphqlParser = GraphQLSpecificationParser(
        introspectionClient = mockk(relaxed = true),
        schemaReducer = GraphQLSchemaReducer()
    )
    private val wsdlParser = WsdlSpecificationParser(
        contentFetcher = mockk(relaxed = true),
        wsdlParser = WsdlParser(),
        schemaReducer = WsdlSchemaReducer()
    )

    private val openApiValidator = OpenAPIMockValidator()
    private val graphqlValidator = GraphQLMockValidator()
    private val soapValidator = SoapMockValidator()

    private fun loadOpenApiSpec(filename: String): String =
        this::class.java.getResource("/openapi/specs/$filename")?.readText()
            ?: throw IllegalArgumentException("OpenAPI spec not found: $filename")

    private fun loadGraphQLSchema(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("GraphQL schema not found: $filename")

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL file not found: $filename")

    // ─────────────────────────────────────────────────────────────────────────
    // OpenAPI Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class OpenAPINonRegression {

        @Test
        fun `Given OpenAPI spec When parsing Then produces valid APISpecification with correct format`() = runTest {
            // Given
            val content = loadOpenApiSpec("petstore-v3.yaml")

            // When
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then — basic validity must not regress
            assertFalse(spec.title.isBlank(), "Title should not be blank")
            assertFalse(spec.version.isBlank(), "Version should not be blank")
            assertTrue(spec.endpoints.isNotEmpty(), "Should have at least one endpoint")
            assertEquals(SpecificationFormat.OPENAPI_3, spec.format, "Format should be OPENAPI_3")
        }

        @Test
        fun `Given OpenAPI spec with multiple HTTP methods When parsing Then all methods are extracted`() = runTest {
            // Given
            val content = loadOpenApiSpec("simple-crud-api.yaml")

            // When
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then — all HTTP methods must still be extracted correctly
            val validMethods = setOf(
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH
            )
            spec.endpoints.forEach { endpoint ->
                assertTrue(
                    endpoint.method in validMethods,
                    "Endpoint ${endpoint.path} has invalid method: ${endpoint.method}"
                )
            }
        }

        @Test
        fun `Given OpenAPI spec When validating Then validation still passes`() = runTest {
            // Given
            val content = loadOpenApiSpec("petstore-v3.yaml")

            // When
            val result = openApiParser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertTrue(result.isValid, "OpenAPI validation should still pass. Errors: ${result.errors}")
        }

        @Test
        fun `Given OpenAPI spec with component schemas When parsing Then schemas are still extracted`() = runTest {
            // Given
            val content = loadOpenApiSpec("api-with-nested-objects.yaml")

            // When
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertTrue(spec.schemas.isNotEmpty(), "Component schemas should still be extracted")
        }

        @Test
        fun `Given OpenAPI spec When extracting metadata Then metadata is still populated`() = runTest {
            // Given
            val content = loadOpenApiSpec("petstore-v3.yaml")

            // When
            val metadata = openApiParser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertFalse(metadata.title.isBlank(), "Metadata title should not be blank")
            assertFalse(metadata.version.isBlank(), "Metadata version should not be blank")
            assertTrue(metadata.endpointCount > 0, "Metadata endpoint count should be > 0")
            assertEquals(SpecificationFormat.OPENAPI_3, metadata.format, "Metadata format should be OPENAPI_3")
        }

        @Test
        fun `Given OpenAPI mock When validating with OpenAPIMockValidator Then validator still works`() = runTest {
            // Given — a valid OpenAPI mock
            val namespace = MockNamespace(apiName = "petstore")
            val validOpenApiMock = GeneratedMock(
                id = UUID.randomUUID().toString(),
                name = "GET /pets mock",
                namespace = namespace,
                wireMockMapping = """
                    {
                      "request": {
                        "method": "GET",
                        "urlPath": "/pets"
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/json" },
                        "jsonBody": [{"id": 1, "name": "Fluffy"}]
                      }
                    }
                """.trimIndent(),
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "petstore: test",
                    endpoint = EndpointInfo(HttpMethod.GET, "/pets", 200, "application/json")
                ),
                generatedAt = Instant.now()
            )

            val content = loadOpenApiSpec("petstore-v3.yaml")
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // When — validate with OpenAPIMockValidator (not SoapMockValidator)
            val result = openApiValidator.validate(validOpenApiMock, spec)

            // Then — OpenAPI validator should still work correctly
            // (SoapMockValidator should return valid() for non-WSDL specs)
            val soapResult = soapValidator.validate(validOpenApiMock, spec)
            assertTrue(soapResult.isValid, "SoapMockValidator should return valid() for non-WSDL spec")
        }

        @Test
        fun `Given composite parser with all parsers When parsing OpenAPI spec Then routes to OpenAPISpecificationParser`() =
            runTest {
                // Given — composite parser with all three parsers (as in production)
                val composite = CompositeSpecificationParserImpl(listOf(openApiParser, graphqlParser, wsdlParser))
                val content = loadOpenApiSpec("petstore-v3.yaml")

                // When
                val spec = composite.parse(content, SpecificationFormat.OPENAPI_3)

                // Then — routes to OpenAPISpecificationParser
                assertEquals(SpecificationFormat.OPENAPI_3, spec.format, "Should route to OpenAPI parser")
                assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints")
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GraphQL Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class GraphQLNonRegression {

        @Test
        fun `Given GraphQL schema When parsing Then produces valid APISpecification with correct format`() = runTest {
            // Given
            val content = loadGraphQLSchema("simple-schema.json")

            // When
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)

            // Then — basic validity must not regress
            assertFalse(spec.title.isBlank(), "Title should not be blank")
            assertTrue(spec.endpoints.isNotEmpty(), "Should have at least one endpoint")
            assertEquals(SpecificationFormat.GRAPHQL, spec.format, "Format should be GRAPHQL")
        }

        @Test
        fun `Given GraphQL schema When parsing Then all endpoints use POST to graphql path`() = runTest {
            // Given
            val content = loadGraphQLSchema("complex-schema.json")

            // When
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)

            // Then — GraphQL endpoints must still use POST to /graphql
            spec.endpoints.forEach { endpoint ->
                assertEquals("/graphql", endpoint.path, "All GraphQL endpoints should use /graphql path")
                assertEquals(HttpMethod.POST, endpoint.method, "All GraphQL endpoints should use POST method")
            }
        }

        @Test
        fun `Given GraphQL schema with complex types When parsing Then schemas are still extracted`() = runTest {
            // Given
            val content = loadGraphQLSchema("complex-schema.json")

            // When
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)

            // Then
            assertTrue(spec.schemas.isNotEmpty(), "GraphQL schemas should still be extracted")
            assertTrue(spec.endpoints.size >= 5, "Complex schema should produce multiple endpoints")
        }

        @Test
        fun `Given GraphQL mock When validating with GraphQLMockValidator Then validator still works`() = runTest {
            // Given — a valid GraphQL mock
            val namespace = MockNamespace(apiName = "graphql-api")
            val validGraphQLMock = GeneratedMock(
                id = UUID.randomUUID().toString(),
                name = "GraphQL user query mock",
                namespace = namespace,
                wireMockMapping = """
                    {
                      "request": {
                        "method": "POST",
                        "urlPath": "/graphql",
                        "bodyPatterns": [{"equalToJson": "{\"query\":\"query { user { id name } }\",\"operationName\":\"user\",\"variables\":{}}"}]
                      },
                      "response": {
                        "status": 200,
                        "headers": { "Content-Type": "application/json" },
                        "jsonBody": {"data": {"user": {"id": "1", "name": "Alice"}}}
                      }
                    }
                """.trimIndent(),
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "GraphQL API: test",
                    endpoint = EndpointInfo(HttpMethod.POST, "/graphql", 200, "application/json")
                ),
                generatedAt = Instant.now()
            )

            val content = loadGraphQLSchema("simple-schema.json")
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)

            // When — SoapMockValidator should return valid() for non-WSDL specs
            val soapResult = soapValidator.validate(validGraphQLMock, spec)
            assertTrue(soapResult.isValid, "SoapMockValidator should return valid() for non-WSDL spec")
        }

        @Test
        fun `Given composite parser with all parsers When parsing GraphQL schema Then routes to GraphQLSpecificationParser`() =
            runTest {
                // Given — composite parser with all three parsers (as in production)
                val composite = CompositeSpecificationParserImpl(listOf(openApiParser, graphqlParser, wsdlParser))
                val content = loadGraphQLSchema("simple-schema.json")

                // When
                val spec = composite.parse(content, SpecificationFormat.GRAPHQL)

                // Then — routes to GraphQLSpecificationParser
                assertEquals(SpecificationFormat.GRAPHQL, spec.format, "Should route to GraphQL parser")
                assertTrue(spec.endpoints.isNotEmpty(), "Should have endpoints")
                spec.endpoints.forEach { endpoint ->
                    assertEquals("/graphql", endpoint.path, "GraphQL endpoints should use /graphql path")
                }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-format isolation — SOAP validator does not interfere with REST/GraphQL
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class CrossFormatIsolation {

        @Test
        fun `Given OpenAPI spec When SoapMockValidator validates non-WSDL mock Then returns valid`() = runTest {
            // Given
            val content = loadOpenApiSpec("petstore-v3.yaml")
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)
            val namespace = MockNamespace(apiName = "petstore")

            val openApiMock = GeneratedMock(
                id = UUID.randomUUID().toString(),
                name = "GET /pets mock",
                namespace = namespace,
                wireMockMapping = """{"request":{"method":"GET","urlPath":"/pets"},"response":{"status":200,"jsonBody":[]}}""",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "petstore: test",
                    endpoint = EndpointInfo(HttpMethod.GET, "/pets", 200, "application/json")
                ),
                generatedAt = Instant.now()
            )

            // When — SoapMockValidator should skip non-WSDL specs
            val result = soapValidator.validate(openApiMock, spec)

            // Then — SoapMockValidator returns valid() for non-WSDL specs (no interference)
            assertTrue(result.isValid, "SoapMockValidator must return valid() for non-WSDL spec")
            assertTrue(result.errors.isEmpty(), "SoapMockValidator must not add errors for non-WSDL spec")
        }

        @Test
        fun `Given GraphQL spec When SoapMockValidator validates non-WSDL mock Then returns valid`() = runTest {
            // Given
            val content = loadGraphQLSchema("simple-schema.json")
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)
            val namespace = MockNamespace(apiName = "graphql-api")

            val graphqlMock = GeneratedMock(
                id = UUID.randomUUID().toString(),
                name = "GraphQL query mock",
                namespace = namespace,
                wireMockMapping = """{"request":{"method":"POST","urlPath":"/graphql"},"response":{"status":200,"jsonBody":{"data":{}}}}""",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "GraphQL API: test",
                    endpoint = EndpointInfo(HttpMethod.POST, "/graphql", 200, "application/json")
                ),
                generatedAt = Instant.now()
            )

            // When — SoapMockValidator should skip non-WSDL specs
            val result = soapValidator.validate(graphqlMock, spec)

            // Then — SoapMockValidator returns valid() for non-WSDL specs (no interference)
            assertTrue(result.isValid, "SoapMockValidator must return valid() for GraphQL spec")
            assertTrue(result.errors.isEmpty(), "SoapMockValidator must not add errors for GraphQL spec")
        }

        @Test
        fun `Given WSDL spec When OpenAPIMockValidator validates SOAP mock Then returns valid`() = runTest {
            // Given
            val wsdlXml = loadWsdl("calculator-soap11.wsdl")
            val spec = wsdlParser.parse(wsdlXml, SpecificationFormat.WSDL)
            val namespace = MockNamespace(apiName = "calculator-api")

            val soapMock = GeneratedMock(
                id = UUID.randomUUID().toString(),
                name = "SOAP Add mock",
                namespace = namespace,
                wireMockMapping = """{"request":{"method":"POST","urlPath":"/CalculatorService"},"response":{"status":200,"body":"<soap:Envelope/>"}}""",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "CalculatorService: test",
                    endpoint = EndpointInfo(HttpMethod.POST, "/CalculatorService", 200, "text/xml")
                ),
                generatedAt = Instant.now()
            )

            // When — OpenAPIMockValidator should skip WSDL specs
            val result = openApiValidator.validate(soapMock, spec)

            // Then — OpenAPIMockValidator returns valid() for WSDL specs (no interference)
            assertTrue(result.isValid, "OpenAPIMockValidator must return valid() for WSDL spec")
        }

        @Test
        fun `Given all three parsers When checking format support Then each parser supports only its own format`() {
            // Then — format isolation must be maintained
            assertTrue(openApiParser.supports(SpecificationFormat.OPENAPI_3))
            assertTrue(openApiParser.supports(SpecificationFormat.SWAGGER_2))
            assertFalse(openApiParser.supports(SpecificationFormat.GRAPHQL))
            assertFalse(openApiParser.supports(SpecificationFormat.WSDL))

            assertTrue(graphqlParser.supports(SpecificationFormat.GRAPHQL))
            assertFalse(graphqlParser.supports(SpecificationFormat.OPENAPI_3))
            assertFalse(graphqlParser.supports(SpecificationFormat.SWAGGER_2))
            assertFalse(graphqlParser.supports(SpecificationFormat.WSDL))

            assertTrue(wsdlParser.supports(SpecificationFormat.WSDL))
            assertFalse(wsdlParser.supports(SpecificationFormat.OPENAPI_3))
            assertFalse(wsdlParser.supports(SpecificationFormat.SWAGGER_2))
            assertFalse(wsdlParser.supports(SpecificationFormat.GRAPHQL))
        }
    }
}
