package nl.vintik.mocknest.application.generation.graphql

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.GraphQLSchemaParsingException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLSchemaReducerTest {

    private val reducer = GraphQLSchemaReducer()

    @AfterEach
    fun tearDown() {
        // No mocks to clear in this test
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/graphql/introspection/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    @Nested
    inner class SuccessfulReduction {

        @Test
        fun `Given simple schema When reducing Then should extract queries with arguments and return types`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertEquals(1, result.queries.size)
            val userQuery = result.queries.first()
            assertEquals("user", userQuery.name)
            assertEquals("Get user by ID", userQuery.description)
            assertEquals("User", userQuery.returnType)
            assertEquals(1, userQuery.arguments.size)
            assertEquals("id", userQuery.arguments.first().name)
            assertEquals("ID!", userQuery.arguments.first().type)
            assertEquals("User ID", userQuery.arguments.first().description)
        }

        @Test
        fun `Given simple schema When reducing Then should extract mutations with arguments and return types`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertEquals(1, result.mutations.size)
            val createUserMutation = result.mutations.first()
            assertEquals("createUser", createUserMutation.name)
            assertEquals("Create a new user", createUserMutation.description)
            assertEquals("User", createUserMutation.returnType)
            assertEquals(1, createUserMutation.arguments.size)
            assertEquals("input", createUserMutation.arguments.first().name)
            assertEquals("CreateUserInput!", createUserMutation.arguments.first().type)
            assertEquals("User input data", createUserMutation.arguments.first().description)
        }

        @Test
        fun `Given simple schema When reducing Then should extract output types with fields`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertTrue(result.types.containsKey("User"))
            val userType = result.types["User"]!!
            assertEquals("User", userType.name)
            assertEquals("User type", userType.description)
            assertEquals(4, userType.fields.size)
            
            val fieldNames = userType.fields.map { it.name }
            assertTrue(fieldNames.contains("id"))
            assertTrue(fieldNames.contains("name"))
            assertTrue(fieldNames.contains("email"))
            assertTrue(fieldNames.contains("status"))
            
            val idField = userType.fields.first { it.name == "id" }
            assertEquals("ID!", idField.type)
            
            val nameField = userType.fields.first { it.name == "name" }
            assertEquals("String!", nameField.type)
            
            val emailField = userType.fields.first { it.name == "email" }
            assertEquals("String", emailField.type)
            
            val statusField = userType.fields.first { it.name == "status" }
            assertEquals("UserStatus", statusField.type)
        }

        @Test
        fun `Given simple schema When reducing Then should extract input types with fields`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertTrue(result.types.containsKey("CreateUserInput"))
            val inputType = result.types["CreateUserInput"]!!
            assertEquals("CreateUserInput", inputType.name)
            assertEquals("Input for creating a user", inputType.description)
            assertEquals(2, inputType.fields.size)
            
            val fieldNames = inputType.fields.map { it.name }
            assertTrue(fieldNames.contains("name"))
            assertTrue(fieldNames.contains("email"))
            
            val nameField = inputType.fields.first { it.name == "name" }
            assertEquals("String!", nameField.type)
            
            val emailField = inputType.fields.first { it.name == "email" }
            assertEquals("String", emailField.type)
        }

        @Test
        fun `Given simple schema When reducing Then should extract enum types with values`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertTrue(result.enums.containsKey("UserStatus"))
            val enumType = result.enums["UserStatus"]!!
            assertEquals("UserStatus", enumType.name)
            assertEquals("User status enum", enumType.description)
            assertEquals(3, enumType.values.size)
            assertTrue(enumType.values.contains("ACTIVE"))
            assertTrue(enumType.values.contains("INACTIVE"))
            assertTrue(enumType.values.contains("SUSPENDED"))
        }

        @Test
        fun `Given complex schema When reducing Then should extract all queries`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertEquals(3, result.queries.size)
            val queryNames = result.queries.map { it.name }
            assertTrue(queryNames.contains("product"))
            assertTrue(queryNames.contains("products"))
            assertTrue(queryNames.contains("order"))
            
            // Verify products query with multiple arguments
            val productsQuery = result.queries.first { it.name == "products" }
            assertEquals(2, productsQuery.arguments.size)
            val argNames = productsQuery.arguments.map { it.name }
            assertTrue(argNames.contains("filter"))
            assertTrue(argNames.contains("limit"))
            assertEquals("[Product]", productsQuery.returnType)
        }

        @Test
        fun `Given complex schema When reducing Then should extract all mutations`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertEquals(3, result.mutations.size)
            val mutationNames = result.mutations.map { it.name }
            assertTrue(mutationNames.contains("createProduct"))
            assertTrue(mutationNames.contains("updateProduct"))
            assertTrue(mutationNames.contains("placeOrder"))
            
            // Verify updateProduct mutation with multiple arguments
            val updateMutation = result.mutations.first { it.name == "updateProduct" }
            assertEquals(2, updateMutation.arguments.size)
            val argNames = updateMutation.arguments.map { it.name }
            assertTrue(argNames.contains("id"))
            assertTrue(argNames.contains("input"))
        }

        @Test
        fun `Given complex schema When reducing Then should extract all types`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertTrue(result.types.size > 5)
            assertTrue(result.types.containsKey("Product"))
            assertTrue(result.types.containsKey("Order"))
            assertTrue(result.types.containsKey("OrderItem"))
            assertTrue(result.types.containsKey("ProductFilter"))
            assertTrue(result.types.containsKey("CreateProductInput"))
            assertTrue(result.types.containsKey("UpdateProductInput"))
            assertTrue(result.types.containsKey("PlaceOrderInput"))
            assertTrue(result.types.containsKey("OrderItemInput"))
            
            // Verify nested type structure
            val orderType = result.types["Order"]!!
            val itemsField = orderType.fields.first { it.name == "items" }
            assertEquals("[OrderItem]", itemsField.type)
        }

        @Test
        fun `Given complex schema When reducing Then should extract all enums`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertEquals(3, result.enums.size)
            assertTrue(result.enums.containsKey("ProductCategory"))
            assertTrue(result.enums.containsKey("ProductStatus"))
            assertTrue(result.enums.containsKey("OrderStatus"))
            
            val productCategory = result.enums["ProductCategory"]!!
            assertTrue(productCategory.values.contains("ELECTRONICS"))
            assertTrue(productCategory.values.contains("CLOTHING"))
            assertTrue(productCategory.values.contains("BOOKS"))
            assertTrue(productCategory.values.contains("HOME"))
            
            val orderStatus = result.enums["OrderStatus"]!!
            assertTrue(orderStatus.values.contains("PENDING"))
            assertTrue(orderStatus.values.contains("PROCESSING"))
            assertTrue(orderStatus.values.contains("SHIPPED"))
            assertTrue(orderStatus.values.contains("DELIVERED"))
            assertTrue(orderStatus.values.contains("CANCELLED"))
        }
    }

    @Nested
    inner class MetadataFieldExclusion {

        @Test
        fun `Given schema with introspection types When reducing Then should exclude __Schema type`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertFalse(result.types.containsKey("__Schema"))
        }

        @Test
        fun `Given schema with introspection types When reducing Then should exclude __Type type`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertFalse(result.types.containsKey("__Type"))
        }

        @Test
        fun `Given schema with __typename field When reducing Then should exclude it from types`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            // Verify no type has __typename field
            result.types.values.forEach { type ->
                val fieldNames = type.fields.map { it.name }
                assertFalse(fieldNames.contains("__typename"))
            }
        }

        @Test
        fun `Given schema with built-in scalars When reducing Then should exclude them from types`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertFalse(result.types.containsKey("String"))
            assertFalse(result.types.containsKey("ID"))
            assertFalse(result.types.containsKey("Int"))
            assertFalse(result.types.containsKey("Float"))
            assertFalse(result.types.containsKey("Boolean"))
        }

        @Test
        fun `Given compact schema When pretty printing Then should not contain introspection metadata`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val result = reducer.reduce(introspectionJson)

            // When
            val prettyPrinted = result.prettyPrint()

            // Then
            assertFalse(prettyPrinted.contains("__Schema"))
            assertFalse(prettyPrinted.contains("__Type"))
            assertFalse(prettyPrinted.contains("__typename"))
        }
    }

    @Nested
    inner class SizeReduction {

        @Test
        fun `Given simple schema When reducing Then should achieve at least 40 percent size reduction`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")
            val originalSize = introspectionJson.length

            // When
            val result = reducer.reduce(introspectionJson)
            val compactSize = result.prettyPrint().length
            val reductionPercent = ((originalSize - compactSize).toDouble() / originalSize * 100).toInt()

            // Then
            assertTrue(reductionPercent > 40, "Expected reduction > 40%, got $reductionPercent%")
            assertEquals(result.prettyPrint().length, compactSize)
        }

        @Test
        fun `Given complex schema When reducing Then should achieve at least 40 percent size reduction`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")
            val originalSize = introspectionJson.length

            // When
            val result = reducer.reduce(introspectionJson)
            val compactSize = result.prettyPrint().length
            val reductionPercent = ((originalSize - compactSize).toDouble() / originalSize * 100).toInt()

            // Then
            assertTrue(reductionPercent > 40, "Expected reduction > 40%, got $reductionPercent%")
        }

        @Test
        fun `Given schema When reducing Then compact representation should be significantly smaller`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")
            val originalSize = introspectionJson.length

            // When
            val result = reducer.reduce(introspectionJson)
            val compactSize = result.prettyPrint().length

            // Then
            assertEquals(result.prettyPrint().length, compactSize)
            assertTrue(originalSize > compactSize)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `Given invalid JSON When reducing Then should throw GraphQLSchemaParsingException`() = runTest {
            // Given
            val invalidJson = "{ invalid json }"

            // When & Then
            assertThrows<GraphQLSchemaParsingException> {
                reducer.reduce(invalidJson)
            }
        }

        @Test
        fun `Given JSON without __schema When reducing Then should throw GraphQLSchemaParsingException`() = runTest {
            // Given
            val jsonWithoutSchema = """{"data": {}}"""

            // When & Then
            val exception = assertThrows<GraphQLSchemaParsingException> {
                reducer.reduce(jsonWithoutSchema)
            }
            assertTrue(exception.message!!.contains("missing __schema"))
        }

        @Test
        fun `Given JSON without types When reducing Then should throw GraphQLSchemaParsingException`() = runTest {
            // Given
            val jsonWithoutTypes = """{"data": {"__schema": {}}}"""

            // When & Then
            val exception = assertThrows<GraphQLSchemaParsingException> {
                reducer.reduce(jsonWithoutTypes)
            }
            assertTrue(exception.message!!.contains("missing types"))
        }

        @Test
        fun `Given schema with no queries or mutations When reducing Then should throw IllegalArgumentException`() = runTest {
            // Given - Schema with types array but no query or mutation types defined
            val schemaWithoutOperations = """{
                "data": {
                    "__schema": {
                        "queryType": null,
                        "mutationType": null,
                        "types": [
                            {
                                "kind": "OBJECT",
                                "name": "SomeType",
                                "fields": [
                                    {
                                        "name": "id",
                                        "args": [],
                                        "type": {
                                            "kind": "SCALAR",
                                            "name": "ID",
                                            "ofType": null
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                }
            }"""

            // When & Then
            val exception = assertThrows<IllegalArgumentException> {
                reducer.reduce(schemaWithoutOperations)
            }
            // The exception is thrown by CompactGraphQLSchema's init block
            assertNotNull(exception.message)
            assertTrue(exception.message!!.contains("query") || exception.message!!.contains("mutation"))
        }
    }

    @Nested
    inner class CompleteSchemaReduction {

        @Test
        fun `Given complete schema When reducing Then should produce valid CompactGraphQLSchema`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            assertNotNull(result)
            assertTrue(result.queries.isNotEmpty())
            assertTrue(result.mutations.isNotEmpty())
            assertTrue(result.types.isNotEmpty())
            assertTrue(result.enums.isNotEmpty())
            assertNotNull(result.metadata)
        }

        @Test
        fun `Given complete schema When reducing Then should preserve operation descriptions`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            val userQuery = result.queries.first { it.name == "user" }
            assertEquals("Get user by ID", userQuery.description)
            
            val createUserMutation = result.mutations.first { it.name == "createUser" }
            assertEquals("Create a new user", createUserMutation.description)
        }

        @Test
        fun `Given complete schema When reducing Then should preserve type descriptions`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            val userType = result.types["User"]!!
            assertEquals("User type", userType.description)
            
            val inputType = result.types["CreateUserInput"]!!
            assertEquals("Input for creating a user", inputType.description)
            
            val enumType = result.enums["UserStatus"]!!
            assertEquals("User status enum", enumType.description)
        }

        @Test
        fun `Given complete schema When reducing Then should handle NON_NULL type wrappers`() = runTest {
            // Given
            val introspectionJson = loadTestData("simple-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            val userQuery = result.queries.first { it.name == "user" }
            assertEquals("ID!", userQuery.arguments.first().type)
            
            val userType = result.types["User"]!!
            val idField = userType.fields.first { it.name == "id" }
            assertEquals("ID!", idField.type)
        }

        @Test
        fun `Given complete schema When reducing Then should handle LIST type wrappers`() = runTest {
            // Given
            val introspectionJson = loadTestData("complex-schema.json")

            // When
            val result = reducer.reduce(introspectionJson)

            // Then
            val productsQuery = result.queries.first { it.name == "products" }
            assertEquals("[Product]", productsQuery.returnType)
            
            val orderType = result.types["Order"]!!
            val itemsField = orderType.fields.first { it.name == "items" }
            assertEquals("[OrderItem]", itemsField.type)
        }
    }
}
