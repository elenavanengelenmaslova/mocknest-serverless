package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test for GraphQL request acceptance.
 *
 * **Validates: Requirements 1.1**
 *
 * Property 1: GraphQL Request Acceptance
 * For any valid GraphQL endpoint URL or pre-fetched introspection schema,
 * the GraphQLSpecificationParser must accept the input without format-related errors
 * and produce a valid APISpecification with format GRAPHQL.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-1")
class GraphQLRequestAcceptancePropertyTest {

    private val introspectionClient: GraphQLIntrospectionClientInterface = mockk()
    private val schemaReducer: GraphQLSchemaReducerInterface = mockk()

    private val parser = GraphQLSpecificationParser(introspectionClient, schemaReducer, urlSafetyValidator = {})

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadIntrospection(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    private fun buildMinimalCompactSchema(): CompactGraphQLSchema = CompactGraphQLSchema(
        queries = listOf(
            GraphQLOperation(name = "user", arguments = emptyList(), returnType = "User")
        ),
        mutations = emptyList(),
        types = mapOf(
            "User" to GraphQLType(
                name = "User",
                fields = listOf(
                    GraphQLField(name = "id", type = "ID!"),
                    GraphQLField(name = "name", type = "String!")
                )
            )
        ),
        enums = emptyMap(),
        metadata = GraphQLSchemaMetadata()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Property 1: Pre-fetched schema acceptance
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Property 1 - Given pre-fetched schema {0} When parsing Then produces valid APISpecification")
    @ValueSource(strings = [
        "simple-schema.json",
        "complex-schema.json",
        "minimal-schema.json",
        "mutations-only-schema.json",
        "queries-only-schema.json",
        "with-enums-schema.json",
        "nested-types-schema.json",
        "large-schema-100-ops.json"
    ])
    fun `Property 1 - Given pre-fetched schema When parsing Then produces valid APISpecification with GRAPHQL format`(
        filename: String
    ) = runTest {
        // Given
        val content = loadIntrospection(filename)
        val compactSchema = buildMinimalCompactSchema()
        coEvery { schemaReducer.reduce(content) } returns compactSchema

        // When
        val spec = parser.parse(content, SpecificationFormat.GRAPHQL)

        // Then
        assertNotNull(spec, "[$filename] Parsed specification should not be null")
        assertEquals(
            SpecificationFormat.GRAPHQL, spec.format,
            "[$filename] Specification format should be GRAPHQL"
        )
        assertTrue(
            spec.endpoints.isNotEmpty(),
            "[$filename] Specification should have at least one endpoint"
        )
    }

    @ParameterizedTest(name = "Property 1 - Given URL {0} When parsing Then accepts and produces valid APISpecification")
    @ValueSource(strings = [
        "https://example.com/graphql",
        "https://api.github.com/graphql",
        "http://localhost:4000/graphql",
        "https://countries.trevorblades.com/graphql",
        "https://api.spacex.land/graphql",
        "https://swapi-graphql.netlify.app/.netlify/functions/index",
        "https://graphql.example.org/v2/api",
        "http://internal-service:8080/graphql",
        "https://my-app.herokuapp.com/graphql",
        "https://api.example.io/graphql/v1"
    ])
    fun `Property 1 - Given GraphQL URL When parsing Then accepts input and produces valid APISpecification`(
        url: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection("simple-schema.json")
        val compactSchema = buildMinimalCompactSchema()
        coEvery { introspectionClient.introspect(url, any(), any()) } returns introspectionJson
        coEvery { schemaReducer.reduce(introspectionJson) } returns compactSchema

        // When
        val spec = parser.parse(url, SpecificationFormat.GRAPHQL)

        // Then
        assertNotNull(spec, "[$url] Parsed specification should not be null")
        assertEquals(
            SpecificationFormat.GRAPHQL, spec.format,
            "[$url] Specification format should be GRAPHQL"
        )
        assertTrue(
            spec.endpoints.isNotEmpty(),
            "[$url] Specification should have at least one endpoint"
        )
    }

    @ParameterizedTest(name = "Property 1 - Given pre-fetched schema {0} When validating Then result is valid")
    @ValueSource(strings = [
        "simple-schema.json",
        "complex-schema.json",
        "minimal-schema.json",
        "mutations-only-schema.json",
        "queries-only-schema.json",
        "with-enums-schema.json",
        "nested-types-schema.json",
        "large-schema-100-ops.json"
    ])
    fun `Property 1 - Given pre-fetched schema When validating Then validation passes`(
        filename: String
    ) = runTest {
        // Given
        val content = loadIntrospection(filename)
        val compactSchema = buildMinimalCompactSchema()
        coEvery { schemaReducer.reduce(content) } returns compactSchema

        // When
        val result = parser.validate(content, SpecificationFormat.GRAPHQL)

        // Then
        assertTrue(result.isValid, "[$filename] Validation should pass. Errors: ${result.errors}")
    }

    @ParameterizedTest(name = "Property 1 - Given format check When calling supports with GRAPHQL Then returns true")
    @ValueSource(strings = [
        "simple-schema.json",
        "complex-schema.json",
        "minimal-schema.json"
    ])
    fun `Property 1 - Given GraphQL parser When checking format support Then supports GRAPHQL`(
        @Suppress("UNUSED_PARAMETER") filename: String
    ) {
        // When / Then
        assertTrue(parser.supports(SpecificationFormat.GRAPHQL), "Parser should support GRAPHQL format")
    }
}
