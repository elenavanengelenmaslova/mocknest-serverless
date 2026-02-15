package nl.vintik.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.AIServiceCapabilities
import nl.vintik.mocknest.domain.generation.*
import kotlin.runCatching
import java.time.Instant

/**
 * Amazon Bedrock implementation of AI model service.
 * Provides AI-powered mock generation using Claude 3 Sonnet.
 */
class BedrockServiceAdapter(
    private val bedrockClient: BedrockRuntimeClient
) : AIModelServiceInterface {
    
    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()
    
    companion object {
        private const val CLAUDE_MODEL_ID = "anthropic.claude-3-sonnet-20240229-v1:0"
        private const val MAX_TOKENS = 4096
        private const val TEMPERATURE = 0.7
    }
    
    override suspend fun generateMockFromDescription(
        description: String,
        namespace: MockNamespace,
        context: Map<String, String>
    ): List<GeneratedMock> {
        logger.info { "Generating mocks from description for namespace: ${namespace.displayName()}" }
        
        val prompt = buildNaturalLanguagePrompt(description, context)
        
        return runCatching {
            val response = invokeClaudeModel(prompt)
            parseClaudeResponse(response, namespace, SourceType.NATURAL_LANGUAGE, description)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to generate mocks from description: $description" }
        }.getOrElse { 
            // Fallback to basic mock generation
            listOf(createFallbackMock(description, namespace))
        }
    }
    
    override suspend fun generateMockFromSpecWithDescription(
        specification: APISpecification,
        description: String,
        namespace: MockNamespace
    ): List<GeneratedMock> {
        logger.info { "Generating enhanced mocks from spec + description for namespace: ${namespace.displayName()}" }
        
        val prompt = buildSpecWithDescriptionPrompt(specification, description)
        
        return runCatching {
            val response = invokeClaudeModel(prompt)
            parseClaudeResponse(response, namespace, SourceType.SPEC_WITH_DESCRIPTION, "${specification.title}: $description")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to generate enhanced mocks for spec: ${specification.title}" }
        }.getOrElse {
            // Fallback to basic spec-based generation would be handled by the calling use case
            emptyList()
        }
    }
    
    override suspend fun refineMock(
        existingMock: GeneratedMock,
        refinementRequest: String
    ): GeneratedMock {
        logger.info { "Refining mock: ${existingMock.name}" }
        
        val prompt = buildRefinementPrompt(existingMock, refinementRequest)
        
        return runCatching {
            val response = invokeClaudeModel(prompt)
            val refinedMocks = parseClaudeResponse(response, existingMock.namespace, SourceType.REFINEMENT, refinementRequest)
            refinedMocks.firstOrNull() ?: existingMock
        }.onFailure { exception ->
            logger.error(exception) { "Failed to refine mock: ${existingMock.name}" }
        }.getOrElse { existingMock }
    }
    
    override suspend fun enhanceResponseRealism(
        mockResponse: String,
        schema: JsonSchema,
        context: Map<String, String>
    ): String {
        logger.debug { "Enhancing response realism" }
        
        val prompt = buildResponseEnhancementPrompt(mockResponse, schema, context)
        
        return runCatching {
            val response = invokeClaudeModel(prompt)
            extractEnhancedResponse(response)
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to enhance response realism, using original" }
        }.getOrElse { mockResponse }
    }
    
    override suspend fun isHealthy(): Boolean {
        return runCatching {
            val testPrompt = "Respond with 'OK' if you can process this request."
            val response = invokeClaudeModel(testPrompt)
            response.contains("OK", ignoreCase = true)
        }.onFailure { exception ->
            logger.warn(exception) { "Bedrock health check failed" }
        }.getOrElse { false }
    }
    
    override fun getCapabilities(): AIServiceCapabilities {
        return AIServiceCapabilities(
            supportsNaturalLanguageGeneration = true,
            supportsSpecEnhancement = true,
            supportsMockRefinement = true,
            supportsResponseEnhancement = true,
            maxInputTokens = 200000, // Claude 3 Sonnet limit
            maxOutputTokens = MAX_TOKENS,
            supportedLanguages = setOf("en", "es", "fr", "de", "it", "pt", "ja", "ko", "zh")
        )
    }
    
    private suspend fun invokeClaudeModel(prompt: String): String {
        val requestBody = mapOf(
            "anthropic_version" to "bedrock-2023-05-31",
            "max_tokens" to MAX_TOKENS,
            "temperature" to TEMPERATURE,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            )
        )
        
        val request = InvokeModelRequest {
            modelId = CLAUDE_MODEL_ID
            contentType = "application/json"
            body = objectMapper.writeValueAsBytes(requestBody)
        }
        
        val response = bedrockClient.invokeModel(request)
        val responseBody = response.body.toString(Charsets.UTF_8)
        
        val responseJson = objectMapper.readTree(responseBody)
        return responseJson.path("content").path(0).path("text").asText()
            ?: throw IllegalStateException("Invalid response format from Claude")
    }
    
    private fun buildNaturalLanguagePrompt(description: String, context: Map<String, String>): String {
        val contextInfo = if (context.isNotEmpty()) {
            "\n\nAdditional Context:\n${context.entries.joinToString("\n") { "${it.key}: ${it.value}" }}"
        } else ""
        
        return """
        You are an expert API mock generator. Generate WireMock JSON mappings based on this description:
        
        Description: $description$contextInfo
        
        Requirements:
        - Generate valid WireMock JSON mapping format
        - Include realistic response data that matches the description
        - Handle appropriate HTTP status codes (default to 200 unless specified)
        - Include relevant headers (Content-Type, CORS headers)
        - Ensure response matches described behavior exactly
        - Generate multiple mappings if the description implies multiple endpoints
        - Use proper URL patterns and request matching
        
        Return only a JSON array of WireMock mappings. Each mapping should be a complete, valid WireMock JSON object.
        Do not include any explanatory text, only the JSON array.
        
        Example format:
        [
          {
            "request": {
              "method": "GET",
              "url": "/api/users"
            },
            "response": {
              "status": 200,
              "headers": {
                "Content-Type": "application/json"
              },
              "body": "[{\"id\":1,\"name\":\"John Doe\"}]"
            }
          }
        ]
        """.trimIndent()
    }
    
    private fun buildSpecWithDescriptionPrompt(specification: APISpecification, description: String): String {
        val specSummary = """
        API Specification Summary:
        - Title: ${specification.title}
        - Version: ${specification.version}
        - Endpoints: ${specification.endpoints.size}
        - Key endpoints: ${specification.endpoints.take(5).joinToString(", ") { "${it.method} ${it.path}" }}
        """.trimIndent()
        
        return """
        You are an expert API mock generator. Generate WireMock JSON mappings based on this API specification and enhancement description:
        
        $specSummary
        
        Enhancement Description: $description
        
        Requirements:
        - Generate WireMock mappings that follow the API specification structure
        - Enhance the mappings based on the description (add error cases, specific data, behaviors, etc.)
        - Include realistic response data that matches both the spec and description
        - Handle appropriate HTTP status codes
        - Include relevant headers and proper content types
        - Generate comprehensive mappings that cover the described scenarios
        
        Return only a JSON array of WireMock mappings. Each mapping should be a complete, valid WireMock JSON object.
        Do not include any explanatory text, only the JSON array.
        """.trimIndent()
    }
    
    private fun buildRefinementPrompt(existingMock: GeneratedMock, refinementRequest: String): String {
        return """
        You are an expert API mock generator. Refine this existing WireMock mapping based on the refinement request:
        
        Existing WireMock Mapping:
        ${existingMock.wireMockMapping}
        
        Refinement Request: $refinementRequest
        
        Requirements:
        - Modify the existing mapping according to the refinement request
        - Maintain the core structure and functionality
        - Ensure the result is still a valid WireMock JSON mapping
        - Only change what's necessary to fulfill the refinement request
        
        Return only the refined WireMock mapping as a JSON object.
        Do not include any explanatory text, only the JSON object.
        """.trimIndent()
    }
    
    private fun buildResponseEnhancementPrompt(mockResponse: String, schema: JsonSchema, context: Map<String, String>): String {
        val contextInfo = if (context.isNotEmpty()) {
            "\n\nContext: ${context.entries.joinToString(", ") { "${it.key}: ${it.value}" }}"
        } else ""
        
        return """
        You are an expert at generating realistic API response data. Enhance this mock response to be more realistic while maintaining the same structure:
        
        Current Response: $mockResponse
        
        Schema Type: ${schema.type}$contextInfo
        
        Requirements:
        - Keep the same JSON structure and field names
        - Make the data more realistic and varied
        - Ensure all data types match the original
        - Add realistic relationships between fields if applicable
        - Keep the response size similar to the original
        
        Return only the enhanced JSON response.
        Do not include any explanatory text, only the JSON.
        """.trimIndent()
    }
    
    private fun parseClaudeResponse(response: String, namespace: MockNamespace, sourceType: SourceType, sourceReference: String): List<GeneratedMock> {
        return try {
            // Try to parse as JSON array first
            val jsonArray = objectMapper.readTree(response)
            
            if (jsonArray.isArray) {
                val mocks = mutableListOf<GeneratedMock>()
                for (i in 0 until jsonArray.size()) {
                    val mappingNode = jsonArray.get(i)
                    val wireMockMapping = objectMapper.writeValueAsString(mappingNode)
                    mocks.add(createGeneratedMock(wireMockMapping, namespace, sourceType, sourceReference, i))
                }
                mocks
            } else {
                // Single mapping
                val wireMockMapping = objectMapper.writeValueAsString(jsonArray)
                listOf(createGeneratedMock(wireMockMapping, namespace, sourceType, sourceReference, 0))
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse Claude response as JSON, attempting text extraction" }
            
            // Try to extract JSON from text response
            val jsonPattern = Regex("""(\[.*\]|\{.*\})""", RegexOption.DOT_MATCHES_ALL)
            val match = jsonPattern.find(response)
            
            if (match != null) {
                try {
                    val jsonArray = objectMapper.readTree(match.value)
                    if (jsonArray.isArray) {
                        val mocks = mutableListOf<GeneratedMock>()
                        for (i in 0 until jsonArray.size()) {
                            val mappingNode = jsonArray.get(i)
                            val wireMockMapping = objectMapper.writeValueAsString(mappingNode)
                            mocks.add(createGeneratedMock(wireMockMapping, namespace, sourceType, sourceReference, i))
                        }
                        mocks
                    } else {
                        val wireMockMapping = objectMapper.writeValueAsString(jsonArray)
                        listOf(createGeneratedMock(wireMockMapping, namespace, sourceType, sourceReference, 0))
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse extracted JSON from Claude response" }
                    emptyList()
                }
            } else {
                logger.error { "No valid JSON found in Claude response: $response" }
                emptyList()
            }
        }
    }
    
    private fun createGeneratedMock(
        wireMockMapping: String, 
        namespace: MockNamespace, 
        sourceType: SourceType, 
        sourceReference: String, 
        index: Int
    ): GeneratedMock {
        // Extract basic info from WireMock mapping for metadata
        val mappingJson = objectMapper.readTree(wireMockMapping)
        val request = mappingJson.path("request")
        val response = mappingJson.path("response")
        
        val method = if (request.has("method")) request.path("method").asText() else "GET"
        val path = if (request.has("url")) {
            request.path("url").asText()
        } else if (request.has("urlPattern")) {
            request.path("urlPattern").asText()
        } else {
            "/unknown"
        }
        val statusCode = if (response.has("status")) response.path("status").asInt() else 200
        val contentType = if (response.path("headers").has("Content-Type")) {
            response.path("headers").path("Content-Type").asText()
        } else {
            "application/json"
        }
        
        return GeneratedMock(
            id = "ai-generated-${namespace.apiName}-${method.lowercase()}-${path.replace("/", "-").replace("{", "").replace("}", "")}-$index",
            name = "AI Generated: $method $path",
            namespace = namespace,
            wireMockMapping = wireMockMapping,
            metadata = MockMetadata(
                sourceType = sourceType,
                sourceReference = sourceReference,
                endpoint = EndpointInfo(
                    method = org.springframework.http.HttpMethod.valueOf(method.uppercase()),
                    path = path,
                    statusCode = statusCode,
                    contentType = contentType
                ),
                tags = setOf("ai-generated", sourceType.name.lowercase(), method.lowercase())
            ),
            generatedAt = Instant.now()
        )
    }
    
    private fun extractEnhancedResponse(response: String): String {
        // Try to extract JSON from the response
        val jsonPattern = Regex("""(\{.*\}|\[.*\])""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(response)
        
        return match?.value ?: response.trim()
    }
    
    private fun createFallbackMock(description: String, namespace: MockNamespace): GeneratedMock {
        // Create a basic mock when AI generation fails
        val fallbackMapping = """
        {
          "request": {
            "method": "GET",
            "url": "/api/fallback"
          },
          "response": {
            "status": 200,
            "headers": {
              "Content-Type": "application/json"
            },
            "body": "{\"message\": \"Fallback mock generated from: $description\"}"
          }
        }
        """.trimIndent()
        
        return GeneratedMock(
            id = "fallback-${System.currentTimeMillis()}",
            name = "Fallback Mock",
            namespace = namespace,
            wireMockMapping = fallbackMapping,
            metadata = MockMetadata(
                sourceType = SourceType.NATURAL_LANGUAGE,
                sourceReference = "Fallback: $description",
                endpoint = EndpointInfo(
                    method = org.springframework.http.HttpMethod.GET,
                    path = "/api/fallback",
                    statusCode = 200,
                    contentType = "application/json"
                ),
                tags = setOf("fallback", "ai-generated")
            )
        )
    }
}