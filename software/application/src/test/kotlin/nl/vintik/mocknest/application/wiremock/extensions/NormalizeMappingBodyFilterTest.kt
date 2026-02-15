package nl.vintik.mocknest.application.wiremock.extensions

import nl.vintik.mocknest.application.interfaces.storage.ObjectStorageInterface
import nl.vintik.mocknest.application.wiremock.store.adapters.FILES_PREFIX
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NormalizeMappingBodyFilterTest {

    private val mockStorage: ObjectStorageInterface = mockk(relaxed = true)
    private val filter = NormalizeMappingBodyFilter(mockStorage)
    private val mapper = jacksonObjectMapper()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadTestData(filename: String): String {
        return this::class.java.getResource("/test-data/mappings/$filename")?.readText()
            ?: throw IllegalArgumentException("Test data file not found: $filename")
    }

    @Nested
    inner class `Request Identification` {

        @Test
        fun `Given POST request to mappings endpoint When checking if save mapping Then should return true`() {
            // Given
            val request = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/mappings")
                .withMethod(RequestMethod.POST)
                .build()

            // When
            val result = filter.isSaveMapping(request)

            // Then
            assertTrue(result)
        }

        @Test
        fun `Given PUT request to mappings endpoint with UUID When checking if save mapping Then should return true`() {
            // Given
            val uuid = "12345678-1234-1234-1234-123456789abc"
            val request = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/mappings/$uuid")
                .withMethod(RequestMethod.PUT)
                .build()

            // When
            val result = filter.isSaveMapping(request)

            // Then
            assertTrue(result)
        }

        @Test
        fun `Given GET request to mappings endpoint When checking if save mapping Then should return false`() {
            // Given
            val request = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/mappings")
                .withMethod(RequestMethod.GET)
                .build()

            // When
            val result = filter.isSaveMapping(request)

            // Then
            assertFalse(result)
        }

        @Test
        fun `Given POST request to non-mappings endpoint When checking if save mapping Then should return false`() {
            // Given
            val request = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/requests")
                .withMethod(RequestMethod.POST)
                .build()

            // When
            val result = filter.isSaveMapping(request)

            // Then
            assertFalse(result)
        }
    }

    @Nested
    inner class `Filter Processing` {

        @Test
        suspend fun `Given non-save mapping request When filtering Then should not call storage`() {
            // Given
            val request = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/requests")
                .withMethod(RequestMethod.GET)
                .build()

            // When
            filter.filter(request, null)

            // Then
            coVerify(exactly = 0) { mockStorage.save(any(), any()) }
        }
    }

    @Nested
    inner class `Mapping Normalization` {

        @Test
        suspend fun `Given mapping with text body When normalizing Then should externalize body to JSON file`() {
            // Given
            val mappingJson = loadTestData("mapping-with-text-body.json")
            coEvery { mockStorage.save(any(), any()) } returns "saved"

            // When
            val result = filter.normalizeMappingToBodyFile(mappingJson)

            // Then
            val resultNode = mapper.readTree(result)
            assertEquals("test-mapping-id.json", resultNode["response"]["bodyFileName"].asText())
            assertEquals("application/json", resultNode["response"]["headers"]["Content-Type"].asText())
            assertFalse(resultNode["response"].has("body"))

            coVerify { mockStorage.save("${FILES_PREFIX}test-mapping-id.json", "{\"message\": \"Hello World\"}") }
        }

        @Test
        suspend fun `Given mapping with base64 body When normalizing Then should externalize body to binary file`() {
            // Given
            val mappingJson = loadTestData("mapping-with-base64-body.json")
            coEvery { mockStorage.save(any(), any()) } returns "saved"

            // When
            val result = filter.normalizeMappingToBodyFile(mappingJson)

            // Then
            val resultNode = mapper.readTree(result)
            assertEquals("test-mapping-id.bin", resultNode["response"]["bodyFileName"].asText())
            assertEquals("application/octet-stream", resultNode["response"]["headers"]["Content-Type"].asText())
            assertFalse(resultNode["response"].has("base64Body"))

            coVerify { mockStorage.save("${FILES_PREFIX}test-mapping-id.bin", "SGVsbG8gV29ybGQ=") }
        }

        @Test
        suspend fun `Given mapping without ID When normalizing Then should generate UUID and externalize body`() {
            // Given
            val mappingJson = loadTestData("mapping-without-id.json")
            coEvery { mockStorage.save(any(), any()) } returns "saved"

            // When
            val result = filter.normalizeMappingToBodyFile(mappingJson)

            // Then
            val resultNode = mapper.readTree(result)
            assertTrue(resultNode.has("id"))
            val generatedId = resultNode["id"].asText()
            assertTrue(generatedId.isNotEmpty())
            assertEquals("$generatedId.json", resultNode["response"]["bodyFileName"].asText())

            coVerify { mockStorage.save("${FILES_PREFIX}$generatedId.json", "{\"message\": \"Hello\"}") }
        }

        @Test
        suspend fun `Given mapping with custom Content-Type When normalizing Then should preserve existing Content-Type`() {
            // Given
            val mappingJson = loadTestData("mapping-with-custom-content-type.json")
            coEvery { mockStorage.save(any(), any()) } returns "saved"

            // When
            val result = filter.normalizeMappingToBodyFile(mappingJson)

            // Then
            val resultNode = mapper.readTree(result)
            assertEquals("application/custom+json", resultNode["response"]["headers"]["Content-Type"].asText())
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "mapping-with-existing-bodyfilename.json",
            "mapping-transient.json",
            "mapping-without-body.json",
            "mapping-default-persistent.json"
        ])
        suspend fun `Given mapping that should not be modified When normalizing Then should return unchanged`(filename: String) {
            // Given
            val mappingJson = loadTestData(filename)

            // When
            val result = filter.normalizeMappingToBodyFile(mappingJson)

            // Then
            assertEquals(mappingJson, result)
            coVerify(exactly = 0) { mockStorage.save(any(), any()) }
        }
    }

    @Nested
    inner class `Request Rebuilding` {

        @Test
        fun `Given original request and new body When rebuilding Then should create request with new body`() {
            // Given
            val originalRequest = ImmutableRequest.create()
                .withAbsoluteUrl("http://localhost:8080/__admin/mappings")
                .withMethod(RequestMethod.POST)
                .withHeaders(HttpHeaders.noHeaders())
                .withBody("original body".toByteArray())
                .build()
            val newBodyJson = """{"new": "content"}"""

            // When
            val result = filter.rebuildWithBody(originalRequest, newBodyJson)

            // Then
            assertEquals(originalRequest.absoluteUrl, result.absoluteUrl)
            assertEquals(originalRequest.method, result.method)
            assertEquals(newBodyJson, result.bodyAsString)
        }
    }

    @Nested
    inner class `Filter Metadata` {

        @Test
        fun `Given filter instance When getting name Then should return correct filter name`() {
            // When
            val name = filter.getName()

            // Then
            assertEquals("normalize-mapping-body-filter", name)
        }
    }
}