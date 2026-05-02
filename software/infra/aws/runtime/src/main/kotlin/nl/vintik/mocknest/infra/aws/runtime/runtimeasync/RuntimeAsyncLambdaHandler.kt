package nl.vintik.mocknest.infra.aws.runtime.runtimeasync

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.infra.aws.core.di.KoinBootstrap
import nl.vintik.mocknest.infra.aws.core.di.coreModule
import nl.vintik.mocknest.infra.aws.runtime.di.asyncModule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * Direct AWS Lambda handler for the RuntimeAsync function.
 *
 * Processes SQS events containing webhook dispatch requests. Replaces the previous
 * Spring Cloud Function `FunctionInvoker` + `runtimeAsyncRouter` bean pattern with
 * a direct [RequestHandler] implementation using Koin for DI.
 *
 * Koin is initialized once per Lambda container lifecycle in the companion object init
 * block. Priming happens eagerly — before the SnapStart snapshot is taken.
 */
class RuntimeAsyncLambdaHandler :
    RequestHandler<SQSEvent, Unit>,
    KoinComponent {

    companion object {
        init {
            KoinBootstrap.init(listOf(coreModule(), asyncModule()))
            // Explicit priming — runs BEFORE SnapStart snapshot, not lazily on first request
            KoinBootstrap.getKoin().get<RuntimeAsyncPrimingHook>().onApplicationReady()
        }
    }

    private val runtimeAsyncHandler: RuntimeAsyncHandler by inject()

    override fun handleRequest(event: SQSEvent, context: Context) {
        logger.info { "RuntimeAsync Lambda received ${event.records?.size ?: 0} SQS records" }
        runtimeAsyncHandler.handle(event)
    }
}
