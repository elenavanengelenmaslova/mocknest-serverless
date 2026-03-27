package nl.vintik.mocknest.application.generation.graphql

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based test for metadata field exclusion in compact schemas.
 *
 * **Validates: Requirements 3.7**
 *
 * Property 6: Metadata Field Exclusion
 * For any valid introspection JSON, the compact schema produced by GraphQLSchemaReducer
 * must exclude GraphQL introspection metadata fields: __schema, __type, __typename, and
 * any other fields starting with double underscore.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-6")
class MetadataFieldExclusionPropertyTest {

    private val reducer = GraphQLSchemaReducer()

    private fun loadIntrospection(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    @ParameterizedTest(name = "Property 6 - Given {0} When reducing Then no query or mutation name starts with __")
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
    fun `Property 6 - Given introspection JSON When reducing Then no operation names start with double underscore`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)

        // Then
        schema.queries.forEach { op ->
            assertFalse(
                op.name.startsWith("__"),
                "[$filename] Query '${op.name}' should not start with __"
            )
        }
        schema.mutations.forEach { op ->
            assertFalse(
                op.name.startsWith("__"),
                "[$filename] Mutation '${op.name}' should not start with __"
            )
        }
    }

    @ParameterizedTest(name = "Property 6 - Given {0} When reducing Then no type or field names start with __")
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
    fun `Property 6 - Given introspection JSON When reducing Then no type or field names start with double underscore`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)

        // Then
        schema.types.forEach { (typeName, type) ->
            assertFalse(
                typeName.startsWith("__"),
                "[$filename] Type '$typeName' should not start with __"
            )
            type.fields.forEach { field ->
                assertFalse(
                    field.name.startsWith("__"),
                    "[$filename] Field '${field.name}' in type '$typeName' should not start with __"
                )
            }
        }
        schema.enums.forEach { (enumName, _) ->
            assertFalse(
                enumName.startsWith("__"),
                "[$filename] Enum '$enumName' should not start with __"
            )
        }
    }

    @ParameterizedTest(name = "Property 6 - Given {0} When reducing Then prettyPrint excludes metadata keywords")
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
    fun `Property 6 - Given introspection JSON When reducing and printing Then output excludes metadata keywords`(
        filename: String
    ) = runTest {
        // Given
        val introspectionJson = loadIntrospection(filename)

        // When
        val schema = reducer.reduce(introspectionJson)
        val sdl = schema.prettyPrint()

        // Then
        assertFalse(sdl.contains("__schema"), "[$filename] SDL should not contain __schema")
        assertFalse(sdl.contains("__type"), "[$filename] SDL should not contain __type")
        assertFalse(sdl.contains("__typename"), "[$filename] SDL should not contain __typename")
        assertFalse(sdl.contains("__Field"), "[$filename] SDL should not contain __Field")
        assertFalse(sdl.contains("__InputValue"), "[$filename] SDL should not contain __InputValue")
        assertFalse(sdl.contains("__EnumValue"), "[$filename] SDL should not contain __EnumValue")
        assertFalse(sdl.contains("__Directive"), "[$filename] SDL should not contain __Directive")

        // Verify original introspection DID contain metadata (proving the reducer stripped them)
        assertTrue(
            introspectionJson.contains("__schema") || introspectionJson.contains("__type"),
            "[$filename] Original introspection should contain metadata fields"
        )
    }
}
