package nl.vintik.mocknest.infra.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import nl.vintik.mocknest.application.generation.util.SafeUrlResolver
import nl.vintik.mocknest.application.generation.util.UrlResolutionException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Property 2: Preservation - SSRF Protection for Safe and Internal URLs
 *
 * This test validates that the UNFIXED code correctly:
 * 1. Allows fetching from safe external URLs
 * 2. Rejects URLs targeting internal/private network addresses
 *
 * IMPORTANT: This test runs on UNFIXED code (before DNS pinning implementation).
 * EXPECTED OUTCOME: All tests PASS (confirms baseline SSRF protection to preserve).
 *
 * These tests capture the observed SSRF protection behavior that must be preserved
 * when implementing the DNS pinning fix for Bug 1 (SSRF/DNS rebinding vulnerability).
 */
class SsrfProtectionPreservationPropertyTest {

    /**
     * Property 2a: Safe External URLs - Successful Fetch
     *
     * For any URL targeting a legitimate external address,
     * the system SHALL successfully validate the URL.
     *
     * Note: We test validation only, not actual fetching, since we don't want
     * to depend on external services in unit tests. Some URLs may fail DNS resolution
     * in test environments, but we verify they are not rejected for SSRF reasons.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://example.com/service.wsdl",
            "https://www.w3.org/TR/wsdl",
            "https://schemas.xmlsoap.org/wsdl/",
            "https://raw.githubusercontent.com/user/repo/main/service.wsdl",
            "https://gist.githubusercontent.com/user/id/raw/service.wsdl",
            "http://webservices.amazon.com/AWSECommerceService/AWSECommerceService.wsdl",
            "https://api.stripe.com/v1/service.wsdl"
        ]
    )
    fun `Given safe external URL When validating THEN should pass validation`(url: String) = runTest {
        // Given: A safe external URL
        logger.info { "Testing safe external URL validation: $url" }

        // When: Validating the URL
        val result = runCatching {
            // This will validate the URL but may fail on actual fetch
            // We only care that SSRF validation passes
            SafeUrlResolver.validateAndResolve(url)
        }

        // Then: Validation should either succeed OR fail with DNS resolution error (not SSRF rejection)
        // We verify it's NOT rejected for SSRF reasons (unsafe address)
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            val message = exception?.message ?: ""
            // If it fails, it should NOT be due to "unsafe address" (SSRF protection)
            // It may fail due to DNS resolution issues in test environment
            assertFalse(
                message.contains("unsafe address", ignoreCase = true),
                "URL should not be rejected for SSRF reasons (unsafe address): $url. Error: $message"
            )
        }
        // If it succeeds, that's perfect - SSRF validation passed
    }

    /**
     * Property 2b: Internal/Private Network URLs - Rejection
     *
     * For any URL targeting internal/private network addresses,
     * the system SHALL reject the URL with UrlResolutionException.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            // Loopback addresses
            "http://localhost/service.wsdl",
            "http://127.0.0.1/service.wsdl",
            "http://127.0.0.2/service.wsdl",
            "http://[::1]/service.wsdl",
            // Private IPv4 ranges (RFC 1918)
            "http://10.0.0.1/service.wsdl",
            "http://10.255.255.255/service.wsdl",
            "http://172.16.0.1/service.wsdl",
            "http://172.31.255.255/service.wsdl",
            "http://192.168.0.1/service.wsdl",
            "http://192.168.255.255/service.wsdl",
            // Link-local addresses
            "http://169.254.1.1/service.wsdl",
            "http://[fe80::1]/service.wsdl",
            // CGNAT range (RFC 6598)
            "http://100.64.0.1/service.wsdl",
            "http://100.127.255.255/service.wsdl"
        ]
    )
    fun `Given internal or private network URL When validating THEN should reject with UrlResolutionException`(
        url: String
    ) = runTest {
        // Given: An internal/private network URL
        logger.info { "Testing internal/private URL rejection: $url" }

        // When/Then: Validation should fail with UrlResolutionException
        val exception = assertFailsWith<UrlResolutionException>(
            message = "Internal/private URL should be rejected: $url"
        ) {
            SafeUrlResolver.validateAndResolve(url)
        }

        // Verify the exception message indicates unsafe address
        assertContains(
            exception.message ?: "",
            "unsafe address",
            ignoreCase = true,
            message = "Exception should indicate unsafe address"
        )
    }

    /**
     * Property 2c: Multicast Addresses - Rejection
     *
     * For any URL targeting multicast addresses,
     * the system SHALL reject the URL with UrlResolutionException.
     */
    @Test
    fun `Given multicast address URL When validating THEN should reject with UrlResolutionException`() = runTest {
        // Given: A multicast address URL
        val url = "http://224.0.0.1/service.wsdl"
        logger.info { "Testing multicast address rejection: $url" }

        // When/Then: Validation should fail with UrlResolutionException
        val exception = assertFailsWith<UrlResolutionException>(
            message = "Multicast address should be rejected: $url"
        ) {
            SafeUrlResolver.validateAndResolve(url)
        }

        // Verify the exception message indicates unsafe address
        assertContains(
            exception.message ?: "",
            "unsafe address",
            ignoreCase = true,
            message = "Exception should indicate unsafe address"
        )
    }

