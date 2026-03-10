package nl.vintik.mocknest.infra.aws.generation.health

import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.application.generation.usecases.AIHealthUseCase
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * AWS-specific adapter for AI health check.
 * 
 * Handles AWS-specific configuration and environment variables,
 * then delegates to the application layer use case.
 */
@Component
class AwsAIHealthUseCase(
    private val aiModelService: AIModelServiceInterface,
    @param:Value("\${bedrock.inference.mode:AUTO}") private val inferenceMode: String
) : GetAIHealth {
    
    override fun invoke(): HttpResponse {
        val region = System.getenv("AWS_REGION") ?: "unknown"
        
        val useCase = AIHealthUseCase(
            aiModelService = aiModelService,
            region = region,
            inferenceMode = inferenceMode
        )
        
        return useCase.invoke()
    }
}