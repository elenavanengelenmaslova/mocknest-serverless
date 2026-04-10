package nl.vintik.mocknest.infra.aws.runtime.webhook

import aws.sdk.kotlin.services.sqs.SqsClient
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function

@Configuration
class WebhookInfraConfig {

    @Bean
    fun webhookConfig(): WebhookConfig = WebhookConfig.fromEnv()

    @Bean
    fun webhookHttpClient(webhookConfig: WebhookConfig): WebhookHttpClientInterface =
        WebhookHttpClient(webhookConfig)

    @Bean
    fun sqsWebhookPublisher(
        @Value("\${AWS_DEFAULT_REGION:eu-west-1}") region: String,
    ): SqsPublisherInterface = SqsWebhookPublisher(
        SqsClient { this.region = region }
    )

    @Bean
    fun runtimeAsyncRouter(handler: RuntimeAsyncHandler): Function<SQSEvent, Unit> =
        Function { event -> handler.handle(event) }
}
