package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.stream.Stream

/**
 * Property-based tests for async processing equivalence (Property 5).
 *
 * Verifies that [RuntimeAsyncLambdaHandler.handleRequest] delegates to
 * [RuntimeAsyncHandler.handle] for all valid SQS events, matching the
 * behavior of the previous `runtimeAsyncRouter` bean.
 *
 * **Validates: Requirements 3.4, 7.3, 10.3**
 */
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncProcessingPropertyTest : KoinTest {

    private val mockRuntimeAsyncHandler: RuntimeAsyncHandler = mockk(relaxed = true)
    private val mockPrimingHook: RuntimeAsyncPrimingHook = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var handler: RuntimeAsyncLambdaHandler

    @BeforeAll
    fun setUp() {
        KoinBootstrap.init(listOf(module {
            single { mockRuntimeAsyncHandler }
            single { mockPrimingHook }
        }))
        handler = RuntimeAsyncLambdaHandler()
    }

    @AfterAll
    fun tearDownAll() {
        stopKoin()
        KoinBootstrap.reset()
    }

    @AfterEach
    fun tearDown() {
        clearMocks(mockRuntimeAsyncHandler)
    }

    @ParameterizedTest(name = "SQS event with {0} record(s) delegates to RuntimeAsyncHandler")
    @MethodSource("sqsEvents")
    fun `Given SQS event When handling Then delegates to RuntimeAsyncHandler handle`(
        description: String,
        event: SQSEvent
    ) {
        handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockRuntimeAsyncHandler.handle(event) }
    }

    @ParameterizedTest(name = "SQS event with {0} record(s) passes exact event reference")
    @MethodSource("sqsEvents")
    fun `Given SQS event When handling Then passes exact event object to handler`(
        description: String,
        event: SQSEvent
    ) {
        handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockRuntimeAsyncHandler.handle(refEq(event)) }
    }

    @ParameterizedTest(name = "SQS event with {0} record(s) calls handle exactly once")
    @MethodSource("sqsEvents")
    fun `Given SQS event When handling Then calls handle exactly once`(
        description: String,
        event: SQSEvent
    ) {
        handler.handleRequest(event, mockContext)

        verify(exactly = 1) { mockRuntimeAsyncHandler.handle(any()) }
    }

    companion object {
        @JvmStatic
        fun sqsEvents(): Stream<Arguments> = Stream.of(
            Arguments.of("1 webhook", createSqsEvent(listOf(
                createWebhookRecord("msg-1", "http://example.com/webhook", "POST")
            ))),
            Arguments.of("2 webhooks", createSqsEvent(listOf(
                createWebhookRecord("msg-1", "http://example.com/webhook1", "POST"),
                createWebhookRecord("msg-2", "http://example.com/webhook2", "PUT")
            ))),
            Arguments.of("3 mixed", createSqsEvent(listOf(
                createWebhookRecord("msg-1", "http://example.com/callback", "POST"),
                createWebhookRecord("msg-2", "http://example.com/notify", "POST"),
                createWebhookRecord("msg-3", "http://example.com/update", "PATCH")
            ))),
            Arguments.of("5 batch", createSqsEvent(
                (1..5).map { i ->
                    createWebhookRecord("msg-$i", "http://example.com/hook$i", "POST")
                }
            )),
            Arguments.of("10 large batch", createSqsEvent(
                (1..10).map { i ->
                    createWebhookRecord("msg-$i", "http://example.com/endpoint$i", if (i % 2 == 0) "POST" else "PUT")
                }
            )),
            Arguments.of("1 with body", createSqsEvent(listOf(
                createWebhookRecord("msg-body", "http://example.com/webhook", "POST", """{"data":"test"}""")
            ))),
            Arguments.of("1 aws_iam auth", createSqsEvent(listOf(
                createWebhookRecord("msg-iam", "http://example.com/secure", "POST", null, "aws_iam")
            ))),
            Arguments.of("0 empty", createSqsEvent(emptyList())),
            Arguments.of("1 with headers", createSqsEvent(listOf(
                createWebhookRecord(
                    "msg-headers", "http://example.com/webhook", "POST",
                    """{"event":"test"}""", "none",
                    mapOf("Content-Type" to "application/json", "X-Custom" to "value")
                )
            ))),
            Arguments.of("1 DELETE method", createSqsEvent(listOf(
                createWebhookRecord("msg-delete", "http://example.com/resource/123", "DELETE")
            )))
        )

        private fun createSqsEvent(records: List<SQSEvent.SQSMessage>): SQSEvent {
            val event = SQSEvent()
            event.records = records
            return event
        }

        private fun createWebhookRecord(
            messageId: String,
            url: String,
            method: String,
            body: String? = null,
            authType: String = "none",
            headers: Map<String, String> = emptyMap()
        ): SQSEvent.SQSMessage {
            val headersJson = headers.entries.joinToString(",") { (k, v) -> """"$k":"$v"""" }
            val bodyJson = if (body != null) """"body":${body.replace("\"", "\\\"")}""" else """"body":null"""
            val authJson = """{"type":"$authType"}"""

            val record = SQSEvent.SQSMessage()
            record.messageId = messageId
            record.body = """{"actionType":"webhook","url":"$url","method":"$method","headers":{$headersJson},$bodyJson,"auth":$authJson}"""
            return record
        }
    }
}
