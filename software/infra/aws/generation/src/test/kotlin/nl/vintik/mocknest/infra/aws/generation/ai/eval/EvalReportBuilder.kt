package nl.vintik.mocknest.infra.aws.generation.ai.eval

/**
 * Builds a structured eval report from a list of [IterationResult] instances.
 * The report uses a bordered, labeled format for easy visual scanning in IDE test runners.
 */
class EvalReportBuilder {

    companion object {
        private const val REPORT_WIDTH = 62
        private const val CONTENT_WIDTH = REPORT_WIDTH - 2 // width inside the borders
    }

    fun buildReport(
        modelName: String,
        region: String,
        iterationCount: Int,
        results: List<IterationResult>,
        totalDurationMs: Long,
        totalTokenUsage: TokenUsageRecord,
        totalEstimatedCost: Double
    ): String {
        val successCount = results.count { it.success }
        val semanticPassCount = results.count { it.semanticScore?.passed == true }
        val totalMocks = results.sumOf { it.mockCount }

        val successRate = if (iterationCount > 0) successCount.toDouble() / iterationCount * 100.0 else 0.0
        val semanticRate = if (iterationCount > 0) semanticPassCount.toDouble() / iterationCount * 100.0 else 0.0

        return buildString {
            appendLine(topBorder())
            appendLine(centeredLine("BEDROCK PROMPT EVAL REPORT"))
            appendLine(centeredLine("(Generation-Only Mode)"))
            appendLine(sectionBorder())
            appendLine(labeledLine("Model:", modelName))
            appendLine(labeledLine("Region:", region))
            appendLine(labeledLine("Iterations:", iterationCount.toString()))
            appendLine(labeledLine("Duration:", "$totalDurationMs ms"))
            appendLine(sectionBorder())
            appendLine(labeledLine("Success Rate:", "$successCount/$iterationCount = ${"%.1f".format(successRate)}%"))
            appendLine(labeledLine("Semantic Score:", "$semanticPassCount/$iterationCount = ${"%.1f".format(semanticRate)}%"))
            appendLine(labeledLine("Total Mocks:", totalMocks.toString()))
            appendLine(sectionBorder())
            appendLine(labeledLine("Token Usage:", ""))
            appendLine(labeledLine("  Input Tokens:", totalTokenUsage.inputTokens.toString()))
            appendLine(labeledLine("  Output Tokens:", totalTokenUsage.outputTokens.toString()))
            appendLine(labeledLine("  Total Tokens:", totalTokenUsage.totalTokens.toString()))
            appendLine(labeledLine("Estimated Cost:", "${"$"}${"%.4f".format(totalEstimatedCost)}"))
            appendLine(sectionBorder())
            appendLine(paddedLine("Per-Iteration Breakdown:"))
            results.forEach { result ->
                appendLine(formatIterationLine(result))
            }
            append(bottomBorder())
        }
    }

    private fun topBorder(): String = "╔${"═".repeat(CONTENT_WIDTH)}╗"

    private fun sectionBorder(): String = "╠${"═".repeat(CONTENT_WIDTH)}╣"

    private fun bottomBorder(): String = "╚${"═".repeat(CONTENT_WIDTH)}╝"

    private fun centeredLine(text: String): String {
        val padding = (CONTENT_WIDTH - text.length).coerceAtLeast(0)
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return "║${" ".repeat(leftPad)}$text${" ".repeat(rightPad)}║"
    }

    private fun labeledLine(label: String, value: String): String {
        val labelWidth = 18
        val content = " ${label.padEnd(labelWidth)}$value"
        return "║${content.padEnd(CONTENT_WIDTH)}║"
    }

    private fun paddedLine(text: String): String {
        val content = " $text"
        return "║${content.padEnd(CONTENT_WIDTH)}║"
    }

    private fun formatIterationLine(result: IterationResult): String {
        val num = "#${result.iterationNumber}"
        val content = if (result.success) {
            val semanticMark = if (result.semanticScore?.passed == true) "✓" else "✗"
            val tokenIn = result.tokenUsage.inputTokens
            val tokenOut = result.tokenUsage.outputTokens
            val cost = "%.4f".format(result.estimatedCost)
            " $num  ✓  mocks=${result.mockCount}  semantic=$semanticMark  in=$tokenIn out=$tokenOut  ${"$"}$cost"
        } else {
            val errorMsg = result.errorMessage ?: "Unknown error"
            " $num  ✗  error: $errorMsg"
        }
        val truncated = if (content.length > CONTENT_WIDTH) content.take(CONTENT_WIDTH) else content
        return "║${truncated.padEnd(CONTENT_WIDTH)}║"
    }
}
