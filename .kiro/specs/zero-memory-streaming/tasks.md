# Implementation Plan: Zero-Memory Streaming

## Overview

This plan implements the zero-memory streaming optimization for chunked dribble delay responses backed by S3 body files. The key change is intercepting `bodyFileName` in the transformer before WireMock loads the file, then streaming directly from S3 with bounded 1MB chunks and inter-chunk delays. The existing inline body dribble path remains unchanged.

## Tasks

- [x] 1. Expand CapturedDribbleConfig and modify ChunkedDribbleDelayCapture transformer
  - [x] 1.1 Add optional `bodyFileName` field to `CapturedDribbleConfig` data class
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCapture.kt`
    - Add `val bodyFileName: String? = null` as a third parameter with default value
    - Existing callers continue to work due to default value
    - _Requirements: 1.1_

  - [x] 1.2 Modify `ChunkedDribbleDelayCapture.transform()` to intercept bodyFileName
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCapture.kt`
    - Add import for `com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder`
    - When both `bodyFileName` and valid `chunkedDribbleDelay` are present:
      - Store `bodyFileName` in `CapturedDribbleConfig`
      - Return `ResponseDefinitionBuilder.like(responseDefinition).withBody("").build()`
    - When only `chunkedDribbleDelay` is present (no bodyFileName): existing behavior unchanged
    - When only `bodyFileName` is present (no dribble): return unchanged, no thread-local stored
    - When neither is present: return unchanged, clear thread-local
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 1.3 Write unit tests for transformer covering all 4 paths
    - File: `software/application/src/test/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCaptureTest.kt`
    - Test: bodyFileName + dribble → config has bodyFileName, returns modified ResponseDefinition with empty body
    - Test: dribble only (no bodyFileName) → config has null bodyFileName, returns original ResponseDefinition
    - Test: bodyFileName only (no dribble) → no config stored, returns original ResponseDefinition
    - Test: neither bodyFileName nor dribble → no config stored, returns original ResponseDefinition
    - Use MockK to mock `ServeEvent` and `ResponseDefinition`
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 1.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Add `writeChunkedFromStream` to ChunkedResponseWriter
  - [x] 2.1 Implement `writeChunkedFromStream` method and `calculateStreamChunkSize` helper
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriter.kt`
    - Add `STREAM_BUFFER_SIZE = 1024 * 1024` constant (1MB)
    - Add `import java.io.InputStream` and `import kotlinx.coroutines.delay`
    - Implement `suspend fun writeChunkedFromStream(input: InputStream, bodySize: Long, numberOfChunks: Int, totalDurationMs: Long, output: OutputStream)`
    - Reads at most `STREAM_BUFFER_SIZE` bytes at a time from the InputStream
    - Writes chunks to output with `delayBetweenChunks = totalDurationMs / numberOfChunks` between them
    - Include idle timeout warning check (same as existing `writeChunked`)
    - Implement `internal fun calculateStreamChunkSize(bodySize: Long, numberOfChunks: Int): Long` using ceiling division
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 2.2 Write unit tests for `writeChunkedFromStream`
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriterStreamTest.kt`
    - Test: output matches input byte-for-byte for various sizes (1KB, 5MB, 10MB)
    - Test: buffer never exceeds 1MB (verify by using a custom InputStream that tracks read sizes)
    - Test: correct number of flush calls (one per chunk)
    - Test: `calculateStreamChunkSize` edge cases (0 size, 1 chunk, more chunks than bytes)
    - Use `runTest` for coroutine testing with virtual time
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 2.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Add `getContentLength` and `getInputStream` to S3ResponseStreamer
  - [x] 3.1 Implement `getContentLength` method using S3 HeadObject
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/S3ResponseStreamer.kt`
    - Add import for `aws.sdk.kotlin.services.s3.model.HeadObjectRequest`
    - Implement `suspend fun getContentLength(s3Key: String): Long?` that calls `s3Client.headObject()` and returns `contentLength`
    - Return null on failure, log error
    - _Requirements: 2.1, 2.4_

  - [x] 3.2 Implement `streamWithConsumer` method for S3 object streaming with callback
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/S3ResponseStreamer.kt`
    - Implement `suspend fun streamWithConsumer(s3Key: String, consumer: suspend (InputStream, Long) -> Unit): Boolean`
    - The consumer callback receives the InputStream and contentLength, and must consume the stream before returning
    - This keeps the getObject lifecycle contained within S3ResponseStreamer (InputStream is valid inside the callback scope)
    - Return false on failure, log error
    - _Requirements: 2.1, 2.3_

  - [x] 3.3 Write unit tests for `getContentLength` and `streamWithConsumer`
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/S3ResponseStreamerTest.kt`
    - Test: `getContentLength` returns correct size for existing object
    - Test: `getContentLength` returns null when object doesn't exist
    - Test: `streamWithConsumer` invokes consumer with readable InputStream for existing object
    - Test: `streamWithConsumer` returns false when object doesn't exist
    - Test: consumer receives correct contentLength
    - Use MockK to mock `S3Client`
    - _Requirements: 2.1, 2.4_

  - [x] 3.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Add `writeS3ChunkedResponse` to StreamingRuntimeLambdaHandler
  - [x] 4.1 Implement S3 streaming path in the handler
    - File: `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandler.kt`
    - Inject `S3ResponseStreamer` via Koin: `private val s3ResponseStreamer: S3ResponseStreamer by inject()`
    - Modify `handleRequest` to check `dribbleConfig.bodyFileName`:
      - If non-null → call new `writeS3ChunkedResponse(response, dribbleConfig, output)`
      - If null → existing inline body path (unchanged)
    - Implement `private fun writeS3ChunkedResponse(response: HttpResponse, config: CapturedDribbleConfig, output: OutputStream)`:
      - Construct S3 key: `"__files/${config.bodyFileName}"` (using `FILES_PREFIX` pattern)
      - Call `s3ResponseStreamer.getContentLength(s3Key)` — if null, write 502 error
      - Validate `contentLength <= MAX_RESPONSE_SIZE_BYTES` — if exceeded, write 502 error
      - Write metadata + delimiter via `protocolWriter.writeMetadataAndDelimiter()`
      - Call `s3ResponseStreamer.streamWithConsumer(s3Key) { inputStream, _ -> chunkedWriter.writeChunkedFromStream(...) }`
      - The InputStream is consumed within the S3 callback scope (safe with Kotlin AWS SDK)
      - If `streamWithConsumer` returns false, log error (metadata already written, can't change status)
      - Flush output
    - Move the 200MB size check for non-S3 path to after the dribble config check
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 4.1, 4.3, 5.2_

  - [x] 4.2 Write unit tests for handler S3 streaming path
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandlerTest.kt`
    - Add tests in a new `S3ChunkedStreamingPath` nested class:
    - Test: When `CapturedDribbleConfig` has bodyFileName → calls S3ResponseStreamer, writes chunked response
    - Test: When `getContentLength` returns null → writes 502 error
    - Test: When content length exceeds 200MB → writes 502 error
    - Test: When `streamWithConsumer` returns false → metadata already written, error logged
    - Test: When `CapturedDribbleConfig` has no bodyFileName → uses existing ByteArray path (no S3 calls)
    - Test: When bodyFileName is intercepted by transformer → response.body is empty/placeholder (proves WireMock didn't load the file)
    - Use `ChunkedDribbleDelayCapture.setForTest()` to set up thread-local
    - _Requirements: 2.1, 2.2, 2.4, 4.1, 4.3_

  - [x] 4.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Property-based tests for correctness properties
  - [x] 6.1 Write property test for transformer bodyFileName interception
    - **Property 1: Transformer captures bodyFileName and removes it from ResponseDefinition**
    - **Validates: Requirements 1.1, 1.2**
    - File: `software/application/src/test/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCapturePropertyTest.kt`
    - Use `@ParameterizedTest` with `@MethodSource` generating diverse ResponseDefinitions with various bodyFileName values and dribble configs
    - Assert: returned ResponseDefinition has null bodyFileName and empty body; thread-local has the original bodyFileName

  - [x] 6.2 Write property test for transformer inline dribble preservation
    - **Property 2: Transformer preserves response unchanged for inline dribble mocks**
    - **Validates: Requirements 1.3, 4.2**
    - File: `software/application/src/test/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCapturePropertyTest.kt`
    - Use `@ParameterizedTest` with diverse inline body + dribble combinations
    - Assert: returned ResponseDefinition is unchanged; thread-local has null bodyFileName

  - [x] 6.3 Write property test for transformer no-op when no dribble
    - **Property 3: Transformer is no-op when no dribble is configured**
    - **Validates: Requirements 1.4, 5.1**
    - File: `software/application/src/test/kotlin/nl/vintik/mocknest/application/runtime/extensions/ChunkedDribbleDelayCapturePropertyTest.kt`
    - Use `@ParameterizedTest` with ResponseDefinitions that have no chunkedDribbleDelay (with and without bodyFileName)
    - Assert: returned ResponseDefinition is unchanged; no thread-local stored

  - [x] 6.4 Write property test for handler S3 routing
    - **Property 4: Handler routes to S3 streaming when bodyFileName is present**
    - **Validates: Requirements 2.1, 2.2**
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandlerPropertyTest.kt`
    - Use `@ParameterizedTest` with various bodyFileName values and dribble configs
    - Assert: S3ResponseStreamer is called, metadata+delimiter written before body

  - [x] 6.5 Write property test for handler ByteArray path routing
    - **Property 5: Handler routes to ByteArray path when no bodyFileName**
    - **Validates: Requirements 4.1, 4.3**
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/function/StreamingRuntimeLambdaHandlerPropertyTest.kt`
    - Use `@ParameterizedTest` with CapturedDribbleConfigs that have null bodyFileName
    - Assert: S3ResponseStreamer is NOT called, existing writeChunked path is used

  - [x] 6.6 Write property test for bounded memory during streaming
    - **Property 6: Bounded memory during InputStream-based streaming**
    - **Validates: Requirements 3.1, 3.2, 3.3**
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriterPropertyTest.kt`
    - Use `@ParameterizedTest` with `@MethodSource` generating InputStreams of varying sizes (1KB, 1MB, 5MB, 10MB, 50MB)
    - Use a custom InputStream wrapper that asserts no single `read()` call requests more than 1MB
    - Assert: buffer never exceeds STREAM_BUFFER_SIZE

  - [x] 6.7 Write property test for streaming round-trip data integrity
    - **Property 7: Streaming round-trip preserves body content byte-for-byte**
    - **Validates: Requirements 7.2, 7.4**
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriterPropertyTest.kt`
    - Use `@ParameterizedTest` with `@MethodSource` generating random byte arrays of various sizes
    - Stream through `writeChunkedFromStream` with various numberOfChunks values
    - Assert: output bytes match input bytes exactly

  - [x] 6.8 Write property test for chunk distribution
    - **Property 8: Chunked delivery distributes bytes across configured number of chunks**
    - **Validates: Requirements 7.3**
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ChunkedResponseWriterPropertyTest.kt`
    - Use `@ParameterizedTest` with various body sizes and chunk counts
    - Use a counting OutputStream that tracks flush calls
    - Assert: number of flush calls equals numberOfChunks (±1 for edge cases)

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Integration test with LocalStack
  - [x] 8.1 Write integration test for S3 streaming with chunked dribble delay
    - File: `software/infra/aws/runtime/src/test/kotlin/nl/vintik/mocknest/infra/aws/runtime/streaming/ZeroMemoryStreamingIntegrationTest.kt`
    - Extend existing `StreamingIntegrationTest` pattern (LocalStack + real WireMock)
    - Test: Create persistent mock with known body + `chunkedDribbleDelay` → invoke handler → verify complete body received byte-for-byte
    - Test: Small body (<1MB) with dribble → verify correct delivery
    - Test: Medium body (~5MB) with dribble → verify correct delivery
    - Test: Verify delivery timing is progressive (elapsed time >= 80% of totalDuration)
    - Test: Verify inline body dribble mock (persistent: false) still works unchanged
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 8.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 9. Post-deploy test script for progressive delivery verification
  - [x] 9.1 Add zero-memory streaming test to post-deploy script
    - File: `scripts/post-deploy-test.sh`
    - Add a test section that:
      - Creates a persistent mock with a body larger than 1MB and `chunkedDribbleDelay` (e.g., 5 chunks, 5000ms total)
      - Issues `curl --no-buffer` request and measures incremental byte arrival
      - Verifies complete response body matches expected content
      - Fails if response arrives entirely within first 10% of configured duration
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 9.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The existing inline body dribble path (persistent: false mocks) must remain unchanged — all existing tests must continue to pass
- The Kotlin AWS SDK's `getObject` lambda scope constraint means the InputStream must be consumed within the lambda or a pipe approach must be used — task 3.2 addresses this
- Use `ResponseDefinitionBuilder.like(responseDefinition).withBody("").build()` to create the modified ResponseDefinition (clears bodyFileName internally since body and bodyFileName are mutually exclusive in WireMock)
- The S3 key prefix is `"__files/"` (from `FILES_PREFIX` constant in `ObjectStorageBlobStore.kt`)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "2.1"] },
    { "id": 3, "tasks": ["1.4", "2.2", "3.1"] },
    { "id": 4, "tasks": ["2.3", "3.2"] },
    { "id": 5, "tasks": ["3.3", "4.1"] },
    { "id": 6, "tasks": ["3.4", "4.2"] },
    { "id": 7, "tasks": ["4.3"] },
    { "id": 8, "tasks": ["6.1", "6.2", "6.3", "6.6", "6.7", "6.8"] },
    { "id": 9, "tasks": ["6.4", "6.5"] },
    { "id": 10, "tasks": ["8.1"] },
    { "id": 11, "tasks": ["8.2", "9.1"] },
    { "id": 12, "tasks": ["9.2"] }
  ]
}
```
