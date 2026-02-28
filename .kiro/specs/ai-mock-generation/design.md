# Design Document: AI Mock Generation - Phase 1

## Overview

Phase 1 of the AI Mock Generation feature establishes the foundation for AI-powered mock creation using the Koog 0.6.2 framework, Kotlin implementation, and Amazon Bedrock integration. This phase focuses on validating the Bedrock integration and implementing core OpenAPI-based mock generation with synchronous response in WireMock import format.

**Phase 1 Goals:**
1. **Hello World Endpoint**: Validate Bedrock + Koog integration with simple text processing
2. **OpenAPI Mock Generation**: Generate WireMock-ready mocks from OpenAPI 3.0 and Swagger 2.0 specifications
3. **Mock Validation**: Validate generated mocks against OpenAPI specifications with automatic retry for invalid mocks
4. **Synchronous Response**: Return generated mocks in WireMock import JSON format (array of mappings)
5. **Standard WireMock Integration**: Users apply mocks using standard `POST /__admin/mappings/import` endpoint

**Deferred to Future Spec (Mock Evolution):**
- Specification storage and versioning
- Detecting API changes and updating mocks
- Namespace storage organization
- Mock evolution based on traffic patterns

**Deferred to Future Phases:**
- Mock enhancement and refinement using AI
- Traffic analysis integration
- GraphQL and WSDL support
- Batch generation
- Conversational interfaces (MCP)

## Architecture

### High-Level Architecture - Phase 1

```mermaid
flowchart TB
    subgraph Input["Input Sources"]
        SPEC[OpenAPI Specifications]
        TEXT[Text Input - Hello World]
    end
    
    subgraph APIs["MockNest APIs - Phase 1"]
        ADMIN[WireMock Admin API<br/>/__admin/*]
        HELLO[Hello World API<br/>/ai/hello]
        AIGEN[AI Generation API<br/>/ai/generation/from-spec]
    end
    
    subgraph AIGeneration["AI Mock Generation Engine - Phase 1"]
        KOOG[Koog Functional Agent 0.6.0]
        PARSER[OpenAPI Parser Only]
        GENERATOR[Mock Generator]
        
        KOOG --> PARSER
        KOOG --> GENERATOR
    end
    
    subgraph CloudAI["Amazon Bedrock"]
        BEDROCK[Foundation Models]
        CLAUDE[Claude 3 Sonnet]
    end
    
    subgraph WireMock["WireMock Runtime"]
        RUNTIME[WireMock Server]
        ENDPOINTS[Mock Endpoints]
        MOCKSTORE[(WireMock Runtime Storage)]
    end
    
    TEXT --> HELLO
    SPEC --> AIGEN
    
    HELLO --> KOOG
    AIGEN --> KOOG
    KOOG <--> BEDROCK
    
    AIGEN -.->|Returns mocks in WireMock import format| Input
    Input -.->|User applies via standard WireMock API| ADMIN
    ADMIN --> MOCKSTORE
    MOCKSTORE --> RUNTIME
    RUNTIME --> ENDPOINTS
```

### Clean Architecture Implementation - Phase 1

Following the established clean architecture pattern with strict dependency rules:

**Domain Layer:**
- `MockGenerationRequest` - Request for generating mocks from OpenAPI specifications
- `GeneratedMock` - Domain model representing a generated mock in WireMock format
- `APISpecification` - Domain model for parsed OpenAPI specifications
- `GenerationOptions` - Configuration options for mock generation

**Application Layer:**
- `HelloWorldUseCase` - Validate Bedrock integration with simple text processing
- `GenerateMocksFromSpecUseCase` - Generate mocks from OpenAPI specifications synchronously
- `KoogMockGenerationAgent` - **Koog 0.6.0 Functional Agent implementation (cloud-independent)**
- `SpecificationParserInterface` - Abstraction for parsing OpenAPI formats
- `MockGeneratorInterface` - Abstraction for mock generation logic
- `MockValidatorInterface` - **Abstraction for validating generated mocks against OpenAPI specifications**
- `AIModelServiceInterface` - **Abstraction for AI model interactions (hides Bedrock)**

**Infrastructure Layer:**
- `BedrockServiceAdapter` - **Amazon Bedrock integration (implements AIModelServiceInterface)**
- `OpenAPISpecificationParser` - OpenAPI 3.0 and Swagger 2.0 specification parsing
- `OpenAPIMockValidator` - **Validates generated mocks against OpenAPI specifications**

**Phase 1 Simplifications:**
- No storage layer for specifications or generated mocks
- No job tracking or async processing
- No namespace storage organization
- Stateless synchronous generation only
- Generated mocks returned in WireMock import JSON format for user review and application

### Clean Architecture Dependency Rules

```mermaid
flowchart TB
    subgraph Domain["Domain Layer"]
        MODELS[Domain Models]
    end
    
    subgraph Application["Application Layer"]
        USECASES[Use Cases]
        KOOG[Koog Agent - Cloud Independent]
        INTERFACES[Service Interfaces]
    end
    
    subgraph Infrastructure["Infrastructure Layer"]
        BEDROCK[Bedrock Adapter]
        PARSERS[Specification Parsers]
        WIREMOCK[WireMock Admin Adapter]
    end
    
    USECASES --> MODELS
    KOOG --> INTERFACES
    KOOG --> MODELS
    INTERFACES --> MODELS
    
    BEDROCK --> INTERFACES
    PARSERS --> INTERFACES
    WIREMOCK --> INTERFACES
    
    BEDROCK -.->|Hidden Implementation| KOOG
```

