package nl.vintik.mocknest.infra.aws.generation.ai.config

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Default implementation of InferencePrefixResolver.
 * 
 * Automatically determines the optimal inference profile prefixes based on:
 * - The AWS region where the application is deployed
 * - The configured inference mode (AUTO, GLOBAL_ONLY, GEO_ONLY)
 * 
 * This implementation derives geo-specific prefixes from AWS region naming conventions
 * and provides fallback strategies based on the inference mode.
 * 
 * @property deployRegion The AWS region where the application is deployed (e.g., "eu-west-1")
 * @property inferenceMode The inference mode controlling prefix selection strategy
 */
class DefaultInferencePrefixResolver(
    override val deployRegion: String,
    private val inferenceMode: InferenceMode
) : InferencePrefixResolver {
    
    /**
     * Get ordered list of candidate inference profile prefixes based on inference mode.
     * 
     * The implementation follows these rules:
     * - AUTO mode: Returns ["global", geo_prefix] - try global first, then geo-specific
     * - GLOBAL_ONLY mode: Returns ["global"] - only use global inference profile
     * - GEO_ONLY mode: Returns [geo_prefix] - only use geo-specific inference profile
     * 
     * @return Ordered list of inference profile prefixes to attempt
     */
    override fun getCandidatePrefixes(): List<String> {
        val geoPrefix = deriveGeoPrefix(deployRegion)
        
        return when (inferenceMode) {
            InferenceMode.AUTO -> listOf("global", geoPrefix)
            InferenceMode.GLOBAL_ONLY -> listOf("global")
            InferenceMode.GEO_ONLY -> listOf(geoPrefix)
        }
    }
    
    /**
     * Derive geo-specific inference profile prefix from AWS region.
     * 
     * Maps AWS region prefixes to their corresponding geo prefixes:
     * - eu-* (Europe) → "eu"
     * - us-* (United States) → "us"
     * - ap-* (Asia Pacific) → "ap"
     * - ca-* (Canada) → "ca"
     * - me-* (Middle East) → "me"
     * - sa-* (South America) → "sa"
     * - af-* (Africa) → "af"
     * 
     * For unknown region prefixes, logs a warning and defaults to "us".
     * 
     * @param region The AWS region string (e.g., "eu-west-1", "us-east-1")
     * @return The geo-specific inference profile prefix
     */
    private fun deriveGeoPrefix(region: String): String {
        return when {
            region.startsWith("eu-") -> "eu"
            region.startsWith("us-") -> "us"
            region.startsWith("ap-") -> "ap"
            region.startsWith("ca-") -> "ca"
            region.startsWith("me-") -> "me"
            region.startsWith("sa-") -> "sa"
            region.startsWith("af-") -> "af"
            else -> {
                logger.warn { "Unknown region prefix for region: $region, defaulting to 'us'" }
                "us"
            }
        }
    }
}
