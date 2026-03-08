package nl.vintik.mocknest.application.runtime.store.adapters

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class ObjectStorageWireMockStoresTest {

    @Test
    fun `Should return all required stores`() {
        val storage = mockk<ObjectStorageInterface>()
        val stores = ObjectStorageWireMockStores(storage)
        
        assertNotNull(stores.getStubStore())
        assertNotNull(stores.getRequestJournalStore())
        assertNotNull(stores.getSettingsStore())
        assertNotNull(stores.getScenariosStore())
        assertNotNull(stores.getRecorderStateStore())
        assertNotNull(stores.getFilesBlobStore())
        assertNotNull(stores.getBlobStore("test"))
        assertNotNull(stores.getObjectStore("test", null, 100))
    }
}
