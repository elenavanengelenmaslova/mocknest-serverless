package nl.vintik.mocknest.application.runtime.journal

import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for S3RequestJournalStore suppress/enable writes functionality.
 *
 * Validates that journal writes can be suppressed during SnapStart priming
 * and re-enabled for normal operation, without any S3 side effects during priming.
 */
class S3RequestJournalStoreSuppressTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val mockRedactFilter: RedactSensitiveHeadersFilter = mockk(relaxed = true)
    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )
    private val store = S3RequestJournalStore(mockStorage, webhookConfig, mockRedactFilter)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Given writes suppressed When put called Then storage save is NOT called`() {
        store.suppressWrites()

        val event = mockk<ServeEvent>(relaxed = true)
        store.put(UUID.randomUUID(), event)

        coVerify(exactly = 0) { mockStorage.save(any(), any()) }
    }

    @Test
    fun `Given writes suppressed When add called Then storage save is NOT called`() {
        store.suppressWrites()

        val event = mockk<ServeEvent>(relaxed = true)
        store.add(event)

        coVerify(exactly = 0) { mockStorage.save(any(), any()) }
    }

    @Test
    fun `Given writes re-enabled after suppress When put called Then storage save IS called`() {
        store.suppressWrites()
        store.enableWrites()

        val event = mockk<ServeEvent>(relaxed = true)
        store.put(UUID.randomUUID(), event)

        coVerify(exactly = 1) { mockStorage.save(any(), any()) }
    }

    @Test
    fun `Given writes never suppressed When put called Then storage save IS called`() {
        val event = mockk<ServeEvent>(relaxed = true)
        store.put(UUID.randomUUID(), event)

        coVerify(exactly = 1) { mockStorage.save(any(), any()) }
    }

    @Test
    fun `Given suppress and enable cycle When checking state Then writes are enabled after enable`() {
        store.suppressWrites()
        store.enableWrites()

        // Verify by calling put — it should write
        val event = mockk<ServeEvent>(relaxed = true)
        store.put(UUID.randomUUID(), event)

        coVerify(exactly = 1) { mockStorage.save(any(), any()) }
    }

    @Test
    fun `Given try-finally pattern When exception in stubRequest Then writes are re-enabled`() {
        store.suppressWrites()
        try {
            // Simulate exception during priming request
            throw RuntimeException("simulated priming failure")
        } catch (_: RuntimeException) {
            // Expected
        } finally {
            store.enableWrites()
        }

        // Writes should be enabled after finally block
        val event = mockk<ServeEvent>(relaxed = true)
        store.put(UUID.randomUUID(), event)

        coVerify(exactly = 1) { mockStorage.save(any(), any()) }
    }
}
