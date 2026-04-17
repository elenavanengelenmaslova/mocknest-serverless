package nl.vintik.mocknest.application.runtime.mappings

import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Preservation Property Tests — Individual mapping save/remove and loading behavior
 *
 * These tests capture BASELINE behavior that must remain unchanged after the bugfix.
 * They MUST PASS on UNFIXED code — they verify non-bug operations are correct.
 *
 * **Validates: Requirements 3.1, 3.3, 3.4, 3.5**
 *
 * Property: _For all individual mapping operations, the system uses single-object storage calls, not batch operations_
 * Property: _For all valid mapping sets, loadMappingsInto() loads all mappings and marks them non-persistent_
 */
class ObjectStorageMappingsSourcePreservationTest {

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

    companion object {
        /**
         * Provides 6 diverse StubMapping instances with varying IDs, request patterns,
         * and response definitions to test preservation across different mapping shapes.
         */
        @JvmStatic
        fun diverseMappings(): Stream<StubMapping> = Stream.of(
            // 1. Simple GET with 200 text body
            createMapping(method = "GET", url = "/api/users", status = 200, body = "[]"),
            // 2. POST with 201 and JSON body
            createMapping(method = "POST", url = "/api/orders", status = 201, body = """{"id":"order-1"}"""),
            // 3. DELETE with 204 no body
            createMapping(method = "DELETE", url = "/api/items/42", status = 204, body = null),
            // 4. PUT with 200 and longer body
            createMapping(method = "PUT", url = "/api/config", status = 200, body = """{"setting":"value","enabled":true}"""),
            // 5. GET with 404 error response
            createMapping(method = "GET", url = "/api/missing", status = 404, body = """{"error":"not found"}"""),
            // 6. PATCH with 200 and minimal body
            createMapping(method = "PATCH", url = "/api/users/1", status = 200, body = """{"updated":true}"""),
        )

        /**
         * Provides varying numbers of mappings (0, 1, 3) for loadMappingsInto() tests.
         */
        @JvmStatic
        fun mappingCounts(): Stream<Int> = Stream.of(0, 1, 3)

        private fun createMapping(method: String, url: String, status: Int, body: String?): StubMapping {
            val id = UUID.randomUUID()
            val json = buildString {
                append("""{"id":"$id","request":{"method":"$method","url":"$url"},"response":{"status":$status""")
                if (body != null) {
                    val escaped = body.replace("\"", "\\\"")
                    append(""","body":"$escaped"""")
                }
                append("}}")
            }
            return Json.read(json, StubMapping::class.java)
        }
    }

    @Nested
    inner class IndividualSavePreservation {

        /**
         * Given a diverse StubMapping When save() is called
         * Then storage.save("mappings/{id}.json", matchingJson) is called exactly once
         * and storage.deleteMany() is NOT called.
         *
         * Property: _For all individual mapping save operations, the system uses single-object
         * storage.save(), not batch operations_
         */
        @ParameterizedTest(name = "Given mapping {0} When save Then should call storage.save once and not deleteMany")
        @MethodSource("nl.vintik.mocknest.application.runtime.mappings.ObjectStorageMappingsSourcePreservationTest#diverseMappings")
        fun `Given diverse mapping When save Then should use single-object storage save`(mapping: StubMapping) {
            // When
            source.save(mapping)

            // Then — storage.save() called exactly once with correct key
            val expectedKey = "$prefix${mapping.id}.json"
            coVerify(exactly = 1) {
                storage.save(
                    expectedKey,
                    match { savedJson ->
                        val decoded = runCatching { Json.read(savedJson, StubMapping::class.java) }.getOrNull()
                        decoded?.id == mapping.id
                    }
                )
            }

            // deleteMany() must NOT be called during individual save
            coVerify(exactly = 0) {
                storage.deleteMany(any<Flow<String>>(), any())
            }
        }
    }

    @Nested
    inner class IndividualRemovePreservation {

        /**
         * Given a diverse StubMapping When remove() is called
         * Then storage.delete("mappings/{id}.json") is called exactly once
         * and storage.deleteMany() is NOT called.
         *
         * Property: _For all individual mapping remove operations, the system uses single-object
         * storage.delete(), not batch operations_
         */
        @ParameterizedTest(name = "Given mapping {0} When remove Then should call storage.delete once and not deleteMany")
        @MethodSource("nl.vintik.mocknest.application.runtime.mappings.ObjectStorageMappingsSourcePreservationTest#diverseMappings")
        fun `Given diverse mapping When remove Then should use single-object storage delete`(mapping: StubMapping) {
            // When
            source.remove(mapping)

            // Then — storage.delete() called exactly once with correct key
            val expectedKey = "$prefix${mapping.id}.json"
            coVerify(exactly = 1) {
                storage.delete(expectedKey)
            }

            // deleteMany() must NOT be called during individual remove
            coVerify(exactly = 0) {
                storage.deleteMany(any<Flow<String>>(), any())
            }
        }
    }

    @Nested
    inner class LoadMappingsPreservation {

        /**
         * Given N valid mappings in storage When loadMappingsInto() is called
         * Then all N mappings are loaded via storage.listPrefix() and storage.get()
         * and each mapping is marked non-persistent.
         *
         * Property: _For all valid mapping sets, loadMappingsInto() loads all mappings
         * and marks them non-persistent_
         */
        @ParameterizedTest(name = "Given {0} mappings When loadMappingsInto Then should load all and mark non-persistent")
        @MethodSource("nl.vintik.mocknest.application.runtime.mappings.ObjectStorageMappingsSourcePreservationTest#mappingCounts")
        fun `Given N mappings When loadMappingsInto Then should load all and mark non-persistent`(count: Int) {
            // Given — create N mappings with unique IDs
            val mappingIds = (1..count).map { UUID.randomUUID() }
            val keys = mappingIds.map { "$prefix$it.json" }

            coEvery { storage.listPrefix(prefix) } returns flowOf(*keys.toTypedArray())

            mappingIds.forEachIndexed { index, id ->
                val json = """{"id":"$id","request":{"method":"GET","url":"/test-$index"},"response":{"status":200,"body":"ok-$index"}}"""
                coEvery { storage.get(keys[index]) } returns json
            }

            val addedMappings = mutableListOf<StubMapping>()
            every { stubMappings.addMapping(capture(addedMappings)) } returns Unit

            // When
            source.loadMappingsInto(stubMappings)

            // Then — all mappings loaded
            assertEquals(count, addedMappings.size, "Expected $count mappings to be loaded")

            // All loaded mappings are marked non-persistent
            addedMappings.forEach { mapping ->
                assertFalse(mapping.isPersistent, "Mapping ${mapping.id} should be marked non-persistent")
            }

            // Verify storage.listPrefix() was called
            coVerify(exactly = 1) { storage.listPrefix(prefix) }

            // Verify storage.get() was called for each key
            keys.forEach { key ->
                coVerify(exactly = 1) { storage.get(key) }
            }
        }
    }
}
