package nl.vintik.mocknest.infra.aws.generation.ai.config

/**
 * Resolves inference profile prefixes for Amazon Bedrock model invocations.
 * 
 * This interface provides a strategy for determining which inference profile prefixes
 * to use when invoking Bedrock models. Inference profiles allow AWS to route requests
 * to different regions based on model availability and performance characteristics.
 * 
 * Implementations should consider:
 * - The deployment region where the Lambda function is running
 * - The configured inference mode (AUTO, GLOBAL_ONLY, GEO_ONLY)
 * - AWS region naming conventions for deriving geo-specific prefixes
 * 
 * Example usage:
 * ```kotlin
 * val resolver = DefaultInferencePrefixResolver(
 *     deployRegion = "eu-west-1",
 *     inferenceMode = InferenceMode.AUTO
 * )
 * val prefixes = resolver.getCandidatePrefixes() // Returns ["global", "eu"]
 * ```
 */
interface InferencePrefixResolver {
    
    /**
     * The AWS region where the application is deployed.
     * 
     * This is typically read from the AWS_REGION environment variable that is
     * automatically set by the Lambda runtime. The deployment region is used
     * to derive geo-specific inference profile prefixes.
     * 
     * Example values: "eu-west-1", "us-east-1", "ap-southeast-1"
     */
    val deployRegion: String
    
    /**
     * Get an ordered list of candidate inference profile prefixes to try.
     * 
     * The order matters: the first prefix in the list should be tried first,
     * with fallback to subsequent prefixes if the first attempt fails with
     * a retryable error (e.g., model not found, access denied).
     * 
     * The returned list depends on the configured inference mode:
     * - AUTO: Returns ["global", geo_prefix] - try global first, then geo-specific
     * - GLOBAL_ONLY: Returns ["global"] - only use global inference profile
     * - GEO_ONLY: Returns [geo_prefix] - only use geo-specific inference profile
     * 
     * Where geo_prefix is derived from the deployment region:
     * - eu-* regions → "eu"
     * - us-* regions → "us"
     * - ap-* regions → "ap"
     * - ca-* regions → "ca"
     * - me-* regions → "me"
     * - sa-* regions → "sa"
     * - af-* regions → "af"
     * 
     * @return Ordered list of inference profile prefixes to attempt
     */
    fun getCandidatePrefixes(): List<String>
}
