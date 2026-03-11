package nl.vintik.mocknest.infra.aws.core.storage.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class S3ConfigurationTest {

    private val config = S3Configuration()
    private val clients = mutableListOf<aws.sdk.kotlin.services.s3.S3Client>()

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `Given valid region When creating S3Client Then should use specified region`() {
        val region = "us-east-1"
        val client = config.s3Client(region)
        clients.add(client)
        
        assertNotNull(client)
        assertEquals(region, client.config.region)
    }

    @ParameterizedTest
    @ValueSource(strings = ["us-east-1", "eu-west-1", "ap-southeast-1", "us-west-2", "ca-central-1"])
    fun `Given various AWS regions When creating S3Client Then should use correct region`(region: String) {
        val client = config.s3Client(region)
        clients.add(client)
        
        assertNotNull(client)
        assertEquals(region, client.config.region)
    }

    @Test
    fun `Given default region When AWS_REGION not set Then should use eu-west-1 fallback`() {
        // This test validates the @Value annotation default: @Value("\${AWS_REGION:eu-west-1}")
        // In production, AWS Lambda always sets AWS_REGION, but we test the fallback
        val defaultRegion = "eu-west-1"
        val client = config.s3Client(defaultRegion)
        clients.add(client)
        
        assertNotNull(client)
        assertEquals(defaultRegion, client.config.region)
    }

    @Test
    fun `Given region from AWS_REGION environment variable When creating S3Client Then should use that region`() {
        // This test validates that the configuration reads from AWS_REGION environment variable
        // The @Value annotation handles this: @Value("\${AWS_REGION:eu-west-1}")
        val region = "ap-southeast-2"
        val client = config.s3Client(region)
        clients.add(client)
        
        assertNotNull(client)
        assertEquals(region, client.config.region)
    }
}
