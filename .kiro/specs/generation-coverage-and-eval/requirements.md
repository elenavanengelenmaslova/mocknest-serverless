# Requirements Document

## Introduction

This feature improves the AI mock generation quality assurance for MockNest Serverless through two complementary efforts: (1) increasing unit test coverage across the generation module subpackages to reach the project's 90%+ threshold, and (2) adding realistic REST API eval scenarios based on real public API specifications from Stripe and Twilio. Together these changes strengthen confidence in generation correctness and provide measurable quality benchmarks against production-grade API shapes.

## Glossary

- **Generation_Module**: The application-layer code under `nl.vintik.mocknest.application.generation` responsible for AI-assisted mock generation, including subpackages: agent, graphql, parsers, services, usecases, util, validators, and wsdl
- **Eval_Suite**: The Bedrock prompt evaluation test suite that measures AI mock generation quality by running scenarios against Amazon Bedrock and validating output structurally and semantically
- **Eval_Dataset**: The JSON file (`multi-protocol-eval-dataset.json`) containing scenario definitions used by the Eval_Suite
- **SafeUrlResolver**: A utility class in the Generation_Module util subpackage responsible for safely fetching and resolving URLs during WSDL/spec retrieval, including HTTP connection handling, error handling, and URL sanitization
- **Kover**: The Kotlin code coverage tool (`org.jetbrains.kotlinx.kover`) used to measure and enforce the 90% aggregated coverage threshold
- **Semantic_Check**: The LLM-as-a-judge evaluation criteria field in eval scenarios that defines what the generated mocks must satisfy
- **Spec_Subset**: A minimal, faithful subset of a public API specification containing only the endpoints needed for eval scenarios, avoiding vendoring of full specifications

## Requirements

### Requirement 1: Unit Test Coverage for SafeUrlResolver

**User Story:** As a developer, I want comprehensive unit tests for SafeUrlResolver, so that HTTP connection logic, error handling, and URL sanitization are verified and protected against regressions.

#### Acceptance Criteria

1. WHEN a valid HTTP URL is fetched and the server returns a 200 response with a body, THE SafeUrlResolver_Test SHALL verify that the fetch method returns a String whose content equals the response body served by the remote endpoint
2. WHEN the remote server returns an HTTP response code outside the 200–299 range, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the HTTP status code
3. WHEN a SocketTimeoutException occurs during URL fetch, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the text "Timeout" and the configured timeout duration in milliseconds
4. WHEN an UnknownHostException occurs during URL fetch, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the text "Cannot resolve host"
5. WHEN a ConnectException occurs during URL fetch, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the text "Connection refused"
6. WHEN an SSLException occurs during URL fetch, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the text "SSL/TLS error"
7. WHEN a general IOException occurs during URL fetch, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains the text "Network error"
8. WHEN a URL contains query parameters matching any of the sensitive patterns (token, key, secret, auth, sig, password, credential, or x-amz- prefix), THE SafeUrlResolver_Test SHALL verify that sanitizeUrlForLogging replaces the parameter value with the literal string "<redacted>" while preserving non-sensitive parameters unchanged
9. WHEN a URL string with an unsupported scheme (not http or https), a missing host, or a malformed URI syntax is provided, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException without attempting a network connection
10. WHEN the response body exceeds 10 MB (the configured maxResponseBytes), THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message indicates the maximum size has been exceeded
11. WHEN the remote server returns an HTTP redirect (3xx status code), THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver does not follow the redirect and throws a UrlResolutionException containing the 3xx status code
12. WHEN a URL resolves to a private, loopback, link-local, multicast, CGNAT (100.64.0.0/10), or IPv6 ULA (fc00::/7) address, THE SafeUrlResolver_Test SHALL verify that SafeUrlResolver throws a UrlResolutionException whose message contains "unsafe address" without making an HTTP connection

### Requirement 2: Unit Test Coverage for Agent Subpackage

