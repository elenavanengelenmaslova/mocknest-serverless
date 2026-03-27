package nl.vintik.mocknest.infra.aws.generation.graphql

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Tag("graphql-introspection-ai-generation")
@Tag("Property-4")
class GraphQLIntrospectionClientPropertyTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var client: GraphQLIntrospectionClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
        client = GraphQLIntrospectionClient()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun loadIntrospectionJson(filename: String): String =
        this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")

    @ParameterizedTest(name = "Property 4 - Given {0} When introspecting Then returns valid JSON with schema")
    @ValueSource(
        strings = [
            "simple-schema.json",
            "complex-schema.json",
            "minimal-schema.json",
            "mutations-only-schema.json",
            "queries-only-schema.json",
            "nested-types-schema.json",
            "with-enums-schema.json",
            "large-schema-100-ops.json"
        ]
    )
    fun `Given valid introspection response When introspect Then returns valid JSON containing schema`(
        filename: String
    ) = runBlocking {
        // Given
        val introspectionJson = loadIntrospectionJson(filename)
        val url = "http://localhost:${wireMockServer.port()}/graphql"
        wireMockServer.stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(introspectionJson)
                )
        )

        // When
        val result = client.introspect(url, emptyMap(), 5000)

        // Then - result is valid JSON with schema structure
        val parsed = Json.parseToJsonElement(result).jsonObject
        assertNotNull(parsed["data"], "Response must contain 'data' field")

        val data = parsed["data"]!!.jsonObject
        assertNotNull(data["__schema"], "Data must contain '__schema' field")

        val schema = data["__schema"]!!.jsonObject
        assertTrue(
            schema.containsKey("queryType") || schema.containsKey("mutationType"),
            "Schema must have at least queryType or mutationType"
        )
        assertNotNull(schema["types"], "Schema must have 'types' array")
    }

    @ParameterizedTest(name = "Property 4 - Given {0} When introspecting Then response body is returned unmodified")
    @ValueSource(
        strings = [
            "simple-schema.json",
            "complex-schema.json",
            "minimal-schema.json",
            "mutations-only-schema.json",
            "queries-only-schema.json",
            "nested-types-schema.json",
            "with-enums-schema.json",
            "large-schema-100-ops.json"
        ]
    )
    fun `Given valid introspection response When introspect Then response body is returned unmodified`(
        filename: String
    ) = runBlocking {
        // Given
        val introspectionJson = loadIntrospectionJson(filename)
        val url = "http://localhost:${wireMockServer.port()}/graphql"
        wireMockServer.stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(introspectionJson)
                )
        )

        // When
        val result = client.introspect(url, emptyMap(), 5000)

        // Then - response body passes through unmodified
        assertEquals(introspectionJson, result)
    }
}
