# Implementation Plan: AI Mock Generation - Phase 1

## Overview
Implement Phase 1 of AI-powered mock generation focusing on:
1. **Hello World Endpoint**: Validate Bedrock + Koog integration
2. **OpenAPI Mock Generation**: Generate WireMock-ready mocks from OpenAPI 3.0 and Swagger 2.0
3. **Namespace Support**: Organize mocks by API and optional client
4. **Specification Storage**: Store API specs for future evolution

**Phase 1 Scope:**
- Hello world endpoint for Bedrock validation
- OpenAPI specification parsing only (no GraphQL/WSDL)
- Scenario-based mock generation (happy path, errors, edge cases)
- Single specification processing (no batch)
- Foundation for future evolution features

**Deferred to Future Phases:**
- Mock evolution and change detection
- Natural language mock generation (beyond hello world)
- Mock enhancement and refinement
- GraphQL and WSDL support
- Batch generation
- Traffic analysis integration

## Tasks

- [ ] 1. Set up domain models and interfaces for Phase 1
  - Create domain models for mock generation requests and responses
  - Define clean architecture interfaces for AI services and storage
  - Set up namespace and generation job models
  - _Requirements: 1, 2, 3, 4, 5, 6, 7_

- [ ] 1.1 Create core domain models
  - Implement MockGenerationRequest with namespace support
  - Implement GeneratedMock with WireMock JSON format
  - Implement MockNamespace with prefix generation logic
  - Implement GenerationJob and JobStatus models
  - _Requirements: 3.1, 4.1, 4.2, 6.1_

- [ ] 1.2 Define application layer interfaces
  - Create AIModelServiceInterface abstraction (hides Bedrock)
  - Create SpecificationParserInterface for OpenAPI formats
  - Create MockGeneratorInterface for generation logic
  - Create GenerationStorageInterface for persistence
  - _Requirements: Clean architecture separation_

- [ ] 2. Implement hello world endpoint (Bedrock validation)
  - Create HelloWorldUseCase for text processing
  - Implement hello world REST controller
  - Add error handling for Bedrock unavailability
  - Add logging for debugging Bedrock integration
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 2.1 Hello world use case
  - Implement HelloWorldUseCase with AIModelServiceInterface
  - Handle Bedrock responses and errors
  - Return HelloWorldResponse with success/error status
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2.2 Hello world REST endpoint
  - Create /ai/hello POST endpoint
  - Accept text input in request body
  - Return AI-generated response or error
  - _Requirements: 1.1, 1.2, 1.3_

- [ ]* 2.3 Test hello world integration
  - Write unit tests for HelloWorldUseCase
  - Write integration tests with mock Bedrock responses
  - Test error handling when Bedrock is unavailable
  - _Requirements: 1.3, 1.4_

- [ ] 3. Implement Bedrock service adapter
  - Create BedrockServiceAdapter implementing AIModelServiceInterface
  - Set up Claude 3 Sonnet integration for hello world
  - Implement prompt building and response parsing
  - Add error handling and retry logic
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 3.1 Bedrock adapter implementation
  - Implement processHelloWorld method with Bedrock SDK
  - Configure Claude 3 Sonnet model
  - Build Claude-compatible prompts
  - Parse Claude responses correctly
  - _Requirements: 1.1, 1.2_

- [ ] 3.2 Bedrock error handling
  - Handle service unavailable errors
  - Handle invalid model responses
  - Implement timeout handling
  - Add comprehensive logging
  - _Requirements: 1.3, 1.4_

- [ ]* 3.3 Test Bedrock adapter
  - Write unit tests with mock Bedrock client
  - Test prompt building and response parsing
  - Test error scenarios and timeouts
  - _Requirements: 1.3_

- [ ] 4. Implement OpenAPI specification parsing
  - Create OpenAPISpecificationParser for OpenAPI 3.0 and Swagger 2.0
  - Convert OpenAPI to internal APISpecification model
  - Extract endpoints, schemas, and response examples
  - Handle parsing errors with detailed messages
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 4.1 OpenAPI parser implementation
  - Use swagger-parser library for parsing
  - Convert to APISpecification domain model
  - Extract endpoint definitions with all details
  - Preserve example responses from specifications
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 4.2 Parser error handling
  - Validate OpenAPI format before parsing
  - Return detailed validation errors with line numbers
  - Handle missing required fields gracefully
  - _Requirements: 2.5_

