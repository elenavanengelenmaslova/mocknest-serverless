package nl.vintik.mocknest.application.generation.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.HttpResponseHelper
import nl.vintik.mocknest.application.core.Version
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.generation.AIHealth
import nl.vintik.mocknest.domain.generation.AIModelHealth
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Application layer implementation of AI health check.
 * 
 * Contains the core business logic for checking AI health,
 * independent of infrastructure concerns.
 */
class AIHealthUseCase(
    private val aiModelService: AIModelServiceInterface,
    private val region: String,
    private val inferenceMode: String
) : GetAIHealth {
    
    override fun invoke(): HttpResponse {
        logger.debug { "Checking AI health" }
        
        val health = AIHealth(
            status = "healthy",
            timestamp = Instant.now(),
            region = region,
            version = Version.MOCKNEST_VERSION,
            ai = AIModelHealth(
                modelName = aiModelService.getModelName(),
                inferencePrefix = aiModelService.getConfiguredPrefix(),
                inferenceMode = inferenceMode.uppercase()
            )
        )
        
        return HttpResponseHelper.ok(health)
    }
}