**Key Architectural Principles:**
1. **Bedrock Abstraction**: `AIModelServiceInterface` in application layer hides Bedrock implementation
2. **Cloud-Independent Koog Agent**: Lives in application layer, uses abstractions only
3. **Dependency Inversion**: Infrastructure implements application interfaces
4. **No Cloud Coupling**: Domain and application layers have no AWS dependencies
5. **Stateless Design**: No storage layer needed for Phase 1 synchronous generation

## Components and Interfaces

### Core Components - Phase 1

#### 1. Hello World Endpoint (Bedrock Validation)

**Purpose**: Validate that Bedrock integration works correctly through Koog before building complex generation features.

```kotlin
@Component
class HelloWorldUseCase(
    private val aiModelService: AIModelServiceInterface
) {
    private val logger = KotlinLogging.logger {}
    
    suspend fun invoke(textInput: String): HelloWorldResponse {
        logger.info { "Processing hello world request with input: ${textInput.take(50)}..." }
        
        return runCatching {
            val response = aiModelService.processHelloWorld(textInput)
            HelloWorldResponse.success(response)
        }.onFailure { exception ->
            logger.error(exception) { "Hello world request failed" }
        }.getOrElse { exception ->
            HelloWorldResponse.error("Bedrock service unavailable: ${exception.message}")
        }
    }
}

data class HelloWorldResponse(
    val success: Boolean,
    val response: String?,
    val error: String?
) {
    companion object {
        fun success(response: String) = HelloWorldResponse(true, response, null)
        fun error(message: String) = HelloWorldResponse(false, null, message)
    }
}
```

#### 2. Koog Functional Agent Framework Integration (0.6.0) - Phase 1

**Agent Type: Functional Agent**
- **Domain-Specific**: Mock generation is a well-defined functional domain
- **Tool Coordination**: Orchestrates parsers and generators
- **Structured I/O**: Takes OpenAPI specs, produces WireMock JSON
- **Domain Expertise**: Encapsulates knowledge about OpenAPI and mock generation patterns

```kotlin
@Component
class MockGenerationFunctionalAgent(
    private val specificationParser: SpecificationParserInterface,
    private val mockGenerator: MockGeneratorInterface
) : FunctionalAgent {
    
    override val domain = "mock-generation"
    override val capabilities = setOf(
        "parse-openapi-specifications",
        "generate-wiremock-mappings",
        "scenario-based-generation"
    )
    
    override suspend fun execute(request: AgentRequest): AgentResponse {
        return when (request.type) {
            RequestType.SPECIFICATION_GENERATION -> generateFromSpec(request)
            else -> AgentResponse.error("Unsupported request type in Phase 1: ${request.type}")
        }
    }
    
    private suspend fun generateFromSpec(request: AgentRequest): AgentResponse {
        val specification = specificationParser.parse(
            request.specificationContent, 
            request.format
        )
        
        val generatedMocks = mockGenerator.generateFromSpecification(
            specification = specification,
            namespace = request.namespace,
            options = request.options
        )
        
        return AgentResponse.success(generatedMocks)
    }
}
```

**Phase 1 Limitations:**
- Only supports `SPECIFICATION_GENERATION` request type
- No natural language generation (beyond hello world)
- No mock evolution or enhancement
- Single specification processing only

#### 3. Use Case Layer - Phase 1

