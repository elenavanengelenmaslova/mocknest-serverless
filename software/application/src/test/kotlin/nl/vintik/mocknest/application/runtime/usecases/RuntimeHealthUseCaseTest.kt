package nl.vintik.mocknest.application.runtime.usecases

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuntimeHealthUseCaseTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val useCase = RuntimeHealthUseCase(mockStorage, "test-bucket", "us-east-1")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given healthy storage When invoking health check Then should return healthy HTTP response`() = runTest {
        // Given
        every { mockStorage.list() } returns flowOf("test-key")

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertNotNull(response.body)
        assertTrue(response.body?.contains("\"status\":\"healthy\"") == true)
        assertTrue(response.body?.contains("\"region\":\"us-east-1\"") == true)
        assertTrue(response.body?.contains("\"bucket\":\"test-bucket\"") == true)
        assertTrue(response.body?.contains("\"connectivity\":\"ok\"") == true)
    }

    @Test
    fun `Given unhealthy storage When invoking health check Then should return degraded status`() = runTest {
        // Given
        every { mockStorage.list() } throws RuntimeException("Storage unavailable")

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"status\":\"degraded\"") == true)
        assertTrue(response.body?.contains("\"region\":\"us-east-1\"") == true)
        assertTrue(response.body?.contains("\"bucket\":\"test-bucket\"") == true)
        assertTrue(response.body?.contains("\"connectivity\":\"error\"") == true)
    }

    @Test
    fun `Given different bucket and region When invoking health check Then should return correct metadata`() = runTest {
        // Given
        val useCase = RuntimeHealthUseCase(mockStorage, "production-bucket", "eu-west-1")
        every { mockStorage.list() } returns flowOf("test-key")

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"region\":\"eu-west-1\"") == true)
        assertTrue(response.body?.contains("\"bucket\":\"production-bucket\"") == true)
        assertTrue(response.body?.contains("\"status\":\"healthy\"") == true)
    }

    @Test
    fun `Given runtime health check When invoking Then should include timestamp and version`() = runTest {
        // Given
        every { mockStorage.list() } returns flowOf("test-key")

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"timestamp\":") == true)
        assertTrue(response.body?.contains("\"version\":") == true)
        // Version should not be "unknown"
        assertTrue(response.body?.contains("\"version\":\"unknown\"") == false)
    }

    @Test
    fun `Given storage connectivity check When storage throws exception Then should handle gracefully`() = runTest {
        // Given
        every { mockStorage.list() } throws IllegalStateException("Connection failed")

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"status\":\"degraded\"") == true)
        assertTrue(response.body?.contains("\"connectivity\":\"error\"") == true)
    }

    @Test
    fun `Given multiple regions When invoking health check Then should return correct region`() = runTest {
        // Given
        val useCase1 = RuntimeHealthUseCase(mockStorage, "bucket1", "ap-southeast-1")
        val useCase2 = RuntimeHealthUseCase(mockStorage, "bucket2", "ca-central-1")
        every { mockStorage.list() } returns flowOf("test-key")

        // When
        val response1 = useCase1.invoke()
        val response2 = useCase2.invoke()

        // Then
        assertTrue(response1.body?.contains("\"region\":\"ap-southeast-1\"") == true)
        assertTrue(response1.body?.contains("\"bucket\":\"bucket1\"") == true)
        assertTrue(response2.body?.contains("\"region\":\"ca-central-1\"") == true)
        assertTrue(response2.body?.contains("\"bucket\":\"bucket2\"") == true)
    }
}