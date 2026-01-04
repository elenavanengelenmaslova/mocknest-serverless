package io.mocknest.application.wiremock.mappings

import com.github.tomakehurst.wiremock.common.ClasspathFileSource
import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource
import com.github.tomakehurst.wiremock.standalone.MappingsSource
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Composite MappingsSource that delegates to a primary source (object storage)
 * and then to WireMock's built-in classpath loader rooted at [rootDir].
 *
 * - Load order: primary first, then secondary (classpath).
 * - Persistence (save/remove) delegates ONLY to the primary to avoid
 *   filesystem/classpath writes in serverless environments.
 */
class CompositeMappingsSource(
    private val primary: MappingsSource,
    rootDir: String,
) : MappingsSource by primary {

    // Secondary loader: standard WireMock classpath loader rooted at mocknest
    private val secondary = JsonFileMappingsSource(
        ClasspathFileSource(rootDir),
        FilenameMaker()
    )

    override fun loadMappingsInto(stubMappings: StubMappings) {
        logger.info { "Load composite mappings" }
        // Load from storage first
        runCatching { primary.loadMappingsInto(stubMappings) }
            .onFailure { e -> logger.error(e) { "Primary mappings load failed; continuing with classpath" } }

        // Then load from classpath (mocknest/mappings)
        runCatching { secondary.loadMappingsInto(stubMappings) }
            .onFailure { e -> logger.error(e) { "Classpath mappings load failed; continuing" } }
    }
}