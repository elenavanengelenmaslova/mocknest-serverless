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
 */
class TokenUsageCapturingClient(
    private val delegate: BedrockRuntimeClient,
    private val tokenUsageStore: TokenUsageStore
) : BedrockRuntimeClient by delegate {

    override suspend fun converse(input: ConverseRequest): ConverseResponse {
        val response = delegate.converse(input)
        val usage = response.usage
        if (usage == null) {
            logger.warn { "No token usage data in Bedrock converse response, recording zeros" }
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