- [ ]* 4.3 Test OpenAPI parser
  - Test with valid OpenAPI 3.0 specifications
  - Test with valid Swagger 2.0 specifications
  - Test with invalid specifications
  - Test example preservation
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 5. Implement mock generation logic
  - Create WireMockMappingGenerator for scenario-based generation
  - Generate mocks for happy path (2xx status codes)
  - Generate mocks for client errors (4xx) and server errors (5xx)
  - Use specification examples when available
  - Generate realistic response data from schemas
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 5.1 WireMock mapping generator
  - Implement generateFromSpecification method
  - Create WireMock JSON format for each endpoint
  - Generate request matchers (URL patterns, methods)
  - Generate response definitions with headers
  - _Requirements: 3.1, 3.5_

- [ ] 5.2 Scenario-based generation
  - Generate happy path mocks (2xx status codes)
  - Generate client error mocks (4xx) when enabled
  - Generate server error mocks (5xx) when enabled
  - Tag mocks with scenario metadata
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ] 5.3 Response data generation
  - Generate realistic JSON from OpenAPI schemas
  - Use specification examples when available
  - Handle primitive types, arrays, objects
  - Handle nested structures
  - _Requirements: 3.2, 3.4_

- [ ]* 5.4 Test mock generator
  - Test happy path mock generation
  - Test error case mock generation
  - Test example response usage
  - Test schema-based data generation
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 7.1, 7.2, 7.3_

- [ ] 6. Implement Koog Functional Agent
  - Set up Koog 0.6.0 dependencies in build.gradle.kts
  - Implement MockGenerationFunctionalAgent
  - Configure agent domain and capabilities
  - Implement execute method for specification generation
  - _Requirements: 3.1, 3.5_

- [ ] 6.1 Add Koog dependencies
  - Add koog-core, koog-functional-agents to build.gradle.kts
  - Add koog-bedrock for Bedrock integration
  - Configure Koog agent registration
  - _Requirements: Framework integration_

- [ ] 6.2 Implement Functional Agent
  - Create MockGenerationFunctionalAgent class
  - Set domain to "mock-generation"
  - Define capabilities: parse-openapi, generate-wiremock, scenario-generation
  - Implement execute method for SPECIFICATION_GENERATION
  - _Requirements: 3.1, 3.5_

- [ ]* 6.3 Test Functional Agent
  - Test agent request handling
  - Test specification generation flow
  - Test error handling in agent
  - _Requirements: 3.1, 3.5_

- [ ] 7. Implement storage layer
  - Create S3GenerationStorageAdapter
  - Implement namespace-based storage organization
  - Store generated mocks, specifications, and job metadata
  - Configure Koog S3 persistence for agent state
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 7.1 S3 storage adapter
  - Implement GenerationStorageInterface with S3 backend
  - Use namespace prefixes for storage paths
  - Store API specifications with versions
  - Store user instructions alongside specifications
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 7.2 Generated mocks storage
  - Store generated mocks by job ID and namespace
  - Store job metadata and results
  - Implement retrieval by job ID
  - _Requirements: 6.3, 6.4_

- [ ] 7.3 Koog persistence configuration
  - Configure Koog S3 persistence backend
  - Set up agent state checkpointing
  - Configure retention policies
  - _Requirements: Agent state management_

- [ ]* 7.4 Test storage layer
  - Test specification storage and retrieval
  - Test generated mocks storage
  - Test namespace isolation
  - Use LocalStack for S3 testing
  - _Requirements: 4.1, 4.2, 4.3, 5.1, 6.3_

- [ ] 8. Implement generation use case
  - Create GenerateMocksFromSpecUseCase
  - Orchestrate parsing, generation, and storage
  - Store API specification for future evolution
  - Handle generation job lifecycle
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3_

