# Implementation Plan: SOAP/WSDL AI Generation

## Overview

This plan breaks down the SOAP/WSDL AI generation feature into discrete, actionable tasks following the phased development order: Domain → Application → Infrastructure → Integration → Documentation. The feature extends the existing AI mock generation flow to support SOAP web services via WSDL parsing, compact representation, AI-driven WireMock mapping generation, and validation with automatic retry/correction.

**Primary test path**: Inline XML content. All automated tests use inline WSDL XML loaded from `src/test/resources/wsdl/`. URL-based fetching is validated manually. Every implementation task is immediately followed by its corresponding test task. All tests are mandatory — none are marked optional.

## Tasks

- [x] 1. Phase 1: Domain Layer — Create SOAP/WSDL domain models and exceptions
  - [x] 1.1 Create WSDL-specific exceptions in the domain layer
    - Create `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/generation/WsdlExceptions.kt`
    - Implement `WsdlParsingException(message: String, cause: Throwable? = null)` extending `RuntimeException`
    - Implement `WsdlFetchException(message: String, cause: Throwable? = null)` extending `RuntimeException`
    - _Requirements: 2.3, 2.4, 2.5, 3.6, 3.7_

  - [x] 1.2 Write unit tests for WSDL exceptions
    - Create test file in `software/domain/src/test/kotlin/`
    - Test that `WsdlParsingException` carries message and cause correctly
    - Test that `WsdlFetchException` carries message and cause correctly
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 3.6, 3.7_

  - [x] 1.3 Create `SoapVersion` enum in the domain layer
    - Create `software/domain/src/main/kotlin/nl/vintik/mocknest/domain/generation/CompactWsdl.kt`
    - Implement `SoapVersion` enum with `SOAP_1_1` (envelopeNamespace=`http://schemas.xmlsoap.org/soap/envelope/`, contentType=`text/xml`) and `SOAP_1_2` (envelopeNamespace=`http://www.w3.org/2003/05/soap-envelope`, contentType=`application/soap+xml`)
    - _Requirements: 9.1, 9.2, 9.5_

  - [x] 1.4 Create `WsdlPortType`, `WsdlOperation`, `WsdlXsdType`, `WsdlXsdField` data classes
    - In the same `CompactWsdl.kt` file, implement all four data classes with `init` validation blocks (non-blank names, non-blank types)
    - `WsdlOperation` fields: `name`, `soapAction`, `inputMessage`, `outputMessage`, `portTypeName`
    - `WsdlXsdType` fields: `name`, `fields: List<WsdlXsdField>`
    - `WsdlXsdField` fields: `name`, `type`
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 4.2, 4.3, 4.4_

  - [x] 1.5 Create `CompactWsdl` domain model with `prettyPrint()`
    - In the same `CompactWsdl.kt` file, implement `CompactWsdl` data class with fields: `serviceName`, `targetNamespace`, `soapVersion`, `portTypes`, `operations`, `xsdTypes`
    - Add `init` validation: non-blank `serviceName`, non-blank `targetNamespace`, non-empty `operations`
    - Implement `prettyPrint()` producing human-readable text with service name, namespace, SOAP version, port types, operations (with soapAction, input, output), and XSD types with fields
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.5_

  - [x] 1.6 Write unit tests for `CompactWsdl` domain model
    - Create test file in `software/domain/src/test/kotlin/`
    - Test `init` validation rules (blank service name, blank namespace, empty operations)
    - Test `prettyPrint()` output includes service name, namespace, SOAP version, all operations with soapAction/input/output, all XSD types with fields
    - Test data class equality and immutability
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 5.1, 5.2, 5.3, 5.5_


