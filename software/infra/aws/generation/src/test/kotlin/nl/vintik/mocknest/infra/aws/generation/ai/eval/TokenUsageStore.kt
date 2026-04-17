package nl.vintik.mocknest.infra.aws.generation.ai.eval

import java.util.concurrent.ConcurrentLinkedQueue

class TokenUsageStore {
    private val records = ConcurrentLinkedQueue<TokenUsageRecord>()

    fun record(usage: TokenUsageRecord) {
        records.add(usage)
    }

    fun getRecords(): List<TokenUsageRecord> = records.toList()

    fun getTotalInputTokens(): Int = records.sumOf { it.inputTokens }

    fun getTotalOutputTokens(): Int = records.sumOf { it.outputTokens }

    fun getTotalTokens(): Int = records.sumOf { it.totalTokens }

    fun clear() {
        records.clear()
    }
}
