package nl.vintik.mocknest.application.generation.usecases

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.agent.TestKoogAgent
import nl.vintik.mocknest.application.runtime.usecases.HandleTestAgentRequest
import nl.vintik.mocknest.domain.generation.TestAgentRequest
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
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

        runCatching {
            when (path) {
                "/chat" if httpRequest.method.name() == "POST" -> chat(httpRequest)
                "/health" if httpRequest.method.name() == "GET" -> health()
                else -> HttpResponse(HttpStatusCode.valueOf(404), body = "Path $path not found for Test Agent")
            }
        }.onFailure { e ->
            logger.error(e) { "Error processing Test Agent request: $path" }
        }.getOrElse { e ->
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(mapOf("error" to (e.message ?: "Internal Server Error")))
            )
        }
    }

    private suspend fun chat(request: HttpRequest): HttpResponse {
        val body = requireNotNull(request.body) { "Body must be a string" }
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
