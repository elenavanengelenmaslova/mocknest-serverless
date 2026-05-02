package nl.vintik.mocknest.application.generation.usecases

import com.fasterxml.jackson.core.JsonProcessingException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.domain.generation.*

private val logger = KotlinLogging.logger {}

class AIGenerationRequestUseCase(
    private val generateFromSpecWithDescriptionUseCase: GenerateMocksFromSpecWithDescriptionUseCase
) : HandleAIGenerationRequest {

    override fun invoke(path: String, httpRequest: HttpRequest): HttpResponse = runBlocking {
        logger.info { "Handling AI generation request: ${httpRequest.method} $path" }

        runCatching {
            when {
                path == "/from-spec" && httpRequest.method.name == "POST" -> generateFromSpecWithDescription(
                    httpRequest
                )

                else -> HttpResponse(HttpStatusCode.NOT_FOUND, body = "Path $path not found for AI generation")
            }
        }.onFailure { exception ->
            logger.error(exception) { "Unexpected error processing AI generation request: $path" }
        }.getOrElse {
            serverError()
        }
    }

    private suspend fun generateFromSpecWithDescription(request: HttpRequest): HttpResponse {
        val body = request.body
            ?: return badRequest("Body must be a string")

        logger.info { "Parsing request body for AI generation with description" }

        val dto = runCatching {
            mapper.readValue(body, GenerateFromSpecWithDescriptionRequest::class.java)
        }.onFailure { e ->
            logger.warn(e) { "Failed to parse request body" }
        }.getOrElse {
            return badRequest("Invalid JSON in request body: ${(it as? JsonProcessingException)?.originalMessage ?: it.message}")
        }

        val specWithDescRequest = runCatching {
            SpecWithDescriptionRequest(
                namespace = dto.namespace,
                specificationContent = dto.specification,
                specificationUrl = dto.specificationUrl,
                format = dto.format,
                description = dto.description,
                options = dto.options
            )
        }.onFailure { e ->
            logger.warn(e) { "Invalid request parameters" }
        }.getOrElse {
            return badRequest(it.message ?: "Invalid request")
        }

        logger.info { "About to generate mocks from spec with description." }
        val result = generateFromSpecWithDescriptionUseCase.execute(specWithDescRequest)

        return if (result.success) {
            logger.info { "Generated successfully: ${result.mocks.size} mocks" }
            runCatching {
                ok(
                    MocksResponse(
                        mappings = result.mocks.map { mock ->
                            mapper.readTree(mock.wireMockMapping)
                        }
                    )
                )
            }.onFailure { e ->
                logger.error(e) { "Failed to serialize generation response" }
            }.getOrElse { serverError() }
        } else {
            logger.info { "Generation failed: ${result.error}." }
            HttpResponse(
                HttpStatusCode.INTERNAL_SERVER_ERROR,
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
        HttpStatusCode.OK,
        jsonHeaders(),
        mapper.writeValueAsString(body)
    )

    private fun badRequest(message: String): HttpResponse = HttpResponse(
        HttpStatusCode.BAD_REQUEST,
        jsonHeaders(),
        mapper.writeValueAsString(mapOf("error" to message))
    )

    private fun serverError(): HttpResponse = HttpResponse(
        HttpStatusCode.INTERNAL_SERVER_ERROR,
        jsonHeaders(),
        mapper.writeValueAsString(mapOf("error" to "Internal Server Error"))
    )

    private fun jsonHeaders(): Map<String, List<String>> = mapOf(
        "Content-Type" to listOf("application/json")
    )
}
