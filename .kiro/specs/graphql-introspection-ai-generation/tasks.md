# Implementation Plan: GraphQL Introspection AI Generation

## Overview

This implementation plan breaks down the GraphQL introspection feature into discrete, actionable tasks following the phased development order specified in the design document. The feature extends the existing AI mock generation flow to support GraphQL APIs through schema introspection, reduction, generation, and validation.

The implementation follows clean architecture principles with strict layer separation: Domain → Application → Infrastructure → Integration → Documentation. Each task builds incrementally on previous work, with comprehensive testing integrated throughout.

## Tasks

- [x] 1. Phase 1: Domain Layer - Create GraphQL domain models and exceptions
  - [x] 1.1 Create CompactGraphQLSchema domain model with validation
    - Create `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/generation/CompactGraphQLSchema.kt`
    - Implement `CompactGraphQLSchema` data class with queries, mutations, types, enums, and metadata
    - Implement `GraphQLOperation`, `GraphQLArgument`, `GraphQLType`, `GraphQLField`, `GraphQLEnum`, `GraphQLSchemaMetadata` data classes
    - Add validation in `init` blocks (non-empty operations, non-blank names, non-empty fields)
    - Implement `prettyPrint()` function to format schema as GraphQL SDL
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 1.2 Write unit tests for CompactGraphQLSchema domain model
    - Create test file in `software/domain/src/test/kotlin/`
    - Test validation rules (empty operations, blank names, empty fields)
    - Test pretty-print output format
    - Test data class equality and immutability
    - _Requirements: 10.1, 10.2_

  - [x] 1.3 Create GraphQL-specific exceptions
    - Create `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/generation/GraphQLExceptions.kt`
    - Implement `GraphQLIntrospectionException` with message and optional cause
    - Implement `GraphQLSchemaParsingException` with message and optional cause
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 3.1_

  - [x] 1.4 Add GRAPHQL to SpecificationFormat enum
    - Locate existing `SpecificationFormat` enum in domain layer
    - Add `GRAPHQL` enum value
    - _Requirements: 1.2_

