package nl.vintik.mocknest.application.generation.parsers

import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Assertions.assertEquals
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
}
