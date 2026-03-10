package nl.vintik.mocknest.application.core

import java.util.Properties

/**
 * MockNest Serverless version information.
 * 
 * Version is read from version.properties which is generated from Gradle version.
 * To update version, change it in build.gradle.kts only.
 * 
 * Version is synchronized with:
 * - deployment/aws/sam/template.yaml SemanticVersion (via processResources)
 * - Git tags (e.g., v1.0.0)
 */
object Version {
    
    /**
     * The MockNest version, loaded from version.properties resource.
     * This is lazy-loaded and cached for performance.
     */
    val MOCKNEST_VERSION: String by lazy {
        loadVersionFromProperties()
    }
    
    private fun loadVersionFromProperties(): String {
        return runCatching {
            val properties = Properties()
            Version::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                properties.load(stream)
                properties.getProperty("version") ?: "unknown"
            } ?: "unknown"
        }.getOrElse { "unknown" }
    }
}