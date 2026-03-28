package nl.vintik.mocknest.application.generation.util

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Interface for fetching content from a URL.
 * Implementations should enforce security checks and timeouts.
 */
interface UrlFetcher {
    fun fetch(url: String): String
}

/**
 * SSRF-safe URL resolver that validates URLs against internal/private network addresses
 * and fetches content with strict timeouts.
 */
class SafeUrlResolver(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000
) : UrlFetcher {

    override fun fetch(url: String): String {
        validateUrlSafety(url)
        return try {
            logger.info { "Fetching URL: $url" }
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: UrlResolutionException) {
            throw e
        } catch (e: Exception) {
            throw UrlResolutionException("Failed to fetch URL: $url", e)
        }
    }

    companion object {
        private val ALLOWED_SCHEMES = listOf("http", "https")

        /**
         * Checks if a string looks like an HTTP(S) URL.
         * Shared utility replacing duplicate private isHttpUrl methods in parsers.
         */
        fun isHttpUrl(content: String): Boolean {
            return runCatching {
                val uri = URI(content.trim())
                uri.scheme?.lowercase() in ALLOWED_SCHEMES && uri.host != null
            }.getOrDefault(false)
        }

        /**
         * Validates a URL is safe to fetch (no SSRF to internal networks).
         * Throws [UrlResolutionException] if the URL targets a forbidden address.
         */
        fun validateUrlSafety(url: String) {
            val uri = try {
                URI(url.trim())
            } catch (e: Exception) {
                throw UrlResolutionException("Invalid URL: $url", e)
            }

            val scheme = uri.scheme?.lowercase()
            if (scheme !in ALLOWED_SCHEMES) {
                throw UrlResolutionException("Unsupported URL scheme: $scheme (only HTTP and HTTPS are allowed)")
            }

            val host = uri.host
                ?: throw UrlResolutionException("URL has no host: $url")

            val address = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                throw UrlResolutionException("Cannot resolve host: $host", e)
            }

            if (address.isLoopbackAddress) {
                throw UrlResolutionException("URL targets a loopback address: $host")
            }
            if (address.isAnyLocalAddress) {
                throw UrlResolutionException("URL targets a wildcard address: $host")
            }
            if (address.isSiteLocalAddress) {
                throw UrlResolutionException("URL targets a private network address: $host")
            }
            if (address.isLinkLocalAddress) {
                throw UrlResolutionException("URL targets a link-local address: $host")
            }
        }
    }
}
