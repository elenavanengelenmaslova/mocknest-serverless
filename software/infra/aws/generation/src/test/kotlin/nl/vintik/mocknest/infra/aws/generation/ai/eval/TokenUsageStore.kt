package nl.vintik.mocknest.infra.aws.generation.ai.eval

import java.util.concurrent.ConcurrentLinkedQueue

class TokenUsageStore {
    private val records = ConcurrentLinkedQueue<TokenUsageRecord>()

    /** Set the current phase for subsequent records */
    var currentPhase: TokenPhase = TokenPhase.GENERATION

    fun record(usage: TokenUsageRecord) {
        records.add(usage)
    }

    fun getRecords(): List<TokenUsageRecord> = records.toList()

    fun getRecordsByPhase(phase: TokenPhase): List<TokenUsageRecord> =
        records.filter { it.phase == phase }

    fun getGenerationRecords(): List<TokenUsageRecord> = getRecordsByPhase(TokenPhase.GENERATION)

    fun getJudgeRecords(): List<TokenUsageRecord> = getRecordsByPhase(TokenPhase.JUDGE)

    fun getTotalInputTokens(): Int = records.sumOf { it.inputTokens }

    fun getTotalOutputTokens(): Int = records.sumOf { it.outputTokens }

    fun getTotalTokens(): Int = records.sumOf { it.totalTokens }

    fun clear() {
        records.clear()
    }
}
