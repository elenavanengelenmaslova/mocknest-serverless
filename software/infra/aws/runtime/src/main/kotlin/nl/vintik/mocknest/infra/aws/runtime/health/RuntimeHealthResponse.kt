package nl.vintik.mocknest.infra.aws.runtime.health

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Runtime health check response.
 * 
 * Provides information about the WireMock runtime status, deployment region,
 * and storage connectivity.
 */
data class RuntimeHealthResponse(
    @JsonProperty("status") val status: String,
    @JsonProperty("timestamp") val timestamp: String,
    @JsonProperty("region") val region: String,
    @JsonProperty("storage") val storage: StorageHealth
)

/**
 * Storage health information.
 * 
 * Includes the S3 bucket name and connectivity status.
 */
data class StorageHealth(
    @JsonProperty("bucket") val bucket: String,
    @JsonProperty("connectivity") val connectivity: String
)
