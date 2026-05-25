package nl.vintik.mocknest.application.generation.validators

import io.mockk.coEvery
import io.mockk.clearMocks
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("unit")
class CompositeMockValidatorTest {

    private val mockValidator1: MockValidatorInterface = mockk(relaxed = true)
    private val mockValidator2: MockValidatorInterface = mockk(relaxed = true)
    private val mockValidator3: MockValidatorInterface = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearMocks(mockValidator1, mockValidator2, mockValidator3)
    }

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

    @Nested
    inner class BasicValidation {

        @Test
        fun `Given no validators When validating Then should return valid result`() = runTest {
            val validator = CompositeMockValidator(emptyList())

            val result = validator.validate(mock, specification)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given single validator returning valid When validating Then should return valid result`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.valid()
            val validator = CompositeMockValidator(listOf(mockValidator1))

            val result = validator.validate(mock, specification)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given single validator returning invalid When validating Then should return invalid with errors`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.invalid(listOf("field mismatch"))
            val validator = CompositeMockValidator(listOf(mockValidator1))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(listOf("field mismatch"), result.errors)
        }
    }

    @Nested
    inner class ErrorAggregation {

        @Test
        fun `Given multiple validators with mixed results When validating Then should aggregate all errors`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error from v1"))
            coEvery { mockValidator2.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error from v2"))
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors.contains("error from v1"))
            assertTrue(result.errors.contains("error from v2"))
        }

        @Test
        fun `Given three validators with multiple errors each When validating Then should aggregate all errors from all validators`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.invalid(
                listOf("v1 error 1", "v1 error 2")
            )
            coEvery { mockValidator2.validate(mock, specification) } returns MockValidationResult.valid()
            coEvery { mockValidator3.validate(mock, specification) } returns MockValidationResult.invalid(
                listOf("v3 error 1")
            )
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2, mockValidator3))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(3, result.errors.size)
            assertTrue(result.errors.contains("v1 error 1"))
            assertTrue(result.errors.contains("v1 error 2"))
            assertTrue(result.errors.contains("v3 error 1"))
        }

        @Test
        fun `Given all validators returning valid When validating Then should return valid`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.valid()
            coEvery { mockValidator2.validate(mock, specification) } returns MockValidationResult.valid()
            coEvery { mockValidator3.validate(mock, specification) } returns MockValidationResult.valid()
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2, mockValidator3))

            val result = validator.validate(mock, specification)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }
    }

    @Nested
    inner class ExceptionIsolation {

        @Test
        fun `Given validator that throws When validating Then should handle gracefully returning no errors from that validator`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } throws RuntimeException("validator crash")
            val validator = CompositeMockValidator(listOf(mockValidator1))

            val result = validator.validate(mock, specification)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given valid and throwing validators When validating Then should include errors from non-throwing validators only`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.invalid(listOf("legitimate error"))
            coEvery { mockValidator2.validate(mock, specification) } throws RuntimeException("crash")
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(listOf("legitimate error"), result.errors)
        }

        @Test
        fun `Given exception between two valid validators When validating Then exception does not prevent other validators from executing`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error before crash"))
            coEvery { mockValidator2.validate(mock, specification) } throws IllegalStateException("unexpected state")
            coEvery { mockValidator3.validate(mock, specification) } returns MockValidationResult.invalid(listOf("error after crash"))
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2, mockValidator3))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors.contains("error before crash"))
            assertTrue(result.errors.contains("error after crash"))
        }

        @Test
        fun `Given all validators throwing exceptions When validating Then should return valid with no errors`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } throws RuntimeException("crash 1")
            coEvery { mockValidator2.validate(mock, specification) } throws OutOfMemoryError("crash 2")
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2))

            val result = validator.validate(mock, specification)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `Given NullPointerException in validator When validating Then should isolate and continue`() = runTest {
            coEvery { mockValidator1.validate(mock, specification) } throws NullPointerException("null ref")
            coEvery { mockValidator2.validate(mock, specification) } returns MockValidationResult.invalid(listOf("valid error"))
            val validator = CompositeMockValidator(listOf(mockValidator1, mockValidator2))

            val result = validator.validate(mock, specification)

            assertFalse(result.isValid)
            assertEquals(listOf("valid error"), result.errors)
        }
    }
}
