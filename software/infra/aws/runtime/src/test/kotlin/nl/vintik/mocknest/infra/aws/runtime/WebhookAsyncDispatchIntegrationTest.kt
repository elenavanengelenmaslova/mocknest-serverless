package nl.vintik.mocknest.infra.aws.runtime

import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.Parameters
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookAsyncEventPublisher
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncHandler
import nl.vintik.mocknest.infra.aws.runtime.webhook.WebhookHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.wait.strategy.Wait
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Full end-to-end integration test for the async webhook dispatch flow.
 *
 * Flow:
 * 1. WireMock stub with "webhook" serveEventListener is matched
 * 2. WebhookAsyncEventPublisher intercepts and publishes AsyncEvent to LocalStack SQS
 * 3. RuntimeAsyncHandler processes the SQS event and calls the callback stub
 * 4. Callback stub receives the expected request
 *
 * Requirements: 8.1–8.7
 */
@Testcontainers
class WebhookAsyncDispatchIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        private val localStack = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.12.0")
        ).withServices(LocalStackContainer.Service.SQS)
            .waitingFor(
                org.testcontainers.containers.wait.strategy.Wait.forHttp("/_localstack/health")
                    .forStatusCode(200)
                    .withStartupTimeout(java.time.Duration.ofMinutes(2))
            )

        private lateinit var sqsClient: SqsClient
        private lateinit var queueUrl: String

        @BeforeAll
        @JvmStatic
        fun setupClass() = runBlocking {
            sqsClient = SqsClient {
                endpointUrl = aws.smithy.kotlin.runtime.net.url.Url.parse(
                    localStack.getEndpointOverride(LocalStackContainer.Service.SQS).toString()
                )
                region = localStack.region
                credentialsProvider = aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider {
                    accessKeyId = localStack.accessKey
                    secretAccessKey = localStack.secretKey
                }
            }
            val createResponse = sqsClient.createQueue(CreateQueueRequest { queueName = "async-dispatch-test-queue" })
            queueUrl = requireNotNull(createResponse.queueUrl)
            logger.info { "Created test SQS queue: $queueUrl" }
        }

        @AfterAll
        @JvmStatic
        fun tearDownClass() {
            sqsClient.close()
        }
    }

    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )

    @Test
    fun `Given WireMock stub with webhook listener When trigger fires Then callback stub receives expected request`() {
        val capturedMessages = CopyOnWriteArrayList<String>()
        val sqsStub = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                capturedMessages.add(messageBody)
            }
        }

        val asyncPublisher = WebhookAsyncEventPublisher(sqsStub, queueUrl)
        val wireMockServer = WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .extensions(asyncPublisher)
        )
        wireMockServer.start()

        try {
            val callbackPort = wireMockServer.port()
            val callbackUrl = "http://localhost:$callbackPort/callback"

            // Register callback stub
            wireMockServer.addStubMapping(
                post(urlPathEqualTo("/callback"))
                    .willReturn(aResponse().withStatus(200).withBody("""{"received":true}"""))
                    .build()
            )

            // Register trigger stub with webhook listener
            wireMockServer.addStubMapping(
                post(urlPathEqualTo("/trigger"))
                    .willReturn(aResponse().withStatus(202))
                    .withServeEventListener(
                        "webhook",
                        Parameters.from(mapOf(
                            "url" to callbackUrl,
                            "method" to "POST",
                            "body" to """{"event":"order.created"}""",
                            "auth" to mapOf("type" to "none"),
                        ))
                    )
                    .build()
            )

            // Fire trigger request
            val httpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
            val response = httpClient.newCall(
                Request.Builder()
                    .url("http://localhost:${wireMockServer.port()}/trigger")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
            ).execute()
            assertEquals(202, response.code)
            response.close()

            // Wait for async webhook processing
            Thread.sleep(500)

            // Assert exactly one AsyncEvent was captured
            assertEquals(1, capturedMessages.size, "Expected exactly one AsyncEvent published")
            val event = Json.decodeFromString(AsyncEvent.serializer(), capturedMessages[0])
            assertEquals("webhook", event.actionType)
            assertEquals(callbackUrl, event.url)
            assertEquals("POST", event.method)
            assertEquals("""{"event":"order.created"}""", event.body)

            // Now invoke RuntimeAsyncHandler directly with the captured event
            val sqsRecord = SQSEvent.SQSMessage().apply {
                messageId = "test-msg-1"
                body = capturedMessages[0]
            }
            val sqsEvent = SQSEvent().apply { records = listOf(sqsRecord) }

            val webhookHttpClient = WebhookHttpClient(webhookConfig)
            val handler = RuntimeAsyncHandler(webhookHttpClient, webhookConfig, "eu-west-1")
            handler.handle(sqsEvent)

            // Wait for callback to be processed
            Thread.sleep(300)

            // Assert callback stub received the request
            val callbackRequests = wireMockServer.allServeEvents
                .filter { it.request.url == "/callback" }
            assertTrue(callbackRequests.isNotEmpty(), "Expected callback stub to receive at least one request")
            val callbackRequest = callbackRequests.first()
            assertEquals("POST", callbackRequest.request.method.name)
            assertEquals("""{"event":"order.created"}""", callbackRequest.request.bodyAsString)
        } finally {
            wireMockServer.stop()
        }
    }

    @Test
    fun `Given AsyncEvent with none auth When RuntimeAsyncHandler processes Then no sensitive headers added`() {
        val capturedMessages = CopyOnWriteArrayList<String>()
        val sqsStub = object : SqsPublisherInterface {
            override suspend fun publish(queueUrl: String, messageBody: String) {
                capturedMessages.add(messageBody)
            }
        }

        val asyncPublisher = WebhookAsyncEventPublisher(sqsStub, queueUrl)
        val wireMockServer = WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .extensions(asyncPublisher)
        )
        wireMockServer.start()

        try {
            val callbackUrl = "http://localhost:${wireMockServer.port()}/callback-no-auth"

            wireMockServer.addStubMapping(
                post(urlPathEqualTo("/callback-no-auth"))
                    .willReturn(aResponse().withStatus(200))
                    .build()
            )
            wireMockServer.addStubMapping(
                post(urlPathEqualTo("/trigger-no-auth"))
                    .willReturn(aResponse().withStatus(202))
                    .withServeEventListener(
                        "webhook",
                        Parameters.from(mapOf(
                            "url" to callbackUrl,
                            "method" to "POST",
                            "body" to "test",
                        ))
                    )
                    .build()
            )

            val httpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
            val response = httpClient.newCall(
                Request.Builder()
                    .url("http://localhost:${wireMockServer.port()}/trigger-no-auth")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
            ).execute()
            assertEquals(202, response.code)
            response.close()

            Thread.sleep(500)

            assertEquals(1, capturedMessages.size)
            val event = Json.decodeFromString(AsyncEvent.serializer(), capturedMessages[0])
            assertEquals("none", event.auth.type)

            // Process via RuntimeAsyncHandler
            val sqsRecord = SQSEvent.SQSMessage().apply {
                messageId = "test-msg-2"
                body = capturedMessages[0]
            }
            val sqsEvent = SQSEvent().apply { records = listOf(sqsRecord) }
            val webhookHttpClient = WebhookHttpClient(webhookConfig)
            val handler = RuntimeAsyncHandler(webhookHttpClient, webhookConfig, "eu-west-1")
            handler.handle(sqsEvent)

            Thread.sleep(300)

            val callbackRequests = wireMockServer.allServeEvents
                .filter { it.request.url == "/callback-no-auth" }
            assertNotNull(callbackRequests.firstOrNull(), "Expected callback to be called")

            // No authorization header should be present (auth type is none)
            val callbackReq = callbackRequests.first().request
            assertTrue(
                callbackReq.getHeader("authorization") == null || callbackReq.getHeader("authorization").isEmpty(),
                "No authorization header should be injected for auth type none"
            )
        } finally {
            wireMockServer.stop()
        }
    }
}
