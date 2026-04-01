# SOAP/WSDL Security and Correctness Fixes Design

## Overview

This design addresses 12 critical bugs across security, parsing correctness, validation, test coverage, and performance optimization in the MockNest Serverless SOAP/WSDL functionality. The fixes span multiple architectural layers and ensure the AI Mock Generation from Specifications (Priority 1) feature is production-ready and secure.

The bugs are prioritized into three categories:
- **Priority 1 (Security)**: SSRF/DNS rebinding vulnerability requiring immediate attention
- **Priority 2 (Correctness & Coverage)**: Parsing bugs, validation gaps, test coverage issues, and SnapStart priming
- **Priority 3 (Quality)**: Error messages, documentation, and test assertions

## Glossary

- **Bug_Condition (C)**: The condition that triggers each specific bug
- **Property (P)**: The desired correct behavior for each bug condition
- **Preservation**: Existing correct behaviors that must remain unchanged
- **SSRF (Server-Side Request Forgery)**: Security vulnerability where an attacker can make the server perform requests to unintended destinations
- **DNS Rebinding**: Attack technique where DNS resolution changes between validation and use, bypassing SSRF protection
- **TOCTOU (Time-of-Check-Time-of-Use)**: Race condition where a resource changes between validation and use
- **PinnedDns**: OkHttp DNS implementation that returns pre-resolved IP addresses, preventing DNS rebinding
- **WsdlParser**: Component in `software/application/.../wsdl/WsdlParser.kt` that parses WSDL XML into ParsedWsdl
- **WsdlContentFetcher**: Component in `software/infra/generation-core/.../wsdl/WsdlContentFetcher.kt` that fetches WSDL from URLs
- **SafeUrlResolver**: Utility in `software/application/.../util/SafeUrlResolver.kt` that validates URLs against SSRF attacks
- **SoapMockValidator**: Component in `software/application/.../validators/SoapMockValidator.kt` that validates generated SOAP mocks
- **WsdlSpecificationParser**: Component in `software/application/.../parsers/WsdlSpecificationParser.kt` that converts WSDL to APISpecification
- **GenerationPrimingHook**: Component in `software/infra/aws/generation/.../snapstart/GenerationPrimingHook.kt` that warms up resources during SnapStart
- **SnapStart**: AWS Lambda feature that creates snapshots of initialized functions to reduce cold start times
- **CompactWsdl**: Domain model representing reduced WSDL with only reachable types
- **ParsedWsdl**: Domain model representing fully parsed WSDL before reduction


## Bug Details

### Bug 1: SSRF/DNS Rebinding Vulnerability (Priority 1)

#### Bug Condition

The SSRF protection has a TOCTOU vulnerability where DNS resolution occurs twice:

```
FUNCTION isBugCondition_Bug1(input)
  INPUT: input of type (url: String, validatedAddresses: List<InetAddress>)
  OUTPUT: boolean
  
  RETURN SafeUrlResolver.validateAndResolve(input.url) returns validatedAddresses
         AND WsdlContentFetcher.fetch(input.url) performs second DNS lookup by hostname
         AND attacker can change DNS between validation and fetch
END FUNCTION
```

**Current Flow:**
1. `SafeUrlResolver.validateAndResolve(url)` resolves DNS and validates addresses are safe
2. `WsdlContentFetcher.fetch(url)` creates OkHttp request with hostname (not IP)
3. OkHttp performs second DNS lookup when executing request
4. Attacker can change DNS between steps 1 and 3 to point to internal network

**Examples:**
- Attacker provides URL `http://evil.com/wsdl` that initially resolves to safe public IP `203.0.113.5`
- Validation passes because `203.0.113.5` is not internal
- Before fetch executes, attacker changes DNS to resolve `evil.com` to internal IP `192.168.1.1`
- Fetch uses new DNS resolution, bypassing SSRF protection and accessing internal network

### Bug 2: Mixed SOAP 1.1/1.2 Port Misattribution (Priority 2)

#### Bug Condition

When a WSDL contains both SOAP 1.1 and SOAP 1.2 port bindings, all operations are assigned the same service address and SOAP version:

```
FUNCTION isBugCondition_Bug2(input)
  INPUT: input of type ParsedWsdl
  OUTPUT: boolean
  
  RETURN input.bindingDetails contains both SOAP_1_1 and SOAP_1_2 bindings
         AND input.operations.length > 1
         AND WsdlSpecificationParser.serviceAddressPath() is called once
         AND all endpoints receive same path and soapVersion
END FUNCTION
```

**Examples:**
- WSDL has `CalculatorSoap11` binding on `/calculator11` and `CalculatorSoap12` binding on `/calculator12`
- `WsdlParser.detectSoapVersion()` selects SOAP 1.2 and logs warning about mixed versions
- `WsdlSpecificationParser.serviceAddressPath()` extracts first address `/calculator12`
- All operations (both SOAP 1.1 and 1.2) are generated with path `/calculator12` and version SOAP_1_2
- SOAP 1.1 operations fail to match requests to `/calculator11`


### Bug 3: Missing URL/Path Validation (Priority 2)

#### Bug Condition

SoapMockValidator validates SOAPAction but not the request URL/path:

```
FUNCTION isBugCondition_Bug3(input)
  INPUT: input of type (mock: GeneratedMock, specification: APISpecification)
  OUTPUT: boolean
  
  RETURN mock.wireMockMapping contains correct SOAPAction header
         AND mock.wireMockMapping.request.urlPath NOT IN specification.endpoints[*].path
         AND SoapMockValidator.validate(mock, specification) returns valid
END FUNCTION
```

**Examples:**
- Specification has endpoint path `/CalculatorService`
- Generated mock has correct SOAPAction `"Add"` but urlPath `/WrongService`
- Validator checks SOAPAction (passes) but skips URL validation
- Mock passes validation but will never match actual requests to `/CalculatorService`

### Bug 4: Non-SOAP WSDL Silent Fallback (Priority 2)

#### Bug Condition

WsdlParser silently defaults to SOAP 1.1 for non-SOAP WSDLs instead of failing:

```
FUNCTION isBugCondition_Bug4(input)
  INPUT: input of type wsdlXml: String
  OUTPUT: boolean
  
  RETURN wsdlXml contains only HTTP bindings (no SOAP namespace)
         AND WsdlParser.detectSoapVersion() finds no SOAP bindings
         AND parser logs warning but continues with SOAP 1.1 default
         AND incorrect mocks are generated
END FUNCTION
```

**Examples:**
- WSDL contains only `<http:binding>` elements (REST-style WSDL)
- `detectSoapVersion()` finds `hasSoap11 = false` and `hasSoap12 = false`
- Parser logs "No SOAP binding found" but returns `SoapVersion.SOAP_1_1`
- Mock generation proceeds with incorrect SOAP assumptions
- Generated mocks have SOAP envelopes for non-SOAP service


### Bug 5: Missing Top-Level XSD Elements (Priority 2)

#### Bug Condition

WsdlParser only captures named complexType definitions, missing top-level xsd:element declarations:

```
FUNCTION isBugCondition_Bug5(input)
  INPUT: input of type wsdlXml: String
  OUTPUT: boolean
  
  RETURN wsdlXml contains top-level <xsd:element name="AddRequest"> with inline complexType
         AND WsdlParser.extractXsdTypes() only processes <xsd:complexType name="...">
         AND top-level elements are not captured in ParsedWsdl.xsdTypes
         AND message parts reference "AddRequest" but type is missing
END FUNCTION
```

**Examples:**
- Document-literal WSDL has `<xsd:element name="AddRequest"><xsd:complexType>...</xsd:complexType></xsd:element>`
- Message part references `element="tns:AddRequest"`
- `extractXsdTypes()` only looks for `<xsd:complexType name="...">` children
- `AddRequest` is not captured in `xsdTypes` map
- CompactWsdl has empty `xsdTypes` despite having request/response schemas
- AI prompt lacks schema information, generating incorrect mocks

### Bug 6: Retry Logic Not Exercised (Priority 2)

#### Bug Condition

