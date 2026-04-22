package nl.vintik.mocknest.infra.aws.generation.ai.eval

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural validation tests for GraphQL introspection schema files used in the GraphQL eval suite.
 *
 * Validates: Requirements 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 6.1, 6.2, 6.3, 6.4, 6.5
 * Correctness Properties: 1
 */
@Tag("graphql-eval-introspection")
class GraphqlIntrospectionSpecValidationTest {

    private val schemaReducer = GraphQLSchemaReducer()

    private val allIntrospectionFiles = listOf(
        "pokemon-graphql-introspection.json",
        "books-graphql-introspection.json",
        "ecommerce-graphql-introspection.json",
        "taskmanagement-graphql-introspection.json"
    )

    private fun loadIntrospectionContent(fileName: String): String {
        val stream = javaClass.getResourceAsStream("/eval/$fileName")
        assertNotNull(stream, "Introspection file not found on classpath: eval/$fileName")
        return stream.use { it.bufferedReader().readText() }
    }

    /**
     * **Validates: Requirements 1.8, 1.9**
     * **Feature: graphql-eval-test-expansion, Property 1: Introspection schema parsing and reduction validity**
     */
    @Nested
    inner class IntrospectionParsingAndReduction {

        @ParameterizedTest
        @ValueSource(
            strings = [
                "pokemon-graphql-introspection.json",
                "books-graphql-introspection.json",
                "ecommerce-graphql-introspection.json",
                "taskmanagement-graphql-introspection.json"
            ]
        )
        fun `Given introspection file When reducing Then should succeed without exception`(fileName: String) = runTest {
            val json = loadIntrospectionContent(fileName)
            val compactSchema = schemaReducer.reduce(json)

            assertNotNull(compactSchema)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "pokemon-graphql-introspection.json",
                "books-graphql-introspection.json",
                "ecommerce-graphql-introspection.json",
                "taskmanagement-graphql-introspection.json"
            ]
        )
        fun `Given introspection file When reducing Then CompactGraphQLSchema should have non-empty queries or mutations`(
            fileName: String
        ) = runTest {
            val json = loadIntrospectionContent(fileName)
            val compactSchema = schemaReducer.reduce(json)

            assertTrue(
                compactSchema.queries.isNotEmpty() || compactSchema.mutations.isNotEmpty(),
                "CompactGraphQLSchema should have non-empty queries or mutations for $fileName"
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "pokemon-graphql-introspection.json",
                "books-graphql-introspection.json",
                "ecommerce-graphql-introspection.json",
                "taskmanagement-graphql-introspection.json"
            ]
        )
        fun `Given introspection file When reducing Then types should be correctly extracted`(
            fileName: String
        ) = runTest {
            val json = loadIntrospectionContent(fileName)
            val compactSchema = schemaReducer.reduce(json)

            assertTrue(
                compactSchema.types.isNotEmpty(),
                "CompactGraphQLSchema should have non-empty types for $fileName"
            )
        }
    }

    /**
     * **Validates: Requirements 1.3, 1.4, 1.5, 1.6, 1.7**
     */
    @Nested
    inner class IntrospectionStructuralRequirements {

        @Test
        fun `Given all schemas When checking Then at least one has mutation operations`() = runTest {
            val hasMutations = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                compactSchema.mutations.isNotEmpty()
            }

            assertTrue(
                hasMutations,
                "At least one introspection schema should have mutation operations"
            )
        }

        @Test
        fun `Given all schemas When checking Then at least one has ENUM types`() = runTest {
            val hasEnums = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                compactSchema.enums.isNotEmpty()
            }

            assertTrue(
                hasEnums,
                "At least one introspection schema should have ENUM types"
            )
        }

        @Test
        fun `Given all schemas When checking Then at least one has INPUT_OBJECT types`() = runTest {
            val hasInputTypes = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                // Verify the raw introspection JSON contains INPUT_OBJECT definitions
                val rawHasInputObject = json.contains("\"INPUT_OBJECT\"")
                // Verify the reducer extracted those INPUT_OBJECT types into the types map
                // Parse the JSON to find INPUT_OBJECT type names reliably
                val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
                val types = root["data"]?.jsonObject?.get("__schema")?.jsonObject?.get("types")?.jsonArray
                    ?: kotlinx.serialization.json.JsonArray(emptyList())
                val inputObjectNames = types
                    .filter { it.jsonObject["kind"]?.jsonPrimitive?.content == "INPUT_OBJECT" }
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                    .toSet()
                val reducerHasInputTypes = inputObjectNames.any { it in compactSchema.types }
                rawHasInputObject && reducerHasInputTypes
            }

            assertTrue(
                hasInputTypes,
                "At least one introspection schema should have INPUT_OBJECT types extracted by the reducer"
            )
        }

        @Test
        fun `Given all schemas When checking Then at least one has nested object types`() = runTest {
            val hasNestedTypes = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                val typeNames = compactSchema.types.keys

                compactSchema.types.values.any { type ->
                    type.fields.any { field ->
                        // Strip all GraphQL wrappers (!, [], NON_NULL, LIST) to get the base type name
                        val baseType = field.type.replace(Regex("[\\[\\]!]"), "")
                        baseType in typeNames
                    }
                }
            }

            assertTrue(
                hasNestedTypes,
                "At least one introspection schema should have nested object types (a type field referencing another object type)"
            )
        }

        @Test
        fun `Given all schemas When checking Then at least one has 6 or more operations`() = runTest {
            val hasLargeOperationCount = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                (compactSchema.queries.size + compactSchema.mutations.size) >= 6
            }

            assertTrue(
                hasLargeOperationCount,
                "At least one introspection schema should have 6 or more operations (queries + mutations)"
            )
        }

        @Test
        fun `Given all schemas When checking Then at least one has multi-field types with 5 or more fields`() = runTest {
            val hasMultiFieldTypes = allIntrospectionFiles.any { fileName ->
                val json = loadIntrospectionContent(fileName)
                val compactSchema = schemaReducer.reduce(json)
                compactSchema.types.values.any { type ->
                    type.fields.size >= 5
                }
            }

            assertTrue(
                hasMultiFieldTypes,
                "At least one introspection schema should have multi-field types (5+ fields per type)"
            )
        }
    }
}
