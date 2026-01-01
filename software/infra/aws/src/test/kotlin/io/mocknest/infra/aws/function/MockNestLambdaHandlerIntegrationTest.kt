package io.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import io.mocknest.application.interfaces.storage.ObjectStorageInterface
import io.mocknest.infra.aws.config.AwsConfiguration
import io.mocknest.infra.aws.storage.S3ObjectStorageAdapter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.net.url.Url
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class MockNestLambdaHandlerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        private val localStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.12.0")
        ).withServices(
            LocalStackContainer.Service.S3
        ).waitingFor(
            Wait.forLogMessage(".*Ready.*", 1)
        ).apply {
            // Ensure container starts before any test setup
            start()
        }

        private lateinit var s3Client: S3Client
        private lateinit var storage: ObjectStorageInterface

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            // Wait a bit more to ensure LocalStack is fully ready
            Thread.sleep(2000)
            
            // Configure LocalStack S3 client
            val s3Endpoint = localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
            
            // Set system properties for LocalStack
            System.setProperty("aws.accessKeyId", localStackContainer.accessKey)
            System.setProperty("aws.secretAccessKey", localStackContainer.secretKey)
            System.setProperty("aws.region", AwsConfiguration.TEST_REGION)
            
            s3Client = S3Client {
                region = AwsConfiguration.TEST_REGION
                endpointUrl = Url.parse(s3Endpoint)
                forcePathStyle = true  // Required for LocalStack
            }
            
            // Create test bucket once for all tests
            runBlocking {
                s3Client.createBucket(CreateBucketRequest {
                    bucket = AwsConfiguration.TEST_BUCKET_NAME
                    // For us-east-1, we don't specify createBucketConfiguration
                    // For other regions, we would need to specify the location constraint
                })
            }
            
            storage = S3ObjectStorageAdapter(AwsConfiguration.TEST_BUCKET_NAME, s3Client)
        }

        @AfterAll
        @JvmStatic
        fun tearDownClass() {
            // Clean up resources
            runBlocking {
                s3Client.close()
            }
        }
    }

    @BeforeEach
    suspend fun setup() {
        // Clear storage before each test
        val keys = storage.list().toList()
        keys.forEach { key -> storage.delete(key) }
    }

    @AfterEach
    suspend fun tearDown() {
        // Clean up after each test
        val keys = storage.list().toList()
        keys.forEach { key -> storage.delete(key) }
    }

    private fun createApiGatewayEvent(
        httpMethod: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        queryStringParameters: Map<String, String> = emptyMap()
    ): APIGatewayProxyRequestEvent {
        return APIGatewayProxyRequestEvent().apply {
            this.httpMethod = httpMethod
            this.path = path
            this.body = body
            this.headers = headers
            this.queryStringParameters = queryStringParameters.takeIf { it.isNotEmpty() }
        }
    }

    @Test
    suspend fun `Given S3 storage When saving and retrieving object Then should work correctly`() {
        // Given
        val testKey = "test-key"
        val testContent = "test content"

        // When
        storage.save(testKey, testContent)
        val retrievedContent = storage.get(testKey)

        // Then
        assertEquals(testContent, retrievedContent)
    }

    @Test
    suspend fun `Given S3 storage When saving mapping and file Then should store both correctly`() {
        // Given
        val mappingKey = "mappings/test-mapping.json"
        val fileKey = "__files/test-file.txt"
        val mappingContent = """{"id": "test", "request": {"method": "GET"}}"""
        val fileContent = "response body content"

        // When
        storage.save(mappingKey, mappingContent)
        storage.save(fileKey, fileContent)
        
        val keys = storage.list().toList()
        val mappingResult = storage.get(mappingKey)
        val fileResult = storage.get(fileKey)

        // Then
        assertEquals(2, keys.size)
        assertTrue(keys.contains(mappingKey))
        assertTrue(keys.contains(fileKey))
        assertEquals(mappingContent, mappingResult)
        assertEquals(fileContent, fileResult)
    }

    @Test
    suspend fun `Given S3 storage When deleting objects Then should remove them correctly`() {
        // Given
        val testKey = "test-delete-key"
        val testContent = "content to delete"

        storage.save(testKey, testContent)
        
        // Verify it exists
        val beforeDelete = storage.list().toList()
        assertTrue(beforeDelete.contains(testKey))

        // When
        storage.delete(testKey)

        // Then
        val afterDelete = storage.list().toList()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    suspend fun `Given S3 storage When listing with prefix Then should filter correctly`() {
        // Given
        val mappingKey1 = "mappings/mapping1.json"
        val mappingKey2 = "mappings/mapping2.json"
        val fileKey = "__files/file1.txt"

        storage.save(mappingKey1, "mapping1")
        storage.save(mappingKey2, "mapping2")
        storage.save(fileKey, "file1")

        // When
        val mappingKeys = storage.listPrefix("mappings/").toList()
        val fileKeys = storage.listPrefix("__files/").toList()

        // Then
        assertEquals(2, mappingKeys.size)
        assertTrue(mappingKeys.contains(mappingKey1))
        assertTrue(mappingKeys.contains(mappingKey2))
        
        assertEquals(1, fileKeys.size)
        assertTrue(fileKeys.contains(fileKey))
    }

    @Test
    suspend fun `Given S3 storage When clearing all objects Then should delete everything`() {
        // Given
        val keys = listOf("mappings/m1.json", "mappings/m2.json", "__files/f1.txt", "__files/f2.txt")
        
        keys.forEach { key ->
            storage.save(key, "content for $key")
        }
        
        // Verify all exist
        val beforeClear = storage.list().toList()
        assertEquals(4, beforeClear.size)

        // When - Delete all
        val keysToDelete = storage.list().toList()
        keysToDelete.forEach { key -> storage.delete(key) }

        // Then
        val afterClear = storage.list().toList()
        assertTrue(afterClear.isEmpty())
    }
}