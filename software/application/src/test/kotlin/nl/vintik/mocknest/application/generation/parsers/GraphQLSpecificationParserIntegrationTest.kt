package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.domain.generation.GraphQLIntrospectionException
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for GraphQLSpecificationParser with introspection client.
 * Tests the full parsing pipeline using real GraphQLSchemaReducer and mocked introspection client.
 */
class GraphQLSpecificationParserIntegrationTest {

    private val mockIntrospectionClient: GraphQLIntrospectionClientInterface = mockk(relaxed = true)
    private val realReducer = GraphQLSchemaReducer()
    private val parser = GraphQLSpecificationParser(mockIntrospectionClient, realReducer, urlSafetyValidator = {})

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    @Nested
    inner class UrlBasedIntrospection {

        @Test
        fun `Given GraphQL endpoint URL When parsing Then should call introspection client`() = runTest {
            // Given
            val endpointUrl = "https://example.com/graphql"
            val introspectionJson = loadTestData("simple-schema.json")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } returns introspectionJson

            // When
            val result = parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { mockIntrospectionClient.introspect(endpointUrl, any(), any()) }
        }

        @Test
        fun `Given GraphQL endpoint URL When parsing Then should produce valid APISpecification`() = runTest {
            // Given
            val endpointUrl = "https://api.example.com/graphql"
            val introspectionJson = loadTestData("simple-schema.json")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } returns introspectionJson

            // When
            val result = parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            assertEquals(SpecificationFormat.GRAPHQL, result.format)
            assertTrue(result.endpoints.isNotEmpty(), "Should have at least one endpoint")
            result.endpoints.forEach { endpoint ->
                assertEquals("/graphql", endpoint.path)
                assertEquals(HttpMethod.POST, endpoint.method)
            }
        }

        @Test
        fun `Given complex schema URL When parsing Then should extract all operations`() = runTest {
            // Given
            val endpointUrl = "https://complex.example.com/graphql"
            val introspectionJson = loadTestData("complex-schema.json")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } returns introspectionJson

            // When
            val result = parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            assertTrue(result.endpoints.size >= 5, "Complex schema should have multiple endpoints")
        }
    }

    @Nested
    inner class PreFetchedSchemaMode {

        @Test
        fun `Given pre-fetched schema content When parsing Then should NOT call introspection client`() = runTest {
            // Given
            val schemaContent = loadTestData("simple-schema.json")

            // When
            val result = parser.parse(schemaContent, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            coVerify(exactly = 0) { mockIntrospectionClient.introspect(any(), any(), any()) }
        }

        @Test
        fun `Given pre-fetched simple schema When parsing Then should produce valid APISpecification`() = runTest {
            // Given
            val schemaContent = loadTestData("simple-schema.json")

            // When
            val result = parser.parse(schemaContent, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            assertEquals(SpecificationFormat.GRAPHQL, result.format)
            assertTrue(result.endpoints.isNotEmpty())
            assertEquals("graphql", result.metadata["operationType"])
        }

        @Test
        fun `Given pre-fetched schema with enums When parsing Then should include enum schemas`() = runTest {
            // Given
            val schemaContent = loadTestData("with-enums-schema.json")

            // When
            val result = parser.parse(schemaContent, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            assertTrue(result.schemas.isNotEmpty(), "Should have schemas from enum types")
        }
    }

    @Nested
    inner class ErrorPropagation {

        @Test
        fun `Given introspection client throws exception When parsing URL Then should propagate exception`() = runTest {
            // Given
            val endpointUrl = "https://unreachable.example.com/graphql"
            val introspectionException = GraphQLIntrospectionException("Network failure: endpoint unreachable")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } throws introspectionException

            // When & Then
            assertThrows<GraphQLIntrospectionException> {
                parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)
            }
        }

        @Test
        fun `Given introspection disabled error When parsing URL Then should propagate exception`() = runTest {
            // Given
            val endpointUrl = "https://no-introspection.example.com/graphql"
            val introspectionException = GraphQLIntrospectionException("Introspection disabled on endpoint")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } throws introspectionException

            // When & Then
            val thrown = assertThrows<GraphQLIntrospectionException> {
                parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)
            }
            assertTrue(thrown.message?.contains("Introspection disabled") == true)
        }

        @Test
        fun `Given rate limit error When parsing URL Then should propagate exception`() = runTest {
            // Given
            val endpointUrl = "https://rate-limited.example.com/graphql"
            val rateLimitException = GraphQLIntrospectionException("Rate limited by endpoint (HTTP 429)")
            coEvery { mockIntrospectionClient.introspect(endpointUrl, any(), any()) } throws rateLimitException

            // When & Then
            val thrown = assertThrows<GraphQLIntrospectionException> {
                parser.parse(endpointUrl, SpecificationFormat.GRAPHQL)
            }
            assertTrue(thrown.message?.contains("Rate limited") == true)
        }
    }
}
