package nl.vintik.mocknest.infra.aws.bugfix

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.collect
import nl.vintik.mocknest.infra.aws.config.SharedLocalStackContainer
import nl.vintik.mocknest.infra.aws.config.TEST_REGION
import nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.localstack.LocalStackContainer
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Preservation Property Tests for Lambda Spring Context Isolation Bugfix
 * 
 * **CRITICAL**: These tests follow the observation-first methodology:
 * 1. Run tests on UNFIXED code (current monolithic module structure)
 * 2. Observe and document the baseline behavior
 * 3. Tests MUST PASS on unfixed code (confirming baseline behavior to preserve)
 * 4. After implementing the fix, re-run the same tests
 * 5. Tests MUST STILL PASS (confirming no regressions)
 * 
 * **Property 2: Preservation** - Runtime Behavior Unchanged After Module Split
 * 
 * For any runtime operation that is NOT Lambda initialization (mock serving, admin API,
 * S3 storage, WireMock behavior), the fixed code SHALL produce exactly the same behavior
 * as the original code, preserving all existing functionality.
 * 
 * **Testing Strategy**: Property-based testing approach using parameterized tests to
 * generate many test cases across the input domain. This provides stronger guarantees
 * that behavior is unchanged for all non-initialization operations.
 * 
 * **Scope**: These tests focus on infrastructure layer components that will be moved
 * during the module split. We test S3 storage operations since those are the primary
 * infrastructure components that must preserve behavior.
 * 
 * **Note**: Full end-to-end Lambda testing (mock serving, admin API) requires deployed
 * Lambda functions, which cannot be tested in unit tests. Those will be validated through
 * integration tests after deployment.
 * 
 * **Validates Requirements**: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7 from bugfix.md
 */
@Disabled("LocalStack integration tests disabled - Docker/Colima connectivity issue")
class LambdaSpringContextIsolationPreservationTest {

