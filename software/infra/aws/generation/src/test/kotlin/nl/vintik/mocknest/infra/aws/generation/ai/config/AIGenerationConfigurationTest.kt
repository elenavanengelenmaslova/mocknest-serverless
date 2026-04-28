package nl.vintik.mocknest.infra.aws.generation.ai.config

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class AIGenerationConfigurationTest {

    @Test
    fun `Should create all beans`() {
        assertNotNull(AIGenerationConfiguration.openApiSpecificationParser())
        assertNotNull(AIGenerationConfiguration.compositeSpecificationParser(emptyList()))
        assertNotNull(AIGenerationConfiguration.bedrockServiceAdapter(mockk(), mockk(), mockk()))
        assertNotNull(AIGenerationConfiguration.mockGenerationAgent(1, mockk(), mockk(), mockk(), mockk()))

        val agent = mockk<nl.vintik.mocknest.application.generation.agent.MockGenerationFunctionalAgent>()
        val useCase = AIGenerationConfiguration.generateMocksFromSpecWithDescriptionUseCase(agent)
        assertNotNull(useCase)
        assertNotNull(AIGenerationConfiguration.aiGenerationRequestUseCase(useCase))
    }
}
