package nl.vintik.mocknest.application.generation.parsers

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import nl.vintik.mocknest.domain.core.HttpMethod
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test: Property-10 (REST and GraphQL Non-Regression).
 *
 * After adding WSDL support, existing OpenAPI and GraphQL specification parsers
 * must continue to work correctly without regression. Verifies that:
 * - OPENAPI_3 and SWAGGER_2 still route to OpenAPISpecificationParser
 * - GRAPHQL still routes to GraphQLSpecificationParser
 * - WSDL routes to WsdlSpecificationParser (new)
 * - Parsing produces valid APISpecification for all formats
 *
 * **Property 10: REST and GraphQL Non-Regression**
 * **Validates: Requirements 10.4**
 */
@Tag("soap-wsdl-ai-generation")
@Tag("Property-10")
class SoapWsdlNonRegressionPropertyTest {

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

    private fun loadOpenApiSpec(filename: String): String =
        this::class.java.getResource("/openapi/specs/$filename")?.readText()
            ?: throw IllegalArgumentException("OpenAPI spec not found: $filename")

    private fun loadGraphQLSchema(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("GraphQL schema not found: $filename")

    private fun loadWsdl(filename: String): String =
        this::class.java.getResource("/wsdl/$filename")?.readText()
            ?: throw IllegalArgumentException("WSDL file not found: $filename")

    companion object {
        @JvmStatic
        fun openApiSpecs(): Stream<String> = Stream.of(
            "petstore-v3.yaml",
            "simple-crud-api.yaml",
            "api-with-query-params.yaml",
            "api-with-path-params.yaml",
            "api-with-request-bodies.yaml",
            "api-with-arrays.yaml",
            "api-with-nested-objects.yaml",
            "minimal-api.yaml",
            "api-with-auth.yaml",
            "api-with-multiple-responses.yaml",
            "api-with-pagination.yaml",
            "large-crud-api.yaml"
        )

        @JvmStatic
        fun graphqlSchemas(): Stream<String> = Stream.of(
            "simple-schema.json",
            "complex-schema.json",
            "minimal-schema.json",
            "queries-only-schema.json",
            "mutations-only-schema.json",
            "nested-types-schema.json",
            "with-enums-schema.json"
        )

        @JvmStatic
        fun wsdlFiles(): Stream<String> = Stream.of(
            "simple-soap12.wsdl",
            "simple-soap12.wsdl",
            "calculator-soap12.wsdl",
            "weather-soap12.wsdl",
            "multi-operation-soap12.wsdl",
            "complex-types-soap12.wsdl"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 10: Format routing — OPENAPI_3 and SWAGGER_2 still route to OpenAPISpecificationParser
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 10 - Given {0} When checking OpenAPI format support Then OPENAPI_3 and SWAGGER_2 still supported")
    @MethodSource("openApiSpecs")
    fun `Property 10 - Given OpenAPI spec When checking format support Then OPENAPI_3 and SWAGGER_2 still supported`(
        filename: String
    ) {
        // Then — format support must not regress after WSDL support was added
        assertTrue(
            openApiParser.supports(SpecificationFormat.OPENAPI_3),
            "[$filename] OpenAPISpecificationParser must still support OPENAPI_3"
        )
        assertTrue(
            openApiParser.supports(SpecificationFormat.SWAGGER_2),
            "[$filename] OpenAPISpecificationParser must still support SWAGGER_2"
        )
        assertFalse(
            openApiParser.supports(SpecificationFormat.GRAPHQL),
            "[$filename] OpenAPISpecificationParser must not support GRAPHQL"
        )
        assertFalse(
            openApiParser.supports(SpecificationFormat.WSDL),
            "[$filename] OpenAPISpecificationParser must not support WSDL"
        )
    }

    @ParameterizedTest(name = "Property 10 - Given {0} When parsing OpenAPI spec Then produces valid APISpecification")
    @MethodSource("openApiSpecs")
    fun `Property 10 - Given OpenAPI spec When parsing Then produces valid APISpecification`(filename: String) =
        runTest {
            // Given
            val content = loadOpenApiSpec(filename)

            // When
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then — basic validity must not regress
            assertFalse(spec.title.isBlank(), "[$filename] Title should not be blank")
            assertFalse(spec.version.isBlank(), "[$filename] Version should not be blank")
            assertTrue(spec.endpoints.isNotEmpty(), "[$filename] Should have at least one endpoint")
            assertEquals(SpecificationFormat.OPENAPI_3, spec.format, "[$filename] Format should be OPENAPI_3")
        }

    @ParameterizedTest(name = "Property 10 - Given {0} When parsing OpenAPI spec Then all endpoints have valid HTTP methods")
    @MethodSource("openApiSpecs")
    fun `Property 10 - Given OpenAPI spec When parsing Then all endpoints have valid HTTP methods`(filename: String) =
        runTest {
            // Given
            val content = loadOpenApiSpec(filename)
            val validMethods = setOf(
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS
            )

            // When
            val spec = openApiParser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            spec.endpoints.forEach { endpoint ->
                assertTrue(
                    endpoint.method in validMethods,
                    "[$filename] Endpoint ${endpoint.path} has invalid method: ${endpoint.method}"
                )
            }
        }

    @ParameterizedTest(name = "Property 10 - Given {0} When validating OpenAPI spec Then validation passes")
    @MethodSource("openApiSpecs")
    fun `Property 10 - Given OpenAPI spec When validating Then validation passes`(filename: String) = runTest {
        // Given
        val content = loadOpenApiSpec(filename)

        // When
        val result = openApiParser.validate(content, SpecificationFormat.OPENAPI_3)

        // Then
        assertTrue(result.isValid, "[$filename] Validation should pass. Errors: ${result.errors}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 10: Format routing — GRAPHQL still routes to GraphQLSpecificationParser
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 10 - Given {0} When checking GraphQL format support Then GRAPHQL still supported")
    @MethodSource("graphqlSchemas")
    fun `Property 10 - Given GraphQL schema When checking format support Then GRAPHQL still supported`(
        filename: String
    ) {
        // Then — format support must not regress after WSDL support was added
        assertTrue(
            graphqlParser.supports(SpecificationFormat.GRAPHQL),
            "[$filename] GraphQLSpecificationParser must still support GRAPHQL"
        )
        assertFalse(
            graphqlParser.supports(SpecificationFormat.OPENAPI_3),
            "[$filename] GraphQLSpecificationParser must not support OPENAPI_3"
        )
        assertFalse(
            graphqlParser.supports(SpecificationFormat.WSDL),
            "[$filename] GraphQLSpecificationParser must not support WSDL"
        )
    }

    @ParameterizedTest(name = "Property 10 - Given {0} When parsing GraphQL schema Then produces valid APISpecification")
    @MethodSource("graphqlSchemas")
    fun `Property 10 - Given GraphQL schema When parsing Then produces valid APISpecification`(filename: String) =
        runTest {
            // Given
            val content = loadGraphQLSchema(filename)

            // When
            val spec = graphqlParser.parse(content, SpecificationFormat.GRAPHQL)

            // Then — basic validity must not regress
            assertFalse(spec.title.isBlank(), "[$filename] Title should not be blank")
            assertTrue(spec.endpoints.isNotEmpty(), "[$filename] Should have at least one endpoint")
            assertEquals(SpecificationFormat.GRAPHQL, spec.format, "[$filename] Format should be GRAPHQL")
            spec.endpoints.forEach { endpoint ->
                assertEquals(
                    "/graphql",
                    endpoint.path,
                    "[$filename] All GraphQL endpoints should use /graphql path"
                )
                assertEquals(
                    HttpMethod.POST,
                    endpoint.method,
                    "[$filename] All GraphQL endpoints should use POST method"
                )
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 10: WSDL routes to WsdlSpecificationParser (new, non-regression check)
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 10 - Given {0} When checking WSDL format support Then WSDL is supported by WsdlSpecificationParser")
    @MethodSource("wsdlFiles")
    fun `Property 10 - Given WSDL file When checking format support Then WSDL is supported by WsdlSpecificationParser`(
        filename: String
    ) {
        // Then — WSDL format is now supported
        assertTrue(
            wsdlParser.supports(SpecificationFormat.WSDL),
            "[$filename] WsdlSpecificationParser must support WSDL"
        )
        assertFalse(
            wsdlParser.supports(SpecificationFormat.OPENAPI_3),
            "[$filename] WsdlSpecificationParser must not support OPENAPI_3"
        )
        assertFalse(
            wsdlParser.supports(SpecificationFormat.GRAPHQL),
            "[$filename] WsdlSpecificationParser must not support GRAPHQL"
        )
    }

    @ParameterizedTest(name = "Property 10 - Given {0} When parsing WSDL Then produces valid APISpecification")
    @MethodSource("wsdlFiles")
    fun `Property 10 - Given WSDL file When parsing Then produces valid APISpecification`(filename: String) =
        runTest {
            // Given
            val content = loadWsdl(filename)

            // When
            val spec = wsdlParser.parse(content, SpecificationFormat.WSDL)

            // Then — WSDL parsing produces valid spec
            assertFalse(spec.title.isBlank(), "[$filename] Title should not be blank")
            assertTrue(spec.endpoints.isNotEmpty(), "[$filename] Should have at least one endpoint")
            assertEquals(SpecificationFormat.WSDL, spec.format, "[$filename] Format should be WSDL")
            spec.endpoints.forEach { endpoint ->
                assertEquals(
                    HttpMethod.POST,
                    endpoint.method,
                    "[$filename] All SOAP endpoints should use POST method"
                )
                assertNotNull(
                    endpoint.metadata["soapAction"],
                    "[$filename] Each SOAP endpoint must have soapAction metadata"
                )
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 10: CompositeSpecificationParser routes correctly after WSDL support added
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 10 - Given {0} When building composite parser Then WSDL format is supported alongside REST and GraphQL")
    @MethodSource("openApiSpecs")
    fun `Property 10 - Given composite parser with all parsers When checking formats Then all formats are supported`(
        filename: String
    ) {
        // Given — composite parser with all three parsers (as in production)
        val composite = CompositeSpecificationParserImpl(listOf(openApiParser, graphqlParser, wsdlParser))

        // Then — all formats must be supported
        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3), "[$filename] Composite must support OPENAPI_3")
        assertTrue(composite.supports(SpecificationFormat.SWAGGER_2), "[$filename] Composite must support SWAGGER_2")
        assertTrue(composite.supports(SpecificationFormat.GRAPHQL), "[$filename] Composite must support GRAPHQL")
        assertTrue(composite.supports(SpecificationFormat.WSDL), "[$filename] Composite must support WSDL")
    }
}
