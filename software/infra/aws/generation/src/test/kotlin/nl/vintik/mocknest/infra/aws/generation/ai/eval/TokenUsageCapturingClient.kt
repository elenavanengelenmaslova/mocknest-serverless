package nl.vintik.mocknest.infra.aws.generation.ai.eval

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseResponse
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Test-only decorator around [BedrockRuntimeClient] that intercepts `converse` calls
 * to capture token usage from Bedrock responses. All other methods delegate unchanged
 * to the wrapped client via Kotlin `by` delegation.
 *
 * This class lives entirely in the test source set and is never included in the
 * production deployment JAR.
 *
 * Koog 0.8.0 review: This decorator operates on the AWS SDK `BedrockRuntimeClient` type,
 * not on any Koog type. The Koog upgrade has no impact on this class. The DataDog LLM
 * Observability exporter added in Koog 0.8.0 (#1591) provides similar token tracking but
 * requires DataDog infrastructure — the custom approach remains appropriate for MockNest's
 * test-only usage.
 */
class TokenUsageCapturingClient(
    private val delegate: BedrockRuntimeClient,
    private val tokenUsageStore: TokenUsageStore
) : BedrockRuntimeClient by delegate {

    override suspend fun converse(input: ConverseRequest): ConverseResponse {
        logger.info { "TokenUsageCapturingClient: intercepted converse call for model=${input.modelId}" }
        val response = delegate.converse(input)
        val usage = response.usage
        if (usage == null) {
            logger.warn { "No token usage data in Bedrock converse response, recording zeros" }
        } else {
            logger.info { "Token usage captured: input=${usage.inputTokens}, output=${usage.outputTokens}, total=${usage.totalTokens}" }
        }
        tokenUsageStore.record(
            TokenUsageRecord(
                inputTokens = usage?.inputTokens ?: 0,
                outputTokens = usage?.outputTokens ?: 0,
                totalTokens = usage?.totalTokens ?: 0
            )
        )
        return response
    }
}
