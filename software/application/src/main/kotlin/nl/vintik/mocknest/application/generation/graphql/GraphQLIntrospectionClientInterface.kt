package nl.vintik.mocknest.application.generation.graphql

import nl.vintik.mocknest.domain.generation.GraphQLIntrospectionException

/**
 * Interface for fetching GraphQL schemas via introspection.
 * Defined in the application layer to maintain clean architecture boundaries.
 * Implementation lives in the infrastructure layer.
 */
interface GraphQLIntrospectionClientInterface {
    /**
     * Execute introspection query against a GraphQL endpoint.
     * @param endpointUrl The GraphQL endpoint URL
     * @param headers Optional HTTP headers for authentication
     * @param timeoutMs Request timeout in milliseconds
     * @return Raw introspection result as JSON string
     * @throws GraphQLIntrospectionException on failure
     */
    suspend fun introspect(
        endpointUrl: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000
    ): String
}
