package nl.vintik.mocknest.application.runtime.config

import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import io.mockk.clearAllMocks
import io.mockk.mockk
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import nl.vintik.mocknest.application.runtime.extensions.SqsPublisherInterface
import nl.vintik.mocknest.application.runtime.journal.S3RequestJournalStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockNestConfigTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Should create WireMock server via createWireMockServer`() {
        val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
        val webhookConfig = WebhookConfig(
            sensitiveHeaders = setOf("x-api-key"),
            webhookTimeoutMs = 10_000L,
            asyncTimeoutMs = 30_000L,
            requestJournalPrefix = "requests/",
        )
        val factory = DirectCallHttpServerFactory()
        val redactFilter = RedactSensitiveHeadersFilter(webhookConfig)
        val journalStore = S3RequestJournalStore(mockStorage, webhookConfig, redactFilter)
        val sqsPublisher: SqsPublisherInterface = mockk(relaxed = true)

        val server = createWireMockServer(factory, mockStorage, webhookConfig, sqsPublisher, "", journalStore, redactFilter)
        try {
            assertNotNull(server)
            assertTrue(server.isRunning)
        } finally {
            server.stop()
        }
    }
}
