package io.mocknest.application.usecase

import io.mocknest.domain.model.HttpRequest
import io.mocknest.domain.model.HttpResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}
internal val mapper = jacksonObjectMapper()

@Component
class AdminRequestUseCase(
    private val directCallHttpServer: DirectCallHttpServer,
) : HandleAdminRequest {

    override fun invoke(
        path: String,
        httpRequest: HttpRequest,
    ): HttpResponse {
        logger.info { "Handling admin request ${httpRequest.method} ${httpRequest.path} " }
        return forwardToDirectCallHttpServer("admin", httpRequest) { httpRequest ->
            directCallHttpServer.adminRequest(
                httpRequest
            )
        }
    }
}