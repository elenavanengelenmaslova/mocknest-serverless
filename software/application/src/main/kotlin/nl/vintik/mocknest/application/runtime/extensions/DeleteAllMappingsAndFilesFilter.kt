package nl.vintik.mocknest.application.runtime.extensions

import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.store.adapters.FILES_PREFIX
import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

class DeleteAllMappingsAndFilesFilter(
    private val storage: ObjectStorageInterface,
) : AdminRequestFilterV2 {
    override fun filter(
        request: Request,
        serveEvent: ServeEvent?,
    ): RequestFilterAction = runBlocking {
        logger.info { "URL is ${request.url}" }
        if (request.url.endsWith("mappings") && request.method == RequestMethod.DELETE) {
            logger.info { "Deleting all WireMock stub mappings and files" }
            // remove __files and reset from mappings from local file system
            storage.deleteMany(storage.listPrefix(FILES_PREFIX))
        }
        RequestFilterAction.continueWith(request)
    }

    override fun getName(): String = "delete-all-mapping-and-files-filter"
}