```kotlin
@Component
class GenerateMocksFromSpecUseCase(
    private val specificationParser: SpecificationParserInterface,
    private val mockGenerator: MockGeneratorInterface,
    private val mockValidator: MockValidatorInterface,
    private val mockGenerationAgent: MockGenerationFunctionalAgent,
    private val aiModelService: AIModelServiceInterface
) : GenerateMocksFromSpec {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun invoke(request: MockGenerationRequest): GenerationResponse {
        logger.info { "Starting synchronous mock generation for ${request.specificationUrl ?: "inline spec"}" }
        
        // Parse specification
        val specification = specificationParser.parse(
            request.specificationContent,
            request.format
        )
        
        // Generate mocks
        val agentRequest = AgentRequest.fromSpec(
            specification = specification,
            instructions = request.instructions,
            options = request.options
        )
        val agentResponse = mockGenerationAgent.execute(agentRequest)
        
        logger.info { "Generated ${agentResponse.mocks.size} mocks, starting validation" }
        
        // Validate and fix mocks
        val validatedMocks = validateAndFixMocks(agentResponse.mocks, specification)
        
        logger.info { "Mock generation completed: valid=${validatedMocks.size}, discarded=${agentResponse.mocks.size - validatedMocks.size}" }
        
        return GenerationResponse(
            mocks = validatedMocks,
            totalGenerated = validatedMocks.size
        )
    }
    
    private suspend fun validateAndFixMocks(
        mocks: List<GeneratedMock>,
        specification: APISpecification
    ): List<GeneratedMock> {
        // Validate all mocks
        val validationResults = mocks.map { mock ->
            mock to mockValidator.validate(mock, specification)
        }
        
        val validMocks = validationResults.filter { it.second.isValid }.map { it.first }.toMutableList()
        val invalidMocks = validationResults.filter { !it.second.isValid }
        
        logger.info { "Initial validation: valid=${validMocks.size}, invalid=${invalidMocks.size}" }
        
        // Log all validation failures
        invalidMocks.forEach { (mock, result) ->
            logger.warn { "Mock ${mock.id} failed validation: ${result.errors.joinToString(", ")}" }
        }
        
        // If there are invalid mocks, attempt batch correction
        if (invalidMocks.isNotEmpty()) {
            logger.info { "Attempting batch correction for ${invalidMocks.size} invalid mocks" }
            
            val correctedMocks = attemptBatchMockCorrection(invalidMocks, specification)
            
            if (correctedMocks.isNotEmpty()) {
                validMocks.addAll(correctedMocks)
                logger.info { "Successfully corrected ${correctedMocks.size} mocks" }
            }
            
            val stillInvalid = invalidMocks.size - correctedMocks.size
            if (stillInvalid > 0) {
                logger.warn { "Failed to correct $stillInvalid mocks, excluding from response" }
            }
        }
        
        return validMocks
    }
    
    private suspend fun attemptBatchMockCorrection(
        invalidMocks: List<Pair<GeneratedMock, ValidationResult>>,
        specification: APISpecification
    ): List<GeneratedMock> {
        return runCatching {
            // Build batch correction prompt with all invalid mocks and their errors
            val batchCorrectionPrompt = buildBatchCorrectionPrompt(invalidMocks)
            
            logger.debug { "Sending ${invalidMocks.size} mocks to AI for batch correction" }
            logger.debug { "Batch correction prompt: ${batchCorrectionPrompt.take(500)}..." }
            
            // Send to AI for batch correction
            val correctedMocksJson = aiModelService.correctMocks(batchCorrectionPrompt)
            
            // Parse corrected mocks (expecting JSON array)
            val correctedMocksList = Json.parseToJsonElement(correctedMocksJson).jsonArray
            
            logger.info { "Received ${correctedMocksList.size} corrected mocks from AI" }
            
            // Validate each corrected mock
            val validCorrectedMocks = mutableListOf<GeneratedMock>()
            
            correctedMocksList.forEachIndexed { index, correctedMockJson ->
                if (index < invalidMocks.size) {
                    val (originalMock, _) = invalidMocks[index]
                    
                    val correctedMock = GeneratedMock(
                        id = originalMock.id,
                        name = originalMock.name,
                        wireMockMapping = correctedMockJson.toString(),
                        metadata = originalMock.metadata
                    )
                    
                    // Validate the corrected mock
                    val correctedValidation = mockValidator.validate(correctedMock, specification)
                    
                    if (correctedValidation.isValid) {
                        validCorrectedMocks.add(correctedMock)
                        logger.info { "Mock ${originalMock.id} correction successful" }
                    } else {
                        logger.warn { "Mock ${originalMock.id} correction still failed validation: ${correctedValidation.errors.joinToString(", ")}" }
                    }
                }
            }
            
            validCorrectedMocks
        }.onFailure { exception ->
            logger.error(exception) { "Error during batch mock correction" }
        }.getOrElse { emptyList() }
    }
    
    private fun buildBatchCorrectionPrompt(
        invalidMocks: List<Pair<GeneratedMock, ValidationResult>>
    ): String {
        val mocksWithErrors = invalidMocks.mapIndexed { index, (mock, result) ->
            """
            Mock ${index + 1}: ${mock.name} (ID: ${mock.id})
            Validation Errors:
            ${result.errors.joinToString("\n") { "  - $it" }}
            
            Invalid WireMock Mapping:
            ${mock.wireMockMapping}
            """.trimIndent()
        }.joinToString("\n\n---\n\n")
        
        return """
        The following ${invalidMocks.size} WireMock mappings failed validation. Please correct ALL of them to fix their respective errors.
        
        $mocksWithErrors
        
        Please return a JSON array containing ONLY the corrected WireMock mapping JSONs in the same order, one for each mock above.
        
        Format: [
          { corrected mock 1 },
          { corrected mock 2 },
          ...
        ]
        
        For each mock, ensure:
        - The request matcher (method, URL path, query parameters) matches the OpenAPI specification
        - The response status code is defined in the specification for this endpoint
        - The response body structure matches the OpenAPI schema exactly
        - All required fields are present
        - Data types match the schema (strings, numbers, booleans, arrays, objects)
        """.trimIndent()
    }
}

data class GenerationResponse(
    val mocks: List<Map<String, Any>>,   // Array of complete WireMock mappings in import format
    val totalGenerated: Int
)
```

#### 4. Specification Parsing - Phase 1 (OpenAPI Only)

