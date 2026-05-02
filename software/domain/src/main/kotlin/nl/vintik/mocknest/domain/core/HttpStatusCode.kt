package nl.vintik.mocknest.domain.core

@JvmInline
value class HttpStatusCode(val value: Int) {
    init {
        require(value in 100..599) { "HTTP status code must be between 100 and 599, got: $value" }
    }

    fun value(): Int = value

    companion object {
        val OK = HttpStatusCode(200)
        val CREATED = HttpStatusCode(201)
        val BAD_REQUEST = HttpStatusCode(400)
        val NOT_FOUND = HttpStatusCode(404)
        val INTERNAL_SERVER_ERROR = HttpStatusCode(500)
    }
}