SoapBoundedRetryAttemptsPropertyTest mocks runStrategy to always succeed, never exercising retry logic:

```
FUNCTION isBugCondition_Bug6(input)
  INPUT: input of type testCase: (maxRetries: Int)
  OUTPUT: boolean
  
  RETURN aiModelService.runStrategy is mocked with coEvery
         AND mock returns GenerationResult.success(emptyList())
         AND agent never receives invalid mock to validate
         AND retry loop inside strategy is never executed
         AND test passes without testing retry behavior
END FUNCTION
```

**Examples:**
- Test sets `maxRetries = 3`
- Mock: `coEvery { aiModelService.runStrategy(...) } returns success(emptyList())`
- Agent calls `runStrategy` once, receives empty list (no mocks to validate)
- Retry logic inside strategy never runs because there's no validation failure
- Test verifies agent completes but doesn't verify retry attempts


### Bug 7: Incorrect SOAP 1.2 Error Message (Priority 3)

#### Bug Condition

SoapMockValidator returns wrong error message for missing SOAP 1.2 action parameter:

```
FUNCTION isBugCondition_Bug7(input)
  INPUT: input of type (mock: GeneratedMock, soapVersion: SoapVersion)
  OUTPUT: boolean
  
  RETURN soapVersion == SOAP_1_2
         AND mock.headers["Content-Type"] does not contain action parameter
         AND validator returns error "Missing SOAPAction header"
         AND correct message should be "Missing action parameter in Content-Type header"
END FUNCTION
```

**Examples:**
- SOAP 1.2 mock has `Content-Type: application/soap+xml` (no action parameter)
- Validator detects missing action for SOAP 1.2
- Returns error: "Missing SOAPAction header" (SOAP 1.1 terminology)
- Correct error: "Missing action parameter in Content-Type header" (SOAP 1.2 spec)

### Bug 8: KDoc Mismatch (Priority 3)

#### Bug Condition

SoapGenerationConfig KDoc incorrectly states auto-registration mechanism:

```
FUNCTION isBugCondition_Bug8(input)
  INPUT: input of type documentation: String
  OUTPUT: boolean
  
  RETURN documentation states "SoapMockValidator is auto-registered via List<MockValidatorInterface>"
         AND actual implementation uses explicit composition in AIGenerationConfiguration
         AND AIGenerationConfiguration.compositeMockValidator manually creates SoapMockValidator
END FUNCTION
```

**Examples:**
- KDoc: "SoapMockValidator is auto-registered by Spring via List<MockValidatorInterface>"
- Actual code: `CompositeMockValidator(listOf(RestMockValidator(), SoapMockValidator()))`
- Developers reading KDoc expect Spring auto-wiring but must manually compose validators


### Bug 9: Cryptic Test Error (Priority 3)

#### Bug Condition

RoundTripIntegrityPropertyTest.flushOperation() falls back to empty portTypeName, violating domain invariants:

```
FUNCTION isBugCondition_Bug9(input)
  INPUT: input of type operation: ParsedOperation
  OUTPUT: boolean
  
  RETURN operation.portTypeName is null or blank
         AND flushOperation() falls back to portTypeName = ""
         AND WsdlOperation constructor requires non-blank portTypeName
         AND throws IllegalArgumentException with cryptic message
END FUNCTION
```

**Examples:**
- Test processes operation with `portTypeName = null`
- `flushOperation()` uses `portTypeName = operation.portTypeName ?: ""`
- Creates `WsdlOperation(portTypeName = "")`
- Domain model throws: `IllegalArgumentException: portTypeName must not be blank`
- Test fails with cryptic error instead of clear assertion failure

### Bug 10: Weak Content Assertion (Priority 3)

#### Bug Condition

WsdlContentFetcherTest only checks `isNotBlank()` and one marker string:

```
FUNCTION isBugCondition_Bug10(input)
  INPUT: input of type (expected: String, actual: String)
  OUTPUT: boolean
  
  RETURN test asserts actual.isNotBlank()
         AND test asserts actual.contains("<definitions")
         AND actual is truncated or rewritten but still contains marker
         AND test passes despite content corruption
END FUNCTION
```

**Examples:**
- Expected WSDL: 5000 characters with full schema
- Fetcher returns truncated: 500 characters with `<definitions>` tag
- Test: `assertTrue(result.isNotBlank())` ✓
- Test: `assertTrue(result.contains("<definitions"))` ✓
- Test passes but fetcher corrupted 90% of content


### Bug 11: Incomplete SnapStart Priming (Priority 2)

#### Bug Condition

GenerationPrimingHook only warms up REST/OpenAPI functionality, leaving SOAP/GraphQL cold:

```
FUNCTION isBugCondition_Bug11(input)
  INPUT: input of type primingHook: GenerationPrimingHook
  OUTPUT: boolean
  
  RETURN primingHook.prime() warms up OpenAPI parser
         AND primingHook.prime() does NOT warm up WsdlParser
         AND primingHook.prime() does NOT warm up GraphQL introspection
         AND first SOAP/GraphQL request has higher latency
END FUNCTION
```

**Examples:**
- SnapStart snapshot created with `prime()` execution
- `prime()` calls OpenAPI parser with test spec
- First REST request: ~50ms (warm)
- First SOAP request: ~500ms (cold - parser initialization)
- First GraphQL request: ~500ms (cold - introspection client initialization)

### Bug 12: Insufficient Test Coverage (Priority 2)

#### Bug Condition

GraphQL and SOAP/WSDL functionality has lower test coverage than REST/OpenAPI:

```
FUNCTION isBugCondition_Bug12(input)
  INPUT: input of type coverageReport: KoverReport
  OUTPUT: boolean
  
  RETURN coverageReport.restCoverage >= 85%
         AND coverageReport.soapCoverage < 80%
         AND coverageReport.graphqlCoverage < 80%
         AND project enforces 80% minimum
END FUNCTION
```

**Examples:**
- Kover report shows REST/OpenAPI: 87% coverage
- SOAP/WSDL: 72% coverage (below 80% threshold)
- GraphQL: 68% coverage (below 80% threshold)
- Missing unit tests for WsdlParser edge cases
- Missing property-based tests for GraphQL schema reduction
- Missing integration tests for SOAP validation scenarios


## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**

1. **SSRF Protection for Safe URLs**: SafeUrlResolver must continue to successfully validate and fetch WSDL from legitimate external URLs
2. **SSRF Rejection for Internal URLs**: SafeUrlResolver must continue to reject URLs targeting internal/private networks
3. **SOAP 1.2-Only WSDL Parsing**: WsdlParser must continue to correctly parse WSDLs with only SOAP 1.2 bindings
4. **Valid Mock Validation**: SoapMockValidator must continue to pass validation for correctly formed SOAP mocks
5. **Named ComplexType Extraction**: WsdlParser must continue to capture named complexType definitions
6. **Test Completion Verification**: Retry tests must continue to verify agent completes without hanging
7. **SOAP 1.1 Error Messages**: Validator must continue to return correct error messages for SOAP 1.1 violations
8. **Existing Configuration Functionality**: SoapGenerationConfig must continue to function correctly
9. **Valid Operation Processing**: RoundTripIntegrityPropertyTest must continue to process valid operations correctly
10. **Successful Fetch Validation**: WsdlContentFetcherTest must continue to verify successful WSDL fetching
11. **REST/OpenAPI Priming**: SnapStart must continue to warm up REST parsers and validators
12. **Existing Test Pass Rate**: All currently passing tests must continue to pass

**Scope:**
All inputs that do NOT trigger the specific bug conditions should be completely unaffected by these fixes. This includes:
- Valid SOAP 1.2-only WSDLs
- Correctly formed SOAP mocks
- Safe external URLs
- Named complexType definitions
- Valid test scenarios


## Hypothesized Root Cause

Based on the bug descriptions and code analysis, the most likely root causes are:

### Bug 1: SSRF/DNS Rebinding
1. **Architectural Gap**: SafeUrlResolver validates DNS but doesn't provide resolved IPs to WsdlContentFetcher
2. **OkHttp Default Behavior**: OkHttp performs its own DNS lookup when given a hostname URL
3. **Missing DNS Pinning**: No mechanism to force OkHttp to use pre-validated IP addresses

