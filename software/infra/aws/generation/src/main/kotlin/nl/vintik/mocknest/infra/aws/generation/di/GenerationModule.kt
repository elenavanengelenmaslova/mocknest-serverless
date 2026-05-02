package nl.vintik.mocknest.infra.aws.generation.di

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.net.url.Url
import nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducer
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.interfaces.CompositeSpecificationParser
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferencePrefixResolver
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.interfaces.SpecificationParserInterface
import nl.vintik.mocknest.application.generation.parsers.CompositeSpecificationParserImpl
import nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.parsers.WsdlSpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.AIGenerationRequestUseCase
import nl.vintik.mocknest.application.generation.usecases.GenerateMocksFromSpecWithDescriptionUseCase
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.generation.validators.CompositeMockValidator
import nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlParser
import nl.vintik.mocknest.application.generation.wsdl.WsdlParserInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducer
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducerInterface
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.infra.aws.generation.ai.BedrockServiceAdapter
import nl.vintik.mocknest.infra.aws.generation.ai.config.DefaultInferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferenceMode
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import nl.vintik.mocknest.infra.aws.generation.graphql.GraphQLIntrospectionClient
import nl.vintik.mocknest.infra.aws.generation.health.AwsAIHealthUseCase
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapter
import nl.vintik.mocknest.infra.generation.wsdl.WsdlContentFetcher
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for the Generation Lambda handler.
 *
 * Provides all beans previously defined in Spring @Configuration classes
 * for the "generation" profile. S3Client comes from [coreModule] — not redeclared here.
 *
 * Defined as a function (not a global val) because `module {}` preallocates
 * factories — functions create fresh instances when needed.
 */
fun generationModule() = module {

    // Bedrock client (S3Client comes from coreModule)
    single {
        val region = System.getenv("AWS_REGION") ?: "eu-west-1"
        val customEndpoint = System.getenv("aws.bedrock.endpoint")
        BedrockRuntimeClient {
            this.region = region
            if (!customEndpoint.isNullOrBlank()) {
                endpointUrl = Url.parse(customEndpoint)
            }
        }
    }

    // Storage
    single<GenerationStorageInterface> {
        S3GenerationStorageAdapter(get(), System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "")
    }

    // Inference prefix resolver
    single<InferencePrefixResolver> {
        val deployRegion = System.getenv("AWS_REGION") ?: "eu-west-1"
        val inferenceMode = System.getenv("BEDROCK_INFERENCE_MODE") ?: "AUTO"
        DefaultInferencePrefixResolver(deployRegion, InferenceMode.valueOf(inferenceMode.uppercase()))
    }

    // Model configuration
    single {
        ModelConfiguration(
            modelName = System.getenv("BEDROCK_MODEL_NAME") ?: "AmazonNovaPro",
            prefixResolver = get()
        )
    }

    // OpenAPI parser (unqualified — also used as the base SpecificationParserInterface)
    single { OpenAPISpecificationParser() }

    // GraphQL components
    single<GraphQLIntrospectionClientInterface> { GraphQLIntrospectionClient() }
    single<GraphQLSchemaReducerInterface> { GraphQLSchemaReducer() }
    single<SpecificationParserInterface>(named("graphql")) {
        GraphQLSpecificationParser(get(), get())
    }

    // WSDL / SOAP components
    single<WsdlContentFetcherInterface> { WsdlContentFetcher() }
    single<WsdlParserInterface> { WsdlParser() }
    single<WsdlSchemaReducerInterface> { WsdlSchemaReducer() }
    single<SpecificationParserInterface>(named("wsdl")) {
        WsdlSpecificationParser(get(), get(), get())
    }

    // Composite specification parser — aggregates OpenAPI, GraphQL, and WSDL parsers
    single<CompositeSpecificationParser> {
        CompositeSpecificationParserImpl(
            listOf(get<OpenAPISpecificationParser>(), get(named("graphql")), get(named("wsdl")))
        )
    }

    // Validators
    single { OpenAPIMockValidator() }
    single { GraphQLMockValidator() }
    single { SoapMockValidator() }
    single<MockValidatorInterface> {
        CompositeMockValidator(listOf(get<OpenAPIMockValidator>(), get<GraphQLMockValidator>(), get<SoapMockValidator>()))
    }

    // Services
    single { PromptBuilderService() }
    single<AIModelServiceInterface> { BedrockServiceAdapter(get(), get(), get()) }

    // Agent and use cases
    single {
        val maxRetries = (System.getenv("BEDROCK_GENERATION_MAX_RETRIES") ?: "1").toInt()
        MockGenerationFunctionalAgent(get(), get<CompositeSpecificationParser>(), get(), get(), maxRetries)
    }
    single { GenerateMocksFromSpecWithDescriptionUseCase(get()) }
    single<HandleAIGenerationRequest> { AIGenerationRequestUseCase(get()) }

    // Health check
    single<GetAIHealth> {
        AwsAIHealthUseCase(get(), System.getenv("BEDROCK_INFERENCE_MODE") ?: "AUTO")
    }

    // Priming hook
    single {
        GenerationPrimingHook(
            get(), get(), System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "",
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}
