package nl.vintik.mocknest.domain.core

import org.springframework.http.HttpStatusCode
import org.springframework.util.MultiValueMap

data class HttpResponse(
    val statusCode: HttpStatusCode,
    val headers: MultiValueMap<String, String>? = null,
    val body: String? = null
)