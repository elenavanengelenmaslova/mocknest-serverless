# Implementation Plan: Generation Coverage and Eval

## Overview

This plan implements two complementary quality improvements: (1) unit test coverage expansion across all generation module subpackages to reach 90%+ aggregated coverage, and (2) addition of 6 realistic REST API eval scenarios based on Stripe Payment Intents and Twilio Messaging public API specifications. All code is Kotlin using JUnit 6, MockK, and the existing project testing patterns.

## Tasks

- [x] 1. Implement SafeUrlResolver unit tests (util subpackage)
  - [x] 1.1 Create SafeUrlResolver unit test class with WireMock/MockWebServer for HTTP tests
    - Create `SafeUrlResolverTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/util/`
    - Implement tests for successful fetch (200 response returns body content)
    - Implement tests for non-2xx error code propagation (4xx, 5xx throw UrlResolutionException with status code)
    - Implement tests for network error wrapping (SocketTimeoutException, UnknownHostException, ConnectException, SSLException, IOException)
    - Implement tests for response size limit (>10MB throws UrlResolutionException)
    - Implement tests for redirect rejection (3xx throws UrlResolutionException without following)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.10, 1.11_

  - [x] 1.2 Create SafeUrlResolver URL validation and safety tests
    - Implement tests for invalid URL rejection (unsupported scheme, missing host, malformed URI)
    - Implement tests for unsafe address detection (private, loopback, link-local, multicast, CGNAT, IPv6 ULA)
    - Implement tests for `isHttpUrl`, `validateAndResolve`, and `validateUrlSafety` companion functions
    - Cover at least 2 valid-input and 2 invalid-input scenarios per public function
    - _Requirements: 1.9, 1.12, 6.1, 6.2, 6.4_

  - [x]* 1.3 Write property tests for sensitive parameter redaction
    - **Property 3: Sensitive parameter redaction**
    - Use `@ParameterizedTest` with 10+ diverse URLs containing sensitive/non-sensitive query parameters
    - Create test data covering: token, key, secret, auth, sig, password, credential, x-amz- prefix patterns
    - Verify redacted values replaced with `<redacted>` and non-sensitive parameters preserved
    - **Validates: Requirements 1.8, 6.3**

  - [x]* 1.4 Write property tests for invalid URL rejection and unsafe address detection
    - **Property 4: Invalid URL rejection without network access**
    - **Property 5: Unsafe address detection**
    - Use `@ParameterizedTest` with 10+ diverse invalid URLs and unsafe addresses
    - Verify no network connection is made for invalid/unsafe inputs
    - **Validates: Requirements 1.9, 1.12, 6.4**

  - [x] 1.5 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Implement agent subpackage unit tests
  - [x] 2.1 Create MockGenerationFunctionalAgent unit test class
    - Create `MockGenerationFunctionalAgentTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/agent/`
    - Mock all external dependencies (AIModelServiceInterface, SpecificationParserInterface, MockValidatorInterface, PromptBuilderService, UrlFetcher) using MockK with `relaxed = true`
    - Implement tests verifying agent initialization with maxRetries, injected PromptBuilderService, and AIModelServiceInterface
    - Implement tests verifying strategy graph transitions (setup → generate → validate → correct) for success and validation-failure scenarios
    - _Requirements: 2.1, 2.2, 2.3, 2.5_

  - [x]* 2.2 Write property test for agent failure propagation
    - **Property 6: Agent failure propagation within retry bound**
    - Use `@ParameterizedTest` with diverse unparseable AI responses and parser exceptions
    - Verify failure propagated as GenerationResult.failure without exceeding maxRetries
    - **Validates: Requirements 2.4**

  - [x] 2.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Implement GraphQL subpackage unit tests
  - [x] 3.1 Create GraphQLSchemaReducer unit test class with parameterized test data
    - Create `GraphQLSchemaReducerTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/graphql/`
    - Create 10+ diverse introspection JSON test data files in `software/application/src/test/resources/test-data/graphql/`
    - Include: simple schema, complex nested types, list types, non-null modifiers, input objects, multiple enums, deep nesting, union types, interface types, large schema
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 3.2 Implement GraphQLSchemaReducer extraction and error tests
    - Implement tests verifying query/mutation extraction from queryType/mutationType
    - Implement tests verifying OBJECT and INPUT_OBJECT types extracted into types map
    - Implement tests verifying ENUM types extracted into enums map
    - Implement error tests: invalid JSON, missing `data.__schema`, missing `types` array, empty types with no query/mutation type
    - _Requirements: 3.1, 3.2, 3.3_

  - [x]* 3.3 Write property tests for GraphQL schema extraction completeness
    - **Property 7: GraphQL schema extraction completeness**
    - **Property 8: GraphQL type reference resolution**
    - Use `@ParameterizedTest` with the 10+ diverse introspection JSON files
    - Verify all non-built-in types, queries, mutations, and enums are extracted
    - Verify type references (`[Type]`, `Type!`, `[Type!]!`) are correctly resolved
    - **Validates: Requirements 3.2, 3.4**

  - [x] 3.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Implement parsers subpackage unit tests
  - [x] 4.1 Create specification parser test classes with diverse test data
    - Create `OpenAPISpecificationParserTest.kt`, `WsdlSpecificationParserTest.kt`, `GraphQLSpecificationParserTest.kt`, `CompositeSpecificationParserImplTest.kt`
    - Create diverse test data files in `software/application/src/test/resources/test-data/openapi/` (OpenAPI 3.0 and Swagger 2.0 specs)
    - Implement tests verifying endpoint extraction per path-method combination for REST
    - Implement tests verifying WSDL operation extraction with soapAction metadata
    - Implement tests verifying GraphQL query/mutation endpoint extraction
    - Implement tests for CompositeSpecificationParser delegation and unsupported format error
    - Implement tests for URL vs inline content parsing paths
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x]* 4.2 Write property tests for parser extraction completeness
    - **Property 9: Parser extraction completeness**
    - Use `@ParameterizedTest` with diverse specs per format (OpenAPI, Swagger, WSDL, GraphQL)
    - Verify one endpoint per path-method (REST), one per operation (WSDL/GraphQL), schemas matching declared types
    - **Validates: Requirements 4.2, 4.3, 4.4**

  - [x] 4.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 5. Implement services subpackage unit tests
  - [x] 5.1 Create PromptBuilderService unit test class
    - Create `PromptBuilderServiceTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/services/`
    - Implement tests verifying template loading and placeholder substitution completeness
    - Implement tests verifying missing template resource throws IllegalStateException
    - Implement tests verifying format-specific prompt content for each SpecificationFormat (OPENAPI_3, SWAGGER_2, GRAPHQL, WSDL)
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x]* 5.2 Write property tests for prompt template substitution
    - **Property 10: Prompt template placeholder substitution completeness**
    - **Property 11: Format-specific prompt content selection**
    - Use `@ParameterizedTest` with diverse APISpecification inputs per format
    - Verify no `{{` or `}}` sequences remain in output
    - Verify format-distinguishing content present (e.g., "SOAPAction" for WSDL)
    - **Validates: Requirements 5.2, 5.4**

  - [x] 5.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement validators subpackage unit tests
  - [x] 7.1 Create validator test classes with valid/invalid mapping test data
    - Create `OpenAPIMockValidatorTest.kt`, `GraphQLMockValidatorTest.kt`, `SoapMockValidatorTest.kt`, `CompositeMockValidatorTest.kt`
    - Create test data files in `software/application/src/test/resources/test-data/validators/` (valid and invalid mappings per protocol)
    - Implement tests with at least 2 valid mapping scenarios per protocol (REST, GraphQL, SOAP)
    - Implement tests with at least 2 invalid mapping scenarios per protocol with specific error identification
    - Implement tests for CompositeMockValidator error aggregation and exception isolation
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x]* 7.2 Write property tests for validator correctness
    - **Property 12: Valid mock mappings pass validation**
    - **Property 13: Invalid mock mappings produce specific errors**
    - **Property 14: Composite validator error aggregation**
    - Use `@ParameterizedTest` with diverse valid/invalid mappings per protocol
    - Verify valid mappings produce zero errors, invalid mappings produce specific errors
    - Verify exception in one validator does not prevent others from executing
    - **Validates: Requirements 7.2, 7.3, 7.5**

  - [x] 7.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 8. Implement WSDL subpackage unit tests
  - [x] 8.1 Create WsdlParser and WsdlSchemaReducer test classes with diverse WSDL test data
    - Create `WsdlParserTest.kt` and `WsdlSchemaReducerTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/wsdl/`
    - Create 3+ structurally diverse WSDL 1.1 test files in `software/application/src/test/resources/test-data/wsdl/`
    - Implement tests verifying ParsedWsdl contains serviceName, targetNamespace, soapVersion, operations, messages, xsdTypes
    - Implement tests for malformed XML and missing required elements (WsdlParsingException within 2 seconds)
    - _Requirements: 8.1, 8.2, 8.4_

  - [x]* 8.2 Write property tests for WSDL extraction and type resolution
    - **Property 15: WSDL field extraction completeness**
    - **Property 16: WSDL transitive type inclusion and exclusion**
    - Use `@ParameterizedTest` with 3+ diverse WSDL files
    - Verify all expected fields extracted from ParsedWsdl
    - Verify transitive type inclusion and unreachable type exclusion in WsdlSchemaReducer
    - **Validates: Requirements 8.2, 8.3**

  - [x] 8.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 9. Implement usecases subpackage unit tests
  - [x] 9.1 Create use case test classes
    - Create `AIGenerationRequestUseCaseTest.kt` and `GenerateMocksFromSpecWithDescriptionUseCaseTest.kt`
    - Mock all dependencies using MockK with `relaxed = true`
    - Implement tests verifying use case orchestration for success and failure paths
    - Implement tests verifying correct delegation to agent and parser components
    - _Requirements: 2.1 (agent orchestration via use cases)_

  - [x] 9.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 10. Checkpoint - Ensure all unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Create Stripe Payment Intents eval spec subset
  - [x] 11.1 Create minimal Stripe Payment Intents OpenAPI 3.0 spec
    - Create `stripe-payment-openapi-3.0.yaml` in `software/infra/aws/generation/src/test/resources/eval/`
    - Define endpoints: POST /v1/payment_intents, GET /v1/payment_intents/{intent}, POST /v1/payment_intents/{intent}/confirm, GET /v1/charges
    - Define schemas: PaymentIntent (with `pi_` prefix IDs, status enum, nested objects, nullable fields), Charge (with `ch_` prefix IDs), Error (type, code, message)
    - Ensure valid OpenAPI 3.0 document parseable by standard parsers
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 12. Create Twilio Messaging eval spec subset
  - [x] 12.1 Create minimal Twilio Messaging OpenAPI 3.0 spec
    - Create `twilio-messaging-openapi-3.0.yaml` in `software/infra/aws/generation/src/test/resources/eval/`
    - Define endpoints: POST /2010-04-01/Accounts/{AccountSid}/Messages.json, GET /2010-04-01/Accounts/{AccountSid}/Messages/{MessageSid}.json, GET /2010-04-01/Accounts/{AccountSid}/Messages.json
    - Define schemas: Message (with `SM` prefix SID, status enum, direction enum, date fields, subresource_uris), Error (code, message, more_info, status)
    - Ensure valid OpenAPI 3.0 document parseable by existing specification parser
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [x] 13. Add eval scenarios to dataset
  - [x] 13.1 Add 3 Stripe eval scenarios to multi-protocol-eval-dataset.json
    - Append `rest-stripe-payment-intent-basic` scenario (verify Stripe-style IDs with correct prefixes)
    - Append `rest-stripe-payment-intent-consistency` scenario (verify cross-reference consistency between payment intent, customer, and charge)
    - Append `rest-stripe-payment-card-declined` scenario (verify HTTP 402 error response with error object)
    - Each scenario references `eval/stripe-payment-openapi-3.0.yaml`, format `OPENAPI_3`, namespace `stripe-eval`
    - Include semanticCheck fields referencing ID prefix patterns and required field validation
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 13.2 Add 3 Twilio eval scenarios to multi-protocol-eval-dataset.json
    - Append `rest-twilio-message-basic` scenario (verify Twilio SID format patterns AC/SM + 32 hex chars)
    - Append `rest-twilio-message-status-consistency` scenario (verify valid status enum values and E.164 phone numbers)
    - Append `rest-twilio-message-invalid-number` scenario (verify HTTP 4xx error with status, message, code, more_info)
    - Each scenario references `eval/twilio-messaging-openapi-3.0.yaml`, format `OPENAPI_3`, protocol `REST`
    - Include semanticCheck fields referencing SID regex patterns and E.164 format validation
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 13.3 Verify eval dataset integrity
    - Confirm dataset contains exactly 52 total scenarios
    - Confirm all 46 existing scenarios preserved with identical field values and original order
    - Confirm dataset remains valid JSON parseable by existing eval test infrastructure
    - Confirm all new scenario specFile paths resolve to existing classpath resources
    - _Requirements: 13.1, 13.2, 13.3, 13.5, 13.6_

