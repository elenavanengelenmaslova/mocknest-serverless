package nl.vintik.mocknest.infra.aws.generation.health

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.interfaces.AIModelServiceInterface
import nl.vintik.mocknest.domain.generation.AIHealth
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AwsAIHealthUseCaseTest {

    private val mockAIModelService: AIModelServiceInterface = mockk(relaxed = true)
    private val useCase = AwsAIHealthUseCase(mockAIModelService, "AUTO")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given AI model service When checking health Then should return healthy status`() {
        // Given
        every { mockAIModelService.getModelName() } returns "AmazonNovaPro"
        every { mockAIModelService.getConfiguredPrefix() } returns "eu"

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertNotNull(response.body)

        val health = mapper.readValue(response.body, AIHealth::class.java)
        assertEquals("healthy", health.status)
        assertNotNull(health.version) // Version should be present but don't assert specific value
        assertEquals("AmazonNovaPro", health.ai.modelName)
        assertEquals("eu", health.ai.inferencePrefix)
        assertEquals("AUTO", health.ai.inferenceMode)
    }

    @Test
    fun `Given AWS_REGION environment variable When checking health Then should include region`() {
        // Given
        every { mockAIModelService.getModelName() } returns "AmazonNovaPro"
        every { mockAIModelService.getConfiguredPrefix() } returns null

        // When
        val response = useCase.invoke()

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val health = mapper.readValue(response.body, AIHealth::class.java)
        assertNotNull(health.region) // Should be "unknown" or actual AWS region
    }
}