# Implementation Plan: Response Streaming

## Overview

Transform MockNest Serverless from buffered Lambda responses (6MB limit) to streaming responses (200MB limit) by switching both Runtime and Generation Lambda handlers from `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>` to `RequestStreamHandler`. Implement SSE mock simulation via chunked delivery with configurable delays. Shared streaming components live in `infra.aws.core`, runtime-specific chunking in `infra.aws.runtime`, and generation-specific handler in `infra.aws.generation`.

## Tasks

- [x] 1. Implement shared streaming components in `infra.aws.core`
  - [x] 1.1 Create `StreamingProtocolWriter` in `nl.vintik.mocknest.infra.aws.core.streaming`
    - Implement metadata JSON serialization (statusCode + headers), 8 null byte delimiter, and body writing
    - Implement `write(response: HttpResponse, output: OutputStream)` for complete responses
    - Implement `writeMetadataAndDelimiter(statusCode, headers, output)` for chunked use
    - Validate metadata size does not exceed 16,376 bytes; throw `MetadataTooLargeException` if exceeded
    - Propagate I/O exceptions without writing partial data
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [x] 1.2 Create `ApiGatewayRequestParser` in `nl.vintik.mocknest.infra.aws.core.streaming`
    - Parse raw `InputStream` JSON into `HttpRequest` using Kotlinx Serialization
    - Handle `isBase64Encoded` body decoding
    - Merge `multiValueHeaders` and `multiValueQueryStringParameters` into `HttpRequest`
    - Throw `RequestParseException` for malformed JSON or missing required fields (`httpMethod`, `path`)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x] 1.3 Update `HttpRequest` domain model in `nl.vintik.mocknest.domain.core`
    - Add `multiValueHeaders: Map<String, List<String>>` field (default `emptyMap()`)
    - Add `multiValueQueryParameters: Map<String, List<String>>` field (default `emptyMap()`)
    - Preserve backward compatibility with existing `headers` and `queryParameters` fields
    - _Requirements: 6.4, 6.5_

  - [x] 1.4 Write unit tests for `StreamingProtocolWriter`
    - Test metadata serialization with various status codes (100, 200, 404, 599)
    - Test null delimiter is exactly 8 bytes
    - Test body writing (empty body, small body, large body)
    - Test `MetadataTooLargeException` when metadata exceeds 16,376 bytes
    - Test I/O error propagation
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7, 3.8_

  - [x] 1.5 Write unit tests for `ApiGatewayRequestParser`
    - Test field extraction (method, path, headers, query params, body)
    - Test base64 body decoding
    - Test multi-value header and query parameter merging
    - Test malformed JSON handling (throws `RequestParseException`)
    - Test missing required fields handling
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x] 1.6 Write property test: Streaming Protocol Round-Trip (`StreamingProtocolRoundTripPropertyTest`)
    - **Property 1: Streaming Protocol Round-Trip**
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse test cases
    - Test cases: empty body, 1-byte body, 1KB body, 1MB body, 10MB body, unicode body, binary body, many headers (50), single header, special characters in headers, status codes 100/200/301/404/500/599, empty headers map, body with null bytes, large header values
    - Verify: write response → parse stream → body is byte-identical, status code matches, headers match
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

  - [x] 1.7 Write property test: Request Parsing Equivalence (`RequestParsingEquivalencePropertyTest`)
    - **Property 2: Request Parsing Equivalence**
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse test cases
    - Test cases: GET with no body, POST with JSON body, POST with base64 body, PUT with large body, DELETE with empty body, request with multi-value headers, request with multi-value query params, request with special characters in path, request with unicode body, minimal request (method + path only), request with all fields populated, PATCH request, OPTIONS request, HEAD request, request with empty string body
    - Verify: parsed `HttpRequest` has identical method, path, decoded body, and merged header/query values
    - **Validates: Requirements 1.2, 2.2, 6.1, 6.2, 6.4, 6.5, 6.6**

  - [x] 1.8 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Implement Runtime Lambda streaming handler in `infra.aws.runtime`
  - [x] 2.1 Create `ChunkedResponseWriter` in `nl.vintik.mocknest.infra.aws.runtime.streaming`
    - Implement `writeChunked(body, numberOfChunks, totalDurationMs, output)` splitting body into chunks with delays
    - Implement `calculateChunkSizes(bodySize, numberOfChunks)` for chunk size calculation
    - Flush after each chunk write
    - Log warning if `totalDuration` > 270,000 ms
    - Handle edge case: `numberOfChunks` > body bytes → one byte per chunk, remaining chunks empty
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.6, 4.8_

  - [x] 2.2 Create `StreamingRuntimeLambdaHandler` in `nl.vintik.mocknest.infra.aws.runtime.function`
    - Implement `RequestStreamHandler` interface
    - Bootstrap Koin with same modules as current `RuntimeLambdaHandler` in companion `init` block
    - Maintain SnapStart priming hooks and CRaC registration
    - Parse input via `ApiGatewayRequestParser`
    - Route requests: `/__admin/health` → health, `/__admin/*` → admin, `/mocknest/*` → client, else → 404
    - For client requests: detect `chunkedDribbleDelay` and use `ChunkedResponseWriter` if present
    - For large S3-stored bodies: stream from S3 with 1MB bounded buffer
    - Write response via `StreamingProtocolWriter`
    - On parse failure: write 400 streaming response with error message
    - On invalid `chunkedDribbleDelay` config (`numberOfChunks` < 1 or `totalDuration` < 0): ignore and write full body
    - On response > 200MB: write 502 streaming response
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 2.3 Write unit tests for `ChunkedResponseWriter`
    - Test chunk size calculation for various body sizes and chunk counts
    - Test delay timing using `runTest` with virtual time
    - Test flush behavior after each chunk
    - Test edge case: chunks > body bytes
    - Test invalid config fallback (numberOfChunks < 1, totalDuration < 0)
    - Test warning log for totalDuration > 270,000 ms
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.6, 4.7, 4.8_

  - [x] 2.4 Write unit tests for `StreamingRuntimeLambdaHandler`
    - Test routing: health, admin, client, 404 paths
    - Test parse failure → 400 response
    - Test chunked response detection and delegation
    - Test S3 streaming with bounded buffer
    - Test 502 response for oversized payloads
    - _Requirements: 1.7, 1.9, 4.4, 7.4_

  - [x] 2.5 Write property test: Chunked Delivery Preserves Body (`ChunkedDeliveryPropertyTest`)
    - **Property 3: Chunked Delivery Preserves Body**
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse test cases
    - Test cases: 1 byte / 2 chunks, 10 bytes / 3 chunks, 100 bytes / 10 chunks, 1000 bytes / 7 chunks, 10000 bytes / 100 chunks, 100000 bytes / 1000 chunks, 5 bytes / 5 chunks, 3 bytes / 2 chunks, 1024 bytes / 512 chunks, 999 bytes / 13 chunks, 50000 bytes / 3 chunks, 7 bytes / 4 chunks, 256 bytes / 256 chunks, 1 byte / 1000 chunks (edge), 100000 bytes / 2 chunks
    - Verify: concatenating all chunks produces byte-identical body, exactly `numberOfChunks` chunks produced
    - **Validates: Requirements 4.1, 4.6**

  - [x] 2.6 Write property test: Chunked Delay Calculation (`ChunkedDelayCalculationPropertyTest`)
    - **Property 4: Chunked Delay Calculation**
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse test cases
    - Test cases: various combinations of totalDuration (0, 100, 1000, 3000, 5000, 10000, 60000, 270000) and numberOfChunks (2, 3, 5, 7, 10, 50, 100, 1000)
    - Verify: inter-chunk delay equals `totalDuration / numberOfChunks` (integer division), total delays = `numberOfChunks - 1`
    - **Validates: Requirements 4.2**

  - [x] 2.7 Write property test: Routing Preservation (`StreamingRoutingPropertyTest`)
    - **Property 6: Routing Preservation Under New Interface**
    - Use `@ParameterizedTest` with `@MethodSource` providing 20+ test cases
    - Test cases: `/__admin/health`, `/__admin/mappings`, `/__admin/requests`, `/mocknest/api/users`, `/mocknest/api/orders/123`, `/unknown`, `/`, `/other/path`, `/__admin/`, `/mocknest/`, `/__admin/settings`, `/mocknest/graphql`, `/mocknest/soap/service`, `/__admin/scenarios`, `/random`, `/api/users`, `/__admin/health/check`, `/mocknest/`, `/mocknest/a/b/c/d`, `/__admin/near-misses`
    - Verify: same routing decision as current `RuntimeLambdaHandler`
    - **Validates: Requirements 1.7, 2.8**

  - [x] 2.8 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Implement Generation Lambda streaming handler in `infra.aws.generation`
  - [x] 3.1 Create `StreamingGenerationLambdaHandler` in `nl.vintik.mocknest.infra.aws.generation.function`
    - Implement `RequestStreamHandler` interface
    - Bootstrap Koin with same modules as current `GenerationLambdaHandler` in companion `init` block
    - Maintain SnapStart priming hooks
    - Parse input via `ApiGatewayRequestParser`
    - Route request (same logic as current handler)
    - Write response via `StreamingProtocolWriter`
    - On parse failure: write 400 streaming response with error message
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10_

  - [x] 3.2 Write unit tests for `StreamingGenerationLambdaHandler`
    - Test routing (health check and AI generation routes)
    - Test parse failure → 400 response
    - Test SnapStart initialization
    - Test response written via streaming protocol
    - _Requirements: 2.8, 2.10_

  - [x] 3.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Implement S3 streaming for large payloads
  - [x] 4.1 Implement S3 bounded-buffer streaming in the runtime handler
    - When WireMock response references a `bodyFileName` in S3, stream content via 1MB buffer
    - Write directly to `OutputStream` without loading entire file into memory
    - Handle S3 retrieval failures: abort stream, log error with S3 key and failure reason
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

  - [x] 4.2 Write unit tests for S3 streaming
    - Test streaming small S3 file (< 1MB)
    - Test streaming large S3 file (> 1MB, verifying buffer size)
    - Test S3 retrieval failure handling (error logged, stream aborted)
    - _Requirements: 7.3, 7.5_

  - [x] 4.3 Write property test: S3 Streaming With Bounded Buffer (`S3StreamingPropertyTest`)
    - **Property 5: S3 Streaming With Bounded Buffer**
    - Use `@ParameterizedTest` with `@MethodSource` providing 10+ diverse test cases
    - Test cases: 1 byte, 100 bytes, 1KB, 100KB, 1MB, 5MB, 10MB, 25MB, 50MB, 1MB+1 byte
    - Verify: complete object content written byte-for-byte, no single buffer allocation exceeds 1MB
    - **Validates: Requirements 7.3**

  - [x] 4.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 5. Checkpoint - Ensure all unit and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Update SAM template for streaming configuration
  - [x] 6.1 Update Runtime Lambda `Handler` property to `StreamingRuntimeLambdaHandler` class name
    - Update on both `MockNestRuntimeFunction` (API Key mode) and `MockNestRuntimeFunctionIam` (IAM mode)
    - _Requirements: 5.4, 5.6_

  - [x] 6.2 Update Generation Lambda `Handler` property to `StreamingGenerationLambdaHandler` class name
    - Update on both `MockNestGenerationFunction` (API Key mode) and `MockNestGenerationFunctionIam` (IAM mode)
    - _Requirements: 5.5, 5.6_

  - [x] 6.3 Add streaming Transfer Mode configuration to API Gateway events
    - Set Transfer Mode to `STREAM` on `/mocknest/{proxy+}` routes for Runtime Lambda
    - Set Transfer Mode to `STREAM` on `/__admin/{proxy+}` routes for Runtime Lambda
    - Set Transfer Mode to `STREAM` on `/ai/{proxy+}` routes for Generation Lambda
    - Apply to both API Key mode and IAM mode conditional resources
    - _Requirements: 5.1, 5.2, 5.3, 5.6_

  - [x] 6.4 Validate SAM template with `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1`
    - Confirm exit code 0
    - _Requirements: 5.7_

  - [x] 6.5 Run `./gradlew clean test` and confirm all tests pass