```kotlin
interface SpecificationParserInterface {
    suspend fun parse(content: String, format: SpecificationFormat): APISpecification
    fun supports(format: SpecificationFormat): Boolean
}

@Component
class OpenAPISpecificationParser : SpecificationParserInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun parse(content: String, format: SpecificationFormat): APISpecification {
        logger.info { "Parsing OpenAPI specification, format=$format" }
        
        return runCatching {
            val openApiSpec = OpenAPIV3Parser().readContents(content)
            
            checkNotNull(openApiSpec.openAPI) { 
                "Failed to parse OpenAPI specification: ${openApiSpec.messages}" 
            }
            
            APISpecification.fromOpenAPI(openApiSpec.openAPI)
        }.onFailure { exception ->
            logger.error(exception) { "OpenAPI parsing failed" }
        }.getOrThrow()
    }
    
    override fun supports(format: SpecificationFormat): Boolean = 
        format in listOf(SpecificationFormat.OPENAPI_3, SpecificationFormat.SWAGGER_2)
}
```

**Phase 1 Limitations:**
- Only OpenAPI 3.0 and Swagger 2.0 support
- No GraphQL schema parsing
- No WSDL parsing
- No composite parser for multiple formats

#### 5. Mock Generation Logic - Phase 1

```kotlin
interface MockGeneratorInterface {
    suspend fun generateFromSpecification(
        specification: APISpecification,
        instructions: String?,
        options: GenerationOptions
    ): List<GeneratedMock>
}

@Component
class WireMockMappingGenerator : MockGeneratorInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun generateFromSpecification(
        specification: APISpecification,
        instructions: String?,
        options: GenerationOptions
    ): List<GeneratedMock> {
        logger.info { "Generating mocks for ${specification.endpoints.size} endpoints" }
        
        val mocks = mutableListOf<GeneratedMock>()
        
        specification.endpoints.forEach { endpoint ->
            // Generate happy path mock (2xx)
            endpoint.responses.filter { it.key in 200..299 }.forEach { (statusCode, response) ->
                mocks.add(generateMock(endpoint, statusCode, response, "happy-path"))
            }
            
            // Generate error case mocks if requested
            if (options.generateErrorCases) {
                // Client errors (4xx)
                endpoint.responses.filter { it.key in 400..499 }.forEach { (statusCode, response) ->
                    mocks.add(generateMock(endpoint, statusCode, response, "client-error"))
                }
                
                // Server errors (5xx)
                endpoint.responses.filter { it.key in 500..599 }.forEach { (statusCode, response) ->
                    mocks.add(generateMock(endpoint, statusCode, response, "server-error"))
                }
            }
        }
        
        logger.info { "Generated ${mocks.size} mocks" }
        return mocks
    }
    
    private fun generateMock(
        endpoint: EndpointDefinition,
        statusCode: Int,
        response: ResponseDefinition,
        scenario: String
    ): GeneratedMock {
        val mockId = generateMockId(endpoint, statusCode)
        val wireMockJson = buildWireMockMapping(endpoint, statusCode, response)
        
        return GeneratedMock(
            id = mockId,
            name = "${endpoint.method} ${endpoint.path} - $statusCode",
            wireMockMapping = wireMockJson,
            metadata = MockMetadata(
                sourceType = "SPECIFICATION",
                scenario = scenario,
                endpoint = endpoint.path,
                method = endpoint.method.name,
                statusCode = statusCode
            )
        )
    }
    
    private fun buildWireMockMapping(
        endpoint: EndpointDefinition,
        statusCode: Int,
        response: ResponseDefinition
    ): String {
        // Build WireMock JSON format
        val mapping = mapOf(
            "request" to mapOf(
                "method" to endpoint.method.name,
                "urlPath" to endpoint.path
            ),
            "response" to mapOf(
                "status" to statusCode,
                "headers" to mapOf("Content-Type" to "application/json"),
                "body" to generateResponseBody(response)
            )
        )
        
        return Json.encodeToString(mapping)
    }
    
    private fun generateResponseBody(response: ResponseDefinition): String {
        // Use example if available, otherwise generate from schema
        return response.examples.values.firstOrNull()?.toString()
            ?: generateFromSchema(response.schema)
    }
}
```

#### 6. Mock Validation - Phase 1

