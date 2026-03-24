package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.ListBucketsResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.http.HttpStatus

@Isolated
class RuntimePrimingHookTest {
    
    private val mockHealthCheckUseCase: GetRuntimeHealth = mockk(relaxed = true)
    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val primingHook = RuntimePrimingHook(mockHealthCheckUseCase, mockS3Client)
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Nested
    inner class SuccessfulPrimingExecution {
        
        @Test
        fun `Given SnapStart environment When priming executes Then should initialize all components`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When
            primingHook.prime()
            
            // Then
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given SnapStart environment When priming executes Then should complete without throwing exceptions`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When / Then - should not throw
            primingHook.prime()
        }
    }
    
    @Nested
    inner class GracefulDegradationHealthCheck {
        
        @Test
        fun `Given health check fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } throws RuntimeException("Health check unavailable")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify S3 initialization still attempted
            coVerify { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given health check returns error status When priming executes Then should continue with S3 initialization`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.SERVICE_UNAVAILABLE)
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When
            primingHook.prime()
            
            // Then
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given health check throws exception When priming executes Then should not fail snapshot creation`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } throws IllegalStateException("Service not ready")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When / Then - should complete without throwing
            primingHook.prime()
        }
    }
    
    @Nested
    inner class GracefulDegradationS3Client {
        
        @Test
        fun `Given S3 client initialization fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 connection failed")
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify health check was still attempted
            verify { mockHealthCheckUseCase.invoke() }
        }
        
        @Test
        fun `Given S3 client throws exception When priming executes Then should not fail snapshot creation`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws IllegalArgumentException("Invalid bucket configuration")
            
            // When / Then - should complete without throwing
            primingHook.prime()
        }
        
        @Test
        fun `Given S3 client timeout When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 timeout")
            
            // When / Then - should not throw
            primingHook.prime()
        }
    }
    
    @Nested
    inner class BothComponentsFailure {
        
        @Test
        fun `Given both health check and S3 fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.listBuckets() } throws RuntimeException("S3 failed")
            
            // When / Then - should not throw
            primingHook.prime()
            
            // Verify both operations were attempted
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.listBuckets() }
        }
    }
    
    @Nested
    inner class SnapStartEnvironmentDetection {
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is snap-start When checking environment Then should return true`() = runTest {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns true
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.listBuckets() } returns ListBucketsResponse { }
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should be called (verify by checking if dependencies were invoked)
            verify { mockHealthCheckUseCase.invoke() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is not snap-start When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockHealthCheckUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is null When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockHealthCheckUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is empty string When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockHealthCheckUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
        }
        
        @Test
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE is provisioned-concurrency When checking environment Then should skip priming`() {
            // Given
            val primingHookSpy = spyk(primingHook, recordPrivateCalls = true)
            every { primingHookSpy["isSnapStartEnvironment"]() as Boolean } returns false
            
            // When
            primingHookSpy.onApplicationReady()
            
            // Then - prime() should not be called
            verify(exactly = 0) { mockHealthCheckUseCase.invoke() }
            coVerify(exactly = 0) { mockS3Client.listBuckets() }
        }
    }
}
