package nl.vintik.mocknest.application.generation.usecases

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AIHealthUseCaseTest {

    private val mockAIModelService: AIModelServiceInterface = mockk(relaxed = true)
    private val useCase = AIHealthUseCase(mockAIModelService, "us-east-1", "bedrock")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given AI model service When invoking health check Then should return healthy HTTP response`() = runTest {
        // Given
        every { mockAIModelService.getModelName() } returns "amazon.nova-pro-v1:0"
        every { mockAIModelService.getConfiguredPrefix() } returns "nova-pro"

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertNotNull(response.body)
        assertTrue(response.body?.contains("\"status\":\"healthy\"") == true)
        assertTrue(response.body?.contains("\"region\":\"us-east-1\"") == true)
        assertTrue(response.body?.contains("\"modelName\":\"amazon.nova-pro-v1:0\"") == true)
        assertTrue(response.body?.contains("\"inferencePrefix\":\"nova-pro\"") == true)
        assertTrue(response.body?.contains("\"inferenceMode\":\"BEDROCK\"") == true)
    }

    @Test
    fun `Given AI model service with null prefix When invoking health check Then should handle null prefix`() = runTest {
        // Given
        every { mockAIModelService.getModelName() } returns "amazon.nova-micro-v1:0"
        every { mockAIModelService.getConfiguredPrefix() } returns null

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"modelName\":\"amazon.nova-micro-v1:0\"") == true)
        assertTrue(response.body?.contains("\"inferencePrefix\":null") == true)
        assertTrue(response.body?.contains("\"inferenceMode\":\"BEDROCK\"") == true)
    }

    @Test
    fun `Given different inference mode When invoking health check Then should uppercase inference mode`() = runTest {
        // Given
        val useCase = AIHealthUseCase(mockAIModelService, "eu-west-1", "custom")
        every { mockAIModelService.getModelName() } returns "test-model"
        every { mockAIModelService.getConfiguredPrefix() } returns "test"

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("\"region\":\"eu-west-1\"") == true)
        assertTrue(response.body?.contains("\"inferenceMode\":\"CUSTOM\"") == true)
    }

    @Test
    fun `Given AI health check When invoking Then should include timestamp and version`() = runTest {
        // Given
        every { mockAIModelService.getModelName() } returns "test-model"
        every { mockAIModelService.getConfiguredPrefix() } returns "test"

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
    fun `Given different regions When invoking health check Then should return correct region`() = runTest {
        // Given
        val useCase1 = AIHealthUseCase(mockAIModelService, "ap-southeast-1", "bedrock")
        val useCase2 = AIHealthUseCase(mockAIModelService, "ca-central-1", "bedrock")
        every { mockAIModelService.getModelName() } returns "test-model"
        every { mockAIModelService.getConfiguredPrefix() } returns "test"

        // When
        val response1 = useCase1.invoke()
        val response2 = useCase2.invoke()

        // Then
        assertTrue(response1.body?.contains("\"region\":\"ap-southeast-1\"") == true)
        assertTrue(response2.body?.contains("\"region\":\"ca-central-1\"") == true)
    }
}