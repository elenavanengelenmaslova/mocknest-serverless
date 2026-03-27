package nl.vintik.mocknest.application.generation.usecases

import com.fasterxml.jackson.core.JsonProcessingException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.generation.*
import org.springframework.http.HttpStatusCode
import org.springframework.util.LinkedMultiValueMap

private val logger = KotlinLogging.logger {}

class AIGenerationRequestUseCase(
    private val generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase
) : HandleAIGenerationRequest {

    override fun invoke(path: String, httpRequest: HttpRequest): HttpResponse = runBlocking {
        logger.info { "Handling AI generation request: ${httpRequest.method} $path" }

        runCatching {
            when {
                path == "/from-spec" && httpRequest.method.name() == "POST" -> generateFromSpecWithDescription(
                    httpRequest
                )

                else -> HttpResponse(HttpStatusCode.valueOf(404), body = "Path $path not found for AI generation")
            }
        }.onFailure {
            logger.error(it) { "Error processing AI generation request: $path: $it" }
        }.getOrElse { exception ->
            val statusCode = when (exception) {
                is IllegalArgumentException, is JsonProcessingException -> 400
                else -> 500
            }
            val defaultMessage = if (statusCode == 400) "Bad Request" else "Internal Server Error"
            HttpResponse(
                HttpStatusCode.valueOf(statusCode),
                jsonHeaders(),
                mapper.writeValueAsString(mapOf("error" to (exception.message ?: defaultMessage)))
            )
        }
    }

    private suspend fun generateFromSpecWithDescription(request: HttpRequest): HttpResponse {
        val body = requireNotNull(request.body) { "Body must be a string" }
        logger.info { "Parsing request body for AI generation with description" }
        val dto = mapper.readValue(body, GenerateFromSpecWithDescriptionRequest::class.java)

        val specWithDescRequest = SpecWithDescriptionRequest(
            namespace = dto.namespace,
            specificationContent = dto.specification,
            specificationUrl = dto.specificationUrl,
            format = dto.format,
            description = dto.description,
            options = dto.options
        )
        logger.info { "About to generate mocks from spec with description." }
        val result = generateFromSpecWithDescriptionUseCase.execute(specWithDescRequest)

        return if (result.success) {
            logger.info { "Generated successfully: ${result.mocks.size} mocks" }
            ok(
                MocksResponse(
                    mappings = result.mocks.map { mock ->
                        mapper.readTree(mock.wireMockMapping)
                    }
                )
            )
        } else {
            logger.info { "Generation failed: ${result.error}." }
            HttpResponse(
                HttpStatusCode.valueOf(500),
                jsonHeaders(),
                mapper.writeValueAsString(
                    GenerationResponse(
                        jobId = result.jobId,
                        namespace = dto.namespace.toPrefix(),
                        status = "FAILED",
                        error = result.error
                    )
                )
            )
        }
    }

    private fun ok(body: Any): HttpResponse = HttpResponse(
        HttpStatusCode.valueOf(200),
        jsonHeaders(),
        mapper.writeValueAsString(body)
    )

    private fun jsonHeaders() = LinkedMultiValueMap<String, String>().apply {
        add("Content-Type", "application/json")
    }
}
