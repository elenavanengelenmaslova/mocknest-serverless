package nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators

import com.fasterxml.jackson.databind.JsonNode
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.BaseEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper

private val logger = KotlinLogging.logger {}

class StatusDistinctnessEvaluator : BaseEvaluator(
    "Status Distinctness",
    1.0,
    listOf(EvalTestCaseParam.ACTUAL_OUTPUT)
) {
    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val actualOutput = testCase.actualOutput()

        val statuses = runCatching {
            extractStatuses(actualOutput)
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse actualOutput as JSON for status distinctness evaluation" }
        }.getOrElse { emptyList() }

        val uniqueStatuses = statuses.toSet()
        val allDistinct = statuses.size == uniqueStatuses.size
        val score = if (allDistinct) 1.0 else 0.0

        val reason = if (allDistinct) {
            "All ${statuses.size} status(es) are distinct: $uniqueStatuses"
        } else {
            val duplicates = statuses.groupingBy { it }.eachCount().filter { it.value > 1 }
            "Duplicate statuses found: $duplicates. All statuses: $statuses"
        }

        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build()
    }

    private fun extractStatuses(json: String): List<String> {
        val mappings = mapper.readTree(json)
        val statuses = mutableListOf<String>()

        for (mapping in mappings) {
            val responseBody = extractResponseBody(mapping) ?: continue
            collectStatuses(responseBody, statuses)
        }

        return statuses
    }

    private fun collectStatuses(node: JsonNode, statuses: MutableList<String>) {
        when {
            node.isArray -> node.forEach { element -> collectStatuses(element, statuses) }
            node.isObject -> {
                if (node.has("status")) {
                    statuses.add(node.get("status").asText())
                }
                // Continue into nested fields
                node.fields().forEach { (_, value) -> collectStatuses(value, statuses) }
            }
        }
    }
}
