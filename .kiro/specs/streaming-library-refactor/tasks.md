# Implementation Tasks

## Task 1: Add library dependency to core module

- [x] 1.1 Add `api("nl.vintik:aws-lambda-streaming-core:2.0.0")` to `software/infra/aws/core/build.gradle.kts`
- [x] 1.2 Run `./gradlew :software:infra:aws:core:dependencies` and verify the library resolves correctly
- [x] 1.3 Run `./gradlew clean test` and confirm all tests pass (library co-exists with custom code at this point)

## Task 2: Migrate StreamingGenerationLambdaHandler to ResponseWriter

- [x] 2.1 Replace `StreamingProtocolWriter` import and instantiation with `ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)` in `StreamingGenerationLambdaHandler.kt`
- [x] 2.2 Replace `protocolWriter.write(response, output)` calls with `writer.writeResponse(output, metadata, body)` using `ResponseMetadata.fromMultiValue()` for header conversion
- [x] 2.3 Replace the error response block (parse failure) to use `writer.writeResponse()` with the existing `application/json` format and `{"error":"..."}` body
- [x] 2.4 Update `StreamingGenerationLambdaHandlerTest.kt` — replace `StreamingProtocolWriter.NULL_DELIMITER_SIZE` references with `DELIMITER_LEN`
- [x] 2.5 Run `./gradlew clean test` and confirm all tests pass

## Task 3: Migrate StreamingRuntimeLambdaHandler to ResponseWriter

- [x] 3.1 Replace `StreamingProtocolWriter` import and instantiation with `ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)` in `StreamingRuntimeLambdaHandler.kt`
- [x] 3.2 Replace `protocolWriter.write(response, output)` in the standard response path with `writer.writeResponse(output, metadata, body)` using `ResponseMetadata.fromMultiValue()`
- [x] 3.3 Replace `protocolWriter.writeMetadataAndDelimiter(...)` in `writeChunkedResponse()` and `writeS3ChunkedResponse()` with `writer.writeMetadata(output, metadata)` using `ResponseMetadata.fromMultiValue()`
- [x] 3.4 Update `writeErrorResponse()` to use `writer.writeResponse()` with `ResponseMetadata(statusCode, mapOf("Content-Type" to "text/plain"))` and message bytes as body — preserving the text/plain contract
- [x] 3.5 Remove the 200MB body-size check's manual `toByteArray` — integrate the size check before calling `writeResponse`
- [x] 3.6 Update `StreamingRuntimeLambdaHandlerTest.kt` — replace `StreamingProtocolWriter.NULL_DELIMITER_SIZE` references with `DELIMITER_LEN`
- [x] 3.7 Run `./gradlew clean test` and confirm all tests pass

## Task 4: Replace manual buffer loop in S3ResponseStreamer with copy()

- [x] 4.1 Import `nl.vintik.lambda.streaming.copy` in `S3ResponseStreamer.kt`
- [x] 4.2 Replace the manual `while/read/write/flush` loop in `streamToOutput()` with `copy(inputStream, output)`
- [x] 4.3 Remove the `BUFFER_SIZE` companion constant from `S3ResponseStreamer` (library's `BUFFER_SIZE` constant is the canonical 1MB value)
- [x] 4.4 Verify `streamWithConsumer()` remains unchanged (it delegates to `ChunkedResponseWriter`, not the manual loop)
- [x] 4.5 Run `./gradlew clean test` and confirm all tests pass

## Task 5: Delete replaced source files

- [x] 5.1 Delete `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/streaming/StreamingProtocolWriter.kt`
- [x] 5.2 Delete `software/infra/aws/core/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/streaming/MetadataTooLargeException.kt`
- [x] 5.3 Update or delete `software/infra/aws/core/src/test/kotlin/.../StreamingProtocolRoundTripPropertyTest.kt` — if it tests the wire format, convert to use `ResponseWriter`; if redundant with library tests, delete it
- [x] 5.4 Search for any remaining references to `StreamingProtocolWriter` or the local `MetadataTooLargeException` and fix them
- [x] 5.5 Run `./gradlew clean test` and confirm all tests pass

## Task 6: Final verification

- [x] 6.1 Run `./gradlew clean test` and confirm exit code 0
- [x] 6.2 Run `./gradlew koverVerify` and confirm 90%+ coverage threshold is met
- [x] 6.3 Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
- [x] 6.4 Verify no references to deleted classes remain: `grep -r "StreamingProtocolWriter\|nl.vintik.mocknest.infra.aws.core.streaming.MetadataTooLargeException" software/`