```kotlin
interface MockValidatorInterface {
    suspend fun validate(mock: GeneratedMock, specification: APISpecification): ValidationResult
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun valid() = ValidationResult(true, emptyList())
        fun invalid(errors: List<String>) = ValidationResult(false, errors)
    }
}

@Component
class OpenAPIMockValidator : MockValidatorInterface {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): ValidationResult {
        logger.debug { "Validating mock ${mock.id} against OpenAPI specification" }
        
        val errors = mutableListOf<String>()
        
        runCatching {
            // Parse the WireMock mapping
            val mapping = Json.parseToJsonElement(mock.wireMockMapping).jsonObject
            
            // Extract request details
            val request = mapping["request"]?.jsonObject
                ?: return ValidationResult.invalid(listOf("Missing request section in WireMock mapping"))
            
            val method = request["method"]?.jsonPrimitive?.content
                ?: return ValidationResult.invalid(listOf("Missing method in request"))
            
            val urlPath = request["urlPath"]?.jsonPrimitive?.content
                ?: request["url"]?.jsonPrimitive?.content
                ?: return ValidationResult.invalid(listOf("Missing URL path in request"))
            
            // Find matching endpoint in specification
            val endpoint = specification.endpoints.find { 
                it.path == urlPath && it.method.name == method 
            }
            
            if (endpoint == null) {
                errors.add("No matching endpoint found in specification for $method $urlPath")
                return ValidationResult.invalid(errors)
            }
            
            // Extract response details
            val response = mapping["response"]?.jsonObject
                ?: return ValidationResult.invalid(listOf("Missing response section in WireMock mapping"))
            
            val statusCode = response["status"]?.jsonPrimitive?.int
                ?: return ValidationResult.invalid(listOf("Missing status code in response"))
            
            // Check if status code is defined in specification
            val responseDefinition = endpoint.responses[statusCode]
            if (responseDefinition == null) {
                errors.add("Status code $statusCode not defined in specification for $method $urlPath")
                return ValidationResult.invalid(errors)
            }
            
            // Validate response body against schema if present
            val responseBody = response["jsonBody"] ?: response["body"]
            if (responseBody != null && responseDefinition.schema != null) {
                val schemaErrors = validateResponseBodyAgainstSchema(
                    responseBody, 
                    responseDefinition.schema,
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
            
        }.onFailure { exception ->
            logger.error(exception) { "Validation failed for mock ${mock.id}" }
            errors.add("Validation error: ${exception.message}")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(errors)
        }
    }
    
    private fun validateResponseBodyAgainstSchema(
        responseBody: JsonElement,
        schema: JsonSchema,
        context: String
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Basic type validation
        when (schema.type) {
            "object" -> {
                if (responseBody !is JsonObject) {
                    errors.add("$context: Expected object but got ${responseBody::class.simpleName}")
                    return errors
                }
                
                // Validate required properties
                schema.required?.forEach { requiredProp ->
                    if (!responseBody.containsKey(requiredProp)) {
                        errors.add("$context: Missing required property '$requiredProp'")
                    }
                }
                
                // Validate property types
                schema.properties?.forEach { (propName, propSchema) ->
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
            "array" -> {
                if (responseBody !is JsonArray) {
                    errors.add("$context: Expected array but got ${responseBody::class.simpleName}")
                }
            }
            "string" -> {
                if (responseBody !is JsonPrimitive || !responseBody.isString) {
                    errors.add("$context: Expected string but got ${responseBody::class.simpleName}")
                }
            }
            "number", "integer" -> {
                if (responseBody !is JsonPrimitive || responseBody.isString) {
                    errors.add("$context: Expected number but got ${responseBody::class.simpleName}")
                }
            }
            "boolean" -> {
                if (responseBody !is JsonPrimitive || responseBody.content !in listOf("true", "false")) {
                    errors.add("$context: Expected boolean but got ${responseBody::class.simpleName}")
                }
            }
        }
        
        return errors
    }
    
    private fun validateQueryParameters(
        queryParams: JsonObject,
        endpoint: EndpointDefinition,
        context: String
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Check if query parameters are defined in specification
        queryParams.keys.forEach { paramName ->
            val paramDef = endpoint.parameters.find { it.name == paramName && it.location == "query" }
            if (paramDef == null) {
                errors.add("$context: Query parameter '$paramName' not defined in specification")
            }
        }
        
        return errors
    }
}
```

#### 7. WireMock Import Format Conversion - Phase 1

Generated mocks are returned in WireMock import JSON format (array of mappings), with each mapping already containing:
- **UUID**: Unique identifier for the mapping
- **Priority**: Set to 2 for all generated mocks
- **Persistent**: Set to true to ensure mocks survive Lambda cold starts

```kotlin
// Example of generated mock in WireMock import format
data class GenerationResponse(
    val mocks: List<Map<String, Any>>,  // Array of complete WireMock mappings
    val totalGenerated: Int
)

// Each mock in the array is a complete WireMock mapping:
{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18347",
  "priority": 2,
  "persistent": true,
  "request": {
    "method": "GET",
    "urlPath": "/activity"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "activity": "Learn a new programming language",
      "type": "education",
      "participants": 1
    }
  }
}toString())
```

Users can then review this JSON array and import selected mocks using the standard WireMock admin API:
```bash
curl -X POST http://mocknest-url/__admin/mappings/import \
  -H "Content-Type: application/json" \
  -d '{"mappings": [...]}'  # Array of selected mocks from generation response
```

**Phase 1 Limitations:**
- Only hello world text processing for Bedrock validation
- No natural language mock generation
- No mock refinement or enhancement
- Future phases will expand this interface

## Data Models

### Domain Models - Phase 1

#### Core Generation Models
```kotlin
data class MockGenerationRequest(
    val spec: SpecificationSource,
    val instructions: String? = null,        // Optional user instructions for customization
    val options: GenerationOptions = GenerationOptions.default()
)

data class SpecificationSource(
    val url: String? = null,                 // URL to fetch specification
    val content: String? = null,             // Inline specification content
    val format: SpecificationFormat = SpecificationFormat.OPENAPI_3
) {
    init {
        require(url != null || content != null) { 
            "Either url or content must be provided" 
        }
    }
}

data class GeneratedMock(
    val id: String,
    val name: String,
    val wireMockMapping: String,             // JSON string in WireMock format
    val metadata: MockMetadata,
    val generatedAt: Instant = Instant.now()
)

data class MockMetadata(
    val sourceType: String,                  // "SPECIFICATION" for Phase 1
    val scenario: String,                    // "happy-path", "client-error", "server-error"
    val endpoint: String,
    val method: String,
    val statusCode: Int
)

data class GenerationOptions(
    val includeExamples: Boolean = true,
    val generateErrorCases: Boolean = true,
    val realisticData: Boolean = true,
    val maxMappings: Int? = null             // Optional limit on number of mocks
) {
    companion object {
        fun default() = GenerationOptions()
    }
}

enum class SpecificationFormat {
    OPENAPI_3,
    SWAGGER_2
    // Future: GRAPHQL, WSDL
}
```

