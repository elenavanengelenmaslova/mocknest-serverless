package nl.vintik.mocknest.domain.core

data class HttpResponse(
    val statusCode: HttpStatusCode,
    val headers: Map<String, List<String>>? = null,
    val body: String? = null
)
