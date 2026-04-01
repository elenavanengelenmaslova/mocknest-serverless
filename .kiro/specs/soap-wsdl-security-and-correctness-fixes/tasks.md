# Implementation Plan

## Priority 1: Security Fixes

- [x] 1. Write bug condition exploration test for SSRF/DNS rebinding vulnerability
  - **Property 1: Bug Condition** - SSRF/DNS Rebinding TOCTOU Vulnerability
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the TOCTOU vulnerability exists
  - **Scoped PBT Approach**: Test with mock DNS that changes resolution between validation and fetch
  - Test that WsdlContentFetcher performs second DNS lookup after SafeUrlResolver validation
  - Verify unfixed code can access internal network after DNS rebinding attack
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found to understand root cause
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1_

- [x] 2. Write preservation property tests for SSRF protection (BEFORE implementing fix)
  - **Property 2: Preservation** - SSRF Protection for Safe and Internal URLs
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for safe external URLs (should succeed)
  - Observe behavior on UNFIXED code for internal/private network URLs (should be rejected)
  - Write property-based tests capturing observed SSRF protection behavior
  - Test with 10+ diverse URL patterns (safe external, internal IPs, localhost, private ranges)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline SSRF protection to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 1.1, 1.2_


- [x] 3. Fix SSRF/DNS rebinding vulnerability

  - [x] 3.1 Implement DNS pinning in WsdlContentFetcher
    - Modify fetch() to use pre-resolved addresses from SafeUrlResolver
    - Extract hostname from URL using URI(url).host
    - Create PinnedDns instance with hostname and validated addresses
    - Build new OkHttpClient with pinned DNS: client.newBuilder().dns(pinnedDns).build()
    - Use pinned client for request execution
    - Add check for empty address list and throw WsdlFetchException if empty
    - _Bug_Condition: isBugCondition_Bug1(input) where SafeUrlResolver validates URL and WsdlContentFetcher performs second DNS lookup_
    - _Expected_Behavior: WsdlContentFetcher uses pre-resolved addresses via PinnedDns, preventing second DNS lookup_
    - _Preservation: Safe external URLs continue to fetch successfully, internal URLs continue to be rejected_
    - _Requirements: 1.1, 1.2_

  - [x] 3.2 Write unit tests for DNS pinning implementation
    - Test DNS pinning with pre-resolved addresses
    - Test error handling for empty address list
    - Test successful fetch with pinned DNS
    - Test SSRF protection still works with pinned DNS
    - Follow Given-When-Then naming convention
    - Use MockK for mocking dependencies
    - _Requirements: 1.1_

  - [x] 3.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - DNS Pinning Prevents TOCTOU
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms DNS pinning prevents rebinding
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 1.1_

  - [x] 3.4 Verify preservation tests still pass
    - **Property 2: Preservation** - SSRF Protection Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all SSRF protection tests still pass after fix

## Priority 2: Correctness Fixes


- [x] 4. Write bug condition exploration test for multiple port type service address misattribution
  - **Property 1: Bug Condition** - Multiple SOAP 1.2 Port Types with Different Service Addresses
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate all operations get same service address
  - **SCOPE**: We ONLY support SOAP 1.2 - this bug is about multiple SOAP 1.2 port types with different service addresses
  - **Scoped PBT Approach**: Load multi-porttype-soap12.wsdl with multiple SOAP 1.2 bindings and different service addresses
  - Test that WsdlSpecificationParser assigns same service address to all operations
  - Verify all operations have first service address regardless of their actual binding's service address
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "GetProduct operation assigned /multiport/user instead of /multiport/product")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 2.1_

- [x] 5. Write preservation property tests for SOAP 1.2 port type parsing (BEFORE implementing fix)
  - **Property 2: Preservation** - SOAP 1.2-Only WSDL Parsing
  - **IMPORTANT**: Follow observation-first methodology
  - **SCOPE**: We ONLY support SOAP 1.2
  - Observe behavior on UNFIXED code for SOAP 1.2-only WSDLs (should parse correctly)
  - Write property-based tests capturing observed correct parsing behavior
  - Test with 10+ SOAP 1.2-only WSDL files
  - Verify all operations have correct paths and SOAP 1.2 version
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 2.1, 2.2_

