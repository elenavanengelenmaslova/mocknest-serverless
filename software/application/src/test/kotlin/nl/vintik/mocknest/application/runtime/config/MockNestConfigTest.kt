package nl.vintik.mocknest.application.runtime.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MockNestConfigTest {

    @Test
    fun `Should create required beans`() {
        val config = MockNestConfig()
        assertNotNull(config.directCallHttpServerFactory())
    }
}
