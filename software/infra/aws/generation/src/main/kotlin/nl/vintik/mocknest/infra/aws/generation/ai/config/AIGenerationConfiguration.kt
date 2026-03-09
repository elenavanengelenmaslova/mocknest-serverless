package nl.vintik.mocknest.infra.aws.generation.ai.config

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.*
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Spring configuration for AI-powered mock generation components.
 * AI features are always enabled.
 * 
 * Note: BedrockRuntimeClient bean is provided by BedrockConfiguration.
 */
@Configuration
class AIGenerationConfiguration {

    /**
     * Inference prefix resolver for automatic Bedrock inference profile selection.
     */
    @Bean
    fun inferencePrefixResolver(
        @Value("\${AWS_REGION:eu-west-1}") deployRegion: String,
        @Value($$"${bedrock.inference.mode:AUTO}") inferenceMode: String
    ): InferencePrefixResolver {
        val mode = InferenceMode.valueOf(inferenceMode.uppercase())
        return DefaultInferencePrefixResolver(deployRegion, mode)
    }

    /**
     * Primary specification parser that delegates to format-specific parsers.
     */
    @Bean
    @Primary
    fun compositeSpecificationParser(
        parsers: List<SpecificationParserInterface>,
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

    @Bean
    fun bedrockServiceAdapter(
        bedrockClient: BedrockRuntimeClient,
        modelConfiguration: ModelConfiguration,
        promptBuilder: PromptBuilderService
    ): AIModelServiceInterface {
        return BedrockServiceAdapter(bedrockClient, modelConfiguration, promptBuilder)
    }

    @Bean
    fun mockGenerationAgent(
        @Value($$"${bedrock.generation.maxRetries:1}")
        maxRetries: Int,
        aiModelService: AIModelServiceInterface,
        specificationParser: SpecificationParserInterface,
        mockValidator: MockValidatorInterface,
        promptBuilder: PromptBuilderService
    ): MockGenerationFunctionalAgent {
        return MockGenerationFunctionalAgent(aiModelService, specificationParser, mockValidator, promptBuilder, maxRetries)
    }

    @Bean
    fun generateMocksFromSpecWithDescriptionUseCase(
        mockGenerationAgent: MockGenerationFunctionalAgent,
    ): GenerateMocksFromSpecWithDescriptionUseCase {
        return GenerateMocksFromSpecWithDescriptionUseCase(mockGenerationAgent)
    }


    @Bean
    fun aiGenerationRequestUseCase(
        generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase
    ): HandleAIGenerationRequest {
        return AIGenerationRequestUseCase(
            generateFromSpecWithDescriptionUseCase
        )
    }
}