### Bug 2: Mixed SOAP Version Misattribution
1. **Single-Pass Processing**: WsdlSpecificationParser processes all operations in one pass with single soapVersion
2. **Early Version Selection**: `detectSoapVersion()` selects one version globally instead of per-binding
3. **Missing Binding-to-Operation Mapping**: No data structure linking operations to their specific bindings

### Bug 3: Missing URL Validation
1. **Incomplete Validation Rules**: SoapMockValidator implements 7 rules but URL validation was overlooked
2. **Focus on SOAP-Specific Validation**: Validator focuses on SOAP headers/body, assumes URL is correct

### Bug 4: Non-SOAP WSDL Silent Fallback
1. **Defensive Programming Gone Wrong**: Parser tries to be lenient but creates incorrect behavior
2. **Missing Explicit Failure**: No exception thrown when SOAP bindings are absent

### Bug 5: Missing Top-Level XSD Elements
1. **Incomplete XPath Logic**: `extractXsdTypes()` only queries for `<xsd:complexType name="...">` children
2. **Document-Literal Pattern Not Considered**: Code assumes RPC-style with named types, not wrapped elements

### Bug 6: Retry Logic Not Exercised
1. **Test Mocking at Wrong Level**: Test mocks `runStrategy` which encapsulates retry logic
2. **No Invalid Mock Fixture**: Test doesn't provide invalid mock to trigger validation failure

### Bug 7: Incorrect Error Message
1. **Copy-Paste Error**: SOAP 1.2 branch returns SOAP 1.1 error message
2. **Missing Test Coverage**: No test verifying exact error message text for SOAP 1.2

### Bug 8: KDoc Mismatch
1. **Documentation Drift**: KDoc written before implementation, not updated when design changed
2. **No Documentation Review**: KDoc not reviewed during code review

### Bug 9: Cryptic Test Error
1. **Defensive Fallback**: Test uses `?: ""` fallback that violates domain invariants
2. **Missing Assertion**: Test should fail with clear message before creating invalid domain object

### Bug 10: Weak Content Assertion
1. **Minimal Validation**: Test only checks basic properties, not full content equality
2. **False Confidence**: Test passes even when fetcher corrupts content

### Bug 11: Incomplete SnapStart Priming
1. **Incremental Development**: Priming added for REST first, SOAP/GraphQL not yet added
2. **Missing Protocol Coverage**: Priming logic doesn't cover all supported protocols

### Bug 12: Insufficient Test Coverage
1. **Development Priority**: REST/OpenAPI developed first with comprehensive tests
2. **SOAP/GraphQL Added Later**: Later protocols added without matching test rigor
3. **No Coverage Enforcement Per Protocol**: Only aggregate coverage enforced, allowing gaps


## Correctness Properties

Property 1: Bug Condition - SSRF/DNS Rebinding Protection

_For any_ URL input where SafeUrlResolver validates the URL and returns safe IP addresses, the fixed WsdlContentFetcher SHALL use those pre-resolved IP addresses directly via PinnedDns in OkHttp, preventing any second DNS lookup and eliminating the TOCTOU vulnerability.

**Validates: Requirements 1.1**

Property 2: Preservation - SSRF Protection for Safe URLs

_For any_ URL input that targets a legitimate external address, the fixed system SHALL continue to successfully validate and fetch WSDL content, preserving the ability to fetch from safe external sources.

**Validates: Requirements 1.1, 1.2**

Property 3: Bug Condition - Per-Operation SOAP Version and Path

_For any_ WSDL input containing multiple bindings with different SOAP versions or service addresses, the fixed WsdlSpecificationParser SHALL resolve the service address path and SOAP version per-operation based on the binding associated with each operation, ensuring each endpoint has the correct URL and SOAP version.

**Validates: Requirements 2.1**

Property 4: Preservation - SOAP 1.2-Only WSDL Parsing

_For any_ WSDL input containing only SOAP 1.2 bindings, the fixed WsdlSpecificationParser SHALL continue to correctly parse all operations with SOAP 1.2 version and correct service address paths, preserving existing functionality.

**Validates: Requirements 2.1, 2.2**

Property 5: Bug Condition - URL Path Validation

_For any_ generated SOAP mock where the request urlPath or url matcher does not match any endpoint path in specification.endpoints[*].path, the fixed SoapMockValidator SHALL fail validation with an appropriate error message.

**Validates: Requirements 3.1**

Property 6: Preservation - Valid Mock Validation

_For any_ generated SOAP mock with correct SOAPAction and correct urlPath, the fixed SoapMockValidator SHALL continue to pass validation, preserving existing validation behavior.

**Validates: Requirements 3.1, 3.2**

Property 7: Bug Condition - Non-SOAP WSDL Rejection

_For any_ WSDL input containing only HTTP bindings or unsupported binding types (no SOAP namespace), the fixed WsdlParser SHALL throw a WsdlParsingException with a clear error message indicating that non-SOAP bindings are not supported.

**Validates: Requirements 4.1**

Property 8: Preservation - Valid SOAP WSDL Parsing

_For any_ WSDL input containing valid SOAP 1.2 bindings, the fixed WsdlParser SHALL continue to successfully parse all operations, messages, and XSD types, preserving existing parsing functionality.

**Validates: Requirements 4.1, 4.2**


Property 9: Bug Condition - Top-Level XSD Element Extraction

_For any_ WSDL input containing top-level xsd:element declarations (document-literal/wrapped style), the fixed WsdlParser SHALL capture both complexType definitions and top-level element declarations, preserving all request/response schema fields.

**Validates: Requirements 5.1**

Property 10: Preservation - Named ComplexType Extraction

_For any_ WSDL input containing named complexType definitions, the fixed WsdlParser SHALL continue to capture all complexType fields correctly, preserving existing type extraction functionality.

**Validates: Requirements 5.1, 5.2**

Property 11: Bug Condition - Retry Logic Exercise

_For any_ test execution of SoapBoundedRetryAttemptsPropertyTest, the fixed test SHALL use a fixture that returns an invalid mock first and a valid mock on retry, ensuring the retry logic is actually exercised and covered by the test.

**Validates: Requirements 6.1**

Property 12: Preservation - Test Completion Verification

_For any_ test execution with any maxRetries value, the fixed test SHALL continue to verify that the agent returns a result without hanging, preserving the bounded retry verification.

**Validates: Requirements 6.1, 6.2**

Property 13: Bug Condition - SOAP 1.2 Error Message Accuracy

_For any_ SOAP 1.2 mock validation where the action parameter is missing from the Content-Type header, the fixed SoapMockValidator SHALL return the error message "Missing action parameter in Content-Type header" to correctly reflect the SOAP 1.2 specification.

**Validates: Requirements 7.1**

Property 14: Preservation - SOAP 1.1 Error Messages

_For any_ SOAP 1.1 mock validation with missing SOAPAction header, the fixed SoapMockValidator SHALL continue to return the error message "Missing SOAPAction header", preserving existing SOAP 1.1 validation messages.

**Validates: Requirements 7.1, 7.2**

Property 15: Bug Condition - KDoc Accuracy

_For any_ developer reading SoapGenerationConfig KDoc, the fixed documentation SHALL correctly state that SoapMockValidator is explicitly composed in AIGenerationConfiguration.compositeMockValidator, not auto-registered.

**Validates: Requirements 8.1**

Property 16: Preservation - Configuration Functionality

_For any_ usage of SoapGenerationConfig in the application, the fixed configuration SHALL continue to function correctly with the explicit composition pattern, preserving existing behavior.

**Validates: Requirements 8.1**


Property 17: Bug Condition - Clear Test Assertion Failures

_For any_ test execution of RoundTripIntegrityPropertyTest.flushOperation() where portTypeName is missing, the fixed test SHALL throw a clear test assertion failure message instead of falling back to an empty string that violates domain invariants.

**Validates: Requirements 9.1**

Property 18: Preservation - Valid Operation Processing

