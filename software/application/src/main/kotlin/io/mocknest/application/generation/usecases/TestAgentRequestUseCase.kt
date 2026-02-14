package io.mocknest.application.generation.usecases

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mocknest.application.generation.agent.TestKoogAgent
import io.mocknest.application.usecase.HandleTestAgentRequest
import io.mocknest.domain.generation.TestAgentRequest
import io.mocknest.domain.model.HttpRequest
import io.mocknest.domain.model.HttpResponse
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatusCode
import org.springframework.util.LinkedMultiValueMap

private val logger = KotlinLogging.logger {}
private val mapper = jacksonObjectMapper()

class TestAgentRequestUseCase(
    private val testKoogAgent: TestKoogAgent
) : HandleTestAgentRequest {

    override fun invoke(path: String, httpRequest: HttpRequest): HttpResponse = runBlocking {
        logger.info { "Handling Test Agent request: ${httpRequest.method} $path" }

        when {
            path == "/chat" && httpRequest.method.name() == "POST" -> chat(httpRequest)
            path == "/health" && httpRequest.method.name() == "GET" -> health()
            else -> HttpResponse(HttpStatusCode.valueOf(404), body = "Path $path not found for Test Agent")
        }
    }

    private suspend fun chat(request: HttpRequest): HttpResponse {
        val body = request.body ?: throw IllegalArgumentException("Body must be a string")
        val testRequest = mapper.readValue(body, TestAgentRequest::class.java)
        
        logger.info { "Received test agent request: ${testRequest.instructions}" }
        
        val response = testKoogAgent.execute(testRequest)
        
        val statusCode = if (response.success) HttpStatusCode.valueOf(200) else HttpStatusCode.valueOf(500)
        return HttpResponse(
            statusCode,
            jsonHeaders(),
            mapper.writeValueAsString(response)
        )
    }

    private fun health(): HttpResponse {
        val body = mapOf(
            "status" to "healthy",
            "service" to "ai-test-agent",
            "message" to "Koog + Bedrock integration ready"
        )
        return HttpResponse(
            HttpStatusCode.valueOf(200),
            jsonHeaders(),
            mapper.writeValueAsString(body)
        )
    }

    private fun jsonHeaders() = LinkedMultiValueMap<String, String>().apply {
        add("Content-Type", "application/json")
    }
}
