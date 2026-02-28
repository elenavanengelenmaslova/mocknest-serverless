package nl.vintik.mocknest.application.runtime.usecases

import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
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
        return forwardToDirectCallHttpServer("admin", httpRequest) { wireMockRequest ->
            directCallHttpServer.adminRequest(
                wireMockRequest
            )
        }
    }
}