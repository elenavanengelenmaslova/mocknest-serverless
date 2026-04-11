package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("graphql-ai-generation")
@Tag("unit")
class GraphQLExceptionsTest {

    @Test
    fun `Given message only When creating GraphQLIntrospectionException Then should carry message correctly`() {
        val exception = GraphQLIntrospectionException("Failed to introspect schema at https://api.example.com/graphql")

        assertEquals("Failed to introspect schema at https://api.example.com/graphql", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating GraphQLIntrospectionException Then should carry both correctly`() {
        val cause = RuntimeException("connection timeout")
        val exception = GraphQLIntrospectionException("Introspection request timed out", cause)

        assertEquals("Introspection request timed out", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given GraphQLIntrospectionException When checking type Then should be RuntimeException`() {
        val exception = GraphQLIntrospectionException("error")

        assertNotNull(exception as? RuntimeException)
    }

    @Test
    fun `Given message only When creating GraphQLSchemaParsingException Then should carry message correctly`() {
        val exception = GraphQLSchemaParsingException("Invalid type definition on line 12")

        assertEquals("Invalid type definition on line 12", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `Given message and cause When creating GraphQLSchemaParsingException Then should carry both correctly`() {
        val cause = IllegalArgumentException("unexpected token")
        val exception = GraphQLSchemaParsingException("Schema parsing failed", cause)

        assertEquals("Schema parsing failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Given GraphQLSchemaParsingException When checking type Then should be RuntimeException`() {
        val exception = GraphQLSchemaParsingException("error")

        assertNotNull(exception as? RuntimeException)
    }
}
