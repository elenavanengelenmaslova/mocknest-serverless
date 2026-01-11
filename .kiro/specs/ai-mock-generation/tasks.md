# Implementation Plan: AI Mock Generation

## Overview
Implement AI-powered mock generation using Koog 0.6.0 Functional Agent, Kotlin, and Amazon Bedrock integration. Focus on the core generation workflow: API spec → AI generation → WireMock-ready JSON → User creates via admin API.

## Tasks

- [-] 1. Set up domain models and interfaces
  - Create domain models for mock generation requests and responses
  - Define clean architecture interfaces for AI services and storage
  - Set up namespace and generation job models
  - _Requirements: All domain modeling requirements_

- [x] 1.1 Create core domain models
  - Implement MockGenerationRequest, NaturalLanguageRequest, GeneratedMock
  - Implement MockNamespace with prefix generation logic
  - Implement GenerationJob and related status models
  - _Requirements: 1.1, 2.1, 4.1_

- [ ] 1.2 Define application layer interfaces
  - Create AIModelServiceInterface abstraction (hides Bedrock)
  - Create SpecificationParserInterface for different formats
  - Create MockGeneratorInterface for generation logic
  - Create GenerationStorageInterface for persistence
  - _Requirements: Clean architecture separation_

- [ ] 2. Implement Koog Functional Agent
  - Set up Koog 0.6.0 dependencies in build.gradle.kts
  - Implement MockGenerationFunctionalAgent with domain capabilities
  - Create agent request/response handling for different generation types
  - _Requirements: 1.1, 2.1, 2.2, 2.3_

- [ ] 2.1 Add Koog dependencies
  - Add koog-core, koog-functional-agents, koog-bedrock to build.gradle.kts
  - Configure Koog agent registration and domain setup
  - _Requirements: Framework integration_

- [ ] 2.2 Implement Functional Agent
  - Create MockGenerationFunctionalAgent with domain "mock-generation"
  - Implement capabilities: parse-api-specifications, generate-wiremock-mappings, etc.
  - Implement execute method with different request type handling
  - _Requirements: 1.1, 2.1, 2.2_

- [ ] 3. Implement specification parsing
  - Create OpenAPISpecificationParser for OpenAPI 3.0 and Swagger 2.0
  - Create basic GraphQL and WSDL parsers (minimal implementation)
  - Implement composite parser that delegates to appropriate format parser
  - _Requirements: 1.1, 1.2, 7.1, 8.1_

- [ ] 3.1 OpenAPI specification parser
  - Use swagger-parser library to parse OpenAPI/Swagger specifications
  - Convert to internal APISpecification domain model
  - Handle endpoint definitions, schemas, and response examples
  - _Requirements: 1.1, 1.2, 1.4_

- [ ] 3.2 Basic GraphQL and WSDL parsers
  - Implement minimal GraphQL schema parsing
  - Implement minimal WSDL parsing for SOAP services
  - Focus on extracting basic endpoint and schema information
  - _Requirements: 7.1, 8.1_

- [ ] 4. Implement mock generation logic
  - Create WireMock mapping generator from parsed specifications
  - Generate realistic response data based on schemas
  - Handle different HTTP methods, status codes, and content types
  - _Requirements: 1.2, 1.3, 1.4_

- [ ] 4.1 WireMock mapping generator
  - Convert APISpecification endpoints to WireMock JSON format
  - Generate request matchers (URL patterns, methods, headers)
  - Generate response definitions with realistic data
  - _Requirements: 1.2, 1.3_

- [ ] 4.2 Response data generation
  - Generate realistic JSON responses based on OpenAPI schemas
  - Handle primitive types, arrays, objects, and nested structures
  - Use specification examples when available
  - _Requirements: 1.2, 1.4_

- [ ] 5. Implement Bedrock integration
  - Create BedrockServiceAdapter implementing AIModelServiceInterface
  - Set up Claude 3 Sonnet integration for natural language generation
  - Implement prompt engineering for WireMock mapping generation
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 5.1 Bedrock service adapter
  - Implement BedrockServiceAdapter with AWS Bedrock SDK
  - Configure Claude 3 Sonnet model for natural language processing
  - Handle Bedrock API calls with proper error handling and retries
  - _Requirements: 2.1, 2.2_

- [ ] 5.2 Prompt engineering for mock generation
  - Create structured prompts for generating WireMock JSON from descriptions
  - Implement response parsing and validation
  - Handle AI model errors and fallbacks
  - _Requirements: 2.2, 2.3, 2.4_

