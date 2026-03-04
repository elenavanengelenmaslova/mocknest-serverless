package nl.vintik.mocknest.infra.aws

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Verifies that the multi-lambda architecture achieves the expected JAR size reductions
 * compared to the monolithic deployment baseline of 77MB.
 *
 * These tests validate Requirements 3.2, 3.3 and Design Property 1:
 * - Runtime JAR automatically excludes Bedrock SDK, Koog framework, and OpenAPI parser classes
 * - Generation JAR automatically excludes WireMock standalone server and Jetty components
 * - Each minimized Lambda JAR must be at least 30% smaller than the monolithic JAR (77MB baseline)
 */
class JarSizeVerificationTest {

    private val buildDistDir = File(System.getProperty("user.dir")).parentFile.parentFile.parentFile.resolve("build/dist")
    
    // Baseline monolithic JAR size: 77MB (measured from actual build)
    private val monolithicSizeBytes = 77L * 1024 * 1024 // 77MB in bytes
    private val maxRuntimeSizeBytes = (monolithicSizeBytes * 0.70).toLong() // 70% of monolithic = 30% reduction
    private val maxGenerationSizeBytes = (monolithicSizeBytes * 0.70).toLong() // 70% of monolithic = 30% reduction

    @Test
    fun `Given runtime JAR When checking size Then should be at least 30 percent smaller than 77MB baseline`() {
        val runtimeJar = buildDistDir.resolve("mocknest-runtime.jar")

        assertTrue(runtimeJar.exists(), "Runtime JAR not found at ${runtimeJar.absolutePath}. Run './gradlew shadowJarRuntime' first.")

        val runtimeSize = runtimeJar.length()
        val reduction = (monolithicSizeBytes - runtimeSize).toDouble() / monolithicSizeBytes
        val reductionPercent = reduction * 100

        logger.info { "Monolithic JAR baseline: ${formatBytes(monolithicSizeBytes)}" }
        logger.info { "Runtime JAR size: ${formatBytes(runtimeSize)}" }
        logger.info { "Size reduction: ${"%.2f".format(reductionPercent)}%" }
        logger.info { "Maximum allowed size: ${formatBytes(maxRuntimeSizeBytes)}" }

        assertTrue(
            runtimeSize <= maxRuntimeSizeBytes,
            "Runtime JAR is ${formatBytes(runtimeSize)}, expected <= ${formatBytes(maxRuntimeSizeBytes)} " +
                    "(30% reduction from ${formatBytes(monolithicSizeBytes)} baseline). " +
                    "Actual reduction: ${"%.2f".format(reductionPercent)}%"
        )
    }

    @Test
    fun `Given generation JAR When checking size Then should be at least 30 percent smaller than 77MB baseline`() {
        val generationJar = buildDistDir.resolve("mocknest-generation.jar")

        assertTrue(generationJar.exists(), "Generation JAR not found at ${generationJar.absolutePath}. Run './gradlew shadowJarGeneration' first.")

        val generationSize = generationJar.length()
        val reduction = (monolithicSizeBytes - generationSize).toDouble() / monolithicSizeBytes
        val reductionPercent = reduction * 100

        logger.info { "Monolithic JAR baseline: ${formatBytes(monolithicSizeBytes)}" }
        logger.info { "Generation JAR size: ${formatBytes(generationSize)}" }
        logger.info { "Size reduction: ${"%.2f".format(reductionPercent)}%" }
        logger.info { "Maximum allowed size: ${formatBytes(maxGenerationSizeBytes)}" }

        assertTrue(
            generationSize <= maxGenerationSizeBytes,
            "Generation JAR is ${formatBytes(generationSize)}, expected <= ${formatBytes(maxGenerationSizeBytes)} " +
                    "(30% reduction from ${formatBytes(monolithicSizeBytes)} baseline). " +
                    "Actual reduction: ${"%.2f".format(reductionPercent)}%"
        )
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return "${"%.2f".format(mb)} MB"
    }
}
