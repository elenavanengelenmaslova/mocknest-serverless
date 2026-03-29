# Requirements Document

## Introduction

This document specifies requirements for adding SOAP/WSDL support to the existing AI mock generation flow in MockNest Serverless. The feature extends the current "create new mocks with AI" capability — which already supports REST (OpenAPI) and GraphQL — to support SOAP web services by fetching or accepting WSDL documents, parsing them into a compact representation, generating WireMock mock mappings using AI, and validating the output against the WSDL with automatic retry/correction.

SOAP requests are handled as HTTP POST requests with XML payloads, matching on the `SOAPAction` header and XML body patterns. Generated mocks return XML SOAP envelopes with proper namespace declarations. Both SOAP 1.1 and SOAP 1.2 are supported.

## Glossary

- **WSDL_Parser**: Component that parses WSDL XML documents and extracts service definitions, port types, operations, messages, and XSD types
- **WSDL_Content_Fetcher**: Infrastructure component that retrieves WSDL documents from remote URLs using an HTTP client
- **Schema_Reducer**: Component that converts a parsed WSDL document into a compact internal representation suitable for AI consumption
- **Compact_WSDL**: Reduced WSDL representation containing only service name, port type names, operation names, input/output message element names, and relevant XSD type definitions
- **SOAP_Mock_Generator**: Component that generates WireMock-compatible SOAP mock mappings using AI, based on the Compact_WSDL and user instructions
- **SOAP_Validator**: Component that validates generated WireMock mappings against the Compact_WSDL, verifying SOAP envelope structure, namespace URIs, and operation names
- **Retry_Coordinator**: Component that orchestrates the validation-retry loop when AI output is invalid, reusing the existing pattern from REST and GraphQL generation
- **AI_Generation_Flow**: The existing mock generation pipeline that currently supports REST (OpenAPI) and GraphQL specifications
- **WireMock_Mapping**: JSON configuration that defines mock request matching and response behavior in the WireMock engine
- **SOAP_Envelope**: The top-level XML element wrapping every SOAP message, containing a Header (optional) and Body
- **SOAP_Action**: HTTP header (SOAP 1.1) or `action` parameter in the `Content-Type` header (SOAP 1.2) that identifies the SOAP operation being invoked
- **Port_Type**: WSDL element that groups related operations; called `interface` in WSDL 2.0
- **XSD_Type**: XML Schema Definition type used in WSDL message parts to describe request and response data structures
- **SOAP_1_1**: SOAP protocol version using namespace `http://schemas.xmlsoap.org/soap/envelope/` and `text/xml` Content-Type
- **SOAP_1_2**: SOAP protocol version using namespace `http://www.w3.org/2003/05/soap-envelope` and `application/soap+xml` Content-Type

## Requirements

### Requirement 1: WSDL Input Support

**User Story:** As a developer, I want to provide a WSDL document — either as inline XML content or as a URL — so that I can generate mocks for SOAP web services using the existing AI generation flow.

#### Acceptance Criteria

1. WHEN a user submits a WSDL document as inline XML content with instructions, THE AI_Generation_Flow SHALL accept the request using the existing `SpecWithDescriptionRequest` model with `format = WSDL`
2. WHEN a user submits a WSDL URL with instructions, THE AI_Generation_Flow SHALL accept the request using the existing `SpecWithDescriptionRequest` model with `specificationUrl` set and `format = WSDL`
3. THE AI_Generation_Flow SHALL distinguish WSDL requests from REST and GraphQL requests based on the `SpecificationFormat.WSDL` parameter
4. THE AI_Generation_Flow SHALL reuse existing request validation and job tracking mechanisms for WSDL requests
5. IF both `specificationContent` and `specificationUrl` are provided, THEN THE AI_Generation_Flow SHALL return a validation error indicating only one input mode is allowed
6. IF neither `specificationContent` nor `specificationUrl` is provided, THEN THE AI_Generation_Flow SHALL return a validation error indicating at least one input mode is required

### Requirement 2: WSDL Document Fetching

**User Story:** As a developer, I want the system to fetch WSDL documents from remote URLs automatically, so that I don't need to manually download and provide WSDL XML content.

#### Acceptance Criteria

