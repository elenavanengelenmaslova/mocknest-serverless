package nl.vintik.mocknest.application.wiremock.mappings

import nl.vintik.mocknest.application.interfaces.storage.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.standalone.MappingsSource
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * WireMock MappingsSource backed by ObjectStorageInterface.
 *
 * - Keys are stored under the [prefix] (default "mappings/") as JSON text of StubMapping
 * - Loads all mappings on startup using prefix-filtered listing and streaming concurrent reads
 */
class ObjectStorageMappingsSource(
    private val storage: ObjectStorageInterface,
    private val prefix: String = "mappings/",
    private val concurrency: Int = 32,
) : MappingsSource {

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadMappingsInto(stubMappings: StubMappings) {
        logger.info { "Loading objects ObjectStorageMappingsSource: $stubMappings" }
        // WireMock expects blocking; stream internally with bounded concurrency and block until done
        runBlocking {
            val keysFlow = storage.runCatching { listPrefix(prefix) }
                .onFailure { e -> logger.error(e) { "Failed to list mappings with prefix '$prefix'" } }
                .getOrThrow()

            var total = 0
            var loaded = 0
            keysFlow
                .flatMapMerge(concurrency) { key ->
                    total++
                    flow {
                        val json = storage.runCatching { get(key) }
                            .onFailure { e -> logger.error(e) { "Failed to get mapping '$key'" } }
                            .getOrNull()
                        if (!json.isNullOrBlank()) emit(key to json)
                    }
                }
                .collect { (key, json) ->
                    runCatching { Json.read(json, StubMapping::class.java) }
                        .mapCatching { mapping ->
                            mapping.isPersistent = false
                            stubMappings.addMapping(mapping)
                            loaded++
                        }.onFailure { e ->
                            logger.error(e) { "Skipping mapping: $key: $e" }
                        }
                }

            logger.info { "Finished loading $loaded/$total mappings from storage." }
        }
    }

    // Persist mappings directly to object storage under the configured prefix
    override fun save(mapping: StubMapping) {
        val id = mapping.id
        val key = "$prefix$id.json"
        runBlocking {
            storage.runCatching {
                save(key, Json.write(mapping))
            }.onFailure { e ->
                logger.error(e) { "Failed to save mapping $id to $key" }
            }
        }
    }

    override fun save(mappings: List<StubMapping>) {
        mappings.forEach { save(it) }
    }

    override fun remove(mapping: StubMapping) {
        val id = mapping.id
        val key = "$prefix$id.json"
        runBlocking {
            storage.runCatching { delete(key) }
                .onFailure { e -> logger.error(e) { "Failed to delete mapping $id at $key" } }
        }
    }

    override fun removeAll() {
        runBlocking {
            val flow = storage.runCatching { listPrefix(prefix) }
                .onFailure { e -> logger.error(e) { "Failed to list mappings for removeAll with prefix '$prefix'" } }
                .getOrThrow()
            flow.collect { key ->
                storage.runCatching { delete(key) }
                    .onFailure { e -> logger.error(e) { "Failed to delete mapping key $key" } }
            }
        }
    }
}