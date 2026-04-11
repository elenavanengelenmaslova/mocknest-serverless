package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `Given GraphQLIntrospectionException When thrown Then should be catchable as RuntimeException`() {
        val thrown = assertFailsWith<RuntimeException> {
            throw GraphQLIntrospectionException("error")
        }

        assertTrue(thrown is GraphQLIntrospectionException)
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
    fun `Given GraphQLSchemaParsingException When thrown Then should be catchable as RuntimeException`() {
        val thrown = assertFailsWith<RuntimeException> {
            throw GraphQLSchemaParsingException("error")
        }

        assertTrue(thrown is GraphQLSchemaParsingException)
    }
}