- [x] 14. Update documentation
  - [x] 14.1 Update PROMPT_EVAL.md documentation
    - Update summary paragraph to state "52 scenarios across 3 protocols and 14 API specifications"
    - Add Stripe and Twilio entries to API Specification Files table (File, Protocol, Domain, Endpoints)
    - Update Protocol Breakdown table with new REST scenario count totaling 52
    - Update Cost Considerations table to reflect 52 scenarios and updated REST count
    - _Requirements: 14.1, 14.2, 14.3, 14.5_

  - [x] 14.2 Update README Generation Quality section
    - Update prose and table to reference 52 scenarios across 14 API specifications
    - _Requirements: 14.4_

- [x] 15. Final verification - Coverage and build
  - [x] 15.1 Run `./gradlew clean test` and confirm all tests compile and pass
    - _Requirements: 15.1_

  - [x] 15.2 Run `./gradlew koverVerify` and confirm exit code 0 (90%+ threshold met)
    - If threshold not met, identify gaps and add targeted tests
    - If classes require exclusion (Lambda entry points, SnapStart hooks), add to `kover.reports.total.filters.excludes.classes` with inline comments
    - _Requirements: 15.1, 15.3_

  - [x] 15.3 Run `./gradlew koverHtmlReport` and verify per-subpackage coverage
    - Confirm each generation-related subpackage shows 90%+ line coverage
    - _Requirements: 15.2_

- [x] 16. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All unit tests use MockK with `relaxed = true` and Given-When-Then naming convention
- Test data files stored in `src/test/resources/test-data/` organized by subpackage
- Eval scenarios are appended to existing dataset without modifying existing entries
- Eval spec subsets are minimal faithful representations (3-4 endpoints each)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "3.1"] },
    { "id": 1, "tasks": ["1.3", "1.4", "2.1", "3.2", "4.1", "5.1", "9.1"] },
    { "id": 2, "tasks": ["1.5", "2.2", "2.3", "3.3", "3.4", "4.2", "4.3", "5.2", "5.3", "7.1", "8.1", "9.2"] },
    { "id": 3, "tasks": ["7.2", "7.3", "8.2", "8.3", "11.1", "12.1"] },
    { "id": 4, "tasks": ["13.1", "13.2"] },
    { "id": 5, "tasks": ["13.3", "14.1", "14.2"] },
    { "id": 6, "tasks": ["15.1"] },
    { "id": 7, "tasks": ["15.2", "15.3"] }
  ]
}
```
