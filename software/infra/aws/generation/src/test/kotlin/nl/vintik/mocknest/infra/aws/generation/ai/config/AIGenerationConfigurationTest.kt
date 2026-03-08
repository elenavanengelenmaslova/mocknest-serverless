package nl.vintik.mocknest.infra.aws.generation.ai.config

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class AIGenerationConfigurationTest {

    private val config = AIGenerationConfiguration()

    @Test
    fun `Should create all beans`() {
        assertNotNull(config.openApiSpecificationParser())
        assertNotNull(config.compositeSpecificationParser(emptyList()))
        assertNotNull(config.bedrockServiceAdapter(mockk(), mockk(), mockk()))
        assertNotNull(config.mockGenerationAgent(1, mockk(), mockk(), mockk(), mockk()))
        
        val agent = mockk<nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent>()
        val useCase = config.generateMocksFromSpecWithDescriptionUseCase(agent)
        assertNotNull(useCase)
        assertNotNull(config.aiGenerationRequestUseCase(useCase))
    }
}
