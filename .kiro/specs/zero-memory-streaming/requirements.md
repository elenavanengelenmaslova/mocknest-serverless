# Requirements Document

## Introduction

Zero-memory streaming eliminates full body materialization when `chunkedDribbleDelay` is configured on a persistent mock whose response body has been externalized to S3 via `NormalizeMappingBodyFilter`. Currently, the handler loads the entire S3 file into memory as a `String`, converts it to a `ByteArray`, and then chunks it — consuming ~2× the payload size in heap. This feature introduces a streaming path where the `ChunkedDribbleDelayCapture` transformer intercepts the `bodyFileName` before WireMock renders the response, and the handler streams directly from S3 in bounded 1MB chunks with inter-chunk delays, keeping peak memory usage constant regardless of payload size.

## Glossary

- **Transformer**: The `ChunkedDribbleDelayCapture` WireMock `ResponseDefinitionTransformerV2` extension that intercepts response definitions before rendering
- **Handler**: The `StreamingRuntimeLambdaHandler` AWS Lambda request stream handler that routes requests and writes streaming responses
- **S3ResponseStreamer**: Existing class that streams S3 object content to an `OutputStream` using a bounded 1MB buffer
- **ChunkedResponseWriter**: Class responsible for splitting response body into chunks with configurable inter-chunk delays
- **CapturedDribbleConfig**: Thread-local data class holding captured dribble configuration (number of chunks, total duration, and optionally the body file name)
- **bodyFileName**: The S3 object key (relative to `__files/` prefix) referencing an externalized response body stored by `NormalizeMappingBodyFilter`
- **Persistent_Mock**: A WireMock stub mapping with `persistent: true` (default) whose body is externalized to S3 as a file
- **Inline_Mock**: A WireMock stub mapping with `persistent: false` whose body remains inline in the mapping JSON (not externalized)
- **Dribble_Config**: The `chunkedDribbleDelay` configuration on a response definition specifying `numberOfChunks` and `totalDuration`
- **Streaming_Buffer**: A fixed-size 1MB byte array used to read S3 content incrementally without loading the full object into memory
- **Progressive_Delivery**: The observable behavior where response bytes arrive incrementally over time rather than all at once

## Requirements

### Requirement 1: Transformer Intercepts bodyFileName for Dribble Responses

**User Story:** As a platform operator, I want the transformer to capture the bodyFileName and prevent WireMock from loading the S3 file, so that large response bodies are never materialized in memory.

#### Acceptance Criteria

1. WHEN a response definition contains both a `bodyFileName` and a `chunkedDribbleDelay` configuration, THE Transformer SHALL store the `bodyFileName` in the `CapturedDribbleConfig` thread-local alongside the dribble parameters
2. WHEN a response definition contains both a `bodyFileName` and a `chunkedDribbleDelay` configuration, THE Transformer SHALL return a modified `ResponseDefinition` with the `bodyFileName` removed and an empty placeholder body set
3. WHEN a response definition contains a `chunkedDribbleDelay` but no `bodyFileName`, THE Transformer SHALL return the response definition unchanged and store only the dribble parameters in the thread-local (existing behavior for inline mocks)
4. WHEN a response definition contains a `bodyFileName` but no `chunkedDribbleDelay`, THE Transformer SHALL return the response definition unchanged and not store any configuration in the thread-local

### Requirement 2: Handler Streams from S3 with Chunked Delays

**User Story:** As a platform operator, I want the handler to stream the response body directly from S3 in chunks with delays when a bodyFileName is captured, so that large payloads are delivered progressively without memory pressure.

#### Acceptance Criteria

1. WHEN the `CapturedDribbleConfig` contains a `bodyFileName`, THE Handler SHALL stream the response body from S3 using the `S3ResponseStreamer` instead of using the in-memory body bytes
2. WHEN streaming from S3 with dribble configuration, THE Handler SHALL write response metadata and delimiter first, then deliver the S3 content in chunks with inter-chunk delays matching the configured `numberOfChunks` and `totalDurationMs`
3. WHEN streaming from S3 with dribble configuration, THE Handler SHALL use the `ChunkedResponseWriter` with an `InputStream`-based variant that reads from the S3 stream in bounded chunks
4. IF the S3 object cannot be retrieved during streaming, THEN THE Handler SHALL log the error and return a 502 error response to the client

