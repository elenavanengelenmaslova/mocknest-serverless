package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.graphql.GraphQLIntrospectionClientInterface
import nl.vintik.mocknest.application.generation.graphql.GraphQLSchemaReducerInterface
import nl.vintik.mocknest.application.generation.parsers.OpenAPISpecificationParser
import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.generation.validators.OpenAPIMockValidator
import nl.vintik.mocknest.application.generation.validators.SoapMockValidator
import nl.vintik.mocknest.application.generation.wsdl.WsdlParserInterface
import nl.vintik.mocknest.application.generation.wsdl.WsdlSchemaReducerInterface
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import nl.vintik.mocknest.domain.core.HttpStatusCode

@Isolated
class GenerationPrimingHookTest {

    private val mockAIHealthUseCase: GetAIHealth = mockk(relaxed = true)
    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val testBucketName = "test-bucket"
    private val mockBedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val mockModelConfig: ModelConfiguration = mockk(relaxed = true)
    private val mockSpecificationParser: OpenAPISpecificationParser = mockk(relaxed = true)
    private val mockPromptBuilderService: PromptBuilderService = mockk(relaxed = true)
    private val mockMockValidator: OpenAPIMockValidator = mockk(relaxed = true)
    private val mockWsdlParser: WsdlParserInterface = mockk(relaxed = true)
    private val mockWsdlSchemaReducer: WsdlSchemaReducerInterface = mockk(relaxed = true)
    private val mockSoapMockValidator: SoapMockValidator = mockk(relaxed = true)
    private val mockGraphQLIntrospectionClient: GraphQLIntrospectionClientInterface = mockk(relaxed = true)
    private val mockGraphQLSchemaReducer: GraphQLSchemaReducerInterface = mockk(relaxed = true)
    private val primingHook = GenerationPrimingHook(
        mockAIHealthUseCase,
        mockS3Client,
        testBucketName,
        mockBedrockClient,
        mockModelConfig,
        mockSpecificationParser,
        mockPromptBuilderService,
        mockMockValidator,
        mockWsdlParser,
        mockWsdlSchemaReducer,
        mockSoapMockValidator,
        mockGraphQLIntrospectionClient,
        mockGraphQLSchemaReducer
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class SuccessfulPrimingExecution {

        @Test
        fun `Given SnapStart environment When priming executes Then should initialize all components`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true

            // When
            primingHook.prime()

            // Then
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockModelConfig.getModelName() }
            verify { mockModelConfig.getConfiguredPrefix() }
            verify { mockModelConfig.isOfficiallySupported() }
        }

