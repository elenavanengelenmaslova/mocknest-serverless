package io.mocknest.domain.generation

/**
 * Represents a namespace for organizing mocks by API and optionally by client.
 * Used to create isolated storage prefixes for different APIs and clients.
 */
data class MockNamespace(
    val apiName: String,                     // Required: API identifier
    val client: String? = null              // Optional: Client/tenant identifier
) {
    init {
        require(apiName.isNotBlank()) { "API name cannot be blank" }
        require(apiName.matches(Regex("^[a-zA-Z0-9-_]+$"))) { 
            "API name must contain only alphanumeric characters, hyphens, and underscores" 
        }
        client?.let { 
            require(it.isNotBlank()) { "Client name cannot be blank" }
            require(it.matches(Regex("^[a-zA-Z0-9-_]+$"))) { 
                "Client name must contain only alphanumeric characters, hyphens, and underscores" 
            }
        }
    }
    
    /**
     * Generates the storage prefix for this namespace.
     * Examples:
     * - MockNamespace(apiName = "salesforce") → "mocknest/salesforce"
     * - MockNamespace(client = "client-a", apiName = "payments") → "mocknest/client-a/payments"
     */
    fun toPrefix(): String = when {
        client != null -> "mocknest/$client/$apiName"
        else -> "mocknest/$apiName"
    }
    
    /**
     * Generates the storage path with trailing slash for this namespace.
     */
    fun toStoragePath(): String = "${toPrefix()}/"
    
    /**
     * Creates a display name for this namespace.
     */
    fun displayName(): String = when {
        client != null -> "$client/$apiName"
        else -> apiName
    }
}