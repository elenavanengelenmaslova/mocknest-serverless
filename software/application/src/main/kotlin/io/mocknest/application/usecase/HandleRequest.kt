package io.mocknest.application.usecase

import io.mocknest.domain.model.HttpRequest
import io.mocknest.domain.model.HttpResponse
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.Response
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.util.LinkedMultiValueMap
import wiremock.org.apache.hc.core5.http.ContentType
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

const val BASE_URL = "http://mocknest.internal"
const val ADMIN_PREFIX = "/__admin/"
const val MOCKNEST_PREFIX = "/mocknest/"

private val logger = KotlinLogging.logger {}

fun interface HandleClientRequest {
    operator fun invoke(httpRequest: HttpRequest): HttpResponse
}
fun interface HandleAdminRequest {
    operator fun invoke(path: String, httpRequest: HttpRequest): HttpResponse
}


fun forwardToDirectCallHttpServer(typeCall: String, httpRequest: HttpRequest, directCall: (ImmutableRequest) -> Response): HttpResponse {
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
                    httpRequest.method.name()
                )
            )
            .withHeaders(
                WireMockHttpHeaders(
                    httpRequest.headers.map { header ->
                        HttpHeader(header.key, header.value)
                    }
                ))
            .withBody(httpRequest.body?.toString().orEmpty().toByteArray())
            .build()

    logger.info { "Calling wiremock $typeCall with request: ${httpRequest.method} ${httpRequest.path}" }

    // Call stubRequest on the DirectCallHttpServer
    val response = directCall(wireMockRequest)

    logger.info { "Wiremock $typeCall response code: ${response.status}" }
    val contentType =
        if (response.headers.contentTypeHeader.isPresent) response.headers.contentTypeHeader.firstValue()
        else ContentType.APPLICATION_JSON.toString()
    // Convert the WireMock Response to an HttpResponse
    val responseHeaders = LinkedMultiValueMap<String, String>()

    val headers = response.headers.all().filter { !it.key.equals("Matched-Stub-Id", ignoreCase = true) }
    headers.forEach { header ->
        responseHeaders.add(header.key(), header.firstValue())
    }
    responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType)

    return HttpResponse(
        HttpStatusCode.valueOf(response.status),
        responseHeaders,
        response.bodyAsString
    )

}