    /**
     * Property 2d: IPv6 ULA (Unique Local Address) - Rejection
     *
     * For any URL targeting IPv6 ULA addresses (fc00::/7),
     * the system SHALL reject the URL with UrlResolutionException.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://[fc00::1]/service.wsdl",
            "http://[fd00::1]/service.wsdl",
            "http://[fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]/service.wsdl"
        ]
    )
    fun `Given IPv6 ULA address When validating THEN should reject with UrlResolutionException`(
        url: String
    ) = runTest {
        // Given: An IPv6 ULA address URL
        logger.info { "Testing IPv6 ULA rejection: $url" }

        // When/Then: Validation should fail with UrlResolutionException
        val exception = assertFailsWith<UrlResolutionException>(
            message = "IPv6 ULA address should be rejected: $url"
        ) {
            SafeUrlResolver.validateAndResolve(url)
        }

        // Verify the exception message indicates unsafe address
        assertContains(
            exception.message ?: "",
            "unsafe address",
            ignoreCase = true,
            message = "Exception should indicate unsafe address"
        )
    }

    /**
     * Property 2e: Invalid URL Schemes - Rejection
     *
     * For any URL with unsupported schemes (not http/https),
     * the system SHALL reject the URL with UrlResolutionException.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "ftp://example.com/service.wsdl",
            "file:///etc/passwd",
            "gopher://example.com/service",
            "javascript:alert(1)"
        ]
    )
    fun `Given unsupported URL scheme When validating THEN should reject with UrlResolutionException`(
        url: String
    ) = runTest {
        // Given: A URL with unsupported scheme
        logger.info { "Testing unsupported scheme rejection: $url" }

        // When/Then: Validation should fail with UrlResolutionException
        val exception = assertFailsWith<UrlResolutionException>(
            message = "Unsupported scheme should be rejected: $url"
        ) {
            SafeUrlResolver.validateAndResolve(url)
        }

        // Verify the exception message indicates unsupported scheme or invalid URL
        val message = exception.message ?: ""
        assertTrue(
            message.contains("scheme", ignoreCase = true) || message.contains("Invalid URL", ignoreCase = true),
            "Exception should mention unsupported scheme or invalid URL. Got: $message"
        )
    }

    /**
     * Property 2f: Malformed URLs - Rejection
     *
     * For any malformed URL,
     * the system SHALL reject the URL with UrlResolutionException.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "not-a-url",
            "http://",
            "://example.com",
            "http:example.com",
            ""
        ]
    )
    fun `Given malformed URL When validating THEN should reject with UrlResolutionException`(
        url: String
    ) = runTest {
        // Given: A malformed URL
        logger.info { "Testing malformed URL rejection: $url" }

        // When/Then: Validation should fail with UrlResolutionException
        val exception = assertFailsWith<UrlResolutionException>(
            message = "Malformed URL should be rejected: $url"
        ) {
            SafeUrlResolver.validateAndResolve(url)
        }

        // Verify the exception was thrown (message content may vary)
        assertTrue(
            exception.message?.isNotEmpty() == true,
            "Exception should have a message"
        )
    }
}