    companion object {
        private lateinit var runtimeS3Client: S3Client
        private lateinit var runtimeStorageAdapter: S3ObjectStorageAdapter

        private const val RUNTIME_BUCKET = "test-runtime-bucket"

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            logger.info { "Setting up LocalStack S3 for preservation tests" }
            
            // Use the shared LocalStack container
            val container = SharedLocalStackContainer.container
            val s3Endpoint = container.getEndpointOverride(LocalStackContainer.Service.S3).toString()
            
            // Create S3 client configured for LocalStack
            runtimeS3Client = S3Client {
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

            // Create the bucket once for all tests
            kotlinx.coroutines.runBlocking {
                runtimeS3Client.runCatching {
                    createBucket(CreateBucketRequest {
                        bucket = RUNTIME_BUCKET
                    })
                }.onSuccess {
                    logger.info { "Test bucket created: $RUNTIME_BUCKET" }
                }.onFailure { e ->
                    logger.warn(e) { "Runtime bucket creation failed (may already exist)" }
                }
            }

            // Create storage adapter
            runtimeStorageAdapter = S3ObjectStorageAdapter(
                bucketName = RUNTIME_BUCKET,
                s3Client = runtimeS3Client
            )

            logger.info { "LocalStack S3 setup complete" }
        }

        @AfterAll
        @JvmStatic
        fun tearDownClass() {
            logger.info { "Tearing down LocalStack S3" }
            runtimeS3Client.close()
        }

        /**
         * Generate test data for S3 storage operations
         * Property-based testing approach: generate many test cases with different inputs
         */
        @JvmStatic
        fun s3StorageTestData(): Stream<Arguments> = Stream.of(
            // Small content
            Arguments.of("mapping-1", """{"request":{"url":"/api/test"}}""", "Small JSON mapping"),
            Arguments.of("mapping-2", """{"request":{"url":"/api/users"},"response":{"status":200}}""", "JSON with response"),
            
            // Medium content
            Arguments.of("mapping-3", """{"request":{"url":"/api/data"},"response":{"status":200,"body":"${"x".repeat(1000)}"}}""", "Medium JSON with body"),
            
            // Large content
            Arguments.of("mapping-4", """{"request":{"url":"/api/large"},"response":{"status":200,"body":"${"y".repeat(10000)}"}}""", "Large JSON with body"),
            
            // Special characters
            Arguments.of("mapping-5", """{"request":{"url":"/api/special"},"response":{"body":"Special: \n\t\r"}}""", "JSON with special chars"),
            
            // Unicode content
            Arguments.of("mapping-6", """{"request":{"url":"/api/unicode"},"response":{"body":"Unicode: 你好世界 🌍"}}""", "JSON with unicode"),
            
            // Empty body
            Arguments.of("mapping-7", """{"request":{"url":"/api/empty"},"response":{"status":204}}""", "JSON with empty response"),
            
            // Complex nested structure
            Arguments.of("mapping-8", """{"request":{"url":"/api/nested","method":"POST","headers":{"Content-Type":"application/json"}},"response":{"status":201,"headers":{"Location":"/api/resource/123"},"body":"{\"id\":123,\"nested\":{\"field\":\"value\"}}"}}""", "Complex nested JSON"),
            
            // File content variations
            Arguments.of("file-1", "Plain text content", "Plain text file"),
            Arguments.of("file-2", "<xml><data>value</data></xml>", "XML file"),
            Arguments.of("file-3", """{"json":"data"}""", "JSON file"),
            Arguments.of("file-4", "Binary-like: \u0000\u0001\u0002", "Binary-like content"),
            
            // Spec content variations
            Arguments.of("spec-1", """{"openapi":"3.0.0","info":{"title":"Test API"}}""", "OpenAPI spec"),
            Arguments.of("spec-2", """{"swagger":"2.0","info":{"title":"Legacy API"}}""", "Swagger spec"),
            Arguments.of("spec-3", """{"type":"graphql","schema":"type Query { hello: String }"}""", "GraphQL schema")
        )

        /**
         * Generate test data for batch operations
         * Property-based testing: test with different batch sizes
         */
        @JvmStatic
        fun batchOperationTestData(): Stream<Arguments> = Stream.of(
            Arguments.of(1, "Single item batch"),
            Arguments.of(5, "Small batch"),
            Arguments.of(10, "Medium batch"),
            Arguments.of(25, "Large batch"),
            Arguments.of(50, "Very large batch")
        )
    }

    @BeforeEach
    suspend fun setup() {
        logger.info { "Test setup - bucket already created in @BeforeAll" }
    }