- [x] 6. Fix multiple port type service address misattribution

  - [x] 6.1 Enhance ParsedWsdl to include binding-to-port mapping
    - Add operationBindings: Map<String, ParsedBindingDetail> to ParsedWsdl
    - Key format: "portTypeName#operationName"
    - Value: binding details including service address
    - Update ParsedBindingDetail to include serviceAddress field
    - **SCOPE**: We ONLY support SOAP 1.2 - all bindings are SOAP 1.2
    - _Bug_Condition: isBugCondition_Bug2(input) where WSDL contains multiple SOAP 1.2 bindings with different service addresses_
    - _Expected_Behavior: Each operation has correct service address from its binding_
    - _Preservation: SOAP 1.2-only WSDLs continue to parse correctly_
    - _Requirements: 2.1, 2.2_

  - [x] 6.2 Update WsdlParser to extract per-binding service addresses
    - Modify extractBindingDetails() to extract service port addresses per binding
    - Map each binding to its service address from <wsdl:port> elements
    - Return Map<String, ParsedBindingDetail> with service address included
    - Update buildOperations() to create operationBindings map
    - **SCOPE**: We ONLY support SOAP 1.2
    - _Requirements: 2.1_

  - [x] 6.3 Update WsdlSpecificationParser for per-operation resolution
    - Remove single serviceAddressPath() call
    - For each operation, look up binding in compactWsdl.operationBindings
    - Extract service address from binding
    - Create endpoint with operation-specific path
    - **SCOPE**: We ONLY support SOAP 1.2 - no version resolution needed
    - _Requirements: 2.1_

  - [x] 6.4 Write unit tests for per-operation binding resolution
    - Test WSDL with multiple SOAP 1.2 bindings and different service addresses
    - Test operation-to-binding mapping correctness
    - Test per-operation service address assignment
    - Follow Given-When-Then naming convention
    - _Requirements: 2.1_

  - [x] 6.5 Write property-based tests for WSDL parsing
    - Create 10-20 diverse WSDL test files covering simple, complex, nested, large scenarios
    - Use @ParameterizedTest with @ValueSource to test all files
    - Verify properties: all operations extracted, all types captured, correct service address assignment
    - **SCOPE**: All test files use SOAP 1.2 only
    - _Requirements: 2.1_

  - [x] 6.6 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Per-Operation Service Address
    - **IMPORTANT**: Re-run the SAME test from task 4 - do NOT write a new test
    - Run bug condition exploration test from step 4
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1_

  - [x] 6.7 Verify preservation tests still pass
    - **Property 2: Preservation** - SOAP 1.2-Only Parsing Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 5 - do NOT write new tests
    - Run preservation property tests from step 5
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 7. Write bug condition exploration test for missing URL validation
  - **Property 1: Bug Condition** - Missing URL/Path Validation
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate validator accepts wrong URL paths
  - **Scoped PBT Approach**: Create mock with correct SOAPAction but wrong urlPath
  - Test that SoapMockValidator passes validation despite incorrect URL
  - Verify validator only checks SOAPAction, not URL path
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "Mock with urlPath '/WrongService' passes validation for spec with '/CalculatorService'")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 3.1_

- [ ] 8. Write preservation property tests for SOAP mock validation (BEFORE implementing fix)
  - **Property 2: Preservation** - Valid Mock Validation
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for correctly formed SOAP mocks (should pass validation)
  - Observe behavior on UNFIXED code for mocks with incorrect SOAPAction (should fail validation)
  - Write property-based tests capturing observed validation behavior
  - Test with 10+ diverse mock configurations (valid and invalid SOAPAction scenarios)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline validation to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2_