- [x] 2. Phase 2: Application Layer — WSDL parsing, reduction, validation, and prompts
  - [x] 2.1 Create `ParsedWsdl` internal model and `WsdlParserInterface`
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/ParsedWsdl.kt` with the full intermediate WSDL structure (all XSD types, binding details, service port addresses)
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlParserInterface.kt` with `fun parse(wsdlXml: String): ParsedWsdl`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.8_

  - [x] 2.2 Implement `WsdlParser` using JDK `DocumentBuilder` (no new XML dependencies)
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlParser.kt`
    - Use `javax.xml.parsers.DocumentBuilder` for XML parsing
    - Extract `targetNamespace` from `<wsdl:definitions>`, service name from `<wsdl:service>`, SOAP version from binding namespace (`http://schemas.xmlsoap.org/wsdl/soap/` → SOAP_1_1, `http://schemas.xmlsoap.org/wsdl/soap12/` → SOAP_1_2, default SOAP_1_1 with warning)
    - Extract `<wsdl:portType>` elements, operations, input/output message references, SOAPAction from `<wsdl:binding>`, inline XSD types from `<wsdl:types>`
    - Throw `WsdlParsingException` on malformed XML (with line info), missing `<wsdl:definitions>`, missing `targetNamespace`, no operations found
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 9.1, 9.2, 9.3, 9.4_

  - [x] 2.3 Write unit tests for `WsdlParser` using inline XML content
    - Create test file in `software/application/src/test/kotlin/`
    - Test successful parsing of SOAP 1.1 document (inline XML): verify service name, namespace, port types, operations, SOAPAction, XSD types extracted correctly
    - Test successful parsing of SOAP 1.2 document (inline XML): verify `SoapVersion.SOAP_1_2` detected
    - Test WSDL with both SOAP 1.1 and 1.2 bindings (inline XML from `mixed-version.wsdl` content)
    - Test default to SOAP_1_1 when no binding namespace found (inline XML)
    - Test `WsdlParsingException` on malformed XML (inline XML from `malformed.wsdl` content)
    - Test `WsdlParsingException` on missing `<wsdl:definitions>` (inline XML)
    - Test `WsdlParsingException` on missing `targetNamespace` (inline XML)
    - Test `WsdlParsingException` on no operations found (inline XML from `invalid-no-operations.wsdl` content)
    - All tests use inline WSDL XML loaded from `src/test/resources/wsdl/`
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 9.1, 9.2, 9.3, 9.4, 12.1, 12.3_

  - [x] 2.4 Create `WsdlSchemaReducerInterface` and implement `WsdlSchemaReducer`
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlSchemaReducerInterface.kt` with `fun reduce(parsedWsdl: ParsedWsdl): CompactWsdl`
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlSchemaReducer.kt`
    - Copy service name, namespace, SOAP version; include all port types and operations with SOAPAction, input/output message element names
    - Walk XSD type graph from referenced message elements — include only reachable types; exclude binding details, service port addresses, unreferenced XSD types, WSDL import/include directives
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 11.3_

  - [x] 2.5 Write unit tests for `WsdlSchemaReducer` using inline XML content
    - Create test file in `software/application/src/test/kotlin/`
    - Test successful reduction: verify `CompactWsdl` contains all operations, SOAPActions, input/output messages, referenced XSD types (inline XML from `complex-types-soap11.wsdl` content)
    - Test unreferenced XSD types are excluded (inline XML from `unreferenced-types-soap11.wsdl` content)
    - Test binding details and service port addresses are excluded (inline XML)
    - Test size reduction: verify `compactWsdl.prettyPrint().length < rawWsdlXml.length` for multi-operation WSDL (inline XML from `multi-operation-soap11.wsdl` content)
    - All tests use inline WSDL XML loaded from `src/test/resources/wsdl/`
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 12.4_

  - [x] 2.6 Write property test: Property-2 (WSDL Extraction Completeness) using inline XML
    - Create property test class in `software/application/src/test/kotlin/`
    - Use `@ParameterizedTest @ValueSource` with 10 WSDL files: `simple-soap11.wsdl`, `simple-soap12.wsdl`, `multi-operation-soap11.wsdl`, `multi-porttype-soap11.wsdl`, `complex-types-soap11.wsdl`, `nested-xsd-soap11.wsdl`, `multi-operation-soap12.wsdl`, `large-service.wsdl`, `calculator-soap11.wsdl`, `weather-soap12.wsdl`
    - For each file (loaded as inline XML from `src/test/resources/wsdl/`): verify all operation names present, all SOAPActions present, all referenced XSD types present
    - **Property 2: WSDL Extraction Completeness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-2")`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 12.7_

  - [x] 2.7 Write property test: Property-3 (Schema Size Reduction) using inline XML
    - Use `@ParameterizedTest @ValueSource` with 8 multi-operation WSDL files: `multi-operation-soap11.wsdl`, `multi-porttype-soap11.wsdl`, `complex-types-soap11.wsdl`, `multi-operation-soap12.wsdl`, `large-service.wsdl`, `calculator-soap11.wsdl`, `weather-soap12.wsdl`, `nested-xsd-soap11.wsdl`
    - For each file (loaded as inline XML): assert `compactWsdl.prettyPrint().length < rawWsdlXml.length`
    - **Property 3: Schema Size Reduction**
    - **Validates: Requirements 4.7**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-3")`
    - _Requirements: 4.7, 12.7_

  - [x] 2.8 Write property test: Property-6 (Unreferenced XSD Type Exclusion) using inline XML
    - Use `@ParameterizedTest @ValueSource` with WSDL files containing unreferenced types: `unreferenced-types-soap11.wsdl`, `complex-types-soap11.wsdl`, `nested-xsd-soap11.wsdl`, `large-service.wsdl`, `multi-porttype-soap11.wsdl`
    - For each file (loaded as inline XML): verify that XSD types not referenced by any operation message are absent from `compactWsdl.xsdTypes`
    - **Property 6: Unreferenced XSD Type Exclusion**
    - **Validates: Requirements 4.6**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-6")`
    - _Requirements: 4.6, 12.7_

  - [x] 2.9 Create `WsdlContentFetcherInterface` in the application layer
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlContentFetcherInterface.kt`
    - Define `suspend fun fetch(url: String): String` — throws `WsdlFetchException` on failure
    - Interface lives in application layer so domain/application have no infrastructure dependency
    - _Requirements: 2.7, 11.1_

  - [x] 2.10 Implement `WsdlSpecificationParser` (inline XML path only at this stage)
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/parsers/WsdlSpecificationParser.kt`
    - Implement `SpecificationParserInterface` with constructor accepting `WsdlContentFetcherInterface`, `WsdlParserInterface`, `WsdlSchemaReducerInterface`
    - Implement `supports(format)` returning true for `SpecificationFormat.WSDL`
    - Implement `parse(content, format)`: if content is an HTTP URL delegate to `contentFetcher.fetch()`, otherwise treat as inline XML; call `wsdlParser.parse()` then `schemaReducer.reduce()`; convert `CompactWsdl` to `APISpecification` (each operation → `EndpointDefinition` with POST method, SOAPAction in metadata, `prettyPrint()` output as `rawContent`)
    - _Requirements: 1.1, 1.3, 3.8, 4.8, 10.5, 11.2_

  - [x] 2.11 Write unit tests for `WsdlSpecificationParser` using inline XML content
    - Create test file in `software/application/src/test/kotlin/`
    - Test inline XML path: parse SOAP 1.1 WSDL inline XML → verify `APISpecification` contains correct endpoints with POST method and SOAPAction metadata
    - Test inline XML path: parse SOAP 1.2 WSDL inline XML → verify SOAP version propagated
    - Test `supports(SpecificationFormat.WSDL)` returns true, other formats return false
    - Test validation of malformed inline XML returns error
    - Test that `rawContent` in `APISpecification` equals `compactWsdl.prettyPrint()` output
    - All tests use inline WSDL XML loaded from `src/test/resources/wsdl/`
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 1.1, 1.3, 12.1_

  - [x] 2.12 Implement `SoapMockValidator`
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/validators/SoapMockValidator.kt`
    - Implement `MockValidatorInterface`
    - Apply all 7 validation rules in order using `DocumentBuilder` for XML parsing (no new XML deps):
      1. Request method must be POST
      2. SOAPAction header (SOAP 1.1) or `action` in Content-Type (SOAP 1.2) references a valid operation name from `CompactWsdl`
      3. Response body is well-formed XML
      4. Response body root is `Envelope` with correct namespace for SOAP version
      5. `Envelope` contains `Body` child element in same namespace
      6. Element inside `Body` uses correct target namespace from `CompactWsdl`
      7. Response `Content-Type` header matches SOAP version (`text/xml` / `application/soap+xml`)
    - Return `MockValidationResult.invalid(errors)` with all errors collected; return `MockValidationResult.valid()` on success
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 11.4_

  - [x] 2.13 Write unit tests for `SoapMockValidator` using inline XML-sourced mocks
    - Create test file in `software/application/src/test/kotlin/`
    - Test valid SOAP 1.1 mock passes all 7 rules (mock generated from inline `simple-soap11.wsdl` content)
    - Test valid SOAP 1.2 mock passes all 7 rules (mock generated from inline `simple-soap12.wsdl` content)
    - Test rule 1: non-POST method rejected with message `"Request method must be POST, found: GET"`
    - Test rule 2: invalid SOAPAction rejected with message containing operation name
    - Test rule 3: non-XML response body rejected with parse error message
    - Test rule 4: wrong envelope namespace rejected with expected vs found namespaces in message
    - Test rule 5: missing Body element rejected
    - Test rule 6: wrong target namespace in body element rejected
    - Test rule 7: wrong Content-Type header rejected
    - Test multiple validation errors returned together in a single result
    - All mocks derived from inline WSDL XML loaded from `src/test/resources/wsdl/`
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 12.5_

  - [x] 2.14 Write property test: Property-7 (Comprehensive SOAP Mock Validation) using inline XML
    - Use `@ParameterizedTest @MethodSource` with diverse mock examples derived from inline WSDL XML content
    - Create 10+ test cases covering: valid SOAP 1.1 mock, valid SOAP 1.2 mock, wrong method, invalid SOAPAction, non-XML body, wrong envelope namespace, missing Body, wrong target namespace, wrong Content-Type, multiple errors simultaneously
    - For each valid mock: assert `MockValidationResult.isValid == true`
    - For each invalid mock: assert `MockValidationResult.isValid == false` and errors list is non-empty with descriptive messages
    - All mocks derived from inline WSDL XML loaded from `src/test/resources/wsdl/`
    - **Property 7: Comprehensive SOAP Mock Validation**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-7")`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 12.5, 12.7_

  - [x] 2.15 Create SOAP-specific prompt templates
    - Create `software/application/src/main/resources/prompts/soap/spec-with-description.txt`
      - Instruct AI to generate WireMock mappings with POST method
      - Match on `SOAPAction` header for SOAP 1.1; match on `action` parameter in `Content-Type` for SOAP 1.2
      - Return XML SOAP envelopes with correct namespace for detected SOAP version
      - Include correct `Content-Type` response header (`text/xml` / `application/soap+xml`)
      - Use target namespace from compact WSDL in response body elements
      - Include `"persistent": true` at top level of each mapping
      - Use same template variables as REST/GraphQL prompts
    - Create `software/application/src/main/resources/prompts/soap/correction.txt`
      - Focus on SOAP-specific validation errors: wrong envelope namespace, missing Body, invalid SOAPAction, wrong Content-Type, wrong target namespace
      - Use same template variables as REST/GraphQL correction prompts
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.9_

  - [x] 2.16 Update `PromptBuilderService` to route WSDL format to SOAP prompts
    - Update `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/services/PromptBuilderService.kt`
    - In `buildSpecWithDescriptionPrompt`: add `SpecificationFormat.WSDL -> "/prompts/soap/spec-with-description.txt"` to the format routing `when` expression
    - In `buildCorrectionPrompt`: add `SpecificationFormat.WSDL -> "/prompts/soap/correction.txt"` to the format routing `when` expression
    - _Requirements: 6.9, 1.3_

  - [x] 2.17 Write unit tests for `PromptBuilderService` WSDL routing
    - Update existing `PromptBuilderService` test file
    - Test that `SpecificationFormat.WSDL` loads from `/prompts/soap/spec-with-description.txt`
    - Test that `SpecificationFormat.WSDL` correction prompt loads from `/prompts/soap/correction.txt`
    - Test that REST and GraphQL formats still load from their respective paths (non-regression)
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 6.9, 1.3_


