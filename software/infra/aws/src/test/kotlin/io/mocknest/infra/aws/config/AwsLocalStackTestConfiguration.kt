package io.mocknest.infra.aws.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.mocknest.application.interfaces.storage.ObjectStorageInterface
import io.mocknest.infra.aws.storage.S3ObjectStorageAdapter
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

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
            registry.add("aws.region") { AwsConfiguration.TEST_REGION }
            registry.add("mocknest.s3.bucket-name") { AwsConfiguration.TEST_BUCKET_NAME }
            registry.add("aws.s3.bucket-name") { AwsConfiguration.TEST_BUCKET_NAME }
        }
    }

    @Bean("testS3Client")
    @Primary
    fun testS3Client(): S3Client {
        val container = SharedLocalStackContainer.container
        val s3Endpoint = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        
        logger.info { "Creating test S3 client with endpoint: $s3Endpoint" }
        
        return S3Client {
            region = AwsConfiguration.TEST_REGION
            endpointUrl = Url.parse(s3Endpoint)
            forcePathStyle = true  // Required for LocalStack
        }
    }

    @Bean("testObjectStorage")
    @Primary
    fun testObjectStorage(): ObjectStorageInterface {
        val s3Client = testS3Client()
        
        // Create the test bucket with retry logic
        runBlocking {
            var attempts = 0
            val maxAttempts = 10
            
            while (attempts < maxAttempts) {
                s3Client.runCatching {
                    createBucket(CreateBucketRequest {
                        bucket = AwsConfiguration.TEST_BUCKET_NAME
                    })
                }.onSuccess {
                    logger.info { "Test bucket created successfully: ${AwsConfiguration.TEST_BUCKET_NAME}" }
                    return@runBlocking
                }.onFailure { exception ->
                    attempts++
                    if (attempts >= maxAttempts) {
                        logger.error(exception) { "Failed to create test bucket after $maxAttempts attempts: ${AwsConfiguration.TEST_BUCKET_NAME}" }
                        throw exception
                    } else {
                        logger.warn { "Test bucket creation attempt $attempts failed, retrying... (${exception.message})" }
                        delay(500) // Wait 500ms before retry
                    }
                }
            }
        }
        
        return S3ObjectStorageAdapter(AwsConfiguration.TEST_BUCKET_NAME, s3Client)
    }
}