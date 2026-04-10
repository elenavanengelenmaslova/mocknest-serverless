package nl.vintik.mocknest.application.runtime.config

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
import org.springframework.context.annotation.PropertySource

private val logger = KotlinLogging.logger {}

@Configuration
@PropertySource("classpath:application.properties")
class MockNestConfig {

    @Bean
    fun webhookConfig(): WebhookConfig = WebhookConfig.fromEnv()

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun wiremockFilesBlobStore(storage: ObjectStorageInterface): BlobStore = ObjectStorageBlobStore(storage)

    @Bean
    fun wireMockServer(
        directCallHttpServerFactory: DirectCallHttpServerFactory,
        storage: ObjectStorageInterface,
        webhookConfig: WebhookConfig,
        sqsPublisher: SqsPublisherInterface,
        @Value("\${MOCKNEST_WEBHOOK_QUEUE_URL:}") webhookQueueUrl: String,
    ): WireMockServer {
        val redactFilter = RedactSensitiveHeadersFilter(webhookConfig)
        val journalStore = S3RequestJournalStore(storage, webhookConfig, redactFilter)
        val asyncPublisher = WebhookAsyncEventPublisher(sqsPublisher, webhookQueueUrl)

        val config = wireMockConfig()
            .notifier(ConsoleNotifier(true))
            .httpServerFactory(directCallHttpServerFactory)
            .withStores(ObjectStorageWireMockStores(storage, journalStore))
            .extensions(
                NormalizeMappingBodyFilter(storage),
                DeleteAllMappingsAndFilesFilter(storage),
                asyncPublisher,
                redactFilter,
            )
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
