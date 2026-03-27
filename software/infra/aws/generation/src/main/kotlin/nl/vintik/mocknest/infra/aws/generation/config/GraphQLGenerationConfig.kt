package nl.vintik.mocknest.infra.aws.generation.config

import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.infra.aws.generation.graphql.GraphQLIntrospectionClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for GraphQL-specific mock generation components.
 * Registers all GraphQL infrastructure beans following clean architecture boundaries.
 */
@Configuration
class GraphQLGenerationConfig {

    /**
     * GraphQL introspection client for fetching schemas from live endpoints.
     * Infrastructure layer implementation of the application-layer interface.
     */
    @Bean
    fun graphQLIntrospectionClient(): GraphQLIntrospectionClientInterface {
        return GraphQLIntrospectionClient()
    }

    /**
     * GraphQL schema reducer for converting raw introspection JSON to compact schema.
     */
    @Bean
    fun graphQLSchemaReducer(): GraphQLSchemaReducerInterface {
        return GraphQLSchemaReducer()
    }

    /**
     * GraphQL specification parser supporting both URL-based introspection and pre-fetched schemas.
     */
    @Bean
    fun graphQLSpecificationParser(
        introspectionClient: GraphQLIntrospectionClientInterface,
        schemaReducer: GraphQLSchemaReducerInterface
    ): SpecificationParserInterface {
        return GraphQLSpecificationParser(introspectionClient, schemaReducer)
    }

    /**
     * GraphQL mock validator for validating generated mocks against the introspected schema.
     */
    @Bean
    fun graphQLMockValidator(): GraphQLMockValidator {
        return GraphQLMockValidator()
    }
}
