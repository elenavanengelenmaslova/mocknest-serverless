package nl.vintik.mocknest.application.core.interfaces.storage

import kotlinx.coroutines.flow.Flow

interface ObjectStorageInterface {
    // Single-object operations (non-blocking)
    suspend fun save(id: String, content: String): String
    suspend fun get(id: String): String?
    suspend fun delete(id: String)

    // Listings as streams
    fun list(): Flow<String>

    /**
     * List keys starting with the provided prefix. Default implementation filters [list()].
     */
    fun listPrefix(prefix: String): Flow<String> = flowFilterPrefix(list(), prefix)

    // Bulk operations as streams
    fun getMany(ids: Flow<String>, concurrency: Int = 32): Flow<Pair<String, String?>>

    suspend fun deleteMany(ids: Flow<String>, concurrency: Int = 8)
}

// Helper to filter a stream by prefix with minimal allocations
private fun flowFilterPrefix(source: Flow<String>, prefix: String): Flow<String> =
    kotlinx.coroutines.flow.flow {
        source.collect { key ->
            if (key.startsWith(prefix)) emit(key)
        }
    }