#### API Specification Models
```kotlin
data class APISpecification(
    val format: SpecificationFormat,
    val version: String,
    val title: String,
    val endpoints: List<EndpointDefinition>,
    val schemas: Map<String, JsonSchema>,
    val metadata: Map<String, String> = emptyMap()
)

data class EndpointDefinition(
    val path: String,
    val method: HttpMethod,
    val operationId: String?,
    val summary: String?,
    val parameters: List<ParameterDefinition>,
    val requestBody: RequestBodyDefinition?,
    val responses: Map<Int, ResponseDefinition>
)

data class ResponseDefinition(
    val statusCode: Int,
    val description: String,
    val schema: JsonSchema?,
    val examples: Map<String, Any> = emptyMap(),
    val headers: Map<String, HeaderDefinition> = emptyMap()
)
```

**Phase 1 Simplifications:**
- No `GenerationJob` model (synchronous generation only)
- No `GenerationResults` model (returned directly in response)
- No `MockNamespace` model (simple string namespace for brave mode)
- No storage-related models

### Storage Organization

**Phase 1: No Storage Layer**

Phase 1 uses a stateless synchronous generation approach with no storage of specifications or generated mocks:

- Generated mocks are returned directly in HTTP response
- No job tracking or status storage
- No specification versioning or storage
- Brave mode optionally applies mocks directly to WireMock via admin API

**Future Spec (Mock Evolution):**
- Specification storage and versioning will be added
- Namespace organization for tracking specifications
- Job history and audit trails
- Mock evolution tracking

## API Design

MockNest Serverless exposes **two distinct APIs** that work together:

### **1. Standard WireMock Admin API (Existing - Unchanged)**
The existing WireMock admin API for managing mocks:

```http
# Create/Update Mock (Standard WireMock)
POST /__admin/mappings
Content-Type: application/json

{
  "request": {
    "method": "GET",
    "url": "/users"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "[{\"id\":1,\"name\":\"John Doe\"}]"
  }
}

# List All Mocks (Standard WireMock)
GET /__admin/mappings

# Delete Mock (Standard WireMock)  
DELETE /__admin/mappings/{id}
```

### **2. AI Generation API - Phase 1 Endpoints**

#### Hello World Endpoint (Bedrock Validation)
```http
POST /ai/hello
Content-Type: application/json

{
  "text": "Hello, Bedrock!"
}

Response:
{
  "success": true,
  "response": "Hello! I received your message: Hello, Bedrock!",
  "error": null
}
```

#### Mock Generation from OpenAPI Specification
```http
POST /ai/generation/from-spec
Content-Type: application/json

{
  "spec": {
    "url": "https://example.com/api/openapi.yaml"
  },
  "instructions": "Focus on error scenarios and include rate limiting examples",
  "options": {
    "includeExamples": true,
    "generateErrorCases": true,
    "realisticData": true,
    "maxMappings": 10
  }
}

Response (WireMock Import Format):
{
  "mappings": [
    {
      "id": "76ada7b0-55ae-4229-91c4-396a36f18347",
      "priority": 2,
      "persistent": true,
      "request": {
        "method": "GET",
        "urlPath": "/activity"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": {
          "activity": "Learn a new programming language",
          "type": "education",
          "participants": 1
        }
      }
    }
  ],
  "totalGenerated": 15
}
```

### **Phase 1 Workflow Example**

```bash
# Step 1: Validate Bedrock integration (Hello World)
curl -X POST /ai/hello \
  -H "Content-Type: application/json" \
  -d '{"text": "Testing Bedrock connection"}'
# Returns: {"success": true, "response": "Hello! I received your message..."}

# Step 2: Generate mocks from OpenAPI spec (returns mocks in WireMock import format)
curl -X POST /ai/generation/from-spec \
  -H "Content-Type: application/json" \
  -d '{
    "spec": {"url": "https://bored-api.appbrewery.com/openapi.yaml"},
    "instructions": "Mock bored api with funny activities",
    "options": {"generateErrorCases": true, "maxMappings": 10}
  }' > generated-mocks.json
# Returns: {"mappings": [...], "totalGenerated": 10}

# Step 3: Review generated mocks in generated-mocks.json, then apply selected ones
curl -X POST http://mocknest-url/__admin/mappings/import \
  -H "Content-Type: application/json" \
  -d @generated-mocks.json
# Standard WireMock import endpoint applies the mocks

# Step 4: Use mocks normally (Standard WireMock)
curl /activity  # Returns mocked response
```

### **Key Design Principles**

