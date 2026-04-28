package nl.vintik.mocknest.infra.aws.generation.ai.config

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Factory for AWS Bedrock Runtime client.
 *
 * Provides BedrockRuntimeClient for AI model invocation.
 */
object BedrockConfiguration {

    fun bedrockRuntimeClient(
        region: String,
        customEndpoint: String?
    ): BedrockRuntimeClient {
        logger.info { "Initializing Bedrock Runtime client for region: $region" }

        return BedrockRuntimeClient {
            this.region = region

            // Support custom endpoint for LocalStack testing
            if (!customEndpoint.isNullOrBlank()) {
                logger.info { "Using custom Bedrock endpoint: $customEndpoint." }
                endpointUrl = Url.parse(customEndpoint)
            }
        }
    }
}
