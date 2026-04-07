package nl.vintik.mocknest.infra.aws.runtime.webhook

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebhookInfraConfig {

    @Bean
    fun webhookHttpClient(webhookConfig: WebhookConfig): WebhookHttpClientInterface =
        WebhookHttpClient(webhookConfig)
}
