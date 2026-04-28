package nl.vintik.mocknest.application.runtime.usecases

import nl.vintik.mocknest.domain.core.HttpResponse

/**
 * Use case for retrieving runtime health status.
 * 
 * Returns health information including deployment region, storage connectivity,
 * and overall system status.
 */
fun interface GetRuntimeHealth {
    /**
     * Get runtime health status.
     * 
     * @return HttpResponse containing health information as JSON
     */
    operator fun invoke(): HttpResponse
}