1. WHEN a WSDL URL is provided, THE WSDL_Content_Fetcher SHALL perform an HTTP GET request to retrieve the WSDL document
2. THE WSDL_Content_Fetcher SHALL validate the URL against the existing safe URL rules before making any network request
3. IF the URL is unreachable or returns a non-2xx HTTP status, THEN THE WSDL_Content_Fetcher SHALL return an error message indicating the fetch failure with the HTTP status code
4. IF the response body is not valid XML, THEN THE WSDL_Content_Fetcher SHALL return an error message indicating the content is not a valid WSDL document
5. IF the request times out, THEN THE WSDL_Content_Fetcher SHALL return an error message indicating a timeout
6. WHEN the fetch succeeds, THE WSDL_Content_Fetcher SHALL return the raw WSDL XML content as a string
7. THE WSDL_Content_Fetcher SHALL be implemented in the infrastructure layer and SHALL NOT be referenced directly from the application or domain layers

### Requirement 3: WSDL Parsing

**User Story:** As a system, I want to parse WSDL documents and extract service definitions, so that the AI can generate accurate SOAP mock mappings.

#### Acceptance Criteria

1. WHEN a WSDL 1.1 document is provided, THE WSDL_Parser SHALL extract the service name, target namespace, and binding style (document/literal or RPC/encoded)
2. WHEN a WSDL 1.1 document is provided, THE WSDL_Parser SHALL extract all port type names and their associated operations
3. WHEN a WSDL 1.1 document is provided, THE WSDL_Parser SHALL extract the input and output message element names for each operation
4. WHEN a WSDL 1.1 document is provided, THE WSDL_Parser SHALL extract XSD type definitions referenced by message parts, including element names, complex types, and simple types
5. WHEN a WSDL 1.1 document is provided, THE WSDL_Parser SHALL extract the SOAP binding information including the SOAPAction value for each operation
6. IF the WSDL document is not well-formed XML, THEN THE WSDL_Parser SHALL return a descriptive error indicating the XML parse failure with position information where available
7. IF the WSDL document is well-formed XML but does not conform to the WSDL 1.1 schema, THEN THE WSDL_Parser SHALL return a descriptive error identifying the missing or invalid elements
8. THE WSDL_Parser SHALL be implemented in the application layer and SHALL NOT contain AWS-specific code

### Requirement 4: Compact WSDL Representation for AI Consumption

**User Story:** As a system, I want to convert a parsed WSDL document into a compact representation, so that AI token usage remains reasonable and the AI receives only the information needed for mock generation.

#### Acceptance Criteria

1. WHEN a parsed WSDL document is received, THE Schema_Reducer SHALL produce a Compact_WSDL containing the service name, target namespace, and SOAP version
2. WHEN a parsed WSDL document is received, THE Schema_Reducer SHALL include all port type names and operation names in the Compact_WSDL
3. WHEN a parsed WSDL document is received, THE Schema_Reducer SHALL include the input and output message element names for each operation
4. WHEN a parsed WSDL document is received, THE Schema_Reducer SHALL include the SOAPAction value for each operation
5. WHEN a parsed WSDL document is received, THE Schema_Reducer SHALL include XSD type definitions that are directly referenced by operation messages
6. THE Schema_Reducer SHALL exclude WSDL binding details, WSDL service port addresses, and XSD types not referenced by any operation message
7. THE Compact_WSDL produced by THE Schema_Reducer SHALL be measurably smaller in character count than the raw WSDL XML for any WSDL document with more than two operations
8. THE Schema_Reducer SHALL be implemented in the application layer and SHALL NOT contain AWS-specific code

### Requirement 5: Compact WSDL Pretty Printing and Round-Trip Integrity

**User Story:** As a developer, I want to serialize and deserialize the Compact_WSDL representation, so that round-trip testing can verify parsing integrity.

#### Acceptance Criteria

1. THE Compact_WSDL domain model SHALL provide a `prettyPrint()` function that formats the compact representation as human-readable text including service name, namespace, operations, and type definitions
2. THE `prettyPrint()` output SHALL include all operation names with their input and output message element names
3. THE `prettyPrint()` output SHALL include all XSD type definitions with their field names and types
4. FOR ALL valid Compact_WSDL objects, parsing a WSDL document then pretty-printing then parsing the pretty-printed output SHALL produce an equivalent Compact_WSDL representation (round-trip property)
5. THE `prettyPrint()` output SHALL include the SOAP version and target namespace

### Requirement 6: SOAP Mock Generation with AI

**User Story:** As a developer, I want the AI to generate WireMock SOAP mock mappings from the Compact_WSDL and my instructions, so that I can quickly create realistic SOAP mocks.

#### Acceptance Criteria

