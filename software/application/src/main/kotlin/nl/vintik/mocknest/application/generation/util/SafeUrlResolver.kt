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
 * and fetches content with strict timeouts. Redirects are disabled to prevent SSRF via redirect.
 */
class SafeUrlResolver(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000
) : UrlFetcher {

    override fun fetch(url: String): String {
        validateUrlSafety(url)
        return runCatching {
            logger.info { "Fetching URL: ${sanitizeUrlForLogging(url)}" }
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse { e ->
            when (e) {
                is UrlResolutionException -> throw e
                else -> throw UrlResolutionException("Failed to fetch URL: ${sanitizeUrlForLogging(url)}", e)
            }
        }
    }

    companion object {
        private val ALLOWED_SCHEMES = listOf("http", "https")
        private val SENSITIVE_PARAM_PATTERNS = listOf("token", "key", "secret", "auth", "sig", "password", "credential")

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
         * Sanitizes a URL for safe logging by stripping userinfo and redacting sensitive query parameters.
         */
        fun sanitizeUrlForLogging(url: String): String = runCatching {
            val uri = URI(url.trim())
            val sanitizedQuery = uri.query?.let { query ->
                query.split("&").joinToString("&") { param ->
                    val paramKey = param.substringBefore("=").lowercase()
                    if (SENSITIVE_PARAM_PATTERNS.any { paramKey.contains(it) } || paramKey.startsWith("x-amz-"))
                        "${param.substringBefore("=")}=<redacted>"
                    else param
                }
            }
            URI(uri.scheme, null, uri.host, uri.port, uri.path, sanitizedQuery, null).toString()
        }.getOrDefault("<unparseable-url>")

        /**
         * Validates a URL is safe to fetch (no SSRF to internal networks).
         * Throws [UrlResolutionException] if the URL targets a forbidden address.
         */
        fun validateUrlSafety(url: String) {
            val uri = runCatching {
                URI(url.trim())
            }.getOrElse { e ->
                throw UrlResolutionException("Invalid URL", e)
            }

            val scheme = uri.scheme?.lowercase()
            if (scheme !in ALLOWED_SCHEMES) {
                throw UrlResolutionException("Unsupported URL scheme: $scheme (only HTTP and HTTPS are allowed)")
            }

            val host = uri.host
                ?: throw UrlResolutionException("URL has no host")

            val addresses = runCatching {
                InetAddress.getAllByName(host)
            }.getOrElse { e ->
                throw UrlResolutionException("Cannot resolve host: $host", e)
            }

            for (address in addresses) {
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
}
