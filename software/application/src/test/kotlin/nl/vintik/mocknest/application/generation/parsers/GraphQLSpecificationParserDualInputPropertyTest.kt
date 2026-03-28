package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test for dual input mode support in GraphQLSpecificationParser.
 * 
 * Property 3: Dual Input Mode Support
 * Validates: Requirements 1.4
 * 
 * For any valid GraphQL schema, whether provided as pre-fetched schema content
 * or as an introspection endpoint URL, the system should successfully parse it
 * into an APISpecification.
 * 
 * STATUS: PARTIALLY COMPLETE
 * - ✅ Pre-fetched schema mode tests are implemented and passing
 * - ⏳ URL-based introspection tests are prepared but commented out
 * - TODO: Complete URL-based tests in Phase 3 after Task 3.5 (Wire GraphQLIntrospectionClient)
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-3")
class GraphQLSpecificationParserDualInputPropertyTest {

    private val mockReducer: GraphQLSchemaReducerInterface = mockk(relaxed = true)
    private val mockIntrospectionClient: GraphQLIntrospectionClientInterface = mockk(relaxed = true)
    private val parser = GraphQLSpecificationParser(mockIntrospectionClient, mockReducer, urlSafetyValidator = {})

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    companion object {
        /**
         * Provides test data files for pre-fetched schema testing.
         * Each file represents a different schema complexity and structure.
         */
        @JvmStatic
        fun preFetchedSchemaTestCases(): List<PreFetchedSchemaTestCase> {
            return listOf(
                PreFetchedSchemaTestCase(
                    name = "simple-schema",
                    filename = "simple-schema.json",
                    expectedQueries = 1,
                    expectedMutations = 1,
                    expectedTypes = 2,
                    expectedEnums = 1
                ),
                PreFetchedSchemaTestCase(
                    name = "complex-schema",
                    filename = "complex-schema.json",
                    expectedQueries = 5,
                    expectedMutations = 3,
                    expectedTypes = 8,
                    expectedEnums = 2
                ),
                PreFetchedSchemaTestCase(
                    name = "minimal-schema",
                    filename = "minimal-schema.json",
                    expectedQueries = 1,
                    expectedMutations = 0,
                    expectedTypes = 1,
                    expectedEnums = 0
                ),
                PreFetchedSchemaTestCase(
                    name = "queries-only-schema",
                    filename = "queries-only-schema.json",
                    expectedQueries = 3,
                    expectedMutations = 0,
                    expectedTypes = 3,
                    expectedEnums = 0
                ),
                PreFetchedSchemaTestCase(
                    name = "mutations-only-schema",
                    filename = "mutations-only-schema.json",
                    expectedQueries = 0,
                    expectedMutations = 3,
                    expectedTypes = 3,
                    expectedEnums = 0
                ),
                PreFetchedSchemaTestCase(
                    name = "with-enums-schema",
                    filename = "with-enums-schema.json",
                    expectedQueries = 2,
                    expectedMutations = 1,
                    expectedTypes = 3,
                    expectedEnums = 3
                ),
                PreFetchedSchemaTestCase(
                    name = "nested-types-schema",
                    filename = "nested-types-schema.json",
                    expectedQueries = 2,
                    expectedMutations = 1,
                    expectedTypes = 5,
                    expectedEnums = 1
                ),
                PreFetchedSchemaTestCase(
                    name = "large-schema-100-ops",
                    filename = "large-schema-100-ops.json",
                    expectedQueries = 60,
                    expectedMutations = 40,
                    expectedTypes = 50,
                    expectedEnums = 10
                )
            )
        }

        /**
         * Provides test data for URL-based introspection testing.
         * 
         * TODO: Uncomment and activate in Phase 3, Task 3.6 after GraphQLIntrospectionClient
         * is wired into GraphQLSpecificationParser (Task 3.5).
         * 
         * These test cases are prepared but currently commented out because:
         * 1. GraphQLIntrospectionClient doesn't exist yet (implemented in Task 3.2)
         * 2. Parser doesn't have URL detection logic yet (added in Task 3.5)
         * 3. Parser constructor doesn't accept introspection client yet (updated in Task 3.5)
         */
        @JvmStatic
        fun urlBasedIntrospectionTestCases(): List<UrlBasedTestCase> {
            return listOf(
                UrlBasedTestCase(
                    name = "simple-graphql-endpoint",
                    url = "https://example.com/graphql",
                    schemaFilename = "simple-schema.json",
                    expectedQueries = 1,
                    expectedMutations = 1
                ),
                UrlBasedTestCase(
                    name = "complex-graphql-endpoint",
                    url = "https://api.example.com/graphql",
                    schemaFilename = "complex-schema.json",
                    expectedQueries = 5,
                    expectedMutations = 3
                ),
                UrlBasedTestCase(
                    name = "minimal-graphql-endpoint",
                    url = "https://minimal.example.com/graphql",
                    schemaFilename = "minimal-schema.json",
                    expectedQueries = 1,
                    expectedMutations = 0
                )
            )
        }
    }