1. **Synchronous Response**: Generated mocks returned immediately in HTTP response
2. **WireMock Import Format**: Mocks returned as JSON array ready for standard `POST /__admin/mappings/import` endpoint
3. **Complete Mappings**: Each mock includes UUID, priority=2, and persistent=true
4. **Stateless Generation**: No storage of specifications or generated mocks
5. **Standard Integration**: Uses standard WireMock admin API for applying mocks
6. **User Control**: Users review generated mocks before importing via standard WireMock endpoint
7. **No Lock-in**: Generated mocks are standard WireMock mappings

**Phase 1 Limitations:**
- No job tracking or async processing
- No specification storage or versioning
- No mock evolution endpoint
- No mock enhancement endpoint
- No batch generation endpoint

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Hello World Bedrock Integration
*For any* text input to the hello world endpoint, the system should successfully communicate with Bedrock and return a response or clear error message
**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: OpenAPI Parsing Completeness
*For any* valid OpenAPI 3.0 or Swagger 2.0 specification, the parser should extract all endpoint definitions without data loss
**Validates: Requirements 2.1, 2.2, 2.3**

### Property 3: Generated Mock Validity
*For any* generated mock, the WireMock mapping should be syntactically valid JSON and executable by the WireMock runtime
**Validates: Requirements 3.1, 3.5**

### Property 4: Schema Compliance
*For any* generated mock response, the response structure should comply with the schema definitions from the source OpenAPI specification
**Validates: Requirements 3.2**

### Property 5: Scenario Coverage
*For any* OpenAPI specification with multiple response status codes, the generator should create separate mocks for happy path (2xx), client errors (4xx), and server errors (5xx) when error generation is enabled
**Validates: Requirements 3.3**

### Property 6: Example Response Preservation
*For any* OpenAPI specification with example responses, those examples should be used as mock response templates in generated mocks
**Validates: Requirements 2.4, 3.4**

### Property 7: Synchronous Response in WireMock Import Format
*For any* successful generation request, all generated mocks should be returned in the HTTP response as a JSON array in WireMock import format, with each mapping containing UUID, priority=2, and persistent=true
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.8**

### Property 8: Mock Validation Completeness
*For any* generated mock, the validator should verify that the mock conforms to the source OpenAPI specification including request matchers, response schemas, and status codes
**Validates: Requirements 3.6**

### Property 9: Batch Mock Correction
*For any* set of invalid mocks, the system should send all invalid mocks with their respective validation errors to AI in a single batch correction request
**Validates: Requirements 3.7, 3.8**

### Property 10: Corrected Mock Validation
*For any* corrected mock returned from batch correction, the system should validate it again and only include it in the response if it passes validation
**Validates: Requirements 3.9, 3.10**

### Property 11: Valid Mock Collection
*For any* generation request, the response should contain all valid mocks including both originally valid mocks and successfully corrected mocks from the batch correction
**Validates: Requirements 3.11**

### Property 12: Comprehensive Logging
*For any* validation and correction process, the system should log all validation failures, batch correction attempts, and final results
**Validates: Requirements 3.12**

### Property 13: Instructions Integration
*For any* generation request with natural language instructions, the generated mocks should reflect those instructions in their response characteristics
**Validates: Requirements 4.1, 4.2, 4.4**

### Property 14: Error Handling Resilience
*For any* invalid OpenAPI specification, the parser should return detailed validation errors without crashing the system
**Validates: Requirements 2.5**

**Deferred to Future Phases:**
- Mock evolution properties (specification change detection)
- Specification storage and versioning properties
- Namespace organization properties
- Job tracking properties

## Error Handling

### Specification Parsing Errors - Phase 1
- **Invalid OpenAPI Format**: Clear error messages indicating specific format issues with line numbers
- **Missing Required Fields**: Detailed validation errors with field-level guidance
- **Unsupported Version**: Clear message indicating only OpenAPI 3.0 and Swagger 2.0 are supported in Phase 1
- **Specification Fetch Errors**: Handle URL fetch failures with appropriate error messages

### Bedrock Integration Errors - Phase 1
- **Service Unavailable**: Return clear error message indicating Bedrock is not available
- **Invalid Model Response**: Validate and handle malformed responses from Claude
- **Timeout Handling**: Configurable timeouts with appropriate error messages
- **Rate Limiting**: Handle Bedrock rate limits gracefully with retry logic

### Generation Process Errors - Phase 1
- **Memory Limits**: Handle large specifications gracefully with appropriate error messages
- **Invalid Schema**: Handle specifications with invalid or missing schemas
- **Timeout Errors**: Return partial results if generation exceeds timeout limits
- **Mock Application Failures**: Return error details with list of successfully applied mocks when some applications fail

## Testing Strategy

### Unit Testing - Phase 1
- Test hello world endpoint with various text inputs
- Test OpenAPI parser with valid and invalid specifications
- Test mock generator with different endpoint configurations
- Test brave mode application with mock WireMock admin API
- Test error handling scenarios with fault injection

### Property-Based Testing - Phase 1
Property-based tests will be implemented using Kotest Property Testing framework, with each test running a minimum of 100 iterations.

Each property-based test will be tagged with comments referencing the design document property:

