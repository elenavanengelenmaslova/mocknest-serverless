package io.mocknest.application.wiremock.config

import io.mocknest.application.interfaces.storage.ObjectStorageInterface
import io.mocknest.application.wiremock.mappings.CompositeMappingsSource
import io.mocknest.application.wiremock.mappings.ObjectStorageMappingsSource
import io.mocknest.application.wiremock.extensions.DeleteAllMappingsAndFilesFilter
import io.mocknest.application.wiremock.extensions.NormalizeMappingBodyFilter
import io.mocknest.application.wiremock.store.adapters.ObjectStorageBlobStore
import io.mocknest.application.wiremock.store.adapters.ObjectStorageWireMockStores
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
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

private val logger = KotlinLogging.logger {}

@Configuration
@PropertySource("classpath:application.properties")
class MockNestConfig {

    @Value("\${mocknest.root-dir:mocknest}")
    internal val rootDir: String = "mocknest"

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun propertySourcesPlaceholderConfigurer() = PropertySourcesPlaceholderConfigurer()

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
            // Keep classpath root for any built-in defaults such as health endpoint
            .mappingSource(CompositeMappingsSource(ObjectStorageMappingsSource(storage), rootDir))


        val server = WireMockServer(config)
        server.start()
        logger.info { "MockNest server started with root dir: $rootDir and custom Stores for FILES and MAPPINGS" }
        return server
    }

    @Bean
    @DependsOn("wireMockServer")
    fun directCallHttpServer(directCallHttpServerFactory: DirectCallHttpServerFactory): DirectCallHttpServer {
        return directCallHttpServerFactory.httpServer
    }
}