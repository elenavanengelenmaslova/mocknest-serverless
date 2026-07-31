# Requirements Document

## Introduction

The MockNest Serverless project currently contains a hand-rolled implementation of the AWS Lambda / API Gateway streaming response protocol in the `infra/aws/core/streaming` package (`StreamingProtocolWriter`, `MetadataTooLargeException`). This protocol — metadata JSON + 8-null-byte delimiter + body — is now available as a published Maven library: `nl.vintik:aws-lambda-streaming-core:2.0.0`.

This refactoring replaces the custom protocol-encoding code with the library, reducing maintenance surface, aligning with the canonical implementation, and gaining features like proper `Set-Cookie` handling and the `copy()` bounded-buffer utility. The request-parsing side (`ApiGatewayRequestParser`) and the MockNest-specific chunked-dribble-delay logic remain untouched.

## Glossary

- **streaming-core library**: The published Maven artifact `nl.vintik:aws-lambda-streaming-core:2.0.0` implementing the API Gateway streaming response protocol
- **ResponseWriter**: Library class that serializes metadata JSON + 8-byte delimiter and optionally writes body bytes
- **ResponseMetadata**: Library data class representing the prelude: `statusCode`, `headers`, `cookies`
- **copy()**: Library utility function that streams bytes from an InputStream to an OutputStream through a fixed 1MB buffer
- **StreamingProtocolWriter**: The existing custom class in `infra/aws/core/streaming/` to be replaced
- **MetadataTooLargeException (local)**: The existing exception in `infra/aws/core/streaming/` to be replaced by the library's version
- **OBSERVED_MAX_PRELUDE_LEN**: Library constant (16,376 bytes) representing the commonly cited metadata size limit

## Requirements

### Requirement 1: Replace StreamingProtocolWriter with Library ResponseWriter

**User Story:** As a maintainer, I want the streaming protocol encoding to use the published library instead of custom code, so that protocol correctness is maintained by a dedicated, tested artifact.

#### Acceptance Criteria

1. ALL usages of `StreamingProtocolWriter` in both `StreamingRuntimeLambdaHandler` and `StreamingGenerationLambdaHandler` SHALL be replaced by `nl.vintik.lambda.streaming.ResponseWriter`
2. THE `ResponseWriter` SHALL be constructed with `maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN` to preserve the existing metadata-size validation behavior
3. WHEN writing a complete response (metadata + body), THE handlers SHALL use `ResponseWriter.writeResponse()` with a `ResponseMetadata.fromMultiValue()` constructed from the `HttpResponse.headers` map
4. WHEN writing only metadata before chunked body delivery, THE handlers SHALL use `ResponseWriter.writeMetadata()` followed by separate body writing
5. THE local `StreamingProtocolWriter.kt` file SHALL be deleted after all usages are migrated
6. THE local `MetadataTooLargeException.kt` file SHALL be deleted after all usages reference the library's version

### Requirement 2: Replace Manual Buffer Loop with Library copy() in S3ResponseStreamer

**User Story:** As a maintainer, I want the S3-to-OutputStream streaming loop to use the library's `copy()` function, so that bounded-buffer streaming logic is not duplicated across projects.

#### Acceptance Criteria

1. THE `S3ResponseStreamer.streamToOutput()` method SHALL replace its manual buffer loop with a call to `nl.vintik.lambda.streaming.copy(inputStream, output)`
2. THE replacement SHALL maintain the same observable behavior: 1MB bounded buffer, per-chunk flush, byte-identical output
3. THE `S3ResponseStreamer.BUFFER_SIZE` companion constant MAY be removed since the library defines `BUFFER_SIZE` (1MB) as the canonical value
4. THE `streamWithConsumer()` method SHALL remain unchanged since it delegates to `ChunkedResponseWriter` which has its own stream-reading logic not covered by the library

### Requirement 3: Add Library Dependency to Core Module