- [ ] 9. Fix missing URL validation

  - [ ] 9.1 Add validateUrlPath() helper method to SoapMockValidator
    - Extract urlPath or url from request node
    - Compare against specification.endpoints[*].path
    - Also check namespaced paths: /${mockNamespace.displayName()}${endpoint.path}
    - Return list of errors if no match found
    - _Bug_Condition: isBugCondition_Bug3(input) where mock has correct SOAPAction but wrong urlPath_
    - _Expected_Behavior: Validator fails validation with URL path error_
    - _Preservation: Mocks with correct SOAPAction and correct urlPath continue to pass_
    - _Requirements: 3.1, 3.2_

  - [ ] 9.2 Call validateUrlPath() in validate() method
    - Add call after Rule 1 (POST method check)
    - Accumulate errors in existing errors list
    - This becomes Rule 1b in the validation sequence
    - _Requirements: 3.1_

  - [ ] 9.3 Write unit tests for URL path validation
    - Test URL path validation with correct paths
    - Test URL path validation with incorrect paths
    - Test URL path validation with namespaced paths
    - Test edge cases: missing urlPath, url matcher patterns
    - Follow Given-When-Then naming convention
    - _Requirements: 3.1_

  - [ ] 9.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - URL Path Validation Enforced
    - **IMPORTANT**: Re-run the SAME test from task 7 - do NOT write a new test
    - Run bug condition exploration test from step 7
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 3.1_

  - [ ] 9.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Valid Mock Validation Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 8 - do NOT write new tests
    - Run preservation property tests from step 8
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 10. Write bug condition exploration test for non-SOAP WSDL silent fallback
  - **Property 1: Bug Condition** - Non-SOAP WSDL Silent Fallback
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate parser accepts non-SOAP WSDLs
  - **SCOPE**: We ONLY support SOAP 1.2 - reject all other protocols and SOAP versions
  - **Scoped PBT Approach**: Create WSDL with only HTTP bindings (no SOAP namespace)
  - Test that WsdlParser silently defaults instead of throwing exception
  - Verify parser accepts non-SOAP bindings
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "HTTP-only WSDL accepted instead of throwing exception")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 4.1_

- [ ] 11. Write preservation property tests for SOAP WSDL parsing (BEFORE implementing fix)
  - **Property 2: Preservation** - Valid SOAP 1.2 WSDL Parsing
  - **IMPORTANT**: Follow observation-first methodology
  - **SCOPE**: We ONLY support SOAP 1.2
  - Observe behavior on UNFIXED code for valid SOAP 1.2 WSDLs (should parse successfully)
  - Write property-based tests capturing observed parsing behavior
  - Test with 10+ valid SOAP 1.2 WSDL files
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline parsing to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 4.1, 4.2_

- [ ] 12. Fix non-SOAP WSDL silent fallback

  - [ ] 12.1 Update detectSoapVersion() to throw exception for non-SOAP WSDLs
    - Remove SOAP 1.1 fallback when no SOAP bindings found
    - Throw WsdlParsingException with clear message: "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
    - Reject SOAP 1.1 bindings with clear message: "Only SOAP 1.2 is supported"
    - **SCOPE**: We ONLY support SOAP 1.2 - reject all other versions and protocols
    - _Bug_Condition: isBugCondition_Bug4(input) where WSDL contains only HTTP bindings or SOAP 1.1_
    - _Expected_Behavior: Parser throws WsdlParsingException with clear message_
    - _Preservation: Valid SOAP 1.2 WSDLs continue to parse successfully_
    - _Requirements: 4.1, 4.2_

  - [ ] 12.2 Document SOAP 1.2-only support in project documentation
    - Add section to README.md under "Supported Protocols" or "SOAP/WSDL Support"
    - Clearly state: "MockNest Serverless supports SOAP 1.2 only. SOAP 1.1 is not supported."
    - Add to docs/USAGE.md or create docs/LIMITATIONS.md if it doesn't exist
    - Document that WSDLs with SOAP 1.1 bindings will be rejected with clear error message
    - Include rationale: simplified implementation, modern standard, reduced complexity
    - _Requirements: 4.1_

  - [ ] 12.3 Write unit tests for non-SOAP WSDL rejection
    - Test WSDL with only HTTP bindings throws exception
    - Test WSDL with SOAP 1.1 bindings throws exception with "Only SOAP 1.2 is supported"
    - Test WSDL with no bindings throws exception
    - Test exception message clarity
    - Test valid SOAP 1.2 WSDLs still parse correctly
    - Follow Given-When-Then naming convention
    - _Requirements: 4.1_

  - [ ] 12.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Non-SOAP WSDL Rejection
    - **IMPORTANT**: Re-run the SAME test from task 10 - do NOT write a new test
    - Run bug condition exploration test from step 10
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 4.1_

  - [ ] 12.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Valid SOAP Parsing Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 11 - do NOT write new tests
    - Run preservation property tests from step 11
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 13. Write bug condition exploration test for missing top-level XSD elements
  - **Property 1: Bug Condition** - Missing Top-Level XSD Elements
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate parser misses top-level elements
  - **Scoped PBT Approach**: Load document-literal-soap12.wsdl with top-level xsd:element declarations
  - Test that WsdlParser only captures named complexType definitions, missing top-level elements
  - Verify xsdTypes map is empty or missing request/response elements
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "AddRequest element with inline complexType not captured in xsdTypes")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 5.1_

