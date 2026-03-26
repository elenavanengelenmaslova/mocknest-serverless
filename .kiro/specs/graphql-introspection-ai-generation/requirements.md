# Requirements Document

## Introduction

This document specifies requirements for adding GraphQL schema introspection support to the existing AI mock generation flow in MockNest Serverless. The feature extends the current "create new mocks with AI" capability to support GraphQL endpoints by fetching schemas via introspection, reducing them to a compact representation, generating mocks using AI, and validating the output against the schema with automatic retry/correction.

## Glossary

- **GraphQL_Introspection_Client**: Component that fetches GraphQL schemas from live endpoints using the standard introspection query
- **Schema_Reducer**: Component that converts raw introspection JSON into a compact internal representation suitable for AI consumption
- **GraphQL_Mock_Generator**: Component that generates WireMock-compatible GraphQL-over-HTTP mock mappings using AI
- **GraphQL_Validator**: Component that validates generated GraphQL operations and response shapes against the introspected schema
- **Retry_Coordinator**: Component that orchestrates the validation-retry loop when AI output is invalid
- **AI_Generation_Flow**: The existing mock generation pipeline that currently supports REST via OpenAPI specifications
- **WireMock_Mapping**: JSON configuration that defines mock request matching and response behavior in the WireMock engine
- **GraphQL_Operation**: A GraphQL query or mutation with its operation name, arguments, and selection set
- **Introspection_Query**: The standard GraphQL introspection query that returns complete schema metadata
- **Compact_Schema**: Reduced schema representation containing only operation names, arguments, types, return types, enums, object fields, and descriptions

## Requirements

### Requirement 1: GraphQL Endpoint Input Support

**User Story:** As a developer, I want to provide a GraphQL endpoint URL and natural-language instructions, so that I can generate mocks for GraphQL APIs using the existing AI generation flow.

#### Acceptance Criteria

1. WHEN a user submits a GraphQL endpoint URL with instructions, THE AI_Generation_Flow SHALL accept the request using the existing request model
2. THE AI_Generation_Flow SHALL distinguish GraphQL requests from REST requests based on the SpecificationFormat parameter
3. THE AI_Generation_Flow SHALL reuse existing request validation and job tracking mechanisms for GraphQL requests
4. THE AI_Generation_Flow SHALL support both specification content (pre-fetched schema) and specification URL (introspection endpoint) input modes

### Requirement 2: GraphQL Schema Introspection

**User Story:** As a developer, I want the system to fetch GraphQL schemas automatically via introspection, so that I don't need to manually obtain and provide schema files.

#### Acceptance Criteria

1. WHEN a GraphQL endpoint URL is provided, THE GraphQL_Introspection_Client SHALL execute the standard introspection query against the endpoint
2. IF the endpoint is unreachable, THEN THE GraphQL_Introspection_Client SHALL return an error message indicating network failure
3. IF introspection is disabled on the endpoint, THEN THE GraphQL_Introspection_Client SHALL return an error message indicating introspection is not available
4. IF the endpoint returns invalid GraphQL response format, THEN THE GraphQL_Introspection_Client SHALL return an error message indicating invalid response structure
5. IF the endpoint times out or rate limits the request, THEN THE GraphQL_Introspection_Client SHALL return an error message indicating timeout or rate limiting
6. WHEN introspection succeeds, THE GraphQL_Introspection_Client SHALL return the complete schema introspection result as JSON

### Requirement 3: Schema Reduction for AI Consumption

**User Story:** As a system, I want to convert raw introspection JSON into a compact representation, so that AI token usage remains reasonable and the AI receives only relevant schema information.

#### Acceptance Criteria

1. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL extract query operation names, arguments, and return types
2. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL extract mutation operation names, arguments, and return types
3. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL extract input argument types and their fields
4. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL extract return object types and their fields
5. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL extract enum types and their possible values
6. WHEN raw introspection JSON is received, THE Schema_Reducer SHALL include field descriptions when they provide useful context
7. THE Schema_Reducer SHALL exclude introspection metadata fields that are not needed for mock generation
8. THE Schema_Reducer SHALL produce a Compact_Schema representation that is significantly smaller than the raw introspection JSON

### Requirement 4: GraphQL Mock Generation with AI

**User Story:** As a developer, I want the AI to generate GraphQL mock mappings from the schema and my instructions, so that I can quickly create realistic GraphQL mocks.

#### Acceptance Criteria

1. WHEN a Compact_Schema and user instructions are provided, THE GraphQL_Mock_Generator SHALL construct an AI prompt containing both the schema and instructions
2. THE GraphQL_Mock_Generator SHALL request the AI to generate WireMock_Mapping JSON compatible with GraphQL-over-HTTP
3. THE GraphQL_Mock_Generator SHALL generate mocks that match GraphQL POST requests with JSON payloads
4. THE GraphQL_Mock_Generator SHALL generate response bodies that conform to GraphQL response format with data and errors fields
5. THE GraphQL_Mock_Generator SHALL integrate with the existing AI model service interface without requiring new AI infrastructure

