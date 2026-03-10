package nl.vintik.mocknest.domain.runtime

import java.time.Instant

/**
 * Runtime health information.
 * 
 * Represents the health status of the MockNest runtime including
 * deployment region, version, and storage connectivity.
 */
data class RuntimeHealth(
    val status: String,
    val timestamp: Instant,
    val region: String,
    val version: String,
    val storage: StorageHealth
)

/**
 * Storage health information.
 * 
 * Includes the storage bucket name and connectivity status.
 */
data class StorageHealth(
    val bucket: String,
    val connectivity: String
)