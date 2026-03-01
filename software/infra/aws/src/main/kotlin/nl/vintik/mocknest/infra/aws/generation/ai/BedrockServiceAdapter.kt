package nl.vintik.mocknest.infra.aws.generation.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import com.fasterxml.jackson.databind.JsonNode
import nl.vintik.mocknest.application.core.mapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import nl.vintik.mocknest.infra.aws.core.ai.ModelConfiguration
import org.springframework.http.HttpMethod
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Amazon Bedrock implementation of AI model service.
 * Provides AI-powered mock generation.
 */
class BedrockServiceAdapter(
    private val bedrockClient: BedrockRuntimeClient,
    private val modelConfiguration: ModelConfiguration,
    private val promptBuilder: PromptBuilderService
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
            systemPrompt = promptBuilder.loadSystemPrompt(),
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
        namespace: MockNamespace,
        prompt: String
    ): List<GeneratedMock> {
        logger.info { "Generating enhanced mocks from spec + description for namespace: ${namespace.displayName()}" }

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
        specification: APISpecification?,
        prompt: String
    ): List<GeneratedMock> {
        logger.info { "Correcting ${invalidMocks.size} invalid mocks" }

        return runCatching {
            val response = agent.run(prompt)
            parseModelResponse(response, namespace, SourceType.REFINEMENT, "Correction for ${namespace.displayName()}")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to correct mocks" }
        }.getOrElse {
            emptyList()
        }
    }

    internal fun parseModelResponse(
        response: String,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock> {
        // Try to parse as raw JSON first
        runCatching {
            val jsonNode = mapper.readTree(response)
            return processJsonNode(jsonNode, namespace, sourceType, sourceReference)
        }

        // If raw parsing fails, it might be wrapped in Markdown or contain explanatory text.
        // Try extracting from Markdown code blocks first (non-greedy)
        val markdownPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
        val markdownMatch = markdownPattern.find(response)
        if (markdownMatch != null) {
            val json = markdownMatch.groupValues[1]
            runCatching {
                val jsonNode = mapper.readTree(json)
                return processJsonNode(jsonNode, namespace, sourceType, sourceReference)
            }.onFailure { e ->
                logger.warn(e) { "Failed to parse JSON extracted from Markdown block" }
            }
        }

        // Fallback: try to extract the first/largest JSON-like block using regex.
        // We use a greedy match for the content to handle nested structures.
        val jsonPattern = Regex("""(\[[\s\S]*\]|\{[\s\S]*\})""")
        val match = jsonPattern.find(response)

        if (match != null) {
            runCatching {
                val jsonNode = mapper.readTree(match.value)
                return processJsonNode(jsonNode, namespace, sourceType, sourceReference)
            }.onFailure { e ->
                logger.error(e) { "Failed to parse extracted JSON from model response" }
            }
        }

        logger.error { "No valid JSON found in model response: $response" }
        return emptyList()
    }

    private fun processJsonNode(
        jsonNode: JsonNode,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock> {
        return if (jsonNode.isArray) {
            (0 until jsonNode.size()).map { i ->
                createGeneratedMock(jsonNode.get(i), namespace, sourceType, sourceReference, i)
            }
        } else {
            listOf(createGeneratedMock(jsonNode, namespace, sourceType, sourceReference, 0))
        }
    }

    internal fun createGeneratedMock(
        mappingJson: JsonNode,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String,
        index: Int
    ): GeneratedMock {
        // Extract basic info from WireMock mapping for metadata
        val wireMockMapping = mapper.writeValueAsString(mappingJson)
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