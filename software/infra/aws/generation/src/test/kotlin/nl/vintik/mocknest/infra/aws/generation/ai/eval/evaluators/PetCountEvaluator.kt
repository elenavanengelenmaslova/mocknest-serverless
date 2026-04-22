package nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators

import com.fasterxml.jackson.databind.JsonNode
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.BaseEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper

private val logger = KotlinLogging.logger {}

class PetCountEvaluator(private val expectedCount: Int, private val exactMatch: Boolean = true) : BaseEvaluator(
    "Pet Count",
    1.0,
    listOf(EvalTestCaseParam.ACTUAL_OUTPUT)
) {
    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val actualOutput = testCase.actualOutput()

        val uniquePetIds = runCatching {
            extractUniquePetIds(actualOutput)
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse actualOutput as JSON for pet count evaluation" }
        }.getOrElse { emptySet() }

        val score = if (exactMatch) {
            if (uniquePetIds.size == expectedCount) 1.0 else 0.0
        } else {
            if (uniquePetIds.size >= expectedCount) 1.0 else 0.0
        }
        val reason = "Found ${uniquePetIds.size} unique pet(s), expected ${if (exactMatch) "exactly" else "at least"} $expectedCount. Pet IDs: $uniquePetIds"

        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build()
    }

    private fun extractUniquePetIds(json: String): Set<String> {
        val mappings = mapper.readTree(json)
        val petIds = mutableSetOf<String>()

        for (mapping in mappings) {
            val responseBody = extractResponseBody(mapping) ?: continue
            collectPetIds(responseBody, petIds)
        }

        return petIds
    }

    private fun collectPetIds(node: JsonNode, petIds: MutableSet<String>) {
        when {
            node.isArray -> node.forEach { element -> collectPetIds(element, petIds) }
            node.isObject -> {
                if (node.has("id") && node.has("name")) {
                    petIds.add(node.get("id").asText())
                }
                // Continue into nested fields
                node.fields().forEach { (_, value) -> collectPetIds(value, petIds) }
            }
        }
    }
}
