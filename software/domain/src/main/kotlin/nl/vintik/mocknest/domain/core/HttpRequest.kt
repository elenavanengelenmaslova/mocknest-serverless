package nl.vintik.mocknest.domain.core

data class HttpRequest(
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val multiValueHeaders: Map<String, List<String>> = emptyMap(),
    val path: String,
    val queryParameters: Map<String, String> = emptyMap(),
    val multiValueQueryParameters: Map<String, List<String>> = emptyMap(),
    val body: String? = null
)