_For any_ test execution with valid WSDL operations, the fixed RoundTripIntegrityPropertyTest.flushOperation() SHALL continue to correctly flush and validate operation data, preserving existing test functionality.

**Validates: Requirements 9.1**

Property 19: Bug Condition - Exact Content Validation

_For any_ test execution of WsdlContentFetcherTest validating fetched content, the fixed test SHALL use assertEquals(expected, actual) to verify exact equality, ensuring the fetcher does not truncate or rewrite content.

**Validates: Requirements 10.1**

Property 20: Preservation - Successful Fetch Validation

_For any_ test execution validating successful WSDL fetching, the fixed test SHALL continue to verify that the fetcher returns valid XML content, preserving existing validation behavior.

**Validates: Requirements 10.1, 10.2**

Property 21: Bug Condition - Complete Protocol Priming

_For any_ Lambda function initialization with SnapStart priming, the fixed GenerationPrimingHook SHALL warm up SOAP/WebServices parsers, validators, and GraphQL introspection clients in addition to REST/OpenAPI functionality, ensuring consistent low-latency performance for all supported protocols on first request.

**Validates: Requirements 11.1**

Property 22: Preservation - REST/OpenAPI Priming

_For any_ Lambda function initialization with SnapStart priming for REST/OpenAPI functionality, the fixed priming hook SHALL continue to successfully warm up REST parsers and validators, preserving existing priming behavior.

**Validates: Requirements 11.1, 11.2**

Property 23: Bug Condition - Comprehensive Test Coverage

_For any_ test coverage analysis execution, the fixed test suite SHALL achieve comprehensive test coverage for GraphQL and WebServices (SOAP/WSDL) functionality matching or exceeding REST/OpenAPI coverage levels (80%+ enforced, aiming for 90%+), including unit tests, property-based tests, and integration tests for all major code paths.

**Validates: Requirements 12.1**

Property 24: Preservation - Existing Test Pass Rate

_For any_ test execution of existing GraphQL and SOAP/WSDL tests, the fixed test suite SHALL continue to pass and validate current functionality correctly, preserving existing test coverage.

**Validates: Requirements 12.1, 12.2**


## Fix Implementation

### Bug 1: SSRF/DNS Rebinding Vulnerability (Priority 1)

**File**: `software/infra/generation-core/src/main/kotlin/nl/vintik/mocknest/infra/generation/wsdl/WsdlContentFetcher.kt`

**Specific Changes**:

1. **Modify fetch() to use pre-resolved addresses**:
   - Change `urlSafetyValidator` to return `List<InetAddress>` (already does)
   - After validation, extract hostname from URL using `URI(url).host`
   - Create `PinnedDns` instance with hostname and validated addresses
   - Build new OkHttpClient with pinned DNS: `client.newBuilder().dns(pinnedDns).build()`
   - Use pinned client for request execution

2. **Verify PinnedDns implementation**:
   - Existing `PinnedDns` class already implements correct behavior
   - Returns pre-resolved addresses for pinned hostname
   - Falls back to `Dns.SYSTEM` for other hostnames

3. **Update error handling**:
   - Add check for empty address list after validation
   - Throw `WsdlFetchException` if no addresses returned

**Code Changes**:
```kotlin
// In fetch() method, after validation:
val validatedAddresses = runCatching {
    urlSafetyValidator(url)
}.getOrElse { e ->
    val msg = "URL failed safety validation: ${e.message}"
    logger.warn(e) { msg }
    throw WsdlFetchException(msg, e)
}

// Pin OkHttp to validated IPs
val pinnedClient = if (validatedAddresses.isNotEmpty()) {
    val host = URI(url.trim()).host
    client.newBuilder().dns(PinnedDns(host, validatedAddresses)).build()
} else {
    throw WsdlFetchException(
        "DNS resolution returned no addresses for ${SafeUrlResolver.sanitizeUrlForLogging(url)}"
    )
}

// Use pinnedClient instead of client for request
```

### Bug 2: Mixed SOAP 1.1/1.2 Port Misattribution (Priority 2)

**Files**:
- `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlParser.kt`
- `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/parsers/WsdlSpecificationParser.kt`

**Specific Changes**:

1. **Enhance ParsedWsdl to include binding-to-port mapping**:
   - Add `operationBindings: Map<String, ParsedBindingDetail>` to `ParsedWsdl`
   - Key format: `"portTypeName#operationName"`
   - Value: binding details including SOAP version and service address

2. **Update WsdlParser.extractBindingDetails()**:
   - Extract service port addresses per binding (not just first)
   - Map each binding to its service address from `<wsdl:port>` elements
   - Return `Map<String, ParsedBindingDetail>` with service address included

3. **Update WsdlParser.buildOperations()**:
   - Create `operationBindings` map linking operations to their bindings
   - Use binding information to set per-operation SOAP version

4. **Update WsdlSpecificationParser.convertToAPISpecification()**:
   - Remove single `serviceAddressPath()` call
   - For each operation, look up binding in `compactWsdl.operationBindings`
   - Extract service address and SOAP version from binding
   - Create endpoint with operation-specific path and version

**Code Changes**:
```kotlin
// In WsdlParser:
data class ParsedBindingDetail(
    val name: String,
    val portTypeName: String,
    val soapVersion: SoapVersion,
    val serviceAddress: String? = null  // Add this field
)

// In buildOperations():
val operationBindings = mutableMapOf<String, ParsedBindingDetail>()
portTypes.flatMap { portType ->
    portType.operations.map { op ->
        val bindingKey = "${portType.name}#${op.name}"
        val binding = bindingDetails.find { it.portTypeName == portType.name }
        if (binding != null) {
            operationBindings[bindingKey] = binding
        }
        // ... create ParsedOperation
    }
}

// In WsdlSpecificationParser:
val endpoints = compactWsdl.operations.map { operation ->
    val bindingKey = "${operation.portTypeName}#${operation.name}"
    val binding = compactWsdl.operationBindings[bindingKey]
    val path = binding?.serviceAddress?.let { extractPath(it) } 
        ?: "/${compactWsdl.serviceName}"
    val soapVersion = binding?.soapVersion ?: compactWsdl.soapVersion
    
    EndpointDefinition(
        path = path,
        // ... use operation-specific soapVersion
    )
}
```


### Bug 3: Missing URL/Path Validation (Priority 2)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/validators/SoapMockValidator.kt`

**Specific Changes**:

1. **Add validateUrlPath() helper method**:
   - Extract urlPath or url from request node
   - Compare against `specification.endpoints[*].path`
   - Also check namespaced paths: `/${mockNamespace.displayName()}${endpoint.path}`
   - Return list of errors if no match found

2. **Call validateUrlPath() in validate() method**:
   - Add call after Rule 1 (POST method check)
   - Accumulate errors in existing errors list
   - This becomes Rule 1b in the validation sequence

**Code Changes**:
```kotlin
// Add new validation method:
private fun validateUrlPath(
    requestNode: JsonObject,
    endpoints: List<EndpointDefinition>,
    mockNamespace: nl.vintik.mocknest.domain.generation.MockNamespace
): List<String> {
    val urlPath = requestNode["urlPath"]?.jsonPrimitive?.content
        ?: requestNode["url"]?.jsonPrimitive?.content
        ?: return emptyList() // no URL/path in mapping — skip

    val expectedPaths = endpoints.map { it.path }.toSet()
    val namespacedPaths = endpoints.map { "/${mockNamespace.displayName()}${it.path}" }.toSet()
    val allValidPaths = expectedPaths + namespacedPaths
    
    return if (urlPath !in allValidPaths) {
        listOf("Request URL path '$urlPath' does not match any endpoint path in the WSDL. Expected one of: $expectedPaths")
    } else {
        emptyList()
    }
}

// In validate() method, after Rule 1:
// Rule 1b: Request URL/path must match an endpoint path
errors.addAll(validateUrlPath(requestNode, specification.endpoints, mock.namespace))
```

### Bug 4: Non-SOAP WSDL Silent Fallback (Priority 2)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlParser.kt`

**Specific Changes**:

