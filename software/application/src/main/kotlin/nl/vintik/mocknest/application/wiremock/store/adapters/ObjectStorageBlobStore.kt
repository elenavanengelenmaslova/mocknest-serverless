package nl.vintik.mocknest.application.wiremock.store.adapters

import nl.vintik.mocknest.application.interfaces.storage.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.store.BlobStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import java.util.stream.Stream

/**
 * BlobStore directly backed by ObjectStorageInterface.
 * - Keys are stored under the "__files/" prefix
 * - Text files (e.g., .json) are stored as UTF-8 text; binaries are Base64-encoded.
 *   On read, for non-text keys we try Base64 decode and fall back to UTF-8 bytes if not Base64.
 */
const val FILES_PREFIX = "__files/"
class ObjectStorageBlobStore(
    private val storage: ObjectStorageInterface,
) : BlobStore {
    private val logger = KotlinLogging.logger {}
    private val textExtensions = setOf(".json", ".txt", ".xml", ".html", ".csv")

    private fun fullKey(key: String) = if (key.startsWith(FILES_PREFIX)) key else FILES_PREFIX + key.trimStart('/')
    private fun isTextKey(key: String) = textExtensions.any { key.endsWith(it, ignoreCase = true) }

    override fun get(key: String): Optional<ByteArray> = runBlocking {
        val raw = storage.get(fullKey(key)) ?: return@runBlocking Optional.empty()
        if (isTextKey(key)) {
            return@runBlocking Optional.of(raw.toByteArray(Charsets.UTF_8))
        }
        return@runBlocking runCatching {
            Optional.of(Base64.getDecoder().decode(raw))
        }.onFailure {
            logger.debug { "Non-Base64 content for key=$key; returning raw UTF-8 bytes" }
        }.getOrDefault(Optional.of(raw.toByteArray(Charsets.UTF_8)))
    }

    override fun getStream(key: String): Optional<InputStream> =
        get(key).map { bytes -> ByteArrayInputStream(bytes) }

    override fun getStreamSource(key: String): InputStreamSource =
        InputStreamSource { getStream(key).orElse(ByteArrayInputStream(ByteArray(0))) }

    override fun put(key: String, value: ByteArray) {
        runBlocking {
            val k = fullKey(key)
            if (isTextKey(key)) {
                storage.save(k, value.toString(Charsets.UTF_8))
            } else {
                val encoded = Base64.getEncoder().encodeToString(value)
                storage.save(k, encoded)
            }
        }
    }

    override fun remove(key: String) = runBlocking {
        storage.delete(fullKey(key))
    }

    override fun getAllKeys(): Stream<String> = runBlocking {
        val list = storage.listPrefix(FILES_PREFIX)
            .map { it.removePrefix(FILES_PREFIX) }
            .toList()
        list.stream()
    }

    override fun clear() {
        runBlocking {
            // Use bulk delete for efficiency
            storage.deleteMany(storage.listPrefix(FILES_PREFIX))
        }
    }
}