1. WHEN a Compact_WSDL and user instructions are provided, THE SOAP_Mock_Generator SHALL construct an AI prompt containing both the compact schema and the instructions
2. THE SOAP_Mock_Generator SHALL inject the WireMock stub JSON schema into the prompt, consistent with the existing REST and GraphQL prompt patterns
3. THE SOAP_Mock_Generator SHALL request the AI to generate WireMock_Mapping JSON that matches SOAP HTTP POST requests
4. THE SOAP_Mock_Generator SHALL instruct the AI to match on the `SOAPAction` header for SOAP 1.1 operations
5. THE SOAP_Mock_Generator SHALL instruct the AI to match on the `action` parameter in the `Content-Type` header for SOAP 1.2 operations
6. THE SOAP_Mock_Generator SHALL instruct the AI to generate XML SOAP response bodies with the correct envelope namespace for the detected SOAP version
7. THE SOAP_Mock_Generator SHALL instruct the AI to include the correct `Content-Type` response header (`text/xml` for SOAP 1.1, `application/soap+xml` for SOAP 1.2)
8. THE SOAP_Mock_Generator SHALL integrate with the existing `AIModelServiceInterface` without requiring new AI infrastructure
9. THE SOAP_Mock_Generator SHALL load its prompt templates from `software/application/src/main/resources/prompts/soap/`

### Requirement 7: SOAP Mock Validation

**User Story:** As a system, I want to validate generated SOAP mock mappings against the Compact_WSDL, so that invalid AI output is detected and corrected automatically.

#### Acceptance Criteria

1. WHEN a WireMock_Mapping is generated, THE SOAP_Validator SHALL verify that the request matcher targets HTTP POST method
2. THE SOAP_Validator SHALL verify that the SOAPAction header matcher (SOAP 1.1) or Content-Type action parameter matcher (SOAP 1.2) references a valid operation name from the Compact_WSDL
3. THE SOAP_Validator SHALL verify that the response body is a well-formed XML document
4. THE SOAP_Validator SHALL verify that the response body contains a SOAP Envelope element with the correct namespace URI for the detected SOAP version
5. THE SOAP_Validator SHALL verify that the response body contains a SOAP Body element inside the Envelope
6. THE SOAP_Validator SHALL verify that the response body element inside the SOAP Body uses the correct target namespace from the Compact_WSDL
7. THE SOAP_Validator SHALL verify that the response Content-Type header matches the SOAP version (`text/xml` for SOAP 1.1, `application/soap+xml` for SOAP 1.2)
8. IF validation fails, THEN THE SOAP_Validator SHALL return a list of specific validation errors with context sufficient for the AI to correct the mapping

### Requirement 8: Automatic Retry and Correction Loop

**User Story:** As a system, I want to automatically retry AI generation with validation errors as feedback, so that invalid SOAP mock output is corrected without manual intervention.

#### Acceptance Criteria

1. WHEN THE SOAP_Validator detects validation errors, THE Retry_Coordinator SHALL feed the errors back to the AI as additional context
2. THE Retry_Coordinator SHALL request the AI to regenerate the WireMock_Mapping with corrections applied
3. THE Retry_Coordinator SHALL limit retry attempts to prevent infinite loops, reusing the existing retry limit from REST and GraphQL generation
4. THE Retry_Coordinator SHALL reuse the existing retry/correction pattern from `MockGenerationFunctionalAgent`
5. WHEN validation succeeds after retry, THE Retry_Coordinator SHALL return the corrected WireMock_Mapping
6. IF validation fails after the maximum number of retry attempts, THEN THE Retry_Coordinator SHALL return an error indicating generation failure with the last set of validation errors

### Requirement 9: SOAP Version Detection

**User Story:** As a system, I want to detect the SOAP version from the WSDL document, so that generated mocks use the correct envelope namespace, Content-Type, and header matching patterns.

#### Acceptance Criteria

1. WHEN a WSDL document references the SOAP 1.1 binding namespace (`http://schemas.xmlsoap.org/wsdl/soap/`), THE WSDL_Parser SHALL set the detected SOAP version to SOAP 1.1
2. WHEN a WSDL document references the SOAP 1.2 binding namespace (`http://schemas.xmlsoap.org/wsdl/soap12/`), THE WSDL_Parser SHALL set the detected SOAP version to SOAP 1.2
3. WHEN a WSDL document contains both SOAP 1.1 and SOAP 1.2 bindings, THE WSDL_Parser SHALL extract operations for both versions and include the version in each operation's metadata
4. IF no SOAP binding namespace is found, THEN THE WSDL_Parser SHALL default to SOAP 1.1 and include a warning in the parse result
5. THE Compact_WSDL SHALL carry the detected SOAP version so that THE SOAP_Mock_Generator and THE SOAP_Validator can apply version-specific rules without re-parsing the WSDL

### Requirement 10: Integration with Existing Persistence and Runtime

