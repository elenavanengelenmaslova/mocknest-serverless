package nl.vintik.mocknest.infra.aws.runtime.snapstart

import com.github.tomakehurst.wiremock.WireMockServer
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.crac.Context
import org.crac.Core
import org.crac.Resource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RuntimeMappingReloadHook].
 *
 * Verifies CRaC lifecycle behavior: `afterRestore()` reloads WireMock mappings,
 * `beforeCheckpoint()` is a no-op, and the hook registers with the CRaC global context.
 *
 * **Validates: Requirements 2.1, 2.2**
 */
class RuntimeMappingReloadHookTest {

    private val wireMockServer: WireMockServer = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `Given a restored snapshot When afterRestore is called Then wireMockServer resetToDefaultMappings is called exactly once`() {
        // Given
        val hook = createHookWithoutRegistration()
        val context: Context<out Resource> = mockk(relaxed = true)

        // When
        hook.afterRestore(context)

        // Then
        verify(exactly = 1) { wireMockServer.resetToDefaultMappings() }
    }

    @Test
    fun `Given a checkpoint is about to be created When beforeCheckpoint is called Then no methods are called on wireMockServer`() {
        // Given
        val hook = createHookWithoutRegistration()
        val context: Context<out Resource> = mockk(relaxed = true)

        // When
        hook.beforeCheckpoint(context)

        // Then
        verify(exactly = 0) { wireMockServer.resetToDefaultMappings() }
        verify(exactly = 0) { wireMockServer.resetMappings() }
        verify(exactly = 0) { wireMockServer.stop() }
        verify(exactly = 0) { wireMockServer.start() }
        verify(exactly = 0) { wireMockServer.stubFor(any()) }
    }

    @Test
    fun `Given a new RuntimeMappingReloadHook When register is called Then it registers with CRaC global context`() {
        // Given
        val mockContext: org.crac.Context<Resource> = mockk(relaxed = true)
        mockkStatic(Core::class)
        io.mockk.every { Core.getGlobalContext() } returns mockContext

        // When
        val hook = RuntimeMappingReloadHook(wireMockServer)
        hook.register()

        // Then
        verify(exactly = 1) { mockContext.register(hook) }
    }

    /**
     * Creates a [RuntimeMappingReloadHook] without triggering CRaC registration,
     * so tests can focus on `afterRestore` and `beforeCheckpoint` behavior in isolation.
     */
    private fun createHookWithoutRegistration(): RuntimeMappingReloadHook {
        val mockContext: org.crac.Context<Resource> = mockk(relaxed = true)
        mockkStatic(Core::class)
        io.mockk.every { Core.getGlobalContext() } returns mockContext
        return RuntimeMappingReloadHook(wireMockServer)
    }
}
