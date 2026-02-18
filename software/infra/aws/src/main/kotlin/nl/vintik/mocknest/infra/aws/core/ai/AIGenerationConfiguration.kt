package nl.vintik.mocknest.infra.aws.generation

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.agent.TestKoogAgent
import nl.vintik.mocknest.application.generation.generators.RealisticTestDataGenerator
import nl.vintik.mocknest.application.generation.generators.WireMockMappingGenerator
import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.usecases.*
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleTestAgentRequest
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
    fun wireMockMappingGenerator(testDataGenerator: TestDataGeneratorInterface): MockGeneratorInterface {
        return WireMockMappingGenerator(testDataGenerator)
    }

    @Bean
    fun realisticTestDataGenerator(): TestDataGeneratorInterface {
        return RealisticTestDataGenerator()
    }

    @Bean
    fun bedrockServiceAdapter(bedrockClient: BedrockRuntimeClient): AIModelServiceInterface {
        return BedrockServiceAdapter(bedrockClient)
    }

    @Bean
    fun bedrockTestKoogAgent(bedrockClient: BedrockRuntimeClient): TestKoogAgent {
        return BedrockTestKoogAgent(bedrockClient)
    }

    @Bean
    fun mockGenerationFunctionalAgent(
        aiModelService: AIModelServiceInterface,
        specificationParser: SpecificationParserInterface,
        mockGenerator: MockGeneratorInterface,
        generationStorage: GenerationStorageInterface,
    ): MockGenerationFunctionalAgent {
        return MockGenerationFunctionalAgent(aiModelService, specificationParser, mockGenerator, generationStorage)
    }

    @Bean
    fun generateMocksFromSpecUseCase(
        specificationParser: SpecificationParserInterface,
        mockGenerator: MockGeneratorInterface,
        generationStorage: GenerationStorageInterface,
    ): GenerateMocksFromSpecUseCase {
        return GenerateMocksFromSpecUseCase(specificationParser, mockGenerator, generationStorage)
    }

    @Bean
    fun generateMocksFromSpecWithDescriptionUseCase(
        mockGenerationAgent: MockGenerationFunctionalAgent,
        generationStorage: GenerationStorageInterface,
    ): GenerateMocksFromSpecWithDescriptionUseCase {
        return GenerateMocksFromSpecWithDescriptionUseCase(mockGenerationAgent, generationStorage)
    }

    @Bean
    fun generateMocksFromDescriptionUseCase(
        mockGenerationAgent: MockGenerationFunctionalAgent,
        generationStorage: GenerationStorageInterface,
    ): GenerateMocksFromDescriptionUseCase {
        return GenerateMocksFromDescriptionUseCase(mockGenerationAgent, generationStorage)
    }

    @Bean
    fun aiGenerationRequestUseCase(
        generateFromSpecUseCase: GenerateMocksFromSpecUseCase,
        generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase,
        generateFromDescriptionUseCase: GenerateMocksFromDescriptionUseCase,
        generationStorageInterface: GenerationStorageInterface,
    ): HandleAIGenerationRequest {
        return AIGenerationRequestUseCase(
            generateFromSpecUseCase,
            generateFromSpecWithDescriptionUseCase,
            generateFromDescriptionUseCase,
            generationStorageInterface
        )
    }

    @Bean
    fun testAgentRequestUseCase(
        testKoogAgent: TestKoogAgent,
    ): HandleTestAgentRequest {
        return TestAgentRequestUseCase(testKoogAgent)
    }
}