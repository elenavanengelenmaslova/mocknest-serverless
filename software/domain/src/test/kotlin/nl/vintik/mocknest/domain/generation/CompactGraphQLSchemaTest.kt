package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompactGraphQLSchemaTest {

    @Nested
    inner class CompactGraphQLSchemaValidation {

        @Test
        fun `Given valid schema with queries When creating Then should succeed`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )
            assertNotNull(schema)
            assertEquals(1, schema.queries.size)
        }

        @Test
        fun `Given valid schema with mutations When creating Then should succeed`() {
            val schema = CompactGraphQLSchema(
                queries = emptyList(),
                mutations = listOf(mockMutation()),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )
            assertNotNull(schema)
            assertEquals(1, schema.mutations.size)
        }

        @Test
        fun `Given schema with both queries and mutations When creating Then should succeed`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = listOf(mockMutation()),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )
            assertNotNull(schema)
            assertEquals(1, schema.queries.size)
            assertEquals(1, schema.mutations.size)
        }

        @Test
        fun `Given schema with no queries or mutations When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                CompactGraphQLSchema(
                    queries = emptyList(),
                    mutations = emptyList(),
                    types = emptyMap(),
                    enums = emptyMap(),
                    metadata = GraphQLSchemaMetadata()
                )
            }
        }
    }

    @Nested
    inner class GraphQLOperationValidation {

        @Test
        fun `Given valid operation When creating Then should succeed`() {
            val operation = GraphQLOperation(
                name = "getUser",
                arguments = emptyList(),
                returnType = "User"
            )
            assertNotNull(operation)
            assertEquals("getUser", operation.name)
            assertEquals("User", operation.returnType)
        }

        @Test
        fun `Given operation with blank name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLOperation(
                    name = "",
                    arguments = emptyList(),
                    returnType = "User"
                )
            }
        }

        @Test
        fun `Given operation with whitespace name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLOperation(
                    name = "   ",
                    arguments = emptyList(),
                    returnType = "User"
                )
            }
        }

        @Test
        fun `Given operation with blank return type When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLOperation(
                    name = "getUser",
                    arguments = emptyList(),
                    returnType = ""
                )
            }
        }

        @Test
        fun `Given operation with whitespace return type When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLOperation(
                    name = "getUser",
                    arguments = emptyList(),
                    returnType = "  "
                )
            }
        }
    }

    @Nested
    inner class GraphQLArgumentValidation {

        @Test
        fun `Given valid argument When creating Then should succeed`() {
            val argument = GraphQLArgument(
                name = "id",
                type = "ID!"
            )
            assertNotNull(argument)
            assertEquals("id", argument.name)
            assertEquals("ID!", argument.type)
        }

        @Test
        fun `Given argument with blank name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLArgument(
                    name = "",
                    type = "ID!"
                )
            }
        }

        @Test
        fun `Given argument with blank type When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLArgument(
                    name = "id",
                    type = ""
                )
            }
        }
    }

    @Nested
    inner class GraphQLTypeValidation {

        @Test
        fun `Given valid type with fields When creating Then should succeed`() {
            val type = GraphQLType(
                name = "User",
                fields = listOf(
                    GraphQLField("id", "ID!"),
                    GraphQLField("name", "String!")
                )
            )
            assertNotNull(type)
            assertEquals("User", type.name)
            assertEquals(2, type.fields.size)
        }

        @Test
        fun `Given type with blank name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLType(
                    name = "",
                    fields = listOf(GraphQLField("id", "ID!"))
                )
            }
        }

        @Test
        fun `Given type with empty fields When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLType(
                    name = "User",
                    fields = emptyList()
                )
            }
        }
    }

    @Nested
    inner class GraphQLFieldValidation {

        @Test
        fun `Given valid field When creating Then should succeed`() {
            val field = GraphQLField(
                name = "id",
                type = "ID!"
            )
            assertNotNull(field)
            assertEquals("id", field.name)
            assertEquals("ID!", field.type)
        }

        @Test
        fun `Given field with blank name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLField(
                    name = "",
                    type = "ID!"
                )
            }
        }

        @Test
        fun `Given field with blank type When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLField(
                    name = "id",
                    type = ""
                )
            }
        }
    }

    @Nested
    inner class GraphQLEnumValidation {

        @Test
        fun `Given valid enum with values When creating Then should succeed`() {
            val enum = GraphQLEnum(
                name = "Status",
                values = listOf("ACTIVE", "INACTIVE")
            )
            assertNotNull(enum)
            assertEquals("Status", enum.name)
            assertEquals(2, enum.values.size)
        }

        @Test
        fun `Given enum with blank name When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLEnum(
                    name = "",
                    values = listOf("ACTIVE")
                )
            }
        }

        @Test
        fun `Given enum with empty values When creating Then should fail`() {
            assertThrows<IllegalArgumentException> {
                GraphQLEnum(
                    name = "Status",
                    values = emptyList()
                )
            }
        }
    }

    @Nested
    inner class PrettyPrintFormat {

        @Test
        fun `Given schema with queries only When pretty printing Then should format correctly`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(
                    GraphQLOperation(
                        name = "getUser",
                        arguments = listOf(GraphQLArgument("id", "ID!")),
                        returnType = "User"
                    )
                ),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("schema {"))
            assertTrue(output.contains("query: Query"))
            assertTrue(output.contains("type Query {"))
            assertTrue(output.contains("getUser(id: ID!): User"))
        }

        @Test
        fun `Given schema with mutations only When pretty printing Then should format correctly`() {
            val schema = CompactGraphQLSchema(
                queries = emptyList(),
                mutations = listOf(
                    GraphQLOperation(
                        name = "createUser",
                        arguments = listOf(GraphQLArgument("name", "String!")),
                        returnType = "User"
                    )
                ),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("schema {"))
            assertTrue(output.contains("mutation: Mutation"))
            assertTrue(output.contains("type Mutation {"))
            assertTrue(output.contains("createUser(name: String!): User"))
        }

        @Test
        fun `Given schema with queries and mutations When pretty printing Then should format both`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = listOf(mockMutation()),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("query: Query"))
            assertTrue(output.contains("mutation: Mutation"))
            assertTrue(output.contains("type Query {"))
            assertTrue(output.contains("type Mutation {"))
        }

        @Test
        fun `Given schema with types When pretty printing Then should format types`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = emptyList(),
                types = mapOf(
                    "User" to GraphQLType(
                        name = "User",
                        fields = listOf(
                            GraphQLField("id", "ID!"),
                            GraphQLField("name", "String!")
                        )
                    )
                ),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("type User {"))
            assertTrue(output.contains("id: ID!"))
            assertTrue(output.contains("name: String!"))
        }

        @Test
        fun `Given schema with enums When pretty printing Then should format enums`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = emptyList(),
                types = emptyMap(),
                enums = mapOf(
                    "Status" to GraphQLEnum(
                        name = "Status",
                        values = listOf("ACTIVE", "INACTIVE")
                    )
                ),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("enum Status {"))
            assertTrue(output.contains("ACTIVE"))
            assertTrue(output.contains("INACTIVE"))
        }

        @Test
        fun `Given operation with no arguments When pretty printing Then should format without parentheses`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(
                    GraphQLOperation(
                        name = "getAllUsers",
                        arguments = emptyList(),
                        returnType = "[User!]!"
                    )
                ),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("getAllUsers: [User!]!"))
        }

        @Test
        fun `Given operation with multiple arguments When pretty printing Then should format with comma separation`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(
                    GraphQLOperation(
                        name = "searchUsers",
                        arguments = listOf(
                            GraphQLArgument("name", "String"),
                            GraphQLArgument("age", "Int"),
                            GraphQLArgument("status", "Status")
                        ),
                        returnType = "[User!]!"
                    )
                ),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            assertTrue(output.contains("searchUsers(name: String, age: Int, status: Status): [User!]!"))
        }

        @Test
        fun `Given complete schema When pretty printing Then should format all sections`() {
            val schema = CompactGraphQLSchema(
                queries = listOf(
                    GraphQLOperation("getUser", listOf(GraphQLArgument("id", "ID!")), "User")
                ),
                mutations = listOf(
                    GraphQLOperation("createUser", listOf(GraphQLArgument("name", "String!")), "User")
                ),
                types = mapOf(
                    "User" to GraphQLType(
                        "User",
                        listOf(
                            GraphQLField("id", "ID!"),
                            GraphQLField("name", "String!"),
                            GraphQLField("status", "Status!")
                        )
                    )
                ),
                enums = mapOf(
                    "Status" to GraphQLEnum("Status", listOf("ACTIVE", "INACTIVE"))
                ),
                metadata = GraphQLSchemaMetadata()
            )

            val output = schema.prettyPrint()

            // Verify all sections are present
            assertTrue(output.contains("schema {"))
            assertTrue(output.contains("query: Query"))
            assertTrue(output.contains("mutation: Mutation"))
            assertTrue(output.contains("type Query {"))
            assertTrue(output.contains("type Mutation {"))
            assertTrue(output.contains("type User {"))
            assertTrue(output.contains("enum Status {"))
        }
    }

    @Nested
    inner class DataClassEquality {

        @Test
        fun `Given two identical schemas When comparing Then should be equal`() {
            val schema1 = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )
            val schema2 = CompactGraphQLSchema(
                queries = listOf(mockQuery()),
                mutations = emptyList(),
                types = emptyMap(),
                enums = emptyMap(),
                metadata = GraphQLSchemaMetadata()
            )
            assertEquals(schema1, schema2)
        }

        @Test
        fun `Given two identical operations When comparing Then should be equal`() {
            val op1 = GraphQLOperation("getUser", emptyList(), "User")
            val op2 = GraphQLOperation("getUser", emptyList(), "User")
            assertEquals(op1, op2)
        }

        @Test
        fun `Given two identical arguments When comparing Then should be equal`() {
            val arg1 = GraphQLArgument("id", "ID!")
            val arg2 = GraphQLArgument("id", "ID!")
            assertEquals(arg1, arg2)
        }

        @Test
        fun `Given two identical types When comparing Then should be equal`() {
            val type1 = GraphQLType("User", listOf(GraphQLField("id", "ID!")))
            val type2 = GraphQLType("User", listOf(GraphQLField("id", "ID!")))
            assertEquals(type1, type2)
        }

        @Test
        fun `Given two identical fields When comparing Then should be equal`() {
            val field1 = GraphQLField("id", "ID!")
            val field2 = GraphQLField("id", "ID!")
            assertEquals(field1, field2)
        }

        @Test
        fun `Given two identical enums When comparing Then should be equal`() {
            val enum1 = GraphQLEnum("Status", listOf("ACTIVE", "INACTIVE"))
            val enum2 = GraphQLEnum("Status", listOf("ACTIVE", "INACTIVE"))
            assertEquals(enum1, enum2)
        }

        @Test
        fun `Given two identical metadata When comparing Then should be equal`() {
            val meta1 = GraphQLSchemaMetadata("1.0.0", "Test schema")
            val meta2 = GraphQLSchemaMetadata("1.0.0", "Test schema")
            assertEquals(meta1, meta2)
        }
    }

    @Nested
    inner class DataClassImmutability {

        @Test
        fun `Given schema When accessing properties Then should return same values`() {
            val queries = listOf(mockQuery())
            val mutations = listOf(mockMutation())
            val types = mapOf("User" to mockType())
            val enums = mapOf("Status" to mockEnum())
            val metadata = GraphQLSchemaMetadata()

            val schema = CompactGraphQLSchema(queries, mutations, types, enums, metadata)

            assertEquals(queries, schema.queries)
            assertEquals(mutations, schema.mutations)
            assertEquals(types, schema.types)
            assertEquals(enums, schema.enums)
            assertEquals(metadata, schema.metadata)
        }

        @Test
        fun `Given operation When accessing properties Then should return same values`() {
            val name = "getUser"
            val arguments = listOf(GraphQLArgument("id", "ID!"))
            val returnType = "User"
            val description = "Get user by ID"

            val operation = GraphQLOperation(name, arguments, returnType, description)

            assertEquals(name, operation.name)
            assertEquals(arguments, operation.arguments)
            assertEquals(returnType, operation.returnType)
            assertEquals(description, operation.description)
        }
    }

    // Helper methods
    private fun mockQuery() = GraphQLOperation(
        name = "getUser",
        arguments = listOf(GraphQLArgument("id", "ID!")),
        returnType = "User"
    )

    private fun mockMutation() = GraphQLOperation(
        name = "createUser",
        arguments = listOf(GraphQLArgument("name", "String!")),
        returnType = "User"
    )

    private fun mockType() = GraphQLType(
        name = "User",
        fields = listOf(GraphQLField("id", "ID!"))
    )

    private fun mockEnum() = GraphQLEnum(
        name = "Status",
        values = listOf("ACTIVE", "INACTIVE")
    )
}
