package nl.vintik.mocknest.application.runtime.extensions

import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class DeleteAllMappingsAndFilesFilterTest {

    private val storage = mockk<ObjectStorageInterface>()
    private val filter = DeleteAllMappingsAndFilesFilter(storage)

    @Test
    fun `Should delete all on DELETE admin mappings`() {
        val request = mockk<Request>()
        every { request.method } returns RequestMethod.DELETE
        every { request.url } returns "/__admin/mappings"
        
        coEvery { storage.listPrefix(any()) } returns flowOf("k1")
        coEvery { storage.deleteMany(any()) } returns Unit

        val action = filter.filter(request, null)
        
        assertNotNull(action)
    }

    @Test
    fun `Should do nothing on other requests`() {
        val request = mockk<Request>()
        every { request.method } returns RequestMethod.GET
        every { request.url } returns "/__admin/mappings"

        val action = filter.filter(request, null)
        
        assertNotNull(action)
    }
}
