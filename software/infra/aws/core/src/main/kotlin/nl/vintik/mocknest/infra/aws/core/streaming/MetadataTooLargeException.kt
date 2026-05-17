package nl.vintik.mocknest.infra.aws.core.streaming

/**
 * Thrown when the serialized metadata block exceeds the maximum allowed size
 * for the API Gateway streaming protocol (16,376 bytes).
 */
class MetadataTooLargeException(message: String) : RuntimeException(message)
