package nl.vintik.mocknest.application.generation.graphql

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import nl.vintik.mocknest.domain.generation.*

private val logger = KotlinLogging.logger {}

/**
 * Interface for reducing raw GraphQL introspection JSON to compact schema.
 */
interface GraphQLSchemaReducerInterface {
    /**
     * Reduce raw introspection JSON to compact schema.
     * @param introspectionJson Raw introspection result
     * @return Compact schema representation
     */
    suspend fun reduce(introspectionJson: String): CompactGraphQLSchema
}

/**
 * Implementation of GraphQL schema reducer.
 * Converts raw introspection JSON into a compact representation suitable for AI consumption.
 */
class GraphQLSchemaReducer : GraphQLSchemaReducerInterface {

    private val builtInScalars = setOf("String", "Int", "Float", "Boolean", "ID")
    
    override suspend fun reduce(introspectionJson: String): CompactGraphQLSchema {
        logger.info { "Starting GraphQL schema reduction" }
        
        return runCatching {
            val json = try {
                Json.parseToJsonElement(introspectionJson).jsonObject
            } catch (e: Exception) {
                throw GraphQLSchemaParsingException("Invalid JSON format: ${e.message}", e)
            }
            
            val schemaElement = json["data"]?.jsonObject?.get("__schema")
            if (schemaElement == null || schemaElement is JsonNull) {
                throw GraphQLSchemaParsingException("Invalid introspection JSON: missing __schema")
            }
            val schema = schemaElement.jsonObject
            
            val typesElement = schema["types"]
            if (typesElement == null || typesElement is JsonNull) {
                throw GraphQLSchemaParsingException("Invalid introspection JSON: missing types")
            }
            val types = typesElement.jsonArray
            
            val queryTypeElement = schema["queryType"]
            val queryTypeName = if (queryTypeElement != null && queryTypeElement !is JsonNull) {
                queryTypeElement.jsonObject["name"]?.jsonPrimitive?.content
            } else null
            
            val mutationTypeElement = schema["mutationType"]
            val mutationTypeName = if (mutationTypeElement != null && mutationTypeElement !is JsonNull) {
                mutationTypeElement.jsonObject["name"]?.jsonPrimitive?.content
            } else null
            
            val queries = mutableListOf<GraphQLOperation>()
            val mutations = mutableListOf<GraphQLOperation>()
            val extractedTypes = mutableMapOf<String, GraphQLType>()
            val extractedEnums = mutableMapOf<String, GraphQLEnum>()
            
            // Process all types
            types.forEach { typeElement ->
                val type = typeElement.jsonObject
                val typeName = type["name"]?.jsonPrimitive?.content ?: return@forEach
                val typeKind = type["kind"]?.jsonPrimitive?.content ?: return@forEach
                
                // Skip introspection metadata types and built-in scalars
                if (typeName.startsWith("__") || builtInScalars.contains(typeName)) {
                    return@forEach
                }
                
                when (typeKind) {
                    "OBJECT" -> {
                        when (typeName) {
                            queryTypeName -> {
                                queries.addAll(extractOperations(type))
                            }
                            mutationTypeName -> {
                                mutations.addAll(extractOperations(type))
                            }
                            else -> {
                                extractObjectType(type)?.let { extractedTypes[typeName] = it }
                            }
                        }
                    }
                    "INPUT_OBJECT" -> {
                        extractInputType(type)?.let { extractedTypes[typeName] = it }
                    }
                    "ENUM" -> {
                        extractEnumType(type)?.let { extractedEnums[typeName] = it }
                    }
                }
            }
            
            val metadata = GraphQLSchemaMetadata(
                schemaVersion = null,
                description = schema["description"]?.jsonPrimitive?.contentOrNull
            )
            
            val compactSchema = CompactGraphQLSchema(
                queries = queries,
                mutations = mutations,
                types = extractedTypes,
                enums = extractedEnums,
                metadata = metadata
            )
            
            val originalSize = introspectionJson.length
            val compactSize = compactSchema.prettyPrint().length
            val reductionPercent = ((originalSize - compactSize).toDouble() / originalSize * 100).toInt()
            
            logger.info { 
                "Schema reduction complete: ${queries.size} queries, ${mutations.size} mutations, " +
                "${extractedTypes.size} types, ${extractedEnums.size} enums. " +
                "Size reduced by $reductionPercent% (${originalSize} -> ${compactSize} bytes)"
            }
            
            compactSchema
        }.onFailure { exception ->
            logger.error(exception) { "Failed to reduce GraphQL schema" }
        }.getOrThrow()
    }
    
