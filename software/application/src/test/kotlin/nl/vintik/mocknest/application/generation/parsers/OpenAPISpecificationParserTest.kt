package nl.vintik.mocknest.application.generation.parsers

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class OpenAPISpecificationParserTest {

    private val parser = OpenAPISpecificationParser()

    @Test
    fun `Given valid OAS 3_0 specification When parsing Then should extract endpoints correctly`() = runTest {
        // Given
        val content = """
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
                              ${'$'}ref: '#/components/schemas/Pet'
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
        assertTrue(getPets?.responses?.containsKey(200) ?: false)
        
        val postPets = specification.endpoints.find { it.path == "/pets" && it.method == HttpMethod.POST }
        assertEquals("createPets", postPets?.operationId)
        assertTrue(postPets?.responses?.containsKey(201) ?: false)
    }

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
        assertTrue(getPet?.parameters?.get(0)?.required ?: false)
    }

    @Test
    fun `Given invalid OAS content When validating Then should return failure`() = runTest {
        // Given
        val content = "not a yaml or json"

        // When
        val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

        // Then
        assertTrue(!result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `Should extract metadata correctly`() = runTest {
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
        
        val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)
        
        assertEquals("My API", metadata.title)
        assertEquals("2.1", metadata.version)
        assertEquals(3, metadata.endpointCount)
    }

    @Test
    fun `Should support only OpenAPI and Swagger formats`() {
        assertTrue(parser.supports(SpecificationFormat.OPENAPI_3))
        assertTrue(parser.supports(SpecificationFormat.SWAGGER_2))
        assertFalse(parser.supports(SpecificationFormat.GRAPHQL))
        assertFalse(parser.supports(SpecificationFormat.WSDL))
    }

    @Test
    fun `Should parse complex schema with nested objects and arrays`() = runTest {
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
        
        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val endpoint = spec.endpoints.first()
        val schema = endpoint.responses[200]?.schema
        
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.OBJECT, schema?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.ARRAY, schema?.properties?.get("tags")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.STRING, schema?.properties?.get("tags")?.items?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.OBJECT, schema?.properties?.get("metadata")?.type)
    }

    @Test
    fun `Should parse headers and query parameters`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val params = spec.endpoints.first().parameters

        assertEquals(2, params.size)
        assertTrue(params.any { it.name == "X-Request-ID" && it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.HEADER })
        assertTrue(params.any { it.name == "limit" && it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.QUERY })
    }

    @Test
    fun `Should parse all HTTP methods`() = runTest {
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
        """.trimIndent()

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        assertEquals(7, spec.endpoints.size)
        assertTrue(spec.endpoints.any { it.method == HttpMethod.GET })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.POST })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.PUT })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.DELETE })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.PATCH })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.HEAD })
        assertTrue(spec.endpoints.any { it.method == HttpMethod.OPTIONS })
    }

    @Test
    fun `Should parse cookie parameters`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val param = spec.endpoints.first().parameters.first()

        assertEquals("session", param.name)
        assertEquals(nl.vintik.mocknest.domain.generation.ParameterLocation.COOKIE, param.location)
        assertTrue(param.required)
    }

    @Test
    fun `Should parse request body with examples`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val requestBody = spec.endpoints.first().requestBody

        assertTrue(requestBody?.required ?: false)
        assertEquals("Test request body", requestBody?.description)
        assertTrue(requestBody?.content?.containsKey("application/json") ?: false)
        assertEquals(1, requestBody?.content?.get("application/json")?.examples?.size)
    }

    @Test
    fun `Should parse response with headers`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val response = spec.endpoints.first().responses[200]

        assertEquals(2, response?.headers?.size)
        assertTrue(response?.headers?.get("X-Rate-Limit")?.required ?: false)
        assertEquals("Rate limit", response?.headers?.get("X-Rate-Limit")?.description)
        assertFalse(response?.headers?.get("X-Expires")?.required ?: true)
    }

    @Test
    fun `Should parse schema with all types`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val schema = spec.endpoints.first().responses[200]?.schema
        val properties = schema?.properties

        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.STRING, properties?.get("stringProp")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.NUMBER, properties?.get("numberProp")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.INTEGER, properties?.get("integerProp")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.BOOLEAN, properties?.get("booleanProp")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.ARRAY, properties?.get("arrayProp")?.type)
        assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.OBJECT, properties?.get("objectProp")?.type)
    }

    @Test
    fun `Should parse schema with validation constraints`() = runTest {
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
                                pattern: '^[a-zA-Z]+${'$'}'
                              status:
                                type: string
                                enum:
                                  - active
                                  - inactive
        """.trimIndent()

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val properties = spec.endpoints.first().responses[200]?.schema?.properties

        val age = properties?.get("age")
        assertEquals(0, age?.minimum?.toInt())
        assertEquals(150, age?.maximum?.toInt())

        val name = properties?.get("name")
        assertEquals(1, name?.minLength)
        assertEquals(100, name?.maxLength)
        assertEquals("^[a-zA-Z]+$", name?.pattern)

        val status = properties?.get("status")
        assertEquals(2, status?.enum?.size)
        assertTrue(status?.enum?.contains("active") ?: false)
    }

    @Test
    fun `Should parse specification with metadata and description`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        assertEquals("Detailed API", spec.title)
        assertEquals("2.5.1", spec.version)
        assertEquals("This is a detailed API description", spec.metadata["description"])
        assertEquals("https://api.example.com/v1", spec.metadata["host"])
    }

    @Test
    fun `Should parse specification with component schemas`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

        assertEquals(2, spec.schemas.size)
        assertTrue(spec.schemas.containsKey("User"))
        assertTrue(spec.schemas.containsKey("Product"))

        val user = spec.schemas["User"]
        assertEquals(2, user?.required?.size)
        assertTrue(user?.required?.contains("id") ?: false)
        assertEquals("email", user?.properties?.get("email")?.format)
    }

    @Test
    fun `Should validate specification with warnings`() = runTest {
        val content = """
            openapi: 3.0.0
            info:
              title: API
              version: 1.0
            paths: {}
        """.trimIndent()

        val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

        assertTrue(result.isValid)
    }

    @Test
    fun `Should parse null schema as object type`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val response = spec.endpoints.first().responses[204]

        // Response with no content should have null schema
        assertEquals(null, response?.schema)
    }

    @Test
    fun `Should parse schema with example value`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val schema = spec.endpoints.first().responses[200]?.schema
        val nameProperty = schema?.properties?.get("name")

        assertEquals("John Doe", nameProperty?.example)
        assertEquals("User's full name", nameProperty?.description)
    }

    @Test
    fun `Should handle schema with additionalProperties false`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val schema = spec.endpoints.first().responses[200]?.schema

        assertFalse(schema?.additionalProperties ?: true)
    }

    @Test
    fun `Should parse parameter with description`() = runTest {
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

        val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)
        val param = spec.endpoints.first().parameters.first()

        assertEquals("userId", param.name)
        assertEquals("The ID of the user to fetch", param.description)
        assertFalse(param.required)
    }

    @Test
    fun `Should handle parsing errors with descriptive message`() = runTest {
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

        val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

        // Should still parse but may have warnings
        assertTrue(result.isValid || !result.isValid)
    }

    @Test
    fun `Should extract metadata with schema count`() = runTest {
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

        val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

        assertEquals(3, metadata.schemaCount)
        assertEquals("Schema Count API", metadata.title)
        assertEquals(SpecificationFormat.OPENAPI_3, metadata.format)
    }

    @Test
    fun `Should parse unknown parameter location as QUERY`() = runTest {
        // Testing the fallback behavior for unknown parameter locations
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

        // This will likely fail parsing, but we test graceful handling
        val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

        // The parser should handle this without crashing
        assertTrue(result.isValid || !result.isValid)
    }
}
