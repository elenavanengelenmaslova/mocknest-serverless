package nl.vintik.mocknest.infra.aws.generation.wsdl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.vintik.mocknest.application.generation.util.SafeUrlResolver
import nl.vintik.mocknest.application.generation.util.UrlResolutionException
import nl.vintik.mocknest.application.generation.wsdl.WsdlContentFetcherInterface
import nl.vintik.mocknest.domain.generation.WsdlFetchException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_TIMEOUT_MS = 30_000L

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
    private val urlSafetyValidator: (String) -> Unit = { SafeUrlResolver.validateUrlSafety(it) }
) : WsdlContentFetcherInterface {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun fetch(url: String): String {
        logger.info { "Fetching WSDL from URL: ${SafeUrlResolver.sanitizeUrlForLogging(url)}" }

        // Validate URL safety before any network call (SSRF protection)
        runCatching {
            urlSafetyValidator(url)
        }.onFailure { e ->
            val msg = "URL targets an unsafe address: ${e.message}"
            logger.warn(e) { msg }
            throw WsdlFetchException(msg, e)
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
            client.newCall(request).executeAsync()
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
                        "Timeout fetching WSDL from $url after ${timeoutMs}ms"
                    is java.net.UnknownHostException ->
                        "Network failure fetching WSDL: ${exception.message}"
                    is IOException ->
                        "Network failure fetching WSDL: ${exception.message}"
                    else -> "Failed to fetch WSDL from $url: ${exception.message}"
                }
                logger.error(exception) { msg }
                throw WsdlFetchException(msg, exception)
            }
        )
    }

    private fun validateXml(body: String, url: String) {
        if (body.isBlank()) {
            throw WsdlFetchException("Response from $url is not valid XML: empty response body")
        }
        val trimmed = body.trimStart()
        val looksLikeXml = trimmed.startsWith("<?xml") || trimmed.startsWith("<")
        if (!looksLikeXml) {
            throw WsdlFetchException("Response from $url is not valid XML")
        }
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
                        WsdlFetchException("HTTP ${resp.code} fetching WSDL from ${resp.request.url}")
                    )
                } else {
                    val body = resp.body.string()
                    continuation.resume(body)
                }
            }
        }
    })

    continuation.invokeOnCancellation { cancel() }
}
