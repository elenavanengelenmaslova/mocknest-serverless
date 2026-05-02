package nl.vintik.mocknest.infra.aws.generation.ai.config

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.interfaces.*
import nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.*
import nl.vintik.mocknest.application.generation.validators.CompositeMockValidator
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter

/**
 * Factory functions for AI-powered mock generation components.
 * AI features are always enabled.
 *
 * Note: BedrockRuntimeClient bean is provided by BedrockConfiguration.
 * Note: GraphQL-specific beans are provided by GraphQLGenerationConfig.
 */
object AIGenerationConfiguration {

    /**
     * Inference prefix resolver for automatic Bedrock inference profile selection.
     */
    fun inferencePrefixResolver(
        deployRegion: String,
        inferenceMode: String
    ): InferencePrefixResolver {
        val mode = InferenceMode.valueOf(inferenceMode.uppercase())
        return DefaultInferencePrefixResolver(deployRegion, mode)
    }

    /**
     * Primary specification parser that delegates to format-specific parsers.
     */
    fun compositeSpecificationParser(
        parsers: List<SpecificationParserInterface>,
    ): CompositeSpecificationParser {
        return CompositeSpecificationParserImpl(parsers)
    }

    /**
     * OpenAPI specification parser.
     */
    fun openApiSpecificationParser(): SpecificationParserInterface {
        return OpenAPISpecificationParser()
    }

    fun bedrockServiceAdapter(
        bedrockClient: BedrockRuntimeClient,
        modelConfiguration: ModelConfiguration,
        promptBuilder: PromptBuilderService
    ): AIModelServiceInterface {
        return BedrockServiceAdapter(bedrockClient, modelConfiguration, promptBuilder)
    }

    /**
     * Primary composite mock validator that delegates to all registered format-specific validators.
     * Each validator handles its own format and returns valid() for unsupported formats.
     * Validators are explicitly listed to avoid circular dependency with List<MockValidatorInterface>.
     * Add new format-specific validators here when extending mock generation support.
     */
    fun compositeMockValidator(
        openAPIMockValidator: OpenAPIMockValidator,
        graphQLMockValidator: GraphQLMockValidator,
        soapMockValidator: SoapMockValidator
    ): MockValidatorInterface {
        return CompositeMockValidator(listOf(openAPIMockValidator, graphQLMockValidator, soapMockValidator))
    }

    fun mockGenerationAgent(
        maxRetries: Int,
        aiModelService: AIModelServiceInterface,
        specificationParser: SpecificationParserInterface,
        mockValidator: MockValidatorInterface,
        promptBuilder: PromptBuilderService
    ): MockGenerationFunctionalAgent {
        return MockGenerationFunctionalAgent(aiModelService, specificationParser, mockValidator, promptBuilder, maxRetries)
    }

    fun generateMocksFromSpecWithDescriptionUseCase(
        mockGenerationAgent: MockGenerationFunctionalAgent,
    ): GenerateMocksFromSpecWithDescriptionUseCase {
        return GenerateMocksFromSpecWithDescriptionUseCase(mockGenerationAgent)
    }

    fun aiGenerationRequestUseCase(
        generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase
    ): HandleAIGenerationRequest {
        return AIGenerationRequestUseCase(
            generateFromSpecWithDescriptionUseCase
        )
    }
}
