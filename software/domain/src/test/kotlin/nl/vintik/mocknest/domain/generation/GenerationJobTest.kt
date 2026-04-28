package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import nl.vintik.mocknest.domain.core.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals

class GenerationJobTest {

    @Test
    fun `Should create valid GenerationJob`() {
        val now = Instant.now()
        val job = GenerationJob(
            id = "job-1",
            status = JobStatus.COMPLETED,
            request = GenerationJobRequest(
                type = GenerationType.SPECIFICATION,
                namespace = MockNamespace("test"),
                specifications = listOf(SpecificationInput("spec", "content", SpecificationFormat.OPENAPI_3)),
                descriptions = emptyList(),
                options = GenerationOptions.default()
            ),
            results = GenerationResults(1, 1, 0, listOf(mockGeneratedMock())),
            createdAt = now,
            completedAt = now.plusSeconds(10)
        )
        assertEquals("job-1", job.id)
        assertEquals(JobStatus.COMPLETED, job.status)
    }

    @Test
    fun `Should fail GenerationJob with completion before creation`() {
        val now = Instant.now()
        assertThrows<IllegalArgumentException> {
            GenerationJob(
                id = "job-1",
                status = JobStatus.COMPLETED,
                request = mockRequest(GenerationType.SPECIFICATION),
                results = null,
                createdAt = now,
                completedAt = now.minusSeconds(10)
            )
        }
    }

    @Test
    fun `Should validate GenerationJobRequest type SPECIFICATION`() {
        assertThrows<IllegalArgumentException> {
            GenerationJobRequest(
                type = GenerationType.SPECIFICATION,
                namespace = MockNamespace("test"),
                specifications = emptyList(),
                options = GenerationOptions.default()
            )
        }
    }

    @Test
    fun `Should validate GenerationJobRequest type NATURAL_LANGUAGE`() {
        assertThrows<IllegalArgumentException> {
            GenerationJobRequest(
                type = GenerationType.NATURAL_LANGUAGE,
                namespace = MockNamespace("test"),
                descriptions = emptyList(),
                options = GenerationOptions.default()
            )
        }
    }

    @Test
    fun `Should validate GenerationJobRequest type SPEC_WITH_DESCRIPTION`() {
        assertThrows<IllegalArgumentException> {
            GenerationJobRequest(
                type = GenerationType.SPEC_WITH_DESCRIPTION,
                namespace = MockNamespace("test"),
                specifications = listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)),
                descriptions = emptyList(),
                options = GenerationOptions.default()
            )
        }
    }

    @Test
    fun `Should validate GenerationResults consistency`() {
        assertThrows<IllegalArgumentException> {
            GenerationResults(1, 1, 1, emptyList()) // 1 != 1 + 1
        }
        assertThrows<IllegalArgumentException> {
            GenerationResults(1, 1, 0, emptyList()) // mocks.size != 1
        }
    }

    @Test
    fun `Should fail GenerationError with blank message`() {
        assertThrows<IllegalArgumentException> {
            GenerationError(ErrorType.UNKNOWN_ERROR, " ")
        }
    }

    private fun mockRequest(type: GenerationType) = GenerationJobRequest(
        type = type,
        namespace = MockNamespace("test"),
        specifications = if (type == GenerationType.SPECIFICATION) listOf(SpecificationInput("s", "c", SpecificationFormat.OPENAPI_3)) else emptyList(),
        descriptions = if (type == GenerationType.NATURAL_LANGUAGE) listOf("desc") else emptyList(),
        options = GenerationOptions.default()
    )

    private fun mockGeneratedMock() = GeneratedMock(
        id = "m-1",
        name = "mock",
        namespace = MockNamespace("test"),
        wireMockMapping = "{}",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "ref",
            endpoint = EndpointInfo(HttpMethod.GET, "/t", 200, "json")
        )
    )
}
