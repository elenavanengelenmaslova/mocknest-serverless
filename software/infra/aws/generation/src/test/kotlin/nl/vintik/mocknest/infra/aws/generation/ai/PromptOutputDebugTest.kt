package nl.vintik.mocknest.infra.aws.generation.ai

import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant

/**
 * Debug test to output actual prompt content for comparison
 */
class PromptOutputDebugTest {

    private val promptBuilder = PromptBuilderService()

    @Test
    fun `Output spec prompt for inspection`() {
        val specification = APISpecification(
            format = SpecificationFormat.OPENAPI_3,
            version = "1.0.0",
            title = "Pet Store API",
            endpoints = listOf(
                EndpointDefinition(
                    path = "/pets",
                    method = HttpMethod.GET,
                    operationId = "getPets",
                    summary = "List all pets",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(200 to ResponseDefinition(200, "OK", null))
                ),
                EndpointDefinition(
                    path = "/pets",
                    method = HttpMethod.POST,
                    operationId = "createPet",
                    summary = "Create a pet",
                    parameters = emptyList(),
                    requestBody = null,
                    responses = mapOf(201 to ResponseDefinition(201, "Created", null))
                )
            ),
            schemas = emptyMap()
        )
        val description = "Add realistic error responses"
        val namespace = MockNamespace(apiName = "petstore")

        val actualPrompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)
        
        println("=== ACTUAL SPEC PROMPT ===")
        println(actualPrompt)
        println("=== END ACTUAL SPEC PROMPT ===")
        println()
        println("Length: ${actualPrompt.length}")
        println("Lines: ${actualPrompt.lines().size}")
    }

    @Test
    fun `Output correction prompt for inspection`() {
        val namespace = MockNamespace(apiName = "petstore")
        val invalidMock = GeneratedMock(
            id = "mock-1",
            name = "Get Pet",
            namespace = namespace,
            wireMockMapping = """{"request":{"method":"GET","url":"/pet/123"},"response":{"status":200}}""",
            metadata = MockMetadata(
                sourceType = SourceType.SPEC_WITH_DESCRIPTION,
                sourceReference = "test",
                endpoint = EndpointInfo(HttpMethod.GET, "/pet/123", 200, "application/json")
            ),
            generatedAt = Instant.now()
        )
        val errors = listOf("URL should be prefixed", "Missing Content-Type")
        val invalidMocks = listOf(invalidMock to errors)

        val actualPrompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)
        
        println("=== ACTUAL CORRECTION PROMPT ===")
        println(actualPrompt)
        println("=== END ACTUAL CORRECTION PROMPT ===")
        println()
        println("Length: ${actualPrompt.length}")
        println("Lines: ${actualPrompt.lines().size}")
    }
}
