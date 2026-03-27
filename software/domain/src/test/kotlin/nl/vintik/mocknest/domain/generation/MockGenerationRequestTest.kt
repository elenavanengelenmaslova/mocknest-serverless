package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MockGenerationRequestTest {

    @Test
    fun `Should create valid SpecWithDescriptionRequest`() {
        val request = SpecWithDescriptionRequest(
            namespace = MockNamespace("test"),
            specificationContent = "content",
            format = SpecificationFormat.OPENAPI_3,
            description = "desc"
        )
        assertNotNull(request.jobId)
        assertEquals("desc", request.description)
    }

    @Test
    fun `Should fail SpecWithDescriptionRequest with blank jobId`() {
        assertThrows<IllegalArgumentException> {
            SpecWithDescriptionRequest(
                jobId = "",
                namespace = MockNamespace("test"),
                specificationContent = "content",
                format = SpecificationFormat.OPENAPI_3,
                description = "desc"
            )
        }
    }

    @Test
    fun `Should fail SpecWithDescriptionRequest without content and url`() {
        assertThrows<IllegalArgumentException> {
            SpecWithDescriptionRequest(
                namespace = MockNamespace("test"),
                specificationContent = null,
                specificationUrl = null,
                format = SpecificationFormat.OPENAPI_3,
                description = "desc"
            )
        }
    }

    @Test
    fun `Should fail SpecWithDescriptionRequest with blank description`() {
        assertThrows<IllegalArgumentException> {
            SpecWithDescriptionRequest(
                namespace = MockNamespace("test"),
                specificationContent = "content",
                format = SpecificationFormat.OPENAPI_3,
                description = " "
            )
        }
    }

    @Test
    fun `Should fail SpecWithDescriptionRequest when both content and url are present`() {
        assertThrows<IllegalArgumentException> {
            SpecWithDescriptionRequest(
                namespace = MockNamespace("test"),
                specificationContent = "content",
                specificationUrl = "https://example.com/spec",
                format = SpecificationFormat.OPENAPI_3,
                description = "desc"
            )
        }
    }

    @Test
    fun `Should create default GenerationOptions`() {
        val options = GenerationOptions.default()
        assertEquals(true, options.enableValidation)
    }
}
