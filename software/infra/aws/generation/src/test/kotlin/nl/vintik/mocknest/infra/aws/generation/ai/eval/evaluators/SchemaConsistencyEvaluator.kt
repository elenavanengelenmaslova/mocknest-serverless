package nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators

import com.fasterxml.jackson.databind.JsonNode
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.BaseEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper

private val logger = KotlinLogging.logger {}

class SchemaConsistencyEvaluator(private val requiredFields: List<String>) : BaseEvaluator(
    "Schema Consistency",
    1.0,
    listOf(EvalTestCaseParam.ACTUAL_OUTPUT)
) {
    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val actualOutput = testCase.actualOutput()

        val validationResult = runCatching {
            validateSchemaConsistency(actualOutput)
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse actualOutput as JSON for schema consistency evaluation" }
        }.getOrElse { ValidationResult(valid = false, reason = "Failed to parse JSON: ${it.message}") }

        val score = if (validationResult.valid) 1.0 else 0.0

        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(validationResult.reason)
            .build()
    }

    private fun validateSchemaConsistency(json: String): ValidationResult {
        val mappings = mapper.readTree(json)
        val violations = mutableListOf<String>()
        var petObjectCount = 0

        for ((index, mapping) in mappings.withIndex()) {
            val responseBody = extractResponseBody(mapping) ?: continue
            val petObjects = extractPetObjects(responseBody)

            for (pet in petObjects) {
                petObjectCount++
                val missingFields = requiredFields.filter { !pet.has(it) }
                if (missingFields.isNotEmpty()) {
                    violations.add("Mapping #$index: pet missing fields $missingFields")
                }
            }
        }

        return if (violations.isEmpty()) {
            ValidationResult(valid = true, reason = "All $petObjectCount pet object(s) contain required fields: $requiredFields")
        } else {
            ValidationResult(valid = false, reason = "Schema violations: $violations")
        }
    }

    private fun extractPetObjects(node: JsonNode): List<JsonNode> =
        when {
            node.isArray -> node.flatMap { extractPetObjects(it) }
            node.isObject && node.has("id") && node.has("name") -> listOf(node)
            node.isObject -> node.fields().asSequence().flatMap { (_, value) -> extractPetObjects(value).asSequence() }.toList()
            else -> emptyList()
        }

    private data class ValidationResult(val valid: Boolean, val reason: String)
}
