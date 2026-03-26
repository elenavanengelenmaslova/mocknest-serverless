package nl.vintik.mocknest.application.generation.services

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockNamespace
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service responsible for building AI prompts from templates.
 * Loads prompt templates from classpath resources and injects parameters.
 */
@Service
class PromptBuilderService {

    /**
     * Loads the system prompt for agent initialization.
     */
    fun loadSystemPrompt(): String {
        return loadTemplate("/prompts/system-prompt.txt")
    }

    /**
     * Builds a prompt for generating mocks from API specification with description.
     */
    fun buildSpecWithDescriptionPrompt(
        specification: APISpecification,
        description: String,
        namespace: MockNamespace
    ): String {
        val template = loadTemplate("/prompts/spec-with-description.txt")
        
        val keyEndpoints = specification.endpoints
            .joinToString("\n") { endpoint ->
                val response200 = endpoint.responses[200] ?: endpoint.responses[201]
                val responseType = response200?.schema?.type?.name ?: "OBJECT"
                "- ${endpoint.method} ${endpoint.path} (Returns: $responseType)"
            }
        
        val clientSection = namespace.client?.let { "\n- Client: $it" } ?: ""
        
        val wireMockSchema = loadTemplate("/prompts/wiremock-stub-schema.yaml")

        return template
            .replace("{{SPEC_TITLE}}", specification.title)
            .replace("{{SPEC_VERSION}}", specification.version)
            .replace("{{ENDPOINT_COUNT}}", specification.endpoints.size.toString())
            .replace("{{KEY_ENDPOINTS}}", keyEndpoints)
            .replace("{{API_NAME}}", namespace.apiName)
            .replace("{{CLIENT_SECTION}}", clientSection)
            .replace("{{DESCRIPTION}}", description)
            .replace("{{NAMESPACE}}", namespace.displayName())
            .replace("{{WIREMOCK_SCHEMA}}", wireMockSchema)
    }

    /**
     * Builds a prompt for correcting invalid mocks.
     */
    fun buildCorrectionPrompt(
        invalidMocks: List<Pair<GeneratedMock, List<String>>>,
        namespace: MockNamespace,
        specification: APISpecification?
    ): String {
        val template = loadTemplate("/prompts/correction.txt")
        
        val specContext = specification?.let {
            """
API Specification Context:
- Title: ${it.title}
- Version: ${it.version}
- Endpoints: ${it.endpoints.size}

"""
        } ?: ""
        
        val clientSection = namespace.client?.let { "\n- Client: $it" } ?: ""
        
        val mocksWithErrors = invalidMocks.joinToString("\n\n---\n\n") { (mock, errors) ->
            """Mock ID: ${mock.id}
Current Mapping:
${mock.wireMockMapping}

Validation Errors:
${errors.joinToString("\n") { "- $it" }}"""
        }
        
        return template
            .replace("{{SPEC_CONTEXT}}", specContext)
            .replace("{{API_NAME}}", namespace.apiName)
            .replace("{{CLIENT_SECTION}}", clientSection)
            .replace("{{MOCKS_WITH_ERRORS}}", mocksWithErrors)
            .replace("{{NAMESPACE}}", namespace.displayName())
    }

    /**
     * Loads a template from classpath resources.
     */
    private fun loadTemplate(resourcePath: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Template not found: $resourcePath"
        }
        return stream.use { it.bufferedReader().readText() }
    }
}
