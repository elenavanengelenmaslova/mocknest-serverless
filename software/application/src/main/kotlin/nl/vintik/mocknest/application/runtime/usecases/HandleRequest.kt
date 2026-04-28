package nl.vintik.mocknest.application.runtime.usecases

import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.Response
import io.github.oshai.kotlinlogging.KotlinLogging
import wiremock.org.apache.hc.core5.http.ContentType
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

const val BASE_URL = "http://mocknest.internal"
const val ADMIN_PREFIX = "/__admin/"
const val MOCKNEST_PREFIX = "/mocknest/"
const val AI_PREFIX = "/ai/generation/"

private val logger = KotlinLogging.logger {}

fun interface HandleClientRequest {
    operator fun invoke(httpRequest: HttpRequest): HttpResponse
}

fun interface HandleAdminRequest {
    operator fun invoke(path: String, httpRequest: HttpRequest): HttpResponse
}

fun interface HandleAIGenerationRequest {
    operator fun invoke(path: String, httpRequest: HttpRequest): HttpResponse
}

fun forwardToDirectCallHttpServer(
    typeCall: String,
    httpRequest: HttpRequest,
    directCall: (ImmutableRequest) -> Response
): HttpResponse {
    logger.info { "Forwarding $typeCall request with path: ${httpRequest.path}" }

    val queryString = httpRequest.queryParameters.entries
        .joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, UTF_8)}=${URLEncoder.encode(value, UTF_8)}"
        }
        .takeIf { it.isNotEmpty() }
        ?.let { "?$it" }
        .orEmpty()

    val path = httpRequest.path

    // Create a WireMock request using the WireMock client
    val wireMockRequest =
        ImmutableRequest.create()
            .withAbsoluteUrl("$BASE_URL/$path$queryString")
            .withMethod(
                RequestMethod.fromString(
                    httpRequest.method.name
                )
            )
            .withHeaders(
                WireMockHttpHeaders(
                    httpRequest.headers.map { header ->
                        HttpHeader(header.key, header.value)
                    }
                ))
            .withBody(httpRequest.body.orEmpty().toByteArray())
            .build()

    logger.info { "Calling wiremock $typeCall with request: ${httpRequest.method} ${httpRequest.path}" }

    // Call stubRequest on the DirectCallHttpServer
    val response = directCall(wireMockRequest)

    logger.info { "Wiremock $typeCall response code: ${response.status}" }
    val contentType =
        if (response.headers.contentTypeHeader.isPresent) response.headers.contentTypeHeader.firstValue()
        else ContentType.APPLICATION_JSON.toString()
    // Convert the WireMock Response to an HttpResponse
    val responseHeaders = mutableMapOf<String, List<String>>()

    val headers = response.headers.all().filter { !it.key.equals("Matched-Stub-Id", ignoreCase = true) }
    headers.forEach { header ->
        responseHeaders[header.key()] = header.values()
    }
    responseHeaders["Content-Type"] = listOf(contentType)

    return HttpResponse(
        HttpStatusCode(response.status),
        responseHeaders,
        response.bodyAsString
    )

}
