package nl.vintik.mocknest.infra.aws.generation.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.Object
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.GenerationJob
import nl.vintik.mocknest.domain.generation.GenerationJobRequest
import nl.vintik.mocknest.domain.generation.GenerationOptions
import nl.vintik.mocknest.domain.generation.GenerationType
import nl.vintik.mocknest.domain.generation.JobStatus
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.ResponseDefinition
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import nl.vintik.mocknest.domain.generation.SpecificationInput
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Preservation Property Tests — S3GenerationStorageAdapter non-delete operations
 *
 * These tests capture BASELINE behavior that must remain unchanged after the bugfix.
 * They MUST PASS on UNFIXED code — they verify non-delete operations are correct.
 *
 * **Validates: Requirements 3.6, 3.7, 3.8**
 *
 * Property: _For all non-deleteGeneratedMocks operations, the system behavior is unchanged_
 */
class S3GenerationStorageAdapterPreservationTest {

    private val s3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val adapter = S3GenerationStorageAdapter(s3Client, bucketName)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    companion object {

        /**
         * Provides diverse job/mock configurations for parameterized preservation tests.
         * Each configuration varies namespace, number of mocks, HTTP methods, and status codes.
         */
        @JvmStatic
        fun mockConfigurations(): Stream<MockConfig> = Stream.of(
            // 1. Single GET mock with simple namespace
            MockConfig(
                namespace = MockNamespace("users-api"),
                jobId = "job-single-get",
                mocks = listOf(
                    mockData("m1", "get-users", HttpMethod.GET, "/users", 200)
                )
            ),
            // 2. Multiple mocks with different methods
            MockConfig(
                namespace = MockNamespace("orders-api"),
                jobId = "job-multi-method",
                mocks = listOf(
                    mockData("m1", "list-orders", HttpMethod.GET, "/orders", 200),
                    mockData("m2", "create-order", HttpMethod.POST, "/orders", 201),
                    mockData("m3", "delete-order", HttpMethod.DELETE, "/orders/1", 204)
                )
            ),
            // 3. Client-scoped namespace with error responses
            MockConfig(
                namespace = MockNamespace("payments-api", "client-a"),
                jobId = "job-error-responses",
                mocks = listOf(
                    mockData("m1", "payment-not-found", HttpMethod.GET, "/payments/999", 404),
                    mockData("m2", "payment-conflict", HttpMethod.POST, "/payments", 409)
                )
            ),
            // 4. Single PUT mock
            MockConfig(
                namespace = MockNamespace("config-api"),
                jobId = "job-put-config",
                mocks = listOf(
                    mockData("m1", "update-config", HttpMethod.PUT, "/config", 200)
                )
            ),
            // 5. Large set of mocks
            MockConfig(
                namespace = MockNamespace("catalog-api"),
                jobId = "job-large-set",
                mocks = (1..5).map { i ->
                    mockData("m$i", "endpoint-$i", HttpMethod.GET, "/items/$i", 200)
                }
            ),
        )

        /**
         * Provides diverse GenerationJob configurations for storeJob/getJob tests.
         */
        @JvmStatic
        fun jobConfigurations(): Stream<JobConfig> = Stream.of(
            // 1. Pending specification job
            JobConfig(
                jobId = "job-pending",
                namespace = MockNamespace("api-one"),
                status = JobStatus.PENDING,
                type = GenerationType.SPECIFICATION
            ),
            // 2. Completed job
            JobConfig(
                jobId = "job-completed",
                namespace = MockNamespace("api-two"),
                status = JobStatus.COMPLETED,
                type = GenerationType.SPECIFICATION
            ),
            // 3. Failed job
            JobConfig(
                jobId = "job-failed",
                namespace = MockNamespace("api-three"),
                status = JobStatus.FAILED,
                type = GenerationType.SPECIFICATION
            ),
            // 4. In-progress job with client namespace
            JobConfig(
                jobId = "job-in-progress",
                namespace = MockNamespace("api-four", "team-b"),
                status = JobStatus.IN_PROGRESS,
                type = GenerationType.SPECIFICATION
            ),
            // 5. Spec-with-description job
            JobConfig(
                jobId = "job-spec-desc",
                namespace = MockNamespace("api-five"),
                status = JobStatus.PENDING,
                type = GenerationType.SPEC_WITH_DESCRIPTION
            ),
        )

        private fun mockData(
            id: String,
            name: String,
            method: HttpMethod,
            path: String,
            statusCode: Int
        ): MockData = MockData(id, name, method, path, statusCode)
    }