**User Story:** As a developer, I want generated SOAP mocks to be persisted and served by the existing runtime, so that SOAP mocks work seamlessly with the rest of the system.

#### Acceptance Criteria

1. THE SOAP_Mock_Generator SHALL produce WireMock_Mapping JSON that is compatible with the existing persistence model
2. THE AI_Generation_Flow SHALL persist generated SOAP mocks using the existing `GenerationStorageInterface`
3. THE WireMock runtime SHALL serve SOAP mocks using its existing HTTP POST matching and XML body matching capabilities
4. THE AI_Generation_Flow SHALL preserve existing REST and GraphQL mock generation behavior without regression
5. THE `SpecificationFormat.WSDL` value SHALL already exist in the domain model (it is present in the codebase) and THE `CompositeSpecificationParserImpl` SHALL register the new `WSDLSpecificationParser` automatically via the existing parser registration mechanism

### Requirement 11: Clean Architecture and Module Boundaries

**User Story:** As a developer, I want SOAP/WSDL-specific code to follow clean architecture principles, so that the codebase remains maintainable and testable.

#### Acceptance Criteria

1. THE WSDL_Content_Fetcher SHALL be implemented in the infrastructure layer (`software/infra/aws/generation/`) with AWS-specific HTTP client code
2. THE WSDL_Parser SHALL be implemented in the application layer (`software/application/`) as protocol-specific parsing logic
3. THE Schema_Reducer SHALL be implemented in the application layer alongside the existing `GraphQLSchemaReducer`
4. THE SOAP_Validator SHALL be implemented in the application layer alongside the existing `OpenAPIMockValidator` and `GraphQLMockValidator`
5. THE Compact_WSDL domain model SHALL be defined in the domain layer (`software/domain/`) alongside `CompactGraphQLSchema`
6. THE implementation SHALL reuse existing AI orchestration patterns from `MockGenerationFunctionalAgent`
7. THE implementation SHALL NOT introduce AWS-specific code into the application or domain layers

### Requirement 12: Comprehensive Testing

**User Story:** As a developer, I want comprehensive automated tests for WSDL parsing and SOAP mock generation, so that the feature is reliable and maintainable.

**Note on test coverage rationale:** The URL-based input mode (fetching WSDL from a remote endpoint) is validated manually during development. The inline XML content input mode is NOT tested manually and therefore MUST be covered extensively by automated unit and property-based tests to compensate. Every parsing, reduction, validation, and generation scenario MUST be exercised using inline WSDL XML content as the primary test input.

#### Acceptance Criteria

1. THE test suite SHALL include unit tests for successful WSDL parsing covering SOAP 1.1 and SOAP 1.2 documents using inline XML content as input
2. THE test suite SHALL include unit tests for WSDL fetch failure scenarios (unreachable URL, non-XML response, timeout) covering the URL-based input path
3. THE test suite SHALL include unit tests for WSDL parse failure scenarios (malformed XML, missing required WSDL elements) using inline XML content as input
4. THE test suite SHALL include unit tests for schema reduction using inline XML content, verifying that the Compact_WSDL is smaller than the raw WSDL and contains all required operations
5. THE test suite SHALL include unit tests for SOAP mock validation covering all validation rules (envelope namespace, Body element, SOAPAction, Content-Type) using mocks generated from inline XML content
6. THE test suite SHALL include unit tests for the retry/correction loop with both success and failure outcomes using inline XML content as the specification source
7. THE test suite SHALL include property-based tests using `@ParameterizedTest` with a minimum of 10 diverse WSDL test data files (stored as inline XML in `src/test/resources/`) covering simple services, complex services with multiple port types, services with XSD complex types, SOAP 1.1 services, SOAP 1.2 services, services with multiple operations, and services with nested XSD types — all exercised via the inline XML content input path
8. THE test suite SHALL include an integration test that exercises the complete inline XML content path end-to-end: inline WSDL XML → parsing → reduction → AI generation → validation → WireMock mapping output
9. THE test suite SHALL include integration tests using LocalStack TestContainers to validate end-to-end WSDL parsing and mock persistence with S3 storage using inline XML content as input
10. THE test suite SHALL include an integration test that exercises the URL-based input path end-to-end using a mock HTTP server to serve the WSDL document, verifying that URL fetching, parsing, and generation produce the same result as the equivalent inline XML content test
11. THE test suite SHALL verify that REST and GraphQL AI generation continue to work without regression after WSDL support is added
12. ALL tests in the test suite SHALL be mandatory with no tests marked as optional or skipped
13. ALL tests SHALL be tagged with the feature tag `soap-wsdl-ai-generation` and the appropriate category tag (`unit`, `property`, or `integration`)
