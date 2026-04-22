package nl.vintik.mocknest.infra.aws.generation.ai.eval

/**
 * Calculates estimated cost from token usage and pricing constants.
 * Default pricing is for Amazon Nova Pro on-demand (US standard tier).
 *
 * Pricing constants (INPUT_PRICE_PER_MILLION = $0.80, OUTPUT_PRICE_PER_MILLION = $3.20)
 * represent US standard-tier pricing as of April 2026 and can vary by AWS region and tier
 * (Standard/Priority/Flex). Consult the AWS Bedrock pricing page for region- or
 * tier-specific rates. These constants are for reference only.
 *
 * Source: aws.amazon.com/bedrock/pricing/ (verified April 2026)
 */
object CostCalculator {
    // Amazon Nova Pro on-demand pricing (USD per token)
    // Override via environment variables for different models/regions
    val INPUT_PRICE_PER_TOKEN: Double = System.getenv("BEDROCK_EVAL_INPUT_PRICE_PER_TOKEN")?.toDoubleOrNull()
        ?: 0.0000008   // $0.0008 per 1K tokens (Nova Pro default)
    val OUTPUT_PRICE_PER_TOKEN: Double = System.getenv("BEDROCK_EVAL_OUTPUT_PRICE_PER_TOKEN")?.toDoubleOrNull()
        ?: 0.0000032  // $0.0032 per 1K tokens (Nova Pro default)

    fun calculateCost(
        inputTokens: Int,
        outputTokens: Int,
        inputPricePerToken: Double = INPUT_PRICE_PER_TOKEN,
        outputPricePerToken: Double = OUTPUT_PRICE_PER_TOKEN
    ): Double = inputTokens * inputPricePerToken + outputTokens * outputPricePerToken
}
