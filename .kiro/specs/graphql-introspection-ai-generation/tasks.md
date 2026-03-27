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

  - [ ] 2.3 Write property test for schema extraction completeness
    - **Property 5: Schema Extraction Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    - Use Kotest property testing with 100 iterations
    - Generate random valid introspection JSON
    - Verify all queries, mutations, types, and enums are extracted
    - Tag test with feature name and property number

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

  - [ ] 2.5 Write unit tests for GraphQLSpecificationParser
    - Test parsing from pre-fetched schema content
    - Test conversion to APISpecification
    - Test metadata extraction
    - Test validation of invalid schemas
    - Test format support check
    - _Requirements: 1.2, 1.4_

  - [ ]* 2.6 Write property test for dual input mode support
    - **Property 3: Dual Input Mode Support**
    - **Validates: Requirements 1.4**
    - Test both URL and pre-fetched schema content inputs
    - Verify both produce valid APISpecification

  - [x] 2.7 Implement GraphQLMockValidator with validation rules
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

  - [ ] 2.8 Write unit tests for GraphQLMockValidator
    - Test validation of valid mocks
    - Test detection of missing operations
    - Test detection of argument type mismatches
    - Test detection of missing required fields
    - Test detection of invalid enum values
    - Test detection of type incompatibilities
    - Test error message formatting with context
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]* 2.9 Write property test for comprehensive mock validation
    - **Property 10: Comprehensive Mock Validation**
    - **Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6, 5.7**
    - Generate random valid mocks and schemas
    - Verify all validation rules are checked
    - Verify validation errors are reported correctly

- [ ] 3. Phase 3: Infrastructure Layer - Implement introspection client and integration
  - [ ] 3.1 Add Kotlin HTTP client dependencies to Gradle
    - Update `software/infra/aws/generation/build.gradle.kts`
    - Add ktor-client-core, ktor-client-cio, ktor-client-content-negotiation, ktor-serialization-kotlinx-json
    - Version: 2.3.0 or compatible with project Kotlin version
    - _Requirements: 2.1_

  - [ ] 3.2 Implement GraphQLIntrospectionClient with HTTP client
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

  - [ ]* 3.3 Write unit tests for GraphQLIntrospectionClient
    - Test successful introspection with valid endpoint (use mock HTTP server)
    - Test network failure scenarios (unreachable, timeout, SSL errors)
    - Test introspection disabled error handling
    - Test invalid response format handling
    - Test rate limiting response handling (HTTP 429)
    - Use MockK for HTTP client mocking
    - _Requirements: 2.2, 2.3, 2.4, 2.5_

  - [ ]* 3.4 Write property test for introspection success
    - **Property 4: Introspection Success Returns Valid JSON**
    - **Validates: Requirements 2.6**
    - Mock successful introspection responses
    - Verify returned JSON is valid and contains schema information

  - [ ] 3.5 Wire GraphQLIntrospectionClient into GraphQLSpecificationParser
    - Update `GraphQLSpecificationParser` constructor to accept `GraphQLIntrospectionClientInterface`
    - Implement URL detection logic (starts with "http")
    - Call introspection client when URL is provided
    - Use pre-fetched content when URL is not provided
    - _Requirements: 1.4, 2.1_

  - [ ]* 3.6 Write integration tests for parser with introspection
    - Test parsing from URL (using mock introspection client)
    - Test parsing from pre-fetched schema
    - Test error propagation from introspection client
    - _Requirements: 1.4, 2.1_

  - [ ] 3.7 Create Spring configuration for GraphQL components
    - Create `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/config/GraphQLGenerationConfig.kt`
    - Define `@Configuration` class with Spring beans
    - Register `GraphQLIntrospectionClient` bean
    - Register `GraphQLSchemaReducer` bean
    - Register `GraphQLSpecificationParser` bean with dependencies
    - Register `GraphQLMockValidator` bean
    - _Requirements: 1.1, 1.2_

