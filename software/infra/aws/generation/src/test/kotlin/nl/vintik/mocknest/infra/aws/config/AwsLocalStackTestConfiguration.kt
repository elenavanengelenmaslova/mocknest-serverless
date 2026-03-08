package nl.vintik.mocknest.infra.aws.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import nl.vintik.mocknest.infra.aws.config.SharedLocalStackContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test configuration for generation module integration tests using LocalStack.
 * 
 * This configuration provides:
 * - LocalStack S3 client for specification storage testing
 * - Test-specific AWS credentials
 * - Shared LocalStack container from core module
 */
@TestConfiguration
class AwsLocalStackTestConfiguration {

    @Bean
    @Primary
    fun testS3Client(): S3Client {
        val container = SharedLocalStackContainer.container
        
        return S3Client {
            region = "us-east-1"
            endpointUrl = Url.parse(container.getEndpointOverride(org.testcontainers.containers.localstack.LocalStackContainer.Service.S3).toString())
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = container.accessKey
                secretAccessKey = container.secretKey
            }
            forcePathStyle = true
        }
    }
}
