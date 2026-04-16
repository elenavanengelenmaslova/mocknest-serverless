package nl.vintik.mocknest.infra.aws.generation.ai.eval

data class IterationResult(
    val iterationNumber: Int,
    val success: Boolean,
    val mockCount: Int = 0,
    val mockIds: List<String> = emptyList(),
    val endpointPaths: List<String> = emptyList(),
    val errorMessage: String? = null,
    val semanticScore: SemanticScore? = null,
    val tokenUsage: TokenUsageRecord = TokenUsageRecord(),
    val estimatedCost: Double = 0.0
)

data class SemanticScore(
    val petCountCorrect: Boolean,
    val endpointsCovered: Boolean,
    val schemaConsistent: Boolean,
    val statusesDistinct: Boolean,
    val llmJudgeScore: Double? = null,
    val passed: Boolean
)
