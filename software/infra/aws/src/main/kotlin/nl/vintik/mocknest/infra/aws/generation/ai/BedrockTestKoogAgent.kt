package nl.vintik.mocknest.infra.aws.generation.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.agent.TestKoogAgent
import nl.vintik.mocknest.domain.generation.TestAgentRequest
import nl.vintik.mocknest.domain.generation.TestAgentResponse
import nl.vintik.mocknest.infra.aws.core.ai.ModelConfiguration
import org.springframework.beans.factory.annotation.Value

private val logger = KotlinLogging.logger {}

/**
 * Bedrock-based implementation of TestKoogAgent using Koog framework.
 * Provides AI-powered mock generation through proper Koog abstractions.
 * 
 * This implementation uses Koog's simpleBedrockExecutor and AIAgent for all
 * Bedrock interactions, following the framework's intended patterns.
 */
class BedrockTestKoogAgent(
    private val modelConfiguration: ModelConfiguration,
    @param:Value("\${aws.region:eu-west-1}")
    private val region: String,
    private val bedrockRuntimeClient: BedrockRuntimeClient? = null
) : TestKoogAgent {
    
    // Lazy initialization of Koog components to avoid cold start penalty
    private val executor by lazy {
        logger.info { "Initializing Bedrock executor: region=$region" }
        val client = bedrockRuntimeClient ?: BedrockLLMClient(
            identityProvider = DefaultChainCredentialsProvider(),
            settings = BedrockClientSettings(region = region)
        )
        SingleLLMPromptExecutor(
            if (client is BedrockLLMClient) client
            else BedrockLLMClient(client as BedrockRuntimeClient)
        )
    }
    
    private val agent by lazy {
        val model = modelConfiguration.getBedrockModel()
        logger.info { "Initializing AI agent: model=${model.id}, region=$region" }
        AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = """
                You are a helpful AI assistant integrated with MockNest Serverless.
                You help users with their requests in a clear and concise manner.
            """.trimIndent(),
            temperature = 0.7,
            toolRegistry = ToolRegistry.EMPTY
        )
    }
    
    override suspend fun execute(request: TestAgentRequest): TestAgentResponse {
        logger.info { "Executing Koog agent: instructions=${request.instructions}, contextKeys=${request.context.keys}" }
        
        return runCatching {
            val prompt = buildPrompt(request.instructions, request.context)
            val response = agent.run(prompt)
            
            logger.info { "Received response from Koog agent: responseLength=${response.length}" }
            
            TestAgentResponse(
                success = true,
                message = "Successfully processed request through Koog and Bedrock",
                bedrockResponse = response
            )
        }.onFailure { exception ->
            logger.error(exception) { "Error executing Koog agent: instructions=${request.instructions}, model=${modelConfiguration.getModelName()}, region=$region" }
        }.getOrElse { exception ->
            TestAgentResponse(
                success = false,
                message = "Failed to process request",
                error = exception.message
            )
        }
    }
    
    private fun buildPrompt(instructions: String, context: Map<String, String>): String {
        val contextStr = if (context.isNotEmpty()) {
            "\n\nContext:\n" + context.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        } else {
            ""
        }
        
        return """
User Instructions:
$instructions$contextStr

Please respond to the user's instructions in a helpful and concise manner.
        """.trimIndent()
    }
}