**User Story:** As a maintainer, I want the library dependency declared in one place (the core module), so that both runtime and generation modules inherit it transitively.

#### Acceptance Criteria

1. THE `software/infra/aws/core/build.gradle.kts` SHALL declare `api("nl.vintik:aws-lambda-streaming-core:2.0.0")` so downstream modules inherit it
2. THE `kotlinx-serialization-json` dependency in the core module SHALL remain because `ApiGatewayRequestParser` still requires it
3. THE project SHALL compile and all tests SHALL pass after the dependency is added

### Requirement 4: Adapt Error Response Format

**User Story:** As a maintainer, I want error responses to use the library's `writeError()` convenience where appropriate, while preserving existing error response contracts.

#### Acceptance Criteria

1. IN `StreamingGenerationLambdaHandler`, error responses that already use `application/json` format SHALL use `ResponseWriter.writeError()` for consistency
2. IN `StreamingRuntimeLambdaHandler`, the `writeErrorResponse()` private method SHALL be updated to use `ResponseWriter.writeResponse()` with the appropriate metadata and body encoding
3. THE error response body format visible to clients SHALL remain unchanged (no breaking API change)

### Requirement 5: Update Test References to Use Library Constants

**User Story:** As a maintainer, I want tests to reference the library's constants instead of the removed local class, so that test compilation succeeds and tests remain correct.

#### Acceptance Criteria

1. ALL test references to `StreamingProtocolWriter.NULL_DELIMITER_SIZE` SHALL be replaced with `nl.vintik.lambda.streaming.DELIMITER_LEN`
2. ALL test references to `StreamingProtocolWriter.MAX_METADATA_SIZE` SHALL be replaced with `nl.vintik.lambda.streaming.OBSERVED_MAX_PRELUDE_LEN`
3. ALL test references to `StreamingProtocolWriter.CONTENT_TYPE` SHALL be replaced with `nl.vintik.lambda.streaming.METADATA_PRELUDE_CONTENT_TYPE`
4. THE `StreamingProtocolRoundTripPropertyTest` SHALL be updated to use `ResponseWriter` instead of `StreamingProtocolWriter`, or deleted if the library's own tests already cover this scenario

### Requirement 6: Preserve Request Parsing Infrastructure

**User Story:** As a developer, I want the API Gateway request parsing to remain unchanged, since the library only covers response encoding.

#### Acceptance Criteria

1. THE `ApiGatewayRequestParser`, `ApiGatewayProxyRequest`, and `RequestParseException` classes SHALL remain in `infra/aws/core/streaming/` without modification
2. NO changes SHALL be made to request deserialization, base64 decoding, or multi-value header/query parameter handling

### Requirement 7: Preserve Chunked Dribble Delay Behavior

**User Story:** As a developer, I want the chunked-dribble-delay streaming behavior to remain unchanged, since it is MockNest-specific and not covered by the library.

#### Acceptance Criteria

1. THE `ChunkedResponseWriter` class SHALL remain unchanged (it implements delay-between-chunks logic specific to MockNest)
2. THE `ChunkedDribbleDelayCapture` WireMock extension SHALL remain unchanged
3. THE `S3ResponseStreamer.streamWithConsumer()` method and its interaction with `ChunkedResponseWriter.writeChunkedFromStream()` SHALL remain unchanged
4. THE overall request flow through `StreamingRuntimeLambdaHandler` (parse → route → check dribble → write) SHALL remain structurally the same, only replacing protocol-encoding calls

### Requirement 8: Build and Test Verification

**User Story:** As a maintainer, I want the full project to build cleanly and all tests to pass after the refactoring, confirming no regressions.

#### Acceptance Criteria

1. `./gradlew clean test` SHALL pass with exit code 0
2. `./gradlew koverVerify` SHALL confirm 90%+ aggregated coverage is maintained
3. `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` SHALL pass (no SAM template changes expected)