- [ ] 14. Write preservation property tests for XSD type extraction (BEFORE implementing fix)
  - **Property 2: Preservation** - Named ComplexType Extraction
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for WSDLs with named complexType definitions (should capture correctly)
  - Observe behavior on UNFIXED code for WSDLs with nested schema elements (should extract nested types)
  - Write property-based tests capturing observed type extraction behavior
  - Test with 10+ WSDL files with various type definition patterns
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline extraction to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 5.1, 5.2_

- [ ] 15. Fix missing top-level XSD elements

  - [ ] 15.1 Update extractXsdTypes() to capture top-level elements
    - After processing named complexType definitions, process top-level xsd:element declarations
    - Filter schema children for localName == "element"
    - For each element, check for inline complexType child
    - If inline complexType exists, extract fields and add to result map
    - If element has type= attribute, resolve reference and copy fields
    - _Bug_Condition: isBugCondition_Bug5(input) where WSDL contains top-level xsd:element with inline complexType_
    - _Expected_Behavior: Parser captures both complexType definitions and top-level element declarations_
    - _Preservation: Named complexType definitions continue to be captured correctly_
    - _Requirements: 5.1, 5.2_

  - [ ] 15.2 Write unit tests for top-level element extraction
    - Test top-level element with inline complexType
    - Test top-level element with type reference
    - Test mixed named complexTypes and top-level elements
    - Test edge cases: empty elements, missing type attributes
    - Follow Given-When-Then naming convention
    - _Requirements: 5.1_

  - [ ] 15.3 Write property-based tests for WSDL type extraction
    - Create 10-20 diverse WSDL test files with various type patterns
    - Test document-literal, RPC-style, nested types, inline types
    - Use @ParameterizedTest with @ValueSource
    - Verify all types captured correctly
    - _Requirements: 5.1_

  - [ ] 15.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Top-Level Element Extraction
    - **IMPORTANT**: Re-run the SAME test from task 13 - do NOT write a new test
    - Run bug condition exploration test from step 13
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 5.1_

  - [ ] 15.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Named ComplexType Extraction Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 14 - do NOT write new tests
    - Run preservation property tests from step 14
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 16. Write bug condition exploration test for retry logic not exercised
  - **Property 1: Bug Condition** - Retry Logic Not Exercised
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate retry loop is never executed
  - **Scoped PBT Approach**: Run SoapBoundedRetryAttemptsPropertyTest with code coverage analysis
  - Test that runStrategy is mocked to always return success(emptyList())
  - Verify agent never receives invalid mock to validate
  - Verify retry loop inside strategy has 0% code coverage
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "Retry loop code has 0% coverage, never executed")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 6.1_

- [ ] 17. Write preservation property tests for retry behavior (BEFORE implementing fix)
  - **Property 2: Preservation** - Test Completion Verification
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for all maxRetries values [0, 1, 2, 3] (agent should complete)
  - Observe behavior on UNFIXED code for runStrategy call count (should be called once)
  - Write property-based tests capturing observed completion behavior
  - Test with all maxRetries values
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline completion to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 6.1, 6.2_

