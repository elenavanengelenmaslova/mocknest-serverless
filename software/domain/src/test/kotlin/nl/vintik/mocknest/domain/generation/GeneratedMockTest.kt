package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals

class GeneratedMockTest {

    @Test
    fun `Should create valid GeneratedMock`() {
        val mock = GeneratedMock(
            id = "test-id",
            name = "test-name",
            namespace = MockNamespace(apiName = "test"),
            wireMockMapping = "{}",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "spec",
                endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
            )
        )
        assertEquals("test-id", mock.id)
        assertEquals("test-name", mock.name)
    }

    @Test
    fun `Should fail GeneratedMock with blank id`() {
        assertThrows<IllegalArgumentException> {
            GeneratedMock(
                id = "",
                name = "name",
                namespace = MockNamespace(apiName = "test"),
                wireMockMapping = "{}",
                metadata = mockMetadata()
            )
        }
    }

    @Test
    fun `Should fail MockMetadata with blank sourceReference`() {
        assertThrows<IllegalArgumentException> {
            MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = " ",
                endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
            )
        }
    }

    @Test
    fun `Should fail EndpointInfo with blank path`() {
        assertThrows<IllegalArgumentException> {
            EndpointInfo(HttpMethod.GET, "", 200, "application/json")
        }
    }

    @Test
    fun `Should fail EndpointInfo with invalid status code`() {
        assertThrows<IllegalArgumentException> {
            EndpointInfo(HttpMethod.GET, "/test", 99, "application/json")
        }
    }

    @Test
    fun `Should fail EndpointInfo with blank contentType`() {
        assertThrows<IllegalArgumentException> {
            EndpointInfo(HttpMethod.GET, "/test", 200, "")
        }
    }

    private fun mockMetadata() = MockMetadata(
        sourceType = SourceType.SPEC_WITH_DESCRIPTION,
        sourceReference = "spec",
        endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
    )
}
