package nl.vintik.mocknest.application.runtime.config

import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class MockNestConfigTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Should create required beans`() {
        val config = MockNestConfig()
        assertNotNull(config.directCallHttpServerFactory())
    }
}
