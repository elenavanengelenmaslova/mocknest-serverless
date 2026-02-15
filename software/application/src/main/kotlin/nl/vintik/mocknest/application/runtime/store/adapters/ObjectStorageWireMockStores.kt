package nl.vintik.mocknest.application.runtime.store.adapters

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import com.github.tomakehurst.wiremock.store.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * A Stores implementation that wires ObjectStorageInterface directly for FILES and MAPPINGS.
 *
 * - FILES: BlobStore implemented directly over ObjectStorageInterface (Base64 + "__files/" prefix)
 * - MAPPINGS: StubMappingStore implemented directly over ObjectStorageInterface (JSON + "mappings/" prefix)
 * - All other stores delegate to WireMock's in-memory implementations by returning null.
 */
private val logger = KotlinLogging.logger {}
class ObjectStorageWireMockStores(
    storage: ObjectStorageInterface,
) : Stores {

    private val filesBlobStore: BlobStore = ObjectStorageBlobStore(storage)
    private val stubStore: StubMappingStore = InMemoryStubMappingStore()
    private val settingsStore: SettingsStore = InMemorySettingsStore()

    private val requestJournalStore: RequestJournalStore = InMemoryRequestJournalStore()
    private val scenariosStore: ScenariosStore = InMemoryScenariosStore()
    private val recorderStateStore: RecorderStateStore = InMemoryRecorderStateStore()

    // Object stores by name (in-memory)
    private val objectStores = ConcurrentHashMap<String, ObjectStore>()

    override fun getStubStore(): StubMappingStore = stubStore
    override fun getRequestJournalStore(): RequestJournalStore = requestJournalStore
    override fun getSettingsStore(): SettingsStore = settingsStore
    override fun getScenariosStore(): ScenariosStore = scenariosStore
    override fun getRecorderStateStore(): RecorderStateStore = recorderStateStore

    override fun getFilesBlobStore(): BlobStore = filesBlobStore
    override fun getBlobStore(name: String): BlobStore = filesBlobStore

    override fun getObjectStore(
        name: String?,
        persistenceTypeHint: Stores.PersistenceType?,
        maxSize: Int,
    ): ObjectStore {
        val key = name ?: "default"
        return objectStores.computeIfAbsent(key) { InMemoryObjectStore(maxSize) }
    }

    override fun start() {}
    override fun stop() {}
}