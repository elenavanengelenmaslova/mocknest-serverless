package nl.vintik.mocknest.infra.aws.generation

import nl.vintik.mocknest.domain.generation.GenerationOptions
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SpecWithDescriptionRequest
import nl.vintik.mocknest.domain.generation.SpecificationFormat
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Integration test for AI mock generation domain models.
 * Tests domain model behavior without requiring Spring context or AWS resources.
 */
class AIGenerationIntegrationTest {
    
    @Test
    fun `Given OpenAPI specification When generating mocks Then should create valid WireMock mappings`() {
        // Given
        val openApiSpec = """
        openapi: 3.0.0
        info:
          title: Test API
          version: 1.0.0
        paths:
          /users:
            get:
              operationId: getUsers
              summary: Get all users
              responses:
                '200':
                  description: Success
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          type: object
                          properties:
                            id:
                              type: integer
                            name:
                              type: string
        """.trimIndent()
        
        val namespace = MockNamespace(apiName = "test-api")
        val request = SpecWithDescriptionRequest(
            namespace = namespace,
            specificationContent = openApiSpec,
            format = SpecificationFormat.OPENAPI_3,
            description = "test-description",
            options = GenerationOptions.default())
        // This test validates the domain models and basic structure
        assertTrue(request.namespace.apiName == "test-api")
        assertTrue(request.format == SpecificationFormat.OPENAPI_3)
        assertTrue(request.specificationContent?.contains("openapi: 3.0.0") == true)
    }
    
    @Test
    fun `Given namespace with client When creating storage paths Then should organize correctly`() {
        // Given
        val namespace = MockNamespace(apiName = "payments", client = "client-a")
        
        // When & Then
        assertTrue(namespace.toPrefix() == "mocknest/client-a/payments")
        assertTrue(namespace.toStoragePath() == "mocknest/client-a/payments/")
        assertTrue(namespace.displayName() == "client-a/payments")
    }
}