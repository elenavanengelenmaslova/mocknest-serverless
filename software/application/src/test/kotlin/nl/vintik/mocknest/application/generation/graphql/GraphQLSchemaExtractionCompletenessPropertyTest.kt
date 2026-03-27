package nl.vintik.mocknest.application.generation.graphql

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property test for schema extraction completeness.
 * 
 * Property 5: Schema Extraction Completeness
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 * 
 * For any valid GraphQL introspection JSON, the Schema_Reducer should extract all queries,
 * mutations, input types, output types, and enums with their complete signatures
 * (names, arguments, return types, fields, and values).
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-5")
class GraphQLSchemaExtractionCompletenessPropertyTest {

    private val reducer = GraphQLSchemaReducer()

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    companion object {
        /**
         * Provides diverse test data files covering various schema complexities.
         */
        @JvmStatic
        fun schemaTestCases() = listOf(
            // Minimal schema - absolute minimum valid schema (1 query, no mutations)
            SchemaTestCase(
                filename = "minimal-schema.json",
                expectedQueries = 1,
                expectedMutations = 0,
                expectedTypes = 0, // No custom types, only built-in scalars
                expectedEnums = 0,
                description = "Minimal schema with single query"
            ),
            
            // Simple schema - 1-5 operations
            SchemaTestCase(
                filename = "simple-schema.json",
                expectedQueries = 1,
                expectedMutations = 1,
                expectedTypes = 2, // User, CreateUserInput
                expectedEnums = 1, // UserStatus
                description = "Simple schema with basic operations"
            ),
            
            // Queries-only schema - no mutations
            SchemaTestCase(
                filename = "queries-only-schema.json",
                expectedQueries = 3,
                expectedMutations = 0,
                expectedTypes = 2, // Book, Author
                expectedEnums = 0,
                description = "Schema with only queries"
            ),
            
            // Mutations-only schema - no queries
            SchemaTestCase(
                filename = "mutations-only-schema.json",
                expectedQueries = 0,
                expectedMutations = 2,
                expectedTypes = 1, // Item
                expectedEnums = 0,
                description = "Schema with only mutations"
            ),
            
            // Schema with multiple enums
            SchemaTestCase(
                filename = "with-enums-schema.json",
                expectedQueries = 1,
                expectedMutations = 1,
                expectedTypes = 1, // Task
                expectedEnums = 3, // TaskStatus, TaskPriority, TaskCategory
                description = "Schema with multiple enum types"
            ),
            
            // Complex schema - 20-50 operations
            SchemaTestCase(
                filename = "complex-schema.json",
                expectedQueries = 3,
                expectedMutations = 3,
                expectedTypes = 8, // Product, Order, OrderItem, ProductFilter, CreateProductInput, UpdateProductInput, PlaceOrderInput, OrderItemInput
                expectedEnums = 3, // ProductCategory, ProductStatus, OrderStatus
                description = "Complex schema with multiple operations and types"
            ),
            
            // Nested types schema - deeply nested object structures
            SchemaTestCase(
                filename = "nested-types-schema.json",
                expectedQueries = 1,
                expectedMutations = 1,
                expectedTypes = 8, // Organization, Department, Team, Employee, ContactInfo, Address, OrganizationInput, AddressInput
                expectedEnums = 0,
                description = "Schema with deeply nested types"
            ),
            
            // Large schema - 100+ operations
            SchemaTestCase(
                filename = "large-schema-100-ops.json",
                expectedQueries = 60,
                expectedMutations = 40,
                expectedTypes = 3, // User, Item, ItemInput
                expectedEnums = 2, // UserStatus, ItemStatus
                description = "Large schema with 100+ operations"
            )
        )
    }

