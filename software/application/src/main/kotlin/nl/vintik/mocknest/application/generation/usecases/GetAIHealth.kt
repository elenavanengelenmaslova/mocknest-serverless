package nl.vintik.mocknest.application.generation.usecases

import nl.vintik.mocknest.domain.core.HttpResponse

/**
 * Use case for retrieving AI features health status.
 * 
 * Returns health information including deployment region, model configuration,
 * inference settings, and AI feature status.
 */
fun interface GetAIHealth {
    /**
     * Get AI features health status.
     * 
     * @return HttpResponse containing health information as JSON
     */
    operator fun invoke(): HttpResponse
}
