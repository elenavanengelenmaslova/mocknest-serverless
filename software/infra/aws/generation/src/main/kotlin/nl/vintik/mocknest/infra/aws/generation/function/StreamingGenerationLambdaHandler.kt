package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.AI_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import nl.vintik.mocknest.infra.aws.core.streaming.ApiGatewayRequestParser
import nl.vintik.mocknest.infra.aws.core.streaming.RequestParseException
import nl.vintik.mocknest.infra.aws.core.streaming.StreamingProtocolWriter
import nl.vintik.mocknest.infra.aws.generation.di.generationModule
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Streaming AWS Lambda handler for the Generation function.
 *
 * Implements [RequestStreamHandler] to support the API Gateway streaming protocol,
 * enabling responses up to 200MB. Uses [StreamingProtocolWriter] to write the
 * metadata + null delimiter + body format expected by API Gateway.
 *
 * Koin is initialized once per Lambda container lifecycle in the companion object init
 * block. Priming happens eagerly — before the SnapStart snapshot is taken.
 */
class StreamingGenerationLambdaHandler : RequestStreamHandler, KoinComponent {

    companion object {
        init {
            KoinBootstrap.init(listOf(coreModule(), generationModule()))
            // Explicit priming — runs BEFORE SnapStart snapshot, not lazily on first request
            KoinBootstrap.getKoin().get<GenerationPrimingHook>().onApplicationReady()
        }
    }

    private val handleAIGenerationRequest: HandleAIGenerationRequest by inject()
    private val getAIHealth: GetAIHealth by inject()
    private val requestParser = ApiGatewayRequestParser()
    private val protocolWriter = StreamingProtocolWriter()

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        val httpRequest = try {
            requestParser.parse(input)
        } catch (e: RequestParseException) {
            logger.warn(e) { "Failed to parse API Gateway request" }
            val escapedMessage = (e.message ?: "Unknown parse error")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            val errorResponse = HttpResponse(
                HttpStatusCode.BAD_REQUEST,
                mapOf("Content-Type" to listOf("application/json")),
                """{"error":"$escapedMessage"}"""
            )
            protocolWriter.write(errorResponse, output)
            output.flush()
            return
        }

        logger.info { "Generation Lambda streaming request: ${httpRequest.method} ${httpRequest.path}" }

        val response = when {
            httpRequest.path == "${AI_PREFIX}health" -> {
                logger.debug { "Processing AI health check request" }
                getAIHealth()
            }
            httpRequest.path.startsWith(AI_PREFIX) -> {
                logger.debug { "Processing AI generation request ${httpRequest.path}" }
                val aiPath = "/" + httpRequest.path.removePrefix(AI_PREFIX)
                val routedRequest = HttpRequest(
                    method = httpRequest.method,
                    headers = httpRequest.headers,
                    multiValueHeaders = httpRequest.multiValueHeaders,
                    path = aiPath,
                    queryParameters = httpRequest.queryParameters,
                    multiValueQueryParameters = httpRequest.multiValueQueryParameters,
                    body = httpRequest.body
                )
                handleAIGenerationRequest(aiPath, routedRequest)
            }
            else -> {
                logger.warn { "Path ${httpRequest.path} not found in generation Lambda" }
                HttpResponse(
                    HttpStatusCode.NOT_FOUND,
                    body = "Path ${httpRequest.path} not found"
                )
            }
        }

        protocolWriter.write(response, output)
        output.flush()
    }
}