### Requirement 5: GraphQL Operation and Response Validation

**User Story:** As a system, I want to validate generated GraphQL operations and responses against the schema, so that invalid AI output is detected and corrected automatically.

#### Acceptance Criteria

1. WHEN a WireMock_Mapping is generated, THE GraphQL_Validator SHALL extract the GraphQL_Operation from the request matcher
2. THE GraphQL_Validator SHALL verify that the operation name exists in the Compact_Schema
3. THE GraphQL_Validator SHALL verify that all operation arguments match the schema-defined argument types
4. THE GraphQL_Validator SHALL verify that the response body contains required fields as defined in the schema
5. THE GraphQL_Validator SHALL verify that scalar field types in the response are compatible with schema-defined types
6. THE GraphQL_Validator SHALL verify that enum values in the response are valid according to the schema
7. THE GraphQL_Validator SHALL verify that list and object structures in the response match the schema
8. IF validation fails, THEN THE GraphQL_Validator SHALL return a list of specific validation errors with context

### Requirement 6: Automatic Retry and Correction Loop

**User Story:** As a system, I want to automatically retry AI generation with validation errors as feedback, so that invalid output is corrected without manual intervention.

#### Acceptance Criteria

1. WHEN THE GraphQL_Validator detects validation errors, THE Retry_Coordinator SHALL feed the errors back to the AI as additional context
2. THE Retry_Coordinator SHALL request the AI to regenerate the WireMock_Mapping with corrections
3. THE Retry_Coordinator SHALL limit retry attempts to prevent infinite loops
4. THE Retry_Coordinator SHALL reuse the existing retry/correction pattern from REST AI generation
5. WHEN validation succeeds after retry, THE Retry_Coordinator SHALL return the corrected WireMock_Mapping
6. IF validation fails after maximum retry attempts, THEN THE Retry_Coordinator SHALL return an error indicating generation failure

### Requirement 7: Integration with Existing Persistence and Runtime

**User Story:** As a developer, I want generated GraphQL mocks to be persisted and served by the existing runtime, so that GraphQL mocks work seamlessly with the rest of the system.

#### Acceptance Criteria

1. THE GraphQL_Mock_Generator SHALL produce WireMock_Mapping JSON that is compatible with the existing persistence model
2. THE AI_Generation_Flow SHALL persist generated GraphQL mocks using the existing storage interface
3. THE WireMock runtime SHALL serve GraphQL mocks using the existing GraphQL-over-HTTP support
4. THE AI_Generation_Flow SHALL preserve existing REST mock generation behavior without regression

### Requirement 8: Clean Architecture and Module Boundaries

**User Story:** As a developer, I want GraphQL-specific code to follow clean architecture principles, so that the codebase remains maintainable and testable.

#### Acceptance Criteria

1. THE GraphQL_Introspection_Client SHALL be implemented in the infrastructure layer with AWS-specific HTTP client code
2. THE Schema_Reducer SHALL be implemented in the application layer as protocol-specific logic
3. THE GraphQL_Validator SHALL be implemented in the application layer alongside the existing OpenAPIMockValidator
4. THE Compact_Schema domain model SHALL be defined in the domain layer
5. THE implementation SHALL reuse existing AI orchestration patterns from the MockGenerationFunctionalAgent
6. THE implementation SHALL not introduce AWS-specific code into the application or domain layers

### Requirement 9: Comprehensive Testing

**User Story:** As a developer, I want comprehensive automated tests for GraphQL introspection and generation, so that the feature is reliable and maintainable.

#### Acceptance Criteria

1. THE test suite SHALL include unit tests for successful introspection and schema reduction
2. THE test suite SHALL include unit tests for introspection failure scenarios (unreachable, disabled, invalid, timeout)
3. THE test suite SHALL include unit tests for validation failure detection and error reporting
4. THE test suite SHALL include unit tests for the retry/correction loop with both success and failure outcomes
5. THE test suite SHALL include integration tests using fixed mock introspection responses to avoid external dependencies
6. THE test suite SHALL include at least one optional manual test using a public GraphQL endpoint such as PokeAPI
7. THE test suite SHALL verify that REST AI generation continues to work without regression

### Requirement 10: GraphQL Schema Parsing and Pretty Printing

**User Story:** As a developer, I want to parse and pretty-print GraphQL schemas, so that round-trip testing can verify schema integrity.

#### Acceptance Criteria

1. WHEN a Compact_Schema is created from introspection, THE Schema_Reducer SHALL parse the introspection JSON into a structured representation
2. THE Schema_Reducer SHALL provide a pretty-print function that formats Compact_Schema back into human-readable GraphQL SDL format
3. FOR ALL valid Compact_Schema objects, parsing the introspection then pretty-printing then parsing again SHALL produce an equivalent schema representation (round-trip property)
4. THE pretty-print output SHALL include operation definitions with arguments and return types
5. THE pretty-print output SHALL include type definitions with fields
6. THE pretty-print output SHALL include enum definitions with possible values
