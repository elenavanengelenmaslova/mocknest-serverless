package nl.vintik.mocknest.application.core

import nl.vintik.mocknest.domain.core.HttpResponse
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap

/**
 * Helper functions for creating HTTP responses.
 */
object HttpResponseHelper {
    
    fun ok(data: Any): HttpResponse {
        val jsonBody = mapper.writeValueAsString(data)
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("Content-Type", "application/json")
        
        return HttpResponse(
            statusCode = HttpStatus.OK,
            headers = headers,
            body = jsonBody
        )
    }
}