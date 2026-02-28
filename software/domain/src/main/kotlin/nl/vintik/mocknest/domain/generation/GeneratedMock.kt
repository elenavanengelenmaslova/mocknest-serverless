package nl.vintik.mocknest.domain.generation

import org.springframework.http.HttpMethod
import java.time.Instant

/**
 * Represents a generated mock ready for WireMock creation.
 */
data class GeneratedMock(
    val id: String,
    val name: String,
    val namespace: MockNamespace,
    val wireMockMapping: String, // JSON string in WireMock format
    val metadata: MockMetadata,
    val generatedAt: Instant = Instant.now()
) {
    init {
        require(id.isNotBlank()) { "Mock ID cannot be blank" }
        require(name.isNotBlank()) { "Mock name cannot be blank" }
        require(wireMockMapping.isNotBlank()) { "WireMock mapping cannot be blank" }
    }
}

/**
 * Metadata about how and why a mock was generated.
 */
data class MockMetadata(
    val sourceType: SourceType,
    val sourceReference: String, // Spec path, description, etc.
    val endpoint: EndpointInfo,
    val tags: Set<String> = emptySet()
) {
    init {
        require(sourceReference.isNotBlank()) { "Source reference cannot be blank" }
    }
}

/**
 * Information about the API endpoint this mock represents.
 */
data class EndpointInfo(
    val method: HttpMethod,
    val path: String,
    val statusCode: Int,
    val contentType: String
) {
    init {
        require(path.isNotBlank()) { "Endpoint path cannot be blank" }
        require(statusCode in 100..599) { "Status code must be valid HTTP status code" }
        require(contentType.isNotBlank()) { "Content type cannot be blank" }
    }
}

/**
 * Source of the mock generation.
 */
enum class SourceType {
    SPEC_WITH_DESCRIPTION, // Generated from spec + natural language
    REFINEMENT          // Generated from mock refinement (e.g. correction)
}