- [ ] 18. Fix retry logic not exercised

  - [ ] 18.1 Update SoapBoundedRetryAttemptsPropertyTest to exercise retry logic
    - Remove mock of runStrategy that returns success
    - Create real agent with real validator
    - Mock AI service to return invalid mock on first call, valid mock on retry
    - This exercises the actual retry loop inside the strategy
    - Add verification of retry behavior: AI service called multiple times, validator called for each mock
    - _Bug_Condition: isBugCondition_Bug6(input) where test mocks runStrategy to always succeed_
    - _Expected_Behavior: Test uses fixture that returns invalid then valid mock, exercising retry logic_
    - _Preservation: Agent completes for all maxRetries values without hanging_
    - _Requirements: 6.1, 6.2_

  - [ ] 18.2 Write unit tests for retry logic coverage
    - Test retry logic is executed with invalid-then-valid mock fixture
    - Test AI service called multiple times for maxRetries > 0
    - Test validator called for each generated mock
    - Test retry loop code has >80% coverage
    - Follow Given-When-Then naming convention
    - _Requirements: 6.1_

  - [ ] 18.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Retry Logic Exercised
    - **IMPORTANT**: Re-run the SAME test from task 16 - do NOT write a new test
    - Run bug condition exploration test from step 16
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 6.1_

  - [ ] 18.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Test Completion Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 17 - do NOT write new tests
    - Run preservation property tests from step 17
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 19. Write bug condition exploration test for incomplete SnapStart priming
  - **Property 1: Bug Condition** - Incomplete SnapStart Priming
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate SOAP/GraphQL have higher cold start latency
  - **Scoped PBT Approach**: Measure cold start time for first SOAP request vs REST request
  - Test that GenerationPrimingHook only warms up REST/OpenAPI functionality
  - Verify first SOAP request has higher latency than first REST request
  - Verify first GraphQL request has higher latency than first REST request
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "First SOAP request: 500ms, First REST request: 50ms")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 11.1_

- [ ] 20. Write preservation property tests for SnapStart priming (BEFORE implementing fix)
  - **Property 2: Preservation** - REST/OpenAPI Priming
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for REST/OpenAPI priming (should warm up successfully)
  - Observe behavior on UNFIXED code for REST cold start time (should be low)
  - Write property-based tests capturing observed priming behavior
  - Test REST priming execution and latency
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline priming to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 11.1, 11.2_

- [ ] 21. Fix incomplete SnapStart priming

  - [ ] 21.1 Add SOAP/WSDL priming to GenerationPrimingHook
    - Inject wsdlParser, wsdlSchemaReducer, soapMockValidator dependencies
    - Create test WSDL specification in createTestWsdl() helper
    - Call wsdlParser.parse() with test WSDL
    - Call wsdlSchemaReducer.reduce() with parsed WSDL
    - Call soapMockValidator.validate() with test SOAP mock
    - Wrap in runCatching with warning log on failure
    - _Bug_Condition: isBugCondition_Bug11(input) where priming only warms up REST/OpenAPI_
    - _Expected_Behavior: Priming warms up SOAP/WSDL parsers and validators_
    - _Preservation: REST/OpenAPI priming continues to work correctly_
    - _Requirements: 11.1, 11.2_

  - [ ] 21.2 Add GraphQL priming to GenerationPrimingHook
    - Inject graphqlIntrospectionClient dependency
    - Create test GraphQL schema or use alternative priming approach
    - Call GraphQL components to warm up
    - Wrap in runCatching with warning log on failure
    - _Requirements: 11.1_

  - [ ] 21.3 Write unit tests for SOAP/GraphQL priming
    - Test SOAP/WSDL priming execution
    - Test GraphQL priming execution
    - Test priming error handling (failures don't break snapshot creation)
    - Test SnapStart environment detection
    - Follow Given-When-Then naming convention
    - _Requirements: 11.1_

  - [ ] 21.4 Write integration tests for SnapStart priming
    - Test Lambda initialization with SnapStart
    - Verify priming hook executes
    - Verify SOAP/GraphQL parsers warmed up
    - Measure first request latency for all protocols
    - Use LocalStack TestContainers for Lambda testing
    - _Requirements: 11.1_

  - [ ] 21.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Complete Protocol Priming
    - **IMPORTANT**: Re-run the SAME test from task 19 - do NOT write a new test
    - Run bug condition exploration test from step 19
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 11.1_

  - [ ] 21.6 Verify preservation tests still pass
    - **Property 2: Preservation** - REST Priming Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 20 - do NOT write new tests
    - Run preservation property tests from step 20
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 22. Write bug condition exploration test for insufficient test coverage
  - **Property 1: Bug Condition** - Insufficient Test Coverage
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate SOAP/GraphQL coverage below threshold
  - **Scoped PBT Approach**: Run ./gradlew koverHtmlReport and analyze coverage
  - Test that SOAP/WSDL module coverage < 80% (below enforced threshold)
  - Test that GraphQL module coverage < 80% (below enforced threshold)
  - Compare with REST/OpenAPI coverage (should be ≥ 85%)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "SOAP coverage: 72%, GraphQL coverage: 68%, REST coverage: 87%")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 12.1_

