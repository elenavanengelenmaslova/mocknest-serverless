package nl.vintik.mocknest.infra.aws.generation.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.domain.generation.*
import nl.vintik.mocknest.infra.aws.core.ai.ModelConfiguration
import org.springframework.http.HttpMethod
import kotlin.runCatching
import java.time.Instant

private val logger = KotlinLogging.logger {}
private val objectMapper = ObjectMapper()

/**
 * Amazon Bedrock implementation of AI model service.
 * Provides AI-powered mock generation.
 */
class BedrockServiceAdapter(
    private val bedrockClient: BedrockRuntimeClient,
    private val modelConfiguration: ModelConfiguration
) : AIModelServiceInterface {

    // Lazy initialization of Koog components to avoid cold start penalty
    private val executor by lazy {
        SingleLLMPromptExecutor(BedrockLLMClient(bedrockClient))
    }

    override fun createAgent(): AIAgent<String, String> {
        val model = modelConfiguration.getModel()
        logger.info { "Initializing AI agent: model=${model.id}" }
        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = """
                You are an expert API mock generator.
                You generate WireMock JSON mappings based on user instructions and specifications.
            """.trimIndent(),
            temperature = TEMPERATURE,
            toolRegistry = ToolRegistry.EMPTY
        )
    }

    companion object {
        private const val TEMPERATURE = 0.7
    }

    override suspend fun generateMockFromSpecWithDescription(
        agent: AIAgent<String, String>,
        specification: APISpecification,
        description: String,
        namespace: MockNamespace
    ): List<GeneratedMock> {
        logger.info { "Generating enhanced mocks from spec + description for namespace: ${namespace.displayName()}" }

        val prompt = buildSpecWithDescriptionPrompt(specification, description, namespace)

        return runCatching {
            val response = agent.run(prompt)
            parseModelResponse(response, namespace, SourceType.SPEC_WITH_DESCRIPTION, "${specification.title}: $description")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to generate enhanced mocks for spec: ${specification.title}" }
        }.getOrElse {
            emptyList()
        }
    }

    override suspend fun correctMocks(
        agent: AIAgent<String, String>,
        invalidMocks: List<Pair<GeneratedMock, List<String>>>,
        namespace: MockNamespace,
        specification: APISpecification?
    ): List<GeneratedMock> {
        logger.info { "Correcting ${invalidMocks.size} invalid mocks" }

        val correctionPrompt = buildCorrectionPrompt(invalidMocks, namespace, specification)

        return runCatching {
            val response = agent.run(correctionPrompt)
            parseModelResponse(response, namespace, SourceType.REFINEMENT, "Correction for ${namespace.displayName()}")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to correct mocks" }
        }.getOrElse {
            emptyList()
        }
    }

    internal fun buildCorrectionPrompt(
        invalidMocks: List<Pair<GeneratedMock, List<String>>>,
        namespace: MockNamespace,
        specification: APISpecification?
    ): String {
        val specContext = specification?.let {
            """
            API Specification Context:
            - Title: ${it.title}
            - Version: ${it.version}
            - Endpoints: ${it.endpoints.size}
            
            """.trimIndent()
        } ?: ""

        val mocksWithErrors = invalidMocks.joinToString("\n\n---\n\n") { (mock, errors) ->
            """
            Mock ID: ${mock.id}
            Current Mapping:
            ${mock.wireMockMapping}
            
            Validation Errors:
            ${errors.joinToString("\n") { " - $it" }}
            """.trimIndent()
        }

        return """
        You are an expert API mock generator. The following WireMock mappings failed validation against the specification.
        
        $specContext
        Namespace:
        - API Name: ${namespace.apiName}
        ${namespace.client?.let { "- Client: $it" } ?: ""}
        
        Please correct ALL of the following mocks to fix their respective errors:
        
        $mocksWithErrors
        
        Requirements:
        - Return only a JSON array containing the corrected WireMock mappings.
        - Each mapping should be a complete, valid WireMock JSON object.
        - Fix all validation errors listed for each mock.
        - Ensure all mock URLs are correctly prefixed with /${namespace.displayName()}
        - Maintain the same structure and intent as the original mocks.
        - For REST API Prefer `jsonBody` over `body` for JSON responses.
        
        Do not include any explanatory text, only the JSON array.
        """.trimIndent()
    }

    internal fun buildSpecWithDescriptionPrompt(specification: APISpecification, description: String, namespace: MockNamespace): String {
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
        
        Namespace:
        - API Name: ${namespace.apiName}
        ${namespace.client?.let { "- Client: $it" } ?: ""}
        
        Enhancement Description: $description
        
        Requirements:
        - Generate WireMock mappings that follow the API specification structure
        - IMPORTANT: All mock URLs must be prefixed with /${namespace.displayName()} (e.g., if the spec has /users, the mock URL should be /${namespace.displayName()}/users)
        - Enhance the mappings based on the description (add error cases, specific data, behaviors, etc.)
        - Include realistic response data that matches both the spec and description
        - Handle appropriate HTTP status codes
        - Include relevant headers and proper content types
        - Generate comprehensive mappings that cover the described scenarios
        - Prefer `jsonBody` over `body` for JSON responses to ensure easy readability and structure
        
        Return only a JSON array of WireMock mappings. Each mapping should be a complete, valid WireMock JSON object.
        Do not include any explanatory text, only the JSON array.
        """.trimIndent()
    }

    private fun parseModelResponse(
        response: String,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock> {
        return runCatching {
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
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse model response as JSON, attempting text extraction" }
        }.getOrElse {
            // Try to extract JSON from text response
            val jsonPattern = Regex("""(\[.*\]|\{.*\})""", RegexOption.DOT_MATCHES_ALL)
            val match = jsonPattern.find(response)

            if (match != null) {
                runCatching {
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
                }.onFailure { e ->
                    logger.error(e) { "Failed to parse extracted JSON from model response" }
                }.getOrElse {
                    emptyList()
                }
            } else {
                logger.error { "No valid JSON found in model response: $response" }
                emptyList()
            }
        }
    }

    internal fun createGeneratedMock(
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
            id = "ai-generated-${namespace.displayName().replace("/", "-")}-${method.lowercase()}-${path.replace("/", "-").replace("{", "").replace("}", "")}-$index",
            name = "AI Generated: $method $path",
            namespace = namespace,
            wireMockMapping = wireMockMapping,
            metadata = MockMetadata(
                sourceType = sourceType,
                sourceReference = sourceReference,
                endpoint = EndpointInfo(
                    method = HttpMethod.valueOf(method.uppercase()),
                    path = path,
                    statusCode = statusCode,
                    contentType = contentType
                ),
                tags = setOf("ai-generated", sourceType.name.lowercase(), method.lowercase())
            ),
            generatedAt = Instant.now()
        )
    }
}