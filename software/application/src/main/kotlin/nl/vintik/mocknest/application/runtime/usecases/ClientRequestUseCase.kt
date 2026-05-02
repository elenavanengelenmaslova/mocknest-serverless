package nl.vintik.mocknest.application.runtime.usecases

import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer

class ClientRequestUseCase(private val directCallHttpServer: DirectCallHttpServer) :
    HandleClientRequest {
    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        return forwardToDirectCallHttpServer(
            "client request",
            httpRequest
        ) { wireMockRequest -> directCallHttpServer.stubRequest(wireMockRequest) }

    }
}