- [ ] 23. Write preservation property tests for test coverage (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Test Pass Rate
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for REST/OpenAPI test coverage (should be ≥ 85%)
  - Observe behavior on UNFIXED code for existing SOAP/GraphQL tests (should pass)
  - Write property-based tests capturing observed test pass rate
  - Run full test suite on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline test quality to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 12.1, 12.2_

- [ ] 24. Fix insufficient test coverage

  - [ ] 24.1 Add missing unit tests for WsdlParser
    - Test top-level element extraction with inline complexType
    - Test top-level element extraction with type reference
    - Test non-SOAP WSDL rejection (HTTP bindings only)
    - Test SOAP 1.1 rejection with clear error message
    - Test edge cases: empty schemas, missing namespaces, malformed XML
    - Follow Given-When-Then naming convention
    - Use MockK for mocking dependencies
    - Target 90%+ line coverage for WsdlParser
    - **SCOPE**: We ONLY support SOAP 1.2
    - _Bug_Condition: isBugCondition_Bug12(input) where SOAP/WSDL coverage < 80%_
    - _Expected_Behavior: Comprehensive test coverage ≥ 85% for SOAP/WSDL_
    - _Preservation: Existing tests continue to pass_
    - _Requirements: 12.1, 12.2_

  - [ ] 24.2 Add property-based tests for WSDL parsing
    - Create 10-20 diverse WSDL test files in src/test/resources/wsdl/
    - Files should cover: simple, complex, nested, large, document-literal, RPC-style
    - Use @ParameterizedTest with @ValueSource to test all files
    - Verify properties: all operations extracted, all types captured, size reduction
    - _Requirements: 12.1_

  - [ ] 24.3 Add missing unit tests for SoapMockValidator
    - Test URL path validation with correct and incorrect paths
    - Test URL path validation with namespaced paths
    - Test SOAP 1.2 error messages
    - Test all 7 validation rules with edge cases
    - Follow Given-When-Then naming convention
    - Target 90%+ line coverage for SoapMockValidator
    - _Requirements: 12.1_

  - [ ] 24.4 Add integration tests for SOAP generation
    - End-to-end test: WSDL URL → fetch → parse → reduce → generate → validate
    - Test with LocalStack S3 for specification storage
    - Test with mock Bedrock for AI generation
    - Verify generated mocks match WSDL operations
    - Use proper TestContainers lifecycle management
    - _Requirements: 12.1_

  - [ ] 24.5 Add missing unit tests for GraphQL components
    - Test GraphQLSchemaReducer with various schema complexities
    - Test GraphQLIntrospectionClient error handling
    - Test GraphQL specification parser edge cases
    - Follow Given-When-Then naming convention
    - Target 85%+ line coverage for GraphQL components
    - _Requirements: 12.1_

  - [ ] 24.6 Add property-based tests for GraphQL
    - Create 10-20 diverse GraphQL schema test files in src/test/resources/graphql/
    - Test schema reduction properties: all types reachable, size reduction
    - Test introspection result parsing
    - Use @ParameterizedTest with @ValueSource
    - _Requirements: 12.1_

  - [ ] 24.7 Add integration tests for GraphQL generation
    - End-to-end test: GraphQL schema → parse → reduce → generate → validate
    - Test with LocalStack S3 for specification storage
    - Test with mock Bedrock for AI generation
    - Use proper TestContainers lifecycle management
    - _Requirements: 12.1_

  - [ ] 24.8 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Comprehensive Test Coverage
    - **IMPORTANT**: Re-run the SAME test from task 22 - do NOT write a new test
    - Run bug condition exploration test from step 22
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 12.1_

  - [ ] 24.9 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Test Pass Rate Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 23 - do NOT write new tests
    - Run preservation property tests from step 23
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)

