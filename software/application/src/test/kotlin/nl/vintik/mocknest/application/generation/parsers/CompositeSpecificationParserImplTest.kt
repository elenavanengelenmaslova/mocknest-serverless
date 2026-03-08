package nl.vintik.mocknest.application.generation.parsers

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeSpecificationParserImplTest {

    @Test
    fun `Should register and use parser`() {
        runBlocking {
            val parser = mockk<SpecificationParserInterface>()
            every { parser.supports(any()) } returns true
            
            val spec = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0",
                title = "Test",
                endpoints = listOf(
                    nl.vintik.mocknest.domain.generation.EndpointDefinition(
                        path = "/test",
                        method = org.springframework.http.HttpMethod.GET,
                        operationId = null,
                        summary = null,
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(200 to nl.vintik.mocknest.domain.generation.ResponseDefinition(200, "OK", null))
                    )
                ),
                schemas = emptyMap()
            )
            coEvery { parser.parse("content", SpecificationFormat.OPENAPI_3) } returns spec
            
            val composite = CompositeSpecificationParserImpl(listOf(parser))
            
            assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
            assertEquals(spec, composite.parse("content", SpecificationFormat.OPENAPI_3))
            assertEquals(SpecificationFormat.values().toSet(), composite.getSupportedFormats())
        }
    }

    @Test
    fun `Should throw error if no parser for format`() {
        runBlocking {
            val composite = CompositeSpecificationParserImpl(emptyList())
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    composite.parse("content", SpecificationFormat.OPENAPI_3)
                }
            }
        }
    }

    @Test
    fun `Should return invalid result if no parser for validation`() {
        runBlocking {
            val composite = CompositeSpecificationParserImpl(emptyList())
            val result = composite.validate("content", SpecificationFormat.OPENAPI_3)
            assertTrue(!result.isValid)
            assertEquals("No parser available for format: OPENAPI_3", result.errors.first().message)
        }
    }
}
