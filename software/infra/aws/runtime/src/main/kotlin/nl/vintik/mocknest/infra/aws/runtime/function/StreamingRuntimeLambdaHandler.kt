package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.github.tomakehurst.wiremock.WireMockServer
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.usecases.ADMIN_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.application.runtime.usecases.MOCKNEST_PREFIX
import nl.vintik.mocknest.application.runtime.store.adapters.FILES_PREFIX
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
        private const val S3_STREAM_BUFFER_SIZE = 1024 * 1024 // 1MB

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
    private val wireMockServer: WireMockServer by inject()
    private val s3ResponseStreamer: S3ResponseStreamer by inject()

    private val requestParser = ApiGatewayRequestParser()
    private val protocolWriter = StreamingProtocolWriter()
    private val chunkedWriter = ChunkedResponseWriter()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val httpRequest = parseRequest(input, output) ?: return

        logger.info { "Runtime Lambda streaming request: ${httpRequest.method} ${httpRequest.path}" }

        val path = httpRequest.path
        val response = routeRequest(path, httpRequest)

        // Check response body size against 200MB limit
        val bodyBytes = response.body?.toByteArray(Charsets.UTF_8)
        if (bodyBytes != null && bodyBytes.size > MAX_RESPONSE_SIZE_BYTES) {
            logger.error { "Response body size ${bodyBytes.size} exceeds maximum supported streaming limit of ${MAX_RESPONSE_SIZE_BYTES} bytes" }
            writeErrorResponse(
                output,
                502,
                "Response payload exceeds the maximum supported streaming limit of 200MB"
            )
            return
        }

        // For client requests, check if chunkedDribbleDelay is configured
        if (path.startsWith(MOCKNEST_PREFIX)) {
            // Check if response body comes from S3 (bodyFileName)
            val bodyFileName = detectBodyFileName()
            if (bodyFileName != null) {
                writeS3StreamedResponse(response, bodyFileName, output)
                return
            }

            val chunkedConfig = detectChunkedDribbleDelay()
            if (chunkedConfig != null) {
                writeChunkedResponse(response, bodyBytes, chunkedConfig, output)
                return
            }
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
     * Detects if the last WireMock serve event has a bodyFileName, indicating
     * the response body is stored in S3 and should be streamed.
     * Returns the body file name if present, or null otherwise.
     */
    private fun detectBodyFileName(): String? {
        val serveEvents = wireMockServer.allServeEvents
        val lastEvent = serveEvents.firstOrNull() ?: return null

        val responseDefinition = lastEvent.responseDefinition ?: return null
        return responseDefinition.bodyFileName
    }

    /**
     * Writes a streaming response where the body is streamed from S3 using a bounded 1MB buffer.
     * Writes metadata+delimiter first, then streams the S3 object content directly to the output.
     * On S3 retrieval failure, the stream is aborted and the error is logged.
     */
    private fun writeS3StreamedResponse(
        response: HttpResponse,
        bodyFileName: String,
        output: OutputStream,
    ) {
        val headers = response.headers
            ?.flatMap { (name, values) -> values.map { name to it } }
            ?.toMap()
            ?: emptyMap()

        protocolWriter.writeMetadataAndDelimiter(response.statusCode.value, headers, output)

        val s3Key = "$FILES_PREFIX$bodyFileName"
        logger.info { "Streaming S3 response body: key=$s3Key" }

        val success = s3ResponseStreamer.streamToOutput(s3Key, output)
        if (!success) {
            logger.error { "S3 streaming failed for key=$s3Key, stream aborted" }
        }
        output.flush()
    }

    /**
     * Detects chunkedDribbleDelay configuration from the last WireMock serve event.
     * Returns a pair of (numberOfChunks, totalDuration) if valid chunked config is present,
     * or null if not configured or invalid.
     */
    private fun detectChunkedDribbleDelay(): ChunkedDribbleConfig? {
        val serveEvents = wireMockServer.allServeEvents
        val lastEvent = serveEvents.firstOrNull() ?: return null

        val responseDefinition = lastEvent.responseDefinition ?: return null
        val chunkedDribbleDelay = responseDefinition.chunkedDribbleDelay ?: return null

        val numberOfChunks = chunkedDribbleDelay.numberOfChunks
        val totalDuration = chunkedDribbleDelay.totalDuration.toLong()

        // Invalid config: numberOfChunks < 1 or totalDuration < 0 → ignore and write full body
        if (numberOfChunks < 1 || totalDuration < 0) {
            logger.debug { "Invalid chunkedDribbleDelay config (numberOfChunks=$numberOfChunks, totalDuration=$totalDuration), ignoring" }
            return null
        }

        return ChunkedDribbleConfig(numberOfChunks, totalDuration)
    }

    /**
     * Writes a chunked streaming response using the ChunkedResponseWriter.
     * Writes metadata+delimiter first, then delivers body in chunks with delays.
     */
    private fun writeChunkedResponse(
        response: HttpResponse,
        bodyBytes: ByteArray?,
        config: ChunkedDribbleConfig,
        output: OutputStream,
    ) {
        val headers = response.headers
            ?.flatMap { (name, values) -> values.map { name to it } }
            ?.toMap()
            ?: emptyMap()

        protocolWriter.writeMetadataAndDelimiter(response.statusCode.value, headers, output)

        if (bodyBytes != null && bodyBytes.isNotEmpty()) {
            chunkedWriter.writeChunked(bodyBytes, config.numberOfChunks, config.totalDurationMs, output)
        }
        output.flush()
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

    /**
     * Internal data class for chunked dribble delay configuration.
     */
    private data class ChunkedDribbleConfig(
        val numberOfChunks: Int,
        val totalDurationMs: Long,
    )
}
