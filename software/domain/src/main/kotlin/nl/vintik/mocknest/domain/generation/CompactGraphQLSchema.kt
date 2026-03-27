package nl.vintik.mocknest.domain.generation

/**
 * Compact representation of a GraphQL schema optimized for AI consumption.
 * Contains only operation signatures, types, and essential metadata.
 */
data class CompactGraphQLSchema(
    val queries: List<GraphQLOperation>,
    val mutations: List<GraphQLOperation>,
    val types: Map<String, GraphQLType>,
    val enums: Map<String, GraphQLEnum>,
    val metadata: GraphQLSchemaMetadata
) {
    init {
        require(queries.isNotEmpty() || mutations.isNotEmpty()) {
            "Schema must have at least one query or mutation"
        }
    }

    /**
     * Pretty-print schema in GraphQL SDL format for round-trip testing.
     */
    fun prettyPrint(): String {
        val builder = StringBuilder()

        // Schema definition
        builder.appendLine("schema {")
        if (queries.isNotEmpty()) builder.appendLine("  query: Query")
        if (mutations.isNotEmpty()) builder.appendLine("  mutation: Mutation")
        builder.appendLine("}")
        builder.appendLine()

        // Query type
        if (queries.isNotEmpty()) {
            builder.appendLine("type Query {")
            queries.forEach { op ->
                builder.append("  ${op.name}")
                if (op.arguments.isNotEmpty()) {
                    builder.append("(")
                    builder.append(op.arguments.joinToString(", ") { arg ->
                        "${arg.name}: ${arg.type}"
                    })
                    builder.append(")")
                }
                builder.appendLine(": ${op.returnType}")
            }
            builder.appendLine("}")
            builder.appendLine()
        }

        // Mutation type
        if (mutations.isNotEmpty()) {
            builder.appendLine("type Mutation {")
            mutations.forEach { op ->
                builder.append("  ${op.name}")
                if (op.arguments.isNotEmpty()) {
                    builder.append("(")
                    builder.append(op.arguments.joinToString(", ") { arg ->
                        "${arg.name}: ${arg.type}"
                    })
                    builder.append(")")
                }
                builder.appendLine(": ${op.returnType}")
            }
            builder.appendLine("}")
            builder.appendLine()
        }

        // Object types
        types.values.forEach { type ->
            builder.appendLine("type ${type.name} {")
            type.fields.forEach { field ->
                builder.appendLine("  ${field.name}: ${field.type}")
            }
            builder.appendLine("}")
            builder.appendLine()
        }

        // Enum types
        enums.values.forEach { enum ->
            builder.appendLine("enum ${enum.name} {")
            enum.values.forEach { value ->
                builder.appendLine("  $value")
            }
            builder.appendLine("}")
            builder.appendLine()
        }

        return builder.toString().trim()
    }
}

/**
 * GraphQL operation (query or mutation).
 */
data class GraphQLOperation(
    val name: String,
    val arguments: List<GraphQLArgument>,
    val returnType: String,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Operation name cannot be blank" }
        require(returnType.isNotBlank()) { "Return type cannot be blank" }
    }
}

/**
 * GraphQL operation argument.
 */
data class GraphQLArgument(
    val name: String,
    val type: String,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Argument name cannot be blank" }
        require(type.isNotBlank()) { "Argument type cannot be blank" }
    }
}

/**
 * GraphQL object type.
 */
data class GraphQLType(
    val name: String,
    val fields: List<GraphQLField>,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Type name cannot be blank" }
        require(fields.isNotEmpty()) { "Type must have at least one field" }
    }
}

/**
 * GraphQL type field.
 */
data class GraphQLField(
    val name: String,
    val type: String,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Field name cannot be blank" }
        require(type.isNotBlank()) { "Field type cannot be blank" }
    }
}

/**
 * GraphQL enum type.
 */
data class GraphQLEnum(
    val name: String,
    val values: List<String>,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Enum name cannot be blank" }
        require(values.isNotEmpty()) { "Enum must have at least one value" }
    }
}

/**
 * Metadata about the GraphQL schema.
 */
data class GraphQLSchemaMetadata(
    val schemaVersion: String? = null,
    val description: String? = null
)
