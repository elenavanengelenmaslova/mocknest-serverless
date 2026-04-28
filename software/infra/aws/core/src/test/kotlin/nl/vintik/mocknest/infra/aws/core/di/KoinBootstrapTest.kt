package nl.vintik.mocknest.infra.aws.core.di

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.koin.core.context.stopKoin
import kotlin.test.assertNotNull

class KoinBootstrapTest {

    companion object {
        @AfterAll
        @JvmStatic
        fun tearDown() {
            stopKoin()
            KoinBootstrap.reset()
        }
    }

    @Test
    fun `Given KoinBootstrap When init called twice Then does not throw`() {
        assertDoesNotThrow {
            KoinBootstrap.init(listOf(coreModule()))
            KoinBootstrap.init(listOf(coreModule()))
        }
    }

    @Test
    fun `Given KoinBootstrap initialized When getKoin called Then returns valid Koin instance`() {
        KoinBootstrap.init(listOf(coreModule()))

        val koin = assertDoesNotThrow { KoinBootstrap.getKoin() }
        assertNotNull(koin)
    }
}
