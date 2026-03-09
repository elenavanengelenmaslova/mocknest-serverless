package nl.vintik.mocknest.infra.aws.generation.health

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.generation.usecases.GetAIHealth
import nl.vintik.mocknest.domain.core.HttpResponse
import nl.vintik.mocknest.infra.aws.generation.ai.config.InferencePrefixResolver
import nl.vintik.mocknest.infra.aws.generation.ai.config.ModelConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of AI health check use case.
 * 
 * Returns comprehensive health information about AI features including
 * model configuration, inference settings, and operational status.
 */
@Component
class AIHealthUseCase(
    private val modelConfig: ModelConfiguration,
    private val prefixResolver: InferencePrefixResolver,
    @param:Value("\${bedrock.inference.mode:AUTO}") private val inferenceMode: String
) : GetAIHealth {
    
    override fun invoke(): HttpResponse {
        logger.debug { "Checking AI health" }
        
        val region = System.getenv("AWS_REGION") ?: "unknown"
        
        val response = AIHealthResponse(
            status = "healthy",
            timestamp = Instant.now().toString(),
            region = region,
            ai = AIHealth(
                modelName = modelConfig.getModelName(),
                inferencePrefix = modelConfig.getConfiguredPrefix(),
                inferenceMode = inferenceMode.uppercase(),
                lastInvocationSuccess = null, // Will be updated after first invocation in future enhancement
                officiallySupported = modelConfig.isOfficiallySupported()
            )
        )
        
        val jsonBody = mapper.writeValueAsString(response)
        
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("Content-Type", "application/json")
        
        return HttpResponse(
            statusCode = HttpStatus.OK,
            headers = headers,
            body = jsonBody
        )
    }
}
