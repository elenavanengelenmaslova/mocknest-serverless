package nl.vintik.mocknest.infra.aws.generation.ai.eval.evaluators

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.application.core.mapper

private val logger = KotlinLogging.logger {}

fun extractResponseBody(mapping: JsonNode): JsonNode? {
    val bodyString = mapping.path("response").path("body").asText(null) ?: return null
    return runCatching {
        mapper.readTree(bodyString)
    }.onFailure { e ->
        logger.debug(e) { "Could not parse response body as JSON" }
    }.getOrNull()
}
