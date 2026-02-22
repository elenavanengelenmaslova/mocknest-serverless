# Implementation Plan: AI Mock Generation - Phase 1

## Overview
Implement Phase 1 of AI-powered mock generation focusing on:
1. **Hello World Endpoint**: Validate Bedrock + Koog integration
2. **OpenAPI Mock Generation**: Generate WireMock-ready mocks from OpenAPI 3.0 and Swagger 2.0
3. **Synchronous Response**: Return generated mocks immediately in HTTP response
4. **Brave Mode**: Optional direct application of mocks to MockNest via WireMock admin API

**Phase 1 Scope:**
- Hello world endpoint for Bedrock validation
- OpenAPI specification parsing only (no GraphQL/WSDL)
- Scenario-based mock generation (happy path, errors, edge cases)
- Synchronous generation with immediate response
- Optional brave mode for direct mock application

**Deferred to Future Spec (Mock Evolution):**
- Specification storage and versioning
- Detecting API changes and updating mocks
- Namespace storage organization
- Mock evolution based on traffic patterns

**Deferred to Future Phases:**
- Natural language mock generation (beyond hello world)
- Mock enhancement and refinement
- GraphQL and WSDL support
- Batch generation
- Traffic analysis integration

## Tasks

- [x] 1. Set up domain models and interfaces for Phase 1
  - Create domain models for mock generation requests and responses
  - Define clean architecture interfaces for AI services and WireMock admin
  - Set up synchronous generation models
  - _Requirements: 1, 2, 3, 4, 5, 6_

- [x] 1.1 Create core domain models
  - Implement MockGenerationRequest with optional brave mode
  - Implement GeneratedMock with WireMock JSON format
  - Implement GenerationResponse with applied mocks tracking
  - Implement ApplyOptions for brave mode configuration
  - _Requirements: 3.1, 5.1, 5.2, 6.1_

- [x] 1.2 Define application layer interfaces
  - Create AIModelServiceInterface abstraction (hides Bedrock)
  - Create SpecificationParserInterface for OpenAPI formats
  - Create MockGeneratorInterface for generation logic
  - Create WireMockAdminInterface for brave mode
  - _Requirements: Clean architecture separation_

- [x] 2. Implement hello world endpoint (Bedrock validation)
  - Create HelloWorldUseCase for text processing
  - Implement hello world REST controller
  - Add error handling for Bedrock unavailability
  - Add logging for debugging Bedrock integration
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  - _Note: Implemented as TestAgentRequestUseCase with /ai/test-agent/chat endpoint_

- [x] 2.1 Hello world use case
  - Implement HelloWorldUseCase with AIModelServiceInterface
  - Handle Bedrock responses and errors
  - Return HelloWorldResponse with success/error status
  - _Requirements: 1.1, 1.2, 1.3_
  - _Note: Implemented as TestAgentRequestUseCase_

- [x] 2.2 Hello world REST endpoint
  - Create /ai/hello POST endpoint
  - Accept text input in request body
  - Return AI-generated response or error
  - _Requirements: 1.1, 1.2, 1.3_
  - _Note: Implemented as /ai/test-agent/chat endpoint_

- [x]* 2.3 Test hello world integration
  - Write unit tests for HelloWorldUseCase
  - Write integration tests with mock Bedrock responses
  - Test error handling when Bedrock is unavailable
  - _Requirements: 1.3, 1.4_

- [x] 3. Implement Bedrock service adapter
  - Create BedrockServiceAdapter implementing AIModelServiceInterface
  - Set up Claude 3 Sonnet integration for hello world
  - Implement prompt building and response parsing
  - Add error handling and retry logic
  - _Requirements: 1.1, 1.2, 1.3_
  - _Note: Implemented as BedrockServiceAdapter and BedrockTestKoogAgent_

