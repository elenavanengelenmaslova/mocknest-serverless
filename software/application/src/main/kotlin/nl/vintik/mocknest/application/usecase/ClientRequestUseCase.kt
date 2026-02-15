package nl.vintik.mocknest.application.usecase

import nl.vintik.mocknest.domain.model.HttpRequest
import nl.vintik.mocknest.domain.model.HttpResponse
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import org.springframework.stereotype.Component

@Component
class ClientRequestUseCase(private val directCallHttpServer: DirectCallHttpServer) :
    HandleClientRequest {
    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        return forwardToDirectCallHttpServer(
            "client request",
            httpRequest
        ) { httpRequest -> directCallHttpServer.stubRequest(httpRequest) }

    }
}