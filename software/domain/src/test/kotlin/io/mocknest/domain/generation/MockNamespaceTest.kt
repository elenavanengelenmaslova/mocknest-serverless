package io.mocknest.domain.generation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MockNamespaceTest {
    
    @Test
    fun `Given valid API name When creating namespace Then should create successfully`() {
        val namespace = MockNamespace(apiName = "salesforce")
        
        assertEquals("salesforce", namespace.apiName)
        assertEquals(null, namespace.client)
        assertEquals("mocknest/salesforce", namespace.toPrefix())
        assertEquals("mocknest/salesforce/", namespace.toStoragePath())
        assertEquals("salesforce", namespace.displayName())
    }
    
    @Test
    fun `Given valid API name and client When creating namespace Then should create successfully`() {
        val namespace = MockNamespace(apiName = "payments", client = "client-a")
        
        assertEquals("payments", namespace.apiName)
        assertEquals("client-a", namespace.client)
        assertEquals("mocknest/client-a/payments", namespace.toPrefix())
        assertEquals("mocknest/client-a/payments/", namespace.toStoragePath())
        assertEquals("client-a/payments", namespace.displayName())
    }
    
    @Test
    fun `Given blank API name When creating namespace Then should throw exception`() {
        assertThrows<IllegalArgumentException> {
            MockNamespace(apiName = "")
        }
    }
    
    @Test
    fun `Given invalid API name with special characters When creating namespace Then should throw exception`() {
        assertThrows<IllegalArgumentException> {
            MockNamespace(apiName = "api@name")
        }
    }
    
    @Test
    fun `Given blank client name When creating namespace Then should throw exception`() {
        assertThrows<IllegalArgumentException> {
            MockNamespace(apiName = "api", client = "")
        }
    }
    
    @Test
    fun `Given invalid client name with special characters When creating namespace Then should throw exception`() {
        assertThrows<IllegalArgumentException> {
            MockNamespace(apiName = "api", client = "client@name")
        }
    }
    
    @Test
    fun `Given valid names with hyphens and underscores When creating namespace Then should create successfully`() {
        val namespace = MockNamespace(apiName = "api-name_v2", client = "client-a_test")
        
        assertEquals("mocknest/client-a_test/api-name_v2", namespace.toPrefix())
    }
}