## Priority 3: Quality Fixes


- [ ] 25. Write bug condition exploration test for KDoc mismatch
  - **Property 1: Bug Condition** - KDoc Mismatch
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate KDoc doesn't match implementation
  - **Scoped PBT Approach**: Read SoapGenerationConfig KDoc and compare with AIGenerationConfiguration
  - Test that KDoc mentions auto-registration via List<MockValidatorInterface>
  - Verify actual implementation uses explicit composition in AIGenerationConfiguration.compositeMockValidator
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "KDoc says auto-registration, code uses explicit composition")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 8.1_

- [ ] 26. Write preservation property tests for configuration functionality (BEFORE implementing fix)
  - **Property 2: Preservation** - Configuration Functionality
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for SoapGenerationConfig in application context (should work correctly)
  - Observe behavior on UNFIXED code for explicit composition pattern (should function correctly)
  - Write property-based tests capturing observed configuration behavior
  - Test bean creation and wiring
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline configuration to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 8.1_

- [ ] 27. Fix KDoc mismatch

  - [ ] 27.1 Update SoapGenerationConfig KDoc to reflect actual implementation
    - Remove reference to auto-registration via List<MockValidatorInterface>
    - Document explicit composition in AIGenerationConfiguration.compositeMockValidator
    - Clarify that SoapMockValidator is manually composed, not auto-registered
    - _Bug_Condition: isBugCondition_Bug8(input) where KDoc states auto-registration_
    - _Expected_Behavior: KDoc correctly states explicit composition_
    - _Preservation: Configuration functionality continues to work correctly_
    - _Requirements: 8.1_

  - [ ] 27.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - KDoc Accuracy
    - **IMPORTANT**: Re-run the SAME test from task 25 - do NOT write a new test
    - Run bug condition exploration test from step 25
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 8.1_

  - [ ] 27.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Configuration Functionality Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 26 - do NOT write new tests
    - Run preservation property tests from step 26
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 28. Write bug condition exploration test for cryptic test error
  - **Property 1: Bug Condition** - Cryptic Test Error
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate cryptic error from domain invariant violation
  - **Scoped PBT Approach**: Create ParsedOperation with blank portTypeName
  - Test that RoundTripIntegrityPropertyTest.flushOperation() falls back to empty string
  - Verify WsdlOperation constructor throws IllegalArgumentException with cryptic message
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "IllegalArgumentException: portTypeName must not be blank" instead of clear test assertion)
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 9.1_

- [ ] 32. Write preservation property tests for valid operation processing (BEFORE implementing fix)
  - **Property 2: Preservation** - Valid Operation Processing
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for valid ParsedOperations (should flush correctly)
  - Observe behavior on UNFIXED code for WsdlOperation creation (should work correctly)
  - Write property-based tests capturing observed operation processing behavior
  - Test with 10+ valid ParsedOperations
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline processing to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 9.1_