**User Story:** As a developer, I want unit tests covering the agent subpackage, so that the Koog-based AI agent orchestration logic is verified independently of Bedrock calls.

#### Acceptance Criteria

1. WHEN the agent subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the `nl.vintik.mocknest.application.generation.agent` package
2. THE Agent_Tests SHALL mock all external dependencies (AIModelServiceInterface, SpecificationParserInterface, MockValidatorInterface, PromptBuilderService, and UrlFetcher) using MockK and verify agent orchestration logic with no network calls or Bedrock invocations
3. WHEN agent configuration parameters are provided, THE Agent_Tests SHALL verify that the agent is initialized with the specified maxRetries value, the injected PromptBuilderService instance, and the injected AIModelServiceInterface instance
4. IF the AI model returns an unparseable response or the specification parser throws an exception, THEN THE Agent_Tests SHALL verify that the agent propagates the failure as a GenerationResult.failure without retrying beyond the configured maxRetries bound
5. WHEN the mock generation strategy is executed, THE Agent_Tests SHALL verify the strategy graph transitions through setup, generate, validate, and correct nodes in the expected order for both success and validation-failure scenarios

### Requirement 3: Unit Test Coverage for GraphQL Subpackage

**User Story:** As a developer, I want unit tests covering the graphql subpackage, so that GraphQL schema parsing and mock generation logic is verified.

#### Acceptance Criteria

1. WHEN the graphql subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the `nl.vintik.mocknest.application.generation.graphql` package (GraphQLSchemaReducer, GraphQLIntrospectionClientInterface), the `nl.vintik.mocknest.application.generation.parsers.GraphQLSpecificationParser` class, and the `nl.vintik.mocknest.application.generation.validators.GraphQLMockValidator` class
2. WHEN a valid GraphQL introspection response is parsed by GraphQLSchemaReducer, THE GraphQL_Tests SHALL verify that the resulting CompactGraphQLSchema contains: all non-built-in OBJECT fields from the queryType extracted as queries, all non-built-in OBJECT fields from the mutationType extracted as mutations, all non-built-in OBJECT and INPUT_OBJECT types extracted into the types map with their fields and type references preserved, and all non-built-in ENUM types extracted into the enums map with their values preserved
3. WHEN an invalid or malformed GraphQL introspection response is provided, THE GraphQL_Tests SHALL verify that a GraphQLSchemaParsingException is raised for each of the following cases: response is not valid JSON, response is valid JSON but missing the `data.__schema` path, response has `__schema` but missing the `types` array, and response has an empty types array with no query or mutation type defined
4. WHEN a valid introspection response containing nested types, list types, non-null modifiers, and input objects is parsed, THE GraphQL_Tests SHALL use at least 10 diverse test data files via @ParameterizedTest to verify that type references (including `[Type]`, `Type!`, and `[Type!]!` notation) are correctly resolved in operation arguments and field types

### Requirement 4: Unit Test Coverage for Parsers Subpackage

**User Story:** As a developer, I want unit tests covering the parsers subpackage, so that API specification parsing logic for all supported formats is verified.

#### Acceptance Criteria

