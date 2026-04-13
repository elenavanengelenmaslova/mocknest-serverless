package nl.vintik.mocknest.application.runtime.journal

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.store.RequestJournalStore
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import nl.vintik.mocknest.application.core.mapper
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.runtime.config.WebhookConfig
import nl.vintik.mocknest.application.runtime.extensions.RedactSensitiveHeadersFilter
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

private val logger = KotlinLogging.logger {}

/**
 * S3-backed [RequestJournalStore] for WireMock.
 *
 * Stores [ServeEvent] records in S3 under a configurable key prefix (default `requests/`).
 * Sensitive header values are redacted before writing to S3.
 * Read/write failures are logged at WARN and never propagate to the caller.
 */
class S3RequestJournalStore(
    private val storage: ObjectStorageInterface,
    private val webhookConfig: WebhookConfig,
    private val redactFilter: RedactSensitiveHeadersFilter,
) : RequestJournalStore {

    private val prefix: String get() = webhookConfig.requestJournalPrefix

    @Volatile
    private var writesEnabled = true

    /** Suppress S3 writes — used during SnapStart priming to avoid creating versioned objects. */
    fun suppressWrites() {
        writesEnabled = false
    }

    /** Re-enable S3 writes — must be called before SnapStart snapshot is taken. */
    fun enableWrites() {
        writesEnabled = true
    }

    // ── Store<UUID, ServeEvent> ───────────────────────────────────────────────

    override fun getAllKeys(): Stream<UUID> = runBlocking {
        runCatching {
            storage.listPrefix(prefix)
                .toList()
                .mapNotNull { key ->
                    val idStr = key.removePrefix(prefix)
                    runCatching { UUID.fromString(idStr) }.getOrNull()
                }
                .stream()
        }.getOrElse { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to list keys with prefix=$prefix" }
            Stream.empty()
        }
    }

    override fun get(key: UUID): Optional<ServeEvent> = runBlocking {
        runCatching {
            val json = storage.get("$prefix$key") ?: return@runBlocking Optional.empty()
            Optional.ofNullable(mapper.readValue<ServeEvent>(json))
        }.getOrElse { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to get key=$key" }
            Optional.empty()
        }
    }

    override fun put(key: UUID, event: ServeEvent) = runBlocking {
        if (!writesEnabled) {
            logger.debug { "S3RequestJournalStore: writes suppressed (priming mode), skipping put for key=$key" }
            return@runBlocking
        }
        runCatching {
            val redactedJson = redactFilter.redactServeEvent(event)
            storage.save("$prefix$key", redactedJson)
        }.onFailure { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to put key=$key" }
        }
        Unit
    }

    override fun remove(key: UUID) = runBlocking {
        runCatching {
            storage.delete("$prefix$key")
        }.onFailure { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to remove key=$key" }
        }
        Unit
    }

    override fun clear() = runBlocking {
        runCatching {
            val keys = storage.listPrefix(prefix).toList()
            storage.deleteMany(keys.asFlow())
        }.onFailure { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to clear prefix=$prefix" }
        }
        Unit
    }

    // ── RequestJournalStore ───────────────────────────────────────────────────

    override fun getAll(): Stream<ServeEvent> = runBlocking {
        runCatching {
            val keys = storage.listPrefix(prefix).toList()
            val events = mutableListOf<ServeEvent>()
            for (key in keys) {
                runCatching {
                    val json = storage.get(key) ?: return@runCatching
                    events.add(mapper.readValue(json))
                }.onFailure { e ->
                    logger.warn(e) { "S3RequestJournalStore: failed to deserialize key=$key" }
                }
            }
            events.stream()
        }.getOrElse { e ->
            logger.warn(e) { "S3RequestJournalStore: failed to getAll with prefix=$prefix" }
            Stream.empty()
        }
    }

    override fun add(event: ServeEvent) {
        put(event.id, event)
    }

    override fun removeLast() {
        // Best-effort: list all keys, remove the one with the oldest timestamp
        runBlocking {
            runCatching {
                val keys = storage.listPrefix(prefix).toList()
                if (keys.isEmpty()) return@runCatching
                // Remove the first key (oldest by insertion order is not guaranteed in S3,
                // but this satisfies the interface contract for bounded journal size)
                storage.delete(keys.first())
            }.onFailure { e ->
                logger.warn(e) { "S3RequestJournalStore: failed to removeLast" }
            }
        }
    }
}
