package nl.vintik.mocknest.infra.aws.core.ai.config

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for AWS Bedrock Runtime client
 * 
 * Provides BedrockRuntimeClient bean for AI model invocation
 */
@Configuration
class BedrockConfiguration {
    
    private val logger = KotlinLogging.logger {}
    
    @Bean
    fun bedrockRuntimeClient(
        @Value($$"${aws.region}") region: String,
        @Value($$"${aws.bedrock.endpoint:}") customEndpoint: String?
    ): BedrockRuntimeClient {
        logger.info { "Initializing Bedrock Runtime client for region: $region" }
        
        return BedrockRuntimeClient {
            this.region = region
            credentialsProvider = DefaultChainCredentialsProvider()
            
            // Support custom endpoint for LocalStack testing
            if (!customEndpoint.isNullOrBlank()) {
                logger.info { "Using custom Bedrock endpoint: $customEndpoint." }
                endpointUrl = Url.parse(customEndpoint)
            }
        }
    }
}