- [ ] 8.1 Generation use case implementation
  - Implement invoke method with full workflow
  - Store generation request for audit
  - Parse specification and generate mocks
  - Store specification when requested
  - Execute Koog agent for generation
  - Store generated mocks in S3
  - _Requirements: 3.1, 5.1, 5.3, 6.1, 6.2, 6.3_

- [ ]* 8.2 Test generation use case
  - Test complete generation workflow
  - Test specification storage
  - Test job tracking
  - Test error handling
  - _Requirements: 3.1, 5.1, 6.1_

- [ ] 9. Implement AI Generation API endpoints
  - Create REST controllers for /ai/* endpoints
  - Implement POST /ai/hello endpoint
  - Implement POST /ai/generation/from-spec endpoint
  - Implement GET /ai/generation/jobs/{jobId}/mocks endpoint
  - _Requirements: API design_

- [ ] 9.1 Hello world endpoint
  - Create AIHelloController
  - Implement POST /ai/hello
  - Accept text input, return AI response
  - Handle errors gracefully
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 9.2 Generation endpoints
  - Create AIGenerationController
  - Implement POST /ai/generation/from-spec
  - Implement GET /ai/generation/jobs/{jobId}/mocks
  - Add request validation and error handling
  - _Requirements: 3.1, 6.3, 6.4_

- [ ]* 9.3 Test API endpoints
  - Test hello world endpoint
  - Test generation endpoint with valid specs
  - Test job retrieval endpoint
  - Test error scenarios
  - _Requirements: API endpoints_

- [ ] 10. Update SAM template for AI features
  - Add Bedrock IAM permissions when AI is enabled
  - Configure Lambda environment variables
  - Update API Gateway routes for /ai/* endpoints
  - Set appropriate Lambda memory and timeout
  - _Requirements: AWS deployment_

- [ ] 10.1 SAM template AI configuration
  - Add conditional Bedrock IAM policies
  - Configure AI_ENABLED environment variable
  - Set Lambda memory to 1024MB minimum
  - Set timeout to 5 minutes
  - _Requirements: Bedrock integration_

- [ ] 10.2 API Gateway AI routes
  - Add /ai/hello route
  - Add /ai/generation/from-spec route
  - Add /ai/generation/jobs/{jobId}/mocks route
  - Configure CORS and authentication
  - _Requirements: API routing_

- [ ] 11. Add comprehensive testing
  - Write unit tests for all use cases and domain logic
  - Write integration tests with LocalStack and mock Bedrock
  - Write property-based tests for generation consistency
  - _Requirements: Testing strategy_

- [ ]* 11.1 Unit tests
  - Test hello world use case
  - Test OpenAPI parser
  - Test mock generator
  - Test Functional Agent
  - Test storage adapter
  - _Requirements: Unit testing_

- [ ]* 11.2 Integration tests
  - Test complete generation workflows with TestContainers
  - Test S3 storage with LocalStack
  - Test Bedrock integration with mock responses
  - Test generated mocks with WireMock runtime
  - _Requirements: Integration testing_

- [ ]* 11.3 Property-based tests
  - **Property 2**: OpenAPI parsing completeness
  - **Property 3**: Generated mock validity
  - **Property 4**: Schema compliance
  - **Property 6**: Namespace isolation
  - _Requirements: Property-based testing_

- [ ] 12. Documentation and examples
  - Update README with Phase 1 AI generation examples
  - Create Postman collection for AI endpoints
  - Document namespace organization
  - Document Phase 1 limitations and future phases
  - _Requirements: Documentation_

- [ ] 13. Final integration and testing
  - Test hello world endpoint validates Bedrock integration
  - Test complete workflow: generate → retrieve → create → use
  - Validate SAM deployment with AI features enabled
  - Verify namespace isolation works correctly
  - _Requirements: End-to-end validation_

## Notes

- **Phase 1 Focus**: Hello world + OpenAPI generation only
- **Deferred Features**: Evolution, enhancement, GraphQL/WSDL, batch, natural language generation
- **Clean Architecture**: Maintain strict layer boundaries throughout
- **Bedrock Usage**: Hello world validation only in Phase 1
- **Future Foundation**: Store specifications for evolution in future phases
- Tasks marked with `*` are optional but recommended for quality
- Each task should be completed and tested before moving to the next