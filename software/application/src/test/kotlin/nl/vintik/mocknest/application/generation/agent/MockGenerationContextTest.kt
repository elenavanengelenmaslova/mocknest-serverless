package nl.vintik.mocknest.application.generation.agent

import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SpecWithDescriptionRequest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockGenerationContextTest {

    @Test
    fun `Should create and copy context`() {
        val request = SpecWithDescriptionRequest(
            namespace = MockNamespace("test"),
            specificationContent = "spec",
            format = SpecificationFormat.OPENAPI_3,
            description = "desc"
        )
        val spec = mockk<APISpecification>()
        val context = MockGenerationContext(request, spec)
        
        assertEquals(1, context.attempt)
        assertTrue(context.mocks.isEmpty())
        
        val newContext = context.copy(attempt = 2, errors = listOf("err"))
        assertEquals(2, newContext.attempt)
        assertEquals(1, newContext.errors.size)
    }
}
