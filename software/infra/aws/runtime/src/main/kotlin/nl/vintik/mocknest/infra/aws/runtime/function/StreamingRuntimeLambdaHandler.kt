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
import nl.vintik.mocknest.infra.aws.core.streaming.StreamingProtocolWriter
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import nl.vintik.mocknest.infra.aws.runtime.streaming.ChunkedResponseWriter
import nl.vintik.mocknest.infra.aws.runtime.streaming.S3ResponseStreamer
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
    private val protocolWriter = StreamingProtocolWriter()
    private val chunkedWriter = ChunkedResponseWriter()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val httpRequest = parseRequest(input, output) ?: return

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
                    return
                }
                // Existing: in-memory chunked path
                val bodyBytes = response.body?.toByteArray(Charsets.UTF_8)
                if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                    writeChunkedResponse(response, bodyBytes, dribbleConfig, output)
                    return
                }
            }
        }

        // Check response body size against 200MB limit (for non-S3 path)
        val bodyBytes = response.body?.toByteArray(Charsets.UTF_8)
        if (bodyBytes != null && bodyBytes.size > MAX_RESPONSE_SIZE_BYTES) {
            logger.error { "Response body size ${bodyBytes.size} exceeds maximum supported streaming limit of ${MAX_RESPONSE_SIZE_BYTES} bytes" }
            writeErrorResponse(output, 502, "Response payload exceeds the maximum supported streaming limit of 200MB")
            return
        }

        // Write standard streaming response
        protocolWriter.write(response, output)
        output.flush()
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
     * Writes a chunked streaming response using the ChunkedResponseWriter.
     * Writes metadata+delimiter first, then delivers body in chunks with delays.
     */
    private fun writeChunkedResponse(
        response: HttpResponse,
        bodyBytes: ByteArray,
        config: CapturedDribbleConfig,
        output: OutputStream,
    ) {
        val headers = response.headers
            ?.flatMap { (name, values) -> values.map { name to it } }
            ?.toMap()
            ?: emptyMap()

        protocolWriter.writeMetadataAndDelimiter(response.statusCode.value, headers, output)

        runBlocking {
            chunkedWriter.writeChunked(bodyBytes, config.numberOfChunks, config.totalDurationMs, output)
        }
        output.flush()
    }

    /**
     * Streams response body from S3 with chunked dribble delays.
     * Uses S3 HEAD for size validation, then streams with bounded memory
     * via the consumer callback pattern (InputStream consumed within S3 callback scope).
     */
    private fun writeS3ChunkedResponse(
        response: HttpResponse,
        config: CapturedDribbleConfig,
        output: OutputStream,
    ) {
        val bodyFileName = config.bodyFileName ?: return
        val s3Key = "__files/$bodyFileName"

        runBlocking {
            // Size check via HEAD request
            val contentLength = s3ResponseStreamer.getContentLength(s3Key)
            if (contentLength == null) {
                writeErrorResponse(output, 502, "Failed to retrieve S3 object metadata: $bodyFileName")
                return@runBlocking
            }
            if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                writeErrorResponse(output, 502, "Response payload exceeds the maximum supported streaming limit of 200MB")
                return@runBlocking
            }

            // Write metadata + delimiter before streaming body
            val headers = response.headers
                ?.flatMap { (name, values) -> values.map { name to it } }
                ?.toMap()
                ?: emptyMap()
            protocolWriter.writeMetadataAndDelimiter(response.statusCode.value, headers, output)

            // Stream from S3 with chunked delays using consumer callback
            // The InputStream is only valid inside the S3 callback scope
            val success = s3ResponseStreamer.streamWithConsumer(s3Key) { inputStream, _ ->
                chunkedWriter.writeChunkedFromStream(
                    input = inputStream,
                    bodySize = contentLength,
                    numberOfChunks = config.numberOfChunks,
                    totalDurationMs = config.totalDurationMs,
                    output = output,
                )
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
     * Writes an error response using the streaming protocol format.
     */
    private fun writeErrorResponse(output: OutputStream, statusCode: Int, message: String) {
        val errorResponse = HttpResponse(
            HttpStatusCode(statusCode),
            mapOf("Content-Type" to listOf("text/plain")),
            message
        )
        protocolWriter.write(errorResponse, output)
        output.flush()
    }
}
