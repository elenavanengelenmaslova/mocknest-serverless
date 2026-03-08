package nl.vintik.mocknest.infra.aws.generation.ai.config

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class BedrockConfigurationTest {

    @Test
    fun `Should create BedrockRuntimeClient`() {
        val config = BedrockConfiguration()
        val client = config.bedrockRuntimeClient("us-east-1", null)
        assertNotNull(client)
        client.close()
    }
}
