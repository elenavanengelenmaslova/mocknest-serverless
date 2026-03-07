package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.usecases.AI_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.function.Function

private val logger = KotlinLogging.logger {}

@Configuration
class GenerationLambdaHandler(
    private val handleAIGenerationRequest: HandleAIGenerationRequest
) {
    @Bean
    fun generationRouter(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "Generation Lambda request: $httpMethod $path $headers" }
                when {
                    path.startsWith(AI_PREFIX) -> {
                        logger.debug { "Processing AI generation request $path" }
                        val aiPath = "/" + path.removePrefix(AI_PREFIX)
                        handleAIGenerationRequest(aiPath, createHttpRequest(aiPath))
                    }
                    else -> {
                        logger.warn { "Path $path not found in generation Lambda" }
                        HttpResponse(
                            HttpStatus.NOT_FOUND,
                            body = "Path $path not found"
                        )
                    }
                }
            }.let {
                APIGatewayProxyResponseEvent()
                    .withStatusCode(it.statusCode.value())
                    .withHeaders(it.headers?.toSingleValueMap())
                    .withBody(it.body.orEmpty())
            }
        }
    }

    private fun APIGatewayProxyRequestEvent.createHttpRequest(path: String): HttpRequest {
        return HttpRequest(
            method = HttpMethod.valueOf(httpMethod),
            headers = headers,
            path = path,
            queryParameters = queryStringParameters.orEmpty(),
            body = body
        )
    }
}
