package nl.vintik.mocknest.infra.aws.generation.ai

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.ModelResponseParsingException
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
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

    override suspend fun <Input, Output> runStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>,
        input: Input
    ): Output {
        val model = modelConfiguration.getModel()
        logger.info { "Running strategy: ${strategy.name} using model=${model.id}" }
        
        val agentConfig = AIAgentConfig.withSystemPrompt(
            prompt = promptBuilder.loadSystemPrompt(),
            llm = model,
            maxAgentIterations = 50
        )
        
        val agent = GraphAIAgent(
            inputType = strategy.inputType,
            outputType = strategy.outputType,
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = ToolRegistry.EMPTY
        )
        
        return agent.run(input)
    }

    override fun parseModelResponse(
        response: String,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock> {
        if (response.isBlank()) {
            logger.error { "Blank model response received" }
            throw ModelResponseParsingException("Blank model response")
        }

        // Try to parse as raw JSON first
        runCatching {
            val jsonNode = mapper.readTree(response)
            return processJsonNode(jsonNode, namespace, sourceType, sourceReference)
        }.onFailure { e ->
            if (e is ModelResponseParsingException) throw e
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
                if (e is ModelResponseParsingException) throw e
                logger.warn(e) { "Failed to parse JSON extracted from Markdown block" }
            }
        }

        // Fallback: try to extract the first/largest JSON-like block using regex.
        val jsonPattern = Regex("""(\[[\s\S]*\]|\{[\s\S]*\})""")
        val match = jsonPattern.find(response)

        if (match != null) {
            runCatching {
                val jsonNode = mapper.readTree(match.value)
                return processJsonNode(jsonNode, namespace, sourceType, sourceReference)
            }.onFailure { e ->
                if (e is ModelResponseParsingException) throw e
                logger.error(e) { "Failed to parse extracted JSON from model response" }
                throw ModelResponseParsingException("Invalid JSON in model response", e)
            }
        }

        logger.error { "No valid JSON found in model response" }
        throw ModelResponseParsingException("No JSON found in model response")
    }

    private fun processJsonNode(
        jsonNode: JsonNode,
        namespace: MockNamespace,
        sourceType: SourceType,
        sourceReference: String
    ): List<GeneratedMock> {
        if (jsonNode.isArray) {
            if (jsonNode.size() == 0) {
                logger.error { "Model returned an explicit empty JSON array" }
                throw ModelResponseParsingException("Model returned an empty JSON array — no mappings generated")
            }
            return (0 until jsonNode.size()).map { i ->
                createGeneratedMock(jsonNode.get(i), namespace, sourceType, sourceReference, i)
            }
        }
        return listOf(createGeneratedMock(jsonNode, namespace, sourceType, sourceReference, 0))
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

    override fun getModelName(): String {
        return modelConfiguration.getModelName()
    }

    override fun getConfiguredPrefix(): String? {
        return modelConfiguration.getConfiguredPrefix()
    }
}