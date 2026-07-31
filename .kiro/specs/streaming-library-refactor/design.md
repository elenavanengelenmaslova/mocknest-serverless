# Design Document

## Overview

This design replaces the custom `StreamingProtocolWriter` and associated local `MetadataTooLargeException` with the published `nl.vintik:aws-lambda-streaming-core:2.0.0` library. The refactoring is confined to the infrastructure layer (`infra/aws/core` and `infra/aws/runtime`) and touches only the response-encoding path. Request parsing, chunked-dribble-delay logic, and S3 streaming orchestration remain structurally unchanged.

## Architecture

### Dependency Flow (after refactoring)

```
streaming-core library (2.0.0)
    ↑ api dependency
infra/aws/core
    ↑ api dependency
infra/aws/runtime  &  infra/aws/generation
```

The library is declared as an `api` dependency in `infra/aws/core/build.gradle.kts`, making `ResponseWriter`, `ResponseMetadata`, `copy()`, and all constants available to both handler modules transitively.

### Component Mapping

| Before (custom) | After (library) | Notes |
|---|---|---|
| `StreamingProtocolWriter` | `ResponseWriter` | 1:1 replacement |
| `StreamingProtocolWriter.write(response, output)` | `writer.writeResponse(output, metadata, body)` | Metadata built via `ResponseMetadata.fromMultiValue()` |
| `StreamingProtocolWriter.writeMetadataAndDelimiter(...)` | `writer.writeMetadata(output, metadata)` | Used before chunked body delivery |
| `MetadataTooLargeException` (local) | `nl.vintik.lambda.streaming.MetadataTooLargeException` | Same semantics |
| `StreamingProtocolWriter.NULL_DELIMITER_SIZE` | `DELIMITER_LEN` (top-level const) | Value: 8 |
| `StreamingProtocolWriter.MAX_METADATA_SIZE` | `OBSERVED_MAX_PRELUDE_LEN` (top-level const) | Value: 16,376 |
| `StreamingProtocolWriter.CONTENT_TYPE` | `METADATA_PRELUDE_CONTENT_TYPE` (top-level const) | Same string |
| Manual buffer loop in `S3ResponseStreamer.streamToOutput()` | `copy(inputStream, output)` | Same 1MB buffer, same flush semantics |

### Header Conversion Strategy

Current code collapses `HttpResponse.headers: Map<String, List<String>>` into `Map<String, String>` manually in each handler. The library provides `ResponseMetadata.fromMultiValue(statusCode, headers)` which:
- Joins repeated header values with `", "`
- Routes `Set-Cookie` values to a dedicated `cookies` list (not comma-joined)

This is a better-correct implementation and will be adopted. The handlers will call `ResponseMetadata.fromMultiValue(response.statusCode.value, response.headers ?: emptyMap())` instead of the inline flatMap/toMap logic.

### Error Response Strategy

The library's `writeError()` produces `{"message":"..."}` with `Content-Type: application/json`. The current `StreamingRuntimeLambdaHandler.writeErrorResponse()` produces `text/plain` body. To avoid a breaking change:

- **Runtime handler**: Keep a private `writeErrorResponse` method that uses `writer.writeResponse()` with a `text/plain` metadata and the message as body bytes. This preserves the existing contract.
- **Generation handler**: Already uses `application/json` for errors with a `{"error":"..."}` shape. Switching to `{"message":"..."}` via `writeError()` would change the contract. We will use `writer.writeResponse()` with custom body to preserve the existing format.

### Files Deleted

1. `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/streaming/StreamingProtocolWriter.kt`
2. `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/streaming/MetadataTooLargeException.kt`

### Files Modified

1. `software/infra/aws/core/build.gradle.kts` — add library dependency
2. `software/infra/aws/runtime/src/main/kotlin/.../StreamingRuntimeLambdaHandler.kt` — use `ResponseWriter`
3. `software/infra/aws/generation/src/main/kotlin/.../StreamingGenerationLambdaHandler.kt` — use `ResponseWriter`
4. `software/infra/aws/runtime/src/main/kotlin/.../S3ResponseStreamer.kt` — use `copy()`
5. `software/infra/aws/core/src/test/kotlin/.../StreamingProtocolRoundTripPropertyTest.kt` — update to library types or delete
6. `software/infra/aws/runtime/src/test/kotlin/.../StreamingRuntimeLambdaHandlerTest.kt` — update constant references
7. `software/infra/aws/generation/src/test/kotlin/.../StreamingGenerationLambdaHandlerTest.kt` — update constant references

### Files Unchanged

- `ApiGatewayRequestParser.kt`, `ApiGatewayProxyRequest.kt`, `RequestParseException.kt`
- `ChunkedResponseWriter.kt`
- `ChunkedDribbleDelayCapture.kt`
- `S3ResponseStreamer.streamWithConsumer()` (only `streamToOutput` changes)
- SAM template, deployment scripts

