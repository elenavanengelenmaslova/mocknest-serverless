package nl.vintik.mocknest.domain.core

enum class HttpMethod {
    GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;

    companion object {
        /**
         * Case-insensitive lookup of an HTTP method by name.
         *
         * Note: This method intentionally uses a different parameter name than the
         * compiler-generated [valueOf] to avoid shadowing. Call sites should use
         * this method for case-insensitive resolution.
         */
        fun resolve(method: String): HttpMethod =
            entries.first { it.name.equals(method, ignoreCase = true) }
    }
}
