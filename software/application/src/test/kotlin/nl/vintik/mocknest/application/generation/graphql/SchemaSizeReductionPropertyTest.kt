package nl.vintik.mocknest.application.generation.graphql

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

/**
 * Property-based test for schema size reduction.
 *
 * **Validates: Requirements 3.8**
 *
 * Property 7: Schema Size Reduction
 * For any valid introspection JSON, the compact schema produced by GraphQLSchemaReducer
 * must be at least 40% smaller than the raw introspection JSON.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-7")
class SchemaSizeReductionPropertyTest {

    private val reducer = GraphQLSchemaReducer()

    private fun loadIntrospection(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    @ParameterizedTest(name = "Property 7 - Given {0} When reducing Then compact schema is at least 40% smaller")
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
    fun `Property 7 - Given introspection JSON When reducing Then compact schema is at least 40 percent smaller`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)
        val originalSize = introspectionJson.length

        // When
        val schema = reducer.reduce(introspectionJson)
        val compactOutput = schema.prettyPrint()
        val compactSize = compactOutput.length

        // Then
        val reductionPercent = ((originalSize - compactSize).toDouble() / originalSize * 100)
        assertTrue(
            reductionPercent >= 40.0,
            "[$filename] Schema size reduction should be at least 40%, but was ${String.format("%.1f", reductionPercent)}% " +
                "(original: $originalSize bytes, compact: $compactSize bytes)"
        )
    }

    @ParameterizedTest(name = "Property 7 - Given {0} When reducing Then compact output is non-empty")
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
    fun `Property 7 - Given introspection JSON When reducing Then compact output is non-empty`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val compactOutput = schema.prettyPrint()

        // Then
        assertTrue(
            compactOutput.isNotBlank(),
            "[$filename] Compact schema output should not be empty"
        )
        assertTrue(
            compactOutput.length < introspectionJson.length,
            "[$filename] Compact schema should be smaller than original introspection"
        )
    }
}
