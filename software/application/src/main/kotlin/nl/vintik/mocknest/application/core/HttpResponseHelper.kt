package nl.vintik.mocknest.application.core

import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode

/**
 * Helper functions for creating HTTP responses.
 */
object HttpResponseHelper {
    
    fun ok(data: Any): HttpResponse {
        val jsonBody = mapper.writeValueAsString(data)
        val headers = mutableMapOf<String, List<String>>()
        headers["Content-Type"] = listOf("application/json")
        
        return HttpResponse(
            statusCode = HttpStatusCode.OK,
            headers = headers,
            body = jsonBody
        )
    }
}
