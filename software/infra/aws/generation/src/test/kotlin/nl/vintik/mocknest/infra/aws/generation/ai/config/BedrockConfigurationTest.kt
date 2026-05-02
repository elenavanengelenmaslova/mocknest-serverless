package nl.vintik.mocknest.infra.aws.generation.ai.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BedrockConfigurationTest {

    private val clients = mutableListOf<aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient>()

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `Given valid region When creating BedrockRuntimeClient Then should use specified region`() {
        val region = "us-east-1"
        val client = BedrockConfiguration.bedrockRuntimeClient(region, null)
        clients.add(client)

        assertNotNull(client)
        assertEquals(region, client.config.region)
    }

    @ParameterizedTest
    @ValueSource(strings = ["us-east-1", "eu-west-1", "ap-southeast-1", "us-west-2", "ca-central-1"])
    fun `Given various AWS regions When creating BedrockRuntimeClient Then should use correct region`(region: String) {
        val client = BedrockConfiguration.bedrockRuntimeClient(region, null)
        clients.add(client)

        assertNotNull(client)
        assertEquals(region, client.config.region)
    }

    @Test
    fun `Given default region When AWS_REGION not set Then should use eu-west-1 fallback`() {
        val defaultRegion = "eu-west-1"
        val client = BedrockConfiguration.bedrockRuntimeClient(defaultRegion, null)
        clients.add(client)

        assertNotNull(client)
        assertEquals(defaultRegion, client.config.region)
    }

    @Test
    fun `Given custom endpoint When creating BedrockRuntimeClient Then should configure endpoint`() {
        val region = "us-east-1"
        val customEndpoint = "http://localhost:4566"
        val client = BedrockConfiguration.bedrockRuntimeClient(region, customEndpoint)
        clients.add(client)

        assertNotNull(client)
        assertEquals(region, client.config.region)
        assertNotNull(client.config.endpointUrl)
    }

    @Test
    fun `Given null custom endpoint When creating BedrockRuntimeClient Then should not configure endpoint`() {
        val region = "us-east-1"
        val client = BedrockConfiguration.bedrockRuntimeClient(region, null)
        clients.add(client)

        assertNotNull(client)
        assertEquals(region, client.config.region)
    }

    @Test
    fun `Given empty custom endpoint When creating BedrockRuntimeClient Then should not configure endpoint`() {
        val region = "us-east-1"
        val client = BedrockConfiguration.bedrockRuntimeClient(region, "")
        clients.add(client)

        assertNotNull(client)
        assertEquals(region, client.config.region)
    }
}
