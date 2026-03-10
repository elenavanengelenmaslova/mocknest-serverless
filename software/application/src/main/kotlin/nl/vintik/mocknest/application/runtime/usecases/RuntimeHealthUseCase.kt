package nl.vintik.mocknest.application.runtime.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.HttpResponseHelper
import nl.vintik.mocknest.application.core.Version
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.runtime.RuntimeHealth
import nl.vintik.mocknest.domain.runtime.StorageHealth
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Application layer implementation of runtime health check.
 * 
 * Contains the core business logic for checking runtime health,
 * independent of infrastructure concerns.
 */
class RuntimeHealthUseCase(
    private val storage: ObjectStorageInterface,
    private val bucketName: String,
    private val region: String
) : GetRuntimeHealth {
    
    override fun invoke(): HttpResponse {
        logger.debug { "Checking runtime health" }
        
        val connectivity = checkStorageConnectivity()
        val status = if (connectivity) "healthy" else "degraded"
        
        val health = RuntimeHealth(
            status = status,
            timestamp = Instant.now(),
            region = region,
            version = Version.MOCKNEST_VERSION,
            storage = StorageHealth(
                bucket = bucketName,
                connectivity = if (connectivity) "ok" else "error"
            )
        )
        
        return HttpResponseHelper.ok(health)
    }
    
    /**
     * Check storage connectivity by attempting to list objects.
     * 
     * @return true if storage is accessible, false otherwise
     */
    private fun checkStorageConnectivity(): Boolean {
        return runCatching {
            // Simple connectivity check - try to list objects
            // We don't need to consume the flow, just verify the operation doesn't throw
            storage.list()
            true
        }.onFailure { exception ->
            logger.warn(exception) { "Storage connectivity check failed for bucket: $bucketName" }
        }.getOrElse { false }
    }
}