- [ ] 3. Phase 3: Infrastructure Layer — WSDL content fetcher and Spring configuration
  - [ ] 3.1 Implement `WsdlContentFetcher` in the infrastructure layer
    - Create `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/wsdl/WsdlContentFetcher.kt`
    - Implement `WsdlContentFetcherInterface` using the existing Ktor HTTP client (already a dependency — no new HTTP client dependency)
    - Validate URL safety via `SafeUrlResolver.validateUrlSafety()` before any network call
    - Perform HTTP GET with configurable timeout (default 30 seconds)
    - Throw `WsdlFetchException` on: unsafe URL, non-2xx HTTP status (include status code in message), network failure, timeout, response not valid XML
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 11.1_

  - [ ] 3.2 Write unit tests for `WsdlContentFetcher` using a mock HTTP server (URL path)
    - Create test file in `software/infra/aws/generation/src/test/kotlin/`
    - Test successful fetch returns raw WSDL XML (mock HTTP server serving `simple-soap11.wsdl` content)
    - Test non-2xx HTTP status (404, 500) throws `WsdlFetchException` with status code in message
    - Test timeout throws `WsdlFetchException` with timeout message
    - Test unsafe URL (private IP, loopback) throws `WsdlFetchException` before network call
    - Test non-XML response body throws `WsdlFetchException`
    - Use MockWebServer or WireMock as mock HTTP server
    - Tag tests with `@Tag("soap-wsdl-ai-generation")` and `@Tag("unit")`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 12.2_

  - [ ] 3.3 Wire `WsdlContentFetcher` into `WsdlSpecificationParser` and verify URL path
    - Confirm `WsdlSpecificationParser.parse()` correctly delegates to `WsdlContentFetcherInterface.fetch()` when content is an HTTP URL
    - Verify URL detection logic (content starts with `http`) routes to fetcher
    - Verify inline XML content (not a URL) bypasses fetcher entirely
    - _Requirements: 1.2, 2.1, 2.7_

  - [ ] 3.4 Write property test: Property-1 (Dual Input Mode Equivalence) — URL vs inline XML
    - Create property test in `software/infra/aws/generation/src/test/kotlin/`
    - Use `@ParameterizedTest @MethodSource` with 5+ WSDL files: `simple-soap11.wsdl`, `simple-soap12.wsdl`, `multi-operation-soap11.wsdl`, `calculator-soap11.wsdl`, `weather-soap12.wsdl`
    - For each file: parse via inline XML path; parse via URL path (mock HTTP server serving same XML); assert both `CompactWsdl` results have equal `serviceName`, `targetNamespace`, `soapVersion`, operation names, and XSD type keys
    - **Property 1: Dual Input Mode Equivalence**
    - **Validates: Requirements 1.1, 1.2, 2.6**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-1")`
    - _Requirements: 1.1, 1.2, 2.6, 12.10_

  - [ ] 3.5 Write property test: Property-5 (SOAP Version Detection) using inline XML
    - Use `@ParameterizedTest @MethodSource` with WSDL files and expected SOAP versions
    - Test cases (inline XML): `simple-soap11.wsdl` → SOAP_1_1, `simple-soap12.wsdl` → SOAP_1_2, `multi-operation-soap11.wsdl` → SOAP_1_1, `multi-operation-soap12.wsdl` → SOAP_1_2, `calculator-soap11.wsdl` → SOAP_1_1, `weather-soap12.wsdl` → SOAP_1_2, `mixed-version.wsdl` → both versions present
    - For each: parse inline XML, assert `compactWsdl.soapVersion` matches expected value
    - **Property 5: SOAP Version Detection Correctness**
    - **Validates: Requirements 9.1, 9.2, 9.5**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-5")`
    - _Requirements: 9.1, 9.2, 9.5, 12.7_

  - [ ] 3.6 Write property test: Property-4 (Round-Trip Integrity) using inline XML
    - Use `@ParameterizedTest @ValueSource` with 10 WSDL files: `simple-soap11.wsdl`, `simple-soap12.wsdl`, `multi-operation-soap11.wsdl`, `complex-types-soap11.wsdl`, `multi-operation-soap12.wsdl`, `large-service.wsdl`, `calculator-soap11.wsdl`, `weather-soap12.wsdl`, `nested-xsd-soap11.wsdl`, `multi-porttype-soap11.wsdl`
    - For each file (inline XML): parse → `CompactWsdl` → `prettyPrint()` → parse pretty-printed output → assert equal `serviceName`, `targetNamespace`, `soapVersion`, operation names set, XSD type keys set
    - **Property 4: Round-Trip Integrity**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-4")`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 12.7_

  - [ ] 3.7 Write integration test: inline XML end-to-end (no AWS)
    - Create integration test in `software/application/src/test/kotlin/`
    - Test complete inline XML path: inline WSDL XML → `WsdlSpecificationParser.parse()` → `WsdlSchemaReducer.reduce()` → `APISpecification` → prompt construction → `SoapMockValidator.validate()`
    - Cover SOAP 1.1 (inline XML from `calculator-soap11.wsdl`) and SOAP 1.2 (inline XML from `weather-soap12.wsdl`)
    - Verify `CompactWsdl.prettyPrint()` output is embedded in `APISpecification.rawContent`
    - Verify generated `APISpecification` has correct endpoint count matching WSDL operations
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 1.1, 3.8, 4.8, 12.8_

  - [ ] 3.8 Write integration test: URL path with mock HTTP server
    - Create integration test in `software/infra/aws/generation/src/test/kotlin/`
    - Use MockWebServer or WireMock to serve `calculator-soap11.wsdl` content
    - Verify `WsdlContentFetcher` performs GET and returns XML
    - Verify the `APISpecification` produced via URL path equals the one produced via inline XML path for the same WSDL content
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 2.1, 2.6, 12.10_

  - [ ] 3.9 Create Spring `SoapGenerationConfig` in the infrastructure layer
    - Create `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/config/SoapGenerationConfig.kt`
    - Define `@Configuration` class with Spring beans: `wsdlContentFetcher()`, `wsdlParser()`, `wsdlSchemaReducer()`, `wsdlSpecificationParser(contentFetcher, wsdlParser, schemaReducer)`, `soapMockValidator()`
    - `WsdlSpecificationParser` bean implements `SpecificationParserInterface` — auto-registered via `List<SpecificationParserInterface>` injection in `CompositeSpecificationParserImpl`
    - `SoapMockValidator` bean implements `MockValidatorInterface` — auto-registered via `CompositeMockValidator`
    - _Requirements: 10.5, 11.1, 11.6_


