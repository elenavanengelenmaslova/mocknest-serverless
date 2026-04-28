package nl.vintik.mocknest.infra.aws.runtime.snapstart

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.crac.Context
import org.crac.Core
import org.crac.Resource

private val logger = KotlinLogging.logger {}

/**
 * CRaC lifecycle hook that reloads WireMock mappings from S3 after SnapStart restore.
 *
 * When AWS Lambda restores a snapshot, the in-memory WireMock stub store contains
 * stale data from snapshot time. This hook calls [WireMockServer.resetToDefaultMappings]
 * in [afterRestore], which clears the in-memory stub store and reloads all mappings
 * from S3 via [ObjectStorageMappingsSource.loadMappingsInto].
 *
 * **Important**: We use [WireMockServer.resetToDefaultMappings] (not `resetMappings`).
 * `resetMappings` calls `MappingsSaver.removeAll()` which deletes all mappings from S3.
 * `resetToDefaultMappings` clears in-memory state and reloads from the MappingsSource.
 *
 * CRaC registration is done explicitly from the Lambda handler init block after Koin
 * initialization, replacing the previous `@PostConstruct` Spring lifecycle callback.
 */
class RuntimeMappingReloadHook(
    private val wireMockServer: WireMockServer
) : Resource {

    fun register() {
        Core.getGlobalContext().register(this)
    }

    override fun beforeCheckpoint(context: Context<out Resource>?) {
        // No-op: nothing to do before snapshot creation
    }

    override fun afterRestore(context: Context<out Resource>?) {
        runCatching {
            logger.info { "SnapStart restore detected — reloading WireMock mappings from S3" }
            wireMockServer.resetToDefaultMappings()
            logger.info { "WireMock mappings reloaded successfully after SnapStart restore" }
        }.onFailure { exception ->
            logger.error(exception) { "SnapStart restore — failed to reload WireMock mappings from S3" }
        }
    }
}
