package nl.vintik.mocknest.infra.aws.generation.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.application.runtime.usecases.AI_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.HandleAIGenerationRequest
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import nl.vintik.mocknest.infra.aws.generation.di.generationModule
import nl.vintik.mocknest.infra.aws.generation.snapstart.GenerationPrimingHook
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * Direct AWS Lambda handler for the Generation function.
 *
 * Replaces the previous Spring Cloud Function `FunctionInvoker` + `generationRouter` bean
 * pattern with a direct [RequestHandler] implementation using Koin for DI.
 *
 * Koin is initialized once per Lambda container lifecycle in the companion object init
 * block. Priming happens eagerly — before the SnapStart snapshot is taken — not lazily
 * on first request.
 */
class GenerationLambdaHandler :
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>,
    KoinComponent {

    companion object {
        init {
            KoinBootstrap.init(listOf(coreModule(), generationModule()))
            // Explicit priming — runs BEFORE SnapStart snapshot, not lazily on first request
            KoinBootstrap.getKoin().get<GenerationPrimingHook>().onApplicationReady()
        }
    }

    private val handleAIGenerationRequest: HandleAIGenerationRequest by inject()
    private val getAIHealth: GetAIHealth by inject()

    override fun handleRequest(
        event: APIGatewayProxyRequestEvent,
        context: Context,
    ): APIGatewayProxyResponseEvent {
        with(event) {
            logger.info { "Generation Lambda request: $httpMethod $path" }
            val response = when {
                path == "${AI_PREFIX}health" -> {
                    logger.debug { "Processing AI health check request" }
                    getAIHealth()
                }
                path.startsWith(AI_PREFIX) -> {
                    logger.debug { "Processing AI generation request $path" }
                    val aiPath = "/" + path.removePrefix(AI_PREFIX)
                    handleAIGenerationRequest(aiPath, createHttpRequest(aiPath))
                }
                else -> {
                    logger.warn { "Path $path not found in generation Lambda" }
                    HttpResponse(
                        HttpStatusCode.NOT_FOUND,
                        body = "Path $path not found",
                    )
                }
            }

            return APIGatewayProxyResponseEvent()
                .withStatusCode(response.statusCode.value())
                .withHeaders(response.headers?.mapValues { it.value.firstOrNull() }?.filterValues { it != null }?.mapValues { it.value!! })
                .withBody(response.body.orEmpty())
        }
    }

    private fun APIGatewayProxyRequestEvent.createHttpRequest(path: String): HttpRequest =
        HttpRequest(
            method = HttpMethod.resolve(httpMethod),
            headers = headers.orEmpty(),
            path = path,
            queryParameters = queryStringParameters.orEmpty(),
            body = body,
        )
}
