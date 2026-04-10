package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nl.vintik.mocknest.application.runtime.extensions.WebhookHttpClientInterface
import nl.vintik.mocknest.application.runtime.extensions.WebhookRequest
import nl.vintik.mocknest.application.runtime.extensions.WebhookResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeAsyncPrimingHookTest {

    private val mockHttpClient: WebhookHttpClientInterface = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun hook(isSnapStart: Boolean = true) = object : RuntimeAsyncPrimingHook(mockHttpClient) {
        override fun isSnapStartEnvironment() = isSnapStart
    }

    @Test
    fun `Given SnapStart environment When onApplicationReady called Then prime executes and HTTP client is exercised`() {
        every { mockHttpClient.send(any()) } returns WebhookResult.Failure(null, "Connection refused")

        hook(isSnapStart = true).onApplicationReady()

        verify(exactly = 1) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given non-SnapStart environment When onApplicationReady called Then prime does NOT execute`() {
        hook(isSnapStart = false).onApplicationReady()

        verify(exactly = 0) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given HTTP client throws When prime called Then no exception propagates`() {
        every { mockHttpClient.send(any()) } throws RuntimeException("unexpected error")

        // Must not throw
        hook().prime()
    }

    @Test
    fun `Given HTTP client returns failure When prime called Then no exception propagates`() {
        every { mockHttpClient.send(any()) } returns WebhookResult.Failure(null, "Connection refused")

        hook().prime()

        verify(exactly = 1) { mockHttpClient.send(any()) }
    }

    @Test
    fun `Given prime called When HTTP client exercised Then request targets localhost noop URL`() {
        val capturedRequests = mutableListOf<WebhookRequest>()
        every { mockHttpClient.send(capture(capturedRequests)) } returns WebhookResult.Failure(null, "refused")

        hook().prime()

        assertTrue(capturedRequests.isNotEmpty())
        assertTrue(capturedRequests[0].url.contains("localhost"),
            "Priming must use a local unreachable URL, not a real external endpoint")
        assertFalse(capturedRequests[0].url.contains("amazonaws.com"),
            "Priming must not call real AWS endpoints")
    }
}
