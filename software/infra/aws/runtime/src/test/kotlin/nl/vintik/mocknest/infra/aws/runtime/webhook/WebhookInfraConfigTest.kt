package nl.vintik.mocknest.infra.aws.runtime.webhook

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebhookInfraConfigTest {

    private val config = WebhookInfraConfig()

    @Nested
    inner class BeanCreation {

        @Test
        fun `Given default environment When creating webhookConfig Then should return valid config`() {
            val webhookConfig = config.webhookConfig()

            assertNotNull(webhookConfig)
        }

        @Test
        fun `Given webhookConfig When creating webhookHttpClient Then should return valid client`() {
            val webhookConfig = WebhookConfig.fromEnv()
            val httpClient = config.webhookHttpClient(webhookConfig)

            assertNotNull(httpClient)
            assertTrue(httpClient is WebhookHttpClient)
        }

        @Test
        fun `Given region When creating sqsWebhookPublisher Then should return valid publisher`() {
            val publisher = config.sqsWebhookPublisher("eu-west-1")

            assertNotNull(publisher)
            assertTrue(publisher is SqsWebhookPublisher)
        }
    }
}