### Requirement 3: Bounded Memory Usage During Streaming

**User Story:** As a platform operator, I want peak memory usage for the response body to remain bounded at 1MB during chunked streaming from S3, so that Lambda memory is not exhausted by large payloads.

#### Acceptance Criteria

1. WHILE streaming a response body from S3 with chunked dribble delay, THE ChunkedResponseWriter SHALL hold at most one 1MB buffer of response body content in memory at any time
2. THE ChunkedResponseWriter SHALL provide an `InputStream`-based `writeChunked` method that reads from the stream in increments no larger than 1MB and writes each increment to the output with the configured inter-chunk delay
3. WHEN the total response body exceeds 1MB, THE ChunkedResponseWriter SHALL NOT accumulate the full body in memory before chunking begins

### Requirement 4: Inline Body Dribble Mocks Continue Unchanged

**User Story:** As a developer, I want inline body dribble mocks (persistent: false) to continue working with the existing in-memory chunking path, so that test mocks are not affected by the streaming optimization.

#### Acceptance Criteria

1. WHEN a `CapturedDribbleConfig` does not contain a `bodyFileName` and the response body bytes are available in memory, THE Handler SHALL use the existing `ByteArray`-based `writeChunked` method to deliver the response
2. WHEN a response definition has `persistent: false` with an inline body and `chunkedDribbleDelay`, THE Transformer SHALL capture only the dribble parameters without a `bodyFileName` (existing behavior preserved)
3. THE Handler SHALL NOT attempt S3 streaming when no `bodyFileName` is present in the captured configuration

### Requirement 5: Non-Dribble Responses Remain Unaffected

**User Story:** As a developer, I want non-dribble responses to continue flowing through the standard WireMock rendering and streaming protocol path, so that the optimization does not introduce regressions.

#### Acceptance Criteria

1. WHEN a response definition does not contain a `chunkedDribbleDelay`, THE Transformer SHALL NOT modify the response definition or store any thread-local configuration
2. WHEN no `CapturedDribbleConfig` is present after routing, THE Handler SHALL write the response using the standard `StreamingProtocolWriter` path
3. WHEN a response definition contains a `bodyFileName` but no `chunkedDribbleDelay`, THE Handler SHALL allow WireMock to load and render the file normally through its standard body resolution

### Requirement 6: Post-Deploy Test Verifies Progressive Delivery

**User Story:** As a platform operator, I want a post-deploy test that verifies chunked dribble responses are delivered progressively over time, so that I can confirm the streaming behavior works end-to-end in the deployed environment.

#### Acceptance Criteria

1. THE Post_Deploy_Test SHALL create a persistent mock with a response body larger than 1MB and a `chunkedDribbleDelay` configuration
2. THE Post_Deploy_Test SHALL issue a curl request with `--no-buffer` and measure that response bytes arrive incrementally over the configured duration rather than all at once
3. THE Post_Deploy_Test SHALL verify that the complete response body is received and matches the expected content
4. IF the response arrives entirely within the first 10% of the configured total duration, THEN THE Post_Deploy_Test SHALL report a failure indicating progressive delivery is not working

### Requirement 7: Integration Test Verifies Correct Body from S3 Streaming

**User Story:** As a developer, I want an integration test that verifies the full response body arrives correctly when streamed from S3 with chunked dribble delay, so that I can confirm data integrity of the streaming path.

#### Acceptance Criteria

1. THE Integration_Test SHALL create a persistent mock with a known response body and `chunkedDribbleDelay` configuration
2. THE Integration_Test SHALL verify that the complete response body received by the client matches the original body byte-for-byte
3. THE Integration_Test SHALL verify that the response is delivered in the configured number of chunks (observable via timing or chunk boundaries)
4. THE Integration_Test SHALL test with response bodies of varying sizes (small: <1MB, medium: ~5MB, large: ~50MB) to confirm the streaming path handles all sizes correctly
