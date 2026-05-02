package nl.vintik.mocknest.domain.core

data class HttpRequest(
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val path: String,
    val queryParameters: Map<String, String> = emptyMap(),
    val body: String? = null
)