1. WHEN the parsers subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the parsers subpackage (including OpenAPISpecificationParser, WsdlSpecificationParser, GraphQLSpecificationParser, and CompositeSpecificationParserImpl)
2. WHEN a valid OpenAPI 3.0 or Swagger 2.0 specification is parsed, THE Parser_Tests SHALL verify that the returned APISpecification contains endpoints matching each path-method combination in the input, schemas matching each component schema name, and parameters matching each declared parameter name and location
3. WHEN a valid WSDL specification is parsed, THE Parser_Tests SHALL verify that the returned APISpecification contains one endpoint per WSDL operation with the correct soapAction metadata, schemas matching each XSD type name, and request/response definitions referencing the correct input and output messages
4. WHEN a valid GraphQL introspection result is parsed, THE Parser_Tests SHALL verify that the returned APISpecification contains one endpoint per query and mutation operation, schemas for each custom type and enum, and request body schemas reflecting each operation's arguments
5. WHEN a specification with an unsupported format is provided to the CompositeSpecificationParser, THE Parser_Tests SHALL verify that an IllegalArgumentException is raised with a message indicating the unsupported format name
6. WHEN a specification is provided as a URL string (starting with http:// or https://), THE Parser_Tests SHALL verify that the parser delegates to the appropriate URL resolution mechanism, and WHEN the specification is provided as inline content, THE Parser_Tests SHALL verify that parsing proceeds without URL fetching
7. WHEN the CompositeSpecificationParser receives a parse request, THE Parser_Tests SHALL verify that it delegates to the format-specific parser whose supports() method returns true for the given SpecificationFormat

### Requirement 5: Unit Test Coverage for Services Subpackage

**User Story:** As a developer, I want unit tests covering the services subpackage, so that mock generation service orchestration is verified.

#### Acceptance Criteria

1. WHEN the services subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the `nl.vintik.mocknest.application.generation.services` package
2. WHEN a generation service method is invoked with a valid APISpecification, description string, and MockNamespace, THE Service_Tests SHALL verify that the returned prompt string contains all substituted parameter values and no unreplaced template placeholders (i.e., no `{{` or `}}` sequences remain)
3. IF a generation service method references a prompt template resource that does not exist on the classpath, THEN THE Service_Tests SHALL verify that an IllegalStateException is thrown with a message containing the missing resource path
4. WHEN a generation service method is invoked with each supported SpecificationFormat (OPENAPI_3, SWAGGER_2, GRAPHQL, WSDL), THE Service_Tests SHALL verify that the format-specific prompt template is loaded and the returned prompt contains format-distinguishing content (e.g., "SOAPAction" for WSDL, "GraphQL-over-HTTP" for GRAPHQL, URL prefix routing for REST formats)

### Requirement 6: Unit Test Coverage for Util Subpackage

**User Story:** As a developer, I want unit tests covering the util subpackage beyond SafeUrlResolver, so that all utility functions are verified.

#### Acceptance Criteria

1. WHEN `./gradlew koverHtmlReport` is executed after running unit tests, THE Kover_Report SHALL show at least 90% line coverage for the `nl.vintik.mocknest.application.generation.util` package
2. THE Util_Tests SHALL cover all public functions of SafeUrlResolver (including companion functions `isHttpUrl`, `sanitizeUrlForLogging`, `validateAndResolve`, and `validateUrlSafety`) and the UrlResolutionException class, with at least 2 valid-input and 2 invalid-input scenarios per public function
3. WHEN `sanitizeUrlForLogging` is invoked with a URL containing sensitive query parameters (token, key, secret, auth, password, credential, or x-amz- prefixed parameters), THE SafeUrlResolver SHALL return a URL string where those parameter values are replaced with `<redacted>` and non-sensitive parameters are preserved unchanged
4. IF a URL passed to `validateUrlSafety` or `validateAndResolve` targets a private, loopback, link-local, multicast, CGNAT (100.64.0.0/10), or IPv6 ULA (fc00::/7) address, THEN THE SafeUrlResolver SHALL throw a UrlResolutionException with a message containing "unsafe address"

### Requirement 7: Unit Test Coverage for Validators Subpackage

**User Story:** As a developer, I want unit tests covering the validators subpackage, so that mock output validation logic for all protocols is verified.

#### Acceptance Criteria

1. WHEN the validators subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the validators subpackage
2. WHEN valid WireMock mapping JSON conforming to the protocol specification and WireMock mapping structure is validated, THE Validator_Tests SHALL verify that validation passes without errors for at least 2 valid mapping scenarios per protocol (REST, GraphQL, SOAP)
3. WHEN invalid WireMock mapping JSON is validated, THE Validator_Tests SHALL verify that at least 2 invalid mapping scenarios per protocol are tested and that each reported error identifies the specific validation rule violated
4. THE Validator_Tests SHALL cover protocol-specific validation rules for REST (structural checks, endpoint matching, status code validation, query parameter validation, response body schema validation, and consistency checks), GraphQL (operation extraction, argument validation, and response body format validation), and SOAP (POST method enforcement, URL path matching, SOAPAction validation, XML well-formedness, envelope structure, namespace validation, and Content-Type header validation)
5. WHEN the CompositeMockValidator delegates to multiple registered validators, THE Validator_Tests SHALL verify that validation errors from all validators are aggregated into a single result and that an exception thrown by one validator does not prevent other validators from executing

### Requirement 8: Unit Test Coverage for WSDL Subpackage

**User Story:** As a developer, I want unit tests covering the wsdl subpackage, so that WSDL parsing and SOAP-specific generation logic is verified.

#### Acceptance Criteria

1. WHEN the wsdl subpackage code is exercised by unit tests, THE Kover_Report SHALL show at least 90% line coverage for the wsdl subpackage classes (WsdlParser, WsdlSchemaReducer, and ParsedWsdl)
2. WHEN a valid WSDL 1.1 document is processed by the WsdlParser, THE WSDL_Tests SHALL verify that the returned ParsedWsdl contains the expected serviceName, targetNamespace, soapVersion, at least one operation with non-blank name and soapAction, at least one message entry, and at least one xsdType entry matching values from the input document, using at least 3 structurally diverse WSDL 1.1 test files
3. WHEN a WSDL document contains complex types with at least 2 levels of nesting (a type referencing another type that itself references a third type), THE WSDL_Tests SHALL verify that the WsdlSchemaReducer output includes all transitively referenced types and excludes types not reachable from any operation's input or output message
4. IF the WsdlParser receives a malformed XML document or a WSDL document missing required elements (service name, port type, or binding), THEN THE WSDL_Tests SHALL verify that a WsdlParsingException is thrown within 2 seconds and that no partial ParsedWsdl is returned

### Requirement 9: Stripe Payment Flow Eval Spec Subset

**User Story:** As a developer, I want a minimal Stripe Payment Intents API specification subset for eval scenarios, so that generation quality can be measured against a real-world payment API shape.

#### Acceptance Criteria

1. THE Spec_Subset SHALL be stored at `software/infra/aws/generation/src/test/resources/eval/stripe-payment-openapi-3.0.yaml`
2. THE Spec_Subset SHALL contain the following endpoints: POST /v1/payment_intents, GET /v1/payment_intents/{intent}, POST /v1/payment_intents/{intent}/confirm, GET /v1/charges
3. THE Spec_Subset SHALL be a valid OpenAPI 3.0 document that can be parsed without errors by standard OpenAPI 3.0 parsers
4. THE Spec_Subset SHALL define schemas for PaymentIntent, Charge, and Error objects where each schema includes at least 2 nested object properties, at least 2 enum fields, and at least 3 nullable fields to represent the complexity of real Stripe API response shapes
5. THE Spec_Subset SHALL model PaymentIntent status as an enum containing at least the values: requires_payment_method, requires_confirmation, requires_action, processing, succeeded, canceled
6. THE Spec_Subset SHALL NOT include webhook, callback, or event endpoint definitions

### Requirement 10: Stripe Payment Flow Eval Scenarios

**User Story:** As a developer, I want eval scenarios that test mock generation against the Stripe Payment API subset, so that generation quality for complex financial APIs is measurable.

#### Acceptance Criteria

1. WHEN the scenario `rest-stripe-payment-intent-basic` is executed, THE Eval_Suite SHALL verify that generated mocks produce Stripe-style IDs where each ID starts with the correct prefix (`pi_` for payment intents, `ch_` for charges, `cus_` for customers, `pm_` for payment methods) followed by 14 to 27 alphanumeric characters
2. WHEN the scenario `rest-stripe-payment-intent-consistency` is executed, THE Eval_Suite SHALL verify that the payment intent mock response contains a `customer` field whose value matches a customer ID present in a customer mock response, and a `latest_charge` field whose value matches a charge ID present in a charge mock response
3. WHEN the scenario `rest-stripe-payment-card-declined` is executed, THE Eval_Suite SHALL verify that the generated error mock returns HTTP status 402 with a response body containing an `error` object that includes non-empty `type`, `code`, and `message` string fields
4. THE Semantic_Check for each Stripe scenario SHALL verify that all ID fields in the response match the prefix pattern defined in criterion 1 and that all required response fields specified in the scenario description are present and non-empty
5. THE Eval_Dataset SHALL contain exactly 3 new Stripe scenarios added to the examples array, each referencing a Stripe Payment API subset OpenAPI 3.0 specification file located in the eval resources directory with format `OPENAPI_3` and a `stripe-eval` namespace

### Requirement 11: Twilio Messaging Flow Eval Spec Subset

**User Story:** As a developer, I want a minimal Twilio Messaging API specification subset for eval scenarios, so that generation quality can be measured against a real-world messaging API shape.

#### Acceptance Criteria

1. THE Spec_Subset SHALL be stored at `software/infra/aws/generation/src/test/resources/eval/twilio-messaging-openapi-3.0.yaml`
2. THE Spec_Subset SHALL be a valid OpenAPI 3.0 document that can be parsed without errors by the existing specification parser
3. THE Spec_Subset SHALL contain the following endpoints: POST /2010-04-01/Accounts/{AccountSid}/Messages.json, GET /2010-04-01/Accounts/{AccountSid}/Messages/{MessageSid}.json, GET /2010-04-01/Accounts/{AccountSid}/Messages.json
4. THE Spec_Subset SHALL define a Message schema containing at minimum the following properties: sid (string, SID pattern starting with "SM"), account_sid (string), from (string), to (string), body (string), status (string enum), direction (string enum), date_created (string in RFC 2822 format), date_updated (string in RFC 2822 format), date_sent (string in RFC 2822 format), price (string), price_unit (string), uri (string), and subresource_uris (object containing at least a media key with a URI string value)
5. THE Spec_Subset SHALL define an Error schema containing at minimum the following properties: code (integer), message (string), more_info (string URI), and status (integer HTTP status code)
6. THE Spec_Subset SHALL NOT include webhook or callback endpoint definitions

### Requirement 12: Twilio Messaging Flow Eval Scenarios

**User Story:** As a developer, I want eval scenarios that test mock generation against the Twilio Messaging API subset, so that generation quality for messaging APIs is measurable.

#### Acceptance Criteria

1. WHEN the scenario `rest-twilio-message-basic` is executed, THE Eval_Suite SHALL verify that generated mocks produce SIDs matching Twilio format patterns: account SIDs matching `AC[0-9a-f]{32}` (34 characters total) and message SIDs matching `SM[0-9a-f]{32}` (34 characters total)
2. WHEN the scenario `rest-twilio-message-status-consistency` is executed, THE Eval_Suite SHALL verify that message status values are one of the valid Twilio statuses (queued, sending, sent, delivered, undelivered, failed) and phone numbers match E.164 format (`+` followed by 1 to 15 digits)
3. WHEN the scenario `rest-twilio-message-invalid-number` is executed, THE Eval_Suite SHALL verify that the generated error mock returns an HTTP 4xx response containing a JSON body with non-empty status, message, code, and more_info fields
4. THE Semantic_Check for each Twilio scenario SHALL reference SID regex patterns (`AC[0-9a-f]{32}`, `SM[0-9a-f]{32}`) and E.164 phone number format validation as pass/fail criteria for the LLM judge
5. THE Eval_Dataset SHALL contain exactly 3 new Twilio scenarios added to the examples array, each referencing a Twilio Messaging OpenAPI specification file located in `src/test/resources/eval/` with protocol set to REST and format set to OPENAPI_3

### Requirement 13: Eval Dataset Integrity

**User Story:** As a developer, I want the eval dataset to remain consistent and backward-compatible after adding new scenarios, so that existing eval results are not disrupted.

#### Acceptance Criteria

1. WHEN new scenarios are added to the Eval_Dataset, THE Eval_Dataset SHALL contain exactly 52 total scenarios in the top-level "examples" array
2. THE Eval_Dataset SHALL preserve all 46 existing scenarios with identical field values (input, metadata.protocol, metadata.specFile, metadata.format, metadata.namespace, metadata.description, metadata.semanticCheck) and in their original order within the "examples" array
3. THE Eval_Dataset SHALL remain valid JSON parseable by the existing eval test infrastructure without modification to the test code
4. WHEN the Eval_Suite is run with the updated dataset, THE Eval_Suite SHALL execute all 52 scenarios and produce passing structural validation results for the existing 46 scenarios
5. WHEN new scenarios are added to the Eval_Dataset, THE Eval_Dataset SHALL contain new scenario entries that each include all required fields (input, metadata.protocol, metadata.specFile, metadata.format, metadata.namespace, metadata.description, metadata.semanticCheck) and reference specFile paths that resolve to existing classpath resources
6. IF a new scenario references a specFile that does not exist on the classpath, THEN the Eval_Suite SHALL report a validation failure for that scenario within 5 seconds of test execution start

### Requirement 14: Documentation Updates

**User Story:** As a developer, I want the eval documentation to reflect the new scenarios and API specifications, so that contributors understand the expanded eval coverage.

#### Acceptance Criteria

1. WHEN new Stripe and Twilio scenarios are added to the eval dataset, THE PROMPT_EVAL documentation SHALL update the summary paragraph to state "52 scenarios across 3 protocols and 14 API specifications"
2. THE PROMPT_EVAL documentation SHALL include Stripe and Twilio entries in the API Specification Files table with all columns populated (File, Protocol, Domain, Endpoints)
3. THE PROMPT_EVAL documentation SHALL update the Protocol Breakdown table to show the new REST scenario count that, combined with the unchanged GraphQL (15) and SOAP (15) counts, totals 52
4. THE README Generation Quality section SHALL update the prose and table to reference 52 scenarios across 14 API specifications
5. THE PROMPT_EVAL documentation SHALL update the Cost Considerations "Estimated Cost per Full Suite Run" table to reflect the new total of 52 scenarios and the updated REST scenario count

### Requirement 15: Aggregated Coverage Verification

**User Story:** As a developer, I want to verify that the combined test improvements bring the generation module to 90%+ coverage, so that the project's enforced coverage threshold is met.

#### Acceptance Criteria

1. WHEN `./gradlew koverVerify` is executed after all generation-module tests compile and pass, THE Kover_Verification SHALL complete with exit code 0, confirming that aggregated line coverage across all included modules meets or exceeds the 90% minimum bound
2. WHEN `./gradlew koverHtmlReport` is executed, THE Kover_Report SHALL show each generation-related subpackage (`domain.generation`, `application.generation`, `infra.aws.generation`) at 90% or higher line coverage as displayed in the per-package summary
3. IF a subpackage cannot reach 90% line coverage because it contains code that requires unavailable external runtime resources (such as AWS Lambda entry points or SnapStart priming hooks that cannot execute outside the Lambda environment), THEN THE Coverage_Exclusion SHALL be added to the `kover.reports.total.filters.excludes.classes` block in the root `build.gradle.kts` with an inline comment stating the specific class names excluded and the reason they are untestable
4. IF more than 3 classes are excluded from coverage measurement across all generation subpackages, THEN THE Coverage_Exclusion SHALL be reviewed and approved in the pull request description before merging
