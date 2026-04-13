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
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import com.github.tomakehurst.wiremock.store.BlobStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.PropertySource

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("!async")
@PropertySource("classpath:application.properties")
class MockNestConfig {

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun wiremockFilesBlobStore(storage: ObjectStorageInterface): BlobStore = ObjectStorageBlobStore(storage)

    @Bean
    fun redactSensitiveHeadersFilter(webhookConfig: WebhookConfig) = RedactSensitiveHeadersFilter(webhookConfig)

    @Bean
    fun s3RequestJournalStore(
        storage: ObjectStorageInterface,
        webhookConfig: WebhookConfig,
        redactFilter: RedactSensitiveHeadersFilter,
    ) = S3RequestJournalStore(storage, webhookConfig, redactFilter)

    @Bean
    fun wireMockServer(
        directCallHttpServerFactory: DirectCallHttpServerFactory,
        storage: ObjectStorageInterface,
        webhookConfig: WebhookConfig,
        sqsPublisher: SqsPublisherInterface,
        @Value("\${MOCKNEST_WEBHOOK_QUEUE_URL:}") webhookQueueUrl: String,
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

    @Bean
    @DependsOn("wireMockServer")
    fun directCallHttpServer(directCallHttpServerFactory: DirectCallHttpServerFactory): DirectCallHttpServer {
        return directCallHttpServerFactory.httpServer
    }
}
