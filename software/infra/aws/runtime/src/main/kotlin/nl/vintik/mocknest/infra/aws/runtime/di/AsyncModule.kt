package nl.vintik.mocknest.infra.aws.runtime.di

import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncHandler
import nl.vintik.mocknest.infra.aws.runtime.runtimeasync.RuntimeAsyncPrimingHook
import nl.vintik.mocknest.infra.aws.runtime.webhook.WebhookHttpClient
import org.koin.dsl.module

/**
 * Koin module for the RuntimeAsync Lambda handler.
 *
 * Provides webhook dispatch beans for the async SQS-triggered Lambda.
 * This is a lightweight module — no WireMock, no S3 storage, no admin API.
 *
 * Defined as a function (not a global val) because `module {}` preallocates
 * factories — functions create fresh instances when needed.
 */
fun asyncModule() = module {

    // Webhook configuration and HTTP client
    single { WebhookConfig.fromEnv() }
    single<WebhookHttpClientInterface> { WebhookHttpClient(get()) }

    // Async handler — dispatches webhook HTTP calls from SQS events
    single {
        RuntimeAsyncHandler(
            webhookHttpClient = get(),
            webhookConfig = get(),
            defaultRegion = System.getenv("AWS_DEFAULT_REGION") ?: "eu-west-1",
        )
    }

    // SnapStart priming hook
    single { RuntimeAsyncPrimingHook(get()) }
}
