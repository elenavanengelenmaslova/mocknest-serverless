package nl.vintik.mocknest.application.generation.parsers

import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import org.springframework.http.HttpMethod
import nl.vintik.mocknest.domain.generation.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.parser.OpenAPIV3Parser

/**
 * Parser for OpenAPI 3.0 and Swagger 2.0 specifications.
 */
class OpenAPISpecificationParser : SpecificationParserInterface {
    
    override suspend fun parse(content: String, format: SpecificationFormat): APISpecification {
        val parseResult = OpenAPIV3Parser().readContents(content)
        
        if (parseResult.messages.isNotEmpty()) {
            throw IllegalArgumentException("OpenAPI parsing errors: ${parseResult.messages.joinToString(", ")}")
        }
        
        val openAPI = parseResult.openAPI 
            ?: throw IllegalArgumentException("Failed to parse OpenAPI specification")
        
        return convertToAPISpecification(openAPI, format)
    }
    
    override fun supports(format: SpecificationFormat): Boolean = 
        format in listOf(SpecificationFormat.OPENAPI_3, SpecificationFormat.SWAGGER_2)
    
    override suspend fun validate(content: String, format: SpecificationFormat): ValidationResult {
        return try {
            val parseResult = OpenAPIV3Parser().readContents(content)
            
            if (parseResult.openAPI == null) {
                ValidationResult.invalid(listOf(
                    ValidationError("Failed to parse OpenAPI specification")
                ))
            } else if (parseResult.messages.isNotEmpty()) {
                ValidationResult(
                    isValid = true,
                    warnings = parseResult.messages.map { ValidationWarning(it) }
                )
            } else {
                ValidationResult.valid()
            }
        } catch (e: Exception) {
            ValidationResult.invalid(listOf(
                ValidationError("Validation failed: ${e.message}")
            ))
        }
    }
    
    override suspend fun extractMetadata(content: String, format: SpecificationFormat): SpecificationMetadata {
        val parseResult = OpenAPIV3Parser().readContents(content)
        val openAPI = parseResult.openAPI 
            ?: throw IllegalArgumentException("Failed to parse OpenAPI specification")
        
        val endpointCount = openAPI.paths?.values?.sumOf { pathItem ->
            listOfNotNull(
                pathItem.get, pathItem.post, pathItem.put, pathItem.delete,
                pathItem.patch, pathItem.head, pathItem.options, pathItem.trace
            ).size
        } ?: 0
        
        val schemaCount = openAPI.components?.schemas?.size ?: 0
        
        return SpecificationMetadata(
            title = openAPI.info?.title ?: "Unknown",
            version = openAPI.info?.version ?: "Unknown",
            format = format,
            endpointCount = endpointCount,
            schemaCount = schemaCount
        )
    }
    
    private fun convertToAPISpecification(openAPI: OpenAPI, format: SpecificationFormat): APISpecification {
        val endpoints = mutableListOf<EndpointDefinition>()
        
        openAPI.paths?.forEach { (path, pathItem) ->
            endpoints.addAll(convertPathItem(path, pathItem))
        }
        
        val schemas = openAPI.components?.schemas?.mapValues { (_, schema) ->
            convertSchema(schema)
        } ?: emptyMap()
        
        return APISpecification(
            format = format,
            version = openAPI.info?.version ?: "Unknown",
            title = openAPI.info?.title ?: "Unknown",
            endpoints = endpoints,
            schemas = schemas,
            metadata = mapOf(
                "description" to (openAPI.info?.description ?: ""),
                "host" to (openAPI.servers?.firstOrNull()?.url ?: "")
            )
        )
    }
    
    private fun convertPathItem(path: String, pathItem: PathItem): List<EndpointDefinition> {
        val endpoints = mutableListOf<EndpointDefinition>()
        
        pathItem.get?.let { endpoints.add(convertOperation(path, HttpMethod.GET, it)) }
        pathItem.post?.let { endpoints.add(convertOperation(path, HttpMethod.POST, it)) }
        pathItem.put?.let { endpoints.add(convertOperation(path, HttpMethod.PUT, it)) }
        pathItem.delete?.let { endpoints.add(convertOperation(path, HttpMethod.DELETE, it)) }
        pathItem.patch?.let { endpoints.add(convertOperation(path, HttpMethod.PATCH, it)) }
        pathItem.head?.let { endpoints.add(convertOperation(path, HttpMethod.HEAD, it)) }
        pathItem.options?.let { endpoints.add(convertOperation(path, HttpMethod.OPTIONS, it)) }
        
        return endpoints
    }
    
