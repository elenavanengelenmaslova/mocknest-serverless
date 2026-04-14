package nl.vintik.mocknest.infra.aws.runtime.snapstart

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.crac.Context
import org.crac.Core
import org.crac.Resource
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * CRaC lifecycle hook that reloads WireMock mappings from S3 after SnapStart restore.
 *
 * When AWS Lambda restores a snapshot, the in-memory WireMock stub store contains
 * stale data from snapshot time. This hook calls [WireMockServer.resetMappings] in
 * [afterRestore], which triggers [ObjectStorageMappingsSource.loadMappingsInto] to
 * reload all mappings from S3 into WireMock's in-memory stub store.
 */
@Component
@Profile("runtime")
class RuntimeMappingReloadHook(
    private val wireMockServer: WireMockServer
) : Resource {

    @PostConstruct
    fun register() {
        Core.getGlobalContext().register(this)
    }

    override fun beforeCheckpoint(context: Context<out Resource>?) {
        // No-op: nothing to do before snapshot creation
    }

    override fun afterRestore(context: Context<out Resource>?) {
        logger.info { "SnapStart restore detected — reloading WireMock mappings from S3" }
        wireMockServer.resetMappings()
        logger.info { "WireMock mappings reloaded successfully after SnapStart restore" }
    }
}
