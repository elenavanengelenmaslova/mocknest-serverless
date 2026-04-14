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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Preservation Property Test — RuntimePrimingHook Priming Logic
 *
 * Verifies that `RuntimePrimingHook.prime()` still exercises the WireMock engine
 * (create stub, send request, remove stub) with journal suppression using MockK.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8**
 */
class RuntimePrimingHookPreservationTest {

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

    @Test
    fun `Given prime called When WireMock engine exercised Then stub is created before request is sent`() = runTest {
        // Given
        every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
        coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
        every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

        // When
        primingHook.prime()

        // Then — verify the WireMock exercise sequence: create stub → send request → remove stub
        io.mockk.verifyOrder {
            mockWireMockServer.stubFor(any())
            mockDirectCallHttpServer.stubRequest(any())
            mockWireMockServer.removeStubMapping(any<UUID>())
        }
    }

    @Test
    fun `Given prime called When WireMock engine exercised Then journal writes are suppressed during request`() = runTest {
        // Given
        every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
        coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
        every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

        // When
        primingHook.prime()

        // Then — verify journal suppression: suppressWrites before request, enableWrites after
        verify { mockJournalStore.suppressWrites() }
        verify { mockJournalStore.enableWrites() }
    }

    @Test
    fun `Given prime called When WireMock engine exercised Then journal writes are re-enabled even on failure`() = runTest {
        // Given — stubRequest throws to simulate failure
        every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
        coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
        every { mockDirectCallHttpServer.stubRequest(any()) } throws RuntimeException("Request failed")

        // When — prime should not throw (graceful degradation)
        primingHook.prime()

        // Then — journal writes must be re-enabled even after failure
        verify { mockJournalStore.suppressWrites() }
        verify { mockJournalStore.enableWrites() }
    }

    @ParameterizedTest(name = "Given prime called When all steps succeed Then ''{0}'' is invoked")
    @MethodSource("primingStepVerifications")
    fun `Given prime called When all steps succeed Then expected priming step is invoked`(
        stepName: String,
        verifier: (RuntimePrimingHookPreservationTest) -> Unit,
    ) = runTest {
        // Given — all steps succeed
        every { mockHealthCheckUseCase.invoke() } returns HttpResponse(HttpStatus.OK, body = "healthy")
        coEvery { mockS3Client.headBucket(any()) } returns HeadBucketResponse { }
        every { mockWireMockServer.removeStubMapping(any<UUID>()) } just Runs

        // When
        primingHook.prime()

        // Then
        verifier(this@RuntimePrimingHookPreservationTest)
    }

    companion object {
        @JvmStatic
        fun primingStepVerifications() = listOf(
            arrayOf("healthCheck.invoke()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockHealthCheckUseCase.invoke() }
            }),
            arrayOf("s3Client.headBucket()", { test: RuntimePrimingHookPreservationTest ->
                coVerify { test.mockS3Client.headBucket(any()) }
            }),
            arrayOf("wireMockServer.stubFor()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockWireMockServer.stubFor(any()) }
            }),
            arrayOf("directCallHttpServer.stubRequest()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockDirectCallHttpServer.stubRequest(any()) }
            }),
            arrayOf("wireMockServer.removeStubMapping()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockWireMockServer.removeStubMapping(any<UUID>()) }
            }),
            arrayOf("journalStore.suppressWrites()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockJournalStore.suppressWrites() }
            }),
            arrayOf("journalStore.enableWrites()", { test: RuntimePrimingHookPreservationTest ->
                verify { test.mockJournalStore.enableWrites() }
            }),
        )
    }
}
