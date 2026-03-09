package nl.vintik.mocknest.infra.aws.runtime.health

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of runtime health check use case.
 * 
 * Checks storage connectivity and returns comprehensive health information
 * including deployment region, S3 bucket name, and connectivity status.
 */
@Component
class RuntimeHealthUseCase(
    private val storage: ObjectStorageInterface,
    @param:Value("\${storage.bucket.name}") private val bucketName: String
) : GetRuntimeHealth {
    
    override fun invoke(): HttpResponse {
        logger.debug { "Checking runtime health" }
        
        val region = System.getenv("AWS_REGION") ?: "unknown"
        val connectivity = checkStorageConnectivity()
        val status = if (connectivity) "healthy" else "degraded"
        
        val response = RuntimeHealthResponse(
            status = status,
            timestamp = Instant.now().toString(),
            region = region,
            storage = StorageHealth(
                bucket = bucketName,
                connectivity = if (connectivity) "ok" else "error"
            )
        )
        
        val jsonBody = mapper.writeValueAsString(response)
        
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("Content-Type", "application/json")
        
        return HttpResponse(
            statusCode = HttpStatus.OK,
            headers = headers,
            body = jsonBody
        )
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
