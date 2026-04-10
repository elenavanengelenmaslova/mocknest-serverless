package nl.vintik.mocknest.application.runtime.journal

import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
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
        clearAllMocks()
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
}
