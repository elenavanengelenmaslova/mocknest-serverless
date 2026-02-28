package nl.vintik.mocknest.infra.aws.core.ai

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.usecases.*
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter
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
        modelConfiguration: ModelConfiguration
    ): AIModelServiceInterface {
        return BedrockServiceAdapter(bedrockClient, modelConfiguration)
    }

    @Bean
    fun mockGenerationAgent(
        modelConfiguration: ModelConfiguration,
        aiModelService: AIModelServiceInterface,
        specificationParser: SpecificationParserInterface,
        mockValidator: MockValidatorInterface,
    ): MockGenerationFunctionalAgent {
        return MockGenerationFunctionalAgent(aiModelService, specificationParser, mockValidator)
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