package nl.vintik.mocknest.infra.aws.runtime.health

import com.fasterxml.jackson.annotation.JsonProperty
import nl.vintik.mocknest.domain.runtime.RuntimeHealth
import nl.vintik.mocknest.domain.runtime.StorageHealth

/**
 * JSON response wrapper for runtime health.
 * 
 * Converts domain models to JSON-serializable format for HTTP responses.
 */
data class RuntimeHealthJsonResponse(
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("timestamp") val timestamp: String,
    @param:JsonProperty("region") val region: String,
    @param:JsonProperty("version") val version: String,
    @param:JsonProperty("storage") val storage: StorageHealthJson
) {
    companion object {
        fun from(health: RuntimeHealth): RuntimeHealthJsonResponse {
            return RuntimeHealthJsonResponse(
                status = health.status,
                timestamp = health.timestamp.toString(),
                region = health.region,
                version = health.version,
                storage = StorageHealthJson.from(health.storage)
            )
        }
    }
}

/**
 * JSON response wrapper for storage health.
 */
data class StorageHealthJson(
    @param:JsonProperty("bucket") val bucket: String,
    @param:JsonProperty("connectivity") val connectivity: String
) {
    companion object {
        fun from(storage: StorageHealth): StorageHealthJson {
            return StorageHealthJson(
                bucket = storage.bucket,
                connectivity = storage.connectivity
            )
        }
    }
}