- [ ] 4. Phase 4: Integration — Wire components, end-to-end flows, and non-regression
  - [ ] 4.1 Register `WsdlSpecificationParser` in `CompositeSpecificationParserImpl`
    - Verify `CompositeSpecificationParserImpl` uses `List<SpecificationParserInterface>` injection
    - Confirm `WsdlSpecificationParser` Spring bean is picked up automatically via `SoapGenerationConfig`
    - Verify `SpecificationFormat.WSDL` routes to `WsdlSpecificationParser` and not to REST or GraphQL parsers
    - _Requirements: 1.3, 10.5_

  - [ ] 4.2 Register `SoapMockValidator` in the validator registry
    - Verify `CompositeMockValidator` (or equivalent) uses `List<MockValidatorInterface>` injection
    - Confirm `SoapMockValidator` Spring bean is picked up automatically via `SoapGenerationConfig`
    - Verify SOAP mocks are routed to `SoapMockValidator` for validation
    - _Requirements: 7.1, 10.5_

  - [ ] 4.3 Write property test: Property-8 (Bounded Retry Attempts)
    - Create property test in `software/application/src/test/kotlin/` or `software/infra/aws/generation/src/test/kotlin/`
    - Use `@ParameterizedTest @ValueSource` with retry configurations: 0, 1, 2, 3 max retries
    - Mock AI service to always return invalid SOAP mocks (inline XML-sourced `CompactWsdl` as specification)
    - For each configuration: assert retry coordinator stops after configured maximum and returns failure result
    - Assert total AI calls equals `maxRetries + 1` (never more)
    - **Property 8: Bounded Retry Attempts**
    - **Validates: Requirements 8.3**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-8")`
    - _Requirements: 8.3, 8.4, 12.6_

  - [ ] 4.4 Write property test: Property-9 (WireMock Mapping Compatibility) using inline XML
    - Use `@ParameterizedTest @MethodSource` with 10+ generated SOAP mock examples derived from inline WSDL XML content
    - For each mock: verify JSON is valid WireMock stub mapping (contains `request`, `response`, `"persistent": true` at top level)
    - Verify `request.method` is `POST`
    - Verify `response` contains `body` or `bodyFileName`
    - **Property 9: WireMock Mapping Compatibility**
    - **Validates: Requirements 10.1**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-9")`
    - _Requirements: 10.1, 10.2, 12.7_

  - [ ] 4.5 Write property test: Property-10 (REST/GraphQL Non-Regression)
    - Use `@ParameterizedTest @MethodSource` with existing OpenAPI and GraphQL specification examples
    - For each spec: run through the full generation flow after WSDL support is added; assert generation succeeds and produces valid mocks
    - Verify `SpecificationFormat.OPENAPI_3`, `SWAGGER_2`, and `GRAPHQL` still route to their respective parsers
    - **Property 10: REST and GraphQL Non-Regression**
    - **Validates: Requirements 10.4**
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("Property-10")`
    - _Requirements: 10.4, 12.11_

  - [ ] 4.6 Write integration test: validation-retry loop with correctable errors (inline XML)
    - Create integration test in `software/application/src/test/kotlin/` or `software/infra/aws/generation/src/test/kotlin/`
    - Use inline WSDL XML from `calculator-soap11.wsdl` as specification source
    - Mock AI service: return invalid SOAP mock on first attempt (wrong envelope namespace), return valid mock on retry
    - Verify retry coordinator feeds validation errors back to AI as correction context
    - Verify corrected mock passes `SoapMockValidator` and is returned as the final result
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 8.1, 8.2, 8.4, 8.5, 12.6_

  - [ ] 4.7 Write integration test: validation-retry loop with uncorrectable errors (inline XML)
    - Use inline WSDL XML from `calculator-soap11.wsdl` as specification source
    - Mock AI service to return invalid SOAP mocks on all attempts
    - Verify retry coordinator respects max retry limit (does not exceed configured maximum)
    - Verify generation fails with error result containing the last set of validation errors
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 8.3, 8.6, 12.6_

  - [ ] 4.8 Write LocalStack integration test: inline XML → S3 persistence
    - Create integration test in `software/infra/aws/generation/src/test/kotlin/`
    - Use LocalStack TestContainers with S3 (follow existing LocalStack test patterns with `@BeforeAll`/`@AfterAll`)
    - Use inline WSDL XML from `calculator-soap11.wsdl` as input
    - Mock AI service to return a valid SOAP mock
    - Run complete flow: inline WSDL XML → parsing → reduction → AI generation (mocked) → `SoapMockValidator` → `GenerationStorageInterface` → S3 persistence
    - Verify generated mocks are retrievable from S3 and match expected WireMock mapping structure
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 10.1, 10.2, 12.9_

  - [ ] 4.9 Write REST/GraphQL non-regression integration tests
    - Create or update integration tests verifying existing OpenAPI and GraphQL generation still works after WSDL support is added
    - Test OpenAPI specification generation produces valid mocks (use existing OpenAPI test fixtures)
    - Test GraphQL specification generation produces valid mocks (use existing GraphQL test fixtures)
    - Verify no breaking changes to REST or GraphQL flows
    - Tag with `@Tag("soap-wsdl-ai-generation")` and `@Tag("integration")`
    - _Requirements: 10.4, 12.11_


- [ ] 5. Phase 5: Documentation, test data, and coverage verification
  - [ ] 5.1 Create WSDL test data files in `src/test/resources/wsdl/`
    - Create the following 14 WSDL test data files (place in the relevant module's `src/test/resources/wsdl/` directory):
      - `simple-soap11.wsdl` — Minimal SOAP 1.1 service, 1 operation, no XSD types
      - `simple-soap12.wsdl` — Minimal SOAP 1.2 service, 1 operation, no XSD types
      - `multi-operation-soap11.wsdl` — SOAP 1.1 service with 5+ operations
      - `multi-porttype-soap11.wsdl` — SOAP 1.1 service with multiple port types (2+)
      - `complex-types-soap11.wsdl` — SOAP 1.1 service with nested XSD complex types
      - `nested-xsd-soap11.wsdl` — SOAP 1.1 service with deeply nested XSD types (3+ levels)
      - `unreferenced-types-soap11.wsdl` — SOAP 1.1 service with XSD types not referenced by any operation (for exclusion tests)
      - `multi-operation-soap12.wsdl` — SOAP 1.2 service with 5+ operations
      - `mixed-version.wsdl` — WSDL with both SOAP 1.1 and SOAP 1.2 bindings
      - `large-service.wsdl` — Service with 15+ operations and many XSD types
      - `calculator-soap11.wsdl` — Classic calculator service (SOAP 1.1): Add, Subtract, Multiply, Divide operations
      - `weather-soap12.wsdl` — Weather service (SOAP 1.2): GetWeather, GetForecast operations
      - `malformed.wsdl` — Malformed XML (unclosed tag) for error testing
      - `invalid-no-operations.wsdl` — Valid XML but no WSDL operations for error testing
    - _Requirements: 12.7_

  - [ ] 5.2 Update API documentation with SOAP/WSDL examples
    - Update `docs/api/mocknest-openapi.yaml` to document the WSDL input mode for `POST /ai/generation/from-spec`
    - Add example request with `format: WSDL` and inline XML content
    - Add example request with `format: WSDL` and `specificationUrl` pointing to a WSDL endpoint
    - Add example response showing generated SOAP WireMock mappings
    - _Requirements: 1.1, 1.2_

  - [ ] 5.3 Update `docs/USAGE.md` with SOAP AI generation section
    - Add a new "Generate Mocks from SOAP/WSDL" subsection under "AI-Assisted Mock Generation" (after the GraphQL section)
    - Include a cURL example using `format: WSDL` with a `specificationUrl` pointing to a real WSDL endpoint (e.g. the calculator service at `http://www.dneonline.com/calculator.asmx?WSDL`)
    - Include a cURL example using `format: WSDL` with inline WSDL XML content in the `specification` field
    - Document the `description` field for guiding SOAP mock generation
    - Update the Table of Contents to include the new SOAP AI generation section
    - Do NOT modify the existing manual SOAP mock management section
    - _Requirements: 1.1, 1.2_

  - [ ] 5.4 Update README with SOAP AI generation support
    - In the "AI-Assisted Mock Generation" feature bullet under "Current Features", add SOAP/WSDL alongside REST and GraphQL
    - In the "What's Next?" / "Learn More" section, verify the link to `docs/USAGE.md` mentions SOAP alongside GraphQL
    - Do NOT modify the "Quick Start (5 Minutes)" section
    - _Requirements: 1.1_

  - [ ] 5.5 Update Postman collection with SOAP/WSDL generation examples
    - Add SOAP generation request to `docs/postman/AWS MockNest Serverless.postman_collection.json`
    - Include example with inline WSDL XML content and generation instructions
    - Include example with WSDL URL and generation instructions
    - _Requirements: 1.1_

  - [ ] 5.5 Verify 90%+ code coverage for new SOAP/WSDL code
    - Run `./gradlew koverHtmlReport` and verify aggregated coverage is 90%+ for new SOAP/WSDL code
    - Run `./gradlew koverVerify` to enforce the 80% project-wide threshold
    - Critical paths must meet: `WsdlParser` ≥ 95%, `WsdlSchemaReducer` ≥ 95%, `SoapMockValidator` ≥ 95%
    - Identify any coverage gaps and add targeted tests if needed
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.10, 12.11, 12.12, 12.13_

- [ ] 6. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tests are mandatory — no tests are marked optional or skipped (Requirement 12.12)
- Inline XML content is the primary test path — every test task explicitly loads WSDL XML from `src/test/resources/wsdl/` (Requirement 12 note)
- URL-based fetching is validated manually during development; automated tests for the URL path use a mock HTTP server
- All tests are tagged with `@Tag("soap-wsdl-ai-generation")` and the appropriate category tag (`unit`, `property`, or `integration`)
- Property tests reference their property number from the design document (Property-1 through Property-10)
- No new XML library dependencies — `WsdlParser` and `SoapMockValidator` use JDK `javax.xml.parsers.DocumentBuilder`
- No new HTTP client dependencies — `WsdlContentFetcher` reuses the existing Ktor HTTP client from the GraphQL feature
- Implementation follows clean architecture: Domain → Application → Infrastructure
- `SpecificationFormat.WSDL` already exists in the domain model — no enum change required
- `WsdlSpecificationParser` and `SoapMockValidator` are auto-registered via Spring `List<T>` injection
