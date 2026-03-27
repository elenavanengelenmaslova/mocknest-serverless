package nl.vintik.mocknest.application.generation.parsers

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

/**
 * Non-regression tests for REST/OpenAPI generation.
 *
 * Verifies that existing REST mock generation behavior is preserved after
 * GraphQL introspection support was added. These tests ensure no breaking
 * changes were introduced to the REST flow.
 *
 * Requirements: 7.4, 9.7
 */
@Tag("graphql-introspection-ai-generation")
class RESTGenerationNonRegressionTest {

    private val parser = OpenAPISpecificationParser()

    // ─────────────────────────────────────────────────────────────────────────
    // Format Support Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class FormatSupportNonRegression {

        @Test
        fun `Given OpenAPI 3 format When checking support Then parser still supports it`() {
            assertTrue(parser.supports(SpecificationFormat.OPENAPI_3))
        }

        @Test
        fun `Given Swagger 2 format When checking support Then parser still supports it`() {
            assertTrue(parser.supports(SpecificationFormat.SWAGGER_2))
        }

        @Test
        fun `Given GraphQL format When checking support Then REST parser does not support it`() {
            assertFalse(parser.supports(SpecificationFormat.GRAPHQL))
        }

        @Test
        fun `Given WSDL format When checking support Then REST parser does not support it`() {
            assertFalse(parser.supports(SpecificationFormat.WSDL))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Endpoint Extraction Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class EndpointExtractionNonRegression {

        @Test
        fun `Given OpenAPI 3 spec with GET and POST When parsing Then both endpoints are extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Pet Store API
                  version: 1.0.0
                paths:
                  /pets:
                    get:
                      operationId: listPets
                      summary: List all pets
                      responses:
                        '200':
                          description: A list of pets
                    post:
                      operationId: createPet
                      summary: Create a pet
                      responses:
                        '201':
                          description: Created
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            assertEquals(2, spec.endpoints.size)
            assertTrue(spec.endpoints.any { it.method == HttpMethod.GET && it.path == "/pets" })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.POST && it.path == "/pets" })
        }

        @Test
        fun `Given OpenAPI 3 spec with path parameters When parsing Then path parameters are extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Pet Store API
                  version: 1.0.0
                paths:
                  /pets/{petId}:
                    get:
                      operationId: getPet
                      parameters:
                        - name: petId
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: A pet
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            val endpoint = spec.endpoints.first()
            assertEquals("/pets/{petId}", endpoint.path)
            assertEquals(1, endpoint.parameters.size)
            assertEquals("petId", endpoint.parameters[0].name)
            assertEquals(nl.vintik.mocknest.domain.generation.ParameterLocation.PATH, endpoint.parameters[0].location)
            assertTrue(endpoint.parameters[0].required)
        }

        @Test
        fun `Given OpenAPI 3 spec with query parameters When parsing Then query parameters are extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Search API
                  version: 1.0.0
                paths:
                  /search:
                    get:
                      operationId: search
                      parameters:
                        - name: q
                          in: query
                          required: true
                          schema:
                            type: string
                        - name: limit
                          in: query
                          required: false
                          schema:
                            type: integer
                      responses:
                        '200':
                          description: Results
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            val endpoint = spec.endpoints.first()
            assertEquals(2, endpoint.parameters.size)
            assertTrue(endpoint.parameters.any { it.name == "q" && it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.QUERY && it.required })
            assertTrue(endpoint.parameters.any { it.name == "limit" && it.location == nl.vintik.mocknest.domain.generation.ParameterLocation.QUERY && !it.required })
        }

        @Test
        fun `Given OpenAPI 3 spec with all HTTP methods When parsing Then all methods are extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Methods API
                  version: 1.0.0
                paths:
                  /resource:
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
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            assertEquals(5, spec.endpoints.size)
            assertTrue(spec.endpoints.any { it.method == HttpMethod.GET })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.POST })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.PUT })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.DELETE })
            assertTrue(spec.endpoints.any { it.method == HttpMethod.PATCH })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schema Extraction Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class SchemaExtractionNonRegression {

        @Test
        fun `Given OpenAPI 3 spec with component schemas When parsing Then schemas are extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Schema API
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                components:
                  schemas:
                    Pet:
                      type: object
                      required:
                        - id
                        - name
                      properties:
                        id:
                          type: integer
                        name:
                          type: string
                    Error:
                      type: object
                      properties:
                        code:
                          type: integer
                        message:
                          type: string
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            assertEquals(2, spec.schemas.size)
            assertTrue(spec.schemas.containsKey("Pet"))
            assertTrue(spec.schemas.containsKey("Error"))

            val pet = spec.schemas["Pet"]!!
            assertEquals(2, pet.required.size)
            assertTrue(pet.required.contains("id"))
            assertTrue(pet.required.contains("name"))
        }

        @Test
        fun `Given OpenAPI 3 spec with response schema When parsing Then response schema is extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Response Schema API
                  version: 1.0.0
                paths:
                  /users:
                    get:
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: array
                                items:
                                  type: object
                                  properties:
                                    id:
                                      type: integer
                                    name:
                                      type: string
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            val endpoint = spec.endpoints.first()
            val responseSchema = endpoint.responses[200]?.schema
            assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.ARRAY, responseSchema?.type)
            assertEquals(nl.vintik.mocknest.domain.generation.JsonSchemaType.OBJECT, responseSchema?.items?.type)
        }

        @Test
        fun `Given OpenAPI 3 spec with request body When parsing Then request body is extracted`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Request Body API
                  version: 1.0.0
                paths:
                  /users:
                    post:
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                name:
                                  type: string
                                email:
                                  type: string
                      responses:
                        '201':
                          description: Created
            """.trimIndent()

            val spec = parser.parse(content, SpecificationFormat.OPENAPI_3)

            val endpoint = spec.endpoints.first()
            assertTrue(endpoint.requestBody?.required ?: false)
            assertTrue(endpoint.requestBody?.content?.containsKey("application/json") ?: false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata Extraction Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class MetadataExtractionNonRegression {

        @Test
        fun `Given OpenAPI 3 spec When extracting metadata Then title and version are correct`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: My REST API
                  version: 2.5.0
                paths:
                  /test:
                    get:
                      responses:
                        '200': { description: OK }
            """.trimIndent()

            val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

            assertEquals("My REST API", metadata.title)
            assertEquals("2.5.0", metadata.version)
            assertEquals(SpecificationFormat.OPENAPI_3, metadata.format)
        }

        @Test
        fun `Given OpenAPI 3 spec with multiple endpoints When extracting metadata Then endpoint count is correct`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Count API
                  version: 1.0.0
                paths:
                  /a:
                    get:
                      responses:
                        '200': { description: OK }
                    post:
                      responses:
                        '201': { description: Created }
                  /b:
                    get:
                      responses:
                        '200': { description: OK }
            """.trimIndent()

            val metadata = parser.extractMetadata(content, SpecificationFormat.OPENAPI_3)

            assertEquals(3, metadata.endpointCount)
        }

        @Test
        fun `Given OpenAPI 3 spec with component schemas When extracting metadata Then schema count is correct`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Schema Count API
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      responses:
                        '200': { description: OK }
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
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class ValidationNonRegression {

        @Test
        fun `Given valid OpenAPI 3 spec When validating Then result is valid`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Valid API
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            assertTrue(result.isValid)
        }

        @Test
        fun `Given invalid content When validating Then result is invalid`() = runTest {
            val content = "this is not yaml or json"

            val result = parser.validate(content, SpecificationFormat.OPENAPI_3)

            assertFalse(result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Composite Parser Non-Regression
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    inner class CompositeParserNonRegression {

        @Test
        fun `Given composite parser with REST and GraphQL parsers When checking REST format Then REST parser is selected`() {
            val openAPIParser = OpenAPISpecificationParser()
            val composite = CompositeSpecificationParserImpl(listOf(openAPIParser))

            assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
            assertTrue(composite.supports(SpecificationFormat.SWAGGER_2))
        }

        @Test
        fun `Given composite parser When getting supported formats Then REST formats are included`() {
            val openAPIParser = OpenAPISpecificationParser()
            val composite = CompositeSpecificationParserImpl(listOf(openAPIParser))

            val formats = composite.getSupportedFormats()

            assertTrue(formats.contains(SpecificationFormat.OPENAPI_3))
            assertTrue(formats.contains(SpecificationFormat.SWAGGER_2))
        }

        @Test
        fun `Given composite parser with REST spec When parsing Then produces valid APISpecification`() = runTest {
            val content = """
                openapi: 3.0.0
                info:
                  title: Composite Test API
                  version: 1.0.0
                paths:
                  /items:
                    get:
                      operationId: listItems
                      responses:
                        '200':
                          description: OK
            """.trimIndent()

            val openAPIParser = OpenAPISpecificationParser()
            val composite = CompositeSpecificationParserImpl(listOf(openAPIParser))

            val spec = composite.parse(content, SpecificationFormat.OPENAPI_3)

            assertEquals("Composite Test API", spec.title)
            assertEquals(SpecificationFormat.OPENAPI_3, spec.format)
            assertEquals(1, spec.endpoints.size)
            assertEquals("listItems", spec.endpoints[0].operationId)
        }
    }
}
