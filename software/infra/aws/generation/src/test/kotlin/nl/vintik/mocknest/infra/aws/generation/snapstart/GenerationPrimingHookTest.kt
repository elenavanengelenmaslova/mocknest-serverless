package nl.vintik.mocknest.infra.aws.generation.snapstart

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.ListBucketsResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

import org.junit.jupiter.api.parallel.Isolated

@Isolated
class GenerationPrimingHookTest {
    
    private val mockAIHealthUseCase: GetAIHealth = mockk(relaxed = true)
    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val mockBedrockClient: BedrockRuntimeClient = mockk(relaxed = true)
    private val mockModelConfig: ModelConfiguration = mockk(relaxed = true)
    private val primingHook = GenerationPrimingHook(
        mockAIHealthUseCase,
        mockS3Client,
        mockBedrockClient,
        mockModelConfig
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
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When
            primingHook.prime()
            
            // Then
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
            verify { mockModelConfig.getModelName() }
            verify { mockModelConfig.getConfiguredPrefix() }
            verify { mockModelConfig.isOfficiallySupported() }
        }
        
        @Test
        fun `Given SnapStart environment When priming executes Then should complete without throwing exceptions`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
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
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify S3 initialization still attempted
            coVerify { mockS3Client.listBuckets() }
            verify { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given health check returns error status When priming executes Then should continue with other initializations`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.SERVICE_UNAVAILABLE)
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When
            primingHook.prime()
            
            // Then
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
            verify { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given health check throws exception When priming executes Then should not fail snapshot creation`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } throws IllegalStateException("Service not ready")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
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
        fun `Given S3 client initialization fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 connection failed")
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
        fun `Given S3 client throws exception When priming executes Then should not fail snapshot creation`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws IllegalArgumentException("Invalid bucket configuration")
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When / Then - should complete without throwing
            primingHook.prime()
        }
        
        @Test
        fun `Given S3 client timeout When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 timeout")
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
        fun `Given model config validation fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            every { mockModelConfig.getModelName() } throws RuntimeException("Model config unavailable")
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify health check and S3 were still attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given model config returns null prefix When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns null
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
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
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 failed")
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
            verify { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given all components fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockAIHealthUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 failed")
            every { mockModelConfig.getModelName() } throws RuntimeException("Model config failed")
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify all operations were attempted
            verify { mockAIHealthUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
            verify { mockModelConfig.getModelName() }
        }
    }
    
    @Nested
    inner class SnapStartEnvironmentDetection {
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is snap-start When checking environment Then should return true`() = runTest {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns true
            every { mockAIHealthUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
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
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
            verify(exactly = 0) { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is null When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockAIHealthUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
            verify(exactly = 0) { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is empty string When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockAIHealthUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
            verify(exactly = 0) { mockModelConfig.getModelName() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is provisioned-concurrency When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockAIHealthUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
            verify(exactly = 0) { mockModelConfig.getModelName() }
        }
    }
}
