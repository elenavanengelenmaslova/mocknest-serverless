package nl.vintik.mocknest.infra.aws.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.localstack.LocalStackContainer

private val logger = KotlinLogging.logger {}

/**
 * Helper for creating LocalStack-backed S3 clients and test buckets
 * for Koin-based integration tests.
 */
object LocalStackTestHelper {

    /**
     * Creates an S3Client pointing at the shared LocalStack container.
     */
    fun createTestS3Client(): S3Client {
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
                    secretAccessKey = container.secretKey,
                )
            )
        }
    }

    /**
     * Ensures the test bucket exists, creating it if necessary.
     */
    fun ensureTestBucket(s3Client: S3Client) {
        runBlocking {
            s3Client.runCatching {
                createBucket(CreateBucketRequest { bucket = TEST_BUCKET_NAME })
            }.onSuccess {
                logger.info { "Test bucket created: $TEST_BUCKET_NAME" }
            }.onFailure { exception ->
                val message = exception.message?.lowercase() ?: ""
                if (!message.contains("bucketalreadyownedbyyou") && !message.contains("bucketalreadyexists")) {
                    logger.error(exception) { "Failed to create test bucket: $TEST_BUCKET_NAME" }
                    throw exception
                }
                logger.info { "Test bucket already exists: $TEST_BUCKET_NAME" }
            }
        }
    }
}
