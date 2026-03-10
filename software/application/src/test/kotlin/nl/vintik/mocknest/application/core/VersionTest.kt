package nl.vintik.mocknest.application.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class VersionTest {

    @Test
    fun `Given version properties file exists When accessing MOCKNEST_VERSION Then should return version from properties`() {
        // When
        val version = Version.MOCKNEST_VERSION
        
        // Then
        assertNotEquals("unknown", version)
        assertNotEquals("", version)
    }

    @Test
    fun `Given version is accessed multiple times When getting MOCKNEST_VERSION Then should return same cached value`() {
        // When
        val version1 = Version.MOCKNEST_VERSION
        val version2 = Version.MOCKNEST_VERSION
        
        // Then
        assertEquals(version1, version2)
    }

    @Test
    fun `Given version properties When loading version Then should handle properties format correctly`() {
        // When
        val version = Version.MOCKNEST_VERSION
        
        // Then
        // Version should be a valid semantic version format or at least not be "unknown"
        assertNotEquals("unknown", version)
        // Should not be empty or null
        assert(version.isNotBlank()) { "Version should not be blank" }
    }
}