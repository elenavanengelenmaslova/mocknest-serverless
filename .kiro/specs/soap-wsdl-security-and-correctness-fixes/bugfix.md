# Bugfix Requirements Document

## Introduction

This document specifies the requirements for fixing critical security vulnerabilities and correctness issues in the MockNest Serverless SOAP/WSDL functionality. The issues span security (SSRF/DNS rebinding), parsing correctness (mixed SOAP versions, missing schema elements), validation gaps, test coverage problems, and performance optimization (SnapStart priming).

These fixes are essential for the AI Mock Generation from Specifications (Priority 1) feature to be production-ready and secure. The bugs affect the WSDL parsing, validation, and content fetching components that are core to SOAP mock generation, as well as test coverage and cold-start performance for SOAP and GraphQL protocols.

## Bug Analysis

### Current Behavior (Defect)

#### 1. Security Issues (Priority 1)

1.1 WHEN SafeUrlResolver.validateAndResolve validates a URL and returns safe IP addresses THEN WsdlContentFetcher.fetch() performs a second DNS lookup by hostname, creating a TOCTOU (Time-of-Check-Time-of-Use) gap that allows DNS rebinding attacks to bypass SSRF protection

#### 2. SOAP Version Misattribution (Priority 2)

2.1 WHEN a WSDL file contains both SOAP 1.1 and SOAP 1.2 port bindings THEN WsdlSpecificationParser.serviceAddressPath() and compactWsdl.soapVersion are derived once and reused for all endpoints, causing all endpoints to be generated with the same URL and SOAP version regardless of their actual binding

#### 3. Missing URL Validation (Priority 2)

3.1 WHEN SoapMockValidator validates a generated mock with the correct SOAPAction header but an incorrect urlPath THEN the validation passes even though the mock will not match requests to the correct endpoint path

#### 4. Non-SOAP WSDL Silent Fallback (Priority 2)

4.1 WHEN WsdlParser encounters a WSDL with HTTP-only bindings or unsupported binding types THEN it silently defaults to SOAP 1.1 with only a warning log message, allowing incorrect mocks to be generated

#### 5. Missing Top-Level XSD Elements (Priority 2)

5.1 WHEN WsdlParser processes a WSDL with top-level xsd:element declarations (document-literal/wrapped style) THEN only complexType definitions are captured, causing all request/response schema fields to be lost and xsdTypes to be empty for most real-world WSDLs

#### 6. Retry Logic Not Exercised (Priority 2)

6.1 WHEN SoapBoundedRetryAttemptsPropertyTest runs THEN runStrategy is mocked to always return success(emptyList()), so the agent never has a mock to validate and never retries, meaning the retry logic is not actually tested

#### 7. Incorrect SOAP 1.2 Error Message (Priority 3)

7.1 WHEN SoapMockValidator detects a missing action parameter for SOAP 1.2 THEN the error message reads "Missing SOAPAction header" instead of correctly indicating the action parameter should be in the Content-Type header

#### 8. KDoc Mismatch (Priority 3)

8.1 WHEN reading SoapGenerationConfig KDoc THEN it states SoapMockValidator is auto-registered via List<MockValidatorInterface>, but the actual implementation explicitly composes it in AIGenerationConfiguration.compositeMockValidator

#### 9. Cryptic Test Error (Priority 3)

9.1 WHEN RoundTripIntegrityPropertyTest.flushOperation() falls back to portTypeName = "" THEN it violates WsdlOperation's non-blank invariant and throws a cryptic IllegalArgumentException instead of a clear test assertion failure

#### 10. Weak Content Assertion (Priority 3)

10.1 WHEN WsdlContentFetcherTest validates fetched content THEN it only checks isNotBlank() plus one marker string, allowing a fetcher that truncates or rewrites content to pass the test

#### 11. Incomplete SnapStart Priming (Priority 2)

11.1 WHEN the Lambda function initializes with SnapStart priming THEN only REST/OpenAPI functionality is warmed up, leaving SOAP/WebServices and GraphQL parsers and validators cold, causing higher latency on first SOAP/GraphQL generation requests

#### 12. Insufficient Test Coverage (Priority 2)

