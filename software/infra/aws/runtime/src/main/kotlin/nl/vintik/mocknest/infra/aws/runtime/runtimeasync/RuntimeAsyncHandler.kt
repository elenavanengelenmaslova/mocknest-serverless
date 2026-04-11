package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.DefaultAwsSigner
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.url.Url
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.AsyncEvent
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Lambda handler for async webhook dispatch.
 *
 * Reads [AsyncEvent] records from SQS and executes the outbound HTTP call directly
 * from the event payload. Does NOT load WireMock mappings or resolve templates.
 *
 * Supports two auth modes:
 * - `none`: forwards static headers as-is
 * - `aws_iam`: signs the outbound request using SigV4 with the Lambda execution role
 *
 * Requirements: 2.4–2.8, 3.1–3.5
 */
@Component
class RuntimeAsyncHandler(
    private val webhookHttpClient: WebhookHttpClientInterface,
    private val webhookConfig: WebhookConfig,
    @Value("\${AWS_DEFAULT_REGION:eu-west-1}") private val defaultRegion: String,
) {

    fun handle(sqsEvent: SQSEvent) {
        for (record in sqsEvent.records) {
            handleRecord(record)
        }
    }

    private fun handleRecord(record: SQSEvent.SQSMessage) {
        // Block 1: JSON parsing — catch SerializationException only (poison-pill skip, no rethrow)
        val event = runCatching {
            Json.decodeFromString(AsyncEvent.serializer(), record.body)
        }.getOrElse { e ->
            if (e is SerializationException) {
                logger.error(e) { "Malformed JSON in SQS record messageId=${record.messageId} — skipping (poison-pill)" }
                return
            }
            throw e
        }

        // Block 2: HTTP dispatch — do NOT catch; let network/5xx exceptions propagate so SQS retries
        when (event.actionType) {
            "webhook" -> dispatchWebhook(event)
            else -> logger.warn { "Unknown actionType '${event.actionType}' in AsyncEvent — skipping" }
        }
    }

    private fun dispatchWebhook(event: AsyncEvent) {
        val outboundHeaders = when (event.auth.type) {
            "aws_iam" -> signRequest(event)
            else -> event.headers
        }

        val request = WebhookRequest(
            url = event.url,
            method = event.method,
            headers = outboundHeaders,
            body = event.body,
        )

        when (val result = webhookHttpClient.send(request)) {
            is WebhookResult.Success ->
                logger.info { "Webhook delivered: url=${event.url} method=${event.method} status=${result.statusCode}" }
            is WebhookResult.Failure ->
                logger.warn { "Webhook failed: url=${event.url} method=${event.method} status=${result.statusCode} message=${result.message}" }
        }
    }

    /**
     * Signs the outbound request using AWS SigV4 with the Lambda execution role credentials.
     * Returns the original headers merged with the SigV4 Authorization, X-Amz-Date, and
     * X-Amz-Security-Token headers.
     *
     * Credentials and signing material are NEVER logged.
     */
    private fun signRequest(event: AsyncEvent): Map<String, String> = runBlocking {
        runCatching {
            val region = event.auth.region ?: defaultRegion
            val service = event.auth.service ?: deriveServiceFromUrl(event.url)

            val credentialsProvider = DefaultChainCredentialsProvider()
            val credentials = credentialsProvider.resolve()

            val bodyBytes = event.body?.toByteArray() ?: ByteArray(0)

            val builder = HttpRequestBuilder()
            builder.method = HttpMethod.parse(event.method)
            builder.url(Url.parse(event.url))
            event.headers.forEach { (k, v) -> builder.header(k, v) }
            builder.body = if (bodyBytes.isNotEmpty()) HttpBody.fromBytes(bodyBytes) else HttpBody.Empty
            val httpRequest = builder.build()

            val signingConfig = AwsSigningConfig {
                this.region = region
                this.service = service
                this.credentials = credentials
            }

            val result = DefaultAwsSigner.sign(httpRequest, signingConfig)
            val signedRequest = result.output

            // Merge original headers with signing headers (Authorization, X-Amz-Date, X-Amz-Security-Token)
            val merged = event.headers.toMutableMap()
            signedRequest.headers.forEach { key, values ->
                merged[key] = values.firstOrNull() ?: ""
            }
            merged
        }.getOrElse { e ->
            logger.warn(e) { "SigV4 signing failed for url=${event.url} — falling back to unsigned request" }
            event.headers
        }
    }

    private fun deriveServiceFromUrl(url: String): String {
        // Derive service from URL pattern: e.g. execute-api from *.execute-api.*.amazonaws.com
        return when {
            url.contains(".execute-api.") -> "execute-api"
            url.contains(".lambda.") -> "lambda"
            url.contains(".s3.") -> "s3"
            else -> "execute-api"
        }
    }
}
