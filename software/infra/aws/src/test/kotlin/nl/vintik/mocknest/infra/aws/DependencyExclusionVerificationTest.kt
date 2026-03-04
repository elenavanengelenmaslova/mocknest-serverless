package nl.vintik.mocknest.infra.aws

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Verifies that the shadow JARs are built correctly with expected classes and reasonable size.
 * 
 * Note: Dependency exclusion tests (checking that unused dependencies are removed) will be
 * implemented after the multi-lambda architecture refactoring is complete. Currently, both
 * runtime and generation code are in the same module, so minimize() cannot properly exclude
 * generation-only dependencies from the runtime JAR.
 */
class DependencyExclusionVerificationTest {

    private val buildDistDir = File(System.getProperty("user.dir")).parentFile.parentFile.parentFile.resolve("build/dist")
    private val maxJarSizeMB = 100 // Maximum expected JAR size in MB

    @Test
    fun `Given runtime JAR When checking Then should exist and contain expected classes`() {
        val runtimeJar = buildDistDir.resolve("mocknest-runtime.jar")

        assertTrue(runtimeJar.exists(), "Runtime JAR not found at ${runtimeJar.absolutePath}. Run './gradlew shadowJarRuntime' first.")

        JarFile(runtimeJar).use { jarFile ->
            // Verify essential runtime classes are present
            val runtimeClasses = jarFile.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.endsWith(".class") }
                .filter { 
                    it.name.contains("nl/vintik/mocknest/infra/aws/runtime/") ||
                    it.name.contains("nl/vintik/mocknest/application/runtime/")
                }
                .map { it.name }
                .toList()

            assertTrue(
                runtimeClasses.isNotEmpty(),
                "Runtime JAR should contain runtime application classes"
            )
            
            logger.info { "Runtime JAR contains ${runtimeClasses.size} runtime classes" }
        }
    }

    @Test
    fun `Given runtime JAR When checking size Then should be under maximum threshold`() {
        val runtimeJar = buildDistDir.resolve("mocknest-runtime.jar")

        assertTrue(runtimeJar.exists(), "Runtime JAR not found at ${runtimeJar.absolutePath}. Run './gradlew shadowJarRuntime' first.")

        val jarSizeMB = runtimeJar.length() / (1024.0 * 1024.0)
        logger.info { "Runtime JAR size: %.2f MB".format(jarSizeMB) }

        assertTrue(
            jarSizeMB <= maxJarSizeMB,
            "Runtime JAR size (%.2f MB) exceeds maximum threshold (%d MB)".format(jarSizeMB, maxJarSizeMB)
        )
    }

    @Test
    fun `Given generation JAR When checking Then should exist and contain expected classes`() {
        val generationJar = buildDistDir.resolve("mocknest-generation.jar")

        assertTrue(generationJar.exists(), "Generation JAR not found at ${generationJar.absolutePath}. Run './gradlew shadowJarGeneration' first.")

        JarFile(generationJar).use { jarFile ->
            // Verify essential generation classes are present
            val generationClasses = jarFile.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.endsWith(".class") }
                .filter { 
                    it.name.contains("nl/vintik/mocknest/infra/aws/generation/") ||
                    it.name.contains("nl/vintik/mocknest/application/generation/")
                }
                .map { it.name }
                .toList()

            assertTrue(
                generationClasses.isNotEmpty(),
                "Generation JAR should contain generation application classes"
            )
            
            logger.info { "Generation JAR contains ${generationClasses.size} generation classes" }
        }
    }

    @Test
    fun `Given generation JAR When checking size Then should be under maximum threshold`() {
        val generationJar = buildDistDir.resolve("mocknest-generation.jar")

        assertTrue(generationJar.exists(), "Generation JAR not found at ${generationJar.absolutePath}. Run './gradlew shadowJarGeneration' first.")

        val jarSizeMB = generationJar.length() / (1024.0 * 1024.0)
        logger.info { "Generation JAR size: %.2f MB".format(jarSizeMB) }

        assertTrue(
            jarSizeMB <= maxJarSizeMB,
            "Generation JAR size (%.2f MB) exceeds maximum threshold (%d MB)".format(jarSizeMB, maxJarSizeMB)
        )
    }
}
