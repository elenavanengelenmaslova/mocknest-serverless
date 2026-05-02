package nl.vintik.mocknest.infra.aws.runtime.snapstart

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import nl.vintik.mocknest.domain.core.HttpStatusCode
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.UUID
import java.util.stream.Stream

@Isolated
@ExtendWith(SystemStubsExtension::class)
class RuntimePrimingHookTest {

    @SystemStub
    private val environmentVariables = EnvironmentVariables()

    private val mockHealthCheckUseCase: GetRuntimeHealth = mockk(relaxed = true)
    private val mockS3Client: S3Client = mockk(relaxed = true)
    private val testBucketName = "test-bucket"
    private val mockWireMockServer: WireMockServer = mockk(relaxed = true)
    private val mockDirectCallHttpServer: DirectCallHttpServer = mockk(relaxed = true)
    private val mockJournalStore: S3RequestJournalStore = mockk(relaxed = true)
    private val primingHook = RuntimePrimingHook(
        mockHealthCheckUseCase,
        mockS3Client,
        testBucketName,
        mockWireMockServer,
        mockDirectCallHttpServer,
        mockJournalStore,
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
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

            // When
            primingHook.prime()

            // Then
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
            verify { mockWireMockServer.stubFor(any()) }
            verify { mockWireMockServer.removeStubMapping(any<UUID>()) }
            verify { mockDirectCallHttpServer.stubRequest(any()) }
            verify { mockJournalStore.suppressWrites() }
            verify { mockJournalStore.enableWrites() }
        }

        @Test
        fun `Given SnapStart environment When priming executes Then should complete without throwing exceptions`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
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
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode(503))
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
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 connection failed")
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
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } throws IllegalArgumentException("Invalid bucket configuration")
                every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

                // When / Then - should complete without throwing
                primingHook.prime()
            }

        @Test
        fun `Given S3 client timeout When priming executes Then should handle gracefully`() = runTest {
            // Given
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } throws RuntimeException("S3 timeout")
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
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
            every { mockWireMockServer.stubFor(any()) } throws RuntimeException("WireMock unavailable")

            // When / Then - should not throw
            primingHook.prime()

            // Verify other components were still attempted
            verify { mockHealthCheckUseCase.invoke() }
            coVerify { mockS3Client.headBucket(any()) }
        }

        @Test
        fun `Given WireMock removeStubMapping fails When priming executes Then should log warning and continue`() =
            runTest {
                // Given
                every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
                coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
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
        private val envVarName = "AWS_LAMBDA_INITIALIZATION_TYPE"

        @ParameterizedTest(name = "initType={0} -> shouldPrime={1}")
        @MethodSource("nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHookTest#snapStartInitTypes")
        fun `Given AWS_LAMBDA_INITIALIZATION_TYPE When onApplicationReady is called Then should invoke priming accordingly`(
            initType: String?,
            shouldPrime: Boolean,
        ) {
            // Given
            environmentVariables.set(envVarName, initType)
            every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatusCode.OK, body = "healthy")
            coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }

            // When
            primingHook.onApplicationReady()

            // Then
            if (shouldPrime) {
                verify { mockHealthCheckUseCase.invoke() }
                coVerify { mockS3Client.headBucket(any()) }
            } else {
                verify(exactly = 0) { mockHealthCheckUseCase.invoke() }
                coVerify(exactly = 0) { mockS3Client.headBucket(any()) }
            }
        }
    }

    companion object {
        @JvmStatic
        fun snapStartInitTypes(): Stream<Arguments> = Stream.of(
            Arguments.of("snap-start", true),
            Arguments.of("on-demand", false),
            Arguments.of(null, false),
        )
    }
}
