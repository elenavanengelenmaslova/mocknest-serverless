package nl.vintik.mocknest.infra.aws.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer

/**
 * Test bucket name used across all runtime integration tests
 */
const val TEST_BUCKET_NAME = "test-mocknest-bucket"

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
            registry.add("aws.region") { TEST_REGION }
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
        // Create the test bucket with retry logic
        runBlocking {
            var attempts = 0
            val maxAttempts = 10

            while (attempts < maxAttempts) {
                s3Client.runCatching {
                    createBucket(CreateBucketRequest {
                        bucket = TEST_BUCKET_NAME
                    })
                }.onSuccess {
                    logger.info { "Test bucket created successfully: $TEST_BUCKET_NAME" }
                    return@runBlocking
                }.onFailure { exception ->
                    attempts++
                    if (attempts >= maxAttempts) {
                        logger.error(exception) { "Failed to create test bucket after $maxAttempts attempts: $TEST_BUCKET_NAME" }
                        throw exception
                    } else {
                        logger.warn { "Test bucket creation attempt $attempts failed, retrying... (${exception.message})" }
                        delay(500) // Wait 500ms before retry
                    }
                }
            }
        }

        return S3ObjectStorageAdapter(TEST_BUCKET_NAME, s3Client)
    }
}