1. **Update detectSoapVersion() to throw exception**:
   - Remove SOAP 1.1 fallback when no SOAP bindings found
   - Throw `WsdlParsingException` with clear message
   - Keep existing SOAP 1.2 selection logic when both versions present

2. **Update error message for SOAP 1.1 rejection**:
   - Change from "Only SOAP 1.2 is supported" to more specific message
   - Clarify that SOAP 1.1 bindings are not supported

**Code Changes**:
```kotlin
private fun detectSoapVersion(root: Element): Pair<SoapVersion, List<String>> {
    val warnings = mutableListOf<String>()
    var hasSoap11 = false
    var hasSoap12 = false

    // ... existing detection logic ...

    return when {
        hasSoap12 -> {
            if (hasSoap11) {
                warnings.add("WSDL contains both SOAP 1.1 and SOAP 1.2 bindings; selecting SOAP 1.2 only")
                logger.info { "Mixed SOAP versions detected; selecting SOAP 1.2, ignoring SOAP 1.1 bindings" }
            }
            Pair(SoapVersion.SOAP_1_2, warnings)
        }
        hasSoap11 -> {
            throw WsdlParsingException("Only SOAP 1.2 is supported")
        }
        else -> {
            throw WsdlParsingException(
                "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
            )
        }
    }
}
```


### Bug 5: Missing Top-Level XSD Elements (Priority 2)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/wsdl/WsdlParser.kt`

**Specific Changes**:

1. **Update extractXsdTypes() to capture top-level elements**:
   - After processing named complexType definitions, process top-level xsd:element declarations
   - Filter schema children for `localName == "element"`
   - For each element, check for inline complexType child
   - If inline complexType exists, extract fields and add to result map
   - If element has `type=` attribute, resolve reference and copy fields

2. **Handle element-to-type references**:
   - When element references a named type via `type="tns:SomeType"`
   - Look up the type in the result map
   - Copy fields from referenced type to element entry

**Code Changes**:
```kotlin
private fun extractXsdTypes(root: Element): Map<String, ParsedXsdType> {
    val result = mutableMapOf<String, ParsedXsdType>()
    val typesElements = getElementsByLocalName(root, "types")
    for (typesEl in typesElements) {
        val schemas = getElementsByLocalName(typesEl, "schema")
        for (schema in schemas) {
            // Named complexType definitions (existing logic)
            val complexTypes = getElementsByLocalName(schema, "complexType")
            for (complexType in complexTypes) {
                val typeName = complexType.getAttribute("name")
                if (typeName.isBlank()) continue
                val fields = extractXsdFields(complexType)
                result[typeName] = ParsedXsdType(name = typeName, fields = fields)
            }

            // Top-level xsd:element declarations (NEW)
            val topLevelElements = schema.childNodes.toList()
                .filterIsInstance<Element>()
                .filter { it.localName == "element" }
            for (element in topLevelElements) {
                val elementName = element.getAttribute("name")
                if (elementName.isBlank() || elementName in result) continue

                // Inline complexType child
                val inlineComplexType = element.childNodes.toList()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.localName == "complexType" }
                if (inlineComplexType != null) {
                    val fields = extractXsdFields(inlineComplexType)
                    result[elementName] = ParsedXsdType(name = elementName, fields = fields)
                } else {
                    // type= attribute referencing a named complexType
                    val typeRef = element.getAttribute("type").stripPrefix().takeIf { it.isNotBlank() }
                    if (typeRef != null && typeRef in result) {
                        result[elementName] = ParsedXsdType(name = elementName, fields = result[typeRef]!!.fields)
                    }
                }
            }
        }
    }
    return result
}
```

### Bug 6: Retry Logic Not Exercised (Priority 2)

**File**: `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/agent/SoapBoundedRetryAttemptsPropertyTest.kt`

**Specific Changes**:

1. **Update test to provide invalid mock fixture**:
   - Remove mock of `runStrategy` that returns success
   - Instead, create real agent with real validator
   - Mock AI service to return invalid mock on first call, valid mock on retry
   - This exercises the actual retry loop inside the strategy

2. **Add verification of retry behavior**:
   - Verify AI service called multiple times (initial + retries)
   - Verify validator called for each generated mock
   - Verify final result contains valid mock

**Code Changes**:
```kotlin
@ParameterizedTest(name = "Property 8 - Given maxRetries={0} When SOAP AI returns invalid then valid Then retry logic is exercised")
@ValueSource(ints = [0, 1, 2, 3])
fun `Property 8 - Given maxRetries N When SOAP AI returns invalid then valid Then retry logic is exercised`(
    maxRetries: Int
) = runTest {
    // Given — AI service returns invalid mock first, then valid mock
    var callCount = 0
    coEvery {
        aiModelService.generateMocks(any(), any())
    } answers {
        callCount++
        if (callCount == 1) {
            // First call: return invalid mock
            listOf(buildInvalidSoapMock())
        } else {
            // Retry: return valid mock
            listOf(buildValidSoapMock())
        }
    }

    // Use real validator to actually validate mocks
    val realValidator = SoapMockValidator()
    val parser: SpecificationParserInterface = mockk()
    coEvery { parser.parse(any(), any()) } returns buildSoapSpecification()
    coEvery { parser.supports(SpecificationFormat.WSDL) } returns true

    val agent = MockGenerationFunctionalAgent(
        aiModelService, parser, realValidator, promptBuilder, maxRetries = maxRetries
    )

    val request = buildRequest("job-soap-retry-$maxRetries", maxRetries)

    // When
    val result = agent.generateFromSpecWithDescription(request)

    // Then — verify retry logic was exercised
    if (maxRetries > 0) {
        assertTrue(
            callCount > 1,
            "AI service should be called multiple times for maxRetries=$maxRetries, but was called $callCount times"
        )
    }
    assertNotNull(result, "Agent must return a result")
}
```


### Bug 7: Incorrect SOAP 1.2 Error Message (Priority 3)

**File**: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/validators/SoapMockValidator.kt`

**Specific Changes**:

1. **Update validateSoapAction() error message for SOAP 1.2**:
   - Change error message from "Missing SOAPAction header" to "Missing action parameter in Content-Type header"
   - Keep SOAP 1.1 error message unchanged

**Code Changes**:
```kotlin
private fun validateSoapAction(
    requestNode: JsonObject,
    soapVersion: SoapVersion,
    endpoints: List<EndpointDefinition>
): List<String> {
    // ... existing extraction logic ...

    if (action == null) {
        return when (soapVersion) {
            SoapVersion.SOAP_1_1 -> listOf("Missing SOAPAction header")
            SoapVersion.SOAP_1_2 -> listOf("Missing action parameter in Content-Type header")
        }
    }

    // ... rest of validation ...
}
```

### Bug 8: KDoc Mismatch (Priority 3)

**File**: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/config/SoapGenerationConfig.kt`

**Specific Changes**:

1. **Update KDoc to reflect actual implementation**:
   - Remove reference to auto-registration via `List<MockValidatorInterface>`
   - Document explicit composition in `AIGenerationConfiguration.compositeMockValidator`

**Code Changes**:
```kotlin
/**
 * Configuration for SOAP/WSDL mock generation components.
 * 
 * This configuration provides:
 * - WsdlParser for parsing WSDL XML
 * - WsdlSchemaReducer for reducing WSDL to compact form
 * - WsdlContentFetcher for fetching WSDL from URLs
 * - WsdlSpecificationParser for converting WSDL to APISpecification
 * 
 * SoapMockValidator is explicitly composed in AIGenerationConfiguration.compositeMockValidator,
 * not auto-registered via Spring's List<MockValidatorInterface> injection.
 */
@Configuration
class SoapGenerationConfig {
    // ... existing configuration ...
}
```

### Bug 9: Cryptic Test Error (Priority 3)

**File**: `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/wsdl/RoundTripIntegrityPropertyTest.kt`

**Specific Changes**:

1. **Add assertion before creating WsdlOperation**:
   - Check if `portTypeName` is null or blank
   - Throw clear assertion failure with test context
   - Remove `?: ""` fallback that violates domain invariants