    data class MockConfig(
        val namespace: MockNamespace,
        val jobId: String,
        val mocks: List<MockData>
    ) {
        override fun toString(): String = "$jobId (${mocks.size} mocks, ns=${namespace.displayName()})"
    }

    data class MockData(
        val id: String,
        val name: String,
        val method: HttpMethod,
        val path: String,
        val statusCode: Int
    )

    data class JobConfig(
        val jobId: String,
        val namespace: MockNamespace,
        val status: JobStatus,
        val type: GenerationType
    ) {
        override fun toString(): String = "$jobId (status=$status, ns=${namespace.displayName()})"
    }

    private fun MockData.toGeneratedMock(namespace: MockNamespace): GeneratedMock = GeneratedMock(
        id = id,
        name = name,
        namespace = namespace,
        wireMockMapping = """{"request":{"method":"${method.name}","url":"$path"},"response":{"status":$statusCode}}""",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "test-spec",
            endpoint = EndpointInfo(method, path, statusCode, "application/json")
        )
    )

    @Nested
    inner class StoreGeneratedMocksPreservation {

        /**
         * Given diverse mock configurations When storeGeneratedMocks() is called
         * Then s3Client.putObject() is called for each mock and the results file.
         *
         * Property: _For all storeGeneratedMocks operations, the system calls putObject()
         * for each mock individually plus one results summary_
         */
        @ParameterizedTest(name = "Given {0} When storeGeneratedMocks Then should call putObject for each mock and results")
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapterPreservationTest#mockConfigurations")
        fun `Given diverse mocks When storeGeneratedMocks Then should call putObject for each mock and results`(config: MockConfig) = runBlocking {
            // Given
            val mocks = config.mocks.map { it.toGeneratedMock(config.namespace) }
            coEvery { s3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            // When
            val result = adapter.storeGeneratedMocks(mocks, config.jobId)

            // Then — result is the expected storage key
            assertNotNull(result)

            // putObject called for each mock + 1 results file
            val expectedCalls = config.mocks.size + 1
            coVerify(exactly = expectedCalls) {
                s3Client.putObject(any<PutObjectRequest>())
            }

            // Verify each mock was stored with correct key pattern
            config.mocks.forEach { mockData ->
                coVerify(exactly = 1) {
                    s3Client.putObject(match<PutObjectRequest> {
                        it.key?.contains("mocks/${mockData.id}.json") == true
                    })
                }
            }

            // Verify results file was stored
            coVerify(exactly = 1) {
                s3Client.putObject(match<PutObjectRequest> {
                    it.key?.contains("results.json") == true
                })
            }
        }
    }

    @Nested
    inner class GetGeneratedMocksPreservation {

        /**
         * Given diverse mock configurations stored in S3 When getGeneratedMocks() is called
         * Then all mocks are retrieved and deserialized correctly.
         *
         * Property: _For all getGeneratedMocks operations, the system retrieves and
         * deserializes all stored mocks correctly_
         */
        @ParameterizedTest(name = "Given {0} When getGeneratedMocks Then should retrieve and deserialize all mocks")
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapterPreservationTest#mockConfigurations")
        fun `Given stored mocks When getGeneratedMocks Then should retrieve all correctly`(config: MockConfig) = runBlocking {
            // Given — set up findJobKey
            val jobPrefix = "${config.namespace.toPrefix()}/generated-mocks/jobs/${config.jobId}"

            val findJobResponse = ListObjectsV2Response {
                contents = listOf(
                    Object { key = "${config.namespace.toPrefix()}/jobs/${config.jobId}/metadata.json" }
                )
            }
            coEvery {
                s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix == "mocknest/" })
            } returns findJobResponse

            // Set up mock objects listing
            val mockObjects = config.mocks.map { mockData ->
                Object { key = "${config.namespace.toPrefix()}/jobs/${config.jobId}/mocks/${mockData.id}.json" }
            }
            val mocksListResponse = ListObjectsV2Response {
                contents = mockObjects
            }
            coEvery {
                s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix?.contains("mocks/") == true })
            } returns mocksListResponse

            // Set up getObject to return serialized mocks
            val generatedMocks = config.mocks.map { it.toGeneratedMock(config.namespace) }
            val mockJsonMap = generatedMocks.associateBy(
                { "${config.namespace.toPrefix()}/jobs/${config.jobId}/mocks/${it.id}.json" },
                { mapper.writeValueAsString(it) }
            )

            coEvery {
                s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>())
            } coAnswers {
                val request = firstArg<GetObjectRequest>()
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                val json = mockJsonMap[request.key] ?: """{}"""
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(json.toByteArray())
                })
            }

            // When
            val result = adapter.getGeneratedMocks(config.jobId)

            // Then — all mocks retrieved
            assertEquals(config.mocks.size, result.size, "Expected ${config.mocks.size} mocks to be retrieved")

            // Verify each mock was deserialized with correct ID
            config.mocks.forEach { mockData ->
                val found = result.any { it.id == mockData.id }
                assert(found) { "Expected mock with id '${mockData.id}' to be in results" }
            }
        }
    }

    @Nested
    inner class StoreJobPreservation {

        /**
         * Given diverse job configurations When storeJob() is called
         * Then s3Client.putObject() is called with the correct key pattern.
         *
         * Property: _For all storeJob operations, the system stores job metadata
         * at the correct S3 key_
         */
        @ParameterizedTest(name = "Given {0} When storeJob Then should store at correct key")
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapterPreservationTest#jobConfigurations")
        fun `Given diverse jobs When storeJob Then should call putObject with correct key`(config: JobConfig) = runBlocking {
            // Given
            val job = createJob(config)
            coEvery { s3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            // When
            val result = adapter.storeJob(job)

            // Then — correct key returned
            val expectedKey = "${config.namespace.toPrefix()}/jobs/${config.jobId}/metadata.json"
            assertEquals(expectedKey, result)

            // putObject called exactly once
            coVerify(exactly = 1) {
                s3Client.putObject(match<PutObjectRequest> {
                    it.key == expectedKey && it.bucket == bucketName
                })
            }
        }
    }

    @Nested
    inner class GetJobPreservation {

        /**
         * Given diverse job configurations stored in S3 When getJob() is called
         * Then the job is retrieved and deserialized correctly.
         *
         * Property: _For all getJob operations, the system retrieves and deserializes
         * job metadata correctly_
         */
        @ParameterizedTest(name = "Given {0} When getJob Then should retrieve and deserialize correctly")
        @MethodSource("nl.vintik.mocknest.infra.aws.generation.storage.S3GenerationStorageAdapterPreservationTest#jobConfigurations")
        fun `Given stored job When getJob Then should retrieve correctly`(config: JobConfig) = runBlocking {
            // Given
            val job = createJob(config)
            val jobJson = mapper.writeValueAsString(job)

            // Mock findJobKey
            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object { key = "${config.namespace.toPrefix()}/jobs/${config.jobId}/metadata.json" }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            // Mock getObject
            coEvery {
                s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>())
            } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(jobJson.toByteArray())
                })
            }

            // When
            val result = adapter.getJob(config.jobId)

            // Then
            assertNotNull(result)
            assertEquals(config.jobId, result.id)
            assertEquals(config.status, result.status)
        }
    }

    private fun createJob(config: JobConfig): GenerationJob {
        val descriptions = if (config.type == GenerationType.SPEC_WITH_DESCRIPTION) {
            listOf("Generate mocks for testing")
        } else {
            emptyList()
        }

        return GenerationJob(
            id = config.jobId,
            status = config.status,
            request = GenerationJobRequest(
                type = config.type,
                namespace = config.namespace,
                specifications = listOf(
                    SpecificationInput("test-spec", "openapi: 3.0.0", SpecificationFormat.OPENAPI_3)
                ),
                descriptions = descriptions,
                options = GenerationOptions.default()
            ),
            results = null,
            createdAt = Instant.now()
        )
    }
}
