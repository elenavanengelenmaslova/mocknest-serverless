# Requirements Document

## Introduction

This document specifies requirements for implementing AI-powered mock generation from OpenAPI specifications in MockNest Serverless. The focus is on the core value proposition: generate mocks from OpenAPI specs and return them to users, with optional direct application to MockNest.

**Core Scope:**
- Hello world endpoint to validate Bedrock integration
- OpenAPI specification parsing (OpenAPI 3.0 and Swagger 2.0 only)
- Synchronous mock generation from specifications with optional natural language instructions
- Return generated mocks in WireMock import JSON format
- Separate endpoint to apply generated mocks to MockNest via WireMock admin API

**Deferred to Future Spec (Mock Evolution):**
- Detecting API specification changes and updating existing mocks
- Specification storage and versioning
- Mock evolution based on traffic patterns
- Namespace storage organization

**Deferred to Future Phases:**
- Mock enhancement and refinement
- Traffic analysis integration
- GraphQL and WSDL support
- Batch generation
- Conversational interfaces (MCP)

## Glossary

- **MockNest_System**: The MockNest Serverless runtime including WireMock engine, AI generation components, and storage systems
- **Mock_Generator**: Component that creates WireMock mappings from OpenAPI specifications
- **Specification_Parser**: Component that processes OpenAPI specifications to extract endpoint definitions
- **API_Specification**: OpenAPI 3.0 or Swagger 2.0 specification describing REST API endpoints, schemas, and behaviors
- **Schema_Compliance**: Alignment between mock response structure and OpenAPI schema definitions
- **Mock_Validation**: Process of verifying generated mocks conform to the source OpenAPI specification
- **Validation_Retry**: Automatic retry of mock generation with validation errors included in the prompt when all mocks fail validation
- **Bedrock_Service**: Amazon Bedrock AI service used for generating mocks from natural language descriptions
- **Koog_Agent**: Functional agent framework used for orchestrating mock generation logic
- **WireMock_Import_Format**: Standard WireMock JSON format for importing mappings via admin API
- **WireMock_Mapping**: Complete WireMock mapping with UUID, priority, request, response, and persistence flag

## Requirements

### Requirement 1: Hello World Bedrock Integration

**User Story:** As a developer, I want to validate that Bedrock integration works correctly through Koog, so that I can confirm the AI infrastructure is properly configured before building complex generation features.

#### Acceptance Criteria

1. WHEN a user sends text input to the hello world endpoint, THE MockNest_System SHALL forward it to Bedrock_Service through Koog_Agent
2. WHEN Bedrock_Service processes the request, THE MockNest_System SHALL return the AI-generated response to the user
3. WHEN Bedrock_Service is unavailable, THE MockNest_System SHALL return a clear error message indicating the service status
4. WHEN the hello world endpoint is called, THE MockNest_System SHALL log the request and response for debugging purposes
5. THE hello world endpoint SHALL validate that Koog_Agent can successfully communicate with Bedrock_Service

### Requirement 2: OpenAPI Specification Parsing

**User Story:** As a developer, I want to parse OpenAPI specifications, so that I can extract endpoint definitions and schemas for mock generation.

#### Acceptance Criteria

1. WHEN a user provides an OpenAPI 3.0 specification, THE Specification_Parser SHALL extract all endpoint definitions with paths, methods, and parameters
2. WHEN a user provides a Swagger 2.0 specification, THE Specification_Parser SHALL convert it to internal format and extract endpoint definitions
3. WHEN parsing specifications, THE Specification_Parser SHALL extract response schemas for all defined status codes
4. WHEN specifications include example responses, THE Specification_Parser SHALL preserve those examples for mock generation
5. WHERE specifications contain invalid syntax, THE Specification_Parser SHALL return detailed validation errors with line numbers and descriptions

### Requirement 3: Mock Generation from OpenAPI

**User Story:** As a developer, I want to generate WireMock-ready mocks from OpenAPI specifications, so that I can quickly create comprehensive mock coverage for REST APIs.

#### Acceptance Criteria

