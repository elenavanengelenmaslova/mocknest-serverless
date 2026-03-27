package nl.vintik.mocknest.application.generation.graphql

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

/**
 * Property-based test for schema round-trip preservation.
 *
 * **Validates: Requirements 10.3, 10.4, 10.5, 10.6**
 *
 * Property 15: Schema Round-Trip Preservation
 * For any valid introspection JSON, after reducing to a compact schema and
 * pretty-printing to SDL, all operations, types, and enums must be preserved
 * in the output.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-15")
class SchemaRoundTripPreservationPropertyTest {

    private val reducer = GraphQLSchemaReducer()

    private fun loadIntrospection(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    @ParameterizedTest(name = "Property 15 - Given {0} When round-tripping Then all query operations are preserved")
    @ValueSource(strings = [
        "simple-schema.json",
        "complex-schema.json",
        "minimal-schema.json",
        "queries-only-schema.json",
        "with-enums-schema.json",
        "nested-types-schema.json",
        "large-schema-100-ops.json"
    ])
    fun `Property 15 - Given introspection JSON When round-tripping Then all query operations are preserved in SDL`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        if (schema.queries.isNotEmpty()) {
            assertTrue(sdl.contains("type Query {"), "[$filename] SDL should contain 'type Query {'")
            schema.queries.forEach { query ->
                assertTrue(
                    sdl.contains(query.name),
                    "[$filename] SDL should contain query operation '${query.name}'"
                )
            }
        }
    }

    @ParameterizedTest(name = "Property 15 - Given {0} When round-tripping Then all mutation operations are preserved")
    @ValueSource(strings = [
        "complex-schema.json",
        "mutations-only-schema.json",
        "with-enums-schema.json",
        "large-schema-100-ops.json"
    ])
    fun `Property 15 - Given introspection JSON When round-tripping Then all mutation operations are preserved in SDL`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        if (schema.mutations.isNotEmpty()) {
            assertTrue(sdl.contains("type Mutation {"), "[$filename] SDL should contain 'type Mutation {'")
            schema.mutations.forEach { mutation ->
                assertTrue(
                    sdl.contains(mutation.name),
                    "[$filename] SDL should contain mutation operation '${mutation.name}'"
                )
            }
        }
    }

    @ParameterizedTest(name = "Property 15 - Given {0} When round-tripping Then all types are preserved")
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
    fun `Property 15 - Given introspection JSON When round-tripping Then all object types are preserved in SDL`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        schema.types.forEach { (typeName, type) ->
            assertTrue(
                sdl.contains("type $typeName {"),
                "[$filename] SDL should contain type definition 'type $typeName {'"
            )
            type.fields.forEach { field ->
                assertTrue(
                    sdl.contains(field.name),
                    "[$filename] SDL should contain field '${field.name}' in type '$typeName'"
                )
            }
        }
    }

    @ParameterizedTest(name = "Property 15 - Given {0} When round-tripping Then all enums are preserved")
    @ValueSource(strings = [
        "with-enums-schema.json",
        "complex-schema.json",
        "large-schema-100-ops.json"
    ])
    fun `Property 15 - Given introspection JSON When round-tripping Then all enum types and values are preserved in SDL`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        schema.enums.forEach { (enumName, enumType) ->
            assertTrue(
                sdl.contains("enum $enumName {"),
                "[$filename] SDL should contain enum definition 'enum $enumName {'"
            )
            enumType.values.forEach { value ->
                assertTrue(
                    sdl.contains(value),
                    "[$filename] SDL should contain enum value '$value' in enum '$enumName'"
                )
            }
        }
    }

    @ParameterizedTest(name = "Property 15 - Given {0} When round-tripping Then schema declaration is present")
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
    fun `Property 15 - Given introspection JSON When round-tripping Then schema declaration is present in SDL`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        assertTrue(sdl.contains("schema {"), "[$filename] SDL should contain 'schema {'")
        if (schema.queries.isNotEmpty()) {
            assertTrue(sdl.contains("query: Query"), "[$filename] SDL should reference Query type")
        }
        if (schema.mutations.isNotEmpty()) {
            assertTrue(sdl.contains("mutation: Mutation"), "[$filename] SDL should reference Mutation type")
        }
    }
}
