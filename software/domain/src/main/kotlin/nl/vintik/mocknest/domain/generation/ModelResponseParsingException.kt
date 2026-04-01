package nl.vintik.mocknest.domain.generation

/**
 * Exception thrown when the AI model response cannot be parsed into valid mock mappings.
 */
class ModelResponseParsingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