- [x] 2. Phase 2: Application Layer - Implement schema reduction, parsing, and validation
  - [x] 2.1 Implement GraphQLSchemaReducer with reduction logic
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/graphql/GraphQLSchemaReducer.kt`
    - Define `GraphQLSchemaReducerInterface` with `reduce(introspectionJson: String): CompactGraphQLSchema` method
    - Implement reduction algorithm: extract queries, mutations, input types, output types, enums
    - Exclude introspection metadata fields (`__schema`, `__type`, `__typename`)
    - Exclude built-in scalar types (String, Int, Float, Boolean, ID)
    - Include descriptions only for operations and types
    - Target 60-80% size reduction from raw introspection JSON
    - Use kotlinx-serialization for JSON parsing
    - Use kotlin-logging for structured logging
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [x] 2.2 Write unit tests for GraphQLSchemaReducer
    - Create test file with Given-When-Then naming convention
    - Test successful reduction of complete schema
    - Test extraction of queries with arguments and return types
    - Test extraction of mutations with arguments and return types
    - Test extraction of input types, output types, and enums
    - Test metadata field exclusion
    - Test size reduction (verify 40%+ reduction)
    - Use test data from `src/test/resources/graphql/introspection/`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8_

  - [x] 2.3 Write property test for schema extraction completeness
    - **Property 5: Schema Extraction Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    - Use JUnit 6 `@ParameterizedTest` with `@ValueSource` or `@MethodSource`
    - Create 10-20 diverse test data files in `src/test/resources/graphql/introspection/`:
      - `simple-schema.json` (1-5 operations)
      - `complex-schema.json` (20-50 operations)
      - `large-schema-100-ops.json` (100+ operations)
      - `nested-types-schema.json` (deeply nested types)
      - `with-enums-schema.json` (multiple enum types)
      - `minimal-schema.json` (absolute minimum valid schema)
      - `mutations-only-schema.json` (no queries, only mutations)
      - `queries-only-schema.json` (no mutations, only queries)
    - For each test file, verify all queries, mutations, types, and enums are extracted
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-5")`

  - [x] 2.4 Implement GraphQLSpecificationParser
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/parsers/GraphQLSpecificationParser.kt`
    - Implement `SpecificationParserInterface` for GraphQL
    - Accept both URL (for introspection) and pre-fetched schema content
    - Integrate with `GraphQLSchemaReducerInterface` (introspection client added later)
    - Convert `CompactGraphQLSchema` to `APISpecification` domain model
    - Each GraphQL operation becomes an `EndpointDefinition` with POST method
    - GraphQL types become `JsonSchema` definitions
    - Implement `supports(format)`, `validate(content, format)`, `extractMetadata(content, format)` methods
    - _Requirements: 1.1, 1.2, 1.4, 3.1_

  - [x] 2.5 Write unit tests for GraphQLSpecificationParser
    - Test parsing from pre-fetched schema content
    - Test conversion to APISpecification
    - Test metadata extraction
    - Test validation of invalid schemas
    - Test format support check
    - _Requirements: 1.2, 1.4_

  - [x] 2.6 Implement GraphQLMockValidator with validation rules
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/validators/GraphQLMockValidator.kt`
    - Implement `MockValidatorInterface` for GraphQL
    - Extract GraphQL operation from WireMock mapping JSON
    - Verify operation name exists in schema
    - Validate operation arguments match schema-defined types
    - Validate response body follows GraphQL format (data/errors fields)
    - Validate response contains required fields
    - Validate scalar types are compatible
    - Validate enum values are valid
    - Validate list/object structures match schema
    - Return `MockValidationResult` with specific error messages
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [x] 2.7 Write unit tests for GraphQLMockValidator
    - Test validation of valid mocks
    - Test detection of missing operations
    - Test detection of argument type mismatches
    - Test detection of missing required fields
    - Test detection of invalid enum values
    - Test detection of type incompatibilities
    - Test error message formatting with context
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [x] 2.8 Write property test for comprehensive mock validation
    - **Property 10: Comprehensive Mock Validation**
    - **Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6, 5.7**
    - Use JUnit 6 `@ParameterizedTest` with diverse mock examples
    - Create 10-15 test data files with valid and invalid mocks
    - Verify all validation rules are checked for each example
    - Verify validation errors are reported correctly
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-10")`

  - [x] 2.9 Reorganize existing REST prompts into subdirectory
    - Create directory `software/application/src/main/resources/prompts/rest/`
    - Move `spec-with-description.txt` to `prompts/rest/spec-with-description.txt`
    - Move `correction.txt` to `prompts/rest/correction.txt`
    - Keep `system-prompt.txt` and `wiremock-stub-schema.yaml` at `prompts/` root (shared)
    - _Note: This organizes prompts by API type for clarity_

  - [x] 2.10 Create GraphQL-specific generation prompt
    - Create `software/application/src/main/resources/prompts/graphql/spec-with-description.txt`
    - Adapt from REST prompt but focus on GraphQL-specific requirements:
      - Single POST endpoint pattern (no URL path parameters)
      - GraphQL operation matching in request body using `bodyPatterns` with `matchesJsonPath`
      - GraphQL response format with `data` and/or `errors` fields
      - Schema-based field validation (required fields, types, enums)
      - Operation name, arguments, and selection set matching
      - Remove REST-specific URL matching rules (urlPath, urlPattern, path parameters)
      - Keep WireMock schema reference and persistent flag requirements
    - Use same template variables: `{{SPEC_TITLE}}`, `{{SPEC_VERSION}}`, `{{ENDPOINT_COUNT}}`, `{{KEY_ENDPOINTS}}`, `{{API_NAME}}`, `{{CLIENT_SECTION}}`, `{{DESCRIPTION}}`, `{{NAMESPACE}}`, `{{WIREMOCK_SCHEMA}}`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 2.11 Create GraphQL-specific correction prompt
    - Create `software/application/src/main/resources/prompts/graphql/correction.txt`
    - Adapt from REST correction prompt but focus on GraphQL validation errors:
      - Operation name not found in schema
      - Argument type mismatches
      - Missing required response fields
      - Invalid enum values
      - Response format issues (missing data/errors fields)
      - Remove REST-specific error types (URL path issues, query parameter issues)
    - Use same template variables: `{{SPEC_CONTEXT}}`, `{{API_NAME}}`, `{{CLIENT_SECTION}}`, `{{MOCKS_WITH_ERRORS}}`, `{{NAMESPACE}}`
    - _Requirements: 5.8, 6.1, 6.2_

  - [x] 2.12 Update PromptBuilderService to support format-specific prompts
    - Update `buildSpecWithDescriptionPrompt` method to accept `SpecificationFormat` parameter
    - Add logic to select prompt path based on format:
      - `SpecificationFormat.GRAPHQL` → load from `/prompts/graphql/spec-with-description.txt`
      - `SpecificationFormat.OPENAPI_3` or `SWAGGER_2` → load from `/prompts/rest/spec-with-description.txt`
    - Update `buildCorrectionPrompt` method to accept `SpecificationFormat` parameter
    - Add logic to select correction prompt based on format:
      - `SpecificationFormat.GRAPHQL` → load from `/prompts/graphql/correction.txt`
      - `SpecificationFormat.OPENAPI_3` or `SWAGGER_2` → load from `/prompts/rest/correction.txt`
    - Keep `loadSystemPrompt()` unchanged (loads from `/prompts/system-prompt.txt`)
    - _Requirements: 1.2, 4.1_

  - [x] 2.13 Update PromptBuilderService tests for format-specific prompts
    - Update existing tests to pass `SpecificationFormat` parameter
    - Add test for GraphQL prompt loading
    - Add test for REST prompt loading
    - Verify correct prompt is selected based on format
    - Verify template variable replacement works for both formats
    - _Requirements: 1.2_