- [ ] 33. Fix cryptic test error

  - [ ] 33.1 Add assertion before creating WsdlOperation in flushOperation()
    - Check if portTypeName is null or blank
    - Throw clear assertion failure with test context
    - Remove ?: "" fallback that violates domain invariants
    - Use require() with descriptive message including operation name and portTypeName value
    - _Bug_Condition: isBugCondition_Bug9(input) where ParsedOperation has blank portTypeName_
    - _Expected_Behavior: Clear test assertion failure before domain invariant violation_
    - _Preservation: Valid operations continue to flush correctly_
    - _Requirements: 9.1_

  - [ ] 33.2 Write unit tests for clear test assertion failures
    - Test flushOperation() with invalid portTypeName throws clear assertion
    - Test assertion message includes operation context
    - Test valid operations continue to work correctly
    - Follow Given-When-Then naming convention
    - _Requirements: 9.1_

  - [ ] 33.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Clear Test Assertion Failures
    - **IMPORTANT**: Re-run the SAME test from task 31 - do NOT write a new test
    - Run bug condition exploration test from step 31
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 9.1_

  - [ ] 33.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Valid Operation Processing Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 32 - do NOT write new tests
    - Run preservation property tests from step 32
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)


- [ ] 31. Write bug condition exploration test for weak content assertion
  - **Property 1: Bug Condition** - Weak Content Assertion
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate test passes with truncated content
  - **Scoped PBT Approach**: Mock WireMock server to return truncated WSDL (500 chars instead of 5000)
  - Test that WsdlContentFetcherTest only checks isNotBlank() and contains("<definitions")
  - Verify test passes despite 90% content truncation
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "Test passes with 500-char truncated WSDL instead of 5000-char full WSDL")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 10.1_

- [ ] 32. Write preservation property tests for successful fetch validation (BEFORE implementing fix)
  - **Property 2: Preservation** - Successful Fetch Validation
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for successful WSDL fetches (should return valid XML)
  - Observe behavior on UNFIXED code for error scenarios (should throw appropriate exceptions)
  - Write property-based tests capturing observed fetch validation behavior
  - Test with 10+ successful fetch scenarios
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline validation to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 10.1, 10.2_

- [ ] 33. Fix weak content assertion

  - [ ] 33.1 Replace weak assertions with exact equality check in WsdlContentFetcherTest
    - Remove assertTrue(result.isNotBlank())
    - Remove assertTrue(result.contains("<definitions"))
    - Add assertEquals(expectedWsdl, result) for exact content verification
    - Load expected WSDL from test resources
    - Compare fetched content against expected content byte-for-byte
    - _Bug_Condition: isBugCondition_Bug10(input) where test only checks isNotBlank() and marker string_
    - _Expected_Behavior: Test uses assertEquals for exact content equality_
    - _Preservation: Successful fetch validation continues to work_
    - _Requirements: 10.1, 10.2_

  - [ ] 33.2 Write unit tests for exact content validation
    - Test fetcher with exact content equality assertion
    - Test fetcher with truncated content fails validation
    - Test fetcher with rewritten content fails validation
    - Follow Given-When-Then naming convention
    - _Requirements: 10.1_

  - [ ] 33.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Exact Content Validation
    - **IMPORTANT**: Re-run the SAME test from task 31 - do NOT write a new test
    - Run bug condition exploration test from step 31
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 10.1_

  - [ ] 33.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Successful Fetch Validation Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 32 - do NOT write new tests
    - Run preservation property tests from step 32
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)

## Final Verification

- [ ] 34. Checkpoint - Ensure all tests pass
  - Run full test suite: ./gradlew test
  - Verify all bug condition exploration tests pass (confirms all bugs fixed)
  - Verify all preservation tests pass (confirms no regressions)
  - Verify all unit tests pass
  - Verify all property-based tests pass
  - Verify all integration tests pass
  - Ask the user if questions arise

- [ ] 38. Verify test coverage and quality
  - Run ./gradlew koverHtmlReport and verify 80%+ coverage for new code (enforced threshold; aim for 90%+ as a goal)
  - Run ./gradlew koverVerify to enforce coverage threshold
  - Review test quality: Given-When-Then naming, proper assertions, edge case coverage
  - Verify SOAP/WSDL module coverage ≥ 85%
  - Verify GraphQL module coverage ≥ 85%
  - Verify all 12 bugs have comprehensive test coverage