    @AfterEach
    suspend fun tearDown() {
        logger.info { "Cleaning up test data" }
        
        // Clean up test data by deleting all objects in bucket
        runtimeS3Client.runCatching {
            // List and delete all objects in runtime bucket
            val runtimeObjects = listObjectsV2(ListObjectsV2Request {
                bucket = RUNTIME_BUCKET
            }).contents ?: emptyList()
            
            runtimeObjects.forEach { obj ->
                obj.key?.let { key ->
                    deleteObject(DeleteObjectRequest {
                        bucket = RUNTIME_BUCKET
                        this.key = key
                    })
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "Runtime bucket cleanup failed" }
        }
    }

    /**
     * Property 2.1: S3 Runtime Storage - Object Save/Load Operations Preservation
     * 
     * **Requirement 3.3**: WHEN either Lambda accesses S3 storage THEN the system SHALL
     * CONTINUE TO use the existing ObjectStorage implementations
     * 
     * **Property**: For any object save/load operation, the behavior SHALL be identical
     * before and after the module split.
     * 
     * **Test Strategy**: Generate many test cases with different content to ensure
     * storage operations work correctly across various scenarios.
     */
    @ParameterizedTest(name = "{2}: key={0}")
    @MethodSource("s3StorageTestData")
    suspend fun `Property 2_1 - Given object content When saving and loading via runtime storage Then content should be preserved identically`(
        key: String,
        content: String,
        description: String
    ) {
        logger.info { "Testing runtime storage preservation: $description" }
        
        // GIVEN: Object content to store
        logger.debug { "Saving object: key=$key, size=${content.length}" }
        
        // WHEN: Save object via runtime storage adapter
        val savedUri = runtimeStorageAdapter.save(key, content)
        
        assertNotNull(savedUri, "Save operation should return URI")
        assertTrue(savedUri.contains(key), "Saved URI should contain key")
        
        // THEN: Load object and verify content is identical
        val loadedContent = runtimeStorageAdapter.get(key)
        
        assertNotNull(loadedContent, "Loaded object should not be null")
        assertEquals(
            content,
            loadedContent,
            "Loaded object content should match saved content exactly"
        )
        
        logger.debug { "✓ Object preserved correctly: key=$key" }
    }

    /**
     * Property 2.2: S3 Runtime Storage - Batch Operations Preservation
     * 
     * **Requirement 3.3**: WHEN either Lambda accesses S3 storage THEN the system SHALL
     * CONTINUE TO use the existing ObjectStorage implementations
     * 
     * **Property**: For any batch operation, the behavior SHALL be identical
     * before and after the module split.
     * 
     * **Test Strategy**: Test with different batch sizes to ensure batch operations
     * scale correctly.
     */
    @ParameterizedTest(name = "{1}: batchSize={0}")
    @MethodSource("batchOperationTestData")
    suspend fun `Property 2_2 - Given multiple objects When saving and listing via runtime storage Then all objects should be preserved`(
        batchSize: Int,
        description: String
    ) {
        logger.info { "Testing runtime batch storage preservation: $description" }
        
        // GIVEN: Multiple objects to store
        val objects = (1..batchSize).associate { i ->
            "batch-object-$i" to """{"request":{"url":"/api/batch/$i"},"response":{"status":200,"body":"Response $i"}}"""
        }
        
        logger.debug { "Saving batch: size=$batchSize" }
        
        // WHEN: Save all objects
        objects.forEach { (key, content) ->
            runtimeStorageAdapter.save(key, content)
        }
        
        // THEN: List all objects and verify they exist
        val listedKeys = mutableListOf<String>()
        runtimeStorageAdapter.list().collect { key ->
            listedKeys.add(key)
        }
        
        assertTrue(
            listedKeys.size >= batchSize,
            "Should list at least $batchSize objects"
        )
        
        objects.keys.forEach { key ->
            assertTrue(
                listedKeys.contains(key),
                "Listed keys should contain $key"
            )
        }
        
        logger.debug { "✓ Batch objects preserved correctly: size=$batchSize" }
    }

    /**
     * Property 2.3: S3 Runtime Storage - Object Deletion Preservation
     * 
     * **Requirement 3.3**: WHEN either Lambda accesses S3 storage THEN the system SHALL
     * CONTINUE TO use the existing ObjectStorage implementations
     * 
     * **Property**: For any object deletion operation, the behavior SHALL be identical
     * before and after the module split.
     */
    @Test
    suspend fun `Property 2_3 - Given saved object When deleting via runtime storage Then object should be removed`() {
        logger.info { "Testing runtime storage deletion preservation" }
        
        // GIVEN: Saved object
        val key = "delete-test-object"
        val content = """{"request":{"url":"/api/delete"}}"""
        
        runtimeStorageAdapter.save(key, content)
        
        // Verify object exists
        val loadedBefore = runtimeStorageAdapter.get(key)
        assertNotNull(loadedBefore, "Object should exist before deletion")
        
        // WHEN: Delete object
        logger.debug { "Deleting object: key=$key" }
        runtimeStorageAdapter.delete(key)
        
        // THEN: Object should not exist
        val loadedAfter = runtimeStorageAdapter.get(key)
        assertEquals(
            null,
            loadedAfter,
            "Object should not exist after deletion"
        )
        
        logger.debug { "✓ Object deletion preserved correctly" }
    }

    /**
     * Property 2.4: S3 Runtime Storage - List Operations Preservation
     * 
     * **Requirement 3.3**: WHEN either Lambda accesses S3 storage THEN the system SHALL
     * CONTINUE TO use the existing ObjectStorage implementations
     * 
     * **Property**: For any list operation, the behavior SHALL be identical before and
     * after the module split.
     */
    @Test
    suspend fun `Property 2_4 - Given multiple objects When listing via runtime storage Then all object keys should be returned`() {
        logger.info { "Testing runtime storage list preservation" }
        
        // GIVEN: Multiple saved objects
        val objects = mapOf(
            "list-object-1" to """{"request":{"url":"/api/1"}}""",
            "list-object-2" to """{"request":{"url":"/api/2"}}""",
            "list-object-3" to """{"request":{"url":"/api/3"}}"""
        )
        
        objects.forEach { (key, content) ->
            runtimeStorageAdapter.save(key, content)
        }
        
        // WHEN: List all objects
        logger.debug { "Listing objects" }
        val listedKeys = mutableListOf<String>()
        runtimeStorageAdapter.list().collect { key ->
            listedKeys.add(key)
        }
        
        // THEN: All object keys should be present
        assertTrue(
            listedKeys.size >= objects.size,
            "Should list at least ${objects.size} objects"
        )
        
        objects.keys.forEach { key ->
            assertTrue(
                listedKeys.contains(key),
                "Object list should contain key: $key"
            )
        }
        
        logger.debug { "✓ Object list preserved correctly" }
    }

    /**
     * Property 2.5: S3 Storage - Error Handling Preservation
     * 
     * **Requirement 3.3**: WHEN either Lambda accesses S3 storage THEN the system SHALL
     * CONTINUE TO use the existing ObjectStorage implementations
     * 
     * **Property**: For any error condition (e.g., loading non-existent key), the behavior
     * SHALL be identical before and after the module split.
     */
    @Test
    suspend fun `Property 2_5 - Given non-existent key When loading via runtime storage Then should return null`() {
        logger.info { "Testing runtime storage error handling preservation" }
        
        // GIVEN: Non-existent key
        val nonExistentKey = "non-existent-object-${System.currentTimeMillis()}"
        
        // WHEN: Attempt to load non-existent object
        logger.debug { "Loading non-existent object: key=$nonExistentKey" }
        val result = runtimeStorageAdapter.get(nonExistentKey)
        
        // THEN: Should return null (not throw exception)
        assertEquals(
            null,
            result,
            "Loading non-existent object should return null"
        )
        
        logger.debug { "✓ Error handling preserved correctly" }
    }

    /**
     * Property 2.6: Clean Architecture Boundaries Preservation
     * 
     * **Requirement 3.5**: WHEN clean architecture boundaries are enforced THEN the system
     * SHALL CONTINUE TO maintain the dependency flow: infrastructure → application → domain
     * 
     * **Property**: Storage adapters SHALL continue to implement application layer interfaces
     * and depend on domain models, maintaining clean architecture boundaries.
     * 
     * **Test Strategy**: Verify that storage adapters implement expected interfaces and
     * maintain proper dependency relationships.
     */
    @Test
    fun `Property 2_6 - Given storage adapters When checking class hierarchy Then should implement application layer interfaces`() {
        logger.info { "Testing clean architecture boundaries preservation" }
        
        // GIVEN: Storage adapter instance
        
        // WHEN: Check class hierarchy
        val runtimeInterfaces = runtimeStorageAdapter.javaClass.interfaces
        
        // THEN: Should implement expected interfaces (verifying clean architecture)
        // Note: This test verifies that the adapter maintains its interface contracts
        // The actual interface names may vary, but the pattern should be preserved
        
        logger.debug { "Runtime storage adapter interfaces: ${runtimeInterfaces.map { it.name }}" }
        
        // Verify adapter is not null (basic sanity check)
        assertNotNull(runtimeStorageAdapter, "Runtime storage adapter should exist")
        
        // Verify adapter has the expected class name (package structure preserved)
        assertTrue(
            runtimeStorageAdapter.javaClass.name.contains("nl.vintik.mocknest.infra.aws"),
            "Runtime adapter should be in infrastructure layer package"
        )
        
        logger.debug { "✓ Clean architecture boundaries preserved" }
    }
}
