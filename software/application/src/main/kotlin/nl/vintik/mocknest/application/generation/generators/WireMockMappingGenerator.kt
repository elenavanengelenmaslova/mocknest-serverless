package nl.vintik.mocknest.application.generation.generators

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.MockGeneratorInterface
import nl.vintik.mocknest.application.generation.interfaces.TestDataGeneratorInterface
import nl.vintik.mocknest.domain.generation.*
import org.springframework.http.HttpMethod

private val logger = KotlinLogging.logger {}
/**
 * Generates WireMock mappings from API specifications.
 * Converts parsed endpoint definitions into valid WireMock JSON format.
 */
class WireMockMappingGenerator(
    private val testDataGenerator: TestDataGeneratorInterface
) : MockGeneratorInterface {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    
    override suspend fun generateFromSpecification(
        specification: APISpecification,
        namespace: MockNamespace,
        options: GenerationOptions
    ): List<GeneratedMock> {
        return specification.endpoints.flatMap { endpoint ->
            generateFromEndpoint(endpoint, namespace, options).let { mock ->
                val mocks = mutableListOf(mock)
                
                // Generate error cases if requested
                if (options.brave) {
                    mocks.addAll(generateErrorCases(endpoint, namespace, options))
                }
                
                mocks
            }
        }
    }
    
    override suspend fun generateFromEndpoint(
        endpoint: EndpointDefinition,
        namespace: MockNamespace,
        options: GenerationOptions
    ): GeneratedMock {
        // Generate success response (200/201)
        val successStatusCode = if (endpoint.method == HttpMethod.POST) 201 else 200
        val successResponse = endpoint.responses[successStatusCode] 
            ?: endpoint.responses.values.firstOrNull()
            ?: ResponseDefinition(successStatusCode, "Success", null)
        
        val responseData = generateResponseData(
            schema = successResponse.schema ?: JsonSchema(JsonSchemaType.OBJECT),
            useExamples = options.brave
        )
        
        val wireMockMapping = createWireMockMapping(endpoint, responseData, successStatusCode)
        
        return GeneratedMock(
            id = generateMockId(endpoint, successStatusCode),
            name = generateMockName(endpoint, successStatusCode),
            namespace = namespace,
            wireMockMapping = wireMockMapping,
            metadata = MockMetadata(
                sourceType = SourceType.SPECIFICATION,
                sourceReference = "${endpoint.method} ${endpoint.path}",
                endpoint = EndpointInfo(
                    method = endpoint.method,
                    path = endpoint.path,
                    statusCode = successStatusCode,
                    contentType = determineContentType(successResponse)
                ),
                tags = setOf("success", "generated", endpoint.method.toString().lowercase())
            )
        )
    }
    
    override suspend fun generateResponseData(
        schema: JsonSchema,
        useExamples: Boolean
    ): String {
        // Use example if available and requested
        if (useExamples && schema.example != null) {
            return objectMapper.writeValueAsString(schema.example)
        }
        
        // Generate realistic data based on schema
        val generatedData = testDataGenerator.generateForSchema(schema)
        return objectMapper.writeValueAsString(generatedData)
    }
    
    override suspend fun createWireMockMapping(
        endpoint: EndpointDefinition,
        responseData: String,
        statusCode: Int
    ): String {
        val mapping = mutableMapOf<String, Any>()
        
        // Request matching
        val request = mutableMapOf<String, Any>()
        request["method"] = endpoint.method.toString()
        
        // URL pattern - convert OpenAPI path parameters to WireMock patterns
        val urlPattern = convertPathToWireMockPattern(endpoint.path)
        if (urlPattern.contains("{") || urlPattern.contains("*")) {
            request["urlPattern"] = urlPattern
        } else {
            request["url"] = urlPattern
        }
        
        // Add query parameter matching if present
        val queryParams = endpoint.parameters.filter { it.location == ParameterLocation.QUERY }
        if (queryParams.isNotEmpty()) {
            val queryParameters = mutableMapOf<String, Any>()
            queryParams.forEach { param ->
                if (param.required) {
                    queryParameters[param.name] = mapOf("matches" to ".*")
                }
            }
            if (queryParameters.isNotEmpty()) {
                request["queryParameters"] = queryParameters
            }
        }
        
        // Add header matching if present
        val headerParams = endpoint.parameters.filter { it.location == ParameterLocation.HEADER }
        if (headerParams.isNotEmpty()) {
            val headers = mutableMapOf<String, Any>()
            headerParams.forEach { param ->
                if (param.required) {
                    headers[param.name] = mapOf("matches" to ".*")
                }
            }
            if (headers.isNotEmpty()) {
                request["headers"] = headers
            }
        }
        
        // Add request body matching for POST/PUT/PATCH
        val requestBody = endpoint.requestBody
        if (requestBody != null && endpoint.method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            val contentType = requestBody.content.keys.firstOrNull() ?: "application/json"
            if (contentType.contains("json")) {
                @Suppress("UNCHECKED_CAST")
                request["headers"] = (request["headers"] as? MutableMap<String, Any> ?: mutableMapOf()).apply {
                    put("Content-Type", mapOf("equalTo" to contentType))
                }
                request["bodyPatterns"] = listOf(
                    mapOf("matchesJsonPath" to "$")
                )
            }
        }
        
        mapping["request"] = request
        
        // Response
        val response = mutableMapOf<String, Any>()
        response["status"] = statusCode
        
        val responseHeaders = mutableMapOf<String, String>()
        val contentType = determineContentType(endpoint.responses[statusCode])
        responseHeaders["Content-Type"] = contentType
        
        // Add CORS headers for browser compatibility
        responseHeaders["Access-Control-Allow-Origin"] = "*"
        responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
        responseHeaders["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
        
        response["headers"] = responseHeaders
        response["body"] = responseData
        
        mapping["response"] = response
        
        // Metadata
        mapping["metadata"] = mapOf(
            "generatedBy" to "MockNest AI Generation",
            "endpoint" to "${endpoint.method} ${endpoint.path}",
            "operationId" to (endpoint.operationId ?: ""),
            "summary" to (endpoint.summary ?: "")
        )
        
        return objectMapper.writeValueAsString(mapping)
    }
    
    override suspend fun validateWireMockMapping(mapping: String): ValidationResult = runCatching {
        val parsedMapping = objectMapper.readTree(mapping)

        val errors = mutableListOf<ValidationError>()

        // Check required fields
        if (!parsedMapping.has("request")) {
            errors.add(ValidationError("Missing required field: request"))
        }
        if (!parsedMapping.has("response")) {
            errors.add(ValidationError("Missing required field: response"))
        }

        // Validate request structure
        parsedMapping.get("request")?.let { request ->
            if (!request.has("method")) {
                errors.add(ValidationError("Missing required field: request.method"))
            }
            if (!request.has("url") && !request.has("urlPattern")) {
                errors.add(ValidationError("Missing required field: request.url or request.urlPattern"))
            }
        }

        // Validate response structure
        parsedMapping.get("response")?.let { response ->
            if (!response.has("status")) {
                errors.add(ValidationError("Missing required field: response.status"))
            }
        }

        if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(errors)
        }
    }.onFailure { e ->
        logger.warn(e) { "Validation of WireMock mapping failed" }
    }.getOrElse { e ->
        ValidationResult.invalid(
            listOf(
                ValidationError("Invalid JSON format: ${e.message}")
            )
        )
    }
    
    private suspend fun generateErrorCases(
        endpoint: EndpointDefinition,
        namespace: MockNamespace,
        options: GenerationOptions
    ): List<GeneratedMock> {
        val errorMocks = mutableListOf<GeneratedMock>()
        
        // Generate common error responses
        val errorCases = listOf(
            400 to "Bad Request",
            401 to "Unauthorized", 
            403 to "Forbidden",
            404 to "Not Found",
            500 to "Internal Server Error"
        )
        
        errorCases.forEach { (statusCode, description) ->
            // Only generate if not already defined in spec
            if (!endpoint.responses.containsKey(statusCode)) {
                val errorResponse = ResponseDefinition(
                    statusCode = statusCode,
                    description = description,
                    schema = JsonSchema(
                        type = JsonSchemaType.OBJECT,
                        properties = mapOf(
                            "error" to JsonSchema(JsonSchemaType.STRING),
                            "message" to JsonSchema(JsonSchemaType.STRING),
                            "code" to JsonSchema(JsonSchemaType.INTEGER)
                        )
                    )
                )
                
                val errorData = generateErrorResponseData(statusCode, description)
                val wireMockMapping = createWireMockMapping(endpoint, errorData, statusCode)
                
                errorMocks.add(GeneratedMock(
                    id = generateMockId(endpoint, statusCode),
                    name = generateMockName(endpoint, statusCode),
                    namespace = namespace,
                    wireMockMapping = wireMockMapping,
                    metadata = MockMetadata(
                        sourceType = SourceType.SPECIFICATION,
                        sourceReference = "${endpoint.method} ${endpoint.path} (error case)",
                        endpoint = EndpointInfo(
                            method = endpoint.method,
                            path = endpoint.path,
                            statusCode = statusCode,
                            contentType = "application/json"
                        ),
                        tags = setOf("error", "generated", endpoint.method.toString().lowercase(), "status-$statusCode")
                    )
                ))
            }
        }
        
        return errorMocks
    }
    
    private fun generateErrorResponseData(statusCode: Int, description: String): String {
        val errorData = mapOf(
            "error" to description,
            "message" to getErrorMessage(statusCode),
            "code" to statusCode,
            "timestamp" to System.currentTimeMillis()
        )
        return objectMapper.writeValueAsString(errorData)
    }
    
    private fun getErrorMessage(statusCode: Int): String = when (statusCode) {
        400 -> "The request was invalid or malformed"
        401 -> "Authentication is required to access this resource"
        403 -> "You do not have permission to access this resource"
        404 -> "The requested resource was not found"
        500 -> "An internal server error occurred"
        else -> "An error occurred while processing the request"
    }
    
    private fun convertPathToWireMockPattern(path: String): String {
        // Convert OpenAPI path parameters {id} to WireMock patterns
        return path.replace(Regex("\\{([^}]+)\\}")) { matchResult ->
            val paramName = matchResult.groupValues[1]
            "([^/]+)" // Match any non-slash characters
        }
    }
    
    private fun determineContentType(response: ResponseDefinition?): String {
        return response?.let { resp ->
            val schema = resp.schema
            // Look for JSON schema indicators
            if (schema?.type == JsonSchemaType.OBJECT || schema?.type == JsonSchemaType.ARRAY) {
                "application/json"
            } else if (schema?.type == JsonSchemaType.STRING && schema.format == "binary") {
                "application/octet-stream"
            } else {
                "application/json" // Default to JSON
            }
        } ?: "application/json"
    }
    
    private fun generateMockId(endpoint: EndpointDefinition, statusCode: Int): String {
        val operationId = endpoint.operationId ?: "${endpoint.method.toString().lowercase()}-${endpoint.path.replace("/", "-").replace("{", "").replace("}", "")}"
        return "$operationId-$statusCode"
    }
    
    private fun generateMockName(endpoint: EndpointDefinition, statusCode: Int): String {
        val baseName = endpoint.summary ?: "${endpoint.method} ${endpoint.path}"
        return if (statusCode in 200..299) {
            baseName
        } else {
            "$baseName ($statusCode)"
        }
    }
}