package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.GeneratedMock

private val logger = KotlinLogging.logger {}

/**
 * Composite mock validator that delegates to all registered validators.
 * Aggregates validation results from all validators, allowing each validator
 * to handle the formats it supports and skip others.
 */
class CompositeMockValidator(
    private val validators: List<MockValidatorInterface>
) : MockValidatorInterface {

    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        logger.debug { "Running composite validation with ${validators.size} validators for mock: ${mock.id}" }

        val allErrors = validators.flatMap { validator ->
            runCatching { validator.validate(mock, specification).errors }
                .onFailure { exception ->
                    logger.error(exception) { "Validator ${validator::class.simpleName} threw exception for mock: ${mock.id}" }
                }
                .getOrDefault(emptyList())
        }

        return if (allErrors.isEmpty()) {
            MockValidationResult.valid()
        } else {
            MockValidationResult.invalid(allErrors)
        }
    }
}
