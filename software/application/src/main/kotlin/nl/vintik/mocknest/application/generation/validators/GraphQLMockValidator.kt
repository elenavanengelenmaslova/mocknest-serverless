package nl.vintik.mocknest.application.generation.validators

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import nl.vintik.mocknest.application.generation.interfaces.MockValidationResult
import nl.vintik.mocknest.application.generation.interfaces.MockValidatorInterface
import nl.vintik.mocknest.domain.generation.APISpecification
import nl.vintik.mocknest.domain.generation.EndpointDefinition
import nl.vintik.mocknest.domain.generation.GeneratedMock
import nl.vintik.mocknest.domain.generation.JsonSchema
import nl.vintik.mocknest.domain.generation.JsonSchemaType
import nl.vintik.mocknest.domain.generation.SpecificationFormat

private val logger = KotlinLogging.logger {}

/**
 * Validator for GraphQL mocks.
 * Validates generated GraphQL mocks against the introspected schema.
 */
class GraphQLMockValidator : MockValidatorInterface {

    override suspend fun validate(mock: GeneratedMock, specification: APISpecification): MockValidationResult {
        // Only validate GraphQL specifications
        if (specification.format != SpecificationFormat.GRAPHQL) {
            return MockValidationResult.valid()
        }

        logger.debug { "Validating GraphQL mock: ${mock.id}" }

        val errors = mutableListOf<String>()

        return runCatching {
            // Parse WireMock mapping JSON
            val wireMockJson = Json.parseToJsonElement(mock.wireMockMapping).jsonObject

            // Extract GraphQL operation from request body matcher
            val operation = extractGraphQLOperation(wireMockJson)
                ?: return@runCatching MockValidationResult.invalid(
                    listOf("Failed to extract GraphQL operation from WireMock mapping")
                )

            // Find matching endpoint in specification
            val endpoint = specification.endpoints.find { it.operationId == operation.operationName }
            if (endpoint == null) {
                errors.add("Operation '${operation.operationName}' not found in schema")
                return@runCatching MockValidationResult.invalid(errors)
            }

            // Validate operation arguments
            errors.addAll(validateArguments(operation, endpoint))

            // Validate response structure
            errors.addAll(validateResponseBody(wireMockJson, endpoint))

            if (errors.isEmpty()) {
                logger.debug { "GraphQL mock validation passed: ${mock.id}" }
                MockValidationResult.valid()
            } else {
                logger.warn { "GraphQL mock validation failed: ${mock.id}, errors: ${errors.size}" }
                MockValidationResult.invalid(errors)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                logger.error(exception) { "GraphQL mock validation error: ${mock.id}" }
                MockValidationResult.invalid(
                    listOf("Validation error: ${exception.message ?: "Unknown error"}")
                )
            }
        )
    }

