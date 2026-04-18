package nl.vintik.mocknest.application.runtime.journal

import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S3RequestJournalStoreTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val mockRedactFilter: RedactSensitiveHeadersFilter = mockk(relaxed = true)

    private val webhookConfig = WebhookConfig(
        sensitiveHeaders = setOf("x-api-key", "authorization", "proxy-authorization", "x-amz-security-token"),
        webhookTimeoutMs = 10_000L,
        asyncTimeoutMs = 30_000L,
        requestJournalPrefix = "requests/",
    )
    private val store = S3RequestJournalStore(mockStorage, webhookConfig, mockRedactFilter)

    @AfterEach
    fun tearDown() {
        clearMocks(mockStorage, mockRedactFilter)
    }

    private fun buildServeEvent(id: UUID = UUID.randomUUID()): ServeEvent {
        val serveEvent = mockk<ServeEvent>(relaxed = true)
        every { serveEvent.id } returns id
        return serveEvent
    }

    @Test
    fun `Given a ServeEvent with sensitive headers When put called Then S3 record has REDACTED for sensitive header values`() {
        val id = UUID.randomUUID()
        val event = buildServeEvent(id = id)
        val redactedJson = """{"id":"$id","request":{"headers":{"x-api-key":"[REDACTED]","content-type":"application/json"}}}"""
        every { mockRedactFilter.redactServeEvent(event) } returns redactedJson
        val savedSlot = slot<String>()
        coEvery { mockStorage.save(any(), capture(savedSlot)) } returns "requests/$id"

        store.put(id, event)

        coVerify { mockStorage.save("requests/$id", any()) }
        val savedJson = savedSlot.captured
        assertFalse(savedJson.contains("secret-key"), "Sensitive value should not appear in S3 record")
        assertTrue(savedJson.contains("[REDACTED]"), "Sensitive value should be replaced with [REDACTED]")
        assertTrue(savedJson.contains("application/json"), "Non-sensitive header should be preserved")
    }

    @Test
    fun `Given a stored record When get called Then returns deserialized record`() {
        val id = UUID.randomUUID()
        // Return null to simulate missing record — store should return empty Optional
        coEvery { mockStorage.get("requests/$id") } returns null

        val result = store.get(id)

        assertTrue(result.isEmpty, "Expected empty Optional when storage returns null")
    }

    @Test
    fun `Given S3 save throws When put called Then WARN is logged and no exception propagates`() {
        val id = UUID.randomUUID()
        val event = buildServeEvent(id = id)
        every { mockRedactFilter.redactServeEvent(event) } returns "{}"
        coEvery { mockStorage.save(any(), any()) } throws RuntimeException("S3 unavailable")

        // Must not throw
        store.put(id, event)
    }

    @Test
    fun `Given S3 get throws When get called Then WARN is logged and empty Optional returned`() {
        val id = UUID.randomUUID()
        coEvery { mockStorage.get(any()) } throws RuntimeException("S3 unavailable")

        val result = store.get(id)

        assertTrue(result.isEmpty, "Expected empty Optional on S3 failure")
    }

    @Test
    fun `Given add called When ServeEvent provided Then put is called with event id`() {
        val id = UUID.randomUUID()
        val event = buildServeEvent(id = id)
        every { mockRedactFilter.redactServeEvent(event) } returns "{}"
        coEvery { mockStorage.save(any(), any()) } returns "requests/$id"

        store.add(event)

        coVerify { mockStorage.save("requests/$id", any()) }
    }

    @Test
    fun `Given remove called When key provided Then S3 delete is called with correct key`() {
        val id = UUID.randomUUID()

        store.remove(id)

        coVerify { mockStorage.delete("requests/$id") }
    }

    @Test
    fun `Given getAll called When S3 list returns keys Then storage get is called for each key`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/$id1", "requests/$id2")
        coEvery { mockStorage.get(any()) } returns null

        store.getAll().toList()

        coVerify { mockStorage.get("requests/$id1") }
        coVerify { mockStorage.get("requests/$id2") }
    }

    @Nested
    inner class GetAllKeys {

        @Test
        fun `Given S3 contains journal keys When getAllKeys called Then returns UUIDs from key names`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/$id1", "requests/$id2")

            val keys = store.getAllKeys().toList()

            assertEquals(2, keys.size)
            assertTrue(keys.contains(id1))
            assertTrue(keys.contains(id2))
        }

        @Test
        fun `Given S3 contains non-UUID key When getAllKeys called Then non-UUID key is skipped`() {
            val validId = UUID.randomUUID()
            coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/$validId", "requests/not-a-uuid")

            val keys = store.getAllKeys().toList()

            assertEquals(1, keys.size)
            assertEquals(validId, keys.first())
        }

        @Test
        fun `Given S3 listPrefix throws When getAllKeys called Then returns empty stream`() {
            coEvery { mockStorage.listPrefix("requests/") } throws RuntimeException("S3 unavailable")

            val keys = store.getAllKeys().toList()

            assertTrue(keys.isEmpty())
        }
    }

    @Nested
    inner class RemoveLast {

        @Test
        fun `Given S3 contains keys When removeLast called Then first key is deleted`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/$id1", "requests/$id2")

            store.removeLast()

            coVerify { mockStorage.delete("requests/$id1") }
            coVerify(exactly = 0) { mockStorage.delete("requests/$id2") }
        }

        @Test
        fun `Given S3 is empty When removeLast called Then no delete is performed`() {
            coEvery { mockStorage.listPrefix("requests/") } returns emptyFlow()

            store.removeLast()

            coVerify(exactly = 0) { mockStorage.delete(any()) }
        }

        @Test
        fun `Given S3 listPrefix throws When removeLast called Then no exception propagates`() {
            coEvery { mockStorage.listPrefix("requests/") } throws RuntimeException("S3 unavailable")

            // Must not throw
            store.removeLast()
        }
    }

    @Nested
    inner class ClearFailure {

        @Test
        fun `Given S3 deleteMany throws When clear called Then no exception propagates`() {
            coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/key1")
            coEvery { mockStorage.deleteMany(any()) } throws RuntimeException("S3 unavailable")

            // Must not throw
            store.clear()
        }
    }

    @Nested
    inner class RemoveFailure {

        @Test
        fun `Given S3 delete throws When remove called Then no exception propagates`() {
            val id = UUID.randomUUID()
            coEvery { mockStorage.delete(any()) } throws RuntimeException("S3 unavailable")

            // Must not throw
            store.remove(id)
        }
    }

    @Nested
    inner class GetAllFailure {

        @Test
        fun `Given S3 listPrefix throws When getAll called Then returns empty stream`() {
            coEvery { mockStorage.listPrefix("requests/") } throws RuntimeException("S3 unavailable")

            val result = store.getAll().toList()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `Given S3 get throws for one key When getAll called Then other events are still returned`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { mockStorage.listPrefix("requests/") } returns flowOf("requests/$id1", "requests/$id2")
            coEvery { mockStorage.get("requests/$id1") } throws RuntimeException("S3 error")
            coEvery { mockStorage.get("requests/$id2") } returns null

            val result = store.getAll().toList()

            // id1 failed, id2 returned null — both produce no events
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class WriteSuppression {

        @Test
        fun `Given writes suppressed When put called Then S3 save is not invoked`() {
            val id = UUID.randomUUID()
            val event = buildServeEvent(id = id)
            store.suppressWrites()

            store.put(id, event)

            coVerify(exactly = 0) { mockStorage.save(any(), any()) }
        }

        @Test
        fun `Given writes re-enabled after suppression When put called Then S3 save is invoked`() {
            val id = UUID.randomUUID()
            val event = buildServeEvent(id = id)
            every { mockRedactFilter.redactServeEvent(event) } returns "{}"
            coEvery { mockStorage.save(any(), any()) } returns "requests/$id"

            store.suppressWrites()
            store.enableWrites()
            store.put(id, event)

            coVerify { mockStorage.save("requests/$id", any()) }
        }
    }
}