    private fun extractOperations(type: JsonObject): List<GraphQLOperation> {
        val fields = type["fields"]?.jsonArray ?: return emptyList()
        
        return fields.mapNotNull { fieldElement ->
            val field = fieldElement.jsonObject
            val name = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            
            // Skip introspection fields
            if (name.startsWith("__")) return@mapNotNull null
            
            val description = field["description"]?.jsonPrimitive?.contentOrNull
            val args = extractArguments(field)
            val returnType = extractTypeName(field["type"]?.jsonObject)
            
            GraphQLOperation(
                name = name,
                arguments = args,
                returnType = returnType,
                description = description
            )
        }
    }
    
    private fun extractArguments(field: JsonObject): List<GraphQLArgument> {
        val args = field["args"]?.jsonArray ?: return emptyList()
        
        return args.mapNotNull { argElement ->
            val arg = argElement.jsonObject
            val name = arg["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = extractTypeName(arg["type"]?.jsonObject)
            val description = arg["description"]?.jsonPrimitive?.contentOrNull
            
            GraphQLArgument(
                name = name,
                type = type,
                description = description
            )
        }
    }
    
    private fun extractObjectType(type: JsonObject): GraphQLType? {
        val name = type["name"]?.jsonPrimitive?.content ?: return null
        val description = type["description"]?.jsonPrimitive?.contentOrNull
        val fields = type["fields"]?.jsonArray ?: return null
        
        val extractedFields = fields.mapNotNull { fieldElement ->
            val field = fieldElement.jsonObject
            val fieldName = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            
            // Skip introspection fields
            if (fieldName.startsWith("__")) return@mapNotNull null
            
            val fieldType = extractTypeName(field["type"]?.jsonObject)
            val fieldDescription = field["description"]?.jsonPrimitive?.contentOrNull
            
            GraphQLField(
                name = fieldName,
                type = fieldType,
                description = fieldDescription
            )
        }
        
        return if (extractedFields.isEmpty()) null else GraphQLType(
            name = name,
            fields = extractedFields,
            description = description
        )
    }
    
    private fun extractInputType(type: JsonObject): GraphQLType? {
        val name = type["name"]?.jsonPrimitive?.content ?: return null
        val description = type["description"]?.jsonPrimitive?.contentOrNull
        val inputFields = type["inputFields"]?.jsonArray ?: return null
        
        val extractedFields = inputFields.mapNotNull { fieldElement ->
            val field = fieldElement.jsonObject
            val fieldName = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val fieldType = extractTypeName(field["type"]?.jsonObject)
            val fieldDescription = field["description"]?.jsonPrimitive?.contentOrNull
            
            GraphQLField(
                name = fieldName,
                type = fieldType,
                description = fieldDescription
            )
        }
        
        return if (extractedFields.isEmpty()) null else GraphQLType(
            name = name,
            fields = extractedFields,
            description = description
        )
    }
    
    private fun extractEnumType(type: JsonObject): GraphQLEnum? {
        val name = type["name"]?.jsonPrimitive?.content ?: return null
        val description = type["description"]?.jsonPrimitive?.contentOrNull
        val enumValues = type["enumValues"]?.jsonArray ?: return null
        
        val values = enumValues.mapNotNull { valueElement ->
            valueElement.jsonObject["name"]?.jsonPrimitive?.content
        }
        
        return if (values.isEmpty()) null else GraphQLEnum(
            name = name,
            values = values,
            description = description
        )
    }
    
    private fun extractTypeName(typeObj: JsonObject?): String {
        if (typeObj == null) return "Unknown"
        
        val kind = typeObj["kind"]?.jsonPrimitive?.content
        val name = typeObj["name"]?.jsonPrimitive?.contentOrNull
        val ofTypeElement = typeObj["ofType"]
        
        // Handle JsonNull case - when ofType is explicitly null in JSON
        val ofType = if (ofTypeElement is JsonNull) null else ofTypeElement?.jsonObject
        
        return when (kind) {
            "NON_NULL" -> "${extractTypeName(ofType)}!"
            "LIST" -> "[${extractTypeName(ofType)}]"
            else -> name ?: "Unknown"
        }
    }
}