12.1 WHEN running test coverage analysis THEN GraphQL and WebServices (SOAP/WSDL) functionality has lower test coverage compared to REST/OpenAPI functionality, leaving potential bugs undetected and reducing confidence in these protocol implementations

### Expected Behavior (Correct)

#### 1. Security Issues (Priority 1)

1.1 WHEN SafeUrlResolver.validateAndResolve validates a URL and returns safe IP addresses THEN WsdlContentFetcher.fetch() SHALL use those resolved addresses directly via PinnedDns in the OkHttp client, preventing any second DNS lookup and closing the TOCTOU gap

#### 2. SOAP Version Misattribution (Priority 2)

2.1 WHEN a WSDL file contains both SOAP 1.1 and SOAP 1.2 port bindings THEN WsdlSpecificationParser SHALL resolve the service address path and SOAP version per-operation based on the binding associated with each operation, ensuring each endpoint has the correct URL and SOAP version

#### 3. Missing URL Validation (Priority 2)

3.1 WHEN SoapMockValidator validates a generated mock THEN it SHALL verify that the request urlPath or url matcher matches one of the endpoint paths in specification.endpoints[*].path, failing validation if the path does not match

#### 4. Non-SOAP WSDL Silent Fallback (Priority 2)

4.1 WHEN WsdlParser encounters a WSDL with HTTP-only bindings or unsupported binding types THEN it SHALL throw a WsdlParsingException with a clear error message indicating that non-SOAP bindings are not supported, preventing incorrect mock generation

#### 5. Missing Top-Level XSD Elements (Priority 2)

5.1 WHEN WsdlParser processes a WSDL with top-level xsd:element declarations THEN it SHALL capture both complexType definitions and top-level element declarations, preserving all request/response schema fields for document-literal/wrapped-style WSDLs

#### 6. Retry Logic Not Exercised (Priority 2)

6.1 WHEN SoapBoundedRetryAttemptsPropertyTest runs THEN it SHALL use a fixture that returns an invalid mock first and a valid mock on retry, ensuring the retry logic is actually exercised and covered by the test

#### 7. Incorrect SOAP 1.2 Error Message (Priority 3)

7.1 WHEN SoapMockValidator detects a missing action parameter for SOAP 1.2 THEN the error message SHALL read "Missing action parameter in Content-Type header" to correctly reflect the SOAP 1.2 specification

#### 8. KDoc Mismatch (Priority 3)

8.1 WHEN reading SoapGenerationConfig KDoc THEN it SHALL correctly state that SoapMockValidator is explicitly composed in AIGenerationConfiguration.compositeMockValidator, not auto-registered

#### 9. Cryptic Test Error (Priority 3)

9.1 WHEN RoundTripIntegrityPropertyTest.flushOperation() encounters a missing portTypeName THEN it SHALL throw a clear test assertion failure message instead of falling back to an empty string that violates domain invariants

#### 10. Weak Content Assertion (Priority 3)

10.1 WHEN WsdlContentFetcherTest validates fetched content THEN it SHALL use assertEquals(wsdlContent, result) to verify exact equality, ensuring the fetcher does not truncate or rewrite content

#### 11. Incomplete SnapStart Priming (Priority 2)

11.1 WHEN the Lambda function initializes with SnapStart priming THEN it SHALL warm up SOAP/WebServices parsers, validators, and GraphQL introspection clients in addition to REST/OpenAPI functionality, ensuring consistent low-latency performance for all supported protocols on first request

#### 12. Insufficient Test Coverage (Priority 2)

12.1 WHEN running test coverage analysis THEN GraphQL and WebServices (SOAP/WSDL) functionality SHALL have comprehensive test coverage matching or exceeding REST/OpenAPI coverage levels, including unit tests, property-based tests, and integration tests for all major code paths

### Unchanged Behavior (Regression Prevention)

#### 1. Security Issues (Priority 1)

1.1 WHEN SafeUrlResolver.validateAndResolve validates a URL that targets a safe external address THEN the system SHALL CONTINUE TO successfully fetch WSDL content from that URL