1. WHEN generating mocks from OpenAPI specifications, THE Mock_Generator SHALL create WireMock mappings for all defined endpoints
2. WHEN generating response data, THE Mock_Generator SHALL create realistic JSON responses based on OpenAPI schemas
3. WHEN specifications define multiple response status codes, THE Mock_Generator SHALL generate separate mocks for happy path (2xx), client errors (4xx), and server errors (5xx)
4. WHEN specifications include example responses, THE Mock_Generator SHALL use those examples as mock response templates
5. THE Mock_Generator SHALL return generated mocks in WireMock_Import_Format that can be imported via standard WireMock admin API
6. WHEN mocks are generated, THE Mock_Generator SHALL validate each mock against the source OpenAPI specification
7. WHERE mocks fail validation, THE Mock_Generator SHALL collect all invalid mocks with their validation errors
8. WHEN invalid mocks are collected, THE Mock_Generator SHALL send all invalid mocks and their respective validation errors to the AI in a single request for batch correction
9. WHEN the AI returns corrected mocks, THE Mock_Generator SHALL validate each corrected mock
10. WHERE corrected mocks pass validation, THE Mock_Generator SHALL add them to the collection of valid mocks
11. THE Mock_Generator SHALL return all valid mocks (both originally valid and successfully corrected ones)
12. THE Mock_Generator SHALL log all validation failures, correction attempts, and final results

### Requirement 4: Natural Language Instructions

**User Story:** As a developer, I want to provide natural language instructions with my OpenAPI spec, so that I can customize mock generation behavior without modifying the specification.

#### Acceptance Criteria

1. WHEN users provide optional instructions with specifications, THE Mock_Generator SHALL use those instructions to guide mock generation
2. WHEN instructions specify response characteristics, THE Mock_Generator SHALL incorporate those characteristics into generated mocks
3. WHERE no instructions are provided, THE Mock_Generator SHALL generate standard mocks based solely on the OpenAPI specification
4. THE Mock_Generator SHALL combine OpenAPI schema constraints with natural language instructions to create appropriate mocks

### Requirement 5: Mock Application Endpoint

**User Story:** As a developer, I want to apply generated mocks to MockNest through a dedicated endpoint, so that I can review generated mocks before applying them and have full control over the application process.

#### Acceptance Criteria

1. WHEN a user sends generated mocks to the apply endpoint, THE MockNest_System SHALL convert each mock to complete WireMock_Mapping format
2. WHEN converting mocks, THE MockNest_System SHALL add a unique UUID identifier to each mapping
3. WHEN converting mocks, THE MockNest_System SHALL set the priority field to 2 for all mappings
4. WHEN converting mocks, THE MockNest_System SHALL set the persistent flag to true for all mappings
5. WHEN applying mocks, THE MockNest_System SHALL call AdminRequestUseCase for each mapping to create it in WireMock
6. WHEN application succeeds, THE MockNest_System SHALL return the list of created mapping IDs
7. WHERE application fails for any mock, THE MockNest_System SHALL return error details with the list of successfully created mapping IDs

### Requirement 6: Synchronous Generation Response

**User Story:** As a developer, I want to receive generated mocks immediately in the API response, so that I can quickly iterate on mock generation without polling for results.

#### Acceptance Criteria

1. WHEN generation completes successfully, THE MockNest_System SHALL return all generated mocks in the HTTP response
2. WHEN generation fails, THE MockNest_System SHALL return error details with partial results if available
3. THE MockNest_System SHALL complete generation requests within reasonable timeout limits (e.g., 30 seconds)
4. WHERE generation would exceed timeout limits, THE MockNest_System SHALL return an error indicating the specification is too large
5. THE MockNest_System SHALL return generated mocks in WireMock_Import_Format ready for immediate use

## Future Phase Requirements

The following requirements are explicitly deferred to future phases:

### Future Spec: Mock Evolution
- Detect API specification changes and suggest mock updates
- Compare specification versions and identify affected mocks
- Generate update suggestions for modified endpoints
- Store API specifications with versioning
- Namespace storage organization for tracking specifications over time

### Future Requirement 1: Mock Enhancement and Refinement
- AI-powered enhancement of existing mocks
- Improve mock realism using Bedrock
- Suggest additional response variations

### Future Requirement 2: Traffic Analysis Integration
- Analyze traffic patterns to identify mock gaps
- Suggest new mocks based on unmatched requests
- Provide coverage analysis against specifications

### Future Requirement 3: Additional Protocol Support
- GraphQL schema parsing and mock generation
- WSDL parsing and SOAP mock generation
- Protocol-specific mock patterns

### Future Requirement 4: Batch Generation
- Process multiple specifications in single job
- Handle naming conflicts with consistent prefixing
- Provide batch generation summary reports

### Future Requirement 5: Conversational Interfaces
- MCP (Model Context Protocol) support
- Interactive mock refinement through dialogue
- Context-aware mock generation