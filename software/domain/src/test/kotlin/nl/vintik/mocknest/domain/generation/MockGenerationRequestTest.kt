package nl.vintik.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MockGenerationRequestTest {

    @Test
    fun `Given valid parameters When creating SpecWithDescriptionRequest Then should succeed`() {
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
    fun `Given blank jobId When creating SpecWithDescriptionRequest Then should throw IllegalArgumentException`() {
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
    fun `Given no content and no URL When creating SpecWithDescriptionRequest Then should throw IllegalArgumentException`() {
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
    fun `Given blank description When creating SpecWithDescriptionRequest Then should throw IllegalArgumentException`() {
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
    fun `Given both content and URL When creating SpecWithDescriptionRequest Then should throw IllegalArgumentException`() {
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
    fun `Given default factory method When creating GenerationOptions Then should have validation enabled`() {
        val options = GenerationOptions.default()
        assertEquals(true, options.enableValidation)
    }
}