- [x] 3.1 Bedrock adapter implementation
  - Implement processHelloWorld method with Bedrock SDK
  - Configure Claude 3 Sonnet model
  - Build Claude-compatible prompts
  - Parse Claude responses correctly
  - _Requirements: 1.1, 1.2_
  - _Note: Implemented in BedrockServiceAdapter_

- [x] 3.2 Bedrock error handling
  - Handle service unavailable errors
  - Handle invalid model responses
  - Implement timeout handling
  - Add comprehensive logging
  - _Requirements: 1.3, 1.4_
  - _Note: Implemented in BedrockServiceAdapter_

- [ ]* 3.3 Test Bedrock adapter
  - Write unit tests with mock Bedrock client
  - Test prompt building and response parsing
  - Test error scenarios and timeouts
  - _Requirements: 1.3_

- [x] 4. Implement OpenAPI specification parsing
  - Create OpenAPISpecificationParser for OpenAPI 3.0 and Swagger 2.0
  - Convert OpenAPI to internal APISpecification model
  - Extract endpoints, schemas, and response examples
  - Handle parsing errors with detailed messages
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - _Note: Implemented with CompositeSpecificationParserImpl for extensibility_

- [x] 4.1 OpenAPI parser implementation
  - Use swagger-parser library for parsing
  - Convert to APISpecification domain model
  - Extract endpoint definitions with all details
  - Preserve example responses from specifications
  - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - _Note: Fully implemented in OpenAPISpecificationParser_

- [x] 4.2 Parser error handling
  - Validate OpenAPI format before parsing
  - Return detailed validation errors with line numbers
  - Handle missing required fields gracefully
  - _Requirements: 2.5_
  - _Note: Implemented with validation method_

- [ ]* 4.3 Test OpenAPI parser
  - Test with valid OpenAPI 3.0 specifications
  - Test with valid Swagger 2.0 specifications
  - Test with invalid specifications
  - Test example preservation
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 5. Implement mock generation logic
  - Create WireMockMappingGenerator for scenario-based generation
  - Generate mocks for happy path (2xx status codes)
  - Generate mocks for client errors (4xx) and server errors (5xx)
  - Use specification examples when available
  - Generate realistic response data from schemas
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 7.1, 7.2, 7.3, 7.4, 7.5_
  - _Note: Fully implemented with RealisticTestDataGenerator for data generation_

- [x] 5.1 WireMock mapping generator
  - Implement generateFromSpecification method
  - Create WireMock JSON format for each endpoint
  - Generate request matchers (URL patterns, methods)
  - Generate response definitions with headers
  - _Requirements: 3.1, 3.5_
  - _Note: Implemented in WireMockMappingGenerator_

- [x] 5.2 Scenario-based generation
  - Generate happy path mocks (2xx status codes)
  - Generate client error mocks (4xx) when enabled
  - Generate server error mocks (5xx) when enabled
  - Tag mocks with scenario metadata
  - _Requirements: 7.1, 7.2, 7.3, 7.4_
  - _Note: Implemented with generateErrorCases method_

- [x] 5.3 Response data generation
  - Generate realistic JSON from OpenAPI schemas
  - Use specification examples when available
  - Handle primitive types, arrays, objects
  - Handle nested structures
  - _Requirements: 3.2, 3.4_
  - _Note: Implemented in RealisticTestDataGenerator with realistic sample data_

- [ ]* 5.4 Test mock generator
  - Test happy path mock generation
  - Test error case mock generation
  - Test example response usage
  - Test schema-based data generation
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 7.1, 7.2, 7.3_

- [x] 6. Implement Koog Functional Agent
  - Set up Koog 0.6.3 dependencies in build.gradle.kts
  - Implement MockGenerationFunctionalAgent
  - Configure agent domain and capabilities
  - Implement synchronous generation methods
  - _Requirements: 3.1, 3.5, 4.1_
  - _Note: Implemented as MockGenerationFunctionalAgent with multiple generation methods_

