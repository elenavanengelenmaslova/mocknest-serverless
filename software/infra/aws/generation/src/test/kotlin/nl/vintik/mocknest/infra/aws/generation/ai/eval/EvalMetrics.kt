package nl.vintik.mocknest.infra.aws.generation.ai.eval

/**
 * Calculates the success rate as a percentage from a list of iteration results.
 *
 * @param results the list of iteration results to evaluate
 * @return the percentage of results where [IterationResult.success] is true, or 0.0 if the list is empty
 */
fun calculateSuccessRate(results: List<IterationResult>): Double {
    if (results.isEmpty()) return 0.0
    val successCount = results.count { it.success }
    return successCount.toDouble() / results.size * 100.0
}

/**
 * Calculates the semantic success rate as a percentage from a list of iteration results.
 *
 * @param results the list of iteration results to evaluate
 * @return the percentage of results where [SemanticScore.passed] is true, or 0.0 if the list is empty
 */
fun calculateSemanticSuccessRate(results: List<IterationResult>): Double {
    if (results.isEmpty()) return 0.0
    val passedCount = results.count { it.semanticScore?.passed == true }
    return passedCount.toDouble() / results.size * 100.0
}

/**
 * Asserts that the given rate meets or exceeds the specified threshold.
 *
 * @param rate the achieved rate (0.0 to 100.0)
 * @param threshold the required minimum rate (0.0 to 100.0)
 * @param label a descriptive label for the assertion (e.g., "Success Rate", "Semantic Score")
 * @throws AssertionError if [rate] is less than [threshold]
 */
fun assertThreshold(rate: Double, threshold: Double, label: String) {
    if (rate < threshold) {
        throw AssertionError(
            "$label below threshold: achieved $rate% but required $threshold%"
        )
    }
}

/**
 * Parses an environment variable string into a positive iteration count.
 *
 * @param envValue the raw environment variable value, or null if not set
 * @return the parsed positive integer, or 1 if [envValue] is null
 * @throws IllegalArgumentException if [envValue] is empty, blank, non-numeric, zero, or negative
 */
fun parseIterationCount(envValue: String?): Int {
    if (envValue == null) return 1
    require(envValue.isNotBlank()) { "BEDROCK_EVAL_ITERATIONS must not be empty or blank" }
    val parsed = envValue.trim().toIntOrNull()
    require(parsed != null) { "BEDROCK_EVAL_ITERATIONS must be a valid integer, got: '$envValue'" }
    require(parsed > 0) { "BEDROCK_EVAL_ITERATIONS must be a positive integer, got: $parsed" }
    return parsed
}
