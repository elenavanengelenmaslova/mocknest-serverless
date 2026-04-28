package nl.vintik.mocknest.infra.aws.runtime.health

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.RuntimeHealthUseCase
import nl.vintik.mocknest.domain.core.HttpResponse

/**
 * AWS-specific adapter for runtime health check.
 * 
 * Handles AWS-specific configuration and environment variables,
 * then delegates to the application layer use case.
 */
class AwsRuntimeHealthUseCase(
    private val storage: ObjectStorageInterface,
    private val bucketName: String = System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "",
) : GetRuntimeHealth {
    
    override fun invoke(): HttpResponse {
        val region = System.getenv("AWS_REGION") ?: "unknown"
        
        val useCase = RuntimeHealthUseCase(
            storage = storage,
            bucketName = bucketName,
            region = region
        )
        
        return useCase.invoke()
    }
}
