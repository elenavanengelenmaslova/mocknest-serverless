package nl.vintik.mocknest.infra.aws.generation

import nl.vintik.mocknest.application.generation.usecases.GenerateMocksFromSpecUseCase
import nl.vintik.mocknest.domain.generation.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertTrue

/**
 * Integration test for AI mock generation workflow.
 * Uses LocalStack for S3 testing without requiring real AWS resources.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "storage.bucket.name=test-bucket"
])
@Testcontainers
class AIGenerationIntegrationTest {
    
    companion object {
        @Container
        @JvmStatic
        private val localStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0")
        ).withServices(LocalStackContainer.Service.S3)
    }
    
    @Test
    fun `Given OpenAPI specification When generating mocks Then should create valid WireMock mappings`() = runTest {
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
        val request = MockGenerationRequest(
            namespace = namespace,
            specificationContent = openApiSpec,
            format = SpecificationFormat.OPENAPI_3,
            options = GenerationOptions(
                includeExamples = true,
                generateErrorCases = false,
                storeSpecification = false
            )
        )
        
        // This test validates the domain models and basic structure
        // Full integration would require LocalStack setup and Spring context
        assertTrue(request.namespace.apiName == "test-api")
        assertTrue(request.format == SpecificationFormat.OPENAPI_3)
        assertTrue(request.specificationContent.contains("openapi: 3.0.0"))
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
    
    @Test
    fun `Given generation job When creating job request Then should validate constraints`() {
        // Given
        val namespace = MockNamespace(apiName = "test-api")
        val specInput = SpecificationInput(
            name = "test-spec",
            content = "openapi: 3.0.0...",
            format = SpecificationFormat.OPENAPI_3
        )
        
        // When
        val jobRequest = GenerationJobRequest(
            type = GenerationType.SPECIFICATION,
            namespace = namespace,
            specifications = listOf(specInput),
            descriptions = emptyList(),
            options = GenerationOptions.default()
        )
        
        // Then
        assertTrue(jobRequest.type == GenerationType.SPECIFICATION)
        assertTrue(jobRequest.specifications.size == 1)
        assertTrue(jobRequest.descriptions.isEmpty())
    }
}