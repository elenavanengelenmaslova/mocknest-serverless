package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.usecases.ADMIN_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.application.runtime.usecases.MOCKNEST_PREFIX
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.function.Function

private val logger = KotlinLogging.logger {}

@Configuration
class RuntimeLambdaHandler(
    private val handleClientRequest: HandleClientRequest,
    private val handleAdminRequest: HandleAdminRequest
) {
    @Bean
    fun runtimeRouter(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "Runtime Lambda request: $httpMethod $path $headers" }
                when {
                    path.startsWith(ADMIN_PREFIX) -> {
                        logger.debug { "Processing admin request $path" }
                        val adminPath = path.removePrefix(ADMIN_PREFIX)
                        handleAdminRequest(adminPath, createHttpRequest(adminPath))
                    }
                    path.startsWith(MOCKNEST_PREFIX) -> {
                        logger.info { "Processing client request $path" }
                        handleClientRequest(createHttpRequest(path.removePrefix(MOCKNEST_PREFIX)))
                    }
                    else -> {
                        logger.warn { "Path $path not found in runtime Lambda" }
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
