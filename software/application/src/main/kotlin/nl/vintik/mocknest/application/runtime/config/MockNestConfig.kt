package nl.vintik.mocknest.application.runtime.config

import com.github.tomakehurst.wiremock.extension.Extension
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.DeleteAllMappingsAndFilesFilter
import nl.vintik.mocknest.application.runtime.extensions.NormalizeMappingBodyFilter
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore
import nl.vintik.mocknest.application.runtime.extensions.WebhookAsyncEventPublisher
import nl.vintik.mocknest.application.runtime.mappings.ObjectStorageMappingsSource
import nl.vintik.mocknest.application.runtime.store.adapters.ObjectStorageBlobStore
import nl.vintik.mocknest.application.runtime.store.adapters.ObjectStorageWireMockStores
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import com.github.tomakehurst.wiremock.store.BlobStore
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Creates and starts a WireMock server with the given dependencies.
 *
 * Replaces the previous Spring @Configuration class. All dependencies
 * are now passed as parameters and wired via Koin modules.
 */
fun createWireMockServer(
    directCallHttpServerFactory: DirectCallHttpServerFactory,
    storage: ObjectStorageInterface,
    webhookConfig: WebhookConfig,
    sqsPublisher: SqsPublisherInterface,
    webhookQueueUrl: String,
    journalStore: S3RequestJournalStore,
    redactFilter: RedactSensitiveHeadersFilter,
): WireMockServer {

    val extensions = mutableListOf<Extension>(
        NormalizeMappingBodyFilter(storage),
        DeleteAllMappingsAndFilesFilter(storage),
        redactFilter,
    )

    if (webhookQueueUrl.isBlank()) {
        logger.warn { "MOCKNEST_WEBHOOK_QUEUE_URL is blank — WebhookAsyncEventPublisher will NOT be registered; webhook events will be skipped" }
    } else {
        extensions.add(2, WebhookAsyncEventPublisher(sqsPublisher, webhookQueueUrl))
    }

    val config = wireMockConfig()
        .notifier(ConsoleNotifier(true))
        .httpServerFactory(directCallHttpServerFactory)
        .withStores(ObjectStorageWireMockStores(storage, journalStore))
        .extensions(*extensions.toTypedArray())
        // Use S3-only storage - no classpath or filesystem fallback
        .mappingSource(ObjectStorageMappingsSource(storage))

    val server = WireMockServer(config)
    server.start()
    logger.info { "MockNest server started with S3-only storage, async webhook dispatch, and S3 request journal" }
    return server
}
