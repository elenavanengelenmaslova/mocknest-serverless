package nl.vintik.mocknest.infra.aws.generation.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectsResponse
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.Object
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Bug Condition Exploration Test 1c — `S3GenerationStorageAdapter.deleteGeneratedMocks()` uses sequential deletes
 *
 * These tests PROVE the bug exists on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bug exists.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * **Validates: Requirements 1.4, 2.4**
 *
 * Bug Condition: `deleteGeneratedMocks()` iterates over mock objects and calls
 * `s3Client.deleteObject()` one-by-one instead of using S3's batch `deleteObjects()` API.
 */
class S3GenerationStorageAdapterBugConditionTest {

    private val s3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val adapter = S3GenerationStorageAdapter(s3Client, bucketName)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Given 3 mock objects for a job When deleteGeneratedMocks() is called
     * Then s3Client.deleteObjects() (batch API) should be called at least once
     * and s3Client.deleteObject() (individual API) should NOT be called.
     *
     * EXPECTED TO FAIL on unfixed code:
     * - `s3Client.deleteObjects()` is never called (0 invocations)
     * - `s3Client.deleteObject()` is called 4 times (3 mocks + 1 results file)
     *
     * Counterexample: `deleteGeneratedMocks("job-123")` with 3 mocks calls
     * `deleteObject()` 4 times (3 mocks + 1 results) instead of `deleteObjects()` once.
     */
    @Test
    fun `Given 3 mock objects When deleteGeneratedMocks called Then should use batch deleteObjects not individual deleteObject`() = runBlocking {
        // Given
        val jobId = "job-123"
        val jobPrefix = "mocknest/test-api/jobs/$jobId"

        // Mock findJobKey — listObjectsV2 with prefix "mocknest/" returns the job metadata key
        val findJobResponse = ListObjectsV2Response {
            contents = listOf(
                Object { key = "$jobPrefix/metadata.json" }
            )
        }
        coEvery {
            s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix == "mocknest/" })
        } returns findJobResponse

        // Mock listObjectsV2 for mocks prefix — returns 3 mock objects
        val mocksListResponse = ListObjectsV2Response {
            contents = listOf(
                Object { key = "$jobPrefix/mocks/m1.json" },
                Object { key = "$jobPrefix/mocks/m2.json" },
                Object { key = "$jobPrefix/mocks/m3.json" },
            )
        }
        coEvery {
            s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix?.contains("mocks/") == true })
        } returns mocksListResponse

        // Stub both individual and batch delete responses
        coEvery { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}
        coEvery { s3Client.deleteObjects(any<DeleteObjectsRequest>()) } returns DeleteObjectsResponse {}

        // When
        adapter.deleteGeneratedMocks(jobId)

        // Then — EXPECTED TO FAIL on unfixed code:
        // Batch deleteObjects() should be called at least once (unfixed code never calls it)
        coVerify(atLeast = 1) {
            s3Client.deleteObjects(any<DeleteObjectsRequest>())
        }

        // Individual deleteObject() should NOT be called for mock objects (unfixed code calls it 4 times)
        coVerify(exactly = 0) {
            s3Client.deleteObject(any<DeleteObjectRequest>())
        }
    }
}
