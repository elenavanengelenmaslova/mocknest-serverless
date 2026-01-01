package io.mocknest.infra.aws.config

/**
 * AWS configuration constants for MockNest Serverless
 */
object AwsConfiguration {
    /**
     * Default AWS region for production deployments
     * This can be overridden via environment variables or application properties
     */
    const val DEFAULT_REGION = "eu-west-1"
    
    /**
     * Test region used for LocalStack integration tests
     * LocalStack typically runs in us-east-1 by default
     */
    const val TEST_REGION = "us-east-1"
    
    /**
     * Test bucket name for integration tests
     */
    const val TEST_BUCKET_NAME = "test-bucket"
}