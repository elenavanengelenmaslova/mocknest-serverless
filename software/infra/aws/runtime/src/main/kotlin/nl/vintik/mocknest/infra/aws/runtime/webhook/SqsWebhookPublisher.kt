package nl.vintik.mocknest.infra.aws.runtime.webhook

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface

private val logger = KotlinLogging.logger {}

/**
 * AWS SQS implementation of [SqsPublisherInterface].
 * Uses the Kotlin AWS SDK v2 [SqsClient] to publish messages.
 */
class SqsWebhookPublisher(
    private val sqsClient: SqsClient,
) : SqsPublisherInterface {

    override suspend fun publish(queueUrl: String, messageBody: String) {
        sqsClient.runCatching {
            sendMessage(
                SendMessageRequest {
                    this.queueUrl = queueUrl
                    this.messageBody = messageBody
                }
            )
        }.onFailure { e ->
            logger.warn(e) { "Failed to publish message to SQS queue: $queueUrl" }
            throw e
        }.onSuccess {
            logger.info { "Published message to SQS queue: $queueUrl messageId=${it.messageId}" }
        }
    }
}
