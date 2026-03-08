package nl.vintik.mocknest.application.runtime.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MockNestConfigTest {

    @Test
    fun `Should have correct defaults`() {
        val config = MockNestConfig()
        assertEquals("mocknest", config.rootDir)
        assertNotNull(config.directCallHttpServerFactory())
    }
}
