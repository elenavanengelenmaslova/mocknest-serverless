package nl.vintik.mocknest.infra.aws.runtime.health

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.domain.runtime.RuntimeHealth
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpStatusCode

class AwsRuntimeHealthUseCaseTest {
    
    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    private val useCase = AwsRuntimeHealthUseCase(mockStorage, bucketName)
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `Given healthy storage When getting health Then should return healthy status`() {
        // Given
        every { mockStorage.list() } returns flowOf("test-key")
        
        // When
        val response = useCase.invoke()
        
        // Then
        assertEquals(HttpStatusCode.OK, response.statusCode)
        assertEquals("application/json", response.headers?.get("Content-Type")?.first())
        assertNotNull(response.body)
        
        val health = mapper.readValue(response.body, RuntimeHealth::class.java)
        assertEquals("healthy", health.status)
        assertNotNull(health.version) // Version should be present but don't assert specific value
        assertEquals(bucketName, health.storage.bucket)
        assertEquals("ok", health.storage.connectivity)
    }
    
    @Test
    fun `Given inaccessible storage When checking connectivity Then should return degraded status`() {
        // Given
        every { mockStorage.list() } throws RuntimeException("S3 connection failed")
        
        // When
        val response = useCase.invoke()
        
        // Then
        assertEquals(HttpStatusCode.OK, response.statusCode)
        val health = mapper.readValue(response.body, RuntimeHealth::class.java)
        assertEquals("degraded", health.status)
        assertEquals("error", health.storage.connectivity)
        assertEquals(bucketName, health.storage.bucket)
    }
    
    @Test
    fun `Given AWS_REGION environment variable When checking health Then should include region`() {
        // Given
        every { mockStorage.list() } returns flowOf("test-key")
        
        // When
        val response = useCase.invoke()
        
        // Then
        assertEquals(HttpStatusCode.OK, response.statusCode)
        val health = mapper.readValue(response.body, RuntimeHealth::class.java)
        assertNotNull(health.region) // Should be "unknown" or actual AWS region
    }
}