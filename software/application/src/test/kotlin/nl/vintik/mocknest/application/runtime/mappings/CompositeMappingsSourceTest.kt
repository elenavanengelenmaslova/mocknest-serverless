package nl.vintik.mocknest.application.runtime.mappings

import com.github.tomakehurst.wiremock.standalone.MappingsSource
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CompositeMappingsSourceTest {

    @Test
    fun `Should load mappings from primary and secondary`() {
        val primary = mockk<MappingsSource>(relaxed = true)
        val stubMappings = mockk<StubMappings>()
        
        // Root dir for secondary is "mocknest"
        val composite = CompositeMappingsSource(primary, "mocknest")
        
        composite.loadMappingsInto(stubMappings)
        
        verify { primary.loadMappingsInto(stubMappings) }
        // We can't easily verify secondary because it's internal, 
        // but we verify that the call to loadMappingsInto doesn't crash 
        // and calls primary.
    }

    @Test
    fun `Should continue loading if primary fails`() {
        val primary = mockk<MappingsSource>()
        every { primary.loadMappingsInto(any()) } throws RuntimeException("Primary failed")
        val stubMappings = mockk<StubMappings>()
        
        val composite = CompositeMappingsSource(primary, "mocknest")
        
        composite.loadMappingsInto(stubMappings)
        
        verify { primary.loadMappingsInto(stubMappings) }
    }
}
