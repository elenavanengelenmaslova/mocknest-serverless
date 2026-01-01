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
import org.testcontainers.utility.DockerImageName
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking

@TestConfiguration
class AwsLocalStackTestConfiguration {

    private val logger = KotlinLogging.logger {}

    companion object {
        val localStackContainer: LocalStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.12.0")
        ).withServices(
            LocalStackContainer.Service.S3,
            LocalStackContainer.Service.LAMBDA,
            LocalStackContainer.Service.API_GATEWAY
        ).apply {
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val s3Endpoint = localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
            registry.add("aws.endpointUrl") { s3Endpoint }
            registry.add("aws.accessKeyId") { localStackContainer.accessKey }
            registry.add("aws.secretAccessKey") { localStackContainer.secretKey }
            registry.add("aws.region") { AwsConfiguration.TEST_REGION }
            registry.add("mocknest.s3.bucket-name") { AwsConfiguration.TEST_BUCKET_NAME }
            registry.add("aws.s3.bucket-name") { AwsConfiguration.TEST_BUCKET_NAME }
        }
    }

    @Bean("testS3Client")
    @Primary
    fun testS3Client(): S3Client {
        val s3Endpoint = localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        
        // Set system properties for LocalStack
        System.setProperty("aws.accessKeyId", localStackContainer.accessKey)
        System.setProperty("aws.secretAccessKey", localStackContainer.secretKey)
        System.setProperty("aws.region", AwsConfiguration.TEST_REGION)
        
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
        
        // Create the test bucket
        runBlocking {
            s3Client.runCatching {
                createBucket(CreateBucketRequest {
                    bucket = AwsConfiguration.TEST_BUCKET_NAME
                })
            }.onFailure { exception ->
                logger.warn(exception) { "Test bucket creation failed, may already exist: ${AwsConfiguration.TEST_BUCKET_NAME}" }
            }
        }
        
        return S3ObjectStorageAdapter(AwsConfiguration.TEST_BUCKET_NAME, s3Client)
    }
}