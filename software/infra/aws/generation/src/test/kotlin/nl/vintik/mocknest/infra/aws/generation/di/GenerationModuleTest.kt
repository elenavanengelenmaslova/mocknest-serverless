package nl.vintik.mocknest.infra.aws.generation.di

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.interfaces.CompositeSpecificationParser
import nl.vintik.mocknest.application.generation.interfaces.GenerationStorageInterface
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import kotlin.test.assertNotNull

/**
 * Docker-free wiring tests for [generationModule].
 *
 * Unlike the LocalStack-based wiring verification, this test mocks the AWS clients so
 * the provider lambdas (env-var resolution, client construction, bean wiring) execute
 * in any environment. Resolving [GenerationPrimingHook] forces transitive instantiation
 * of nearly every bean in the module.
 */
class GenerationModuleTest {

    private val envVars = EnvironmentVariables()

    private fun testCoreModule() = module {
        single { mapper }
        single<S3Client> { mockk(relaxed = true) }
    }

    private fun bedrockOverride() = module {
        single<BedrockRuntimeClient> { mockk(relaxed = true) }
    }

    @AfterEach
    fun tearDown() {
        runCatching { envVars.teardown() }
    }

    @Test
    fun `Given default env When generationModule started Then all key beans resolve`() {
        envVars.set("AWS_REGION", "eu-west-1")
        envVars.set("MOCKNEST_S3_BUCKET_NAME", "test-bucket")
        envVars.set("BEDROCK_INFERENCE_MODE", "AUTO")
        envVars.set("BEDROCK_MODEL_NAME", "AmazonNovaPro")
        envVars.set("BEDROCK_GENERATION_MAX_RETRIES", "1")
        envVars.setup()

        val app = koinApplication {
            allowOverride(true)
            modules(testCoreModule(), generationModule(), bedrockOverride())
        }
        val koin = app.koin

        assertNotNull(koin.get<GenerationStorageInterface>())
        assertNotNull(koin.get<InferencePrefixResolver>())
        assertNotNull(koin.get<ModelConfiguration>())
        assertNotNull(koin.get<CompositeSpecificationParser>())
        assertNotNull(koin.get<MockValidatorInterface>())
        assertNotNull(koin.get<HandleAIGenerationRequest>())
        assertNotNull(koin.get<GetAIHealth>())
        // Resolving the priming hook forces instantiation of almost every other bean
        assertNotNull(koin.get<GenerationPrimingHook>())

        app.close()
    }

    @Test
    fun `Given custom bedrock endpoint and explicit inference mode When generationModule started Then beans resolve via alternate branches`() {
        // Exercises the non-blank custom-endpoint branch and a non-default inference mode + retry count
        envVars.set("AWS_REGION", "us-east-1")
        envVars.set("MOCKNEST_S3_BUCKET_NAME", "another-bucket")
        envVars.set("aws.bedrock.endpoint", "https://bedrock.example.com")
        envVars.set("BEDROCK_INFERENCE_MODE", "GLOBAL_ONLY")
        envVars.set("BEDROCK_MODEL_NAME", "AmazonNovaLite")
        envVars.set("BEDROCK_GENERATION_MAX_RETRIES", "3")
        envVars.setup()

        val app = koinApplication {
            allowOverride(true)
            modules(testCoreModule(), generationModule(), bedrockOverride())
        }
        val koin = app.koin

        assertNotNull(koin.get<ModelConfiguration>())
        assertNotNull(koin.get<InferencePrefixResolver>())
        assertNotNull(koin.get<GenerationPrimingHook>())

        app.close()
    }
}
