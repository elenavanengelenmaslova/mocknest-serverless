package nl.vintik.mocknest.infra.aws.generation.ai.eval

enum class TokenPhase {
    GENERATION,
    JUDGE
}

data class TokenUsageRecord(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val phase: TokenPhase = TokenPhase.GENERATION
)
