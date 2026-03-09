package nl.vintik.mocknest.infra.aws.runtime.health

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class RuntimeHealthUseCaseTest {
    
    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val bucketName = "test-bucket"
    
    private lateinit var useCase: RuntimeHealthUseCase
    
    @BeforeEach
    fun setup() {
        useCase = RuntimeHealthUseCase(mockStorage, bucketName)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Nested
    inner class HealthEndpointResponseStructure {
        
        @Test
        fun `Given healthy storage When getting health Then should return 200 OK`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            
            assertEquals(HttpStatus.OK, response.statusCode)
        }
        
        @Test
        fun `Given healthy storage When getting health Then should return JSON content type`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            
            assertNotNull(response.headers)
            assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        }
        
        @Test
        fun `Given healthy storage When getting health Then should return valid JSON body`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            
            assertNotNull(response.body)
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            assertNotNull(healthResponse)
        }
        
        @Test
        fun `Given healthy storage When getting health Then should include status field`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertEquals("healthy", healthResponse.status)
        }
        
        @Test
        fun `Given healthy storage When getting health Then should include timestamp field`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertNotNull(healthResponse.timestamp)
            assertTrue(healthResponse.timestamp.isNotEmpty())
        }
        
        @Test
        fun `Given healthy storage When getting health Then should include region field`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertNotNull(healthResponse.region)
        }
        
        @Test
        fun `Given healthy storage When getting health Then should include storage health`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertNotNull(healthResponse.storage)
            assertEquals(bucketName, healthResponse.storage.bucket)
            assertEquals("ok", healthResponse.storage.connectivity)
        }
    }
    
    @Nested
    inner class StorageConnectivityChecking {
        
        @Test
        fun `Given accessible storage When checking connectivity Then should return healthy status`() {
            every { mockStorage.list() } returns flowOf("test-key")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertEquals("healthy", healthResponse.status)
            assertEquals("ok", healthResponse.storage.connectivity)
        }
        
        @Test
        fun `Given inaccessible storage When checking connectivity Then should return degraded status`() {
            every { mockStorage.list() } throws RuntimeException("S3 connection failed")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertEquals("degraded", healthResponse.status)
            assertEquals("error", healthResponse.storage.connectivity)
        }
        
        @Test
        fun `Given storage throws exception When checking connectivity Then should still return 200 OK`() {
            every { mockStorage.list() } throws RuntimeException("S3 connection failed")
            
            val response = useCase()
            
            assertEquals(HttpStatus.OK, response.statusCode)
        }
        
        @Test
        fun `Given storage throws exception When checking connectivity Then should include bucket name`() {
            every { mockStorage.list() } throws RuntimeException("S3 connection failed")
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, RuntimeHealthResponse::class.java)
            
            assertEquals(bucketName, healthResponse.storage.bucket)
        }
    }
}
