package nl.vintik.mocknest.infra.aws.generation.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class S3GenerationStorageAdapterTest {

    private val s3Client = mockk<S3Client>()
    private val adapter = S3GenerationStorageAdapter(s3Client, "test-bucket")

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
}
