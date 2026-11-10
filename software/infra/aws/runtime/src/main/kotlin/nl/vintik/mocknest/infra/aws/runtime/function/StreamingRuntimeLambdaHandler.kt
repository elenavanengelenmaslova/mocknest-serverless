package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.runtime.extensions.CapturedDribbleConfig
import nl.vintik.mocknest.application.runtime.extensions.ChunkedDribbleDelayCapture
import nl.vintik.mocknest.application.runtime.usecases.ADMIN_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.application.runtime.usecases.MOCKNEST_PREFIX
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import nl.vintik.mocknest.infra.aws.core.streaming.ApiGatewayRequestParser
import nl.vintik.mocknest.infra.aws.core.streaming.RequestParseException
import nl.vintik.lambda.streaming.OBSERVED_MAX_PRELUDE_LEN
import nl.vintik.lambda.streaming.ResponseMetadata
import nl.vintik.lambda.streaming.ResponseWriter
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import nl.vintik.mocknest.infra.aws.runtime.streaming.ChunkedResponseWriter
import nl.vintik.mocknest.infra.aws.runtime.streaming.S3ResponseStreamer
import nl.vintik.mocknest.infra.aws.runtime.streaming.SseDribbleWriter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Streaming AWS Lambda handler for the Runtime function.
 *
 * Implements [RequestStreamHandler] to enable response streaming via the API Gateway
 * streaming protocol, supporting responses up to 200MB and SSE mock simulation
 * via chunked delivery with configurable delays.
 *
 * Uses [ResponseWriter] from the streaming-core library for protocol encoding.
 *
 * Koin is initialized once per Lambda container lifecycle in the companion object init
 * block. Priming and CRaC registration happen eagerly — before the SnapStart snapshot
 * is taken — not lazily on first request.
 */
class StreamingRuntimeLambdaHandler : RequestStreamHandler, KoinComponent {

    companion object {
        private const val MAX_RESPONSE_SIZE_BYTES = 200L * 1024 * 1024 // 200MB

        init {
            KoinBootstrap.init(listOf(coreModule(), runtimeModule()))
            // Explicit priming — runs BEFORE SnapStart snapshot, not lazily on first request
            KoinBootstrap.getKoin().get<RuntimePrimingHook>().onApplicationReady()
            // CRaC registration — enables afterRestore() for mock reload after SnapStart restore
            KoinBootstrap.getKoin().get<RuntimeMappingReloadHook>().register()
        }
    }

    private val handleClientRequest: HandleClientRequest by inject()
    private val handleAdminRequest: HandleAdminRequest by inject()
    private val getRuntimeHealth: GetRuntimeHealth by inject()
    private val s3ResponseStreamer: S3ResponseStreamer by inject()

