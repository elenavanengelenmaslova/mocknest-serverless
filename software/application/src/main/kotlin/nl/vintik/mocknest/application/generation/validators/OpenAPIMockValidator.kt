package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.domain.generation.*

/**
 * Validates generated mocks against OpenAPI specifications.
 */
class OpenAPIMockValidator : MockValidatorInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        // Only validate OpenAPI/Swagger specifications
        if (specification.format != SpecificationFormat.OPENAPI_3 && specification.format != SpecificationFormat.SWAGGER_2) {
            return MockValidationResult.valid()
        }

        return try {
            val errors = mutableListOf<String>()
            
            val processedMapping = preProcessMapping(mock.wireMockMapping)
            val mappingJson = try {
                Json.parseToJsonElement(processedMapping).jsonObject
            } catch (e: Exception) {
                return MockValidationResult.invalid(listOf("Validation error: Malformed JSON"))
            }

            // 1. Structural checks
            val requestNode = mappingJson["request"]?.jsonObject
            if (requestNode == null) return MockValidationResult.invalid(listOf("Missing request section in WireMock mapping"))
            
            if (!requestNode.containsKey("method")) errors.add("Missing method in request")
            if (!requestNode.containsKey("url") && !requestNode.containsKey("urlPath") && 
                !requestNode.containsKey("urlPattern") && !requestNode.containsKey("urlPathPattern")) {
                errors.add("Missing URL path in request")
            }
            
            val responseNode = mappingJson["response"]?.jsonObject
            if (responseNode == null) {
                errors.add("Missing response section in WireMock mapping")
            } else if (!responseNode.containsKey("status")) {
                errors.add("Missing status code in response")
            }
            
            if (errors.isNotEmpty()) return MockValidationResult.invalid(errors)

            // 2. Find matching endpoint
            val method = requestNode["method"]?.jsonPrimitive?.content ?: ""
            val url = requestNode["url"]?.jsonPrimitive?.content 
                ?: requestNode["urlPath"]?.jsonPrimitive?.content 
                ?: requestNode["urlPathPattern"]?.jsonPrimitive?.content 
                ?: requestNode["urlPattern"]?.jsonPrimitive?.content ?: ""
            
            val endpoint = findEndpoint(method, url, specification, mock.namespace.displayName())
            if (endpoint == null) {
                return MockValidationResult.invalid(listOf("No matching endpoint found in specification for $method $url"))
            }

            // 3. Validate status code
            val status = responseNode!!["status"]?.jsonPrimitive?.intOrNull ?: 200
            val responseDef = endpoint.responses[status]
            if (responseDef == null && status in 200..299) {
                // Only reject undefined success status codes (2xx).
                // Error status codes (4xx, 5xx) are accepted without validation — they are
                // commonly generated for error scenarios even when not explicitly listed in
                // the spec (many specs use a 'default' response to cover error codes).
                return MockValidationResult.invalid(listOf("Status code $status not defined in specification for ${endpoint.method} ${endpoint.path}"))
            }

            // 4. Validate query parameters
            requestNode["queryParameters"]?.jsonObject?.forEach { (name, _) ->
                if (endpoint.parameters.none { it.name == name && it.location == ParameterLocation.QUERY }) {
                    errors.add("${endpoint.method} ${endpoint.path}: Query parameter '$name' not defined in specification")
                }
            }

            // 5. Validate response body schema
            val body = responseNode["jsonBody"] ?: responseNode["body"]
            val schema = responseDef?.schema
            if (body != null && schema != null) {
                errors.addAll(validateResponseBodyAgainstSchema(body, schema, "${endpoint.method} ${endpoint.path} - $status"))
            }

            // 6. Consistency checks
            errors.addAll(validateConsistency(mock, mappingJson, endpoint))

            if (errors.isEmpty()) MockValidationResult.valid() else MockValidationResult.invalid(errors)
        } catch (e: Exception) {
            logger.error(e) { "Validation failed for mock ${mock.id}" }
            MockValidationResult.invalid(listOf("Validation error: ${e.message}"))
        }
    }

    private fun preProcessMapping(wireMockMapping: String): String {
        return runCatching {
            val json = Json.parseToJsonElement(wireMockMapping).jsonObject
            val response = json["response"]?.jsonObject ?: return wireMockMapping
            if (response.containsKey("body") && response["body"] is JsonObject) {
                val mutableResponse = response.toMutableMap()
                mutableResponse["jsonBody"] = mutableResponse.remove("body")!!
                val mutableJson = json.toMutableMap()
                mutableJson["response"] = JsonObject(mutableResponse)
                return Json.encodeToString(JsonObject(mutableJson))
            }
            wireMockMapping
        }.getOrDefault(wireMockMapping)
    }

    private fun findEndpoint(method: String, url: String, spec: APISpecification, namespace: String): EndpointDefinition? {
        val prefix = if (namespace.startsWith("/")) namespace else "/$namespace"
        val normalizedUrl = if (url.startsWith(prefix)) url.substring(prefix.length).let { if (it.isEmpty()) "/" else it } else url
        val normalizedUrlPath = if (normalizedUrl.startsWith("/")) normalizedUrl else "/$normalizedUrl"
        val cleanUrlPath = normalizedUrlPath.split("?")[0]
        
        return spec.endpoints
            .filter { it.method.toString().equals(method, ignoreCase = true) }
            .sortedByDescending { it.path.length }
            .find { ep ->
                val specPath = ep.path.let { if (it.startsWith("/")) it else "/$it" }
                
                // 1. Match normalized URL (as literal) against spec path (as regex)
                // This handles specific IDs in mock URLs (e.g., /pet/1 matches /pet/{petId})
                val specRegex = ("^" + specPath.replace(Regex("\\{[^}]+\\}"), "[^/]+") + "$").toRegex()
                if (specRegex.matches(cleanUrlPath)) return@find true
                
                // 2. Match spec path (as sample) against mock URL (as regex)
                // This handles regex matchers in mocks (e.g., /pet/.* matches /pet/{petId})
                val sample = specPath.replace(Regex("\\{[^}]+\\}"), "123")
                runCatching { cleanUrlPath.toRegex().matches(sample) }.getOrDefault(false)
            }
    }

    private fun validateResponseBodyAgainstSchema(responseBody: JsonElement, schema: JsonSchema, context: String): List<String> {
        val errors = mutableListOf<String>()
        when (schema.type) {
            JsonSchemaType.OBJECT -> {
                if (responseBody !is JsonObject) return listOf("$context: Expected object but got ${responseBody::class.simpleName}")
                schema.required.forEach { if (!responseBody.containsKey(it)) errors.add("$context: Missing required property '$it'") }
                schema.properties.forEach { (name, propSchema) ->
                    responseBody[name]?.let { errors.addAll(validateResponseBodyAgainstSchema(it, propSchema, "$context.$name")) }
                }
            }
            JsonSchemaType.ARRAY -> {
                if (responseBody !is JsonArray) return listOf("$context: Expected array but got ${responseBody::class.simpleName}")
                schema.items?.let { itemSchema ->
                    responseBody.forEachIndexed { i, item -> errors.addAll(validateResponseBodyAgainstSchema(item, itemSchema, "$context[$i]")) }
                }
            }
            JsonSchemaType.STRING -> if (responseBody !is JsonPrimitive || !responseBody.isString) errors.add("$context: Expected string but got ${responseBody::class.simpleName}")
            JsonSchemaType.NUMBER, JsonSchemaType.INTEGER -> if (responseBody !is JsonPrimitive || responseBody.isString) errors.add("$context: Expected number but got ${responseBody::class.simpleName}")
            JsonSchemaType.BOOLEAN -> if (responseBody !is JsonPrimitive || (responseBody.content != "true" && responseBody.content != "false")) errors.add("$context: Expected boolean but got ${responseBody::class.simpleName}")
            JsonSchemaType.NULL -> {}
        }
        return errors
    }

    private fun validateConsistency(mock: GeneratedMock, mapping: JsonObject, endpoint: EndpointDefinition): List<String> {
        val errors = mutableListOf<String>()
        val request = mapping["request"]?.jsonObject ?: return emptyList()
        val response = mapping["response"]?.jsonObject ?: return emptyList()
        
        request["queryParameters"]?.jsonObject?.forEach { (key, value) ->
            val expectedValue = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value["equalTo"]?.jsonPrimitive?.content ?: value["contains"]?.jsonPrimitive?.content
                else -> null
            }
            if (expectedValue != null) {
                val jsonBody = response["jsonBody"] ?: response["body"]
                if (jsonBody is JsonArray) {
                    jsonBody.forEachIndexed { i, item ->
                        val actual = (item as? JsonObject)?.get(key)
                        if (actual != null && !matchesExpectedValue(actual, expectedValue)) errors.add("[CONSISTENCY] Consistency error in item [$i]: query parameter '$key' is '$expectedValue' but response contains '$actual'")
                    }
                } else if (jsonBody is JsonObject) {
                    val actual = jsonBody[key]
                    if (actual != null && !matchesExpectedValue(actual, expectedValue)) errors.add("[CONSISTENCY] Consistency error: query parameter '$key' is '$expectedValue' but response contains '$actual'")
                }
            }
        }
        
        val urlPath = request["urlPath"]?.jsonPrimitive?.content ?: request["url"]?.jsonPrimitive?.content ?: ""
        endpoint.parameters.filter { it.location == ParameterLocation.PATH }.forEach { param ->
            val specSegments = endpoint.path.split("/").filter { it.isNotEmpty() }
            val paramIdx = specSegments.indexOfFirst { it == "{${param.name}}" }
            if (paramIdx != -1) {
                val prefix = "/${mock.namespace.displayName()}"
                val normalizedPath = if (urlPath.startsWith(prefix)) urlPath.removePrefix(prefix).let { if (it.isEmpty()) "/" else it } else urlPath
                val mockSegments = normalizedPath.split("/").filter { it.isNotEmpty() }
                if (mockSegments.size == specSegments.size) {
                    val expectedValue = mockSegments[paramIdx]
                    val jsonBody = response["jsonBody"] ?: response["body"]
                    if (jsonBody is JsonObject) {
                        val bodyValue = (jsonBody[param.name] as? JsonPrimitive)?.content
                            ?: (jsonBody["id"] as? JsonPrimitive)?.content
                        if (bodyValue != null && bodyValue != expectedValue) errors.add("[CONSISTENCY] Consistency error: path parameter '${param.name}' is '$expectedValue' but response body has value '$bodyValue'")
                    }
                }
            }
        }
        return errors
    }

    private fun matchesExpectedValue(element: JsonElement, expected: String): Boolean {
        return when (element) {
            is JsonPrimitive -> element.content == expected
            is JsonArray -> element.any { matchesExpectedValue(it, expected) }
            is JsonObject -> element.values.any { matchesExpectedValue(it, expected) }
            else -> false
        }
    }
}
