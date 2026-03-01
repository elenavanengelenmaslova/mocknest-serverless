package nl.vintik.mocknest.infra.aws.generation.ai

import nl.vintik.mocknest.application.generation.services.PromptBuilderService
import nl.vintik.mocknest.domain.generation.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant

/**
 * Byte-for-byte prompt content verification
 * 
 * This test verifies that the refactored prompt building produces
 * EXACTLY the same output as the original implementation, character by character.
 * 
 * These tests compare actual prompt outputs against the documented baseline
 * from the original BedrockServiceAdapter implementation.
 */
class ByteForBytePromptComparisonTest {

    private val promptBuilder = PromptBuilderService()

    @Nested
    inner class SystemPromptByteComparison {

        @Test
        fun `Given system prompt When loaded Then should match original exactly`() {
            // Given - Expected system prompt from original BedrockServiceAdapter
            val expectedSystemPrompt = """You are an expert API mock generator.
You generate WireMock JSON mappings based on user instructions and specifications."""

            // When
            val actualSystemPrompt = promptBuilder.loadSystemPrompt()

            // Then - Byte-for-byte comparison
            assertEquals(
                expectedSystemPrompt,
                actualSystemPrompt,
                "System prompt must be byte-for-byte identical to original implementation"
            )
        }
    }

    @Nested
    inner class SpecPromptByteComparison {

        @Test
        fun `Given spec prompt without client When built Then should match original format exactly`() {
            // Given
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

            // Expected output from original implementation
            val expectedPrompt = """You are an expert API mock generator. Generate WireMock JSON mappings based on this API specification and enhancement description:

API Specification Summary:
- Title: Pet Store API
- Version: 1.0.0
- Endpoints: 2
- Key endpoints: GET /pets, POST /pets

Namespace:
- API Name: petstore

Enhancement Description: Add realistic error responses

Requirements:
- Generate WireMock mappings that follow the API specification structure
- IMPORTANT: All mock URLs must be prefixed with /petstore (e.g., if the spec has /users, the mock URL should be /petstore/users)
- Enhance the mappings based on the description (add error cases, specific data, behaviors, etc.)
- Include realistic response data that matches both the spec and description
- Handle appropriate HTTP status codes
- Include relevant headers and proper content types
- Generate comprehensive mappings that cover the described scenarios
- Prefer `jsonBody` over `body` for JSON responses to ensure easy readability and structure
- WireMock URL matching rules (IMPORTANT):
  - Do NOT use OpenAPI-style placeholders like `{petId}` in `url` or `urlPath`. WireMock treats them as literal text.
  - Use exactly ONE of these per mapping (never combine them):
    - `url` (exact full URL match)
    - `urlPath` (exact path match)
    - `urlPattern` (regex full URL match)
    - `urlPathPattern` (regex path match)  <-- preferred for path parameters
  - For endpoints with path parameters (e.g. `/pet/{petId}`), use `urlPathPattern` and a regex like:
    - `/petstore/pet/[^/]+`
  - If you return multiple mappings for the same endpoint, they MUST be non-overlapping and deterministic:
    - Either match specific IDs (`/petstore/pet/1`, `/petstore/pet/2`, …), OR
    - Add additional request constraints (queryParameters, headers, bodyPatterns), OR
    - Use `priority` to ensure deterministic selection.
  - Never output multiple mappings that would all match the same request unless they differ by `priority` and are intended as fallback.

Return only a JSON array of WireMock mappings. Each mapping should be a complete, valid WireMock JSON object.
Do not include any explanatory text, only the JSON array."""

            // When
            val actualPrompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then - Byte-for-byte comparison
            assertEquals(
                expectedPrompt,
                actualPrompt,
                "Spec prompt must be byte-for-byte identical to original implementation"
            )
        }

        @Test
        fun `Given spec prompt with client When built Then should match original format exactly`() {
            // Given
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
                    )
                ),
                schemas = emptyMap()
            )
            val description = "Test"
            val namespace = MockNamespace(apiName = "petstore", client = "acme-corp")

            // Expected output from original implementation
            val expectedPrompt = """You are an expert API mock generator. Generate WireMock JSON mappings based on this API specification and enhancement description:

API Specification Summary:
- Title: Pet Store API
- Version: 1.0.0
- Endpoints: 1
- Key endpoints: GET /pets

Namespace:
- API Name: petstore
- Client: acme-corp

Enhancement Description: Test

Requirements:
- Generate WireMock mappings that follow the API specification structure
- IMPORTANT: All mock URLs must be prefixed with /acme-corp/petstore (e.g., if the spec has /users, the mock URL should be /acme-corp/petstore/users)
- Enhance the mappings based on the description (add error cases, specific data, behaviors, etc.)
- Include realistic response data that matches both the spec and description
- Handle appropriate HTTP status codes
- Include relevant headers and proper content types
- Generate comprehensive mappings that cover the described scenarios
- Prefer `jsonBody` over `body` for JSON responses to ensure easy readability and structure
- WireMock URL matching rules (IMPORTANT):
  - Do NOT use OpenAPI-style placeholders like `{petId}` in `url` or `urlPath`. WireMock treats them as literal text.
  - Use exactly ONE of these per mapping (never combine them):
    - `url` (exact full URL match)
    - `urlPath` (exact path match)
    - `urlPattern` (regex full URL match)
    - `urlPathPattern` (regex path match)  <-- preferred for path parameters
  - For endpoints with path parameters (e.g. `/pet/{petId}`), use `urlPathPattern` and a regex like:
    - `/acme-corp/petstore/pet/[^/]+`
  - If you return multiple mappings for the same endpoint, they MUST be non-overlapping and deterministic:
    - Either match specific IDs (`/acme-corp/petstore/pet/1`, `/acme-corp/petstore/pet/2`, …), OR
    - Add additional request constraints (queryParameters, headers, bodyPatterns), OR
    - Use `priority` to ensure deterministic selection.
  - Never output multiple mappings that would all match the same request unless they differ by `priority` and are intended as fallback.

Return only a JSON array of WireMock mappings. Each mapping should be a complete, valid WireMock JSON object.
Do not include any explanatory text, only the JSON array."""

            // When
            val actualPrompt = promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)

            // Then - Byte-for-byte comparison
            assertEquals(
                expectedPrompt,
                actualPrompt,
                "Spec prompt with client must be byte-for-byte identical to original implementation"
            )
        }
    }

    @Nested
    inner class CorrectionPromptByteComparison {

        @Test
        fun `Given correction prompt without spec When built Then should match original format exactly`() {
            // Given
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

            // Expected output from original implementation
            val expectedPrompt = """You are an expert API mock generator. The following WireMock mappings failed validation against the specification.

Namespace:
- API Name: petstore

Please correct ALL of the following mocks to fix their respective errors:

Mock ID: mock-1
Current Mapping:
{"request":{"method":"GET","url":"/pet/123"},"response":{"status":200}}

Validation Errors:
- URL should be prefixed
- Missing Content-Type

Requirements:
- Return only a JSON array containing the corrected WireMock mappings.
- Each mapping should be a complete, valid WireMock JSON object.
- Fix all validation errors listed for each mock.
- Ensure all mock URLs are correctly prefixed with /petstore
- Maintain the same structure and intent as the original mocks.
- For REST API Prefer `jsonBody` over `body` for JSON responses.
- Do not include any explanatory text, only the JSON array."""

            // When
            val actualPrompt = promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, null)

            // Then - Byte-for-byte comparison
            assertEquals(
                expectedPrompt,
                actualPrompt,
                "Correction prompt must be byte-for-byte identical to original implementation"
            )
        }
    }
}
