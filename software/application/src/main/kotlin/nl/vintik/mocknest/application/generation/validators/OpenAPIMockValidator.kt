package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json as KotlinJson
import com.github.tomakehurst.wiremock.common.Json as WireMockJson
import com.github.tomakehurst.wiremock.stubbing.StubMapping
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
 * Uses WireMock's native matching logic for robust URL validation.
 */
@Component
class OpenAPIMockValidator : MockValidatorInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        logger.debug { "Validating mock ${mock.id} against OpenAPI specification" }
        
        val errors = mutableListOf<String>()
        
        val mappingJson = runCatching { KotlinJson.parseToJsonElement(mock.wireMockMapping).jsonObject }.getOrElse {
            errors.add("Validation error: Malformed JSON")
            return reportResults(mock, errors)
        }

        runCatching {
            // 1. Basic structural checks
            val request = mappingJson["request"]?.jsonObject
            if (request == null) {
                errors.add("Missing request section in WireMock mapping")
                return reportResults(mock, errors)
            }
            
            if (!request.containsKey("url") && !request.containsKey("urlPath") && 
                !request.containsKey("urlPattern") && !request.containsKey("urlPathPattern")) {
                errors.add("Missing URL path in request")
            }
            
            if (!request.containsKey("method")) {
                errors.add("Missing method in request")
            }
            
            val responseNode = mappingJson["response"]?.jsonObject
            if (responseNode == null) {
                errors.add("Missing response section in WireMock mapping")
            } else if (!responseNode.containsKey("status")) {
                errors.add("Missing status code in response")
            }
            
            if (errors.isNotEmpty()) return reportResults(mock, errors)

            // 2. Pre-process and Parse the WireMock mapping into StubMapping for advanced matching
            val processedMapping = preProcessMapping(mock.wireMockMapping)
            val stubMapping = WireMockJson.read(processedMapping, StubMapping::class.java)
            val requestPattern = stubMapping.request
            
            // 3. Find matching endpoint in specification using WireMock's matching engine
            val endpoint = specification.endpoints.find { ep: EndpointDefinition ->
                matchesEndpoint(stubMapping, ep, mock.namespace.displayName())
            }
            
            if (endpoint == null) {
                val method = requestPattern.method.toString()
                val urlInfo = requestPattern.urlMatcher.toString()
                errors.add("No matching endpoint found in specification for $method $urlInfo")
                return reportResults(mock, errors)
            }
            
            // 4. Extract and validate response details
            val response = stubMapping.response
            if (response == null) {
                errors.add("Missing response section in WireMock mapping")
                return reportResults(mock, errors)
            }
            val statusCode = response.status
            
            // Check if status code is defined in specification
            val responseDefinition = endpoint.responses[statusCode]
            if (responseDefinition == null) {
                errors.add("Status code $statusCode not defined in specification for ${endpoint.method} ${endpoint.path}")
                return reportResults(mock, errors)
            }
            
            // 5. Validate response body against schema if present
            val responseBodyJson = response.jsonBody?.let { KotlinJson.parseToJsonElement(WireMockJson.write(it)) }
                ?: response.body?.let { KotlinJson.parseToJsonElement(it) }
            
            val responseSchema = responseDefinition.schema
            if (responseBodyJson != null && responseSchema != null) {
                val schemaErrors = validateResponseBodyAgainstSchema(
                    responseBodyJson, 
                    responseSchema,
                    "${endpoint.method} ${endpoint.path} - $statusCode"
                )
                errors.addAll(schemaErrors)
            }
            
            // 6. Validate query parameters if present
            val queryParams = request["queryParameters"]?.jsonObject
            if (queryParams != null) {
                val paramErrors = validateQueryParameters(queryParams, endpoint, "${endpoint.method} ${endpoint.path}")
                errors.addAll(paramErrors)
            }
            
            // 7. Logical consistency checks (Best Effort)
            val consistencyErrors = validateConsistency(mock, mappingJson, endpoint)
            errors.addAll(consistencyErrors)
            
        }.onFailure { exception ->
            val msg = exception.message ?: ""
            if (exception is com.github.tomakehurst.wiremock.common.JsonException) {
                if (msg.contains("response")) errors.add("Missing response section in WireMock mapping")
                else if (msg.contains("status")) errors.add("Missing status code in response")
                else errors.add("Validation error: $msg")
            } else {
                logger.error(exception) { "Validation failed for mock ${mock.id}" }
                errors.add("Validation error: $msg")
            }
        }
        
        return reportResults(mock, errors)
    }

    /**
     * Pre-processes the mapping string to handle common AI mistakes like using 'body' for objects.
     */
    private fun preProcessMapping(wireMockMapping: String): String {
        return runCatching {
            val json = KotlinJson.parseToJsonElement(wireMockMapping).jsonObject
            val response = json["response"]?.jsonObject ?: return wireMockMapping
            
            if (response.containsKey("body") && response["body"] is JsonObject) {
                // Swap body to jsonBody
                val mutableResponse = response.toMutableMap()
                mutableResponse["jsonBody"] = mutableResponse.remove("body")!!
                
                val mutableJson = json.toMutableMap()
                mutableJson["response"] = JsonObject(mutableResponse)
                return KotlinJson.encodeToString(mutableJson)
            }
            wireMockMapping
        }.getOrDefault(wireMockMapping)
    }

    private fun isCatchAll(stub: StubMapping): Boolean {
        val urlMatcher = stub.request.urlMatcher
        return urlMatcher == null || urlMatcher.toString() == "any everything"
    }

    private fun reportResults(mock: GeneratedMock, errors: List<String>): MockValidationResult {
        return if (errors.isEmpty()) {
            MockValidationResult.valid()
        } else {
            logger.warn { "Validation failed for mock ${mock.id}. Errors:\n${errors.joinToString("\n")}" }
            MockValidationResult.invalid(errors)
        }
    }

    /**
     * Uses WireMock's matching logic to see if a stub matches an OpenAPI endpoint definition.
     */
    private fun matchesEndpoint(stub: StubMapping, endpoint: EndpointDefinition, namespace: String): Boolean {
        // Check Method
        if (stub.request.method.toString() != endpoint.method.toString()) return false
        
        // 1. Try matching without prefix
        val specPath = endpoint.path.let { if (it.startsWith("/")) it else "/$it" }
        val samplePathWithoutPrefix = specPath.replace(Regex("\\{[^}]+\\}"), "123")
        if (stub.request.urlMatcher.match(samplePathWithoutPrefix).isExactMatch) return true
        
        // 2. Try matching with namespace prefix
        val prefix = if (namespace.startsWith("/")) namespace else "/$namespace"
        val samplePathWithPrefix = if (samplePathWithoutPrefix.startsWith(prefix)) {
            samplePathWithoutPrefix 
        } else {
            "$prefix$samplePathWithoutPrefix".replace("//", "/")
        }
        
        return stub.request.urlMatcher.match(samplePathWithPrefix).isExactMatch
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
    private fun validateConsistency(
        mock: GeneratedMock, 
        mapping: JsonObject, 
        endpoint: EndpointDefinition
    ): List<String> {
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
                            val actualElement = (item as? JsonObject)?.get(key)
                            if (actualElement != null && !matchesExpectedValue(actualElement, expectedValue)) {
                                val actualStr = if (actualElement is JsonPrimitive) actualElement.content else actualElement.toString()
                                errors.add("Consistency error in item [$i]: query parameter '$key' is '$expectedValue' but response contains '$actualStr'")
                            }
                        }
                    } else if (jsonBody is JsonObject) {
                        val actualElement = jsonBody[key]
                        if (actualElement != null && !matchesExpectedValue(actualElement, expectedValue)) {
                            val actualStr = if (actualElement is JsonPrimitive) actualElement.content else actualElement.toString()
                            errors.add("Consistency error: query parameter '$key' is '$expectedValue' but response contains '$actualStr'")
                        }
                    }
                }
            }
        }
        
        // 2. Check Path Parameter Consistency (e.g., /pet/1 matches id: 1)
        val urlPath = request["urlPath"]?.jsonPrimitive?.content ?: request["url"]?.jsonPrimitive?.content ?: ""
        
        // Find path parameters in endpoint definition
        endpoint.parameters.filter { it.location.name == "PATH" }.forEach { param ->
            val paramName = param.name
            val specPath = endpoint.path
            
            // Find where this param is in the spec path
            val specSegments = specPath.split("/").filter { it.isNotEmpty() }
            val paramIdx = specSegments.indexOfFirst { it == "{$paramName}" }
            
            if (paramIdx != -1) {
                // Try to find the value in the actual mock URL path
                // This is tricky if it's a pattern, but if it's literal we can try.
                // If it's a pattern, we might not be able to easily extract it.
                // Best effort: if urlPath matches the same number of segments as specPath (ignoring namespace)
                val prefix = "/${mock.namespace.displayName()}"
                val normalizedPath = if (urlPath.startsWith(prefix)) {
                    urlPath.removePrefix(prefix).let { if (it.isEmpty()) "/" else it }
                } else {
                    urlPath
                }
                val mockSegments = normalizedPath.split("/").filter { it.isNotEmpty() }
                
                if (mockSegments.size == specSegments.size) {
                    val expectedValue = mockSegments[paramIdx]
                    
                    if (jsonBody is JsonObject) {
                        // Check common ID fields
                        val bodyValue = jsonBody["id"]?.jsonPrimitive?.content 
                            ?: jsonBody[paramName]?.jsonPrimitive?.content
                        
                        if (bodyValue != null && bodyValue != expectedValue) {
                            errors.add("Consistency error: path parameter '$paramName' is '$expectedValue' but response body has value '$bodyValue'")
                        }
                    }
                }
            }
        }

        return errors
    }

    private fun matchesExpectedValue(element: JsonElement, expected: String): Boolean {
        return when (element) {
            is JsonPrimitive -> element.content == expected
            is JsonArray -> element.any { it is JsonPrimitive && it.content == expected }
            else -> false
        }
    }
}
