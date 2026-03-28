package nl.vintik.mocknest.infra.aws.generation.graphql

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end integration test for GraphQL URL-based introspection and parsing.
 * Validates the full chain: specificationUrl → real introspection client → real parser → schema output.
 *
 * Uses WireMock to simulate a GraphQL endpoint with introspection enabled.
 */
@Tag("graphql-introspection-ai-generation")
class GraphQLUrlEndToEndTest {

    private lateinit var wireMockServer: WireMockServer

    private fun loadTestData(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `Given GraphQL URL When parsing via real introspection client Then should produce valid endpoints`() = runTest {
        // Given - WireMock serves introspection response
        val introspectionJson = loadTestData("simple-schema.json")
        wireMockServer.stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(introspectionJson)
                )
        )

        val endpointUrl = "http://localhost:${wireMockServer.port()}/graphql"

        // Real components — no mocks
        val realClient = GraphQLIntrospectionClient()
        val realReducer = GraphQLSchemaReducer()
        // Bypass SSRF validation for localhost WireMock in tests
        val realParser = GraphQLSpecificationParser(
            introspectionClient = realClient,
            schemaReducer = realReducer,
            urlSafetyValidator = {}
        )

        // When - full chain: URL → introspection fetch → schema parse
        val specification = realParser.parse(endpointUrl, SpecificationFormat.GRAPHQL)

        // Then - should have parsed the introspection response into endpoints
        assertTrue(specification.endpoints.isNotEmpty(), "Should have parsed endpoints from introspection")
        assertEquals(SpecificationFormat.GRAPHQL, specification.format)
        specification.endpoints.forEach { endpoint ->
            assertEquals("/graphql", endpoint.path, "All GraphQL endpoints should use /graphql path")
            assertEquals(HttpMethod.POST, endpoint.method, "All GraphQL endpoints should use POST method")
        }

        // Verify introspection was actually called on the WireMock server
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(containing("__schema"))
        )
    }

    @Test
    fun `Given GraphQL URL and parsed schema When validating mock Then should pass validation`() = runTest {
        // Given - WireMock serves introspection response
        val introspectionJson = loadTestData("simple-schema.json")
        wireMockServer.stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(introspectionJson)
                )
        )

        val endpointUrl = "http://localhost:${wireMockServer.port()}/graphql"
        val testNamespace = MockNamespace(apiName = "graphql-e2e")

        val realClient = GraphQLIntrospectionClient()
        val realReducer = GraphQLSchemaReducer()
        // Bypass SSRF validation for localhost WireMock in tests
        val realParser = GraphQLSpecificationParser(
            introspectionClient = realClient,
            schemaReducer = realReducer,
            urlSafetyValidator = {}
        )
        val realValidator = GraphQLMockValidator()

        // Parse schema from URL
        val specification = realParser.parse(endpointUrl, SpecificationFormat.GRAPHQL)

        // Create a valid mock based on the parsed schema
        val validMock = GeneratedMock(
            id = UUID.randomUUID().toString(),
            name = "GraphQL user mock",
            namespace = testNamespace,
            wireMockMapping = """{"request":{"method":"POST","urlPath":"/graphql","bodyPatterns":[{"equalToJson":"{\"query\":\"query user(${'$'}id: ID!) { user(id: ${'$'}id) { id name email } }\",\"operationName\":\"user\",\"variables\":{\"id\":\"user-123\"}}"}]},"response":{"status":200,"jsonBody":{"data":{"user":{"id":"user-123","name":"Alice Smith","email":"alice@example.com"}}}}}""",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "GraphQL API: e2e test",
                endpoint = EndpointInfo(
                    method = HttpMethod.POST,
                    path = "/graphql",
                    statusCode = 200,
                    contentType = "application/json"
                )
            ),
            generatedAt = Instant.now()
        )

        // When - validate the mock against the URL-parsed specification
        val validationResult = realValidator.validate(validMock, specification)

        // Then
        assertTrue(
            validationResult.isValid,
            "Mock should pass validation against URL-parsed schema. Errors: ${validationResult.errors}"
        )
    }
}