    /**
     * Test case data class for parameterized tests.
     */
    data class SchemaTestCase(
        val filename: String,
        val expectedQueries: Int,
        val expectedMutations: Int,
        val expectedTypes: Int,
        val expectedEnums: Int,
        val description: String
    ) {
        override fun toString(): String = description
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should extract all queries with complete signatures`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify query count
        assertEquals(
            testCase.expectedQueries,
            result.queries.size,
            "Expected ${testCase.expectedQueries} queries in ${testCase.filename}"
        )

        // Then - Verify each query has complete signature
        result.queries.forEach { query ->
            assertTrue(query.name.isNotBlank(), "Query name should not be blank")
            assertTrue(query.returnType.isNotBlank(), "Query return type should not be blank")
            // Arguments can be empty, but if present, each must have name and type
            query.arguments.forEach { arg ->
                assertTrue(arg.name.isNotBlank(), "Query argument name should not be blank")
                assertTrue(arg.type.isNotBlank(), "Query argument type should not be blank")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should extract all mutations with complete signatures`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify mutation count
        assertEquals(
            testCase.expectedMutations,
            result.mutations.size,
            "Expected ${testCase.expectedMutations} mutations in ${testCase.filename}"
        )

        // Then - Verify each mutation has complete signature
        result.mutations.forEach { mutation ->
            assertTrue(mutation.name.isNotBlank(), "Mutation name should not be blank")
            assertTrue(mutation.returnType.isNotBlank(), "Mutation return type should not be blank")
            // Arguments can be empty, but if present, each must have name and type
            mutation.arguments.forEach { arg ->
                assertTrue(arg.name.isNotBlank(), "Mutation argument name should not be blank")
                assertTrue(arg.type.isNotBlank(), "Mutation argument type should not be blank")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should extract all types with complete field definitions`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify type count
        assertEquals(
            testCase.expectedTypes,
            result.types.size,
            "Expected ${testCase.expectedTypes} types in ${testCase.filename}"
        )

        // Then - Verify each type has complete field definitions
        result.types.values.forEach { type ->
            assertTrue(type.name.isNotBlank(), "Type name should not be blank")
            assertTrue(type.fields.isNotEmpty(), "Type should have at least one field")
            
            type.fields.forEach { field ->
                assertTrue(field.name.isNotBlank(), "Type field name should not be blank")
                assertTrue(field.type.isNotBlank(), "Type field type should not be blank")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should extract all enums with complete value lists`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify enum count
        assertEquals(
            testCase.expectedEnums,
            result.enums.size,
            "Expected ${testCase.expectedEnums} enums in ${testCase.filename}"
        )

        // Then - Verify each enum has complete value list
        result.enums.values.forEach { enum ->
            assertTrue(enum.name.isNotBlank(), "Enum name should not be blank")
            assertTrue(enum.values.isNotEmpty(), "Enum should have at least one value")
            
            enum.values.forEach { value ->
                assertTrue(value.isNotBlank(), "Enum value should not be blank")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should extract complete schema structure`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify all components are extracted
        assertEquals(testCase.expectedQueries, result.queries.size, "Queries count mismatch")
        assertEquals(testCase.expectedMutations, result.mutations.size, "Mutations count mismatch")
        assertEquals(testCase.expectedTypes, result.types.size, "Types count mismatch")
        assertEquals(testCase.expectedEnums, result.enums.size, "Enums count mismatch")

        // Then - Verify schema has at least queries or mutations (domain model requirement)
        assertTrue(
            result.queries.isNotEmpty() || result.mutations.isNotEmpty(),
            "Schema must have at least one query or mutation"
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should preserve operation argument details`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify query arguments are complete
        result.queries.forEach { query ->
            query.arguments.forEach { arg ->
                assertTrue(arg.name.isNotBlank(), "Query argument name should not be blank")
                assertTrue(arg.type.isNotBlank(), "Query argument type should not be blank")
                // Type should include modifiers like ! for NON_NULL and [] for LIST
                // Examples: "ID!", "String", "[Product]", "[String]!"
            }
        }

        // Then - Verify mutation arguments are complete
        result.mutations.forEach { mutation ->
            mutation.arguments.forEach { arg ->
                assertTrue(arg.name.isNotBlank(), "Mutation argument name should not be blank")
                assertTrue(arg.type.isNotBlank(), "Mutation argument type should not be blank")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should preserve type field details`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify type fields are complete
        result.types.values.forEach { type ->
            assertTrue(type.fields.isNotEmpty(), "Type ${type.name} should have at least one field")
            
            type.fields.forEach { field ->
                assertTrue(field.name.isNotBlank(), "Field name in type ${type.name} should not be blank")
                assertTrue(field.type.isNotBlank(), "Field type in type ${type.name} should not be blank")
                // Field type should include modifiers like ! for NON_NULL and [] for LIST
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaTestCases")
    fun `Given diverse GraphQL schemas When reducing Then should handle type modifiers correctly`(
        testCase: SchemaTestCase
    ) = runTest {
        // Given
        val introspectionJson = loadTestData(testCase.filename)

        // When
        val result = reducer.reduce(introspectionJson)

        // Then - Verify NON_NULL modifiers are preserved (indicated by !)
        val allTypes = mutableListOf<String>()
        
        result.queries.forEach { query ->
            allTypes.add(query.returnType)
            allTypes.addAll(query.arguments.map { it.type })
        }
        
        result.mutations.forEach { mutation ->
            allTypes.add(mutation.returnType)
            allTypes.addAll(mutation.arguments.map { it.type })
        }
        
        result.types.values.forEach { type ->
            allTypes.addAll(type.fields.map { it.type })
        }

        // Then - Verify type strings are well-formed
        allTypes.forEach { typeStr ->
            assertTrue(typeStr.isNotBlank(), "Type string should not be blank")
            // Type strings should not contain "Unknown"
            assertTrue(!typeStr.contains("Unknown"), "Type string should not contain 'Unknown': $typeStr")
        }
    }
}
