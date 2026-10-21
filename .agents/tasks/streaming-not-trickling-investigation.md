# Why the streaming/dribble endpoint buffers instead of trickling

## Summary answer (root cause)

The Lambda code is a correct response-streaming handler, but the **API Gateway integration in front of it is NOT configured for response streaming**, so API Gateway invokes the function in buffered mode and releases the whole body in one block at the end. That is exactly what the curl numbers show: `time_starttransfer (4.847164s) ≈ time_total (4.847802s)` — the server-side `chunkedDribbleDelay` of ~5s is applied inside the Lambda, but no bytes reach the client until the Lambda finishes.

Concretely:

- The endpoint `https://sksfacxr61.execute-api.eu-west-1.amazonaws.com/...` is an **API Gateway REST API** (`AWS::Serverless::Api`), not a Lambda Function URL. There is no Function URL / `FunctionUrlConfig` / `AWS::Lambda::Url` resource anywhere in the template.
- To stream through API Gateway REST, the integration must be set to `responseTransferMode: STREAM` so that API Gateway invokes the function with the `InvokeWithResponseStream` Lambda API instead of the buffered `Invoke` API. (Reference: [AWS — Lambda proxy integration with payload response streaming](https://docs.aws.amazon.com/apigateway/latest/developerguide/response-transfer-mode-lambda.html). Content was rephrased for compliance with licensing restrictions.)
- The template *tries* to do this with `ResponseTransferMode: RESPONSE_STREAM` on each `Type: Api` event — but **that property is not supported by SAM's `Api` event source**, so it does not configure the integration. The integration is deployed as a plain buffered Lambda proxy integration. (Reference: [aws-sam-cli issue #8606](https://github.com/aws/aws-sam-cli/issues/8606), an open feature request confirming SAM `Api` events reject streaming properties — SAM returns "property not defined for resource of type Api". Content was rephrased for compliance.)

**Most likely single root cause:** the `ResponseTransferMode: RESPONSE_STREAM` lines in the SAM template are not honored by SAM, so API Gateway never sets `responseTransferMode: STREAM` / never uses `InvokeWithResponseStream`. API Gateway therefore buffers the entire Lambda response.

Confidence: **High** that the handler/code is correct and streaming-capable; **High** that the endpoint is REST API Gateway (not a Function URL); **Medium-High** that the SAM `ResponseTransferMode` property is being ignored at deploy time (verifiable from the SAM issue and SAM docs, but the *actual deployed* integration setting needs one AWS CLI check — see "Unverifiable from the repo alone").

---

## Evidence

### 1. The endpoint is API Gateway REST, and there is no Function URL

`deployment/aws/sam/template.yaml`:

- The runtime function is exposed only through `AWS::Serverless::Api` REST APIs:
  - `MockNestApiKeyModeApi` / `MockNestIamModeApi` — both `Type: AWS::Serverless::Api` (REST API, not HTTP API / ApiGatewayV2).
  - `MockNestRuntimeFunction` (API-key mode) and `MockNestRuntimeFunctionIam` (IAM mode) attach the mock routes via `Events` of `Type: Api`:
    ```
    MockRoutes:
      Type: Api
      Properties:
        RestApiId: !Ref MockNestApiKeyModeApi
        Path: /mocknest/{proxy+}
        Method: ANY
        ResponseTransferMode: RESPONSE_STREAM
    ```
- The stack output builds exactly the host the user is hitting:
  ```
  MockNestApiUrl = https://${ApiId}.execute-api.${AWS::Region}.amazonaws.com/${DeploymentName}/
  ```
  i.e. an `execute-api` host — confirming the user's `sksfacxr61.execute-api.eu-west-1.amazonaws.com/mocks` URL is this REST API.
- A repo-wide search for `FunctionUrl`, `FunctionUrlConfig`, `AWS::Lambda::Url`, `InvokeMode`, `HttpApi`, `ApiGatewayV2` found **no** Function URL / HTTP API resources. The only `RESPONSE_STREAM` occurrences are the six `ResponseTransferMode: RESPONSE_STREAM` lines on the `Api` events; the only `InvokeMode` hits are unrelated Bedrock `InvokeModelWithResponseStream` IAM actions.

### 2. The Lambda handler IS a correct streaming handler

`software/infra/aws/runtime/.../function/StreamingRuntimeLambdaHandler.kt`:

- Implements `RequestStreamHandler` (`handleRequest(input: InputStream, output: OutputStream, context)`), the model required for response streaming.
- Writes the API Gateway streaming wire format via `ResponseWriter(maxPreludeLen = OBSERVED_MAX_PRELUDE_LEN)`: metadata JSON, then the 8-null-byte delimiter, then the payload. This matches AWS's documented streaming output format (metadata JSON + 8 null bytes within the first 16 KB). `OBSERVED_MAX_PRELUDE_LEN` = 16,376 lines up with the 16 KB delimiter window.
- For a captured `chunkedDribbleDelay`, it calls `writeChunkedResponse` / `writeS3ChunkedResponse`, which use `ChunkedResponseWriter` to write a chunk, **`output.flush()`**, `delay(...)`, next chunk — i.e. it genuinely flushes incrementally with coroutine delays.

`software/infra/aws/runtime/.../streaming/ChunkedResponseWriter.kt`:

- `writeChunked` and `writeChunkedFromStream` both loop: optional `delay(delayBetweenChunks)`, `output.write(chunk)`, `output.flush()`. So the Lambda does flush each chunk as it goes.

`software/infra/aws/core/.../streaming/ApiGatewayRequestParser.kt`:

- Parses the standard API Gateway **proxy** request JSON (`httpMethod`, `path`, `headers`, `multiValueHeaders`, `queryStringParameters`, base64 body). This is the proxy input format, consistent with a Lambda proxy integration — which is a prerequisite for REST streaming.

Conclusion for (2): nothing in the Lambda explains buffering. The flush-per-chunk streaming is implemented correctly. If API Gateway were invoking it with `InvokeWithResponseStream`, the chunks would trickle.

### 3. The streaming configuration does not actually reach API Gateway

- AWS requires, for REST streaming: integration `responseTransferMode = STREAM` **and** invocation via `InvokeWithResponseStream`. The relevant AWS support matrix: a Lambda whose output adheres to the streaming format but is invoked with the plain `Invoke` API is explicitly **not** a supported streaming configuration; buffered `Invoke` with a proxy integration is the normal buffered path. (Reference: [AWS response-transfer-mode-lambda doc](https://docs.aws.amazon.com/apigateway/latest/developerguide/response-transfer-mode-lambda.html). Rephrased for compliance.)
- The SAM template expresses the intent with `ResponseTransferMode: RESPONSE_STREAM` on the `Api` events. But:
  - The SAM `Api` event source type has a fixed set of supported properties (RestApiId, Path, Method, Auth, RequestModel, RequestParameters, etc.). A streaming/transfer-mode property is **not** among them. SAM CLI rejects the analogous `InvokeMode` with "property … not defined for resource of type Api" ([issue #8606](https://github.com/aws/aws-sam-cli/issues/8606)).
  - That issue is an **open feature request** (SAM CLI 1.144.0, early 2026) precisely asking AWS to add streaming support to SAM `Api` events — strong evidence it was not available when this template was authored. Even the property *name* used in the template (`ResponseTransferMode`) differs from the API Gateway integration property (`responseTransferMode: STREAM`) and the SDK/CDK enum (`ResponseTransferMode.STREAM`); `RESPONSE_STREAM` is the Function-URL `InvokeMode` value, not the REST integration value.
- Net effect: whether SAM silently drops the unknown property or it is cosmetically present, the deployed REST integration is a standard buffered Lambda proxy integration. API Gateway waits for the full Lambda response, then sends it — producing `starttransfer ≈ total`.

### 4. The project's own validation never actually tested trickling — so this shipped undetected

The zero-memory-streaming spec (`.kiro/specs/zero-memory-streaming/requirements.md`) Requirement 6 demanded a post-deploy test that curls with `--no-buffer` and **fails if the response is not delivered progressively** (6.2) / arrives entirely within the first 10% of the duration (6.4).

But the implemented test `scripts/post-deploy-test.sh :: test_streaming_sse_chunked_delay` only asserts:
```
elapsed_ms (time_total) >= expected_min_ms   # (chunks-1)*(duration/chunks)*0.9
```
It measures **total** time only (`--write-out "%{time_total}"`) and never compares `time_starttransfer` to `time_total`. A fully buffered response that is simply delayed ~5s server-side passes this check. So the acceptance criterion that would have caught "no trickle" (TTFB vs total) was never implemented, and the gap went green in CI.

The CHANGELOG reinforces the mistaken premise: `[0.9.0]` claims "Lambda response streaming **via API Gateway REST API**" and "Added `ResponseTransferMode: RESPONSE_STREAM` on all API Gateway routes" — i.e. the design assumed the SAM property works.

---

## Which of the four candidate causes applies

Per the brief's taxonomy:

- (a) "Lambda is exposed via API Gateway so streaming is impossible end-to-end" — **Partially true historically, but no longer the blocker.** API Gateway REST *can* stream as of late 2025 with `responseTransferMode: STREAM` + `InvokeWithResponseStream`. So being behind REST API Gateway is not fatal by itself.
- (b) "A Function URL exists but InvokeMode RESPONSE_STREAM not set" — **N/A.** There is no Function URL.
- (c) "The handler is not registered/packaged as a streaming handler" — **No.** The handler correctly implements `RequestStreamHandler` and emits the streaming wire format.
- (d) "Deployed version predates the streaming work" — possible but not the core issue; the streaming code is present (0.9.0). Cannot confirm what version is behind `sksfacxr61` from the repo.

**Actual cause (a new variant):** The function is a correct streaming handler behind a REST API, but the **API Gateway integration is not set to `responseTransferMode: STREAM`**, because SAM's `Api` event source does not support the `ResponseTransferMode` property the template uses. API Gateway falls back to buffered `Invoke`.

---

## Recommended fix

Goal: make API Gateway invoke the runtime function with `InvokeWithResponseStream` by setting the integration's `responseTransferMode` to `STREAM`.

Because SAM's `Api` event does not expose this, options (in order of preference):

1. **Keep the REST API but configure the integration explicitly.** Define the REST API via an inline/OpenAPI body (`DefinitionBody`) where the `x-amazon-apigateway-integration` for the `/mocknest/{proxy+}` (and `/__admin/{proxy+}`, `/ai/{proxy+}`) methods sets the streaming integration URI/`responseTransferMode: STREAM`, rather than relying on the unsupported SAM event property. This preserves the existing `execute-api` URL and the existing `x-api-key` auth / usage plan. This is the smallest behavioral change for the user: same host, same API key, but responses trickle.
   - Verify SAM/CloudFormation actually renders the integration with streaming; after deploy, confirm with `aws apigateway get-integration` that `responseTransferMode` (or equivalent) is `STREAM`.

2. **Add a Lambda Function URL with response streaming** (`AWS::Lambda::Url` with `InvokeMode: RESPONSE_STREAM`, or SAM `FunctionUrlConfig: { InvokeMode: RESPONSE_STREAM }`). Then the streaming demo must target the **Function URL host**, which looks like `https://<id>.lambda-url.eu-west-1.on.aws/...` — **not** an `execute-api` host.
   - **Auth caveat (design consideration only, do not solve here):** today API Gateway performs `x-api-key` authentication and throttling. A Function URL does not support API keys — it offers only `AuthType: NONE` or `AWS_IAM` (SigV4). Switching the demo to a Function URL would drop the `x-api-key` model and change the client's auth (and lose the usage-plan throttling). Flag this before choosing option 2.

Regardless of option chosen:

3. **Fix the post-deploy test to actually assert trickling** (satisfy the never-implemented Requirement 6.2/6.4): capture both `time_starttransfer` and `time_total` with `--no-buffer`, and fail if `time_starttransfer` is not meaningfully smaller than `time_total` (e.g. first byte should arrive well before the configured total duration elapses). Without this, a buffered regression will keep passing.

---

## Unverifiable from the repo alone (needs the live AWS stack)

The repo shows intent and code; it cannot show the *deployed* integration's actual settings. To confirm the diagnosis against the stack behind `sksfacxr61`, the user would need to check AWS directly:

- Confirm there is no Function URL: `aws lambda get-function-url-config --function-name mocks-runtime` (expect: not found).
- Inspect the REST integration transfer mode:
  - `aws apigateway get-rest-apis` → find API id `sksfacxr61`.
  - `aws apigateway get-resources --rest-api-id sksfacxr61` → resource id for `/mocknest/{proxy+}`.
  - `aws apigateway get-integration --rest-api-id sksfacxr61 --resource-id <id> --http-method ANY` → check whether `responseTransferMode` is `STREAM` (diagnosis predicts it is absent/`BUFFERED` and the integration URI uses `.../functions/.../invocations`, not `.../response-streaming-invocations`).
- Confirm which code version is deployed (CHANGELOG/git tag vs the Lambda's published version) to rule out candidate (d).

These AWS checks are outside this read-only repo investigation.
