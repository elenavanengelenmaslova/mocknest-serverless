package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLSpecificationParserTest {

    private val mockReducer: GraphQLSchemaReducerInterface = mockk(relaxed = true)
    private val mockIntrospectionClient: GraphQLIntrospectionClientInterface = mockk(relaxed = true)
    private val parser = GraphQLSpecificationParser(mockIntrospectionClient, mockReducer)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    private fun createMockCompactSchema(
        queries: List<GraphQLOperation> = listOf(
            GraphQLOperation(
                name = "user",
                arguments = listOf(GraphQLArgument("id", "ID!", "User ID")),
                returnType = "User",
                description = "Get user by ID"
            )
        ),
        mutations: List<GraphQLOperation> = listOf(
            GraphQLOperation(
                name = "createUser",
                arguments = listOf(GraphQLArgument("input", "CreateUserInput!", "User input data")),
                returnType = "User",
                description = "Create a new user"
            )
        ),
        types: Map<String, GraphQLType> = mapOf(
            "User" to GraphQLType(
                name = "User",
                fields = listOf(
                    GraphQLField("id", "ID!", null),
                    GraphQLField("name", "String!", null),
                    GraphQLField("email", "String", null)
                ),
                description = "User type"
            ),
            "CreateUserInput" to GraphQLType(
                name = "CreateUserInput",
                fields = listOf(
                    GraphQLField("name", "String!", null),
                    GraphQLField("email", "String", null)
                ),
                description = "Input for creating a user"
            )
        ),
        enums: Map<String, GraphQLEnum> = mapOf(
            "UserStatus" to GraphQLEnum(
                name = "UserStatus",
                values = listOf("ACTIVE", "INACTIVE", "SUSPENDED"),
                description = "User status enum"
            )
        ),
        metadata: GraphQLSchemaMetadata = GraphQLSchemaMetadata(
            schemaVersion = "1.0",
            description = "GraphQL API"
        )
    ): CompactGraphQLSchema {
        return CompactGraphQLSchema(
            queries = queries,
            mutations = mutations,
            types = types,
            enums = enums,
            metadata = metadata
        )
    }

    @Nested
    inner class ParsingFromPreFetchedSchema {

        @Test
        fun `Given valid pre-fetched schema When parsing Then should return APISpecification`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertNotNull(result)
            assertEquals(SpecificationFormat.GRAPHQL, result.format)
            assertEquals("1.0", result.version)
            assertEquals("GraphQL API", result.title)
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should extract queries as endpoints`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val queryEndpoints = result.endpoints.filter { it.operationId == "user" }
            assertEquals(1, queryEndpoints.size)
            
            val userQuery = queryEndpoints.first()
            assertEquals("/graphql", userQuery.path)
            assertEquals(HttpMethod.POST, userQuery.method)
            assertEquals("user", userQuery.operationId)
            assertEquals("Get user by ID", userQuery.summary)
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should extract mutations as endpoints`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val mutationEndpoints = result.endpoints.filter { it.operationId == "createUser" }
            assertEquals(1, mutationEndpoints.size)
            
            val createUserMutation = mutationEndpoints.first()
            assertEquals("/graphql", createUserMutation.path)
            assertEquals(HttpMethod.POST, createUserMutation.method)
            assertEquals("createUser", createUserMutation.operationId)
            assertEquals("Create a new user", createUserMutation.summary)
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should convert types to JSON schemas`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertTrue(result.schemas.containsKey("User"))
            assertTrue(result.schemas.containsKey("CreateUserInput"))
            
            val userSchema = result.schemas["User"]!!
            assertEquals(JsonSchemaType.OBJECT, userSchema.type)
            assertEquals(3, userSchema.properties.size)
            assertTrue(userSchema.properties.containsKey("id"))
            assertTrue(userSchema.properties.containsKey("name"))
            assertTrue(userSchema.properties.containsKey("email"))
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should convert enums to JSON schemas`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertTrue(result.schemas.containsKey("UserStatus"))
            
            val statusSchema = result.schemas["UserStatus"]!!
            assertEquals(JsonSchemaType.STRING, statusSchema.type)
            assertEquals(3, statusSchema.enum.size)
            assertTrue(statusSchema.enum.contains("ACTIVE"))
            assertTrue(statusSchema.enum.contains("INACTIVE"))
            assertTrue(statusSchema.enum.contains("SUSPENDED"))
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should include metadata`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals("graphql", result.metadata["operationType"])
            assertEquals("1", result.metadata["queryCount"])
            assertEquals("1", result.metadata["mutationCount"])
        }

        @Test
        fun `Given pre-fetched schema When parsing Then should preserve raw content`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals(introspectionJson, result.rawContent)
        }
    }

    @Nested
    inner class ConversionToAPISpecification {

        @Test
        fun `Given GraphQL operation When converting Then should create POST endpoint with request body`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val endpoint = result.endpoints.first()
            assertNotNull(endpoint.requestBody)
            assertTrue(endpoint.requestBody?.required ?: false)
            assertTrue(endpoint.requestBody?.content?.containsKey("application/json") ?: false)
        }

        @Test
        fun `Given GraphQL operation When converting Then should create request schema with query field`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val endpoint = result.endpoints.first()
            val requestSchema = endpoint.requestBody?.content?.get("application/json")?.schema
            
            assertNotNull(requestSchema)
            assertEquals(JsonSchemaType.OBJECT, requestSchema.type)
            assertTrue(requestSchema.properties.containsKey("query"))
            assertTrue(requestSchema.properties.containsKey("variables"))
            assertTrue(requestSchema.properties.containsKey("operationName"))
            assertTrue(requestSchema.required.contains("query"))
        }

        @Test
        fun `Given GraphQL operation with arguments When converting Then should include variables schema`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val endpoint = result.endpoints.first { it.operationId == "user" }
            val requestSchema = endpoint.requestBody?.content?.get("application/json")?.schema
            val variablesSchema = requestSchema?.properties?.get("variables")
            
            assertNotNull(variablesSchema)
            assertEquals(JsonSchemaType.OBJECT, variablesSchema.type)
            assertTrue(variablesSchema.properties.containsKey("id"))
            assertEquals(JsonSchemaType.STRING, variablesSchema.properties["id"]?.type)
        }

        @Test
        fun `Given GraphQL operation When converting Then should create response schema with data and errors`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val endpoint = result.endpoints.first()
            val responseSchema = endpoint.responses[200]?.schema
            
            assertNotNull(responseSchema)
            assertEquals(JsonSchemaType.OBJECT, responseSchema.type)
            assertTrue(responseSchema.properties.containsKey("data"))
            assertTrue(responseSchema.properties.containsKey("errors"))
        }

        @Test
        fun `Given GraphQL type When converting Then should map required fields correctly`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val userSchema = result.schemas["User"]!!
            assertTrue(userSchema.required.contains("id"))
            assertTrue(userSchema.required.contains("name"))
            assertFalse(userSchema.required.contains("email"))
        }

        @Test
        fun `Given GraphQL scalar types When converting Then should map to JSON schema types correctly`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                types = mapOf(
                    "TestType" to GraphQLType(
                        name = "TestType",
                        fields = listOf(
                            GraphQLField("stringField", "String", null),
                            GraphQLField("intField", "Int", null),
                            GraphQLField("floatField", "Float", null),
                            GraphQLField("boolField", "Boolean", null),
                            GraphQLField("idField", "ID", null)
                        )
                    )
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val testSchema = result.schemas["TestType"]!!
            assertEquals(JsonSchemaType.STRING, testSchema.properties["stringField"]?.type)
            assertEquals(JsonSchemaType.INTEGER, testSchema.properties["intField"]?.type)
            assertEquals(JsonSchemaType.NUMBER, testSchema.properties["floatField"]?.type)
            assertEquals(JsonSchemaType.BOOLEAN, testSchema.properties["boolField"]?.type)
            assertEquals(JsonSchemaType.STRING, testSchema.properties["idField"]?.type)
        }

        @Test
        fun `Given GraphQL custom type When converting Then should map to OBJECT type`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                types = mapOf(
                    "TestType" to GraphQLType(
                        name = "TestType",
                        fields = listOf(
                            GraphQLField("customField", "CustomType", null)
                        )
                    )
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            val testSchema = result.schemas["TestType"]!!
            assertEquals(JsonSchemaType.OBJECT, testSchema.properties["customField"]?.type)
        }
    }

    @Nested
    inner class MetadataExtraction {

        @Test
        fun `Given valid schema When extracting metadata Then should return correct title`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                metadata = GraphQLSchemaMetadata(
                    schemaVersion = "2.0",
                    description = "My Custom API"
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals("My Custom API", metadata.title)
        }

        @Test
        fun `Given valid schema When extracting metadata Then should return correct version`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                metadata = GraphQLSchemaMetadata(
                    schemaVersion = "3.5.1",
                    description = "GraphQL API"
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals("3.5.1", metadata.version)
        }

        @Test
        fun `Given valid schema When extracting metadata Then should return correct format`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals(SpecificationFormat.GRAPHQL, metadata.format)
        }

        @Test
        fun `Given valid schema When extracting metadata Then should return correct endpoint count`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")
            val mockSchema = createMockCompactSchema(
                queries = listOf(
                    GraphQLOperation("query1", emptyList(), "Type1"),
                    GraphQLOperation("query2", emptyList(), "Type2"),
                    GraphQLOperation("query3", emptyList(), "Type3")
                ),
                mutations = listOf(
                    GraphQLOperation("mutation1", emptyList(), "Type1"),
                    GraphQLOperation("mutation2", emptyList(), "Type2")
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals(5, metadata.endpointCount)
        }

        @Test
        fun `Given valid schema When extracting metadata Then should return correct schema count`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")
            val mockSchema = createMockCompactSchema(
                types = mapOf(
                    "Type1" to GraphQLType("Type1", listOf(GraphQLField("field1", "String"))),
                    "Type2" to GraphQLType("Type2", listOf(GraphQLField("field2", "String"))),
                    "Type3" to GraphQLType("Type3", listOf(GraphQLField("field3", "String")))
                ),
                enums = mapOf(
                    "Enum1" to GraphQLEnum("Enum1", listOf("VALUE1")),
                    "Enum2" to GraphQLEnum("Enum2", listOf("VALUE2"))
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals(5, metadata.schemaCount)
        }

        @Test
        fun `Given schema without description When extracting metadata Then should use default title`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                metadata = GraphQLSchemaMetadata(
                    schemaVersion = "1.0",
                    description = null
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals("GraphQL API", metadata.title)
        }

        @Test
        fun `Given schema without version When extracting metadata Then should use default version`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema(
                metadata = GraphQLSchemaMetadata(
                    schemaVersion = null,
                    description = "GraphQL API"
                )
            )
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val metadata = parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertEquals("1.0", metadata.version)
        }
    }

    @Nested
    inner class ValidationOfInvalidSchemas {

        @Test
        fun `Given invalid JSON When validating Then should return invalid result`() = runTest {
            // Given
            val invalidJson = "{ invalid json }"
            coEvery { mockReducer.reduce(invalidJson) } throws GraphQLSchemaParsingException("Invalid JSON")

            // When
            val result = parser.validate(invalidJson, SpecificationFormat.GRAPHQL)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
            assertTrue(result.errors.first().message.contains("Invalid JSON"))
        }

        @Test
        fun `Given schema without operations When validating Then should return invalid result`() = runTest {
            // Given
            val schemaWithoutOps = """{"data": {"__schema": {"types": []}}}"""
            coEvery { mockReducer.reduce(schemaWithoutOps) } throws IllegalArgumentException("Schema must have at least one query or mutation")

            // When
            val result = parser.validate(schemaWithoutOps, SpecificationFormat.GRAPHQL)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        fun `Given malformed introspection JSON When validating Then should return invalid result`() = runTest {
            // Given
            val malformedJson = """{"data": {}}"""
            coEvery { mockReducer.reduce(malformedJson) } throws GraphQLSchemaParsingException("Missing __schema field")

            // When
            val result = parser.validate(malformedJson, SpecificationFormat.GRAPHQL)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.first().message.contains("__schema"))
        }

        @Test
        fun `Given valid schema When validating Then should return valid result`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val mockSchema = createMockCompactSchema()
            coEvery { mockReducer.reduce(introspectionJson) } returns mockSchema

            // When
            val result = parser.validate(introspectionJson, SpecificationFormat.GRAPHQL)

            // Then
            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given reducer throws exception When parsing Then should propagate exception`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            coEvery { mockReducer.reduce(introspectionJson) } throws GraphQLSchemaParsingException("Parsing failed")

            // When & Then
            assertFailsWith<GraphQLSchemaParsingException> {
                parser.parse(introspectionJson, SpecificationFormat.GRAPHQL)
            }
        }

        @Test
        fun `Given reducer throws exception When extracting metadata Then should propagate exception`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            coEvery { mockReducer.reduce(introspectionJson) } throws GraphQLSchemaParsingException("Parsing failed")

            // When & Then
            assertFailsWith<GraphQLSchemaParsingException> {
                parser.extractMetadata(introspectionJson, SpecificationFormat.GRAPHQL)
            }
        }
    }

    @Nested
    inner class FormatSupportCheck {

        @Test
        fun `Given GRAPHQL format When checking support Then returns true`() {
            // When & Then
            assertTrue(parser.supports(SpecificationFormat.GRAPHQL))
        }

        @Test
        fun `Given OPENAPI_3 format When checking support Then returns false`() {
            // When & Then
            assertFalse(parser.supports(SpecificationFormat.OPENAPI_3))
        }

        @Test
        fun `Given SWAGGER_2 format When checking support Then returns false`() {
            // When & Then
            assertFalse(parser.supports(SpecificationFormat.SWAGGER_2))
        }

        @Test
        fun `Given WSDL format When checking support Then returns false`() {
            // When & Then
            assertFalse(parser.supports(SpecificationFormat.WSDL))
        }

        @Test
        fun `Given non-GRAPHQL format When parsing Then should throw IllegalArgumentException`() = runTest {
            // Given
            val content = "some content"

            // When & Then
            assertFailsWith<IllegalArgumentException> {
                parser.parse(content, SpecificationFormat.OPENAPI_3)
            }
        }

        @Test
        fun `Given non-GRAPHQL format When validating Then should throw IllegalArgumentException`() = runTest {
            // Given
            val content = "some content"

            // When & Then
            assertFailsWith<IllegalArgumentException> {
                parser.validate(content, SpecificationFormat.OPENAPI_3)
            }
        }

        @Test
        fun `Given non-GRAPHQL format When extracting metadata Then should throw IllegalArgumentException`() = runTest {
            // Given
            val content = "some content"

            // When & Then
            assertFailsWith<IllegalArgumentException> {
                parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)
            }
        }
    }
}
