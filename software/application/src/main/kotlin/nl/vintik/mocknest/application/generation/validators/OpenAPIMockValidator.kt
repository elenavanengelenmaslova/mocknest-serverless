package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.JsonSchema
import nl.vintik.mocknest.domain.generation.JsonSchemaType
import org.springframework.stereotype.Component

/**
 * Validates generated mocks against OpenAPI specifications.
 */
@Component
class OpenAPIMockValidator : MockValidatorInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        logger.debug { "Validating mock ${mock.id} against OpenAPI specification" }
        
        val errors = mutableListOf<String>()
        
        runCatching {
            // Parse the WireMock mapping
            val mapping = Json.parseToJsonElement(mock.wireMockMapping).jsonObject
            
            // Extract request details
            val request = mapping["request"]?.jsonObject
                ?: return MockValidationResult.invalid(listOf("Missing request section in WireMock mapping"))
            
            val method = request["method"]?.jsonPrimitive?.content
                ?: return MockValidationResult.invalid(listOf("Missing method in request"))
            
            val urlPath = request["urlPath"]?.jsonPrimitive?.content
                ?: request["url"]?.jsonPrimitive?.content
                ?: return MockValidationResult.invalid(listOf("Missing URL path in request"))
            
            // Normalize path by removing namespace prefix if present
            val prefix = "/${mock.namespace.displayName()}"
            val normalizedPath = if (urlPath.startsWith(prefix)) {
                urlPath.removePrefix(prefix).let { if (it.isEmpty()) "/" else it }
            } else {
                urlPath
            }
            
            // Find matching endpoint in specification
            val endpoint = specification.endpoints.find { 
                val specPath = it.path.let { p -> if (p.startsWith("/")) p else "/$p" }
                val mockPath = normalizedPath.let { p -> if (p.startsWith("/")) p else "/$p" }
                specPath == mockPath && it.method.toString() == method 
            }
            
            if (endpoint == null) {
                errors.add("No matching endpoint found in specification for $method $urlPath")
                return MockValidationResult.invalid(errors)
            }
            
            // Extract response details
            val response = mapping["response"]?.jsonObject
                ?: return MockValidationResult.invalid(listOf("Missing response section in WireMock mapping"))
            
            val statusCode = response["status"]?.jsonPrimitive?.int
                ?: return MockValidationResult.invalid(listOf("Missing status code in response"))
            
            // Check if status code is defined in specification
            val responseDefinition = endpoint.responses[statusCode]
            if (responseDefinition == null) {
                errors.add("Status code $statusCode not defined in specification for $method $urlPath")
                return MockValidationResult.invalid(errors)
            }
            
            // Validate response body against schema if present
            val responseBody = response["jsonBody"] ?: response["body"]
            val responseSchema = responseDefinition.schema
            if (responseBody != null && responseSchema != null) {
                val schemaErrors = validateResponseBodyAgainstSchema(
                    responseBody, 
                    responseSchema,
                    "$method $urlPath - $statusCode"
                )
                errors.addAll(schemaErrors)
            }
            
            // Validate query parameters if present
            val queryParams = request["queryParameters"]?.jsonObject
            if (queryParams != null) {
                val paramErrors = validateQueryParameters(queryParams, endpoint, "$method $urlPath")
                errors.addAll(paramErrors)
            }
            
            // Logical consistency checks (e.g., status=available returns available pets)
            val consistencyErrors = validateConsistency(mock, mapping)
            errors.addAll(consistencyErrors)
            
        }.onFailure { exception ->
            logger.error(exception) { "Validation failed for mock ${mock.id}" }
            errors.add("Validation error: ${exception.message}")
        }
        
        return if (errors.isEmpty()) {
            MockValidationResult.valid()
        } else {
            MockValidationResult.invalid(errors)
        }
    }
    
    /**
     * Validates response body structure against JSON schema.
     */
    private fun validateResponseBodyAgainstSchema(
        responseBody: JsonElement,
        schema: JsonSchema,
        context: String
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Basic type validation
        when (schema.type) {
            JsonSchemaType.OBJECT -> {
                if (responseBody !is JsonObject) {
                    errors.add("$context: Expected object but got ${responseBody::class.simpleName}")
                    return errors
                }
                
                // Validate required properties
                schema.required.forEach { requiredProp ->
                    if (!responseBody.containsKey(requiredProp)) {
                        errors.add("$context: Missing required property '$requiredProp'")
                    }
                }
                
                // Validate property types
                schema.properties.forEach { (propName, propSchema) ->
                    val propValue = responseBody[propName]
                    if (propValue != null) {
                        val propErrors = validateResponseBodyAgainstSchema(
                            propValue, 
                            propSchema, 
                            "$context.$propName"
                        )
                        errors.addAll(propErrors)
                    }
                }
            }
            JsonSchemaType.ARRAY -> {
                if (responseBody !is JsonArray) {
                    errors.add("$context: Expected array but got ${responseBody::class.simpleName}")
                    return errors
                }
                
                // Validate array items if schema is defined
                val itemSchema = schema.items
                if (itemSchema != null) {
                    responseBody.forEachIndexed { index, item ->
                        val itemErrors = validateResponseBodyAgainstSchema(
                            item,
                            itemSchema,
                            "$context[$index]"
                        )
                        errors.addAll(itemErrors)
                    }
                }
            }
            JsonSchemaType.STRING -> {
                if (responseBody !is JsonPrimitive || !responseBody.isString) {
                    errors.add("$context: Expected string but got ${responseBody::class.simpleName}")
                }
            }
            JsonSchemaType.NUMBER, JsonSchemaType.INTEGER -> {
                if (responseBody !is JsonPrimitive || responseBody.isString) {
                    errors.add("$context: Expected number but got ${responseBody::class.simpleName}")
                }
            }
            JsonSchemaType.BOOLEAN -> {
                if (responseBody !is JsonPrimitive || responseBody.content !in listOf("true", "false")) {
                    errors.add("$context: Expected boolean but got ${responseBody::class.simpleName}")
                }
            }
            JsonSchemaType.NULL -> {
                if (responseBody !is JsonNull) {
                    errors.add("$context: Expected null but got ${responseBody::class.simpleName}")
                }
            }
        }
        
        return errors
    }
    
    /**
     * Validates query parameters against endpoint definition.
     */
    private fun validateQueryParameters(
        queryParams: JsonObject,
        endpoint: EndpointDefinition,
        context: String
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Check if query parameters are defined in specification
        queryParams.keys.forEach { paramName ->
            val paramDef = endpoint.parameters.find { 
                it.name == paramName && it.location.name == "QUERY" 
            }
            if (paramDef == null) {
                errors.add("$context: Query parameter '$paramName' not defined in specification")
            }
        }
        
        return errors
    }

    /**
     * Performs logical consistency checks on the generated mock data.
     */
    private fun validateConsistency(mock: GeneratedMock, mapping: JsonObject): List<String> {
        val errors = mutableListOf<String>()
        val request = mapping["request"]?.jsonObject ?: return emptyList()
        val response = mapping["response"]?.jsonObject ?: return emptyList()
        
        // 1. Check Query Parameter Consistency (e.g., status=available)
        val queryParams = request["queryParameters"]?.jsonObject
        val jsonBody = response["jsonBody"] ?: response["body"]
        
        if (queryParams != null && jsonBody != null) {
            queryParams.forEach { (key, value) ->
                // Extract the expected value from WireMock matcher (equalTo, contains, etc.)
                val expectedValue = when (value) {
                    is JsonPrimitive -> value.content
                    is JsonObject -> value["equalTo"]?.jsonPrimitive?.content ?: value["contains"]?.jsonPrimitive?.content
                    else -> null
                }
                
                if (expectedValue != null) {
                    if (jsonBody is JsonArray) {
                        jsonBody.forEachIndexed { i, item ->
                            val actual = item.jsonObject[key]?.jsonPrimitive?.content
                            if (actual != null && actual != expectedValue) {
                                errors.add("Consistency error in item [$i]: query parameter '$key' is '$expectedValue' but response contains '$actual'")
                            }
                        }
                    } else if (jsonBody is JsonObject) {
                        val actual = jsonBody[key]?.jsonPrimitive?.content
                        if (actual != null && actual != expectedValue) {
                            errors.add("Consistency error: query parameter '$key' is '$expectedValue' but response contains '$actual'")
                        }
                    }
                }
            }
        }
        
        // 2. Check Path Parameter Consistency (e.g., /pet/1 matches id: 1)
        val urlPath = request["urlPath"]?.jsonPrimitive?.content ?: request["url"]?.jsonPrimitive?.content ?: ""
        // Simple regex to extract ID from common patterns like /pet/{id} or /user/{id}
        // Looking for numbers or common UUID formats
        val idInPath = Regex(".*/(?:pet|pets|user|users|order|orders|id)/([^/]+)").find(urlPath)?.groupValues?.get(1)
        
        if (idInPath != null && jsonBody is JsonObject) {
            val bodyId = jsonBody["id"]?.jsonPrimitive?.content
            if (bodyId != null && bodyId != idInPath) {
                errors.add("Consistency error: path ID is '$idInPath' but response body has ID '$bodyId'")
            }
        }

        return errors
    }
}
