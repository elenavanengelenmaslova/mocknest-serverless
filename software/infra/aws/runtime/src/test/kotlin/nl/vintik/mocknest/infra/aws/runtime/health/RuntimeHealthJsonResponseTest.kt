package nl.vintik.mocknest.infra.aws.runtime.health

import nl.vintik.mocknest.domain.runtime.RuntimeHealth
import nl.vintik.mocknest.domain.runtime.StorageHealth
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RuntimeHealthJsonResponseTest {

    @Nested
    inner class RuntimeHealthJsonResponseMapping {

        @Test
        fun `Given healthy RuntimeHealth When converting to JSON response Then should map all fields correctly`() {
            // Given
            val timestamp = Instant.now()
            val testVersion = "test-version"
            val health = RuntimeHealth(
                status = "healthy",
                timestamp = timestamp,
                region = "eu-west-1",
                version = testVersion,
                storage = StorageHealth(
                    bucket = "test-bucket",
                    connectivity = "ok"
                )
            )

            // When
            val jsonResponse = RuntimeHealthJsonResponse.from(health)

            // Then
            assertEquals("healthy", jsonResponse.status)
            assertEquals(timestamp.toString(), jsonResponse.timestamp)
            assertEquals("eu-west-1", jsonResponse.region)
            assertEquals(testVersion, jsonResponse.version)
            assertNotNull(jsonResponse.storage)
            assertEquals("test-bucket", jsonResponse.storage.bucket)
            assertEquals("ok", jsonResponse.storage.connectivity)
        }

        @Test
        fun `Given degraded RuntimeHealth When converting to JSON response Then should map degraded status`() {
            // Given
            val timestamp = Instant.now()
            val testVersion = "test-version"
            val health = RuntimeHealth(
                status = "degraded",
                timestamp = timestamp,
                region = "us-east-1",
                version = testVersion,
                storage = StorageHealth(
                    bucket = "production-bucket",
                    connectivity = "error"
                )
            )

            // When
            val jsonResponse = RuntimeHealthJsonResponse.from(health)

            // Then
            assertEquals("degraded", jsonResponse.status)
            assertEquals(timestamp.toString(), jsonResponse.timestamp)
            assertEquals("us-east-1", jsonResponse.region)
            assertEquals(testVersion, jsonResponse.version)
            assertEquals("production-bucket", jsonResponse.storage.bucket)
            assertEquals("error", jsonResponse.storage.connectivity)
        }

        @Test
        fun `Given RuntimeHealth with unknown region When converting to JSON response Then should preserve unknown region`() {
            // Given
            val timestamp = Instant.now()
            val testVersion = "test-version"
            val health = RuntimeHealth(
                status = "healthy",
                timestamp = timestamp,
                region = "unknown",
                version = testVersion,
                storage = StorageHealth(
                    bucket = "test-bucket",
                    connectivity = "ok"
                )
            )

            // When
            val jsonResponse = RuntimeHealthJsonResponse.from(health)

            // Then
            assertEquals("unknown", jsonResponse.region)
        }
    }

    @Nested
    inner class StorageHealthJsonMapping {

        @Test
        fun `Given healthy StorageHealth When converting to JSON Then should map all fields correctly`() {
            // Given
            val storage = StorageHealth(
                bucket = "my-bucket",
                connectivity = "ok"
            )

            // When
            val jsonStorage = StorageHealthJson.from(storage)

            // Then
            assertEquals("my-bucket", jsonStorage.bucket)
            assertEquals("ok", jsonStorage.connectivity)
        }

        @Test
        fun `Given StorageHealth with error When converting to JSON Then should map error connectivity`() {
            // Given
            val storage = StorageHealth(
                bucket = "failed-bucket",
                connectivity = "error"
            )

            // When
            val jsonStorage = StorageHealthJson.from(storage)

            // Then
            assertEquals("failed-bucket", jsonStorage.bucket)
            assertEquals("error", jsonStorage.connectivity)
        }

        @Test
        fun `Given StorageHealth with long bucket name When converting to JSON Then should preserve full bucket name`() {
            // Given
            val longBucketName = "my-very-long-bucket-name-with-many-characters-2024"
            val storage = StorageHealth(
                bucket = longBucketName,
                connectivity = "ok"
            )

            // When
            val jsonStorage = StorageHealthJson.from(storage)

            // Then
            assertEquals(longBucketName, jsonStorage.bucket)
        }
    }

    @Nested
    inner class DataClassProperties {

        @Test
        fun `Given RuntimeHealthJsonResponse When creating with constructor Then should set all properties`() {
            // Given / When
            val testVersion = "test-version"
            val storage = StorageHealthJson(
                bucket = "test-bucket",
                connectivity = "ok"
            )
            val response = RuntimeHealthJsonResponse(
                status = "healthy",
                timestamp = "2024-03-17T10:00:00Z",
                region = "eu-west-1",
                version = testVersion,
                storage = storage
            )

            // Then
            assertEquals("healthy", response.status)
            assertEquals("2024-03-17T10:00:00Z", response.timestamp)
            assertEquals("eu-west-1", response.region)
            assertEquals(testVersion, response.version)
            assertEquals("test-bucket", response.storage.bucket)
            assertEquals("ok", response.storage.connectivity)
        }

        @Test
        fun `Given StorageHealthJson When creating with constructor Then should set all properties`() {
            // Given / When
            val storage = StorageHealthJson(
                bucket = "direct-bucket",
                connectivity = "ok"
            )

            // Then
            assertEquals("direct-bucket", storage.bucket)
            assertEquals("ok", storage.connectivity)
        }
    }
}