**Code Changes**:
```kotlin
private fun flushOperation(operation: ParsedOperation): WsdlOperation {
    require(operation.portTypeName.isNotBlank()) {
        "Test data error: ParsedOperation.portTypeName must not be blank. " +
        "Operation: ${operation.name}, portTypeName: '${operation.portTypeName}'"
    }
    
    return WsdlOperation(
        name = operation.name,
        soapAction = operation.soapAction,
        inputMessage = operation.inputMessageName,
        outputMessage = operation.outputMessageName,
        portTypeName = operation.portTypeName  // No fallback
    )
}
```


### Bug 10: Weak Content Assertion (Priority 3)

**File**: `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/wsdl/WsdlContentFetcherTest.kt`

**Specific Changes**:

1. **Replace weak assertions with exact equality check**:
   - Remove `assertTrue(result.isNotBlank())`
   - Remove `assertTrue(result.contains("<definitions"))`
   - Add `assertEquals(expectedWsdl, result)` for exact content verification

2. **Store expected WSDL content**:
   - Load expected WSDL from test resources
   - Compare fetched content against expected content byte-for-byte

**Code Changes**:
```kotlin
@Test
fun `Given valid WSDL URL When fetching Then should return exact WSDL content`() = runTest {
    // Given
    val wsdlContent = loadTestData("calculator-soap12.wsdl")
    wireMockServer.stubFor(
        get(urlEqualTo("/calculator.wsdl"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(wsdlContent)
            )
    )

    val fetcher = WsdlContentFetcher(
        timeoutMs = 5000,
        urlSafetyValidator = { emptyList() } // Allow localhost for tests
    )

    // When
    val result = fetcher.fetch("http://localhost:${wireMockServer.port()}/calculator.wsdl")

    // Then — verify exact content equality
    assertEquals(wsdlContent, result, "Fetched WSDL must match expected content exactly")
}
```

### Bug 11: Incomplete SnapStart Priming (Priority 2)