        @Test
        fun `Given SnapStart environment When priming executes Then should complete without throwing exceptions`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When / Then - should not throw
                primingHook.prime()
            }
    }

    @Nested
    inner class GracefulDegradationHealthCheck {

        @Test
        fun `Given health check fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } throws RuntimeException("Health check unavailable")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true

            // When / Then - should not throw
            primingHook.prime()

            // Verify S3 initialization still attempted
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockModelConfig.getModelName() }
        }

        @Test
        fun `Given health check returns error status When priming executes Then should continue with other initializations`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode(503))
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When
                primingHook.prime()

                // Then
                verify { mockAIHealthUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
                verify { mockModelConfig.getModelName() }
            }

        @Test
        fun `Given health check throws exception When priming executes Then should not fail snapshot creation`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } throws IllegalStateException("Service not ready")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When / Then - should complete without throwing
                primingHook.prime()
            }
    }

    @Nested
    inner class GracefulDegradationS3Client {

        @Test
        fun `Given S3 client initialization fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 connection failed")
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When / Then - should not throw
                primingHook.prime()

                // Verify health check was still attempted
                verify { mockAIHealthUseCase.invoke() }
                verify { mockModelConfig.getModelName() }
            }

        @Test
        fun `Given S3 client throws exception When priming executes Then should not fail snapshot creation`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws IllegalArgumentException("Invalid bucket configuration")
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When / Then - should complete without throwing
                primingHook.prime()
            }

        @Test
        fun `Given S3 client timeout When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 timeout")
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true

            // When / Then - should not throw
            primingHook.prime()
        }
    }

    @Nested
    inner class GracefulDegradationModelConfig {

        @Test
        fun `Given model config validation fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockModelConfig.getModelName() } throws RuntimeException("Model config unavailable")

                // When / Then - should not throw
                primingHook.prime()

                // Verify health check and S3 were still attempted
                verify { mockAIHealthUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            }

        @Test
        fun `Given model config returns null prefix When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns null
            every { mockModelConfig.isOfficiallySupported() } returns true

            // When / Then - should not throw
            primingHook.prime()

            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockModelConfig.getModelName() }
            verify { mockModelConfig.getConfiguredPrefix() }
        }
    }

    @Nested
    inner class MultipleComponentsFailure {

        @Test
        fun `Given health check and S3 fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 failed")
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true

            // When / Then - should not throw
            primingHook.prime()

            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockModelConfig.getModelName() }
        }

        @Test
        fun `Given all components fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 failed")
            every { mockModelConfig.getModelName() } throws RuntimeException("Model config failed")

            // When / Then - should not throw
            primingHook.prime()

            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockModelConfig.getModelName() }
        }
    }

    @Nested
    inner class SnapStartEnvironmentDetection {

        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is snap-start When checking environment Then should return true`() =
            runTest {
                // Given
                val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
                every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns true
                every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
                every { mockModelConfig.getConfiguredPrefix() } returns "eu"
                every { mockModelConfig.isOfficiallySupported() } returns true

                // When
                primingHookSpy.onApplicationReady()

                // Then - prime() should be called (verify by checking if dependencies were invoked)
                verify { mockAIHealthUseCase.invoke() }
            }

        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is not snap-start When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false

            // When
            primingHookSpy.onApplicationReady()

            // Then - prime() should not be called
            verify(exactly = 0) { mockAIHealthUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.headBucket(any()) }
            verify(exactly = 0) { mockModelConfig.getModelName() }
        }
    }

    @Nested
    inner class SoapWsdlPriming {

        @Test
        fun `Given SnapStart environment When priming executes Then should warm up SOAP WSDL components`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            every { mockWsdlParser.parse(any()) } returns mockk(relaxed = true)
            every { mockWsdlSchemaReducer.reduce(any()) } returns mockk(relaxed = true)

            // When
            primingHook.prime()

            // Then
            coVerify { mockWsdlParser.parse(any()) }
            coVerify { mockWsdlSchemaReducer.reduce(any()) }
        }

        @Test
        fun `Given WSDL parser fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            coEvery { mockWsdlParser.parse(any()) } throws RuntimeException("WSDL parsing failed")
            coEvery { mockGraphQLSchemaReducer.reduce(any()) } returns mockk(relaxed = true)

            // When / Then - should not throw
            primingHook.prime()

            // Verify GraphQL priming still attempted
            coVerify { mockGraphQLSchemaReducer.reduce(any()) }
        }

        @Test
        fun `Given WSDL schema reducer fails When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            coEvery { mockWsdlParser.parse(any()) } returns mockk(relaxed = true)
            coEvery { mockWsdlSchemaReducer.reduce(any()) } throws RuntimeException("Schema reduction failed")

            // When / Then - should not throw
            primingHook.prime()

            // Verify parser was still called
            coVerify { mockWsdlParser.parse(any()) }
        }
    }

    @Nested
    inner class GraphQLPriming {

        @Test
        fun `Given SnapStart environment When priming executes Then should warm up GraphQL components`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            every { mockWsdlParser.parse(any()) } returns mockk(relaxed = true)
            every { mockWsdlSchemaReducer.reduce(any()) } returns mockk(relaxed = true)
            coEvery { mockGraphQLSchemaReducer.reduce(any()) } returns mockk(relaxed = true)

            // When
            primingHook.prime()

            // Then
            coVerify { mockGraphQLSchemaReducer.reduce(any()) }
        }

        @Test
        fun `Given GraphQL schema reducer fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            every { mockWsdlParser.parse(any()) } returns mockk(relaxed = true)
            every { mockWsdlSchemaReducer.reduce(any()) } returns mockk(relaxed = true)
            coEvery { mockGraphQLSchemaReducer.reduce(any()) } throws RuntimeException("GraphQL schema reduction failed")

            // When / Then - should not throw
            primingHook.prime()

            // Verify SOAP priming was still attempted
            coVerify { mockWsdlParser.parse(any()) }
            coVerify { mockWsdlSchemaReducer.reduce(any()) }
        }

        @Test
        fun `Given GraphQL priming fails When priming executes Then should not fail snapshot creation`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            every { mockWsdlParser.parse(any()) } returns mockk(relaxed = true)
            every { mockWsdlSchemaReducer.reduce(any()) } returns mockk(relaxed = true)
            coEvery { mockGraphQLSchemaReducer.reduce(any()) } throws IllegalStateException("GraphQL not ready")

            // When / Then - should complete without throwing
            primingHook.prime()
        }
    }
}
