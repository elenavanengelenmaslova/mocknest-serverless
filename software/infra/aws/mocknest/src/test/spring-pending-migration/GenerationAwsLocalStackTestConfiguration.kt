package nl.vintik.mocknest.infra.aws.generation.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.config.SharedLocalStackContainer
import nl.vintik.mocknest.infra.aws.config.TEST_BUCKET_NAME
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer

/**
 * Test configuration for generation module integration tests using LocalStack.
 * 
 * This configuration provides:
 * - LocalStack S3 client for specification storage testing
 * - Test-specific AWS credentials
 * - Shared LocalStack container
 */
@TestConfiguration
class AwsLocalStackTestConfiguration {

    private val logger = KotlinLogging.logger {}

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Use the shared LocalStack container
            val container = SharedLocalStackContainer.container
            val s3Endpoint = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()

            registry.add("aws.endpointUrl") { s3Endpoint }
            registry.add("aws.accessKeyId") { container.accessKey }
            registry.add("aws.secretAccessKey") { container.secretKey }
            registry.add("AWS_REGION") { TEST_REGION }
            registry.add("mocknest.s3.bucket-name") { TEST_BUCKET_NAME }
            registry.add("storage.bucket.name") { TEST_BUCKET_NAME }
        }
    }

    @Bean("testS3Client")
    @Primary
    fun testS3Client(): S3Client {
        val container = SharedLocalStackContainer.container
        val s3Endpoint = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()

        logger.info { "Creating test S3 client with endpoint: $s3Endpoint" }

        return S3Client {
            region = TEST_REGION
            endpointUrl = Url.parse(s3Endpoint)
            forcePathStyle = true

            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = container.accessKey,
                    secretAccessKey = container.secretKey
                )
            )
        }
    }

    @Bean("testObjectStorage")
    @Primary
    fun testObjectStorage(@Qualifier("testS3Client") s3Client: S3Client): ObjectStorageInterface {
        runBlocking {
            s3Client.runCatching {
                createBucket(CreateBucketRequest {
                    bucket = TEST_BUCKET_NAME
                })
            }.onSuccess {
                logger.info { "Test bucket created successfully: $TEST_BUCKET_NAME" }
            }.onFailure { exception ->
                // Only throw if it's NOT a "BucketAlreadyOwnedByYou" or "BucketAlreadyExists" error
                val message = exception.message?.lowercase() ?: ""
                if (!message.contains("bucketalreadyownedbyyou") && !message.contains("bucketalreadyexists")) {
                    logger.error(exception) { "Failed to create test bucket: $TEST_BUCKET_NAME" }
                    throw exception
                }
                logger.info { "Test bucket already exists: $TEST_BUCKET_NAME" }
            }
        }

        return S3ObjectStorageAdapter(TEST_BUCKET_NAME, s3Client)
    }
}
