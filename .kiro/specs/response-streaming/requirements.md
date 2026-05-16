# Requirements Document

## Introduction

MockNest Serverless currently uses buffered Lambda invocations via `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`, which imposes a 6MB response payload limit from API Gateway REST API. This feature adds response streaming support by switching to `RequestStreamHandler` with the API Gateway streaming protocol, enabling responses up to 200MB and supporting Server-Sent Events (SSE) mock simulation with chunked delivery and configurable delays.

The streaming protocol writes a metadata JSON block (status code and headers), followed by an 8 null byte delimiter, followed by the response payload. For normal mocks the payload is written at once; for SSE/streaming mocks (configured via WireMock's `chunkedDribbleDelay`), the payload is split into chunks with delays between writes.

## Glossary

- **Runtime_Lambda**: The AWS Lambda function that serves mock HTTP responses via WireMock, currently implemented as `RuntimeLambdaHandler`
- **Generation_Lambda**: The AWS Lambda function that handles AI-powered mock generation requests, currently implemented as `GenerationLambdaHandler`
- **Streaming_Protocol**: The API Gateway response streaming format consisting of metadata JSON, 8 null byte delimiter, and payload data, using content type `application/vnd.awslambda.http-integration-response`
- **Metadata_Block**: A JSON object containing `statusCode` (integer) and `headers` (map of string to string) written as the first segment of the streaming response
- **Null_Byte_Delimiter**: A sequence of exactly 8 null bytes (`\u0000`) separating the metadata block from the response payload in the streaming protocol
- **SSE_Mock**: A mock configured with `chunkedDribbleDelay` in its WireMock stub definition, indicating the response body should be delivered in chunks with delays to simulate Server-Sent Events streaming behavior
- **Chunked_Dribble_Delay**: A WireMock response definition feature specifying `numberOfChunks` and `totalDuration` (in milliseconds) to split the response body into equal-sized chunks delivered over the specified duration
- **Transfer_Mode**: The API Gateway integration configuration property set to `STREAM` that enables Lambda response streaming on a route
- **RequestStreamHandler**: The AWS Lambda Java interface (`com.amazonaws.services.lambda.runtime.RequestStreamHandler`) that provides raw `InputStream` and `OutputStream` for request/response handling
- **SAM_Template**: The AWS SAM `template.yaml` file defining all Lambda functions, API Gateway configuration, and associated resources

## Requirements

### Requirement 1: Runtime Lambda Handler Streaming Refactor

**User Story:** As a developer using MockNest Serverless, I want the Runtime Lambda to use response streaming, so that mock responses are no longer limited to 6MB.

#### Acceptance Criteria

1. THE Runtime_Lambda SHALL implement `RequestStreamHandler` instead of `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`
2. WHEN a request is received, THE Runtime_Lambda SHALL parse the raw `InputStream` into an HTTP request object containing method, path, headers, query parameters, and body
3. WHEN producing a response, THE Runtime_Lambda SHALL write the Metadata_Block as the first segment of the output stream, where the Metadata_Block is a JSON object containing `statusCode` (integer), `headers` (map of header name to single value), and optionally `cookies` (array of set-cookie strings)
4. WHEN producing a response, THE Runtime_Lambda SHALL write the Null_Byte_Delimiter (8 null bytes) immediately after the Metadata_Block
5. THE Runtime_Lambda SHALL write the Null_Byte_Delimiter within the first 16KB of stream data by ensuring the serialized Metadata_Block does not exceed 16,368 bytes
6. WHEN producing a response, THE Runtime_Lambda SHALL set the output stream content type to `application/vnd.awslambda.http-integration-response`
7. THE Runtime_Lambda SHALL preserve existing request routing logic: health check requests to `/__admin/health`, admin requests with the `/__admin/` prefix, client mock requests with the `/mocknest/` prefix, and a 404 response for unrecognized paths
8. THE Runtime_Lambda SHALL maintain SnapStart compatibility with priming hooks and CRaC registration
9. IF the raw `InputStream` cannot be parsed into a valid HTTP request object, THEN THE Runtime_Lambda SHALL write a streaming response with a 400 status code in the Metadata_Block and a body indicating the parsing failure reason

### Requirement 2: Generation Lambda Handler Streaming Refactor

**User Story:** As a developer using MockNest Serverless, I want the Generation Lambda to use response streaming, so that AI-generated mock responses exceeding 6MB can be returned.

#### Acceptance Criteria

1. THE Generation_Lambda SHALL implement `RequestStreamHandler` instead of `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`
2. WHEN a request is received, THE Generation_Lambda SHALL parse the raw `InputStream` into an HTTP request object containing method, path, headers, query parameters, and body
3. WHEN producing a response, THE Generation_Lambda SHALL write the Metadata_Block as the first segment of the output stream, where the Metadata_Block is a JSON object containing `statusCode` (integer), `headers` (map of header name to value), and optionally `cookies` (list of set-cookie strings)
4. WHEN producing a response, THE Generation_Lambda SHALL write the Null_Byte_Delimiter (8 null bytes) immediately after the Metadata_Block
5. THE Generation_Lambda SHALL write the Null_Byte_Delimiter within the first 16KB of stream data
6. WHEN producing a response, THE Generation_Lambda SHALL set the output stream content type to `application/vnd.awslambda.http-integration-response`
7. WHEN producing a response, THE Generation_Lambda SHALL write the response body bytes to the output stream immediately after the Null_Byte_Delimiter
8. THE Generation_Lambda SHALL preserve existing request routing logic (health check and AI generation routes)
9. THE Generation_Lambda SHALL maintain SnapStart compatibility with priming hooks
10. IF the raw `InputStream` cannot be parsed into a valid HTTP request object, THEN THE Generation_Lambda SHALL write a streaming response with a 400 status code in the Metadata_Block and an error message body indicating the parsing failure

### Requirement 3: Streaming Protocol Serialization

**User Story:** As a developer, I want a reusable streaming protocol writer, so that both Lambda handlers produce correctly formatted streaming responses without code duplication.

#### Acceptance Criteria

1. THE Streaming_Protocol writer SHALL serialize the Metadata_Block as a UTF-8 encoded JSON object with `statusCode` as an integer in the range 100–599 and `headers` as a map of header name to header value
2. THE Streaming_Protocol writer SHALL write exactly 8 null bytes as the Null_Byte_Delimiter immediately after the last byte of the serialized Metadata_Block JSON
3. THE Streaming_Protocol writer SHALL write the response body bytes after the Null_Byte_Delimiter
4. WHEN the response body is empty, THE Streaming_Protocol writer SHALL write the Metadata_Block and Null_Byte_Delimiter with no payload bytes following
5. THE Streaming_Protocol writer SHALL produce output such that for any HttpResponse with a status code in 100–599, a headers map of string-to-string entries, and a body of 0 to 200MB, deserializing the written stream SHALL yield a byte-identical body and identical status code and header name-value pairs
6. THE Streaming_Protocol writer SHALL produce output where the Null_Byte_Delimiter appears within the first 16KB of stream data
7. IF the serialized Metadata_Block JSON exceeds 16,376 bytes (16KB minus 8 bytes for the delimiter), THEN THE Streaming_Protocol writer SHALL reject the response with an error indicating the metadata size exceeds the streaming protocol limit
8. IF an I/O error occurs while writing to the output stream, THEN THE Streaming_Protocol writer SHALL propagate the exception to the caller without writing partial or corrupted data to the stream

### Requirement 4: SSE Mock Streaming with Chunked Dribble Delay

**User Story:** As a tester, I want to simulate Server-Sent Events with chunked delivery and delays, so that I can test how my application handles streaming responses from external APIs.

#### Acceptance Criteria

1. WHEN a mock response includes a `chunkedDribbleDelay` configuration with `numberOfChunks` >= 2 and `totalDuration` >= 0, THE Runtime_Lambda SHALL split the response body into `numberOfChunks` chunks of equal byte size, with any remainder bytes appended to the last chunk
2. WHEN delivering chunked responses, THE Runtime_Lambda SHALL distribute the total delay evenly between chunks by waiting `totalDuration / numberOfChunks` milliseconds (integer division, discarding remainder) before writing each chunk after the first
3. WHEN delivering chunked responses, THE Runtime_Lambda SHALL flush each chunk to the output stream immediately after writing it
4. WHEN a mock response does not include `chunkedDribbleDelay`, THE Runtime_Lambda SHALL write the full response body at once without delays
5. IF the calculated total delivery time (`totalDuration` milliseconds) exceeds 270000 milliseconds (4 minutes 30 seconds), THEN THE Runtime_Lambda SHALL log a warning indicating the delay configuration may exceed the 5-minute streaming idle timeout
6. WHEN delivering chunked responses, THE Runtime_Lambda SHALL preserve the complete response body content across all chunks without data loss, such that concatenating all chunks produces a byte sequence identical to the original response body
7. IF `chunkedDribbleDelay` is configured with `numberOfChunks` less than 1 or `totalDuration` less than 0, THEN THE Runtime_Lambda SHALL ignore the chunked dribble delay configuration and write the full response body at once without delays
8. IF `numberOfChunks` exceeds the response body size in bytes, THEN THE Runtime_Lambda SHALL deliver one byte per chunk for the available bytes and deliver the remaining chunks as empty writes

### Requirement 5: SAM Template Streaming Configuration

**User Story:** As a DevOps engineer deploying MockNest Serverless, I want the SAM template to configure response streaming on the appropriate routes, so that API Gateway forwards streamed responses to clients.

#### Acceptance Criteria

1. THE SAM_Template SHALL set the API Gateway integration Transfer_Mode to `STREAM` on mock routes (`/mocknest/{proxy+}`) for the Runtime Lambda function events
2. THE SAM_Template SHALL set the API Gateway integration Transfer_Mode to `STREAM` on admin routes (`/__admin/{proxy+}`) for the Runtime Lambda function events
3. THE SAM_Template SHALL set the API Gateway integration Transfer_Mode to `STREAM` on AI generation routes (`/ai/{proxy+}`) for the Generation Lambda function events
4. THE SAM_Template SHALL update the Runtime Lambda Handler property from `nl.vintik.mocknest.infra.aws.runtime.function.RuntimeLambdaHandler` to the new streaming handler class on both `MockNestRuntimeFunction` and `MockNestRuntimeFunctionIam` resources
5. THE SAM_Template SHALL update the Generation Lambda Handler property from `nl.vintik.mocknest.infra.aws.generation.function.GenerationLambdaHandler` to the new streaming handler class on both `MockNestGenerationFunction` and `MockNestGenerationFunctionIam` resources
6. THE SAM_Template SHALL apply identical streaming configuration (Transfer_Mode and Handler updates) to both the `IsApiKeyMode` conditional resources and the `IsIamMode` conditional resources
7. THE SAM_Template SHALL pass validation with `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` returning exit code 0 after all streaming configuration changes are applied

### Requirement 6: Request Input Stream Parsing

**User Story:** As a developer, I want the raw Lambda input stream to be correctly parsed into a structured HTTP request, so that WireMock can match requests as before.

#### Acceptance Criteria

1. THE Runtime_Lambda SHALL parse the API Gateway proxy request JSON from the raw `InputStream` into method, path, headers, query parameters, and body fields, supporting payloads up to 6MB (the Lambda invocation payload limit)
2. WHEN the request body is base64-encoded (indicated by `isBase64Encoded` field being true), THE Runtime_Lambda SHALL decode the body from Base64 before passing it to WireMock
3. WHEN the request body is absent or null, THE Runtime_Lambda SHALL pass a null body to the request routing logic
4. WHEN multi-value headers are present in the `multiValueHeaders` field of the request, THE Runtime_Lambda SHALL merge them into the HttpRequest such that each header name maps to all its associated values for WireMock matching
5. WHEN multi-value query parameters are present in the `multiValueQueryStringParameters` field of the request, THE Runtime_Lambda SHALL merge them into the HttpRequest such that each parameter name maps to all its associated values for WireMock matching
6. THE Runtime_Lambda SHALL produce an HttpRequest with identical method, path, headers, query parameters, and body values to what the previous `APIGatewayProxyRequestEvent` deserialization produced for the same input
7. IF the raw `InputStream` contains malformed JSON or is missing required fields (httpMethod, path), THEN THE Runtime_Lambda SHALL return an HTTP 400 response with an error message indicating the parsing failure

### Requirement 7: Large Payload Response Delivery

**User Story:** As a tester, I want to configure mock responses larger than 6MB, so that I can test how my application handles large API payloads such as file downloads or bulk data exports.

#### Acceptance Criteria

1. WHEN a mock response body exceeds 6MB, THE Runtime_Lambda SHALL deliver the complete response body via the streaming output without truncation
2. THE Runtime_Lambda SHALL support response payloads from 6MB up to 200MB when delivered via streaming
3. WHEN a large response body is stored in S3 as an external file reference, THE Runtime_Lambda SHALL stream the file content from S3 to the output stream using a buffer no larger than 1MB, without loading the entire file into memory at once
4. IF a response payload exceeds 200MB, THEN THE Runtime_Lambda SHALL return an HTTP 502 status with an error message indicating the payload exceeds the maximum supported streaming limit of 200MB
5. IF the S3 file retrieval fails or the connection is interrupted during streaming of a large response, THEN THE Runtime_Lambda SHALL abort the stream and log an error message indicating the S3 key and the nature of the failure

### Requirement 8: Integration Testing for Streaming Behavior

**User Story:** As a developer, I want integration tests that validate streaming responses end-to-end, so that I can be confident the streaming protocol works correctly after deployment.

#### Acceptance Criteria

1. THE integration test suite SHALL include a test that registers a mock with a response body of at least 1KB and fewer than 6MB, invokes the mock endpoint, and verifies the received response body matches the registered body byte-for-byte with the expected HTTP status code
2. THE integration test suite SHALL include a test that registers a mock with a response body of at least 7MB, invokes the mock endpoint, and verifies the received response byte count equals the registered response byte count
3. THE integration test suite SHALL include a test that registers an SSE mock with `chunkedDribbleDelay` configured with at least 3 chunks and a `totalDuration` of at least 3000 milliseconds, invokes the mock endpoint, and verifies the total response delivery time is at least 80% of the configured `totalDuration`
4. THE integration test suite SHALL include a test that registers a mock with a custom status code and at least one custom response header, invokes the mock endpoint, and verifies the client receives the exact status code and custom header values set in the mock definition
5. THE integration test suite SHALL include a test that registers a mock returning HTTP 404 and a test that registers a mock returning HTTP 503, invokes each mock endpoint, and verifies the client receives the configured status code and response body for each
6. WHEN any streaming integration test completes, THE integration test suite SHALL delete all mappings created during that test to avoid affecting subsequent test runs

### Requirement 9: Post-Deploy Validation Tests

**User Story:** As a DevOps engineer, I want post-deploy curl tests that validate streaming works in the deployed AWS environment, so that I can confirm the feature works end-to-end after deployment.

#### Acceptance Criteria

1. THE post-deploy test script SHALL include a test that registers a mock with a response body of at least 7MB, invokes the mock, and verifies the received response byte length equals the registered body byte length
2. THE post-deploy test script SHALL include a test that registers an SSE mock with `chunkedDribbleDelay` configured with at least 3 chunks and a `totalDuration` of at least 2000 milliseconds, invokes the mock, and verifies the total response elapsed time is at least `totalDuration` milliseconds
3. THE post-deploy test script SHALL include a test that registers a mock with a response body under 6MB, invokes the mock, and verifies an HTTP 200 status code and that the response body matches the registered body content
4. THE post-deploy test script SHALL include a test that registers a mock with at least 2 custom response headers, invokes the mock, and verifies each registered custom header name and value appears in the curl response headers
5. WHEN any streaming post-deploy test completes, THE post-deploy test script SHALL delete all mappings created during that test
6. THE post-deploy test script streaming tests SHALL use the same curl commands and mock configurations documented in the USAGE.md SSE streaming examples, ensuring documentation and tests stay in sync

### Requirement 10: Documentation Updates

**User Story:** As a user of MockNest Serverless, I want updated documentation explaining SSE mock capabilities and the removal of the 6MB limit, so that I can take advantage of the new streaming features.

#### Acceptance Criteria

1. THE USAGE.md documentation SHALL include at least one complete SSE mock configuration example with `chunkedDribbleDelay` that contains a curl command to create the mapping, a curl command to call the mock endpoint, and the expected streamed response output
2. THE USAGE.md documentation SHALL include a section explaining that streaming responses via API Gateway support a maximum response size of 200MB, replacing the previous 6MB synchronous payload limit
3. THE README.md "When Not to Use MockNest" section SHALL replace the current "Very large request or response payloads (over 6 MB)" entry with a request-only note: "Very large request payloads (over 6 MB) — Request payloads are limited to 6 MB by Lambda's invocation payload limit. This is rare in typical REST API testing scenarios. Response payloads support up to 200 MB via streaming."
4. THE README.md SHALL list streaming response support and SSE mock simulation in the "Current Features" section as a bullet point
5. THE USAGE.md documentation SHALL explain the 5-minute idle timeout constraint for streaming responses, stating that if no data is sent for 300 seconds the connection is closed, and that the delay between any two consecutive chunks must not exceed 300 seconds
6. THE README.md "Known Limitations and Best Practices" section SHALL include a "Payload Size Limits" subsection explaining: request payloads are limited to 6MB (Lambda invocation limit), response payloads support up to 200MB via streaming, and that most APIs will not approach these limits in typical integration testing scenarios
7. THE `.kiro/steering/structure.md` "Service Limits & Quotas" section SHALL update the Lambda Limits entry from "6MB request/response payload size limit" to reflect the new 6MB request / 200MB response (streaming) limits