- [x] 6.1 Add Koog dependencies
  - Add koog-core, koog-functional-agents to build.gradle.kts
  - Add koog-bedrock for Bedrock integration
  - Configure Koog agent registration
  - _Requirements: Framework integration_
  - _Note: Added ai.koog:koog-agents dependency_

- [x] 6.2 Implement Functional Agent
  - Create MockGenerationFunctionalAgent class
  - Set domain to "mock-generation"
  - Define capabilities: parse-openapi, generate-wiremock, scenario-generation
  - Implement synchronous generation methods
  - _Requirements: 3.1, 3.5, 4.1_
  - _Note: Implemented with generateFromSpec, generateFromDescription, and generateFromSpecWithDescription methods_

- [ ]* 6.3 Test Functional Agent
  - Test agent request handling
  - Test specification generation flow
  - Test error handling in agent
  - _Requirements: 3.1, 3.5_

- [ ] 7. Implement WireMock admin adapter for brave mode
  - Create WireMockAdminAdapter implementing WireMockAdminInterface
  - Implement createMapping method for applying mocks
  - Add error handling for WireMock admin API failures
  - Configure WireMock admin URL from environment
  - _Requirements: 5.1, 5.2, 5.3_

- [ ] 7.1 WireMock admin adapter implementation
  - Implement WireMockAdminInterface with HTTP client
  - Create mapping via POST /__admin/mappings
  - Parse mapping ID from WireMock response
  - Handle namespace prefixes if provided
  - _Requirements: 5.1, 5.2_

- [ ] 7.2 Brave mode error handling
  - Handle WireMock service unavailable errors
  - Handle invalid mapping format errors
  - Implement timeout handling
  - Add comprehensive logging
  - _Requirements: 5.4_

- [ ]* 7.3 Test WireMock admin adapter
  - Test mapping creation with mock WireMock API
  - Test error scenarios and timeouts
  - Test namespace prefix handling
  - _Requirements: 5.1, 5.3_

- [ ] 8. Implement generation use case with brave mode
  - Create GenerateMocksFromSpecUseCase
  - Orchestrate parsing, generation, and optional application
  - Handle brave mode application logic
  - Return synchronous response with all mocks
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 5.1, 5.3, 6.1, 6.2, 6.3_

- [ ] 8.1 Generation use case implementation
  - Implement invoke method with full workflow
  - Parse specification and generate mocks
  - Execute Koog agent for generation
  - Apply mocks if brave mode is enabled
  - Return GenerationResponse with mocks and application status
  - _Requirements: 3.1, 5.1, 5.3, 6.1, 6.2, 6.3_

- [ ]* 8.2 Test generation use case
  - Test complete generation workflow
  - Test brave mode application
  - Test error handling
  - Test synchronous response
  - _Requirements: 3.1, 5.1, 6.1_

- [ ] 9. Implement AI Generation API endpoints
  - Add routing for /ai/generation/from-spec endpoint
  - Integrate with GenerateMocksFromSpecUseCase
  - Add request validation and error handling
  - Return synchronous response with generated mocks
  - _Requirements: API design, 6.1, 6.5_
  - _Note: Using MockNestLambdaHandler entry point, REST controllers not needed for Phase 1_

- [ ] 9.1 Generation endpoint implementation
  - Add route handler for POST /ai/generation/from-spec
  - Parse MockGenerationRequest from HTTP request
  - Invoke GenerateMocksFromSpecUseCase
  - Return GenerationResponse as JSON
  - Handle errors gracefully with appropriate HTTP status codes
  - _Requirements: 3.1, 6.1, 6.5_

- [ ]* 9.2 Test API endpoint
  - Test generation endpoint with valid specs
  - Test brave mode application
  - Test error scenarios
  - Test synchronous response format
  - _Requirements: API endpoints_

