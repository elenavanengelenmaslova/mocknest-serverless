package io.mocknest.application.wiremock.mappings

import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import io.mocknest.application.interfaces.storage.ObjectStorageInterface

class ObjectStorageMappingsSourceTest {

    private val storage: ObjectStorageInterface = mockk(relaxed = true)
    private val stubMappings: StubMappings = mockk(relaxed = true)

    private val prefix = "mappings/"
    private val concurrency = 4

    private val source = ObjectStorageMappingsSource(
        storage = storage,
        prefix = prefix,
        concurrency = concurrency,
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class LoadMappingsInto {

        @Test
        fun `Given keys with valid and blank mapping json When loading mappings Then should add only valid mappings and mark them non-persistent`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()

            val key1 = "${prefix}${id1}.json"
            val key2 = "${prefix}${id2}.json"
            val blankKey = "${prefix}blank.json"

            val mappingJson1 = loadTestData("mappings/valid-mapping-1.json")
                .replace("{{ID}}", id1.toString())
            val mappingJson2 = loadTestData("mappings/valid-mapping-2.json")
                .replace("{{ID}}", id2.toString())

            coEvery { storage.listPrefix(prefix) } returns flowOf(key1, blankKey, key2)
            coEvery { storage.get(key1) } returns mappingJson1
            coEvery { storage.get(blankKey) } returns "   "
            coEvery { storage.get(key2) } returns mappingJson2

            val addedMappings = mutableListOf<StubMapping>()
            every { stubMappings.addMapping(capture(addedMappings)) } returns Unit

            source.loadMappingsInto(stubMappings)

            assertEquals(2, addedMappings.size)
            assertFalse(addedMappings[0].isPersistent, "Expected mapping to be marked non-persistent")
            assertFalse(addedMappings[1].isPersistent, "Expected mapping to be marked non-persistent")
            assertEquals(id1, addedMappings[0].id)
            assertEquals(id2, addedMappings[1].id)

            coVerify(exactly = 1) { storage.listPrefix(prefix) }
            coVerify(exactly = 1) { storage.get(key1) }
            coVerify(exactly = 1) { storage.get(blankKey) }
            coVerify(exactly = 1) { storage.get(key2) }
        }

        @Test
        fun `Given a mapping json that cannot be parsed When loading mappings Then should skip that mapping and continue`() {
            val validId = UUID.randomUUID()
            val validKey = "${prefix}${validId}.json"
            val invalidKey = "${prefix}invalid.json"

            val validJson = loadTestData("mappings/valid-mapping-1.json")
                .replace("{{ID}}", validId.toString())
            val invalidJson = loadTestData("mappings/invalid-mapping.json")

            coEvery { storage.listPrefix(prefix) } returns flowOf(invalidKey, validKey)
            coEvery { storage.get(invalidKey) } returns invalidJson
            coEvery { storage.get(validKey) } returns validJson

            val added = slot<StubMapping>()
            every { stubMappings.addMapping(capture(added)) } returns Unit

            source.loadMappingsInto(stubMappings)

            assertEquals(validId, added.captured.id)
            assertFalse(added.captured.isPersistent)

            coVerify(exactly = 1) { storage.listPrefix(prefix) }
            coVerify(exactly = 1) { storage.get(invalidKey) }
            coVerify(exactly = 1) { storage.get(validKey) }
            // Only one valid mapping should be added
            coVerify(exactly = 1) { stubMappings.addMapping(any()) }
        }
    }

    @Nested
    inner class SaveAndRemove {

        @Test
        fun `Given a mapping When saving Then should store json under prefix with id filename`() {
            val id = UUID.randomUUID()
            val mapping = Json.read(
                loadTestData("mappings/valid-mapping-1.json").replace("{{ID}}", id.toString()),
                StubMapping::class.java
            )

            source.save(mapping)

            val expectedKey = "$prefix$id.json"
            coVerify(exactly = 1) {
                storage.save(
                    expectedKey,
                    match { savedJson ->
                        val decoded = runCatching { Json.read(savedJson, StubMapping::class.java) }.getOrNull()
                        decoded?.id == id
                    }
                )
            }
        }

        @Test
        fun `Given a mapping When removing Then should delete json under prefix with id filename`() {
            val id = UUID.randomUUID()
            val mapping = Json.read(
                loadTestData("mappings/valid-mapping-1.json").replace("{{ID}}", id.toString()),
                StubMapping::class.java
            )

            source.remove(mapping)

            coVerify(exactly = 1) { storage.delete("$prefix$id.json") }
        }

        @Test
        fun `Given keys in storage When removeAll Then should delete each key returned by listPrefix`() {
            val key1 = "${prefix}a.json"
            val key2 = "${prefix}b.json"

            coEvery { storage.listPrefix(prefix) } returns flowOf(key1, key2)

            source.removeAll()

            coVerify(exactly = 1) { storage.listPrefix(prefix) }
            coVerify(exactly = 1) { storage.delete(key1) }
            coVerify(exactly = 1) { storage.delete(key2) }
        }
    }

    private fun loadTestData(relativePath: String): String {
        val fullPath = "/test-data/$relativePath"
        return this::class.java.getResource(fullPath)?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $fullPath")
    }
}
