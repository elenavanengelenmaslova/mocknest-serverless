package nl.vintik.mocknest.infra.aws.runtime.webhook

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest
import aws.sdk.kotlin.services.sqs.model.SendMessageResponse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqsWebhookPublisherTest {

    private val mockSqsClient: SqsClient = mockk(relaxed = true)
    private val publisher = SqsWebhookPublisher(mockSqsClient)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given valid queue URL and message When publish called Then SQS sendMessage is invoked with correct parameters`() = runTest {
        val queueUrl = "https://sqs.eu-west-1.amazonaws.com/123456789/test-queue"
        val messageBody = """{"actionType":"webhook","url":"https://example.com"}"""
        val requestSlot = slot<SendMessageRequest>()

        coEvery { mockSqsClient.sendMessage(capture(requestSlot)) } returns SendMessageResponse {
            messageId = "msg-123"
        }

        publisher.publish(queueUrl, messageBody)

        coVerify { mockSqsClient.sendMessage(any()) }
        assertEquals(queueUrl, requestSlot.captured.queueUrl)
        assertEquals(messageBody, requestSlot.captured.messageBody)
    }

    @Test
    fun `Given SQS client throws When publish called Then exception is logged and rethrown`() = runTest {
        val queueUrl = "https://sqs.eu-west-1.amazonaws.com/123456789/test-queue"
        coEvery { mockSqsClient.sendMessage(any<SendMessageRequest>()) } throws RuntimeException("SQS unavailable")

        assertFailsWith<RuntimeException> {
            publisher.publish(queueUrl, "test-message")
        }
    }
}
