package nl.vintik.mocknest.infra.aws.storage

import aws.sdk.kotlin.runtime.auth.credentials.*
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.*
import aws.smithy.kotlin.runtime.net.url.Url
import nl.vintik.mocknest.application.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.infra.aws.config.SharedLocalStackContainer
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.testcontainers.containers.localstack.LocalStackContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val TEST_BUCKET_NAME = "test-bucket"

class S3StorageIntegrationTest {

    companion object {
        private lateinit var s3Client: S3Client
        private lateinit var storage: ObjectStorageInterface

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            // Use the shared LocalStack container - no sleep needed, TestContainers handles readiness
            val container = SharedLocalStackContainer.container
            val s3Endpoint = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()

            s3Client = S3Client {
                region = TEST_REGION
                endpointUrl =
                    Url.parse(s3Endpoint)
                forcePathStyle = true  // Required for LocalStack

                credentialsProvider = StaticCredentialsProvider(
                    Credentials(
                        accessKeyId = container.accessKey,      // usually "test"
                        secretAccessKey = container.secretKey   // usually "test"
                    )
                )
            }

            // Create test bucket once for all tests
            runBlocking {
                s3Client.createBucket(CreateBucketRequest {
                    bucket = TEST_BUCKET_NAME
                })
            }

            storage = S3ObjectStorageAdapter(TEST_BUCKET_NAME, s3Client)
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
    fun setup() {
    }

    @AfterEach
    suspend fun tearDown() {
        // Clean up after each test
        val keys = storage.list().toList()
        keys.forEach { key -> storage.delete(key) }
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

    @Test
    suspend fun `Given multiple ids When getMany is called Then should return contents for all`() {
        // Given
        val keys = listOf("k1.txt", "k2.txt", "k3.txt")
        keys.forEach { storage.save(it, "content-$it") }

        // When
        val result = storage
            .getMany(keys.asFlow(), concurrency = 2)
            .toList()

        // Then
        assertEquals(3, result.size)
        result.forEach { (id, content) ->
            assertEquals("content-$id", content)
        }
    }

    @Test
    suspend fun `Given many ids When deleteMany is called Then should delete all objects`() {
        // Given: more than 1 batch is NOT required, but parallelism is
        val keys = (1..5).map { "bulk-$it.txt" }
        keys.forEach { storage.save(it, "content-$it") }

        // Sanity check
        assertEquals(5, storage.list().toList().size)

        // When
        storage.deleteMany(keys.asFlow(), concurrency = 3)

        // Then
        val remaining = storage.list().toList()
        assertTrue(remaining.isEmpty())
    }

    @Test
    suspend fun `Given missing ids When getMany is called Then should return null contents`() {
        // Given
        val keys = listOf("missing-1", "missing-2")

        // When
        val result = storage
            .getMany(keys.asFlow(), concurrency = 2)
            .toList()

        // Then
        assertEquals(2, result.size)
        result.forEach { (_, content) ->
            assertEquals(null, content)
        }
    }

    @Test
    suspend fun `Given missing ids When deleteMany is called Then should not fail`() {
        // Given
        val keys = listOf("does-not-exist-1", "does-not-exist-2")

        // When / Then (should not throw)
        storage.deleteMany(keys.asFlow(), concurrency = 2)
    }

}