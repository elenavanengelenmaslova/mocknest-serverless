package nl.vintik.mocknest.infra.aws.deployment

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Validates that the shadow jar does not exceed the 90MB size limit.
 *
 * This test prevents dependency bloat from new libraries (e.g., ktor-client, graphql-java)
 * by asserting the packaged jar stays under the AWS Lambda deployment size constraint.
 *
 * The jar is built by the shadowJar task which is a dependency of the test task,
 * so it is already available when this test runs.
 */
@Tag("integration")
class JarSizeValidationTest {

    companion object {
        private const val MAX_JAR_SIZE_MB = 90
        private const val MAX_JAR_SIZE_BYTES = MAX_JAR_SIZE_MB * 1024L * 1024L
    }

    @Test
    fun `Given shadow jar When checking size Then jar is under 90MB limit`() {
        // Given - find the jar relative to project root
        val projectRoot = findProjectRoot()
        val jarFile = File(projectRoot, "build/dist/mocknest-serverless.jar")

        assertTrue(
            jarFile.exists(),
            "Shadow jar not found at ${jarFile.absolutePath}. " +
                "Ensure shadowJar task has run (it should be a test task dependency)."
        )

        // When
        val jarSizeBytes = jarFile.length()
        val jarSizeMB = jarSizeBytes.toDouble() / (1024 * 1024)
        val percentOfLimit = (jarSizeBytes.toDouble() / MAX_JAR_SIZE_BYTES * 100)

        println("Jar size: %.2f MB (%.1f%% of %d MB limit)".format(jarSizeMB, percentOfLimit, MAX_JAR_SIZE_MB))

        // Then
        assertTrue(
            jarSizeBytes < MAX_JAR_SIZE_BYTES,
            "Shadow jar size %.2f MB exceeds %d MB limit. ".format(jarSizeMB, MAX_JAR_SIZE_MB) +
                "Review recently added dependencies for size impact."
        )
    }

    private fun findProjectRoot(): File {
        // Walk up from current working directory to find the project root (has settings.gradle.kts)
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        // Fallback to user.dir
        return File(System.getProperty("user.dir"))
    }
}
