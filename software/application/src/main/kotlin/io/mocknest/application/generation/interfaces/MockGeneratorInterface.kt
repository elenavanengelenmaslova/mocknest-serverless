package io.mocknest.application.generation.interfaces

import io.mocknest.domain.generation.*

/**
 * Abstraction for mock generation logic.
 * Handles conversion from API specifications to WireMock mappings.
 */
interface MockGeneratorInterface {
    
    /**
     * Generate mocks from parsed API specification.
     */
    suspend fun generateFromSpecification(
        specification: APISpecification,
        namespace: MockNamespace,
        options: GenerationOptions = GenerationOptions.default()
    ): List<GeneratedMock>
    
    /**
     * Generate a single mock from an endpoint definition.
     */
    suspend fun generateFromEndpoint(
        endpoint: EndpointDefinition,
        namespace: MockNamespace,
        options: GenerationOptions = GenerationOptions.default()
    ): GeneratedMock
    
    /**
     * Generate realistic response data based on JSON schema.
     */
    suspend fun generateResponseData(
        schema: JsonSchema,
        useExamples: Boolean = true
    ): String
    
    /**
     * Convert endpoint definition to WireMock JSON mapping.
     */
    suspend fun createWireMockMapping(
        endpoint: EndpointDefinition,
        responseData: String,
        statusCode: Int = 200
    ): String
    
    /**
     * Validate that generated WireMock mapping is syntactically correct.
     */
    suspend fun validateWireMockMapping(mapping: String): ValidationResult
}

/**
 * Interface for generating realistic test data.
 */
interface TestDataGeneratorInterface {
    
    /**
     * Generate realistic data for a given JSON schema.
     */
    suspend fun generateForSchema(schema: JsonSchema): Any
    
    /**
     * Generate data for primitive types.
     */
    suspend fun generatePrimitive(type: JsonSchemaType, format: String? = null): Any
    
    /**
     * Generate data for complex objects.
     */
    suspend fun generateObject(properties: Map<String, JsonSchema>, required: List<String>): Map<String, Any>
    
    /**
     * Generate data for arrays.
     */
    suspend fun generateArray(itemSchema: JsonSchema, minItems: Int = 1, maxItems: Int = 5): List<Any>
}