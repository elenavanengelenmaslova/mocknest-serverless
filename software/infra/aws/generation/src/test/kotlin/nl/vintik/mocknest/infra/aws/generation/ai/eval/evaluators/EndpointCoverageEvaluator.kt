package nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators

import com.fasterxml.jackson.databind.JsonNode
import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.EvalTestCaseParam
import dev.dokimos.core.BaseEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper

private val logger = KotlinLogging.logger {}

class EndpointCoverageEvaluator(private val requiredEndpoints: List<String>) : BaseEvaluator(
    "Endpoint Coverage",
    1.0,
    listOf(EvalTestCaseParam.ACTUAL_OUTPUT)
) {
    override fun runEvaluation(testCase: EvalTestCase): EvalResult {
        val actualOutput = testCase.actualOutput()

        val coveredEndpoints = runCatching {
            extractEndpoints(actualOutput)
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse actualOutput as JSON for endpoint coverage evaluation" }
        }.getOrElse { emptyList() }

        val missingEndpoints = requiredEndpoints.filter { required ->
            coveredEndpoints.none { covered -> endpointMatches(required, covered) }
        }

        logger.info { "Endpoint coverage: found=$coveredEndpoints, required=$requiredEndpoints, missing=$missingEndpoints" }

        val score = if (missingEndpoints.isEmpty()) 1.0 else 0.0
        val reason = if (missingEndpoints.isEmpty()) {
            "All required endpoints covered: $requiredEndpoints"
        } else {
            "Missing endpoints: $missingEndpoints. Found: $coveredEndpoints"
        }

        return EvalResult.builder()
            .name(name())
            .score(score)
            .threshold(threshold())
            .reason(reason)
            .build()
    }

    private fun extractEndpoints(json: String): List<String> {
        val mappings = mapper.readTree(json)
        val endpoints = mutableListOf<String>()

        for (mapping in mappings) {
            val request = mapping.path("request")
            val method = request.path("method").asText("").uppercase()
            val path = extractPath(request)
            if (method.isNotEmpty() && path.isNotEmpty()) {
                endpoints.add("$method $path")
            }
        }

        return endpoints
    }

    private fun extractPath(request: JsonNode): String =
        request.path("url").asText(null)?.takeIf { it.isNotBlank() }
            ?: request.path("urlPattern").asText(null)?.takeIf { it.isNotBlank() }
            ?: request.path("urlPathPattern").asText(null)?.takeIf { it.isNotBlank() }
            ?: ""

    private fun endpointMatches(required: String, actual: String): Boolean {
        val (requiredMethod, requiredPath) = splitEndpoint(required)
        val (actualMethod, actualPath) = splitEndpoint(actual)

        if (!requiredMethod.equals(actualMethod, ignoreCase = true)) return false

        return pathMatches(requiredPath, actualPath)
    }

    private fun splitEndpoint(endpoint: String): Pair<String, String> {
        val parts = endpoint.trim().split(" ", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "" to endpoint
    }

    private fun pathMatches(requiredPath: String, actualPath: String): Boolean {
        // Direct match
        if (requiredPath == actualPath) return true

        // Required path has template variables like {petId}
        // Convert to regex and match against actual path, escaping literal segments
        if (requiredPath.contains("{")) {
            val placeholder = "__PATH_PARAM__"
            val escapedPattern = requiredPath
                .replace(Regex("\\{[^}]+}"), placeholder)
                .let { Regex.escape(it) }
                .replace(placeholder, "[^/]+")
            if (Regex("^$escapedPattern$").matches(actualPath)) return true
        }

        // Actual path may be a regex pattern (e.g., /pet/[0-9]+)
        // Check if the required path template matches the actual regex pattern
        if (actualPath.contains("[") || actualPath.contains("(") || actualPath.contains("\\")) {
            runCatching {
                val regex = Regex(actualPath)
                // Generate a sample path from the required template
                val samplePath = requiredPath.replace(Regex("\\{[^}]+}"), "1")
                if (regex.matches(samplePath)) return true
            }
        }

        // Check if required path (without template) is a prefix match for findByStatus-style endpoints
        val requiredBase = requiredPath.replace(Regex("\\{[^}]+}"), "").trimEnd('/')
        val actualBase = actualPath.replace(Regex("\\?.*"), "")
        if (requiredBase.isNotEmpty() && (actualBase == requiredBase || actualBase.startsWith("$requiredBase/"))) return true

        return false
    }
}