- [ ] 6. Implement storage layer
  - Create S3GenerationStorageAdapter for generated mocks and specifications
  - Implement namespace-based storage organization
  - Handle job persistence and retrieval
  - _Requirements: 6.1, 6.2, 6.3_

- [ ] 6.1 S3 storage adapter
  - Implement GenerationStorageInterface with S3 backend
  - Use namespace prefixes for storage organization
  - Store generated mocks, specifications, and job metadata
  - _Requirements: 6.1, 6.2_

- [ ] 6.2 Namespace storage organization
  - Implement storage paths: mocknest/{client}/{apiName}/
  - Store API specifications for future evolution
  - Organize generated mocks by job and namespace
  - _Requirements: Namespace organization_

- [ ] 7. Implement use cases
  - Create GenerateMocksFromSpecUseCase for specification-only generation
  - Create GenerateMocksFromSpecWithDescriptionUseCase for enhanced generation
  - Create GenerateMocksFromDescriptionUseCase for natural language generation
  - _Requirements: 1.1, 2.1, Clean architecture_

- [ ] 7.1 Specification generation use case
  - Implement spec parsing and mock generation workflow
  - Store API specification for future evolution
  - Handle generation job creation and storage
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 7.2 Enhanced generation use cases
  - Implement spec + description combination generation
  - Implement natural language only generation
  - Handle existing specification context for natural language
  - _Requirements: 2.1, 2.2, Enhanced generation_

- [ ] 8. Implement AI Generation API endpoints
  - Create REST controllers for /ai/generation/* endpoints
  - Implement POST /ai/generation/from-spec endpoint
  - Implement POST /ai/generation/from-description endpoint
  - Implement GET /ai/generation/jobs/{jobId}/mocks endpoint
  - _Requirements: API design_

- [ ] 8.1 Generation API controllers
  - Create AIGenerationController with namespace support
  - Implement request validation and error handling
  - Handle asynchronous job processing and status reporting
  - _Requirements: API endpoints_

- [ ] 8.2 Job management endpoints
  - Implement job status tracking and retrieval
  - Handle job results with generated mock listings
  - Provide WireMock-ready JSON in responses
  - _Requirements: Job management_

- [ ] 9. Update SAM template for AI features
  - Add Bedrock IAM permissions when AI is enabled
  - Configure Lambda environment variables for AI generation
  - Update API Gateway routes for /ai/generation/* endpoints
  - _Requirements: AWS deployment_

- [ ] 9.1 SAM template AI configuration
  - Add conditional Bedrock IAM policies
  - Configure AI_ENABLED environment variable
  - Set up proper Lambda memory and timeout for AI processing
  - _Requirements: Bedrock integration_

- [ ] 9.2 API Gateway AI routes
  - Add /ai/generation/* routes to API Gateway
  - Configure proper method mappings and CORS
  - Ensure API key authentication for AI endpoints
  - _Requirements: API routing_

- [ ] 10. Add comprehensive testing
  - Write unit tests for all use cases and domain logic
  - Write integration tests with mock Bedrock responses
  - Write property-based tests for generation consistency
  - _Requirements: Testing strategy_

- [ ]* 10.1 Unit tests for core logic
  - Test specification parsing with various OpenAPI formats
  - Test mock generation logic with different schemas
  - Test Functional Agent request handling
  - _Requirements: Unit testing_

- [ ]* 10.2 Integration tests
  - Test complete generation workflows with TestContainers
  - Test S3 storage operations with LocalStack
  - Test Bedrock integration with mock responses
  - _Requirements: Integration testing_

- [ ]* 10.3 Property-based tests
  - Test specification parsing completeness and consistency
  - Test generated mock validity and WireMock compatibility
  - Test namespace isolation and storage organization
  - _Requirements: Property-based testing_

- [ ] 11. Documentation and examples
  - Update README with AI generation examples
  - Create Postman collection for AI generation endpoints
  - Document namespace organization and best practices
  - _Requirements: Documentation_

- [ ] 12. Final integration and testing
  - Ensure AI generation works with existing WireMock runtime
  - Test complete workflow: generate → retrieve → create → use
  - Validate SAM deployment with AI features enabled
  - _Requirements: End-to-end validation_

## Notes

- Focus on minimal viable implementation for current use case (new mock creation)
- Mark advanced features (mock evolution, enhancement) as future work
- Ensure clean architecture boundaries are maintained
- All AI features should be optional and gracefully degrade when disabled
- Generated mocks must be valid WireMock JSON format
- Tasks marked with `*` are optional for MVP but recommended for quality