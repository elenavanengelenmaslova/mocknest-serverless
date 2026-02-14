package io.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.mocknest.domain.model.HttpRequest
import io.mocknest.domain.model.HttpResponse
import io.mocknest.application.usecase.ADMIN_PREFIX
import io.mocknest.application.usecase.HandleAdminRequest
import io.mocknest.application.usecase.HandleClientRequest
import io.mocknest.application.usecase.HandleAIGenerationRequest
import io.mocknest.application.usecase.HandleTestAgentRequest
import io.mocknest.application.usecase.MOCKNEST_PREFIX
import io.mocknest.application.usecase.AI_PREFIX
import io.mocknest.application.usecase.TEST_AI_PREFIX
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.function.Function

private val logger = KotlinLogging.logger {}

@Configuration
class MockNestLambdaHandler(
    private val handleClientRequest: HandleClientRequest,
    private val handleAdminRequest: HandleAdminRequest,
    private val handleAIGenerationRequest: HandleAIGenerationRequest,
    private val handleTestAgentRequest: HandleTestAgentRequest,
) {
    @Bean
    fun router(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "MockNest request: $httpMethod $path $headers" }
                when {
                    path.startsWith(ADMIN_PREFIX) -> {
                        logger.debug { "Processing admin request $path" }
                        val adminPath = path.removePrefix(ADMIN_PREFIX)
                        handleAdminRequest(adminPath, createHttpRequest(adminPath))
                    }
                    path.startsWith(MOCKNEST_PREFIX) -> {
                        logger.info { "Processing client request $path." }
                        handleClientRequest(createHttpRequest(path.removePrefix(MOCKNEST_PREFIX)))
                    }
                    path.startsWith(AI_PREFIX) -> {
                        logger.info { "Processing AI generation request $path" }
                        val aiPath = "/" + path.removePrefix(AI_PREFIX)
                        handleAIGenerationRequest(aiPath, createHttpRequest(aiPath))
                    }
                    path.startsWith(TEST_AI_PREFIX) -> {
                        logger.info { "Processing Test AI agent request $path" }
                        val testPath = "/" + path.removePrefix(TEST_AI_PREFIX)
                        handleTestAgentRequest(testPath, createHttpRequest(testPath))
                    }
                    else -> {
                        logger.debug { "Did not match admin or mocknest request prefix: $path" }
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
        val request = HttpRequest(
            method = HttpMethod.valueOf(httpMethod),
            headers = headers,
            path = path,
            queryParameters = queryStringParameters.orEmpty(),
            body = body
        )
        return request
    }
}