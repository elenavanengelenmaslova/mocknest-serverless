package nl.vintik.mocknest.application.generation.parsers

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.JsonSchemaType
import nl.vintik.mocknest.domain.generation.ParameterLocation
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAPISpecificationParserTest {

    private val parser = OpenAPISpecificationParser()

    @Nested
    inner class EndpointParsing {

        @Test
        fun `Given valid OAS 3_0 specification When parsing Then should extract endpoints correctly`() = runTest {
            // Given
            val content = $$"""
                openapi: 3.0.0
                info:
                  title: Pet Store API
                  version: 1.0.0
                paths:
                  /pets:
                    get:
                      summary: List all pets
                      operationId: listPets
                      responses:
                        '200':
                          description: A paged array of pets
                          content:
                            application/json:
                              schema:
                                type: array
                                items:
                                  $ref: '#/components/schemas/Pet'
                    post:
                      summary: Create a pet
                      operationId: createPets
                      responses:
                        '201':
                          description: Null response
                components:
                  schemas:
                    Pet:
                      required:
                        - id
                        - name
                      properties:
                        id:
                          type: integer
                          format: int64
                        name:
                          type: string
                        tag:
                          type: string
            """.trimIndent()

            // When
            val specification = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals("Pet Store API", specification.title)
            assertEquals("1.0.0", specification.version)
            assertEquals(2, specification.endpoints.size)

            val getPets = specification.endpoints.find { it.path == "/pets" && it.method == HttpMethod.GET }
            assertEquals("listPets", getPets?.operationId)
            assertEquals("List all pets", getPets?.summary)
            assertEquals(true, getPets?.responses?.containsKey(200))

            val postPets = specification.endpoints.find { it.path == "/pets" && it.method == HttpMethod.POST }
            assertEquals("createPets", postPets?.operationId)
            assertEquals(true, postPets?.responses?.containsKey(201))
        }

        @Test
        fun `Given OAS 3_0 with all HTTP methods When parsing Then should extract all methods`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Methods API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200': { description: OK }
                    post:
                      responses:
                        '201': { description: Created }
                    put:
                      responses:
                        '200': { description: Updated }
                    delete:
                      responses:
                        '204': { description: Deleted }
                    patch:
                      responses:
                        '200': { description: Patched }
                    head:
                      responses:
                        '200': { description: Head }
                    options:
                      responses:
                        '200': { description: Options }
                    trace:
                      responses:
                        '200': { description: Trace }
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals(8, spec.endpoints.size)
            assertTrue(spec.endpoints.any { it.method == HttpMethod.GET })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.POST })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.PUT })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.DELETE })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.PATCH })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.HEAD })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.OPTIONS })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.TRACE })
        }

        @Test
        fun `Given spec with no-content response When parsing Then should have null schema`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Null Schema API
                  version: 1.0
                paths:
                  /test:
                    post:
                      responses:
                        '204':
                          description: No content
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            val response = spec.endpoints.first().responses[204]
            assertEquals(null, response?.schema)
        }
    }

    @Nested
    inner class ParameterParsing {

        @Test
        fun `Given OAS 3_0 with path parameters When parsing Then should extract parameters correctly`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Pet Store API
                  version: 1.0.0
                paths:
                  /pets/{petId}:
                    get:
                      summary: Info for a specific pet
                      operationId: showPetById
                      parameters:
                        - name: petId
                          in: path
                          required: true
                          description: The id of the pet to retrieve
                          schema:
                            type: string
                      responses:
                        '200':
                          description: Expected response to a valid request
            """.trimIndent()

            // When
            val specification = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            val getPet = specification.endpoints.find { it.path == "/pets/{petId}" }
            assertEquals(1, getPet?.parameters?.size)
            assertEquals("petId", getPet?.parameters?.get(0)?.name)
            assertEquals("PATH", getPet?.parameters?.get(0)?.location?.name)
            assertEquals(true, getPet?.parameters?.get(0)?.required)
        }

        @Test
        fun `Given spec with header and query parameters When parsing Then should extract both`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Params API
                  version: 1.0
                paths:
                  /test:
                    get:
                      parameters:
                        - name: X-Request-ID
                          in: header
                          required: true
                          schema:
                            type: string
                        - name: limit
                          in: query
                          schema:
                            type: integer
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val params = spec.endpoints.first().parameters

            // Then
            assertEquals(2, params.size)
            assertTrue(params.any { it.name == "X-Request-ID" && it.location == ParameterLocation.HEADER })
            assertTrue(params.any { it.name == "limit" && it.location == ParameterLocation.QUERY })
        }

        @Test
        fun `Given spec with cookie parameter When parsing Then should extract cookie location`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Cookie API
                  version: 1.0
                paths:
                  /test:
                    get:
                      parameters:
                        - name: session
                          in: cookie
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val param = spec.endpoints.first().parameters.first()

            // Then
            assertEquals("session", param.name)
            assertEquals(ParameterLocation.COOKIE, param.location)
            assertTrue(param.required)
        }

        @Test
        fun `Given spec with parameter description When parsing Then should extract description`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Param Description API
                  version: 1.0
                paths:
                  /test:
                    get:
                      parameters:
                        - name: userId
                          in: query
                          required: false
                          description: The ID of the user to fetch
                          schema:
                            type: string
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val param = spec.endpoints.first().parameters.first()

            // Then
            assertEquals("userId", param.name)
            assertEquals("The ID of the user to fetch", param.description)
            assertFalse(param.required)
        }
    }

    @Nested
    inner class SchemaParsing {

        @Test
        fun `Given spec with nested objects and arrays When parsing Then should extract schema types`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Complex API
                  version: 1.0
                paths:
                  /test:
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  tags:
                                    type: array
                                    items:
                                      type: string
                                  metadata:
                                    type: object
                                    additionalProperties: true
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val schema = spec.endpoints.first().responses[200]?.schema

            // Then
            assertEquals(JsonSchemaType.OBJECT, schema?.type)
            assertEquals(JsonSchemaType.ARRAY, schema?.properties?.get("tags")?.type)
            assertEquals(JsonSchemaType.STRING, schema?.properties?.get("tags")?.items?.type)
            assertEquals(JsonSchemaType.OBJECT, schema?.properties?.get("metadata")?.type)
        }

        @Test
        fun `Given spec with all schema types When parsing Then should map types correctly`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Schema Types API
                  version: 1.0
                paths:
                  /test:
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  stringProp:
                                    type: string
                                  numberProp:
                                    type: number
                                  integerProp:
                                    type: integer
                                  booleanProp:
                                    type: boolean
                                  arrayProp:
                                    type: array
                                    items:
                                      type: string
                                  objectProp:
                                    type: object
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val properties = spec.endpoints.first().responses[200]?.schema?.properties

            // Then
            assertEquals(JsonSchemaType.STRING, properties?.get("stringProp")?.type)
            assertEquals(JsonSchemaType.NUMBER, properties?.get("numberProp")?.type)
            assertEquals(JsonSchemaType.INTEGER, properties?.get("integerProp")?.type)
            assertEquals(JsonSchemaType.BOOLEAN, properties?.get("booleanProp")?.type)
            assertEquals(JsonSchemaType.ARRAY, properties?.get("arrayProp")?.type)
            assertEquals(JsonSchemaType.OBJECT, properties?.get("objectProp")?.type)
        }

        @Test
        fun `Given spec with validation constraints When parsing Then should extract constraints`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Validation API
                  version: 1.0
                paths:
                  /test:
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  age:
                                    type: integer
                                    minimum: 0
                                    maximum: 150
                                  name:
                                    type: string
                                    minLength: 1
                                    maxLength: 100
                                    pattern: '^[a-zA-Z]+$'
                                  status:
                                    type: string
                                    enum:
                                      - active
                                      - inactive
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val properties = spec.endpoints.first().responses[200]?.schema?.properties

            // Then
            val age = properties?.get("age")
            assertEquals(0, age?.minimum?.toInt())
            assertEquals(150, age?.maximum?.toInt())

            val name = properties?.get("name")
            assertEquals(1, name?.minLength)
            assertEquals(100, name?.maxLength)
            assertEquals("^[a-zA-Z]+$", name?.pattern)

            val status = properties?.get("status")
            assertEquals(2, status?.enum?.size)
            assertEquals(true, status?.enum?.contains("active"))
        }

        @Test
        fun `Given spec with example value When parsing Then should extract example`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Example API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  name:
                                    type: string
                                    example: John Doe
                                    description: User's full name
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val nameProperty = spec.endpoints.first().responses[200]?.schema?.properties?.get("name")

            // Then
            assertEquals("John Doe", nameProperty?.example)
            assertEquals("User's full name", nameProperty?.description)
        }

        @Test
        fun `Given spec with additionalProperties false When parsing Then should extract flag`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Strict Schema API
                  version: 1.0
                paths:
                  /test:
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                additionalProperties: false
                                properties:
                                  id:
                                    type: integer
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val schema = spec.endpoints.first().responses[200]?.schema

            // Then
            assertEquals(false, schema?.additionalProperties)
        }

        @Test
        fun `Given spec with component schemas When parsing Then should extract schemas`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Schemas API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                components:
                  schemas:
                    User:
                      type: object
                      required:
                        - id
                        - email
                      properties:
                        id:
                          type: integer
                        email:
                          type: string
                          format: email
                    Product:
                      type: object
                      properties:
                        name:
                          type: string
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals(2, spec.schemas.size)
            assertTrue(spec.schemas.containsKey("User"))
            assertTrue(spec.schemas.containsKey("Product"))

            val user = spec.schemas["User"]
            assertEquals(2, user?.required?.size)
            assertEquals(true, user?.required?.contains("id"))
            assertEquals("email", user?.properties?.get("email")?.format)
        }
    }

    @Nested
    inner class RequestBodyParsing {

        @Test
        fun `Given spec with request body and examples When parsing Then should extract body`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Request Body API
                  version: 1.0
                paths:
                  /test:
                    post:
                      requestBody:
                        required: true
                        description: Test request body
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                name:
                                  type: string
                            examples:
                              example1:
                                value:
                                  name: John
                      responses:
                        '201':
                          description: Created
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val requestBody = spec.endpoints.first().requestBody

            // Then
            assertEquals(true, requestBody?.required)
            assertEquals("Test request body", requestBody?.description)
            assertEquals(true, requestBody?.content?.containsKey("application/json"))
            assertEquals(1, requestBody?.content?.get("application/json")?.examples?.size)
        }
    }

    @Nested
    inner class ResponseParsing {

        @Test
        fun `Given spec with response headers When parsing Then should extract headers`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Response Headers API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                          headers:
                            X-Rate-Limit:
                              required: true
                              description: Rate limit
                              schema:
                                type: integer
                            X-Expires:
                              required: false
                              schema:
                                type: string
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
            val response = spec.endpoints.first().responses[200]

            // Then
            assertEquals(2, response?.headers?.size)
            assertEquals(true, response?.headers?.get("X-Rate-Limit")?.required)
            assertEquals("Rate limit", response?.headers?.get("X-Rate-Limit")?.description)
            assertEquals(false, response?.headers?.get("X-Expires")?.required)
        }
    }

    @Nested
    inner class MetadataExtraction {

        @Test
        fun `Given spec with metadata and description When parsing Then should extract metadata`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Detailed API
                  version: 2.5.1
                  description: This is a detailed API description
                servers:
                  - url: https://api.example.com/v1
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals("Detailed API", spec.title)
            assertEquals("2.5.1", spec.version)
            assertEquals("This is a detailed API description", spec.metadata["description"])
            assertEquals("https://api.example.com/v1", spec.metadata["host"])
        }

        @Test
        fun `Given spec with endpoints When extracting metadata Then should count endpoints`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: My API
                  version: 2.1
                paths:
                  /p1:
                    get: {}
                    post: {}
                  /p2:
                    put: {}
            """.trimIndent()

            // When
            val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals("My API", metadata.title)
            assertEquals("2.1", metadata.version)
            assertEquals(3, metadata.endpointCount)
        }

        @Test
        fun `Given spec with component schemas When extracting metadata Then should count schemas`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Schema Count API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                components:
                  schemas:
                    User:
                      type: object
                    Product:
                      type: object
                    Order:
                      type: object
            """.trimIndent()

            // When
            val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals(3, metadata.schemaCount)
            assertEquals("Schema Count API", metadata.title)
            assertEquals(SpecificationFormat.OPENAPI_3, metadata.format)
        }
    }

    @Nested
    inner class FormatSupport {

        @Test
        fun `Given format check When querying supported formats Then should support only OpenAPI and Swagger`() {
            // Then
            assertTrue(parser.supports(SpecificationFormat.OPENAPI_3))
            assertTrue(parser.supports(SpecificationFormat.SWAGGER_2))
            assertFalse(parser.supports(SpecificationFormat.GRAPHQL))
            assertFalse(parser.supports(SpecificationFormat.WSDL))
        }
    }

    @Nested
    inner class ValidationAndParsingConsistency {

        @Test
        fun `Given invalid content When validating Then should return failure`() = runTest {
            // Given
            val content = "not a yaml or json"

            // When
            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        fun `Given spec with empty paths When validating Then should be valid`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: API
                  version: 1.0
                paths: {}
            """.trimIndent()

            // When
            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertTrue(result.isValid)
        }

        @Test
        fun `Given spec with invalid status code When validating Then should handle gracefully`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Invalid API
                paths:
                  /test:
                    get:
                      responses:
                        invalid-status-code:
                          description: Bad
            """.trimIndent()

            // When
            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then - should handle without crashing
            assertTrue(result.isValid)
        }

        @Test
        fun `Given spec with unknown parameter location When validating Then should handle gracefully`() = runTest {
            // Given
            val content = """
                openapi: 3.0.0
                info:
                  title: Unknown Param API
                  version: 1.0
                paths:
                  /test:
                    get:
                      parameters:
                        - name: test
                          in: unknown
                          schema:
                            type: string
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            // When
            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then - should handle without crashing
            assertTrue(result.isValid)
        }

        @Test
        fun `Given spec with undefined ref When validating Then should be valid with warnings`() = runTest {
            // Given
            val content = $$"""
                openapi: 3.0.0
                info:
                  title: Test API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/NonExistent'
            """.trimIndent()

            // When
            val validateResult = parser.validate(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertTrue(validateResult.isValid)
        }

        @Test
        fun `Given spec with undefined ref When parsing Then should succeed like validate`() = runTest {
            // Given
            val content = $$"""
                openapi: 3.0.0
                info:
                  title: Test API
                  version: 1.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/NonExistent'
            """.trimIndent()

            // When
            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            // Then
            assertEquals(1, spec.endpoints.size)
            assertEquals("/test", spec.endpoints.first().path)
        }
    }
}