    private val requestParser = ApiGatewayRequestParser()
    private val writer = ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)

    // Non-SSE dribble: progressive-by-size chunk/flush delivery (no per-chunk pacing — the Lambda
    // streaming runtime buffers small writes, so a hand-rolled flush cannot trickle sub-buffer
    // chunks; only size-based progress is possible for arbitrary bodies).
    private val chunkedWriter = ChunkedResponseWriter()

    // SSE dribble: padded, paced Server-Sent Events via the streaming-sse extension. Padding
    // inflates each event past the runtime buffer so small delayed chunks trickle progressively.
    private val sseDribbleWriter = SseDribbleWriter()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        output.use { output ->
            val httpRequest = parseRequest(input, output) ?: return@use

            logger.info { "Runtime Lambda streaming request: ${httpRequest.method} ${httpRequest.path}" }

            val path = httpRequest.path

            // Clear any previous dribble config before routing
            ChunkedDribbleDelayCapture.clear()

            val response = routeRequest(path, httpRequest)

            // For client requests, check if chunkedDribbleDelay was captured by the transformer
            if (path.startsWith(MOCKNEST_PREFIX)) {
                val dribbleConfig = ChunkedDribbleDelayCapture.getAndClear()
                if (dribbleConfig != null) {
                    if (dribbleConfig.bodyFileName != null) {
                        // S3 streaming path with chunked dribble
                        writeS3ChunkedResponse(response, dribbleConfig, output)
                        return@use
                    }
                    // Existing: in-memory chunked path
                    val bodyBytes = response.body?.toByteArray(Charsets.UTF_8)
                    if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                        writeChunkedResponse(response, bodyBytes, dribbleConfig, output)
                        return@use
                    }
                }
            }

            // Check response body size against 200MB limit (for non-S3 path)
            val bodyBytes = response.body?.toByteArray(Charsets.UTF_8)
            if (bodyBytes != null && bodyBytes.size > MAX_RESPONSE_SIZE_BYTES) {
                logger.error { "Response body size ${bodyBytes.size} exceeds maximum supported streaming limit of ${MAX_RESPONSE_SIZE_BYTES} bytes" }
                writeErrorResponse(output, 502, "Response payload exceeds the maximum supported streaming limit of 200MB")
                return@use
            }

            // Write standard streaming response
            val metadata = ResponseMetadata.fromMultiValue(
                statusCode = response.statusCode.value,
                headers = response.headers ?: emptyMap(),
            )
            writer.writeResponse(output, metadata, bodyBytes)
            output.flush()
        }
    }

    private fun parseRequest(input: InputStream, output: OutputStream): HttpRequest? =
        runCatching {
            requestParser.parse(input)
        }.getOrElse { e ->
            val message = when (e) {
                is RequestParseException -> e.message ?: "Failed to parse request"
                else -> "Failed to parse request: ${e.message}"
            }
            logger.error(e) { "Failed to parse incoming request: $message" }
            writeErrorResponse(output, 400, message)
            null
        }

    private fun routeRequest(path: String, httpRequest: HttpRequest): HttpResponse =
        when {
            path == "${ADMIN_PREFIX}health" -> {
                logger.debug { "Processing health check request" }
                getRuntimeHealth()
            }
            path.startsWith(ADMIN_PREFIX) -> {
                logger.debug { "Processing admin request $path" }
                val adminPath = path.removePrefix(ADMIN_PREFIX)
                handleAdminRequest(adminPath, httpRequest.copy(path = adminPath))
            }
            path.startsWith(MOCKNEST_PREFIX) -> {
                logger.info { "Processing client request $path" }
                handleClientRequest(httpRequest.copy(path = path.removePrefix(MOCKNEST_PREFIX)))
            }
            else -> {
                logger.warn { "Path $path not found in runtime Lambda" }
                HttpResponse(
                    HttpStatusCode.NOT_FOUND,
                    body = "Path $path not found",
                )
            }
        }

    /**
     * Writes an in-memory chunked streaming response. Writes metadata+delimiter first, then
     * delivers the body in chunks.
     *
     * For `text/event-stream` bodies the body is delivered as padded, paced SSE events via the
     * streaming-sse extension so small delayed chunks trickle to the client progressively.
     * For any other content type the body is delivered progressively-by-size via
     * [ChunkedResponseWriter] (the Lambda streaming runtime buffers sub-threshold writes, so
     * per-chunk pacing is only effective with SSE padding — see [SseDribbleWriter]).
     */
    private fun writeChunkedResponse(
        response: HttpResponse,
        bodyBytes: ByteArray,
        config: CapturedDribbleConfig,
        output: OutputStream,
    ) {
        val sse = isServerSentEvents(response)

        // SSE padding inflates the wire body far beyond the raw body size, so a Content-Length
        // reflecting the raw body would make the client (and API Gateway/CloudFront) truncate the
        // padded stream at that length — delivering only the first few hundred bytes. SSE responses
        // are open-ended streams and must NOT advertise a fixed Content-Length.
        val headers = if (sse) {
            stripContentLength(response.headers)
        } else {
            response.headers ?: emptyMap()
        }

        val metadata = ResponseMetadata.fromMultiValue(
            statusCode = response.statusCode.value,
            headers = headers,
        )
        writer.writeMetadata(output, metadata)

        if (sse) {
            sseDribbleWriter.writeSseChunked(bodyBytes, config.numberOfChunks, config.totalDurationMs, output)
        } else {
            runBlocking {
                chunkedWriter.writeChunked(bodyBytes, config.numberOfChunks, config.totalDurationMs, output)
            }
        }
        output.flush()
    }

    /**
     * Streams response body from S3 with chunked dribble delays.
     * Uses S3 HEAD for size validation, then streams with bounded memory
     * via the consumer callback pattern (InputStream consumed within S3 callback scope).
     *
     * Uses ETag from HEAD to ensure the same object version is streamed (prevents
     * TOCTOU issues if the object is replaced between HEAD and GET).
     */
    private fun writeS3ChunkedResponse(
        response: HttpResponse,
        config: CapturedDribbleConfig,
        output: OutputStream,
    ) {
        val bodyFileName = config.bodyFileName ?: return
        val s3Key = "__files/$bodyFileName"

        runBlocking {
            // Metadata check via HEAD request (returns contentLength + ETag)
            val s3Metadata = s3ResponseStreamer.getObjectMetadata(s3Key)
            if (s3Metadata == null) {
                writeErrorResponse(output, 502, "Failed to retrieve S3 object metadata: $bodyFileName")
                return@runBlocking
            }
            if (s3Metadata.contentLength > MAX_RESPONSE_SIZE_BYTES) {
                writeErrorResponse(output, 502, "Response payload exceeds the maximum supported streaming limit of 200MB")
                return@runBlocking
            }

            val sse = isServerSentEvents(response)

            // Build headers. For a plain (non-SSE) dribble the body streams 1:1 from S3, so the
            // Content-Length is the actual S3 object size. For SSE the padded/framed wire body is
            // far larger than the S3 object, and an open-ended SSE stream must NOT advertise a fixed
            // Content-Length — otherwise the client truncates the padded stream at that length and
            // only the first few hundred bytes arrive (as a terminal burst). So Content-Length is
            // stripped for SSE and only re-added with the object size on the non-SSE path.
            val responseHeaders = stripContentLength(response.headers).toMutableMap()
            if (!sse) {
                responseHeaders["Content-Length"] = listOf(s3Metadata.contentLength.toString())
            }

            // Write metadata + delimiter before streaming body
            val headerMetadata = ResponseMetadata.fromMultiValue(
                statusCode = response.statusCode.value,
                headers = responseHeaders,
            )
            writer.writeMetadata(output, headerMetadata)

            // Stream from S3 with chunked delays using consumer callback. Both the SSE and
            // non-SSE writers read from the bounded ≤1MB loop inside the callback, so the whole
            // S3 object is never materialized (zero-memory-streaming guarantee preserved).
            // Pass ETag to ensure we stream the same object version we validated.
            val success = s3ResponseStreamer.streamWithConsumer(s3Key, expectedETag = s3Metadata.eTag) { inputStream, _ ->
                if (sse) {
                    sseDribbleWriter.writeSseChunkedFromStream(
                        input = inputStream,
                        bodySize = s3Metadata.contentLength,
                        numberOfChunks = config.numberOfChunks,
                        totalDurationMs = config.totalDurationMs,
                        output = output,
                    )
                } else {
                    chunkedWriter.writeChunkedFromStream(
                        input = inputStream,
                        bodySize = s3Metadata.contentLength,
                        numberOfChunks = config.numberOfChunks,
                        totalDurationMs = config.totalDurationMs,
                        output = output,
                    )
                }
            }

            if (!success) {
                logger.error { "S3 streaming with chunked dribble failed for key=$s3Key" }
                // Note: metadata+delimiter already written, can't change status code
                // Client will receive a truncated response
            }
            output.flush()
        }
    }

    /**
     * True when the response declares a `text/event-stream` Content-Type. SSE padding/pacing only
     * makes sense for SSE bodies — the padding lives in SSE comment lines that only an SSE client
     * (or `sed -n 's/^data: //p'`) ignores. Non-SSE bodies stay on size-based progressive delivery.
     */
    private fun isServerSentEvents(response: HttpResponse): Boolean =
        response.headers
            ?.entries
            ?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.any { it.contains("text/event-stream", ignoreCase = true) }
            ?: false

    /**
     * Returns [headers] without any `Content-Length` entry (case-insensitive).
     *
     * SSE dribble responses are open-ended, padded streams whose wire size is far larger than the
     * raw mock body. Advertising a fixed `Content-Length` (the raw body size) makes the client and
     * API Gateway/CloudFront truncate the stream at that length, so the client receives only the
     * first few hundred bytes in a single terminal burst instead of the full padded SSE stream.
     */
    private fun stripContentLength(headers: Map<String, List<String>>?): Map<String, List<String>> =
        headers
            ?.filterKeys { !it.equals("Content-Length", ignoreCase = true) }
            ?: emptyMap()

    /**
     * Writes an error response using the streaming protocol format.
     */
    private fun writeErrorResponse(output: OutputStream, statusCode: Int, message: String) {
        val metadata = ResponseMetadata(
            statusCode = statusCode,
            headers = mapOf("Content-Type" to "text/plain"),
        )
        writer.writeResponse(output, metadata, message.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
