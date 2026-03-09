package nl.vintik.mocknest.infra.aws.generation.ai.config

/**
 * Exception thrown when model configuration fails after all retry attempts.
 * 
 * This exception indicates that the system was unable to configure a Bedrock model
 * with any of the attempted inference profile prefixes. The exception message includes
 * detailed context about the failure, including:
 * - The model name that was being configured
 * - The deployment region
 * - All inference prefixes that were attempted
 * - The underlying error that caused the failure
 * 
 * This exception typically occurs when:
 * - The selected model is not available in the deployment region
 * - Model access has not been enabled in the AWS account
 * - There are permission issues with the IAM role
 * - The model name is invalid and fallback also failed
 * 
 * @param message Detailed error message with context
 * @param cause The underlying exception that caused the failure
 */
class ModelConfigurationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
