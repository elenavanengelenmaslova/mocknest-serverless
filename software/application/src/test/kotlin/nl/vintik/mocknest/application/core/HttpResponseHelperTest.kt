package nl.vintik.mocknest.application.core

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpResponseHelperTest {

    @Test
    fun `Given data object When creating OK response Then should return HTTP 200 with JSON content type`() {
        // Given
        val testData = mapOf("message" to "test", "status" to "success")
        
        // When
        val response = HttpResponseHelper.ok(testData)
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertTrue(response.body?.contains("\"message\":\"test\"") == true)
        assertTrue(response.body?.contains("\"status\":\"success\"") == true)
    }

    @Test
    fun `Given string data When creating OK response Then should return properly formatted JSON response`() {
        // Given
        val testData = "simple string"
        
        // When
        val response = HttpResponseHelper.ok(testData)
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertEquals("\"simple string\"", response.body)
    }

    @Test
    fun `Given complex object When creating OK response Then should serialize to JSON correctly`() {
        // Given
        data class TestObject(val id: Int, val name: String, val active: Boolean)
        val testData = TestObject(1, "test-object", true)
        
        // When
        val response = HttpResponseHelper.ok(testData)
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertTrue(response.body?.contains("\"id\":1") == true)
        assertTrue(response.body?.contains("\"name\":\"test-object\"") == true)
        assertTrue(response.body?.contains("\"active\":true") == true)
    }

    @Test
    fun `Given list data When creating OK response Then should serialize list to JSON array`() {
        // Given
        val testData = listOf("item1", "item2", "item3")
        
        // When
        val response = HttpResponseHelper.ok(testData)
        
        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("application/json", response.headers?.getFirst("Content-Type"))
        assertTrue(response.body?.startsWith("[") == true)
        assertTrue(response.body?.endsWith("]") == true)
        assertTrue(response.body?.contains("\"item1\"") == true)
        assertTrue(response.body?.contains("\"item2\"") == true)
        assertTrue(response.body?.contains("\"item3\"") == true)
    }
}