- [x] 3. Phase 3: Infrastructure Layer - Implement introspection client and integration
  - [x] 3.1 Add Kotlin HTTP client dependencies to Gradle
    - Update `software/infra/aws/generation/build.gradle.kts`
    - Add ktor-client-core, ktor-client-cio, ktor-client-content-negotiation, ktor-serialization-kotlinx-json
    - Version: 2.3.0 or compatible with project Kotlin version
    - _Requirements: 2.1_

  - [x] 3.2 Implement GraphQLIntrospectionClient with HTTP client
    - Create `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/graphql/GraphQLIntrospectionClient.kt`
    - Define `GraphQLIntrospectionClientInterface` with `introspect(endpointUrl, headers, timeoutMs)` method
    - Use Kotlin ktor HTTP client for requests
    - Execute standard GraphQL introspection query (from design document)
    - Handle network failures, timeouts, SSL errors
    - Handle introspection disabled errors
    - Handle invalid response format
    - Handle rate limiting (HTTP 429)
    - Return raw introspection JSON string
    - Throw `GraphQLIntrospectionException` with descriptive messages on failure
    - Use kotlin-logging for structured error logging
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.3 Write unit tests for GraphQLIntrospectionClient
    - Test successful introspection with valid endpoint (use mock HTTP server)
    - Test network failure scenarios (unreachable, timeout, SSL errors)
    - Test introspection disabled error handling
    - Test invalid response format handling
    - Test rate limiting response handling (HTTP 429)
    - Use MockK for HTTP client mocking
    - _Requirements: 2.2, 2.3, 2.4, 2.5_

  - [x] 3.4 Write property test for introspection success
    - **Property 4: Introspection Success Returns Valid JSON**
    - **Validates: Requirements 2.6**
    - Use JUnit 6 `@ParameterizedTest` with mock HTTP responses
    - Create 5-10 example introspection responses
    - Verify returned JSON is valid and contains schema information
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-4")`

  - [x] 3.5 Wire GraphQLIntrospectionClient into GraphQLSpecificationParser
    - Update `GraphQLSpecificationParser` constructor to accept `GraphQLIntrospectionClientInterface`
    - Implement URL detection logic (starts with "http")
    - Call introspection client when URL is provided
    - Use pre-fetched content when URL is not provided
    - _Requirements: 1.4, 2.1_

  - [x] 3.6 Write property test for dual input mode support
    - **Property 3: Dual Input Mode Support**
    - **Validates: Requirements 1.4**
    - Use JUnit 6 `@ParameterizedTest` with both URL and pre-fetched schema inputs
    - Update existing test file `GraphQLSpecificationParserDualInputPropertyTest.kt`
    - Uncomment and complete URL-based introspection test cases
    - Mock introspection client for URL-based tests
    - Verify both input modes produce valid APISpecification
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-3")`
    - _Note: Pre-fetched schema tests already implemented in Phase 2, this task completes URL-based testing_

  - [x] 3.7 Write integration tests for parser with introspection
    - Test parsing from URL (using mock introspection client)
    - Test parsing from pre-fetched schema
    - Test error propagation from introspection client
    - _Requirements: 1.4, 2.1_

  - [x] 3.8 Create Spring configuration for GraphQL components
    - Create `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/config/GraphQLGenerationConfig.kt`
    - Define `@Configuration` class with Spring beans
    - Register `GraphQLIntrospectionClient` bean
    - Register `GraphQLSchemaReducer` bean
    - Register `GraphQLSpecificationParser` bean with dependencies
    - Register `GraphQLMockValidator` bean
    - _Requirements: 1.1, 1.2_

