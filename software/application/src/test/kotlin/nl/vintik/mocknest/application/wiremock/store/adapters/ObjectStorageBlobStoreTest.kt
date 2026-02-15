package nl.vintik.mocknest.application.wiremock.store.adapters

import nl.vintik.mocknest.application.interfaces.storage.ObjectStorageInterface
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Optional
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjectStorageBlobStoreTest {

    private val storage: ObjectStorageInterface = mockk(relaxed = true)
    private val blobStore = ObjectStorageBlobStore(storage)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    inner class Get {

        @Test
        fun `Given missing key When getting Then should return empty optional`() {
            coEvery { storage.get("__files/missing.json") } returns null

            val result = blobStore.get("missing.json")

            assertTrue(result.isEmpty)
            coVerify(exactly = 1) { storage.get("__files/missing.json") }
        }

        @Test
        fun `Given text key and stored utf8 content When getting Then should return raw utf8 bytes`() {
            val content = """{"ok":true}"""
            coEvery { storage.get("__files/test.json") } returns content

            val result = blobStore.get("test.json")

            assertTrue(result.isPresent)
            assertContentEquals(content.toByteArray(Charsets.UTF_8), result.get())
            coVerify(exactly = 1) { storage.get("__files/test.json") }
        }

        @Test
        fun `Given binary key and stored base64 content When getting Then should base64 decode`() {
            val bytes = byteArrayOf(0x01, 0x02, 0x7F, 0x00)
            val encoded = Base64.getEncoder().encodeToString(bytes)
            coEvery { storage.get("__files/img.bin") } returns encoded

            val result = blobStore.get("img.bin")

            assertTrue(result.isPresent)
            assertContentEquals(bytes, result.get())
            coVerify(exactly = 1) { storage.get("__files/img.bin") }
        }

        @Test
        fun `Given binary key and stored non-base64 content When getting Then should return utf8 bytes`() {
            val raw = "not-base64-@@@"
            coEvery { storage.get("__files/file.bin") } returns raw

            val result = blobStore.get("file.bin")

            assertTrue(result.isPresent)
            assertContentEquals(raw.toByteArray(Charsets.UTF_8), result.get())
            coVerify(exactly = 1) { storage.get("__files/file.bin") }
        }

        @Test
        fun `Given key already prefixed When getting Then should not double prefix`() {
            val content = "hello"
            coEvery { storage.get("__files/already.txt") } returns content

            val result = blobStore.get("__files/already.txt")

            assertTrue(result.isPresent)
            assertContentEquals(content.toByteArray(Charsets.UTF_8), result.get())
            coVerify(exactly = 1) { storage.get("__files/already.txt") }
        }

        @Test
        fun `Given key with leading slash When getting Then should trim leading slash and prefix`() {
            val content = "hello"
            coEvery { storage.get("__files/a/b.txt") } returns content

            val result = blobStore.get("/a/b.txt")

            assertTrue(result.isPresent)
            assertContentEquals(content.toByteArray(Charsets.UTF_8), result.get())
            coVerify(exactly = 1) { storage.get("__files/a/b.txt") }
        }
    }

    @Nested
    inner class Put {

        @Test
        fun `Given text key When putting Then should store utf8 string`() {
            val bytes = """{"a":1}""".toByteArray(Charsets.UTF_8)

            blobStore.put("x.json", bytes)

            coVerify(exactly = 1) { storage.save("__files/x.json", """{"a":1}""") }
        }

        @Test
        fun `Given binary key When putting Then should store base64 string`() {
            val bytes = byteArrayOf(0x10, 0x20, 0x30)
            val expected = Base64.getEncoder().encodeToString(bytes)

            blobStore.put("bin.dat", bytes)

            coVerify(exactly = 1) { storage.save("__files/bin.dat", expected) }
        }

        @Test
        fun `Given key with leading slash When putting Then should trim and prefix`() {
            val bytes = "hi".toByteArray(Charsets.UTF_8)

            blobStore.put("/folder/t.txt", bytes)

            coVerify(exactly = 1) { storage.save("__files/folder/t.txt", "hi") }
        }

        @Test
        fun `Given key already prefixed When putting Then should keep single prefix`() {
            val bytes = "hi".toByteArray(Charsets.UTF_8)

            blobStore.put("__files/ok.txt", bytes)

            coVerify(exactly = 1) { storage.save("__files/ok.txt", "hi") }
        }
    }

    @Nested
    inner class Streams {

        @Test
        fun `Given present key When getting stream Then should return input stream with same bytes`() {
            val content = "stream"
            coEvery { storage.get("__files/s.txt") } returns content

            val streamOpt = blobStore.getStream("s.txt")

            assertTrue(streamOpt.isPresent)
            val bytes = streamOpt.get().readAllBytes()
            assertContentEquals(content.toByteArray(Charsets.UTF_8), bytes)
        }
    }

    @Nested
    inner class RemoveAndListAndClear {

        @Test
        fun `Given key When removing Then should delete from prefixed key`() {
            blobStore.remove("gone.txt")

            coVerify(exactly = 1) { storage.delete("__files/gone.txt") }
        }

        @Test
        fun `Given storage keys with prefix When getting all keys Then should return keys without prefix`() {
            coEvery { storage.listPrefix(FILES_PREFIX) } returns flowOf(
                "__files/a.json",
                "__files/b/c.txt",
            )

            val keys = blobStore.getAllKeys().toList()

            assertEquals(listOf("a.json", "b/c.txt"), keys)
            coVerify(exactly = 1) { storage.listPrefix(FILES_PREFIX) }
        }

        @Test
        fun `Given clear called When clearing Then should bulk delete all keys under files prefix`() {
            val keysFlow: Flow<String> = flowOf("__files/a.json", "__files/b.bin")
            coEvery { storage.listPrefix(FILES_PREFIX) } returns keysFlow

            blobStore.clear()

            coVerify(exactly = 1) { storage.listPrefix(FILES_PREFIX) }
            coVerify(exactly = 1) { storage.deleteMany(keysFlow) }
        }
    }
}