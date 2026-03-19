package nl.vintik.mocknest.application.generation.parsers

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeSpecificationParserImplTest {

    @Test
    suspend fun `Should register and use parser`() {
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
                    responses = mapOf(
                        200 to nl.vintik.mocknest.domain.generation.ResponseDefinition(
                            200,
                            "OK",
                            null
                        )
                    )
                )
            ),
            schemas = emptyMap()
        )
        coEvery { parser.parse("content", SpecificationFormat.OPENAPI_3) } returns spec

        val composite = CompositeSpecificationParserImpl(listOf(parser))

        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
        assertEquals(spec, composite.parse("content", SpecificationFormat.OPENAPI_3))
        assertEquals(SpecificationFormat.entries.toSet(), composite.getSupportedFormats())
    }

    @Test
    suspend fun `Should throw error if no parser for format`() {
        val composite = CompositeSpecificationParserImpl(emptyList())
        assertThrows<IllegalArgumentException> {
            composite.parse("content", SpecificationFormat.OPENAPI_3)
        }
    }

    @Test
    suspend fun `Should return invalid result if no parser for validation`() {
        val composite = CompositeSpecificationParserImpl(emptyList())
        val result = composite.validate("content", SpecificationFormat.OPENAPI_3)
        assertTrue(!result.isValid)
        assertEquals("No parser available for format: OPENAPI_3", result.errors.first().message)
    }

    @Test
    suspend fun `Should throw error if no parser for extractMetadata`() {
        val composite = CompositeSpecificationParserImpl(emptyList())
        assertThrows<IllegalArgumentException> {
            composite.extractMetadata("content", SpecificationFormat.OPENAPI_3)
        }
    }

    @Test
    suspend fun `Should delegate validate to correct parser`() {
        val parser = mockk<SpecificationParserInterface>()
        every { parser.supports(any()) } returns false
        every { parser.supports(SpecificationFormat.OPENAPI_3) } returns true

        val validationResult = nl.vintik.mocknest.domain.generation.ValidationResult.valid()
        coEvery { parser.validate("content", SpecificationFormat.OPENAPI_3) } returns validationResult

        val composite = CompositeSpecificationParserImpl(listOf(parser))

        val result = composite.validate("content", SpecificationFormat.OPENAPI_3)

        assertEquals(validationResult, result)
        assertTrue(result.isValid)
    }

    @Test
    suspend fun `Should delegate extractMetadata to correct parser`() {
        val parser = mockk<SpecificationParserInterface>()
        every { parser.supports(any()) } returns false
        every { parser.supports(SpecificationFormat.OPENAPI_3) } returns true

        val metadata = nl.vintik.mocknest.domain.generation.SpecificationMetadata(
            title = "Test API",
            version = "1.0",
            format = SpecificationFormat.OPENAPI_3,
            endpointCount = 5,
            schemaCount = 3
        )
        coEvery { parser.extractMetadata("content", SpecificationFormat.OPENAPI_3) } returns metadata

        val composite = CompositeSpecificationParserImpl(listOf(parser))

        val result = composite.extractMetadata("content", SpecificationFormat.OPENAPI_3)

        assertEquals(metadata, result)
        assertEquals("Test API", result.title)
        assertEquals(5, result.endpointCount)
    }

    @Test
    fun `Should register parser dynamically`() {
        val composite = CompositeSpecificationParserImpl(emptyList())

        assertFalse(composite.supports(SpecificationFormat.OPENAPI_3))
        assertEquals(0, composite.getSupportedFormats().size)

        val parser = mockk<SpecificationParserInterface>()
        every { parser.supports(any()) } returns false
        every { parser.supports(SpecificationFormat.OPENAPI_3) } returns true

        composite.registerParser(parser)

        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
        assertEquals(1, composite.getSupportedFormats().size)
    }

    @Test
    fun `Should support multiple formats with different parsers`() {
        val openApiParser = mockk<SpecificationParserInterface>()
        every { openApiParser.supports(any()) } returns false
        every { openApiParser.supports(SpecificationFormat.OPENAPI_3) } returns true
        every { openApiParser.supports(SpecificationFormat.SWAGGER_2) } returns true

        val graphqlParser = mockk<SpecificationParserInterface>()
        every { graphqlParser.supports(any()) } returns false
        every { graphqlParser.supports(SpecificationFormat.GRAPHQL) } returns true

        val composite = CompositeSpecificationParserImpl(listOf(openApiParser, graphqlParser))

        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
        assertTrue(composite.supports(SpecificationFormat.SWAGGER_2))
        assertTrue(composite.supports(SpecificationFormat.GRAPHQL))
        assertFalse(composite.supports(SpecificationFormat.WSDL))
        assertEquals(3, composite.getSupportedFormats().size)
    }

    @Test
    suspend fun `Should replace parser when registering new parser for same format`() {
        val parser1 = mockk<SpecificationParserInterface>()
        every { parser1.supports(any()) } returns false
        every { parser1.supports(SpecificationFormat.OPENAPI_3) } returns true

        val spec1 = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0",
            title = "API 1",
            endpoints = listOf(
                nl.vintik.mocknest.domain.generation.EndpointDefinition(
                    path = "/test1",
                    method = org.springframework.http.HttpMethod.GET,
                    operationId = null,
                    summary = null,
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(
                        200 to nl.vintik.mocknest.domain.generation.ResponseDefinition(
                            200,
                            "OK",
                            null
                        )
                    )
                )
            ),
            schemas = emptyMap()
        )
        coEvery { parser1.parse("content", SpecificationFormat.OPENAPI_3) } returns spec1

        val composite = CompositeSpecificationParserImpl(listOf(parser1))

        // Should use parser1
        assertEquals("API 1", composite.parse("content", SpecificationFormat.OPENAPI_3).title)

        // Now register a new parser for the same format
        val parser2 = mockk<SpecificationParserInterface>()
        every { parser2.supports(any()) } returns false
        every { parser2.supports(SpecificationFormat.OPENAPI_3) } returns true

        val spec2 = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "2.0",
            title = "API 2",
            endpoints = listOf(
                nl.vintik.mocknest.domain.generation.EndpointDefinition(
                    path = "/test2",
                    method = org.springframework.http.HttpMethod.GET,
                    operationId = null,
                    summary = null,
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(
                        200 to nl.vintik.mocknest.domain.generation.ResponseDefinition(
                            200,
                            "OK",
                            null
                        )
                    )
                )
            ),
            schemas = emptyMap()
        )
        coEvery { parser2.parse("content", SpecificationFormat.OPENAPI_3) } returns spec2

        composite.registerParser(parser2)

        // Should now use parser2
        assertEquals("API 2", composite.parse("content", SpecificationFormat.OPENAPI_3).title)
    }

    @Test
    fun `Should return false for unsupported formats`() {
        val parser = mockk<SpecificationParserInterface>()
        every { parser.supports(any()) } returns false
        every { parser.supports(SpecificationFormat.OPENAPI_3) } returns true

        val composite = CompositeSpecificationParserImpl(listOf(parser))

        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
        assertFalse(composite.supports(SpecificationFormat.SWAGGER_2))
        assertFalse(composite.supports(SpecificationFormat.GRAPHQL))
        assertFalse(composite.supports(SpecificationFormat.WSDL))
    }

    @Test
    fun `Should handle empty getSupportedFormats`() {
        val composite = CompositeSpecificationParserImpl(emptyList())

        val formats = composite.getSupportedFormats()

        assertEquals(0, formats.size)
        assertTrue(formats.isEmpty())
    }

    @Test
    fun `Should initialize with multiple parsers covering all formats`() {
        val parser1 = mockk<SpecificationParserInterface>()
        every { parser1.supports(any()) } returns false
        every { parser1.supports(SpecificationFormat.OPENAPI_3) } returns true
        every { parser1.supports(SpecificationFormat.SWAGGER_2) } returns true

        val parser2 = mockk<SpecificationParserInterface>()
        every { parser2.supports(any()) } returns false
        every { parser2.supports(SpecificationFormat.GRAPHQL) } returns true
        every { parser2.supports(SpecificationFormat.WSDL) } returns true

        val composite = CompositeSpecificationParserImpl(listOf(parser1, parser2))

        // All formats should be supported
        assertTrue(composite.supports(SpecificationFormat.OPENAPI_3))
        assertTrue(composite.supports(SpecificationFormat.SWAGGER_2))
        assertTrue(composite.supports(SpecificationFormat.GRAPHQL))
        assertTrue(composite.supports(SpecificationFormat.WSDL))
        assertEquals(4, composite.getSupportedFormats().size)
    }
}