- [x] 4. Phase 4: Integration - Wire components and test end-to-end flows
  - [x] 4.1 Register GraphQLSpecificationParser in CompositeSpecificationParserImpl
    - Locate `CompositeSpecificationParserImpl` in application layer
    - Add `GraphQLSpecificationParser` to list of parsers
    - Verify parser selection based on `SpecificationFormat.GRAPHQL`
    - _Requirements: 1.2_

  - [x] 4.2 Write property test for format-based parser selection
    - **Property 2: Format-Based Parser Selection**
    - **Validates: Requirements 1.2**
    - Use JUnit 6 `@ParameterizedTest` with `@EnumSource(SpecificationFormat.class)`
    - Test that GRAPHQL format routes to GraphQLSpecificationParser
    - Test that OPENAPI_3/SWAGGER_2 routes to OpenAPISpecificationParser
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-2")`

  - [x] 4.3 Register GraphQLMockValidator in validator registry
    - Locate validator registry in application layer
    - Add `GraphQLMockValidator` to list of validators
    - Ensure validator is selected for GraphQL mocks
    - _Requirements: 5.1_

  - [x] 4.4 Create test data resources for integration tests
    - Create directory structure: `src/test/resources/graphql/introspection/`, `schemas/`, `mocks/`, `invalid/`
    - Add sample introspection JSON files (simple, complex, PokeAPI)
    - Add sample compact schemas
    - Add sample generated mocks
    - Add invalid schemas for error testing
    - _Requirements: 9.5_

  - [x] 4.5 Write end-to-end integration test for GraphQL generation
    - Test complete flow: introspection → reduction → generation → validation
    - Use fixed mock introspection responses (no external dependencies)
    - Verify generated mocks are valid WireMock mappings
    - Verify mocks pass validation
    - _Requirements: 9.5_

  - [x] 4.6 Write integration test for validation-retry loop with correctable errors
    - Mock AI service to return invalid mock on first attempt
    - Mock AI service to return valid mock on retry
    - Verify retry coordinator feeds validation errors to AI
    - Verify corrected mock is accepted
    - _Requirements: 6.1, 6.2, 6.5_

  - [x] 4.7 Write integration test for validation-retry loop with uncorrectable errors
    - Mock AI service to return invalid mocks on all attempts
    - Verify retry coordinator respects max retry limit
    - Verify generation fails with accumulated errors
    - _Requirements: 6.3, 6.6_

  - [x] 4.8 Write property test for bounded retry attempts
    - **Property 12: Bounded Retry Attempts**
    - **Validates: Requirements 6.3**
    - Use JUnit 6 `@ParameterizedTest` with various retry configurations (0, 1, 2, 3 retries)
    - Verify retry coordinator limits attempts to configured maximum
    - Test with mock AI service that always fails
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-12")`

  - [x] 4.9 Write REST generation non-regression tests
    - Test OpenAPI specification generation still works
    - Test existing REST mocks are generated correctly
    - Verify no breaking changes to REST flow
    - _Requirements: 7.4, 9.7_

  - [x] 4.10 Write property test for REST generation non-regression
    - **Property 14: REST Generation Non-Regression**
    - **Validates: Requirements 7.4**
    - Use JUnit 6 `@ParameterizedTest` with 10-15 OpenAPI spec examples
    - Create test data files: `petstore-openapi.yaml`, `simple-rest-api.yaml`, `complex-rest-api.yaml`, etc.
    - Verify REST generation produces valid mocks for each example
    - Verify no breaking changes to REST flow
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-14")`

  - [x] 4.11 Write property test for GraphQL request acceptance
    - **Property 1: GraphQL Request Acceptance**
    - **Validates: Requirements 1.1**
    - Use JUnit 6 `@ParameterizedTest` with various GraphQL endpoint URLs and instructions
    - Create 10+ combinations of valid endpoints and instructions
    - Verify requests are accepted without format-related errors
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-1")`

  - [x] 4.12 Write property test for GraphQL-over-HTTP mock format
    - **Property 8: GraphQL-over-HTTP Mock Format**
    - **Validates: Requirements 4.3**
    - Use JUnit 6 `@ParameterizedTest` with 10+ generated mock examples
    - Verify generated mocks specify POST method
    - Verify mocks include JSON body matcher for GraphQL operation
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-8")`

  - [x] 4.13 Write property test for GraphQL response format compliance
    - **Property 9: GraphQL Response Format Compliance**
    - **Validates: Requirements 4.4**
    - Use JUnit 6 `@ParameterizedTest` with 10+ mock response examples
    - Verify response bodies contain data or errors fields
    - Verify compliance with GraphQL response specification
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-9")`

  - [x] 4.14 Write property test for validation error reporting
    - **Property 11: Validation Error Reporting**
    - **Validates: Requirements 5.8**
    - Use JUnit 6 `@ParameterizedTest` with 10+ invalid mock examples
    - Verify validator returns non-empty error list with context
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-11")`

  - [x] 4.15 Write property test for WireMock persistence compatibility
    - **Property 13: WireMock Persistence Compatibility**
    - **Validates: Requirements 7.1**
    - Use JUnit 6 `@ParameterizedTest` with 10+ generated mock examples
    - Verify generated mocks are valid WireMock JSON
    - Verify compatibility with existing persistence model
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-13")`

  - [x] 4.16 Write property test for schema round-trip preservation
    - **Property 15: Schema Round-Trip Preservation**
    - **Validates: Requirements 10.3, 10.4, 10.5, 10.6**
    - Use JUnit 6 `@ParameterizedTest` with 10+ compact schema examples
    - Parse introspection → pretty-print → parse SDL → compare
    - Verify operations, types, and enums are preserved
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-15")`

  - [x] 4.17 Write property test for metadata field exclusion
    - **Property 6: Metadata Field Exclusion**
    - **Validates: Requirements 3.7**
    - Use JUnit 6 `@ParameterizedTest` with 10+ introspection JSON examples
    - Verify compact schema excludes `__schema`, `__type`, `__typename`
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-6")`

  - [x] 4.18 Write property test for schema size reduction
    - **Property 7: Schema Size Reduction**
    - **Validates: Requirements 3.8**
    - Use JUnit 6 `@ParameterizedTest` with 10+ introspection JSON examples
    - Verify compact schema is at least 40% smaller than raw introspection
    - Tag test with `@Tag("graphql-introspection-ai-generation")` and `@Tag("Property-7")`

  - [x] 4.19 Write REST API property tests for non-regression
    - Create comprehensive property tests for REST/OpenAPI generation to ensure no regressions
    - Use JUnit 6 `@ParameterizedTest` with diverse OpenAPI examples
    - Create test data directory: `src/test/resources/openapi/specs/`
    - Add 15-20 OpenAPI spec examples:
      - `petstore-v3.yaml` (Swagger Petstore)
      - `simple-crud-api.yaml` (basic CRUD operations)
      - `complex-api-with-refs.yaml` (with $ref references)
      - `api-with-query-params.yaml` (query parameter handling)
      - `api-with-path-params.yaml` (path parameter handling)
      - `api-with-request-bodies.yaml` (POST/PUT with bodies)
      - `api-with-arrays.yaml` (array responses)
      - `api-with-nested-objects.yaml` (complex nested structures)
      - `api-with-enums.yaml` (enum types)
      - `api-with-auth.yaml` (authentication schemes)
      - `minimal-api.yaml` (minimal valid spec)
      - `large-api-50-endpoints.yaml` (large API)
    - Test properties:
      - All endpoints from spec are generated as mocks
      - URL paths are correctly prefixed with namespace
      - HTTP methods match spec
      - Response status codes match spec
      - Request/response schemas are valid
      - Query parameters are handled correctly
      - Path parameters are handled correctly
    - Tag tests with `@Tag("rest-api-property-tests")`
    - _Note: These tests ensure GraphQL changes don't break REST generation_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [-] 6. Phase 5: Documentation and Deployment - Update documentation and verify deployment
  - [x] 6.1 Create jar size validation test
    - Create `software/infra/aws/mocknest/src/test/kotlin/nl/vintik/mocknest/infra/aws/deployment/JarSizeValidationTest.kt`
    - Implement test that executes `./gradlew :software:infra:aws:mocknest:shadowJar` to build the jar
    - After build completes, check `build/dist/mocknest-serverless.jar` file size
    - Assert jar size is under 90MB (90 * 1024 * 1024 bytes)
    - Test should fail with clear error message if size exceeds 90MB limit
    - Log actual jar size in MB and percentage of 90MB limit used
    - Use `ProcessBuilder` or similar to execute Gradle command from test
    - Mark test with `@Tag("integration")` since it runs Gradle build
    - _Note: This prevents dependency bloat from new GraphQL libraries (ktor-client, graphql-java)_
    - _Note: This test runs as part of regular build pipeline, not just SAR publish_

  - [x] 6.2 Update API documentation with GraphQL examples
    - Update relevant documentation files in `docs/`
    - Add GraphQL endpoint examples
    - Add sample GraphQL generation requests
    - Add sample GraphQL mock responses
    - Document dual input mode (URL vs pre-fetched schema)
    - _Requirements: 1.1, 1.4_

  - [x] 6.3 Update README with GraphQL support information
    - Add GraphQL to list of supported specification formats
    - Add GraphQL generation example
    - Add link to GraphQL documentation
    - _Requirements: 1.1_

  - [ ] 6.4 Update Postman collection with GraphQL generation examples
    - Add GraphQL generation request to `docs/postman/AWS MockNest Serverless.postman_collection.json`
    - Include sample GraphQL endpoint URL
    - Include sample generation instructions
    - _Requirements: 1.1_

  - [x] 6.4 Verify code coverage meets 90% threshold
    - Run `./gradlew koverHtmlReport`
    - Verify aggregated coverage is at least 90%
    - Identify any coverage gaps and add tests if needed
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_


## Notes

- Tasks marked with `*` are optional testing tasks and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties with 100 iterations each
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows without external dependencies
- No infrastructure changes required - feature integrates with existing AWS deployment
- Implementation follows clean architecture: Domain → Application → Infrastructure → Integration
- All code follows Kotlin idioms, uses kotlin-logging, and includes proper error handling
- Test coverage target: 90% aggregated across all modules
