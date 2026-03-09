package nl.vintik.mocknest.infra.aws.generation.ai.config

/**
 * Inference mode configuration for Bedrock model invocation.
 * 
 * Controls the strategy for selecting inference profile prefixes when invoking
 * Amazon Bedrock models. Different modes provide different trade-offs between
 * reliability, latency, and regional availability.
 */
enum class InferenceMode {
    /**
     * Automatic mode: Try global inference profile first, then fall back to geo-specific prefix.
     * 
     * This is the recommended mode for most use cases as it provides the best balance
     * between reliability and performance. AWS will route requests to the best available
     * region using the global inference profile, and if that fails (e.g., model not available
     * globally), the system will automatically retry with a geo-specific prefix derived
     * from the deployment region.
     * 
     * Example: In eu-west-1, tries "global" first, then "eu" if global fails.
     */
    AUTO,
    
    /**
     * Global-only mode: Only use the global inference profile prefix.
     * 
     * Use this mode when you want to ensure AWS routes requests optimally across all
     * available regions, and you're confident the model is available globally. This mode
     * will fail if the model is not available via the global inference profile.
     * 
     * Example: Always uses "global" prefix regardless of deployment region.
     */
    GLOBAL_ONLY,
    
    /**
     * Geo-only mode: Only use the geo-specific inference profile prefix.
     * 
     * Use this mode when you want to ensure requests stay within a specific geographic
     * region for compliance, data residency, or latency requirements. The geo prefix is
     * automatically derived from the deployment region (e.g., eu-west-1 → "eu").
     * 
     * Example: In eu-west-1, only uses "eu" prefix; in us-east-1, only uses "us" prefix.
     */
    GEO_ONLY
}
