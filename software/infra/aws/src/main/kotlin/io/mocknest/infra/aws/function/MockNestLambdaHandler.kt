package io.mocknest.infra.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.mocknest.domain.model.HttpRequest
import io.mocknest.domain.model.HttpResponse
import io.mocknest.application.usecase.ADMIN_PREFIX
import io.mocknest.application.usecase.HandleAdminRequest
import io.mocknest.application.usecase.HandleClientRequest
import io.mocknest.application.usecase.MOCKNEST_PREFIX
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
                        logger.debug { "Processing client request $path" }
                        handleClientRequest(createHttpRequest(path.removePrefix(MOCKNEST_PREFIX)))
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
                    .withBody(it.body?.toString().orEmpty())
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