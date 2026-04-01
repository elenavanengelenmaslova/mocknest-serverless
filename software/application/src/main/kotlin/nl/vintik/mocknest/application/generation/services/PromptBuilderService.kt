package nl.vintik.mocknest.application.generation.services

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.MockNamespace
import nl.vintik.mocknest.domain.generation.SpecificationFormat
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
        namespace: MockNamespace,
        format: SpecificationFormat = specification.format
    ): String {
        val promptPath = when (format) {
            SpecificationFormat.GRAPHQL -> "/prompts/graphql/spec-with-description.txt"
            SpecificationFormat.WSDL -> "/prompts/soap/spec-with-description.txt"
            else -> "/prompts/rest/spec-with-description.txt"
        }
        val template = loadTemplate(promptPath)
        
        val keyEndpoints = specification.endpoints
            .joinToString("\n") { endpoint ->
                val response200 = endpoint.responses[200] ?: endpoint.responses[201]
                val responseType = response200?.schema?.type?.name ?: "OBJECT"
                if (format == SpecificationFormat.GRAPHQL && endpoint.operationId != null) {
                    "- ${endpoint.operationId} (Returns: $responseType)"
                } else {
                    "- ${endpoint.method} ${endpoint.path} (Returns: $responseType)"
                }
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
        specification: APISpecification?,
        format: SpecificationFormat = specification?.format ?: SpecificationFormat.OPENAPI_3
    ): String {
        val promptPath = when (format) {
            SpecificationFormat.GRAPHQL -> "/prompts/graphql/correction.txt"
            SpecificationFormat.WSDL -> "/prompts/soap/correction.txt"
            else -> "/prompts/rest/correction.txt"
        }
        val template = loadTemplate(promptPath)
        
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
     * Builds a prompt for retrying after a model response parsing failure.
     */
    fun buildParsingCorrectionPrompt(
        parsingError: String,
        namespace: MockNamespace,
        specification: APISpecification?
    ): String {
        val template = loadTemplate("/prompts/common/parsing-correction.txt")
        val clientSection = namespace.client?.let { "\n- Client: $it" } ?: ""
        val wireMockSchema = loadTemplate("/prompts/wiremock-stub-schema.yaml")

        return template
            .replace("{{PARSING_ERROR}}", parsingError)
            .replace("{{SPEC_TITLE}}", specification?.title ?: "Unknown")
            .replace("{{SPEC_VERSION}}", specification?.version ?: "Unknown")
            .replace("{{ENDPOINT_COUNT}}", (specification?.endpoints?.size ?: 0).toString())
            .replace("{{API_NAME}}", namespace.apiName)
            .replace("{{CLIENT_SECTION}}", clientSection)
            .replace("{{WIREMOCK_SCHEMA}}", wireMockSchema)
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
