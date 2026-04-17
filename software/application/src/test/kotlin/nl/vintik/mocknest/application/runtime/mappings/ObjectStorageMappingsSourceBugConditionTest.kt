package nl.vintik.mocknest.application.runtime.mappings

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import nl.vintik.mocknest.application.core.interfaces.storage.ObjectStorageInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Bug Condition Exploration Test 1a — `ObjectStorageMappingsSource.removeAll()` uses sequential deletes instead of batch
 *
 * These tests PROVE the bug exists on UNFIXED code.
 * They are EXPECTED TO FAIL — that is the correct outcome, confirming the bug exists.
 *
 * DO NOT fix production code to make these pass.
 * DO NOT fix these tests when they fail.
 *
 * **Validates: Requirements 1.1, 2.1**
 *
 * Bug Condition: `removeAll()` calls `storage.delete(key)` N times sequentially
 * instead of calling `storage.deleteMany(flow)` once.
 */
class ObjectStorageMappingsSourceBugConditionTest {

    private val storage: ObjectStorageInterface = mockk(relaxed = true)
    private val prefix = "mappings/"

    private val source = ObjectStorageMappingsSource(
        storage = storage,
        prefix = prefix,
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    companion object {
        @JvmStatic
        fun keyCounts(): Stream<Int> = Stream.of(0, 1, 5)
    }

    /**
     * Given N keys in storage When removeAll() is called Then storage.deleteMany() should be
     * called exactly once with the keys flow, and storage.delete() should NOT be called individually.
     *
     * EXPECTED TO FAIL on unfixed code:
     * - `storage.deleteMany()` is never called (0 invocations instead of 1)
     * - `storage.delete()` is called N times individually instead of 0
     *
     * Counterexample: removeAll() with N keys calls storage.delete(key) N times
     * instead of storage.deleteMany(flow) once.
     */
    @ParameterizedTest(name = "Given {0} keys When removeAll Then should use deleteMany not sequential delete")
    @MethodSource("keyCounts")
    fun `Given N keys in storage When removeAll called Then should use deleteMany and not individual delete`(keyCount: Int) {
        // Given
        val keys = (1..keyCount).map { "$prefix$it.json" }
        coEvery { storage.listPrefix(prefix) } returns flowOf(*keys.toTypedArray())

        // When
        source.removeAll()

        // Then — EXPECTED TO FAIL on unfixed code:
        // deleteMany() should be called exactly once (unfixed code never calls it)
        coVerify(exactly = 1) {
            storage.deleteMany(any<Flow<String>>(), any())
        }

        // Individual delete() should NOT be called (unfixed code calls it N times)
        coVerify(exactly = 0) {
            storage.delete(any())
        }
    }
}
