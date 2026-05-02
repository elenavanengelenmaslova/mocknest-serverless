package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class APISpecificationTest {

    @Test
    fun `Should create valid APISpecification`() {
        val endpoint = EndpointDefinition(
            path = "/test",
            method = HttpMethod.GET,
            operationId = "testOp",
            summary = "Test summary",
            parameters = emptyList(),
            requestBody = null,
            responses = mapOf(200 to ResponseDefinition(200, "OK", null))
        )
        val spec = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0.0",
            title = "Test API",
            endpoints = listOf(endpoint),
            schemas = emptyMap()
        )
        assertEquals("1.0.0", spec.version)
        assertEquals("Test API", spec.title)
        assertEquals(1, spec.endpoints.size)
    }

    @Test
    fun `Should fail APISpecification with blank version`() {
        assertThrows<IllegalArgumentException> {
            APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = " ",
                title = "Test API",
                endpoints = listOf(mockEndpoint()),
                schemas = emptyMap()
            )
        }
    }

    @Test
    fun `Should fail APISpecification with blank title`() {
        assertThrows<IllegalArgumentException> {
            APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0.0",
                title = "",
                endpoints = listOf(mockEndpoint()),
                schemas = emptyMap()
            )
        }
    }

    @Test
    fun `Should fail APISpecification with no endpoints`() {
        assertThrows<IllegalArgumentException> {
            APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0.0",
                title = "Test API",
                endpoints = emptyList(),
                schemas = emptyMap()
            )
        }
    }

    @Test
    fun `Should fail EndpointDefinition with blank path`() {
        assertThrows<IllegalArgumentException> {
            EndpointDefinition(
                path = "",
                method = HttpMethod.GET,
                operationId = null,
                summary = null,
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(200 to ResponseDefinition(200, "OK", null))
            )
        }
    }

    @Test
    fun `Should fail EndpointDefinition with no responses`() {
        assertThrows<IllegalArgumentException> {
            EndpointDefinition(
                path = "/test",
                method = HttpMethod.GET,
                operationId = null,
                summary = null,
                parameters = emptyList(),
                requestBody = null,
                responses = emptyMap()
            )
        }
    }

    @Test
    fun `Should fail ParameterDefinition with blank name`() {
        assertThrows<IllegalArgumentException> {
            ParameterDefinition(
                name = "",
                location = ParameterLocation.QUERY,
                required = true,
                schema = JsonSchema(JsonSchemaType.STRING)
            )
        }
    }

    @Test
    fun `Should fail RequestBodyDefinition with no content`() {
        assertThrows<IllegalArgumentException> {
            RequestBodyDefinition(
                required = true,
                content = emptyMap()
            )
        }
    }

    @Test
    fun `Should fail ResponseDefinition with invalid status code`() {
        assertThrows<IllegalArgumentException> {
            ResponseDefinition(99, "Invalid", null)
        }
        assertThrows<IllegalArgumentException> {
            ResponseDefinition(600, "Invalid", null)
        }
    }

    @Test
    fun `Should fail ResponseDefinition with blank description`() {
        assertThrows<IllegalArgumentException> {
            ResponseDefinition(200, "", null)
        }
    }

    @Test
    fun `Should fail SecurityRequirement with blank name`() {
        assertThrows<IllegalArgumentException> {
            SecurityRequirement(SecurityType.API_KEY, "")
        }
    }

    @Test
    fun `Should fail JsonSchema of type ARRAY without items`() {
        assertThrows<IllegalArgumentException> {
            JsonSchema(JsonSchemaType.ARRAY, items = null)
        }
    }

    @Test
    fun `Should fail JsonSchema with required field not in properties`() {
        assertThrows<IllegalArgumentException> {
            JsonSchema(
                type = JsonSchemaType.OBJECT,
                properties = mapOf("id" to JsonSchema(JsonSchemaType.INTEGER)),
                required = listOf("name")
            )
        }
    }
    
    @Test
    fun `Should create valid JsonSchema OBJECT with required fields`() {
        val schema = JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = mapOf("id" to JsonSchema(JsonSchemaType.INTEGER)),
            required = listOf("id")
        )
        assertNotNull(schema)
    }

    private fun mockEndpoint() = EndpointDefinition(
        path = "/test",
        method = HttpMethod.GET,
        operationId = null,
        summary = null,
        parameters = emptyList(),
        requestBody = null,
        responses = mapOf(200 to ResponseDefinition(200, "OK", null))
    )
}