- [x] 10. Update SAM template for AI features
  - Add Bedrock IAM permissions when AI is enabled
  - Configure Lambda environment variables
  - Update API Gateway routes for /ai/generation/* endpoints
  - Set appropriate Lambda memory and timeout
  - _Requirements: AWS deployment_

- [x] 10.1 SAM template AI configuration
  - Add conditional Bedrock IAM policies
  - Set Lambda memory to 1024MB minimum
  - Set timeout to 30 seconds for synchronous generation
  - _Requirements: Bedrock integration_

- [x] 10.2 API Gateway AI routes
  - Add /ai/generation/from-spec route
  - Configure CORS and authentication
  - _Requirements: API routing_
  - _Note: Using catch-all proxy routes, specific routes handled by Spring routing_

- [ ] 11. Achieve 90% test coverage with comprehensive unit and integration tests
  - Write unit tests for all use cases, domain logic, and components
  - Write integration tests with TestContainers (mock Bedrock, mock WireMock admin)
  - Use Kover to measure and verify 90% aggregated code coverage
  - Focus on integration tests over artificial per-module coverage
  - _Requirements: Testing strategy, 90% coverage target_

- [ ] 11.1 Unit tests for core components
  - Test TestAgentRequestUseCase (hello world functionality)
  - Test OpenAPISpecificationParser with valid/invalid specs
  - Test WireMockMappingGenerator for all scenarios
  - Test RealisticTestDataGenerator for schema-based data generation
  - Test MockGenerationFunctionalAgent request handling
  - Test WireMockAdminAdapter methods
  - Test GenerateMocksFromSpecUseCase workflow orchestration
  - _Requirements: Unit testing, code coverage_

- [ ] 11.2 Integration tests with TestContainers
  - Set up mock Bedrock service for testing with proper lifecycle management
  - Test complete generation workflow: spec → parse → generate → return
  - Test brave mode: spec → parse → generate → apply → return
  - Test WireMock admin integration with mock admin API
  - Test error scenarios: invalid specs, Bedrock failures, WireMock failures
  - Use @BeforeAll/@AfterAll for container setup, @BeforeEach/@AfterEach for data cleanup
  - _Requirements: Integration testing, TestContainers_

- [ ] 11.3 Verify 90% coverage target
  - Run `./gradlew koverHtmlReport` to generate coverage report
  - Run `./gradlew koverVerify` to enforce 90% threshold
  - Review coverage report and identify gaps
  - Add targeted tests for uncovered code paths
  - Ensure aggregated project-level coverage meets 90% target
  - _Requirements: Code coverage verification_

- [ ]* 11.4 Property-based tests (optional but recommended)
  - **Property 2**: OpenAPI parsing completeness
  - **Property 3**: Generated mock validity
  - **Property 4**: Schema compliance
  - **Property 7**: Synchronous response completeness
  - **Property 8**: Brave mode application
  - _Requirements: Property-based testing_

- [ ] 12. Documentation and examples
  - Update README with Phase 1 AI generation examples
  - Create Postman collection for AI generation endpoint
  - Document brave mode usage and limitations
  - Document Phase 1 limitations and future phases
  - _Requirements: Documentation_

- [ ] 13. Final integration and testing
  - Test hello world endpoint validates Bedrock integration
  - Test complete workflow: generate → review → create → use
  - Test brave mode workflow: generate → apply → use
  - Validate SAM deployment with AI features enabled
  - Verify synchronous response times meet requirements
  - _Requirements: End-to-end validation_

## Notes

- **Phase 1 Focus**: Hello world + OpenAPI synchronous generation + brave mode
- **Deferred to Future Spec (Mock Evolution)**: Specification storage, versioning, change detection, namespace organization
- **Deferred to Future Phases**: Enhancement, refinement, GraphQL/WSDL, batch, natural language generation
- **Clean Architecture**: Maintain strict layer boundaries throughout
- **Bedrock Usage**: Hello world validation only in Phase 1
- **Stateless Design**: No storage layer for specifications or generated mocks
- **Brave Mode**: Optional direct application to MockNest via WireMock admin API
- Tasks marked with `*` are optional but recommended for quality
- Each task should be completed and tested before moving to the next