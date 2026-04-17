package nl.vintik.mocknest.infra.aws.generation.ai.eval

data class TokenUsageRecord(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0
)