    /**
     * Test case for pre-fetched schema content.
     */
    data class PreFetchedSchemaTestCase(
        val name: String,
        val filename: String,
        val expectedQueries: Int,
        val expectedMutations: Int,
        val expectedTypes: Int,
        val expectedEnums: Int
    ) {
        override fun toString(): String = name
    }

    /**
     * Test case for URL-based introspection.
     */
    data class UrlBasedTestCase(
        val name: String,
        val url: String,
        val schemaFilename: String,
        val expectedQueries: Int,
        val expectedMutations: Int
    ) {
        override fun toString(): String = name
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    private fun createMockCompactSchema(
        queryCount: Int,
        mutationCount: Int,
        typeCount: Int,
        enumCount: Int
    ): CompactGraphQLSchema {
        val queries = (1..queryCount).map { i ->
            GraphQLOperation(
                name = "query$i",
                arguments = listOf(GraphQLArgument("id", "ID!", "ID argument")),
                returnType = "Type$i",
                description = "Query $i"
            )
        }

        val mutations = (1..mutationCount).map { i ->
            GraphQLOperation(
                name = "mutation$i",
                arguments = listOf(GraphQLArgument("input", "Input$i!", "Input argument")),
                returnType = "Type$i",
                description = "Mutation $i"
            )
        }

        val types = (1..typeCount).associate { i ->
            "Type$i" to GraphQLType(
                name = "Type$i",
                fields = listOf(
                    GraphQLField("id", "ID!", null),
                    GraphQLField("name", "String!", null)
                ),
                description = "Type $i"
            )
        }

        val enums = (1..enumCount).associate { i ->
            "Enum$i" to GraphQLEnum(
                name = "Enum$i",
                values = listOf("VALUE1", "VALUE2", "VALUE3"),
                description = "Enum $i"
            )
        }

        return CompactGraphQLSchema(
            queries = queries,
            mutations = mutations,
            types = types,
            enums = enums,
            metadata = GraphQLSchemaMetadata(
                schemaVersion = "1.0",
                description = "Test GraphQL API"
            )
        )
    }

    @ParameterizedTest(name = "Pre-fetched schema: {0}")
    @MethodSource("preFetchedSchemaTestCases")
    fun `Property 3 - Given pre-fetched schema content When parsing Then should produce valid APISpecification`(
        testCase: PreFetchedSchemaTestCase
    ) = runTest {
        // Given - Load pre-fetched schema content from test data file
        val schemaContent = loadTestData(testCase.filename)
        val mockSchema = createMockCompactSchema(
            queryCount = testCase.expectedQueries,
            mutationCount = testCase.expectedMutations,
            typeCount = testCase.expectedTypes,
            enumCount = testCase.expectedEnums
        )
        coEvery { mockReducer.reduce(schemaContent) } returns mockSchema

        // When - Parse the pre-fetched schema content
        val result = parser.parse(schemaContent, SpecificationFormat.GRAPHQL)

        // Then - Verify valid APISpecification is produced
        assertNotNull(result, "APISpecification should not be null for ${testCase.name}")
        assertEquals(SpecificationFormat.GRAPHQL, result.format, "Format should be GRAPHQL for ${testCase.name}")
        
        // Verify endpoints are created from operations
        val totalExpectedEndpoints = testCase.expectedQueries + testCase.expectedMutations
        assertEquals(
            totalExpectedEndpoints,
            result.endpoints.size,
            "Should have $totalExpectedEndpoints endpoints for ${testCase.name}"
        )
        
        // Verify all endpoints are POST to /graphql
        result.endpoints.forEach { endpoint ->
            assertEquals("/graphql", endpoint.path, "All GraphQL endpoints should use /graphql path")
            assertEquals(org.springframework.http.HttpMethod.POST, endpoint.method, "All GraphQL endpoints should use POST method")
            assertNotNull(endpoint.requestBody, "All GraphQL endpoints should have request body")
            assertTrue(endpoint.requestBody?.required == true, "Request body should be required")
        }
        
        // Verify schemas are created from types and enums
        val totalExpectedSchemas = testCase.expectedTypes + testCase.expectedEnums
        assertTrue(
            result.schemas.size >= totalExpectedSchemas,
            "Should have at least $totalExpectedSchemas schemas for ${testCase.name}, got ${result.schemas.size}"
        )
        
        // Verify metadata
        assertNotNull(result.metadata, "Metadata should not be null")
        assertEquals("graphql", result.metadata["operationType"], "Operation type should be graphql")
        assertEquals(testCase.expectedQueries.toString(), result.metadata["queryCount"], "Query count should match")
        assertEquals(testCase.expectedMutations.toString(), result.metadata["mutationCount"], "Mutation count should match")
        
        // Verify raw content is preserved
        assertEquals(schemaContent, result.rawContent, "Raw content should be preserved")
        
        // Verify version and title are set
        assertNotNull(result.version, "Version should not be null")
        assertNotNull(result.title, "Title should not be null")
    }

    @ParameterizedTest(name = "URL-based introspection: {0}")
    @MethodSource("urlBasedIntrospectionTestCases")
    fun `Property 3 - Given GraphQL endpoint URL When parsing Then should produce valid APISpecification`(
        testCase: UrlBasedTestCase
    ) = runTest {
        // Given - Mock introspection client to return schema for URL
        val introspectionJson = loadTestData(testCase.schemaFilename)
        val mockSchema = createMockCompactSchema(
            queryCount = testCase.expectedQueries,
            mutationCount = testCase.expectedMutations,
            typeCount = 5,
            enumCount = 2
        )

        coEvery { mockIntrospectionClient.introspect(testCase.url, any(), any()) } returns introspectionJson
        coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

        // When - Parse using URL (should trigger introspection)
        val result = parser.parse(testCase.url, SpecificationFormat.GRAPHQL)

        // Then - Verify valid APISpecification is produced
        assertNotNull(result, "APISpecification should not be null for ${testCase.name}")
        assertEquals(SpecificationFormat.GRAPHQL, result.format, "Format should be GRAPHQL for ${testCase.name}")

        // Verify endpoints are created
        val totalExpectedEndpoints = testCase.expectedQueries + testCase.expectedMutations
        assertEquals(
            totalExpectedEndpoints,
            result.endpoints.size,
            "Should have $totalExpectedEndpoints endpoints for ${testCase.name}"
        )

        // Verify all endpoints follow GraphQL-over-HTTP pattern
        result.endpoints.forEach { endpoint ->
            assertEquals("/graphql", endpoint.path, "All GraphQL endpoints should use /graphql path")
            assertEquals(org.springframework.http.HttpMethod.POST, endpoint.method, "All GraphQL endpoints should use POST method")
        }

        // Verify introspection client was called with correct URL
        coVerify { mockIntrospectionClient.introspect(testCase.url, any(), any()) }
    }

    @ParameterizedTest(name = "Validation: {0}")
    @MethodSource("preFetchedSchemaTestCases")
    fun `Property 3 - Given valid schema content When validating Then should return valid result`(
        testCase: PreFetchedSchemaTestCase
    ) = runTest {
        // Given
        val schemaContent = loadTestData(testCase.filename)
        val mockSchema = createMockCompactSchema(
            queryCount = testCase.expectedQueries,
            mutationCount = testCase.expectedMutations,
            typeCount = testCase.expectedTypes,
            enumCount = testCase.expectedEnums
        )
        coEvery { mockReducer.reduce(schemaContent) } returns mockSchema

        // When
        val result = parser.validate(schemaContent, SpecificationFormat.GRAPHQL)

        // Then
        assertTrue(result.isValid, "Validation should succeed for ${testCase.name}")
        assertTrue(result.errors.isEmpty(), "Should have no validation errors for ${testCase.name}")
    }

    @ParameterizedTest(name = "Metadata extraction: {0}")
    @MethodSource("preFetchedSchemaTestCases")
    fun `Property 3 - Given valid schema content When extracting metadata Then should return correct metadata`(
        testCase: PreFetchedSchemaTestCase
    ) = runTest {
        // Given
        val schemaContent = loadTestData(testCase.filename)
        val mockSchema = createMockCompactSchema(
            queryCount = testCase.expectedQueries,
            mutationCount = testCase.expectedMutations,
            typeCount = testCase.expectedTypes,
            enumCount = testCase.expectedEnums
        )
        coEvery { mockReducer.reduce(schemaContent) } returns mockSchema

        // When
        val metadata = parser.extractMetadata(schemaContent, SpecificationFormat.GRAPHQL)

        // Then
        assertNotNull(metadata, "Metadata should not be null for ${testCase.name}")
        assertEquals(SpecificationFormat.GRAPHQL, metadata.format, "Format should be GRAPHQL")
        assertEquals(
            testCase.expectedQueries + testCase.expectedMutations,
            metadata.endpointCount,
            "Endpoint count should match total operations for ${testCase.name}"
        )
        assertEquals(
            testCase.expectedTypes + testCase.expectedEnums,
            metadata.schemaCount,
            "Schema count should match total types and enums for ${testCase.name}"
        )
        assertNotNull(metadata.title, "Title should not be null")
        assertNotNull(metadata.version, "Version should not be null")
    }
}
