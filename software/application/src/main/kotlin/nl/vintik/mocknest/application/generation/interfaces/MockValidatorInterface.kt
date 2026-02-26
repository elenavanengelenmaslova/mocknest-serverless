package nl.vintik.mocknest.application.generation.interfaces

import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.GeneratedMock

/**
 * Interface for validating generated mocks against API specifications.
 */
interface MockValidatorInterface {
    /**
     * Validates a generated mock against the source API specification.
     *
     * @param mock The generated mock to validate
     * @param specification The source API specification
     * @return MockValidationResult indicating whether the mock is valid and any errors found
     */
    suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult
}

/**
 * Result of mock validation.
 */
data class MockValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun valid() = MockValidationResult(true, emptyList())
        fun invalid(errors: List<String>) = MockValidationResult(false, errors)
    }
}
