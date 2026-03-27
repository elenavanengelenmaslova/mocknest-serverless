package nl.vintik.mocknest.application.generation.parsers

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.domain.generation.*
import org.springframework.http.HttpMethod

private val logger = KotlinLogging.logger {}

/**
 * Parser for GraphQL schemas.
 * Supports both URL-based introspection and pre-fetched schema content.
 */
class GraphQLSpecificationParser(
    private val introspectionClient: GraphQLIntrospectionClientInterface,
    private val schemaReducer: GraphQLSchemaReducerInterface
) : SpecificationParserInterface {

    override suspend fun parse(content: String, format: SpecificationFormat): APISpecification {
        require(format == SpecificationFormat.GRAPHQL) { "Only GRAPHQL format supported" }

        logger.info { "Parsing GraphQL specification" }

        return runCatching {
            val introspectionJson = if (content.startsWith("http")) {
                logger.info { "Detected URL input, executing introspection: $content" }
                introspectionClient.introspect(content)
            } else {
                logger.debug { "Using pre-fetched schema content" }
                content
            }
            val compactSchema = schemaReducer.reduce(introspectionJson)
            convertToAPISpecification(compactSchema, introspectionJson)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to parse GraphQL specification" }
        }.getOrThrow()
    }

    override fun supports(format: SpecificationFormat): Boolean {
        return format == SpecificationFormat.GRAPHQL
    }

    override suspend fun validate(content: String, format: SpecificationFormat): ValidationResult {
        require(format == SpecificationFormat.GRAPHQL) { "Only GRAPHQL format supported" }
        
        logger.debug { "Validating GraphQL specification" }
        
        return runCatching {
            // Attempt to reduce the schema - if it succeeds, it's valid
            schemaReducer.reduce(content)
            ValidationResult.valid()
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                logger.warn(exception) { "GraphQL specification validation failed" }
                ValidationResult.invalid(
                    listOf(
                        ValidationError(
                            message = exception.message ?: "Unknown validation error",
                            path = null
                        )
                    )
                )
            }
        )
    }

    override suspend fun extractMetadata(content: String, format: SpecificationFormat): SpecificationMetadata {
        require(format == SpecificationFormat.GRAPHQL) { "Only GRAPHQL format supported" }
        
        logger.debug { "Extracting metadata from GraphQL specification" }
        
        return runCatching {
            val compactSchema = schemaReducer.reduce(content)
            
            SpecificationMetadata(
                title = compactSchema.metadata.description ?: "GraphQL API",
                version = compactSchema.metadata.schemaVersion ?: "1.0",
                format = SpecificationFormat.GRAPHQL,
                endpointCount = compactSchema.queries.size + compactSchema.mutations.size,
                schemaCount = compactSchema.types.size + compactSchema.enums.size
            )
        }.onFailure { exception ->
            logger.error(exception) { "Failed to extract metadata from GraphQL specification" }
        }.getOrThrow()
    }

    private fun convertToAPISpecification(
        schema: CompactGraphQLSchema,
        rawContent: String
    ): APISpecification {
        logger.debug { "Converting CompactGraphQLSchema to APISpecification" }
        
        val endpoints = mutableListOf<EndpointDefinition>()
        val schemas = mutableMapOf<String, JsonSchema>()
        
        // Convert queries to endpoints
        schema.queries.forEach { query ->
            endpoints.add(createEndpointDefinition(query, "query", schema))
        }
        
        // Convert mutations to endpoints
        schema.mutations.forEach { mutation ->
            endpoints.add(createEndpointDefinition(mutation, "mutation", schema))
        }
        
        // Convert GraphQL types to JSON schemas
        schema.types.forEach { (name, type) ->
            schemas[name] = convertTypeToJsonSchema(type)
        }
        
        // Convert GraphQL enums to JSON schemas
        schema.enums.forEach { (name, enum) ->
            schemas[name] = convertEnumToJsonSchema(enum)
        }
        
        return APISpecification(
            format = SpecificationFormat.GRAPHQL,
            version = schema.metadata.schemaVersion ?: "1.0",
            title = schema.metadata.description ?: "GraphQL API",
            endpoints = endpoints,
            schemas = schemas,
            metadata = mapOf(
                "operationType" to "graphql",
                "queryCount" to schema.queries.size.toString(),
                "mutationCount" to schema.mutations.size.toString()
            ),
            rawContent = rawContent
        )
    }

    private fun createEndpointDefinition(
        operation: GraphQLOperation,
        operationType: String,
        schema: CompactGraphQLSchema
    ): EndpointDefinition {
        // GraphQL operations are always POST to /graphql endpoint
        val requestBodySchema = createGraphQLRequestSchema(operation, operationType)
        val responseSchema = createGraphQLResponseSchema(operation, schema)
        
        return EndpointDefinition(
            path = "/graphql",
            method = HttpMethod.POST,
            operationId = operation.name,
            summary = operation.description,
            parameters = emptyList(), // GraphQL uses body, not query params
            requestBody = RequestBodyDefinition(
                required = true,
                content = mapOf(
                    "application/json" to MediaTypeDefinition(
                        schema = requestBodySchema
                    )
                ),
                description = "GraphQL $operationType request"
            ),
            responses = mapOf(
                200 to ResponseDefinition(
                    statusCode = 200,
                    description = "Successful GraphQL response",
                    schema = responseSchema
                )
            )
        )
    }

    private fun createGraphQLRequestSchema(
        operation: GraphQLOperation,
        operationType: String
    ): JsonSchema {
        // GraphQL request body: { "query": "...", "variables": {...}, "operationName": "..." }
        val variablesSchema = if (operation.arguments.isNotEmpty()) {
            JsonSchema(
                type = JsonSchemaType.OBJECT,
                properties = operation.arguments.associate { arg ->
                    arg.name to JsonSchema(
                        type = mapGraphQLTypeToJsonSchemaType(arg.type),
                        description = arg.description
                    )
                },
                required = operation.arguments.filter { it.type.endsWith("!") }.map { it.name }
            )
        } else {
            JsonSchema(type = JsonSchemaType.OBJECT)
        }
        
        return JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = mapOf(
                "query" to JsonSchema(
                    type = JsonSchemaType.STRING,
                    description = "GraphQL $operationType string"
                ),
                "variables" to variablesSchema,
                "operationName" to JsonSchema(
                    type = JsonSchemaType.STRING,
                    description = "Operation name"
                )
            ),
            required = listOf("query")
        )
    }

    private fun createGraphQLResponseSchema(
        operation: GraphQLOperation,
        schema: CompactGraphQLSchema
    ): JsonSchema {
        // GraphQL response: { "data": {...}, "errors": [...] }
        val returnTypeName = operation.returnType.removeSuffix("!").removePrefix("[").removeSuffix("]")
        val dataSchema = schema.types[returnTypeName]?.let { convertTypeToJsonSchema(it) }
            ?: JsonSchema(
                type = mapGraphQLTypeToJsonSchemaType(operation.returnType),
                description = "Response data"
            )
        
        return JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = mapOf(
                "data" to dataSchema,
                "errors" to JsonSchema(
                    type = JsonSchemaType.ARRAY,
                    items = JsonSchema(
                        type = JsonSchemaType.OBJECT,
                        properties = mapOf(
                            "message" to JsonSchema(type = JsonSchemaType.STRING),
                            "path" to JsonSchema(
                                type = JsonSchemaType.ARRAY,
                                items = JsonSchema(type = JsonSchemaType.STRING)
                            )
                        )
                    ),
                    description = "GraphQL errors"
                )
            )
        )
    }

    private fun convertTypeToJsonSchema(type: GraphQLType): JsonSchema {
        return JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = type.fields.associate { field ->
                field.name to JsonSchema(
                    type = mapGraphQLTypeToJsonSchemaType(field.type),
                    description = field.description
                )
            },
            required = type.fields.filter { it.type.endsWith("!") }.map { it.name },
            description = type.description
        )
    }

    private fun convertEnumToJsonSchema(enum: GraphQLEnum): JsonSchema {
        return JsonSchema(
            type = JsonSchemaType.STRING,
            enum = enum.values,
            description = enum.description
        )
    }

    private fun mapGraphQLTypeToJsonSchemaType(graphQLType: String): JsonSchemaType {
        val baseType = graphQLType.removeSuffix("!").removePrefix("[").removeSuffix("]")
        
        return when (baseType) {
            "String", "ID" -> JsonSchemaType.STRING
            "Int" -> JsonSchemaType.INTEGER
            "Float" -> JsonSchemaType.NUMBER
            "Boolean" -> JsonSchemaType.BOOLEAN
            else -> JsonSchemaType.OBJECT // Custom types
        }
    }
}
