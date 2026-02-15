package nl.vintik.mocknest.domain.generation

import org.springframework.http.HttpMethod

/**
 * Domain model for parsed API specifications.
 */
data class APISpecification(
    val format: SpecificationFormat,
    val version: String,
    val title: String,
    val endpoints: List<EndpointDefinition>,
    val schemas: Map<String, JsonSchema>,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(version.isNotBlank()) { "Specification version cannot be blank" }
        require(title.isNotBlank()) { "Specification title cannot be blank" }
        require(endpoints.isNotEmpty()) { "Specification must have at least one endpoint" }
    }
}

/**
 * Definition of an API endpoint.
 */
data class EndpointDefinition(
    val path: String,
    val method: HttpMethod,
    val operationId: String?,
    val summary: String?,
    val parameters: List<ParameterDefinition>,
    val requestBody: RequestBodyDefinition?,
    val responses: Map<Int, ResponseDefinition>,
    val security: List<SecurityRequirement> = emptyList()
) {
    init {
        require(path.isNotBlank()) { "Endpoint path cannot be blank" }
        require(responses.isNotEmpty()) { "Endpoint must have at least one response definition" }
    }
}

/**
 * Definition of an endpoint parameter.
 */
data class ParameterDefinition(
    val name: String,
    val location: ParameterLocation,
    val required: Boolean,
    val schema: JsonSchema,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Parameter name cannot be blank" }
    }
}

/**
 * Location of a parameter in the HTTP request.
 */
enum class ParameterLocation {
    QUERY, PATH, HEADER, COOKIE
}

/**
 * Definition of a request body.
 */
data class RequestBodyDefinition(
    val required: Boolean,
    val content: Map<String, MediaTypeDefinition>,
    val description: String? = null
) {
    init {
        require(content.isNotEmpty()) { "Request body must have at least one content type" }
    }
}

/**
 * Definition of a response.
 */
data class ResponseDefinition(
    val statusCode: Int,
    val description: String,
    val schema: JsonSchema?,
    val examples: Map<String, Any> = emptyMap(),
    val headers: Map<String, HeaderDefinition> = emptyMap()
) {
    init {
        require(statusCode in 100..599) { "Status code must be valid HTTP status code" }
        require(description.isNotBlank()) { "Response description cannot be blank" }
    }
}

/**
 * Definition of a media type (content type).
 */
data class MediaTypeDefinition(
    val schema: JsonSchema,
    val examples: Map<String, Any> = emptyMap()
)

/**
 * Definition of a response header.
 */
data class HeaderDefinition(
    val schema: JsonSchema,
    val required: Boolean = false,
    val description: String? = null
)

/**
 * Security requirement for an endpoint.
 */
data class SecurityRequirement(
    val type: SecurityType,
    val name: String,
    val scopes: List<String> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "Security requirement name cannot be blank" }
    }
}

/**
 * Type of security requirement.
 */
enum class SecurityType {
    API_KEY, HTTP_BASIC, HTTP_BEARER, OAUTH2, OPENID_CONNECT
}

/**
 * JSON Schema representation for validation and generation.
 */
data class JsonSchema(
    val type: JsonSchemaType,
    val format: String? = null,
    val properties: Map<String, JsonSchema> = emptyMap(),
    val items: JsonSchema? = null,
    val required: List<String> = emptyList(),
    val enum: List<Any> = emptyList(),
    val example: Any? = null,
    val description: String? = null,
    val minimum: Number? = null,
    val maximum: Number? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val additionalProperties: Boolean = true
) {
    init {
        if (type == JsonSchemaType.ARRAY) {
            require(items != null) { "Array schema must have items definition" }
        }
        if (type == JsonSchemaType.OBJECT && properties.isNotEmpty()) {
            required.forEach { requiredField ->
                require(properties.containsKey(requiredField)) { 
                    "Required field '$requiredField' must be defined in properties" 
                }
            }
        }
    }
}

/**
 * JSON Schema primitive types.
 */
enum class JsonSchemaType {
    STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT, NULL
}