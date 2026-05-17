package nl.vintik.mocknest.application.runtime.usecases

import nl.vintik.mocknest.domain.core.HttpRequest
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import nl.vintik.mocknest.domain.core.HttpMethod
import kotlin.test.assertEquals

class AdminClientRequestUseCasesTest {

    private val directCallHttpServer = mockk<DirectCallHttpServer>()
    private val adminUseCase = AdminRequestUseCase(directCallHttpServer)
    private val clientUseCase = ClientRequestUseCase(directCallHttpServer)

    @Test
    fun `AdminRequestUseCase should forward to adminRequest`() {
        val httpRequest = HttpRequest(method = HttpMethod.GET, headers = emptyMap(), path = "/__admin/mappings", queryParameters = emptyMap(), body = null)
        val mockResponse = mockk<Response>()
        every { mockResponse.status } returns 200
        every { mockResponse.bodyAsString } returns "admin-ok"
        every { mockResponse.headers } returns WireMockHttpHeaders.noHeaders()

        every { directCallHttpServer.adminRequest(any()) } returns mockResponse

        val response = adminUseCase.invoke("/mappings", httpRequest)

        assertEquals(200, response.statusCode.value())
        assertEquals("admin-ok", response.body)
    }

    @Test
    fun `ClientRequestUseCase should forward to stubRequest`() {
        val httpRequest = HttpRequest(method = HttpMethod.GET, headers = emptyMap(), path = "/test", queryParameters = emptyMap(), body = null)
        val mockResponse = mockk<Response>()
        every { mockResponse.status } returns 200
        every { mockResponse.bodyAsString } returns "client-ok"
        every { mockResponse.headers } returns WireMockHttpHeaders.noHeaders()

        every { directCallHttpServer.stubRequest(any()) } returns mockResponse

        val response = clientUseCase.invoke(httpRequest)

        assertEquals(200, response.statusCode.value())
        assertEquals("client-ok", response.body)
    }
}
