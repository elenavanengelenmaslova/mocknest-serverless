package nl.vintik.mocknest.application.generation.parsers

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based test for format-based parser selection in CompositeSpecificationParserImpl.
 *
 * Property 2: Format-Based Parser Selection
 * Validates: Requirements 1.2
 *
 * For any SpecificationFormat, the composite parser must route to the correct
 * format-specific parser: GRAPHQL → GraphQLSpecificationParser,
 * OPENAPI_3/SWAGGER_2 → OpenAPISpecificationParser.
 */
@Tag("graphql-introspection-ai-generation")
@Tag("Property-2")
class FormatBasedParserSelectionPropertyTest {

    private val mockGraphQLParser: GraphQLSpecificationParser = mockk(relaxed = true)
    private val mockOpenAPIParser: OpenAPISpecificationParser = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildComposite(): CompositeSpecificationParserImpl {
        every { mockGraphQLParser.supports(SpecificationFormat.GRAPHQL) } returns true
        every { mockGraphQLParser.supports(SpecificationFormat.OPENAPI_3) } returns false
        every { mockGraphQLParser.supports(SpecificationFormat.SWAGGER_2) } returns false
        every { mockGraphQLParser.supports(SpecificationFormat.WSDL) } returns false

        every { mockOpenAPIParser.supports(SpecificationFormat.OPENAPI_3) } returns true
        every { mockOpenAPIParser.supports(SpecificationFormat.SWAGGER_2) } returns true
        every { mockOpenAPIParser.supports(SpecificationFormat.GRAPHQL) } returns false
        every { mockOpenAPIParser.supports(SpecificationFormat.WSDL) } returns false

        return CompositeSpecificationParserImpl(listOf(mockGraphQLParser, mockOpenAPIParser))
    }

    @ParameterizedTest(name = "Property 2 - Given format {0} When checking support Then correct parser is selected")
    @EnumSource(SpecificationFormat::class)
    fun `Property 2 - Given any SpecificationFormat When checking support Then composite routes to correct parser`(
        format: SpecificationFormat
    ) {
        // Given
        val composite = buildComposite()

        // When
        val isSupported = composite.supports(format)

        // Then - GRAPHQL, OPENAPI_3, and SWAGGER_2 are supported; WSDL is not
        when (format) {
            SpecificationFormat.GRAPHQL,
            SpecificationFormat.OPENAPI_3,
            SpecificationFormat.SWAGGER_2 -> assertTrue(
                isSupported,
                "Composite should support $format"
            )
            else -> assertTrue(
                !isSupported,
                "Composite should not support $format (no parser registered)"
            )
        }
    }

    @ParameterizedTest(name = "Property 2 - Given GRAPHQL format When checking support Then GraphQLSpecificationParser is selected")
    @EnumSource(SpecificationFormat::class, names = ["GRAPHQL"])
    fun `Property 2 - Given GRAPHQL format When checking support Then GraphQLSpecificationParser handles it`(
        format: SpecificationFormat
    ) {
        // Given
        val composite = buildComposite()

        // When / Then - GRAPHQL is supported (routed to GraphQLSpecificationParser)
        assertTrue(composite.supports(format), "Composite should support GRAPHQL via GraphQLSpecificationParser")
        assertTrue(
            composite.getSupportedFormats().contains(SpecificationFormat.GRAPHQL),
            "Supported formats should include GRAPHQL"
        )
    }

    @ParameterizedTest(name = "Property 2 - Given REST format {0} When checking support Then OpenAPISpecificationParser is selected")
    @EnumSource(SpecificationFormat::class, names = ["OPENAPI_3", "SWAGGER_2"])
    fun `Property 2 - Given REST format When checking support Then OpenAPISpecificationParser handles it`(
        format: SpecificationFormat
    ) {
        // Given
        val composite = buildComposite()

        // When / Then - OPENAPI_3 and SWAGGER_2 are supported (routed to OpenAPISpecificationParser)
        assertTrue(composite.supports(format), "Composite should support $format via OpenAPISpecificationParser")
        assertTrue(
            composite.getSupportedFormats().contains(format),
            "Supported formats should include $format"
        )
    }

    @ParameterizedTest(name = "Property 2 - Given format {0} When getting supported formats Then set is consistent with supports()")
    @EnumSource(SpecificationFormat::class)
    fun `Property 2 - Given any format When querying supported formats Then getSupportedFormats is consistent with supports`(
        format: SpecificationFormat
    ) {
        // Given
        val composite = buildComposite()

        // When
        val supportedFormats = composite.getSupportedFormats()
        val isSupported = composite.supports(format)

        // Then - getSupportedFormats() and supports() must be consistent
        if (isSupported) {
            assertTrue(
                supportedFormats.contains(format),
                "getSupportedFormats() should contain $format when supports($format) is true"
            )
        } else {
            assertTrue(
                !supportedFormats.contains(format),
                "getSupportedFormats() should NOT contain $format when supports($format) is false"
            )
        }
    }

    @ParameterizedTest(name = "Property 2 - Given format {0} When building composite Then parser map is correctly populated")
    @EnumSource(SpecificationFormat::class, names = ["GRAPHQL", "OPENAPI_3", "SWAGGER_2"])
    fun `Property 2 - Given supported format When building composite Then format is registered in parser map`(
        format: SpecificationFormat
    ) {
        // Given
        val composite = buildComposite()

        // When
        val supportedFormats = composite.getSupportedFormats()

        // Then
        assertNotNull(supportedFormats, "Supported formats should not be null")
        assertTrue(
            supportedFormats.contains(format),
            "Supported formats should contain $format"
        )
    }
}