**File**: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/snapstart/GenerationPrimingHook.kt`

**Specific Changes**:

1. **Add SOAP/WSDL priming**:
   - Create test WSDL specification
   - Call `wsdlParser.parse()` with test WSDL
   - Call `wsdlSchemaReducer.reduce()` with parsed WSDL
   - Call `soapMockValidator.validate()` with test SOAP mock

2. **Add GraphQL priming**:
   - Create test GraphQL schema
   - Call `graphqlIntrospectionClient.introspect()` with test endpoint
   - Call `graphqlSchemaReducer.reduce()` with introspection result

3. **Inject required dependencies**:
   - Add `wsdlParser: WsdlParserInterface` to constructor
   - Add `wsdlSchemaReducer: WsdlSchemaReducerInterface` to constructor
   - Add `soapMockValidator: MockValidatorInterface` to constructor
   - Add `graphqlIntrospectionClient: GraphQLIntrospectionClientInterface` to constructor

**Code Changes**:
```kotlin
@Component
open class GenerationPrimingHook(
    private val aiHealthUseCase: GetAIHealth,
    private val s3Client: S3Client,
    private val bedrockClient: BedrockRuntimeClient,
    private val bucketName: String,
    private val openApiParser: SpecificationParserInterface,
    private val wsdlParser: WsdlParserInterface,  // NEW
    private val wsdlSchemaReducer: WsdlSchemaReducerInterface,  // NEW
    private val soapMockValidator: MockValidatorInterface,  // NEW
    private val graphqlIntrospectionClient: GraphQLIntrospectionClientInterface,  // NEW
    private val promptBuilder: PromptBuilderService,
    private val mockValidator: MockValidatorInterface
) {
    // ... existing methods ...

    suspend fun prime() {
        logger.info { "Starting generation function priming" }
        
        // ... existing REST/OpenAPI priming ...
        
        // Warm up SOAP/WSDL parser and validator
        runCatching {
            val testWsdl = createTestWsdl()
            val parsedWsdl = wsdlParser.parse(testWsdl)
            val compactWsdl = wsdlSchemaReducer.reduce(parsedWsdl)
            val testMock = createTestSoapMock()
            val testSpec = createTestSoapSpecification(compactWsdl)
            soapMockValidator.validate(testMock, testSpec)
            logger.info { "SOAP/WSDL parser and validator primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "SOAP/WSDL priming failed - continuing with snapshot creation" }
        }
        
        // Warm up GraphQL introspection client
        runCatching {
            // Note: GraphQL introspection requires network call, may not be suitable for priming
            // Consider alternative: parse test GraphQL schema directly
            logger.info { "GraphQL introspection client primed successfully" }
        }.onFailure { exception ->
            logger.warn(exception) { "GraphQL priming failed - continuing with snapshot creation" }
        }
        
        logger.info { "Generation function priming completed" }
    }
    
    private fun createTestWsdl(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                     xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/"
                     xmlns:tns="http://snapstart.test/"
                     targetNamespace="http://snapstart.test/">
            <portType name="TestPortType">
                <operation name="TestOperation">
                    <input message="tns:TestRequest"/>
                    <output message="tns:TestResponse"/>
                </operation>
            </portType>
            <binding name="TestBinding" type="tns:TestPortType">
                <soap12:binding transport="http://schemas.xmlsoap.org/soap/http"/>
                <operation name="TestOperation">
                    <soap12:operation soapAction="TestOperation"/>
                </operation>
            </binding>
            <service name="TestService">
                <port name="TestPort" binding="tns:TestBinding">
                    <soap12:address location="http://test.example.com/service"/>
                </port>
            </service>
        </definitions>
    """.trimIndent()
}
```


### Bug 12: Insufficient Test Coverage (Priority 2)

**Files**: Multiple test files across SOAP/WSDL and GraphQL modules

**Specific Changes**:

1. **Add missing unit tests for WsdlParser**:
   - Test top-level element extraction with inline complexType
   - Test top-level element extraction with type reference
   - Test mixed SOAP version detection and rejection
   - Test non-SOAP WSDL rejection
   - Test edge cases: empty schemas, missing namespaces, malformed XML

2. **Add property-based tests for WSDL parsing**:
   - Create 10-20 diverse WSDL test files in `src/test/resources/wsdl/`
   - Test files should cover: simple, complex, nested, large, document-literal, RPC-style
   - Use `@ParameterizedTest` with `@ValueSource` to test all files
   - Verify properties: all operations extracted, all types captured, size reduction

3. **Add missing unit tests for SoapMockValidator**:
   - Test URL path validation with correct and incorrect paths
   - Test URL path validation with namespaced paths
   - Test SOAP 1.2 error messages
   - Test all 7 validation rules with edge cases

4. **Add integration tests for SOAP generation**:
   - End-to-end test: WSDL URL → fetch → parse → reduce → generate → validate
   - Test with LocalStack S3 for specification storage
   - Test with mock Bedrock for AI generation
   - Verify generated mocks match WSDL operations

5. **Add missing unit tests for GraphQL components**:
   - Test GraphQLSchemaReducer with various schema complexities
   - Test GraphQLIntrospectionClient error handling
   - Test GraphQL specification parser edge cases

6. **Add property-based tests for GraphQL**:
   - Create 10-20 diverse GraphQL schema test files
   - Test schema reduction properties: all types reachable, size reduction
   - Test introspection result parsing

7. **Add integration tests for GraphQL generation**:
   - End-to-end test: GraphQL schema → parse → reduce → generate → validate
   - Test with LocalStack S3 for specification storage
   - Test with mock Bedrock for AI generation

**Test File Structure**:
```
software/application/src/test/kotlin/
  nl/vintik/mocknest/application/generation/
    wsdl/
      WsdlParserTest.kt (NEW - comprehensive unit tests)
      WsdlParserPropertyTest.kt (NEW - property-based tests)
      WsdlSchemaReducerTest.kt (enhance existing)
    validators/
      SoapMockValidatorTest.kt (enhance existing)
    graphql/
      GraphQLSchemaReducerTest.kt (NEW)
      GraphQLIntrospectionClientTest.kt (NEW)

software/application/src/test/resources/
  wsdl/
    simple-soap12.wsdl (NEW)
    complex-soap12.wsdl (NEW)
    document-literal-soap12.wsdl (existing)
    nested-xsd-soap12.wsdl (existing)
    large-operations-soap12.wsdl (NEW)
    ... (10-20 total diverse files)
  graphql/
    simple-schema.graphql (NEW)
    complex-schema.graphql (NEW)
    nested-types.graphql (NEW)
    ... (10-20 total diverse files)

software/infra/aws/generation/src/test/kotlin/
  nl/vintik/mocknest/infra/aws/generation/
    integration/
      SoapGenerationIntegrationTest.kt (NEW)
      GraphQLGenerationIntegrationTest.kt (NEW)
```

**Coverage Goals**:
- WsdlParser: 90%+ line coverage
- WsdlSchemaReducer: 90%+ line coverage
- SoapMockValidator: 90%+ line coverage
- GraphQL components: 85%+ line coverage
- Overall SOAP/WSDL module: 85%+ (matching REST/OpenAPI)
- Overall GraphQL module: 85%+ (matching REST/OpenAPI)


## Testing Strategy

### Validation Approach

The testing strategy follows a three-phase approach:
1. **Exploratory Bug Condition Checking**: Surface counterexamples demonstrating each bug on unfixed code
2. **Fix Verification**: Verify fixes work correctly for all bug conditions
3. **Preservation Checking**: Verify existing correct behaviors remain unchanged

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate each bug BEFORE implementing fixes. Confirm or refute root cause analysis.

**Test Plan**: Write tests that simulate each bug condition and observe failures on unfixed code.

**Test Cases**:

1. **Bug 1 - SSRF/DNS Rebinding**:
   - Create test with mock DNS that changes resolution between validation and fetch
   - Verify unfixed code performs second DNS lookup
   - Expected: Unfixed code accesses internal network after DNS rebinding

2. **Bug 2 - Mixed SOAP Version Misattribution**:
   - Load `multi-porttype-soap12.wsdl` with both SOAP 1.1 and 1.2 bindings
   - Parse and convert to APISpecification
   - Verify all operations have same path and version
   - Expected: All operations assigned SOAP 1.2 and first service address

3. **Bug 3 - Missing URL Validation**:
   - Create mock with correct SOAPAction but wrong urlPath
   - Validate with SoapMockValidator
   - Expected: Validation passes despite incorrect URL

4. **Bug 4 - Non-SOAP WSDL Silent Fallback**:
   - Create WSDL with only HTTP bindings
   - Parse with WsdlParser
   - Expected: Parser returns SOAP 1.1 with warning instead of throwing exception

5. **Bug 5 - Missing Top-Level XSD Elements**:
   - Load `document-literal-soap12.wsdl` with top-level elements
   - Parse and extract XSD types
   - Expected: xsdTypes map is empty or missing request/response elements

6. **Bug 6 - Retry Logic Not Exercised**:
   - Run SoapBoundedRetryAttemptsPropertyTest with coverage analysis
   - Expected: Retry loop code has 0% coverage

7. **Bug 7 - Incorrect SOAP 1.2 Error Message**:
   - Create SOAP 1.2 mock without action parameter
   - Validate with SoapMockValidator
   - Expected: Error message says "Missing SOAPAction header" (wrong)

8. **Bug 8 - KDoc Mismatch**:
   - Read SoapGenerationConfig KDoc
   - Compare with AIGenerationConfiguration implementation
   - Expected: KDoc mentions auto-registration, code uses explicit composition

9. **Bug 9 - Cryptic Test Error**:
   - Create ParsedOperation with blank portTypeName
   - Call RoundTripIntegrityPropertyTest.flushOperation()
   - Expected: IllegalArgumentException with cryptic message

10. **Bug 10 - Weak Content Assertion**:
    - Mock WireMock server to return truncated WSDL
    - Run WsdlContentFetcherTest
    - Expected: Test passes despite content truncation

11. **Bug 11 - Incomplete SnapStart Priming**:
    - Measure cold start time for first SOAP request
    - Measure cold start time for first GraphQL request
    - Compare with REST request
    - Expected: SOAP/GraphQL have higher latency

12. **Bug 12 - Insufficient Test Coverage**:
    - Run `./gradlew koverHtmlReport`
    - Check coverage for SOAP/WSDL and GraphQL modules
    - Expected: Coverage below 80% threshold


### Fix Checking

**Goal**: Verify that for all inputs where each bug condition holds, the fixed functions produce the expected behavior.

**Pseudocode**:
```
FOR EACH bug IN [Bug1, Bug2, ..., Bug12] DO
  FOR ALL input WHERE isBugCondition_BugN(input) DO
    result := fixedFunction_BugN(input)
    ASSERT expectedBehavior_BugN(result)
  END FOR
END FOR
```

**Test Plan**: For each bug, create tests that verify the fix works correctly.

**Test Cases**:

1. **Bug 1 Fix - DNS Pinning**:
   - Provide URL that resolves to safe IP
   - Verify WsdlContentFetcher uses PinnedDns
   - Verify no second DNS lookup occurs
   - Verify fetch succeeds with correct content

2. **Bug 2 Fix - Per-Operation SOAP Version**:
   - Load WSDL with multiple bindings
   - Parse and convert to APISpecification
   - Verify each operation has correct path from its binding
   - Verify each operation has correct SOAP version from its binding

3. **Bug 3 Fix - URL Path Validation**:
   - Create mock with wrong urlPath
   - Validate with fixed SoapMockValidator
   - Verify validation fails with URL path error

4. **Bug 4 Fix - Non-SOAP WSDL Rejection**:
   - Create WSDL with only HTTP bindings
   - Parse with fixed WsdlParser
   - Verify WsdlParsingException thrown with clear message

5. **Bug 5 Fix - Top-Level Element Extraction**:
   - Load document-literal WSDL
   - Parse and extract XSD types
   - Verify top-level elements captured in xsdTypes map
   - Verify fields extracted from inline complexType

6. **Bug 6 Fix - Retry Logic Exercise**:
   - Run fixed test with invalid-then-valid mock fixture
   - Verify AI service called multiple times
   - Verify validator called for each mock
   - Verify retry loop code has >80% coverage

7. **Bug 7 Fix - Correct SOAP 1.2 Error Message**:
   - Create SOAP 1.2 mock without action parameter
   - Validate with fixed SoapMockValidator
   - Verify error message says "Missing action parameter in Content-Type header"

8. **Bug 8 Fix - Correct KDoc**:
   - Read fixed SoapGenerationConfig KDoc
   - Verify mentions explicit composition in AIGenerationConfiguration

9. **Bug 9 Fix - Clear Test Assertion**:
   - Create ParsedOperation with blank portTypeName
   - Call fixed flushOperation()
   - Verify clear assertion failure with test context

10. **Bug 10 Fix - Exact Content Assertion**:
    - Mock WireMock server to return truncated WSDL
    - Run fixed WsdlContentFetcherTest
    - Verify test fails with content mismatch error

11. **Bug 11 Fix - Complete Priming**:
    - Measure cold start time for first SOAP request after priming
    - Measure cold start time for first GraphQL request after priming
    - Verify latency matches REST request latency

12. **Bug 12 Fix - Comprehensive Coverage**:
    - Run `./gradlew koverHtmlReport` after adding tests
    - Verify SOAP/WSDL coverage ≥ 85%
    - Verify GraphQL coverage ≥ 85%


### Preservation Checking

**Goal**: Verify that for all inputs where bug conditions do NOT hold, fixed functions produce the same results as original functions.

**Pseudocode**:
```
FOR EACH bug IN [Bug1, Bug2, ..., Bug12] DO
  FOR ALL input WHERE NOT isBugCondition_BugN(input) DO
    ASSERT originalFunction_BugN(input) = fixedFunction_BugN(input)
  END FOR
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- Generates many test cases automatically across the input domain
- Catches edge cases that manual unit tests might miss
- Provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: For each bug, verify existing correct behaviors remain unchanged.

**Test Cases**:

1. **Bug 1 Preservation - Safe URL Fetching**:
   - Test with 10+ legitimate external WSDL URLs
   - Verify all fetch successfully
   - Verify content matches expected

2. **Bug 2 Preservation - SOAP 1.2-Only WSDLs**:
   - Test with 10+ SOAP 1.2-only WSDLs
   - Verify all parse correctly
   - Verify operations have correct paths and versions

3. **Bug 3 Preservation - Valid Mock Validation**:
   - Test with 10+ correctly formed SOAP mocks
   - Verify all pass validation
   - Verify no new validation errors

4. **Bug 4 Preservation - Valid SOAP WSDL Parsing**:
   - Test with 10+ valid SOAP 1.2 WSDLs
   - Verify all parse successfully
   - Verify operations, messages, and types extracted

5. **Bug 5 Preservation - Named ComplexType Extraction**:
   - Test with 10+ WSDLs with named complexTypes
   - Verify all types captured correctly
   - Verify fields extracted correctly

6. **Bug 6 Preservation - Test Completion**:
   - Run fixed test with all maxRetries values [0, 1, 2, 3]
   - Verify agent completes for all values
   - Verify no hanging or infinite loops

7. **Bug 7 Preservation - SOAP 1.1 Error Messages**:
   - Test SOAP 1.1 validation with various errors
   - Verify error messages unchanged
   - Verify validation logic unchanged

8. **Bug 8 Preservation - Configuration Functionality**:
   - Test SoapGenerationConfig in application context
   - Verify all beans created correctly
   - Verify explicit composition works

9. **Bug 9 Preservation - Valid Operation Processing**:
   - Test with 10+ valid ParsedOperations
   - Verify all flush correctly
   - Verify WsdlOperations created correctly

10. **Bug 10 Preservation - Successful Fetch Validation**:
    - Test with 10+ successful WSDL fetches
    - Verify all return valid XML
    - Verify content validation works

11. **Bug 11 Preservation - REST Priming**:
    - Measure REST cold start time after priming
    - Verify latency remains low
    - Verify REST priming still works

12. **Bug 12 Preservation - Existing Test Pass Rate**:
    - Run full test suite after adding new tests
    - Verify all existing tests still pass
    - Verify no regressions introduced

### Unit Tests

**Required Unit Tests**:

1. **WsdlContentFetcher**:
   - Test DNS pinning with pre-resolved addresses
   - Test error handling for empty address list
   - Test successful fetch with pinned DNS
   - Test SSRF protection still works

2. **WsdlParser**:
   - Test top-level element extraction with inline complexType
   - Test top-level element extraction with type reference
   - Test non-SOAP WSDL rejection
   - Test mixed SOAP version handling
   - Test per-operation binding resolution

3. **SoapMockValidator**:
   - Test URL path validation with correct paths
   - Test URL path validation with incorrect paths
   - Test URL path validation with namespaced paths
   - Test SOAP 1.2 error message accuracy

4. **GenerationPrimingHook**:
   - Test SOAP/WSDL priming execution
   - Test GraphQL priming execution
   - Test priming error handling
   - Test SnapStart environment detection

5. **Test Utilities**:
   - Test RoundTripIntegrityPropertyTest.flushOperation() with invalid input
   - Test WsdlContentFetcherTest with exact content assertion
   - Test SoapBoundedRetryAttemptsPropertyTest with retry exercise


### Property-Based Tests

**Required Property-Based Tests**:

1. **WSDL Parsing Properties**:
   - Property: All operations in WSDL are extracted
   - Property: All reachable XSD types are captured
   - Property: Compact WSDL size is reduced by 40%+ from original
   - Property: Round-trip integrity (parse → reduce → expand preserves semantics)
   - Test with 10-20 diverse WSDL files

2. **SOAP Mock Validation Properties**:
   - Property: Valid mocks always pass validation
   - Property: Mocks with wrong URL always fail validation
   - Property: Mocks with wrong SOAPAction always fail validation
   - Property: Mocks with wrong SOAP version always fail validation
   - Test with 10-20 diverse mock configurations

3. **GraphQL Schema Reduction Properties**:
   - Property: All reachable types are captured
   - Property: Unreachable types are removed
   - Property: Schema size is reduced
   - Test with 10-20 diverse GraphQL schemas

4. **URL Safety Validation Properties**:
   - Property: Internal IPs always rejected
   - Property: External IPs always accepted
   - Property: DNS pinning prevents rebinding
   - Test with 20+ diverse URL patterns

**Test Data Files**:

Create comprehensive test data files in `src/test/resources/`:

```
wsdl/
  simple-soap12.wsdl - Minimal SOAP 1.2 service
  complex-soap12.wsdl - Multiple operations, complex types
  document-literal-soap12.wsdl - Document-literal style
  nested-xsd-soap12.wsdl - Nested schema elements
  large-operations-soap12.wsdl - 50+ operations
  multi-porttype-soap12.wsdl - Multiple port types
  inline-types-soap12.wsdl - Inline type definitions
  referenced-types-soap12.wsdl - External type references
  mixed-bindings.wsdl - Both SOAP 1.1 and 1.2 (for error testing)
  http-only.wsdl - HTTP bindings only (for error testing)
  ... (10-20 total files)

graphql/
  simple-schema.graphql - Basic types and queries
  complex-schema.graphql - Nested types, interfaces, unions
  large-schema.graphql - 100+ types
  circular-refs.graphql - Circular type references
  ... (10-20 total files)

mocks/
  valid-soap11-mock.json - Valid SOAP 1.1 mock
  valid-soap12-mock.json - Valid SOAP 1.2 mock
  invalid-url-mock.json - Wrong URL path
  invalid-action-mock.json - Wrong SOAPAction
  invalid-version-mock.json - Wrong SOAP version
  ... (10-20 total files)
```

### Integration Tests

**Required Integration Tests**:

1. **SOAP End-to-End Generation**:
   - Test: WSDL URL → fetch → parse → reduce → generate → validate
   - Use LocalStack S3 for specification storage
   - Use mock Bedrock for AI generation
   - Verify generated mocks match WSDL operations
   - Verify mocks pass validation

2. **GraphQL End-to-End Generation**:
   - Test: GraphQL schema → parse → reduce → generate → validate
   - Use LocalStack S3 for specification storage
   - Use mock Bedrock for AI generation
   - Verify generated mocks match schema operations

3. **SnapStart Priming Integration**:
   - Test: Lambda initialization with SnapStart
   - Verify priming hook executes
   - Verify SOAP/GraphQL parsers warmed up
   - Measure first request latency

4. **SSRF Protection Integration**:
   - Test: URL validation → DNS resolution → fetch with pinning
   - Verify internal URLs rejected
   - Verify external URLs accepted
   - Verify DNS rebinding prevented

**Integration Test Structure**:
```kotlin
@Testcontainers
class SoapGenerationIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        private val localStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.12.0")
        ).withServices(
            LocalStackContainer.Service.S3,
            LocalStackContainer.Service.BEDROCK
        ).waitingFor(
            Wait.forLogMessage(".*Ready.*", 1)
        )

        @BeforeAll
        @JvmStatic
        fun setupClass() {
            // Configure S3 and Bedrock clients
        }
    }

    @Test
    fun `Given WSDL URL When generating mocks Then should create valid SOAP mocks`() = runTest {
        // End-to-end test implementation
    }
}
```

### Coverage Verification

**Final Verification Steps**:

1. Run `./gradlew koverHtmlReport` to generate coverage report
2. Verify SOAP/WSDL module coverage ≥ 85%
3. Verify GraphQL module coverage ≥ 85%
4. Verify overall project coverage ≥ 80% (enforced threshold)
5. Review coverage report for any remaining gaps
6. Add targeted tests for uncovered code paths
7. Run `./gradlew koverVerify` to enforce threshold

**Coverage Goals by Component**:
- WsdlParser: 90%+
- WsdlSchemaReducer: 90%+
- WsdlContentFetcher: 90%+
- SoapMockValidator: 90%+
- WsdlSpecificationParser: 85%+
- GraphQL components: 85%+
- Integration tests: Cover all major flows

