package io.mocknest.application.wiremock.extensions

import io.mocknest.application.interfaces.storage.ObjectStorageInterface
import io.mocknest.application.usecase.mapper
import io.mocknest.application.wiremock.store.adapters.FILES_PREFIX
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
            logger.info { "Creating new WireMock stub mapping" }
            val normalized = normalizeMappingToBodyFile(request.bodyAsString)
            RequestFilterAction.continueWith(rebuildWithBody(request, normalized))

        } else RequestFilterAction.continueWith(request)
    }

    internal fun isSaveMapping(request: Request): Boolean {
        // Match POST .../__admin/mappings (absolute URL)
        val postRegex = Regex(
            pattern = ".*mappings$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val isPostCreate = request.method == RequestMethod.POST && postRegex.matches(request.url)

        // Match PUT .../__admin/mappings/{uuid}
        val uuidRe = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
        val putRegex = Regex(
            pattern = ".*mappings/$uuidRe$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val isPutUpdate = request.method == RequestMethod.PUT && putRegex.matches(request.url)

        return isPostCreate || isPutUpdate
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
        // Get or create response without overwriting existing one
        val response = (root["response"] as? ObjectNode)
            ?: root.putObject("response")

        // If already bodyFileName, or mapping is transient (persistent=false), nothing to do
        val persistent = root["persistent"]?.asBoolean() ?: false
        if (response.has("bodyFileName") || !persistent) return mappingJson

        val bodyNode = response.remove("body")
        val base64Node = response.remove("base64Body")

        if (bodyNode == null && base64Node == null) return mappingJson

        // Ensure mapping has an id to derive file name
        val mappingId = root["id"]?.asText() ?: UUID.randomUUID().toString().also { root.put("id", it) }
        val isBinary = base64Node != null

        val fileName = "$mappingId${if (isBinary) ".bin" else ".json"}"
        val fullFileName = "$FILES_PREFIX$fileName"
        // Persist into FILES store under the relative file name
        if (isBinary) {
            storage.save(fullFileName, base64Node.asText())
        } else {
            val text = bodyNode.asText()
            storage.save(fullFileName, text)
        }

        // Get or create headers without overwriting existing headers
        val headers = (response["headers"] as? ObjectNode)
            ?: response.putObject("headers")

        // Only set default Content-Type when it's missing
        if (!headers.has("Content-Type")) {
            headers.put("Content-Type", if (isBinary) "application/octet-stream" else "application/json")
        }

        response.put("bodyFileName", fileName)
        return mapper.writeValueAsString(root)
    }

    override fun getName(): String = "normalize-mapping-body-filter"

}