```kotlin
// **Feature: ai-mock-generation, Property 2: OpenAPI Parsing Completeness**
@Test
suspend fun `openapi parsing preserves all endpoint information`() {
    checkAll<OpenAPISpecification> { spec ->
        val parsed = parser.parse(spec.toJson(), SpecificationFormat.OPENAPI_3)
        parsed.endpoints.size shouldBe spec.paths.size
    }
}

// **Feature: ai-mock-generation, Property 3: Generated Mock Validity**
@Test
suspend fun `generated mocks are valid wiremock json`() {
    checkAll<APISpecification> { spec ->
        val mocks = generator.generateFromSpecification(spec, null, defaultOptions)
        mocks.forEach { mock ->
            // Should parse as valid JSON
            Json.parseToJsonElement(mock.wireMockMapping)
            // Should contain required WireMock fields
            mock.wireMockMapping shouldContain "request"
            mock.wireMockMapping shouldContain "response"
        }
    }
}

// **Feature: ai-mock-generation, Property 7: Synchronous Response Completeness**
@Test
suspend fun `generation returns all mocks in response`() {
    checkAll<APISpecification> { spec ->
        val response = useCase.invoke(MockGenerationRequest(
            spec = SpecificationSource(content = spec.toJson()),
            options = GenerationOptions.default()
        ))
        response.totalGenerated shouldBe response.mocks.size
        response.mocks.forEach { mock ->
            mock.wireMockMapping.shouldNotBeEmpty()
        }
    }
}
```

### Integration Testing - Phase 1
- Test complete generation workflows with real OpenAPI specifications
- Test Koog agent orchestration with mock Bedrock responses
- Validate generated mocks work with actual WireMock runtime
- Test hello world endpoint with actual Bedrock integration
- Test brave mode with actual WireMock admin API

### Performance Testing - Phase 1
- Measure generation times for various specification sizes
- Test memory usage with large specifications
- Validate synchronous response times stay within timeout limits
- Test Bedrock API latency and timeout handling

## Deployment Considerations

### Koog Framework Integration - Phase 1
```yaml
Dependencies:
  - koog-core: 0.6.0
  - koog-functional-agents: 0.6.0
  - koog-bedrock: 0.6.0
  - koog-kotlin-dsl: 0.6.0

Configuration:
  - Functional Agent registration with Koog runtime
  - Agent domain: "mock-generation"
  - Capabilities: parse-openapi, generate-wiremock, scenario-generation
  - Bedrock model configuration for hello world validation
  - No persistence configuration needed (stateless generation)
```

### AWS Bedrock Configuration - Phase 1
```yaml
Required Models:
  - anthropic.claude-3-sonnet-20240229-v1:0 (Hello world validation only)

IAM Permissions:
  - bedrock:InvokeModel
  - bedrock:ListFoundationModels

Phase 1 Usage:
  - Hello world endpoint only
  - No natural language mock generation yet
  - Future phases will expand Bedrock usage
```

### Resource Scaling - Phase 1
- **Lambda Memory**: 1024MB minimum for OpenAPI parsing and generation
- **Timeout**: 30 seconds for synchronous generation (configurable)
- **Concurrency**: Start with default, monitor usage
- **No Storage**: No S3 storage needed for Phase 1 (stateless generation)

## Security Considerations

### API Specification Security - Phase 1
- **Sanitization**: Remove sensitive data from specifications before processing
- **Validation**: Strict input validation for OpenAPI format
- **Access Control**: API key-based access to generation endpoints
- **URL Validation**: Validate and sanitize specification URLs before fetching

### Bedrock Integration Security - Phase 1
- **Model Access**: Restrict to Claude 3 Sonnet only
- **Input Validation**: Sanitize text inputs for hello world endpoint
- **Response Validation**: Validate Bedrock responses before returning
- **Rate Limiting**: Implement rate limiting for Bedrock API calls

### Generated Mock Security - Phase 1
- **Content Filtering**: Basic validation of generated mock content
- **Brave Mode Safety**: Validate mocks before applying to WireMock
- **Audit Logging**: Track all generation activities

## Monitoring and Observability

### Generation Metrics - Phase 1
- **Success Rate**: Percentage of successful generations
- **Processing Time**: Average generation time by specification size
- **Bedrock Usage**: Hello world endpoint usage tracking
- **Error Rate**: Failed generations by error type
- **Brave Mode Success**: Application success rate when brave mode is enabled

### Business Metrics - Phase 1
- **Adoption**: Number of specifications processed
- **Mock Volume**: Total mocks generated over time
- **Endpoint Coverage**: Distribution of generated mocks by HTTP method and status code

## Future Phase Enhancements

The following features are explicitly deferred to future phases:

### Future Spec: Mock Evolution
- Specification storage and versioning
- Specification change detection and diff generation
- Automated mock update suggestions
- Version comparison and rollback capabilities
- Namespace storage organization

### Future Phase 2: AI-Powered Enhancement
- Natural language mock generation using Bedrock (beyond hello world)
- Mock refinement and improvement suggestions
- Response realism enhancement

### Future Phase 3: Advanced Features
- GraphQL and WSDL support
- Batch generation for multiple specifications
- Traffic analysis integration
- Conversational interfaces (MCP)
- Async job processing for large specifications