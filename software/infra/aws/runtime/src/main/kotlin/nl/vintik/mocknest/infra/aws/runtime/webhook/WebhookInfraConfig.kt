package nl.vintik.mocknest.infra.aws.runtime.webhook

import aws.sdk.kotlin.services.sqs.SqsClient
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface

/**
 * Webhook infrastructure configuration.
 * 
 * Previously a Spring @Configuration class with @Bean methods.
 * Now a plain utility class — bean definitions have moved to Koin modules
 * (runtimeModule and asyncModule).
 */
object WebhookInfraConfig {

    fun webhookConfig(): WebhookConfig = WebhookConfig.fromEnv()

    fun webhookHttpClient(webhookConfig: WebhookConfig): WebhookHttpClientInterface =
        WebhookHttpClient(webhookConfig)

    fun sqsWebhookPublisher(
        region: String = System.getenv("AWS_DEFAULT_REGION") ?: "eu-west-1",
    ): SqsPublisherInterface = SqsWebhookPublisher(
        SqsClient { this.region = region }
    )
}
