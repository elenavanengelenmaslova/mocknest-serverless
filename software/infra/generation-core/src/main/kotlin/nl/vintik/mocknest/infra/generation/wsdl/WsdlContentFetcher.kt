package nl.vintik.mocknest.infra.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.vintik.mocknest.application.generation.util.SafeUrlResolver
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.domain.generation.WsdlFetchException
import okhttp3.*
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_TIMEOUT_MS = 30_000L
private const val MAX_BODY_BYTES = 10L * 1024 * 1024 // 10 MiB

/**
 * OkHttp-based implementation of [WsdlContentFetcherInterface].
 * Fetches WSDL XML from a remote URL with SSRF protection and configurable timeout.
 * Lives in the infrastructure layer — no application or domain layer may reference this directly.
 *
 * @param timeoutMs HTTP request timeout in milliseconds (default 30 seconds)
 * @param urlSafetyValidator Optional override for URL safety validation. Defaults to
 *   [SafeUrlResolver.validateUrlSafety]. Pass a no-op lambda in tests to allow localhost URLs.
 */
class WsdlContentFetcher(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val urlSafetyValidator: (String) -> List<InetAddress> = { SafeUrlResolver.validateAndResolve(it) }
) : WsdlContentFetcherInterface {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override suspend fun fetch(url: String): String {
        logger.info { "Fetching WSDL from URL: ${SafeUrlResolver.sanitizeUrlForLogging(url)}" }

        // Validate URL safety and resolve DNS once (SSRF + DNS-rebinding protection)
        val validatedAddresses = runCatching {
            urlSafetyValidator(url)
        }.getOrElse { e ->
            val msg = "URL failed safety validation: ${e.message}"
            logger.warn(e) { msg }
            throw WsdlFetchException(msg, e)
        }

        // Pin OkHttp to the validated IPs so DNS cannot rebind between check and use
        val pinnedClient = if (validatedAddresses.isNotEmpty()) {
            val host = URI(url.trim()).host
            client.newBuilder().dns(PinnedDns(host, validatedAddresses)).build()
        } else {
            client
        }

        val request = runCatching {
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/xml, application/xml, */*")
                .build()
        }.getOrElse { e ->
            val msg = "Invalid WSDL URL format"
            logger.warn(e) { msg }
            throw WsdlFetchException(msg, e)
        }

        return runCatching {
            pinnedClient.newCall(request).executeAsync()
        }.fold(
            onSuccess = { body ->
                validateXml(body, url)
                logger.info { "WSDL fetched successfully: url=${SafeUrlResolver.sanitizeUrlForLogging(url)}, size=${body.length}" }
                body
            },
            onFailure = { exception ->
                val msg = when (exception) {
                    is WsdlFetchException -> throw exception
                    is java.net.SocketTimeoutException ->
                        "Timeout fetching WSDL from ${SafeUrlResolver.sanitizeUrlForLogging(url)} after ${timeoutMs}ms"
                    is java.net.UnknownHostException ->
                        "Network failure fetching WSDL: ${exception.message}"
                    is IOException ->
                        "Network failure fetching WSDL: ${exception.message}"
                    else -> "Failed to fetch WSDL from ${SafeUrlResolver.sanitizeUrlForLogging(url)}: ${exception.message}"
                }
                logger.error(exception) { msg }
                throw WsdlFetchException(msg, exception)
            }
        )
    }

    private fun validateXml(body: String, url: String) {
        val sanitizedUrl = SafeUrlResolver.sanitizeUrlForLogging(url)
        if (body.isBlank()) {
            throw WsdlFetchException("Response from $sanitizedUrl is not valid XML: empty response body")
        }
        val trimmed = body.dropWhile { it == '\uFEFF' || it.isWhitespace() }
        val looksLikeXml = trimmed.startsWith("<?xml") || trimmed.startsWith("<")
        if (!looksLikeXml) {
            throw WsdlFetchException("Response from $sanitizedUrl is not valid XML")
        }
    }
}

/**
 * OkHttp [Dns] that returns pre-resolved addresses for a single pinned hostname,
 * preventing DNS rebinding between SSRF validation and the actual HTTP request.
 */
private class PinnedDns(
    private val pinnedHost: String,
    private val addresses: List<InetAddress>
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return if (hostname.equals(pinnedHost, ignoreCase = true)) addresses
        else Dns.SYSTEM.lookup(hostname)
    }
}

/**
 * Suspending extension to execute an OkHttp call asynchronously.
 * Throws [WsdlFetchException] on non-2xx HTTP status.
 */
private suspend fun Call.executeAsync(): String = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    continuation.resumeWithException(
                        WsdlFetchException("HTTP ${resp.code} fetching WSDL from ${SafeUrlResolver.sanitizeUrlForLogging(resp.request.url.toString())}")
                    )
                } else {
                    val contentLength = resp.body.contentLength()
                    if (contentLength > MAX_BODY_BYTES) {
                        continuation.resumeWithException(
                            IOException(
                                "WSDL response Content-Length ($contentLength bytes) exceeds maximum allowed size of ${MAX_BODY_BYTES / (1024 * 1024)} MiB"
                            )
                        )
                        return
                    }
                    val body = try {
                        SizeLimitedSource(resp.body.source(), MAX_BODY_BYTES)
                            .buffer()
                            .readString(Charsets.UTF_8)
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                        return
                    }
                    continuation.resume(body)
                }
            }
        }
    })

    continuation.invokeOnCancellation { cancel() }
}

/**
 * Okio [ForwardingSource] that throws [IOException] if the total bytes read exceed [maxBytes].
 */
private class SizeLimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
    private var bytesRead = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val result = super.read(sink, byteCount)
        if (result != -1L) {
            bytesRead += result
            if (bytesRead > maxBytes) {
                throw IOException(
                    "WSDL response body exceeds maximum allowed size of ${maxBytes / (1024 * 1024)} MiB"
                )
            }
        }
        return result
    }
}
