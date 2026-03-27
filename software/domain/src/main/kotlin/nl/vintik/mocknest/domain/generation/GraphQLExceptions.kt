package nl.vintik.mocknest.domain.generation

/**
 * Exception thrown when GraphQL introspection fails.
 */
class GraphQLIntrospectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when GraphQL schema parsing fails.
 */
class GraphQLSchemaParsingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
