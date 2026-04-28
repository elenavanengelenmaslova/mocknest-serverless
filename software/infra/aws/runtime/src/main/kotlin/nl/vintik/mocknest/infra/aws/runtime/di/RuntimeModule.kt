package nl.vintik.mocknest.infra.aws.runtime.di

import aws.sdk.kotlin.services.sqs.SqsClient
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import com.github.tomakehurst.wiremock.store.BlobStore
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.config.createWireMockServer
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore
import nl.vintik.mocknest.application.runtime.store.adapters.ObjectStorageBlobStore
import nl.vintik.mocknest.application.runtime.usecases.AdminRequestUseCase
import nl.vintik.mocknest.application.runtime.usecases.ClientRequestUseCase
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.infra.aws.runtime.health.AwsRuntimeHealthUseCase
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import nl.vintik.mocknest.infra.aws.runtime.storage.S3ObjectStorageAdapter
import nl.vintik.mocknest.infra.aws.runtime.webhook.SqsWebhookPublisher
import nl.vintik.mocknest.infra.aws.runtime.webhook.WebhookHttpClient
import org.koin.dsl.module

/**
 * Koin module for the Runtime Lambda handler.
 *
 * Provides all beans previously defined in Spring @Configuration classes
 * for the "runtime" profile. S3Client comes from [coreModule] — not redeclared here.
 *
 * Defined as a function (not a global val) because `module {}` preallocates
 * factories — functions create fresh instances when needed.
 */
fun runtimeModule() = module {

    // SQS client for async webhook dispatch
    single { SqsClient { region = System.getenv("AWS_DEFAULT_REGION") ?: "eu-west-1" } }

    // S3-backed object storage (S3Client comes from coreModule)
    single<ObjectStorageInterface> {
        S3ObjectStorageAdapter(
            bucketName = System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "",
            s3Client = get(),
        )
    }

    // Webhook configuration and clients
    single { WebhookConfig.fromEnv() }
    single<WebhookHttpClientInterface> { WebhookHttpClient(get()) }
    single<SqsPublisherInterface> { SqsWebhookPublisher(get()) }

    // WireMock server and supporting components
    single { DirectCallHttpServerFactory() }
    single<BlobStore> { ObjectStorageBlobStore(get()) }
    single { RedactSensitiveHeadersFilter(get()) }
    single { S3RequestJournalStore(get(), get(), get()) }
    single {
        createWireMockServer(
            get(),
            get(),
            get(),
            get(),
            System.getenv("MOCKNEST_WEBHOOK_QUEUE_URL") ?: "",
            get(),
            get(),
        )
    }
    // DirectCallHttpServer — must resolve WireMockServer first to ensure server.start() has been called
    single {
        get<com.github.tomakehurst.wiremock.WireMockServer>() // trigger WireMock startup
        get<DirectCallHttpServerFactory>().httpServer
    }

    // Health check
    single<GetRuntimeHealth> {
        AwsRuntimeHealthUseCase(get(), System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "")
    }

    // Use cases
    single<HandleAdminRequest> { AdminRequestUseCase(get()) }
    single<HandleClientRequest> { ClientRequestUseCase(get()) }

    // SnapStart priming hook
    single {
        RuntimePrimingHook(get(), get(), System.getenv("MOCKNEST_S3_BUCKET_NAME") ?: "", get(), get(), get())
    }

    // CRaC lifecycle — reloads WireMock mappings from S3 after SnapStart restore
    single { RuntimeMappingReloadHook(get()) }
}
