package nl.vintik.mocknest.application.generation.parsers

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import nl.vintik.mocknest.domain.core.HttpMethod

/**
 * Property-based test for REST generation non-regression.
 *
 * **Validates: Requirements 7.4**
 *
 * Property 14: REST Generation Non-Regression
 * For any valid OpenAPI specification, after implementing GraphQL support,
 * the OpenAPISpecificationParser should still successfully parse the spec
 * and produce a valid APISpecification with correct endpoints, methods, and schemas.
 *
 * Uses 16 diverse OpenAPI YAML test data files covering different API patterns.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-14")
class RESTGenerationNonRegressionPropertyTest {

    private val parser = OpenAPISpecificationParser()

    private fun loadSpec(filename: String): String =
        this::class.java.getResource("/openapi/specs/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    // ─────────────────────────────────────────────────────────────────────────
    // Property 14: REST Generation Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then produces valid APISpecification")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When parsing Then produces valid APISpecification`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then - basic validity
        assertFalse(spec.title.isBlank(), "[$filename] Title should not be blank")
        assertFalse(spec.version.isBlank(), "[$filename] Version should not be blank")
        assertTrue(spec.endpoints.isNotEmpty(), "[$filename] Should have at least one endpoint")
        assertTrue(spec.format == SpecificationFormat.OPENAPI_3, "[$filename] Format should be OPENAPI_3")
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then all endpoints have valid HTTP methods")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When parsing Then all endpoints have valid HTTP methods`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)
        val validMethods = setOf(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS
        )

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then
        spec.endpoints.forEach { endpoint ->
            assertTrue(
                endpoint.method in validMethods,
                "[$filename] Endpoint ${endpoint.path} has invalid method: ${endpoint.method}"
            )
        }
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then all endpoints have non-blank paths")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When parsing Then all endpoints have non-blank paths`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then
        spec.endpoints.forEach { endpoint ->
            assertFalse(
                endpoint.path.isBlank(),
                "[$filename] Endpoint path should not be blank"
            )
            assertTrue(
                endpoint.path.startsWith("/"),
                "[$filename] Endpoint path '${endpoint.path}' should start with /"
            )
        }
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then all endpoints have at least one response")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When parsing Then all endpoints have at least one response`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then
        spec.endpoints.forEach { endpoint ->
            assertTrue(
                endpoint.responses.isNotEmpty(),
                "[$filename] Endpoint ${endpoint.method} ${endpoint.path} should have at least one response"
            )
        }
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then validation passes")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When validating Then result is valid`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

        // Then
        assertTrue(result.isValid, "[$filename] Validation should pass. Errors: ${result.errors}")
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When extracting metadata Then metadata is populated")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec When extracting metadata Then metadata is populated`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

        // Then
        assertFalse(metadata.title.isBlank(), "[$filename] Metadata title should not be blank")
        assertFalse(metadata.version.isBlank(), "[$filename] Metadata version should not be blank")
        assertTrue(metadata.endpointCount > 0, "[$filename] Metadata endpoint count should be > 0")
        assertTrue(metadata.format == SpecificationFormat.OPENAPI_3, "[$filename] Metadata format should be OPENAPI_3")
    }

    @ParameterizedTest(name = "Property 14 - Given {0} with path params When parsing Then path parameters are extracted")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-path-params.yaml",
        "api-with-file-upload.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec with path params When parsing Then path parameters are extracted`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then - endpoints with path parameters should have them extracted
        val endpointsWithPathParams = spec.endpoints.filter { it.path.contains("{") }
        endpointsWithPathParams.forEach { endpoint ->
            assertTrue(
                endpoint.parameters.any { it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.PATH },
                "[$filename] Endpoint ${endpoint.path} should have PATH parameters extracted"
            )
        }
    }

    @ParameterizedTest(name = "Property 14 - Given {0} with query params When parsing Then query parameters are extracted")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "api-with-query-params.yaml",
        "api-with-enums.yaml",
        "api-with-pagination.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec with query params When parsing Then query parameters are extracted`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then - at least one endpoint should have query parameters
        val hasQueryParams = spec.endpoints.any { endpoint ->
            endpoint.parameters.any { it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.QUERY }
        }
        assertTrue(hasQueryParams, "[$filename] Should have at least one endpoint with QUERY parameters")
    }

    @ParameterizedTest(name = "Property 14 - Given {0} with schemas When parsing Then component schemas are extracted")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec with component schemas When parsing Then schemas are extracted`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then
        assertTrue(spec.schemas.isNotEmpty(), "[$filename] Should have at least one component schema")
        spec.schemas.forEach { (name, _) ->
            assertFalse(name.isBlank(), "[$filename] Schema name should not be blank")
        }
    }

    @ParameterizedTest(name = "Property 14 - Given {0} with POST endpoints When parsing Then request bodies are extracted")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-request-bodies.yaml",
        "api-with-nested-objects.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given OpenAPI spec with POST endpoints When parsing Then request bodies are extracted`(filename: String) = runTest {
        // Given
        val content = loadSpec(filename)

        // When
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        // Then - POST endpoints with requestBody should have it extracted
        val postEndpoints = spec.endpoints.filter { it.method == HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty(), "[$filename] Should have at least one POST endpoint")
        assertTrue(
            postEndpoints.any { it.requestBody != null },
            "[$filename] At least one POST endpoint should have a request body"
        )
    }

    @ParameterizedTest(name = "Property 14 - Given {0} When parsing Then OPENAPI_3 format is supported")
    @ValueSource(strings = [
        "petstore-v3.yaml",
        "simple-crud-api.yaml",
        "api-with-query-params.yaml",
        "api-with-path-params.yaml",
        "api-with-request-bodies.yaml",
        "api-with-arrays.yaml",
        "api-with-nested-objects.yaml",
        "api-with-enums.yaml",
        "minimal-api.yaml",
        "api-with-auth.yaml",
        "api-with-multiple-responses.yaml",
        "api-with-pagination.yaml",
        "api-with-oneOf.yaml",
        "api-with-file-upload.yaml",
        "api-with-deprecated.yaml",
        "large-crud-api.yaml"
    ])
    fun `Property 14 - Given any OpenAPI spec When checking format support Then OPENAPI_3 is supported`(filename: String) {
        // Given - just verify the parser still supports REST formats (no regression)
        assertTrue(parser.supports(SpecificationFormat.OPENAPI_3), "[$filename] Parser should support OPENAPI_3")
        assertTrue(parser.supports(SpecificationFormat.SWAGGER_2), "[$filename] Parser should support SWAGGER_2")
        assertFalse(parser.supports(SpecificationFormat.GRAPHQL), "[$filename] REST parser should not support GRAPHQL")
    }
}
