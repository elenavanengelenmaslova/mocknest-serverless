package nl.vintik.mocknest.domain.generation

/**
 * Exception thrown when WSDL parsing fails.
 */
class WsdlParsingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when WSDL fetching from a URL fails.
 */
class WsdlFetchException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