1.2 WHEN SafeUrlResolver.validateAndResolve detects a URL targeting an internal/private network address THEN the system SHALL CONTINUE TO reject the URL with a UrlResolutionException

#### 2. SOAP Version Misattribution (Priority 2)

2.1 WHEN a WSDL file contains only SOAP 1.2 bindings THEN WsdlSpecificationParser SHALL CONTINUE TO correctly parse all operations with SOAP 1.2 version and correct service address paths

2.2 WHEN WsdlSpecificationParser processes a valid WSDL THEN it SHALL CONTINUE TO return an APISpecification with all operations, schemas, and metadata correctly populated

#### 3. Missing URL Validation (Priority 2)

3.1 WHEN SoapMockValidator validates a mock with correct SOAPAction and correct urlPath THEN it SHALL CONTINUE TO pass validation

3.2 WHEN SoapMockValidator validates a mock with incorrect SOAPAction THEN it SHALL CONTINUE TO fail validation with appropriate error messages

#### 4. Non-SOAP WSDL Silent Fallback (Priority 2)

4.1 WHEN WsdlParser processes a valid SOAP 1.2 WSDL THEN it SHALL CONTINUE TO successfully parse all operations, messages, and XSD types

4.2 WHEN WsdlParser detects both SOAP 1.1 and SOAP 1.2 bindings THEN it SHALL CONTINUE TO select SOAP 1.2 and log a warning about mixed versions

#### 5. Missing Top-Level XSD Elements (Priority 2)

5.1 WHEN WsdlParser processes a WSDL with named complexType definitions THEN it SHALL CONTINUE TO capture all complexType fields correctly

5.2 WHEN WsdlParser processes a WSDL with nested schema elements THEN it SHALL CONTINUE TO extract all nested type definitions

#### 6. Retry Logic Not Exercised (Priority 2)

6.1 WHEN SoapBoundedRetryAttemptsPropertyTest validates that the agent completes without hanging THEN it SHALL CONTINUE TO verify that the agent returns a result for all maxRetries values

6.2 WHEN SoapBoundedRetryAttemptsPropertyTest validates bounded attempts THEN it SHALL CONTINUE TO verify that runStrategy is called exactly once per generation request

#### 7. Incorrect SOAP 1.2 Error Message (Priority 3)

7.1 WHEN SoapMockValidator validates SOAP 1.1 mocks with missing SOAPAction header THEN it SHALL CONTINUE TO return the error message "Missing SOAPAction header"

7.2 WHEN SoapMockValidator validates all other SOAP validation rules THEN it SHALL CONTINUE TO return appropriate error messages for each rule violation

#### 8. KDoc Mismatch (Priority 3)

8.1 WHEN SoapGenerationConfig is used in the application THEN it SHALL CONTINUE TO function correctly with the explicit composition pattern

#### 9. Cryptic Test Error (Priority 3)

9.1 WHEN RoundTripIntegrityPropertyTest.flushOperation() processes valid WSDL operations THEN it SHALL CONTINUE TO correctly flush and validate operation data

#### 10. Weak Content Assertion (Priority 3)

10.1 WHEN WsdlContentFetcherTest validates successful WSDL fetching THEN it SHALL CONTINUE TO verify that the fetcher returns valid XML content

10.2 WHEN WsdlContentFetcherTest validates error scenarios THEN it SHALL CONTINUE TO verify that appropriate exceptions are thrown

#### 11. Incomplete SnapStart Priming (Priority 2)

11.1 WHEN the Lambda function initializes with SnapStart priming for REST/OpenAPI functionality THEN it SHALL CONTINUE TO successfully warm up REST parsers and validators

11.2 WHEN SnapStart priming completes THEN it SHALL CONTINUE TO reduce cold start latency for REST/OpenAPI generation requests

#### 12. Insufficient Test Coverage (Priority 2)

12.1 WHEN running test coverage analysis for REST/OpenAPI functionality THEN it SHALL CONTINUE TO maintain high test coverage levels (80%+ enforced, aiming for 90%+)

12.2 WHEN existing GraphQL and SOAP/WSDL tests run THEN they SHALL CONTINUE TO pass and validate current functionality correctly
