package nl.vintik.mocknest.application.generation.validators

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.EndpointInfo
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockMetadata
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.ResponseDefinition
import nl.vintik.mocknest.domain.generation.SourceType
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("unit")
class CompositeMockValidatorTest {

    private val mock = GeneratedMock(
        id = "test-id",
        name = "test-name",
        namespace = MockNamespace(apiName = "test-api"),
        wireMockMapping = "{}",
        metadata = MockMetadata(
            sourceType = SourceType.SPEC_WITH_DESCRIPTION,
            sourceReference = "spec",
            endpoint = EndpointInfo(HttpMethod.GET, "/test", 200, "application/json")
        )
    )

    private val specification = APISpecification(
        format = SpecificationFormat.OPENAPI_3,
        version = "1.0.0",
        title = "Test API",
        endpoints = listOf(
            EndpointDefinition(
                path = "/test",
                method = HttpMethod.GET,
                operationId = "getTest",
                summary = "Get test",
                parameters = emptyList(),
                requestBody = null,
                responses = mapOf(200 to ResponseDefinition(200, "OK", null))
            )
        ),
        schemas = emptyMap()
    )

    @Test
    fun `Given no validators When validating Then should return valid result`() = runTest {
        val validator = CompositeMockValidator(emptyList())

        val result = validator.validate(mock, specification)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `Given single validator returning valid When validating Then should return valid result`() = runTest {
        val delegate = mockk<MockValidatorInterface>()
        coEvery { delegate.validate(mock, specification) } returns MockValidationResult.valid()
        val validator = CompositeMockValidator(listOf(delegate))

        val result = validator.validate(mock, specification)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `Given single validator returning invalid When validating Then should return invalid with errors`() = runTest {
        val delegate = mockk<MockValidatorInterface>()
        coEvery { delegate.validate(mock, specification) } returns MockValidationResult.invalid(listOf("field mismatch"))
        val validator = CompositeMockValidator(listOf(delegate))

        val result = validator.validate(mock, specification)

        assertFalse(result.isValid)
        assertEquals(listOf("field mismatch"), result.errors)
    }

    @Test
    fun `Given multiple validators with mixed results When validating Then should aggregate all errors`() = runTest {
        val v1 = mockk<MockValidatorInterface>()
        val v2 = mockk<MockValidatorInterface>()
        coEvery { v1.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error from v1"))
        coEvery { v2.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error from v2"))
        val validator = CompositeMockValidator(listOf(v1, v2))

        val result = validator.validate(mock, specification)

        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.contains("error from v1"))
        assertTrue(result.errors.contains("error from v2"))
    }

    @Test
    fun `Given validator that throws When validating Then should handle gracefully returning no errors from that validator`() = runTest {
        val failingValidator = mockk<MockValidatorInterface>()
        coEvery { failingValidator.validate(mock, specification) } throws RuntimeException("validator crash")
        val validator = CompositeMockValidator(listOf(failingValidator))

        val result = validator.validate(mock, specification)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `Given valid and throwing validators When validating Then should include errors from non-throwing validators only`() = runTest {
        val goodValidator = mockk<MockValidatorInterface>()
        val crashingValidator = mockk<MockValidatorInterface>()
        coEvery { goodValidator.validate(mock, specification) } returns MockValidationResult.invalid(listOf("legitimate error"))
        coEvery { crashingValidator.validate(mock, specification) } throws RuntimeException("crash")
        val validator = CompositeMockValidator(listOf(goodValidator, crashingValidator))

        val result = validator.validate(mock, specification)

        assertFalse(result.isValid)
        assertEquals(listOf("legitimate error"), result.errors)
    }
}