- [ ] 4. Phase 4: Integration - Wire components and test end-to-end flows
  - [ ] 4.1 Register GraphQLSpecificationParser in CompositeSpecificationParserImpl
    - Locate `CompositeSpecificationParserImpl` in application layer
    - Add `GraphQLSpecificationParser` to list of parsers
    - Verify parser selection based on `SpecificationFormat.GRAPHQL`
    - _Requirements: 1.2_

  - [ ]* 4.2 Write property test for format-based parser selection
    - **Property 2: Format-Based Parser Selection**
    - **Validates: Requirements 1.2**
    - Test that GRAPHQL format routes to GraphQLSpecificationParser
    - Test that OPENAPI_3/SWAGGER_2 routes to OpenAPISpecificationParser

  - [ ] 4.3 Register GraphQLMockValidator in validator registry
    - Locate validator registry in application layer
    - Add `GraphQLMockValidator` to list of validators
    - Ensure validator is selected for GraphQL mocks
    - _Requirements: 5.1_

  - [ ] 4.4 Create test data resources for integration tests
    - Create directory structure: `src/test/resources/graphql/introspection/`, `schemas/`, `mocks/`, `invalid/`
    - Add sample introspection JSON files (simple, complex, PokeAPI)
    - Add sample compact schemas
    - Add sample generated mocks
    - Add invalid schemas for error testing
    - _Requirements: 9.5_

  - [ ] 4.5 Write end-to-end integration test for GraphQL generation
    - Test complete flow: introspection → reduction → generation → validation
    - Use fixed mock introspection responses (no external dependencies)
    - Verify generated mocks are valid WireMock mappings
    - Verify mocks pass validation
    - _Requirements: 9.5_

  - [ ] 4.6 Write integration test for validation-retry loop with correctable errors
    - Mock AI service to return invalid mock on first attempt
    - Mock AI service to return valid mock on retry
    - Verify retry coordinator feeds validation errors to AI
    - Verify corrected mock is accepted
    - _Requirements: 6.1, 6.2, 6.5_

  - [ ] 4.7 Write integration test for validation-retry loop with uncorrectable errors
    - Mock AI service to return invalid mocks on all attempts
    - Verify retry coordinator respects max retry limit
    - Verify generation fails with accumulated errors
    - _Requirements: 6.3, 6.6_

  - [ ] 4.8 Write property test for bounded retry attempts
    - **Property 12: Bounded Retry Attempts**
    - **Validates: Requirements 6.3**
    - Verify retry coordinator limits attempts to configured maximum
    - Test with various retry configurations

  - [ ]* 4.9 Write REST generation non-regression tests
    - Test OpenAPI specification generation still works
    - Test existing REST mocks are generated correctly
    - Verify no breaking changes to REST flow
    - _Requirements: 7.4, 9.7_

  - [ ]* 4.10 Write property test for REST generation non-regression
    - **Property 14: REST Generation Non-Regression**
    - **Validates: Requirements 7.4**
    - Generate random valid OpenAPI specs
    - Verify REST generation produces valid mocks

  - [ ] 4.11 Write property test for GraphQL request acceptance
    - **Property 1: GraphQL Request Acceptance**
    - **Validates: Requirements 1.1**
    - Generate random valid GraphQL endpoints and instructions
    - Verify requests are accepted without format-related errors

  - [ ] 4.12 Write property test for GraphQL-over-HTTP mock format
    - **Property 8: GraphQL-over-HTTP Mock Format**
    - **Validates: Requirements 4.3**
    - Verify generated mocks specify POST method
    - Verify mocks include JSON body matcher for GraphQL operation

  - [ ] 4.13 Write property test for GraphQL response format compliance
    - **Property 9: GraphQL Response Format Compliance**
    - **Validates: Requirements 4.4**
    - Verify response bodies contain data or errors fields
    - Verify compliance with GraphQL response specification

  - [ ] 4.14 Write property test for validation error reporting
    - **Property 11: Validation Error Reporting**
    - **Validates: Requirements 5.8**
    - Generate invalid mocks
    - Verify validator returns non-empty error list with context

  - [ ] 4.15 Write property test for WireMock persistence compatibility
    - **Property 13: WireMock Persistence Compatibility**
    - **Validates: Requirements 7.1**
    - Verify generated mocks are valid WireMock JSON
    - Verify compatibility with existing persistence model

  - [ ] 4.16 Write property test for schema round-trip preservation
    - **Property 15: Schema Round-Trip Preservation**
    - **Validates: Requirements 10.3, 10.4, 10.5, 10.6**
    - Parse introspection → pretty-print → parse SDL → compare
    - Verify operations, types, and enums are preserved

  - [ ] 4.17 Write property test for metadata field exclusion
    - **Property 6: Metadata Field Exclusion**
    - **Validates: Requirements 3.7**
    - Verify compact schema excludes `__schema`, `__type`, `__typename`

  - [ ] 4.18 Write property test for schema size reduction
    - **Property 7: Schema Size Reduction**
    - **Validates: Requirements 3.8**
    - Verify compact schema is at least 40% smaller than raw introspection

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Phase 5: Documentation and Deployment - Update documentation and verify deployment
  - [ ] 6.1 Create jar size validation test
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

  - [ ] 6.2 Update API documentation with GraphQL examples
    - Update relevant documentation files in `docs/`
    - Add GraphQL endpoint examples
    - Add sample GraphQL generation requests
    - Add sample GraphQL mock responses
    - Document dual input mode (URL vs pre-fetched schema)
    - _Requirements: 1.1, 1.4_

  - [ ] 6.3 Update README with GraphQL support information
    - Add GraphQL to list of supported specification formats
    - Add GraphQL generation example
    - Add link to GraphQL documentation
    - _Requirements: 1.1_

  - [ ] 6.4 Update Postman collection with GraphQL generation examples
    - Add GraphQL generation request to `docs/postman/AWS MockNest Serverless.postman_collection.json`
    - Include sample GraphQL endpoint URL
    - Include sample generation instructions
    - _Requirements: 1.1_

  - [ ] 6.4 Verify code coverage meets 90% threshold
    - Run `./gradlew koverHtmlReport`
    - Verify aggregated coverage is at least 90%
    - Identify any coverage gaps and add tests if needed
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 6.5 Build and deploy to AWS Lambda
    - Run full build with tests: `./gradlew clean build`
    - Verify jar size validation test passes (confirms jar < 90MB)
    - Deploy using existing SAM template (no infrastructure changes needed)
    - Verify deployment succeeds
    - _Requirements: 7.1, 7.2, 7.3_

  - [ ] 6.6 Verify REST generation still works in deployed environment
    - Test OpenAPI generation endpoint
    - Verify existing REST mocks work correctly
    - Confirm no regression in REST functionality
    - _Requirements: 7.4_

  - [ ] 6.7 Test GraphQL generation in deployed environment
    - Test GraphQL generation endpoint with sample schema
    - Verify generated mocks are persisted correctly
    - Verify generated mocks are served by WireMock runtime
    - Test with both URL and pre-fetched schema inputs
    - _Requirements: 1.1, 1.4, 7.2, 7.3_

  - [ ] 6.8 Monitor CloudWatch logs and metrics
    - Verify introspection requests are logged correctly
    - Verify validation errors are logged with context
    - Check for any unexpected errors or warnings
    - Verify performance is within acceptable range (< 1s overhead)
    - _Requirements: 2.1, 5.8_

- [ ] 7. Final checkpoint - Ensure all tests pass and deployment is verified
  - Ensure all tests pass, ask the user if questions arise.

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