## Detailed Design

### StreamingRuntimeLambdaHandler (after)

```kotlin
import nl.vintik.lambda.streaming.ResponseWriter
import nl.vintik.lambda.streaming.ResponseMetadata
import nl.vintik.lambda.streaming.OBSERVED_MAX_PRELUDE_LEN

class StreamingRuntimeLambdaHandler : RequestStreamHandler, KoinComponent {
    // ...
    private val writer = ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)
    // requestParser unchanged

    // Standard response path:
    private fun writeResponse(response: HttpResponse, output: OutputStream) {
        val metadata = ResponseMetadata.fromMultiValue(
            statusCode = response.statusCode.value,
            headers = response.headers ?: emptyMap(),
        )
        val body = response.body?.toByteArray(Charsets.UTF_8)
        writer.writeResponse(output, metadata, body)
    }

    // Chunked path (metadata only, body written separately):
    private fun writeMetadataOnly(statusCode: Int, headers: Map<String, List<String>>, output: OutputStream) {
        val metadata = ResponseMetadata.fromMultiValue(statusCode, headers)
        writer.writeMetadata(output, metadata)
    }

    // Error response (preserves text/plain contract):
    private fun writeErrorResponse(output: OutputStream, statusCode: Int, message: String) {
        val metadata = ResponseMetadata(
            statusCode = statusCode,
            headers = mapOf("Content-Type" to "text/plain"),
        )
        writer.writeResponse(output, metadata, message.toByteArray(Charsets.UTF_8))
    }
}
```

### StreamingGenerationLambdaHandler (after)

```kotlin
import nl.vintik.lambda.streaming.ResponseWriter
import nl.vintik.lambda.streaming.ResponseMetadata
import nl.vintik.lambda.streaming.OBSERVED_MAX_PRELUDE_LEN

class StreamingGenerationLambdaHandler : RequestStreamHandler, KoinComponent {
    // ...
    private val writer = ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context) {
        // ... parse, route ...

        // Write response:
        val metadata = ResponseMetadata.fromMultiValue(
            statusCode = response.statusCode.value,
            headers = response.headers ?: emptyMap(),
        )
        writer.writeResponse(output, metadata, response.body?.toByteArray(Charsets.UTF_8))
    }
}
```

### S3ResponseStreamer.streamToOutput (after)

```kotlin
import nl.vintik.lambda.streaming.copy

fun streamToOutput(s3Key: String, output: OutputStream): Boolean =
    runCatching {
        runBlocking {
            s3Client.getObject(GetObjectRequest {
                bucket = bucketName
                key = s3Key
            }) { response ->
                val body = response.body
                    ?: throw S3StreamingException("S3 object body is null for key: $s3Key")
                val inputStream = body.toInputStream()
                copy(inputStream, output)
            }
        }
        true
    }.onFailure { exception ->
        logger.error(exception) {
            "Failed to stream S3 object: key=$s3Key, bucket=$bucketName, reason=${exception.message}"
        }
    }.getOrDefault(false)
```

### build.gradle.kts change (core module)

```kotlin
dependencies {
    // Streaming protocol library (response encoding + bounded buffer copy)
    api("nl.vintik:aws-lambda-streaming-core:2.0.0")

    // ... existing deps ...
}
```

## Correctness Properties

1. **Wire-format equivalence**: For any `HttpResponse` with status 100-599, the bytes written by the new code through `ResponseWriter` SHALL be byte-identical to what `StreamingProtocolWriter` would have written (same JSON field order may differ, but API Gateway accepts any valid JSON)
2. **Metadata size enforcement**: The library's `MetadataTooLargeException` is thrown with the same 16,376-byte threshold when `maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN` is set
3. **Bounded memory invariant**: `copy()` and the existing `ChunkedResponseWriter` both cap buffer at 1MB — memory profile unchanged
4. **Flush semantics**: Library's `copy()` flushes after each chunk and after final bytes, matching the existing manual loop
5. **Set-Cookie correctness improvement**: `ResponseMetadata.fromMultiValue()` correctly separates `Set-Cookie` into the `cookies` array instead of comma-joining, which is actually an improvement over the current code

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JSON field ordering difference in metadata | Low | None — API Gateway parses JSON regardless of key order | Verified by library's own test suite |
| `Set-Cookie` routing to `cookies` array changes wire format | Low | Low — MockNest rarely sets cookies in mock responses | Improvement, not regression; test with cookie-bearing response |
| Library version compatibility with Java 25 runtime | Low | High | Library targets Java 21, fully compatible with Java 25 runtime |
| Transitive kotlinx-serialization version conflict | Low | Medium | Both use kotlinx-serialization-json; Gradle resolves to highest compatible version |