- [x] 7. Write integration tests with LocalStack
  - [x] 7.1 Create `StreamingIntegrationTest` in `infra.aws.runtime` test sources
    - Use shared `SharedLocalStackContainer` for S3
    - Test: register mock with 1KB–6MB body → invoke → verify response body matches byte-for-byte with expected status code
    - Test: register mock with 7MB+ body → invoke → verify received byte count equals registered byte count
    - Test: register SSE mock with `chunkedDribbleDelay` (3 chunks, 3000ms) → invoke → verify delivery time ≥ 80% of totalDuration
    - Test: register mock with custom status code and custom headers → invoke → verify exact status and headers received
    - Test: register 404 mock and 503 mock → invoke each → verify correct status codes and bodies
    - Clean up all mappings after each test
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 7.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 8. Add post-deploy curl tests
  - [x] 8.1 Add streaming tests to `scripts/post-deploy-test.sh`
    - Test: register mock with 7MB+ body → invoke → verify received byte length matches
    - Test: register SSE mock with `chunkedDribbleDelay` (3 chunks, 2000ms) → invoke → verify elapsed time ≥ totalDuration
    - Test: register mock with body < 6MB → invoke → verify HTTP 200 and body matches
    - Test: register mock with 2+ custom headers → invoke → verify headers in curl response
    - Clean up all mappings after each test
    - Use same curl commands and mock configurations as documented in USAGE.md SSE examples
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [x] 8.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 9. Update documentation
  - [x] 9.1 Update `docs/USAGE.md` with SSE mock configuration example
    - Include complete `chunkedDribbleDelay` example with curl commands (create mapping, call mock, expected output)
    - Add section explaining 200MB max response size replacing 6MB limit
    - Explain 5-minute idle timeout constraint (no data for 300s → connection closed)
    - _Requirements: 10.1, 10.2, 10.5_

  - [x] 9.2 Update `README.md`
    - Replace "Very large request or response payloads (over 6 MB)" in "When Not to Use" with request-only note
    - Add streaming response support and SSE mock simulation to "Current Features" section
    - Add "Payload Size Limits" subsection to "Known Limitations and Best Practices"
    - _Requirements: 10.3, 10.4, 10.6_

  - [x] 9.3 Update `.kiro/steering/structure.md` Service Limits & Quotas
    - Change Lambda Limits entry from "6MB request/response payload size limit" to "6MB request payload / 200MB response payload (streaming) size limit"
    - _Requirements: 10.7_

  - [x] 9.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 10. Final checkpoint - Coverage verification
  - [x] 10.1 Run `./gradlew koverHtmlReport` and verify 90%+ coverage for new streaming code
  - [x] 10.2 Run `./gradlew koverVerify` to enforce the 90% coverage threshold
    - _Requirements: all_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major component
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Integration tests use the shared `SharedLocalStackContainer` — do not create new LocalStack containers
- All Kotlin code targets JVM 25 with Kotlin 2.3.0, using Koin for DI and Kotlinx Serialization for JSON
- Follow clean architecture: shared streaming components in `infra.aws.core`, runtime-specific in `infra.aws.runtime`, generation-specific in `infra.aws.generation`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "1.5", "1.6", "1.7"] },
    { "id": 2, "tasks": ["1.8", "2.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.1"] },
    { "id": 4, "tasks": ["2.4", "2.5", "2.6", "2.7", "3.2"] },
    { "id": 5, "tasks": ["2.8", "3.3", "4.1"] },
    { "id": 6, "tasks": ["4.2", "4.3"] },
    { "id": 7, "tasks": ["4.4", "6.1", "6.2", "6.3"] },
    { "id": 8, "tasks": ["6.4", "6.5"] },
    { "id": 9, "tasks": ["7.1"] },
    { "id": 10, "tasks": ["7.2", "8.1"] },
    { "id": 11, "tasks": ["8.2", "9.1", "9.2", "9.3"] },
    { "id": 12, "tasks": ["9.4", "10.1"] },
    { "id": 13, "tasks": ["10.2"] }
  ]
}
```
