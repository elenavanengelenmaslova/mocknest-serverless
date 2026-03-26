package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.http.Response
import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.springframework.http.HttpStatus
import java.util.*

@Isolated
class RuntimePrimingHookTest {

    private val mockHealthCheckUseCase: GetRuntimeHealth = mockk(relaxed = true)
    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val testBucketName = "test-bucket"
    private val mockWireMockServer: WireMockServer = mockk(relaxed = true)
    private val mockDirectCallHttpServer: DirectCallHttpServer = mockk(relaxed = true)
    private val primingHook = RuntimePrimingHook(
        mockHealthCheckUseCase,
        mockS3Client,
        testBucketName,
        mockWireMockServer,
        mockDirectCallHttpServer
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
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                every { status } returns 200
            }
            every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

            // When
            primingHook.prime()

            // Then
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockWireMockServer.stubFor(any()) }
            verify { mockWireMockServer.getStubMapping(any<UUID>()) }
            verify { mockDirectCallHttpServer.stubRequest(any()) }
            verify { mockWireMockServer.removeStubMapping(any<UUID>()) }
        }

        @Test
        fun `Given SnapStart environment When priming executes Then should complete without throwing exceptions`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                    every { status } returns 200
                }
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

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
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }

            // When / Then - should not throw
            primingHook.prime()

            // Verify S3 initialization still attempted
            coVerify { mockS3Client.headBucket(any()) }
        }

        @Test
        fun `Given health check returns error status When priming executes Then should continue with S3 initialization`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.SERVICE_UNAVAILABLE)
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }

                // When
                primingHook.prime()

                // Then
                verify { mockHealthCheckUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            }

        @Test
        fun `Given health check throws exception When priming executes Then should not fail snapshot creation`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } throws IllegalStateException("Service not ready")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }

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
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 connection failed")
                every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                    every { status } returns 200
                }
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

                // When / Then - should not throw
                primingHook.prime()

                // Verify health check and WireMock were still attempted
                verify { mockHealthCheckUseCase.invoke() }
                verify { mockWireMockServer.stubFor(any()) }
            }

        @Test
        fun `Given S3 client throws exception When priming executes Then should not fail snapshot creation`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws IllegalArgumentException("Invalid bucket configuration")
                every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                    every { status } returns 200
                }
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

                // When / Then - should complete without throwing
                primingHook.prime()
            }

        @Test
        fun `Given S3 client timeout When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 timeout")
            every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                every { status } returns 200
            }
            every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

            // When / Then - should not throw
            primingHook.prime()
        }
    }

    @Nested
    inner class GracefulDegradationWireMock {

        @Test
        fun `Given WireMock stubFor fails When priming executes Then should log warning and continue`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockWireMockServer.stubFor(any()) } throws RuntimeException("WireMock unavailable")

            // When / Then - should not throw
            primingHook.prime()

            // Verify other components were still attempted
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
        }

        @Test
        fun `Given WireMock getStubMapping fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockWireMockServer.getStubMapping(any<UUID>()) } throws RuntimeException("Failed to retrieve mapping")

                // When / Then - should not throw
                primingHook.prime()

                // Verify other components were still attempted
                verify { mockHealthCheckUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            }

        @Test
        fun `Given DirectCallHttpServer stubRequest fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockDirectCallHttpServer.stubRequest(any()) } throws RuntimeException("Request failed")

                // When / Then - should not throw
                primingHook.prime()

                // Verify other components were still attempted
                verify { mockHealthCheckUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            }

        @Test
        fun `Given DirectCallHttpServer stubRequest fails When priming executes Then should still attempt cleanup`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockDirectCallHttpServer.stubRequest(any()) } throws RuntimeException("Request failed")
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

                // When / Then - should not throw
                primingHook.prime()

                // Verify cleanup was still attempted despite stubRequest failure
                verify { mockWireMockServer.stubFor(any()) }
                verify { mockWireMockServer.removeStubMapping(any<UUID>()) }
            }

        @Test
        fun `Given WireMock removeStubMapping fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
                every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                    every { status } returns 200
                }
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } throws RuntimeException("Failed to remove mapping")

                // When / Then - should not throw
                primingHook.prime()

                // Verify other components were still attempted
                verify { mockHealthCheckUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            }
    }

    @Nested
    inner class BothComponentsFailure {

        @Test
        fun `Given both health check and S3 fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 failed")
            every { mockDirectCallHttpServer.stubRequest(any()) } returns mockk<Response>(relaxed = true) {
                every { status } returns 200
            }
            every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

            // When / Then - should not throw
            primingHook.prime()

            // Verify all operations were attempted
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockWireMockServer.stubFor(any()) }
        }

        @Test
        fun `Given all components fail When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } throws RuntimeException("Health check failed")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 failed")
            every { mockWireMockServer.stubFor(any()) } throws RuntimeException("WireMock failed")

            // When / Then - should not throw
            primingHook.prime()

            // Verify all operations were attempted
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockWireMockServer.stubFor(any()) }
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
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }

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
            coVerify(exactly = 0) { mockS3Client.headBucket(any()) }
        }
    }
}