    private fun convertOperation(path: String, method: HttpMethod, operation: Operation): EndpointDefinition {
        val parameters = operation.parameters?.map { convertParameter(it) } ?: emptyList()
        
        val requestBody = operation.requestBody?.let { requestBodySpec ->
            RequestBodyDefinition(
                required = requestBodySpec.required ?: false,
                content = requestBodySpec.content?.mapValues { (_, mediaType) ->
                    MediaTypeDefinition(
                        schema = convertSchema(mediaType.schema),
                        examples = mediaType.examples?.mapValues { it.value.value } ?: emptyMap()
                    )
                } ?: emptyMap(),
                description = requestBodySpec.description
            )
        }
        
        val responses = operation.responses?.mapNotNull { (statusCode, response) ->
            statusCode.toIntOrNull()?.let { code ->
                code to convertResponse(code, response)
            }
        }?.toMap() ?: emptyMap()
        
        return EndpointDefinition(
            path = path,
            method = method,
            operationId = operation.operationId,
            summary = operation.summary,
            parameters = parameters,
            requestBody = requestBody,
            responses = responses,
            security = emptyList() // TODO: Convert security requirements
        )
    }
    
    private fun convertParameter(parameter: Parameter): ParameterDefinition {
        val location = when (parameter.`in`) {
            "query" -> ParameterLocation.QUERY
            "path" -> ParameterLocation.PATH
            "header" -> ParameterLocation.HEADER
            "cookie" -> ParameterLocation.COOKIE
            else -> ParameterLocation.QUERY
        }
        
        return ParameterDefinition(
            name = parameter.name,
            location = location,
            required = parameter.required ?: false,
            schema = convertSchema(parameter.schema),
            description = parameter.description
        )
    }
    
    private fun convertResponse(statusCode: Int, response: ApiResponse): ResponseDefinition {
        val schema = response.content?.values?.firstOrNull()?.schema?.let { convertSchema(it) }
        
        val examples = response.content?.values?.firstOrNull()?.examples?.mapValues { 
            it.value.value 
        } ?: emptyMap()
        
        val headers = response.headers?.mapValues { (_, header) ->
            HeaderDefinition(
                schema = convertSchema(header.schema),
                required = header.required ?: false,
                description = header.description
            )
        } ?: emptyMap()
        
        return ResponseDefinition(
            statusCode = statusCode,
            description = response.description ?: "",
            schema = schema,
            examples = examples,
            headers = headers
        )
    }
    
    private fun convertSchema(schema: Schema<*>?): JsonSchema {
        if (schema == null) {
            return JsonSchema(type = JsonSchemaType.OBJECT)
        }
        
        val type = when (schema.type?.lowercase()) {
            "string" -> JsonSchemaType.STRING
            "number" -> JsonSchemaType.NUMBER
            "integer" -> JsonSchemaType.INTEGER
            "boolean" -> JsonSchemaType.BOOLEAN
            "array" -> JsonSchemaType.ARRAY
            "object" -> JsonSchemaType.OBJECT
            "null" -> JsonSchemaType.NULL
            else -> JsonSchemaType.OBJECT
        }
        
        val properties = schema.properties?.mapValues { (_, propSchema) ->
            convertSchema(propSchema)
        } ?: emptyMap()
        
        val items = schema.items?.let { convertSchema(it) }
        
        return JsonSchema(
            type = type,
            format = schema.format,
            properties = properties,
            items = items,
            required = schema.required ?: emptyList(),
            enum = schema.enum ?: emptyList(),
            example = schema.example,
            description = schema.description,
            minimum = schema.minimum,
            maximum = schema.maximum,
            minLength = schema.minLength,
            maxLength = schema.maxLength,
            pattern = schema.pattern,
            additionalProperties = schema.additionalProperties != false
        )
    }
}