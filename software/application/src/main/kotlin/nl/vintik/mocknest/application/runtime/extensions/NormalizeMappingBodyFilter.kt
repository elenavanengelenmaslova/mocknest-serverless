package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.runtime.store.adapters.FILES_PREFIX
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {}

class NormalizeMappingBodyFilter(
    private val storage: ObjectStorageInterface,
) : AdminRequestFilterV2 {
    override fun filter(
        request: Request,
        serveEvent: ServeEvent?,
    ): RequestFilterAction = runBlocking {
         if (isSaveMapping(request)) {
            logger.info { "Creating or importing WireMock stub mapping(s)" }
            val normalized = normalizeMappingToBodyFile(request.bodyAsString)
            RequestFilterAction.continueWith(rebuildWithBody(request, normalized))

        } else RequestFilterAction.continueWith(request)
    }

    internal fun isSaveMapping(request: Request): Boolean {
        // Match POST .../__admin/mappings or .../__admin/mappings/import (absolute URL)
        val postRegex = Regex(
            pattern = ".*mappings(/import)?$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val isPostSaveOrImport = request.method == RequestMethod.POST && postRegex.matches(request.url)

        // Match PUT .../__admin/mappings/{uuid}
        val uuidRe = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
        val putRegex = Regex(
            pattern = ".*mappings/$uuidRe$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val isPutUpdate = request.method == RequestMethod.PUT && putRegex.matches(request.url)

        return isPostSaveOrImport || isPutUpdate
    }

    internal fun rebuildWithBody(original: Request, newBodyJson: String): Request {
        val bytes = newBodyJson.toByteArray(UTF_8)

        return ImmutableRequest.create()
            .withAbsoluteUrl(original.absoluteUrl)
            .withMethod(original.method)
            .withProtocol(original.protocol)
            .withClientIp(original.clientIp)
            .withHeaders(original.headers)
            .withBody(bytes)
            .withMultipart(original.isMultipart)
            .withBrowserProxyRequest(original.isBrowserProxyRequest)
            .build()
    }

    internal suspend fun normalizeMappingToBodyFile(mappingJson: String): String {
        val root = mapper.readTree(mappingJson) as ObjectNode

        if (root.has("mappings") && root["mappings"].isArray) {
            val mappings = root["mappings"] as ArrayNode
            for (i in 0 until mappings.size()) {
                val mapping = mappings[i]
                if (mapping is ObjectNode) {
                    normalizeSingleMapping(mapping)
                }
            }
        } else {
            normalizeSingleMapping(root)
        }

        return mapper.writeValueAsString(root)
    }

    private suspend fun normalizeSingleMapping(root: ObjectNode) {
        // Get or create response without overwriting existing one
        val response = (root["response"] as? ObjectNode)
            ?: root.putObject("response")

        // If already bodyFileName, or mapping is transient (persistent=false), nothing to do
        // Default to persistent=true for admin API requests
        val persistent = root["persistent"]?.asBoolean() ?: true
        if (response.has("bodyFileName") || !persistent) return

        val bodyNode = response.remove("body")
        val base64Node = response.remove("base64Body")
        val jsonBodyNode = response.remove("jsonBody")

        if (bodyNode == null && base64Node == null && jsonBodyNode == null) return

        // Ensure mapping has an id to derive file name
        val mappingId = root["id"]?.asText() ?: UUID.randomUUID().toString().also { root.put("id", it) }
        val isBinary = base64Node != null

        val fileName = "$mappingId${if (isBinary) ".bin" else ".json"}"
        val fullFileName = "$FILES_PREFIX$fileName"
        // Persist into FILES store under the relative file name
        when {
            base64Node != null -> storage.save(fullFileName, base64Node.asText())
            jsonBodyNode != null -> storage.save(fullFileName, mapper.writeValueAsString(jsonBodyNode))
            else -> storage.save(fullFileName, bodyNode.asText())
        }

        // Get or create headers without overwriting existing headers
        val headers = (response["headers"] as? ObjectNode)
            ?: response.putObject("headers")

        // Only set default Content-Type when it's missing
        if (!headers.has("Content-Type")) {
            headers.put("Content-Type", if (isBinary) "application/octet-stream" else "application/json")
        }

        response.put("bodyFileName", fileName)
    }

    override fun getName(): String = "normalize-mapping-body-filter"

}