    /**
     * Extracts GraphQL operation from WireMock mapping JSON.
     */
    private fun extractGraphQLOperation(wireMockJson: JsonObject): GraphQLOperationInfo? {
        return runCatching {
            // Extract request body matcher
            val request = wireMockJson["request"]?.jsonObject ?: return@runCatching null
            val bodyPatterns = request["bodyPatterns"]?.jsonArray ?: return@runCatching null

            // Find the body pattern containing an equalToJson matcher
            val equalToJson = bodyPatterns
                .mapNotNull { it.jsonObject["equalToJson"]?.jsonPrimitive?.contentOrNull }
                .firstOrNull() ?: return@runCatching null

            // Parse the GraphQL request body
            val graphqlRequest = Json.parseToJsonElement(equalToJson).jsonObject
            val query = graphqlRequest["query"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val operationName = graphqlRequest["operationName"]?.jsonPrimitive?.contentOrNull
            val variables = graphqlRequest["variables"]?.jsonObject

            // Extract operation name from query if not provided
            val finalOperationName = operationName ?: extractOperationNameFromQuery(query)

            GraphQLOperationInfo(
                operationName = finalOperationName,
                query = query,
                variables = variables?.toMap() ?: emptyMap()
            )
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to extract GraphQL operation from WireMock mapping" }
        }.getOrNull()
    }

    /**
     * Extracts operation name from GraphQL query string.
     */
    private fun extractOperationNameFromQuery(query: String): String {
        // Simple regex to extract operation name from query/mutation
        val regex = """(?:query|mutation)\s+(\w+)""".toRegex()
        val match = regex.find(query)
        return match?.groupValues?.get(1) ?: "UnknownOperation"
    }

    /**
     * Validates operation arguments against schema.
     */
    private fun validateArguments(
        operation: GraphQLOperationInfo,
        endpoint: EndpointDefinition
    ): List<String> {
        val errors = mutableListOf<String>()

        // Get expected arguments from endpoint request body schema
        val requestBody = endpoint.requestBody ?: return errors
        val requestSchema = requestBody.content["application/json"]?.schema ?: return errors
        val variablesSchema = requestSchema.properties["variables"] ?: return errors

        // Get required arguments
        val requiredArgs = variablesSchema.required
        val expectedArgs = variablesSchema.properties

        // Check for missing required arguments
        requiredArgs.forEach { argName ->
            if (!operation.variables.containsKey(argName)) {
                errors.add("Missing required argument: '$argName'")
            }
        }

        // Validate argument types
        operation.variables.forEach { (argName, argValue) ->
            val expectedSchema = expectedArgs[argName]
            if (expectedSchema == null) {
                errors.add("Unexpected argument: '$argName' not defined in schema")
            } else {
                val typeError = validateJsonElementAgainstSchema(argValue, expectedSchema, argName)
                if (typeError != null) {
                    errors.add(typeError)
                }
            }
        }

        return errors
    }

    /**
     * Validates response body against schema.
     */
    private fun validateResponseBody(
        wireMockJson: JsonObject,
        endpoint: EndpointDefinition
    ): List<String> {
        val errors = mutableListOf<String>()

        return runCatching {
            // Extract response body
            val response = wireMockJson["response"]?.jsonObject ?: return@runCatching errors
            val bodyString = response["body"]?.jsonPrimitive?.contentOrNull
                ?: response["jsonBody"]?.toString()
                ?: return@runCatching errors

            val responseBody = Json.parseToJsonElement(bodyString).jsonObject

            // Validate GraphQL response format (must have data or errors field)
            if (!responseBody.containsKey("data") && !responseBody.containsKey("errors")) {
                errors.add("Response must contain 'data' or 'errors' field (GraphQL response format)")
                return@runCatching errors
            }

            // If errors field exists, validate it
            responseBody["errors"]?.let { errorsField ->
                if (errorsField !is JsonArray) {
                    errors.add("Response 'errors' field must be an array")
                }
            }

            // Validate data field against schema
            responseBody["data"]?.let { dataField ->
                val responseSchema = endpoint.responses[200]?.schema
                if (responseSchema != null) {
                    val dataSchema = responseSchema.properties.get("data")
                    if (dataSchema != null) {
                        errors.addAll(validateDataAgainstSchema(dataField, dataSchema, "data"))
                    }
                }
            }

            errors
        }.fold(
            onSuccess = { it },
            onFailure = { exception ->
                logger.warn(exception) { "Failed to validate response body" }
                errors.add("Response body validation error: ${exception.message}")
                errors
            }
        )
    }

    /**
     * Validates a data value against a JSON schema.
     */
    private fun validateDataAgainstSchema(
        data: JsonElement,
        schema: JsonSchema,
        path: String
    ): List<String> {
        val errors = mutableListOf<String>()

        when (schema.type) {
            JsonSchemaType.OBJECT -> {
                if (data !is JsonObject) {
                    errors.add("Field '$path' must be an object")
                    return errors
                }

                // Check required fields
                schema.required.forEach { requiredField ->
                    if (!data.containsKey(requiredField)) {
                        errors.add("Missing required field: '$path.$requiredField'")
                    }
                }

                // Validate each field
                schema.properties.forEach { (fieldName, fieldSchema) ->
                    data[fieldName]?.let { fieldValue ->
                        errors.addAll(
                            validateDataAgainstSchema(
                                fieldValue,
                                fieldSchema,
                                "$path.$fieldName"
                            )
                        )
                    }
                }
            }

            JsonSchemaType.ARRAY -> {
                if (data !is JsonArray) {
                    errors.add("Field '$path' must be an array")
                    return errors
                }

                // Validate each item
                schema.items?.let { itemSchema ->
                    data.forEachIndexed { index, item ->
                        errors.addAll(
                            validateDataAgainstSchema(
                                item,
                                itemSchema,
                                "$path[$index]"
                            )
                        )
                    }
                }
            }

            JsonSchemaType.STRING -> {
                if (data !is JsonPrimitive || !data.isString) {
                    errors.add("Field '$path' must be a string")
                    return errors
                }

                // Validate enum values (only when enum list is non-empty)
                schema.enum.takeIf { it.isNotEmpty() }?.let { enumValues ->
                    if (!enumValues.contains(data.content)) {
                        errors.add("Field '$path' has invalid enum value: '${data.content}'. Valid values: ${enumValues.joinToString(", ")}")
                    }
                }
            }

            JsonSchemaType.INTEGER -> {
                if (data !is JsonPrimitive || data.intOrNull == null) {
                    errors.add("Field '$path' must be an integer")
                }
            }

            JsonSchemaType.NUMBER -> {
                if (data !is JsonPrimitive || (data.intOrNull == null && data.doubleOrNull == null)) {
                    errors.add("Field '$path' must be a number")
                }
            }

            JsonSchemaType.BOOLEAN -> {
                if (data !is JsonPrimitive || data.booleanOrNull == null) {
                    errors.add("Field '$path' must be a boolean")
                }
            }

            JsonSchemaType.NULL -> {
                if (data !is JsonNull) {
                    errors.add("Field '$path' must be null")
                }
            }
        }

        return errors
    }

    /**
     * Validates a JsonElement value against a JSON schema (for arguments extracted from JSON body).
     */
    private fun validateJsonElementAgainstSchema(
        value: JsonElement,
        schema: JsonSchema,
        fieldName: String
    ): String? {
        return when (schema.type) {
            JsonSchemaType.STRING -> {
                if (value !is JsonPrimitive || !value.isString) "Argument '$fieldName' must be a string" else null
            }
            JsonSchemaType.INTEGER -> {
                if (value !is JsonPrimitive || value.intOrNull == null) "Argument '$fieldName' must be an integer" else null
            }
            JsonSchemaType.NUMBER -> {
                if (value !is JsonPrimitive || (value.intOrNull == null && value.doubleOrNull == null)) "Argument '$fieldName' must be a number" else null
            }
            JsonSchemaType.BOOLEAN -> {
                if (value !is JsonPrimitive || value.booleanOrNull == null) "Argument '$fieldName' must be a boolean" else null
            }
            JsonSchemaType.OBJECT -> {
                if (value !is JsonObject) "Argument '$fieldName' must be an object" else null
            }
            JsonSchemaType.ARRAY -> {
                if (value !is JsonArray) "Argument '$fieldName' must be an array" else null
            }
            JsonSchemaType.NULL -> {
                if (value !is JsonNull) "Argument '$fieldName' must be null" else null
            }
        }
    }
}

/**
 * Information about a GraphQL operation extracted from WireMock mapping.
 */
private data class GraphQLOperationInfo(
    val operationName: String,
    val query: String,
    val variables: Map<String, JsonElement>
)
