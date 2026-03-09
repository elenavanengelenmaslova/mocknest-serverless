package nl.vintik.mocknest.infra.aws.generation.health

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AIHealthUseCaseTest {
    
    private val mockModelConfig: ModelConfiguration = mockk(relaxed = true)
    private val mockPrefixResolver: InferencePrefixResolver = mockk(relaxed = true)
    private val inferenceMode = "AUTO"
    
    private lateinit var useCase: AIHealthUseCase
    
    @BeforeEach
    fun setup() {
        useCase = AIHealthUseCase(mockModelConfig, mockPrefixResolver, inferenceMode)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Nested
    inner class AIHealthEndpointResponseStructure {
        
        @Test
        fun `Given AI features configured When getting health Then should return 200 OK`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            
            assertEquals(HttpStatus.OK, response.statusCode)
        }
        
        @Test
        fun `Given AI features configured When getting health Then should return JSON content type`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            
            assertNotNull(response.headers)
            assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        }
        
        @Test
        fun `Given AI features configured When getting health Then should return valid JSON body`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            
            assertNotNull(response.body)
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            assertNotNull(healthResponse)
        }
        
        @Test
        fun `Given AI features configured When getting health Then should include status field`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertEquals("healthy", healthResponse.status)
        }
        
        @Test
        fun `Given AI features configured When getting health Then should include timestamp field`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertNotNull(healthResponse.timestamp)
            assertTrue(healthResponse.timestamp.isNotEmpty())
        }
        
        @Test
        fun `Given AI features configured When getting health Then should include region field`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertNotNull(healthResponse.region)
        }
        
        @Test
        fun `Given AI features configured When getting health Then should include AI health details`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertNotNull(healthResponse.ai)
            assertEquals("AmazonNovaPro", healthResponse.ai.modelName)
            assertEquals("eu", healthResponse.ai.inferencePrefix)
            assertEquals("AUTO", healthResponse.ai.inferenceMode)
            assertNull(healthResponse.ai.lastInvocationSuccess)
            assertTrue(healthResponse.ai.officiallySupported)
        }
    }
    
    @Nested
    inner class OfficiallySupportedModelIndication {
        
        @Test
        fun `Given AmazonNovaPro model When getting health Then should indicate officially supported`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertTrue(healthResponse.ai.officiallySupported)
        }
        
        @Test
        fun `Given non-Nova model When getting health Then should indicate not officially supported`() {
            every { mockModelConfig.getModelName() } returns "AnthropicClaude35SonnetV2"
            every { mockModelConfig.getConfiguredPrefix() } returns "global"
            every { mockModelConfig.isOfficiallySupported() } returns false
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertFalse(healthResponse.ai.officiallySupported)
        }
    }
    
    @Nested
    inner class InferencePrefixReporting {
        
        @Test
        fun `Given geo prefix configured When getting health Then should report geo prefix`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertEquals("eu", healthResponse.ai.inferencePrefix)
        }
        
        @Test
        fun `Given global prefix configured When getting health Then should report global prefix`() {
            every { mockModelConfig.getModelName() } returns "AnthropicClaude35SonnetV2"
            every { mockModelConfig.getConfiguredPrefix() } returns "global"
            every { mockModelConfig.isOfficiallySupported() } returns false
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertEquals("global", healthResponse.ai.inferencePrefix)
        }
        
        @Test
        fun `Given no prefix configured When getting health Then should report null prefix`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns null
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertNull(healthResponse.ai.inferencePrefix)
        }
    }
    
    @Nested
    inner class InferenceModeReporting {
        
        @Test
        fun `Given AUTO inference mode When getting health Then should report AUTO mode`() {
            every { mockModelConfig.getModelName() } returns "AmazonNovaPro"
            every { mockModelConfig.getConfiguredPrefix() } returns "eu"
            every { mockModelConfig.isOfficiallySupported() } returns true
            
            val response = useCase()
            val healthResponse = mapper.readValue(response.body, AIHealthResponse::class.java)
            
            assertEquals("AUTO", healthResponse.ai.inferenceMode)
        }
    }
}
