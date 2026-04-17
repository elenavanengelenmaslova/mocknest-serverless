package nl.vintik.mocknest.infra.aws.generation.ai.eval

/**
 * Calculates estimated cost from token usage and pricing constants.
 * Default pricing is for Amazon Nova Pro on-demand.
 */
object CostCalculator {
    // Amazon Nova Pro on-demand pricing (USD per token)
    const val NOVA_PRO_INPUT_PRICE_PER_TOKEN = 0.0000008   // $0.0008 per 1K tokens
    const val NOVA_PRO_OUTPUT_PRICE_PER_TOKEN = 0.0000032  // $0.0032 per 1K tokens

    fun calculateCost(
        inputTokens: Int,
        outputTokens: Int,
        inputPricePerToken: Double = NOVA_PRO_INPUT_PRICE_PER_TOKEN,
        outputPricePerToken: Double = NOVA_PRO_OUTPUT_PRICE_PER_TOKEN
    ): Double = inputTokens * inputPricePerToken + outputTokens * outputPricePerToken
}
