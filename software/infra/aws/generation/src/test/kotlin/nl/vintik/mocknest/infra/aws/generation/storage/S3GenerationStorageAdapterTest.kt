package nl.vintik.mocknest.infra.aws.generation.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.*
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.*

class S3GenerationStorageAdapterTest {

    private val s3Client = mockk<S3Client>()
    private val adapter = S3GenerationStorageAdapter(s3Client, "test-bucket")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Should serialize GeneratedMock`() {
        val namespace = MockNamespace("test-api")
        val mock = GeneratedMock(
            id = "m1",
            name = "mock",
            namespace = namespace,
            wireMockMapping = "{}",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "ref",
                endpoint = EndpointInfo(HttpMethod.GET, "/t", 200, "application/json")
            )
        )
        val json = nl.vintik.mocknest.application.core.mapper.writeValueAsString(mock)
        assertNotNull(json)
    }

    @Test
    fun `Should store generated mocks`() = runBlocking {
        val namespace = MockNamespace("test-api")
        val mock = GeneratedMock(
            id = "m1",
            name = "mock",
            namespace = namespace,
            wireMockMapping = "{}",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "ref",
                endpoint = EndpointInfo(HttpMethod.GET, "/t", 200, "application/json")
            )
        )

        coEvery { s3Client.putObject(any()) } returns PutObjectResponse {}

        val result = adapter.storeGeneratedMocks(listOf(mock), "job-1")

        assertNotNull(result)
        assertEquals("mocknest/test-api/generated-mocks/jobs/job-1", result)
    }

    @Test
    fun `Should store specification`() = runBlocking {
        val namespace = MockNamespace("test-api")
        val spec = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0",
            title = "Test",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/test",
                    method = HttpMethod.GET,
                    operationId = null,
                    summary = null,
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                )
            ),
            schemas = emptyMap()
        )

        coEvery { s3Client.putObject(any()) } returns PutObjectResponse {}

        val result = adapter.storeSpecification(namespace, spec, "v1")

        assertEquals("mocknest/test-api/api-specs/current.json", result)
    }

    @Test
    fun `Should store job`() = runBlocking {
        val job = GenerationJob(
            id = "job-1",
            status = JobStatus.PENDING,
            request = GenerationJobRequest(
                type = GenerationType.SPECIFICATION,
                namespace = MockNamespace("test-api"),
                specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                options = GenerationOptions.default()
            ),
            results = null,
            createdAt = java.time.Instant.now()
        )

        coEvery { s3Client.putObject(any()) } returns PutObjectResponse {}

        val result = adapter.storeJob(job)

        assertEquals("mocknest/test-api/jobs/job-1/metadata.json", result)
    }
    
    @Test
    fun `Should check health`() = runBlocking {
        coEvery { s3Client.listObjectsV2(any()) } returns mockk()

        val healthy = adapter.isHealthy()

        assertEquals(true, healthy)
    }

    @Nested
    inner class GetGeneratedMocks {

        @Test
        fun `Given stored mocks When getting mocks Then should retrieve and deserialize them`() = runBlocking {
            // Given
            val jobId = "job-123"
            val namespace = MockNamespace("test-api")
            val mock = GeneratedMock(
                id = "m1",
                name = "test-mock",
                namespace = namespace,
                wireMockMapping = "{}",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "ref",
                    endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
                )
            )
            val mockJson = mapper.writeValueAsString(mock)

            // Mock findJobKey
            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            // Mock getObject for mocks
            val mocksListResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/mocks/m1.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix?.contains("mocks/") == true }) } returns mocksListResponse

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(mockJson.toByteArray())
                })
            }

            // When
            val result = adapter.getGeneratedMocks(jobId)

            // Then
            assertEquals(1, result.size)
            assertEquals("m1", result[0].id)
            assertEquals("test-mock", result[0].name)
        }

        @Test
        fun `Given no mocks When getting mocks Then should return empty list`() = runBlocking {
            // Given
            val jobId = "job-123"

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            val emptyListResponse = ListObjectsV2Response {
                contents = emptyList()
            }
            coEvery { s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix?.contains("mocks/") == true }) } returns emptyListResponse

            // When
            val result = adapter.getGeneratedMocks(jobId)

            // Then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class GetSpecification {

        @Test
        fun `Given stored current spec When getting spec without version Then should retrieve current`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")
            val spec = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = "1.0",
                title = "Test API",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/test",
                        method = HttpMethod.GET,
                        operationId = null,
                        summary = null,
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                    )
                ),
                schemas = emptyMap()
            )
            val specJson = mapper.writeValueAsString(spec)

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(specJson.toByteArray())
                })
            }

            // When
            val result = adapter.getSpecification(namespace, null)

            // Then
            assertNotNull(result)
            assertEquals("Test API", result.title)
            assertEquals("1.0", result.version)
        }

        @Test
        fun `Given stored versioned spec When getting spec with version Then should retrieve versioned spec`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")
            val version = "2.0"
            val spec = APISpecification(
                format = SpecificationFormat.OPENAPI_3,
                version = version,
                title = "Test API v2",
                endpoints = listOf(
                    EndpointDefinition(
                        path = "/test",
                        method = HttpMethod.GET,
                        operationId = null,
                        summary = null,
                        parameters = emptyList(),
                        requestBody = null,
                        responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                    )
                ),
                schemas = emptyMap()
            )
            val specJson = mapper.writeValueAsString(spec)

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(specJson.toByteArray())
                })
            }

            // When
            val result = adapter.getSpecification(namespace, version)

            // Then
            assertNotNull(result)
            assertEquals("Test API v2", result.title)
            assertEquals(version, result.version)
        }

        @Test
        fun `Given no spec When getting spec Then should return null`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } throws NoSuchKey {}

            // When
            val result = adapter.getSpecification(namespace, null)

            // Then
            assertNull(result)
        }
    }

    @Nested
    inner class GetJob {

        @Test
        fun `Given stored job When getting job Then should retrieve and deserialize it`() = runBlocking {
            // Given
            val jobId = "job-123"
            val job = GenerationJob(
                id = jobId,
                status = JobStatus.COMPLETED,
                request = GenerationJobRequest(
                    type = GenerationType.SPECIFICATION,
                    namespace = MockNamespace("test-api"),
                    specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                    options = GenerationOptions.default()
                ),
                results = null,
                createdAt = Instant.now()
            )
            val jobJson = mapper.writeValueAsString(job)

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(jobJson.toByteArray())
                })
            }

            // When
            val result = adapter.getJob(jobId)

            // Then
            assertNotNull(result)
            assertEquals(jobId, result.id)
            assertEquals(JobStatus.COMPLETED, result.status)
        }

        @Test
        fun `Given no job When getting job Then should return null`() = runBlocking {
            // Given
            val jobId = "non-existent"

            val emptyListResponse = ListObjectsV2Response {
                contents = emptyList()
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns emptyListResponse

            // When
            val result = adapter.getJob(jobId)

            // Then
            assertNull(result)
        }
    }

    @Nested
    inner class UpdateJobStatus {

        @Test
        fun `Given existing job When updating status to completed Then should update with completion time`() = runBlocking {
            // Given
            val jobId = "job-123"
            val job = GenerationJob(
                id = jobId,
                status = JobStatus.IN_PROGRESS,
                request = GenerationJobRequest(
                    type = GenerationType.SPECIFICATION,
                    namespace = MockNamespace("test-api"),
                    specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                    options = GenerationOptions.default()
                ),
                results = null,
                createdAt = Instant.now(),
                completedAt = null
            )
            val jobJson = mapper.writeValueAsString(job)

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(jobJson.toByteArray())
                })
            }

            coEvery { s3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            // When
            adapter.updateJobStatus(jobId, JobStatus.COMPLETED, null)

            // Then
            coVerify { s3Client.putObject(any<PutObjectRequest>()) }
        }
    }

    @Nested
    inner class StoreJobResults {

        @Test
        fun `Given job results When storing Then should save to S3`() = runBlocking {
            // Given
            val jobId = "job-123"
            val mock1 = GeneratedMock(
                id = "m1",
                name = "mock1",
                namespace = MockNamespace("test-api"),
                wireMockMapping = "{}",
                metadata = MockMetadata(
                    sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                    sourceReference = "ref",
                    endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
                )
            )
            val results = GenerationResults(
                totalGenerated = 5,
                successful = 4,
                failed = 1,
                generatedMocks = listOf(mock1, mock1, mock1, mock1),
                errors = emptyList()
            )

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse
            coEvery { s3Client.putObject(any<PutObjectRequest>()) } returns PutObjectResponse {}

            // When
            adapter.storeJobResults(jobId, results)

            // Then
            coVerify { s3Client.putObject(match<PutObjectRequest> { it.key?.contains("results.json") == true }) }
        }
    }

    @Nested
    inner class ListSpecifications {

        @Test
        fun `Given stored specifications When listing Then should return metadata`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/api-specs/versions/1.0.json"
                    },
                    Object {
                        key = "mocknest/test-api/api-specs/versions/2.0.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            // When
            val result = adapter.listSpecifications(namespace)

            // Then
            assertEquals(2, result.size)
            assertTrue(result.any { it.version == "1.0" })
            assertTrue(result.any { it.version == "2.0" })
        }

        @Test
        fun `Given no specifications When listing Then should return empty list`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")

            val emptyListResponse = ListObjectsV2Response {
                contents = emptyList()
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns emptyListResponse

            // When
            val result = adapter.listSpecifications(namespace)

            // Then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ListJobs {

        @Test
        fun `Given stored jobs When listing Then should return jobs`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")
            val job1 = GenerationJob(
                id = "job-1",
                status = JobStatus.COMPLETED,
                request = GenerationJobRequest(
                    type = GenerationType.SPECIFICATION,
                    namespace = namespace,
                    specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                    options = GenerationOptions.default()
                ),
                results = null,
                createdAt = Instant.now()
            )
            val job1Json = mapper.writeValueAsString(job1)

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/job-1/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(job1Json.toByteArray())
                })
            }

            // When
            val result = adapter.listJobs(namespace, null, 10)

            // Then
            assertEquals(1, result.size)
            assertEquals("job-1", result[0].id)
        }

        @Test
        fun `Given jobs with filter When listing Then should return filtered jobs`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")
            val completedJob = GenerationJob(
                id = "job-completed",
                status = JobStatus.COMPLETED,
                request = GenerationJobRequest(
                    type = GenerationType.SPECIFICATION,
                    namespace = namespace,
                    specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                    options = GenerationOptions.default()
                ),
                results = null,
                createdAt = Instant.now()
            )
            val completedJobJson = mapper.writeValueAsString(completedJob)

            val listResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/job-completed/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns listResponse

            coEvery { s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Unit>()) } coAnswers {
                val handler = secondArg<suspend (GetObjectResponse) -> Unit>()
                handler(GetObjectResponse {
                    body = ByteStream.fromBytes(completedJobJson.toByteArray())
                })
            }

            // When
            val result = adapter.listJobs(namespace, JobStatus.COMPLETED, 10)

            // Then
            assertEquals(1, result.size)
            assertEquals(JobStatus.COMPLETED, result[0].status)
        }
    }

    @Nested
    inner class DeleteGeneratedMocks {

        @Test
        fun `Given stored mocks When deleting Then should delete all mock objects and results`() = runBlocking {
            // Given
            val jobId = "job-123"

            val findJobResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/metadata.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix == "mocknest/" }) } returns findJobResponse

            val mocksListResponse = ListObjectsV2Response {
                contents = listOf(
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/mocks/m1.json"
                    },
                    Object {
                        key = "mocknest/test-api/jobs/$jobId/mocks/m2.json"
                    }
                )
            }
            coEvery { s3Client.listObjectsV2(match<ListObjectsV2Request> { it.prefix?.contains("mocks/") == true }) } returns mocksListResponse

            coEvery { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}

            // When
            val result = adapter.deleteGeneratedMocks(jobId)

            // Then
            assertTrue(result)
            coVerify(exactly = 3) { s3Client.deleteObject(any<DeleteObjectRequest>()) } // 2 mocks + 1 results file
        }

        @Test
        fun `Given non-existent job When deleting mocks Then should return false`() = runBlocking {
            // Given
            val jobId = "non-existent"

            val emptyListResponse = ListObjectsV2Response {
                contents = emptyList()
            }
            coEvery { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns emptyListResponse

            // When
            val result = adapter.deleteGeneratedMocks(jobId)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    inner class DeleteSpecification {

        @Test
        fun `Given versioned spec When deleting with version Then should delete versioned file`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")
            val version = "1.0"

            coEvery { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}

            // When
            val result = adapter.deleteSpecification(namespace, version)

            // Then
            assertTrue(result)
            coVerify { s3Client.deleteObject(match<DeleteObjectRequest> { it.key?.contains("versions/$version") == true }) }
        }

        @Test
        fun `Given current spec When deleting without version Then should delete current file`() = runBlocking {
            // Given
            val namespace = MockNamespace("test-api")

            coEvery { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse {}

            // When
            val result = adapter.deleteSpecification(namespace, null)

            // Then
            assertTrue(result)
            coVerify { s3Client.deleteObject(match<DeleteObjectRequest> { it.key?.contains("current.json") == true }) }
        }
    }
}
