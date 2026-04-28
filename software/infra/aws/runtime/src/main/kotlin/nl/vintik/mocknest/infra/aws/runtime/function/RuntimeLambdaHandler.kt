package nl.vintik.mocknest.infra.aws.runtime.function

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.runtime.usecases.ADMIN_PREFIX
import nl.vintik.mocknest.application.runtime.usecases.GetRuntimeHealth
import nl.vintik.mocknest.application.runtime.usecases.HandleAdminRequest
import nl.vintik.mocknest.application.runtime.usecases.HandleClientRequest
import nl.vintik.mocknest.application.runtime.usecases.MOCKNEST_PREFIX
import nl.vintik.mocknest.domain.core.HttpMethod
import nl.vintik.mocknest.domain.core.HttpRequest
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.domain.core.HttpStatusCode
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.runtime.di.runtimeModule
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook
import nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimePrimingHook
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * Direct AWS Lambda handler for the Runtime function.
 *
 * Replaces the previous Spring Cloud Function `FunctionInvoker` + `runtimeRouter` bean
 * pattern with a direct [RequestHandler] implementation using Koin for DI.
 *
 * Koin is initialized once per Lambda container lifecycle in the companion object init
 * block. Priming and CRaC registration happen eagerly — before the SnapStart snapshot
 * is taken — not lazily on first request.
 */
class RuntimeLambdaHandler :
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>,
    KoinComponent {

    companion object {
        init {
            KoinBootstrap.init(listOf(coreModule(), runtimeModule()))
            // Explicit priming — runs BEFORE SnapStart snapshot, not lazily on first request
            KoinBootstrap.getKoin().get<RuntimePrimingHook>().onApplicationReady()
            // CRaC registration — enables afterRestore() for mock reload after SnapStart restore
            KoinBootstrap.getKoin().get<RuntimeMappingReloadHook>().register()
        }
    }

    private val handleClientRequest: HandleClientRequest by inject()
    private val handleAdminRequest: HandleAdminRequest by inject()
    private val getRuntimeHealth: GetRuntimeHealth by inject()

    override fun handleRequest(
        event: APIGatewayProxyRequestEvent,
        context: Context,
    ): APIGatewayProxyResponseEvent {
        with(event) {
            logger.info { "Runtime Lambda request: $httpMethod $path $headers" }
            val response = when {
                path == "${ADMIN_PREFIX}health" -> {
                    logger.debug { "Processing health check request" }
                    getRuntimeHealth()
                }
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
                        HttpStatusCode.NOT_FOUND,
                        body = "Path $path not found",
                    )
                }
            }

            return APIGatewayProxyResponseEvent()
                .withStatusCode(response.statusCode.value())
                .withHeaders(response.headers?.mapValues { it.value.first() })
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
