package nl.vintik.mocknest.application.runtime.config

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.mappings.ObjectStorageMappingsSource
import nl.vintik.mocknest.application.runtime.extensions.DeleteAllMappingsAndFilesFilter
import nl.vintik.mocknest.application.runtime.extensions.NormalizeMappingBodyFilter
import nl.vintik.mocknest.application.runtime.store.adapters.ObjectStorageBlobStore
import nl.vintik.mocknest.application.runtime.store.adapters.ObjectStorageWireMockStores
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import com.github.tomakehurst.wiremock.store.BlobStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.PropertySource

private val logger = KotlinLogging.logger {}

@Configuration
@PropertySource("classpath:application.properties")
class MockNestConfig {

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun wiremockFilesBlobStore(storage: ObjectStorageInterface): BlobStore = ObjectStorageBlobStore(storage)

    @Bean
    fun wireMockServer(
        directCallHttpServerFactory: DirectCallHttpServerFactory,
        storage: ObjectStorageInterface,
    ): WireMockServer {
        val config = wireMockConfig()
            .notifier(ConsoleNotifier(true))
            .httpServerFactory(directCallHttpServerFactory)
            .withStores(ObjectStorageWireMockStores(storage))
            .extensions(NormalizeMappingBodyFilter(storage), DeleteAllMappingsAndFilesFilter(storage))
            // Use S3-only storage - no classpath or filesystem fallback
            .mappingSource(ObjectStorageMappingsSource(storage))


        val server = WireMockServer(config)
        server.start()
        logger.info { "MockNest server started with S3-only storage and custom Stores for FILES and MAPPINGS" }
        return server
    }

    @Bean
    @DependsOn("wireMockServer")
    fun directCallHttpServer(directCallHttpServerFactory: DirectCallHttpServerFactory): DirectCallHttpServer {
        return directCallHttpServerFactory.httpServer
    }
}