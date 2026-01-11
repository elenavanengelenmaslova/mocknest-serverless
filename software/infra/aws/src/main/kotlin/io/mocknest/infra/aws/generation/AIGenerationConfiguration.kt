package io.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.mocknest.application.generation.interfaces.*
import io.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import io.mocknest.application.generation.parsers.OpenAPISpecificationParser
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Spring configuration for AI-powered mock generation components.
 * Conditionally enables AI features based on configuration.
 */
@Configuration
@ConditionalOnProperty(name = ["ai.enabled"], havingValue = "true")
class AIGenerationConfiguration {
    
    /**
     * Bedrock Runtime Client for AI model interactions.
     */
    @Bean
    @ConditionalOnProperty(name = ["ai.enabled"], havingValue = "true")
    fun bedrockRuntimeClient(): BedrockRuntimeClient {
        return BedrockRuntimeClient {
            region = System.getenv("AWS_REGION") ?: "eu-west-1"
        }
    }
    
    /**
     * Primary specification parser that delegates to format-specific parsers.
     */
    @Bean
    @Primary
    fun compositeSpecificationParser(
        parsers: List<SpecificationParserInterface>
    ): CompositeSpecificationParser {
        return CompositeSpecificationParserImpl(parsers)
    }
    
    /**
     * OpenAPI specification parser.
     */
    @Bean
    fun openApiSpecificationParser(): SpecificationParserInterface {
        return OpenAPISpecificationParser()
    }
}

/**
 * Base configuration for mock generation components (always enabled).
 */
@Configuration
class MockGenerationConfiguration {
    
    /**
     * S3 client for storage operations.
     */
    @Bean
    fun s3Client(): S3Client {
        return S3Client {
            region = System.getenv("AWS_REGION") ?: "eu-west-1"
        }
    }
    
    /**
     * Fallback specification parser when AI is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = ["ai.enabled"], havingValue = "false", matchIfMissing = true)
    fun fallbackSpecificationParser(): SpecificationParserInterface {
        return OpenAPISpecificationParser()
    }
    
    /**
     * Fallback composite parser when AI is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = ["ai.enabled"], havingValue = "false", matchIfMissing = true)
    @Primary
    fun fallbackCompositeSpecificationParser(): CompositeSpecificationParser {
        return CompositeSpecificationParserImpl(listOf(OpenAPISpecificationParser()))
    }
}