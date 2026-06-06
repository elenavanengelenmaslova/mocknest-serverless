# Adding response streaming to a Kotlin Lambda behind API Gateway

### How I tested large streaming HTTP responses from a JVM Lambda

Mocking a typical JSON response is easy. Mocking a large response that behaves like a real download, or a response that arrives slowly like Server-Sent Events, is less straightforward.

That was the problem I hit while working on MockNest Serverless, my open source serverless mock runtime for AWS [8]. MockNest Serverless runs in your own AWS account, exposes WireMock-compatible mock endpoints through API Gateway and AWS Lambda, and stores persistent mock definitions in S3.

MockNest is the real-life example in this article, but the topic is broader: how do you add response streaming to a Kotlin or Java Lambda running on the JVM, and how do you prove that it actually streams?

I needed this because some APIs do not return small JSON documents. They return files, exports, large generated payloads, or responses that arrive progressively.

A good example is Salesforce Bulk API 2.0. Query jobs can return CSV result sets from endpoints such as `/services/data/vXX.X/jobs/query/{queryJobId}/results`. Salesforce also supports locators and parallel result URLs for retrieving larger query result sets [1]. If an application integrates with that kind of API, a mock server should be able to simulate large CSV downloads too.

The old Lambda implementation could not.

It used a buffered Lambda response. That means the full response had to be built before it was returned to API Gateway. API Gateway has a 10 MB payload limit for non-WebSocket APIs [2], but in the Lambda path the stricter limit is usually Lambda itself: synchronous invocation payloads are limited to 6 MB for both request and response [3].

For many endpoints, 6 MB is enough.

For large CSV exports, generated files, or slow streamed responses, it is not.

Response streaming changes that. Lambda response streaming lets a function send bytes as they become available. It improves time-to-first-byte and supports streamed response payloads up to 200 MB instead of the 6 MB buffered response limit [4].

So I added response streaming to a Kotlin Lambda running on the JVM.

I expected the change to be mostly about replacing the handler interface.

It was not.

## Step 1: Move from `RequestHandler` to `RequestStreamHandler`

The usual Java/Kotlin Lambda handler often looks like this:

```kotlin
RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
```

That model is convenient. AWS gives you a request object, and you return a response object.

For response streaming, I moved to `RequestStreamHandler`:

```kotlin
class StreamingLambdaHandler : RequestStreamHandler {
    override fun handleRequest(
        input: InputStream,
        output: OutputStream,
        context: Context
    ) {
        // parse request from input
        // write streamed response to output
    }
}
```

This is the first mental shift.

You are no longer returning an `APIGatewayProxyResponseEvent`. You are writing bytes.

The request still arrives as an API Gateway proxy event, but now it arrives through the raw `InputStream`. That means you need to parse the JSON yourself, including method, path, headers, query parameters, body, and base64 encoding if your API uses it.

In my case, I added a small parser that converts the raw API Gateway event into my own domain-level HTTP request object. That kept the rest of the application code mostly unchanged.

The response side is where things get more interesting.

## Step 2: Write the API Gateway streaming response format

With Node.js, AWS examples often use helpers such as `awslambda.HttpResponseStream.from()`. That helper writes the HTTP metadata framing for you.

On the JVM, I did not have that helper. With `RequestStreamHandler`, I had to write the expected format myself.

For API Gateway response streaming, the Lambda output stream must contain three parts:

1. response metadata as JSON
2. eight null bytes as a delimiter
3. the response body bytes

Conceptually:

```text
{"statusCode":200,"headers":{"Content-Type":"text/csv"}}
<8 null bytes>
body bytes...
```

A simplified Kotlin writer looks like this:

```kotlin
@Serializable
data class ResponseMetadata(
    val statusCode: Int,
    val headers: Map<String, String>
)

private fun writeMetadata(
    output: OutputStream,
    statusCode: Int,
    headers: Map<String, String>
) {
    val metadata = ResponseMetadata(
        statusCode = statusCode,
        headers = headers
    )

    val json = Json.encodeToString(metadata).encodeToByteArray()

    output.write(json)
    output.write(ByteArray(8))
    output.flush()
}
```

You can use your JSON library of choice. In my project, Kotlinx Serialization was the natural fit.

One trap hides in that `Map<String, String>`. HTTP allows a header to appear more than once — `Set-Cookie` is the common case — and a plain string-to-string map silently collapses duplicates to a single value. The streaming metadata format supports a `cookies` array and multi-value headers for exactly this reason, so if your responses can carry repeated headers, model them as `Map<String, List<String>>` (or a dedicated `cookies` field) before you serialize, not after.

After the metadata and delimiter are written, the body can be written progressively:

```kotlin
writeMetadata(
    output = output,
    statusCode = 200,
    headers = mapOf("Content-Type" to "text/csv")
)

sourceInputStream.copyTo(output)
output.flush()
```

This is where JVM streaming feels lower-level than many examples. The Lambda handler is not only producing the body. It is producing the response protocol that API Gateway expects.

## Step 3: Enable streaming in API Gateway

Changing the handler is not enough. API Gateway also has to invoke the Lambda through the streaming path.

API Gateway response streaming is supported for REST APIs with proxy integrations [5]. AWS also has a good Compute Blog walkthrough of the API Gateway side of the feature [7]. Request streaming is not supported, so this helps with large responses, not large request bodies.

MockNest Serverless uses AWS SAM, so the API event needed response streaming enabled:

```yaml
Events:
  MockApi:
    Type: Api
    Properties:
      Path: /{proxy+}
      Method: ANY
      ResponseTransferMode: RESPONSE_STREAM
```

This naming is slightly confusing.

In AWS SAM `Api` events, the value is `RESPONSE_STREAM` [6]. In lower-level API Gateway integration configuration, the response transfer mode is `STREAM` [5].

If you configure the lower-level integration yourself, you also need to make sure API Gateway uses the Lambda response streaming invocation path:

```yaml
Uri: !Sub arn:aws:apigateway:${AWS::Region}:lambda:path/2021-11-15/functions/${FunctionArn}/response-streaming-invocations
```

That `/response-streaming-invocations` suffix matters. It tells API Gateway to use the Lambda streaming invocation API.

This distinction cost me a deploy cycle. `STREAM` and `RESPONSE_STREAM` sound like the same setting, but they belong to different configuration layers.

## Step 4: Stream from the real source

For a demo, you can stream a small in-memory string:

```kotlin
output.write("hello".toByteArray())
output.flush()
Thread.sleep(1000)

output.write(" world".toByteArray())
output.flush()
```

The sleep only makes the two writes observable. It proves that bytes can be flushed in separate chunks, but it does not solve the real memory problem.

If your goal is to return a 50 MB CSV file, this is not enough:

```kotlin
val bytes = file.readBytes()
output.write(bytes)
```

That still loads the full response into memory. You may have response streaming at the Lambda/API Gateway boundary, but your function is still buffering internally.

The better pattern is to stream from the source directly. The source could be S3, a database cursor, a generated CSV writer, or another HTTP response.

For example, with an `InputStream`:

```kotlin
private fun streamToOutput(
    input: InputStream,
    output: OutputStream
) {
    val buffer = ByteArray(1024 * 1024)

    while (true) {
        val read = input.read(buffer)
        if (read == -1) break

        output.write(buffer, 0, read)
        output.flush()
    }
}
```

The exact buffer size depends on your workload. The important part is that memory usage is bounded. A 10 MB response, a 50 MB response, and a 150 MB response should not require the function to hold the whole body in memory.

In MockNest Serverless, some response bodies are stored in S3, so the Lambda streams the S3 object to the API Gateway output stream. In another application, the same idea could apply to generated reports, CSV exports, AI output, or file transformations.

The rule is the same: do not turn the response into a `String` or `ByteArray` unless you are sure it is small enough.

## Step 5: Validate before committing the status code

Streaming changes error handling.

With a buffered response, you can build the whole response first and only then decide whether to return `200`, `404`, or `500`.

With streaming, the metadata comes first.

Once you write this:

```json
{"statusCode":200}
```

followed by the eight null bytes, the client has effectively received a `200` response. If your source fails halfway through the body, you cannot go back and turn that response into a clean `500`.

That means some validation must move earlier.

For an S3-backed response, check that the object exists and check its size before writing the response metadata. For a generated CSV, validate the input parameters before writing headers. For a database-backed response, make sure the query can start before committing the HTTP status.

The general pattern is:

```kotlin
validateSourceBeforeStreaming()

writeMetadata(output, 200, headers)

streamBody(output)
```

This does not solve every possible failure. A network error can still happen halfway through a stream. But it avoids the most avoidable case: returning a successful status before discovering that there is no body to stream.

## Step 6: Flush deliberately

For slow responses or SSE-like behaviour, flushing matters.

Writing to an `OutputStream` does not always mean the client immediately receives the bytes. If you want the client to observe progressive delivery, flush after each chunk:

```kotlin
chunks.forEachIndexed { index, chunk ->
    if (index > 0) {
        delay(delayBetweenChunks)
    }

    output.write(chunk)
    output.flush()
}
```

This is especially important when you are testing whether the endpoint really streams. Without flushing, your application code may look like it writes progressively, while the client still receives data later than expected.

You also need to keep idle timeouts in mind. API Gateway response streams are still subject to idle timeouts. For Regional and private endpoints, the idle timeout is 5 minutes. For edge-optimized endpoints, it is 30 seconds [5].

So if you simulate a slow stream, make sure the gap between chunks stays below the relevant idle timeout.

## Step 7: Test it in layers

Testing response streaming is tricky because different tests prove different things.

A unit test can prove that your writer produces the right bytes. An integration test can prove that your handler can return a large payload. But neither of those proves that the deployed API Gateway endpoint sends bytes progressively to a real HTTP client.

So I tested the feature in three layers.

### Unit and property tests

At the lowest level, I tested the mechanics that should be deterministic:

* the streaming protocol writer
* the API Gateway request parser
* chunk size calculation
* delayed chunk writing
* bounded-buffer streaming
* routing preservation after switching to `RequestStreamHandler`

The most important test was the protocol round-trip. For a range of responses, the writer produced:

1. metadata
2. eight null bytes
3. body bytes

The test then parsed the result back and verified that the status code, headers, and body were preserved byte-for-byte.

I also tested larger bodies at this layer. Not because a unit test proves platform streaming, but because it proves my own writer does not corrupt the stream when the body is larger than the old 6 MB limit.

For streaming from a source, I tested a different property: the output must be byte-identical to the input, and the buffer allocation must stay bounded. In my case, the buffer was 1 MB. That means a large response can grow from 7 MB to 50 MB without requiring a 50 MB in-memory byte array.

Those tests answer the question:

> Does my JVM code write the correct stream without loading the whole response into memory?

They do not answer:

> Does the deployed API actually stream to the client?

That needs a different test.

### Integration tests

The next layer tested the handler and the runtime path more realistically.

For MockNest Serverless, that meant registering a mock, invoking it through the local or test runtime path, and checking the response. For another application, it could mean generating a CSV, reading from S3, or streaming from a database cursor.

I used integration tests to cover both sides of the original limit:

* a normal response below 6 MB
* a large response above 6 MB

That distinction matters. A streaming implementation that only works for small payloads can hide the exact problem you are trying to solve.

For the large payload test, I generated a response larger than 6 MB and verified the received byte length and content. That proves the implementation can carry a response that would not fit in the old buffered Lambda response model.

I also tested delayed delivery. For a response configured to be split into chunks over a duration, the test verified that the total elapsed time was in the expected range. This does not prove real network streaming through API Gateway, but it does prove that the handler writes chunks with delays instead of writing the whole body immediately.

These tests answer the question:

> Can the handler produce large and delayed responses correctly?

They still do not fully answer:

> Does the deployed API Gateway endpoint send the first bytes early?

That final question needs a deployed test.

### Post-deploy tests

The last test runs after deployment against the real API endpoint.

This is the one that proves the infrastructure is wired correctly.

I used two checks.

The first check verifies a payload larger than 6 MB. The test creates a response above the buffered Lambda limit, calls the deployed endpoint, and verifies that the received payload has the expected size. If this fails, either streaming is not enabled correctly or something still buffers through the old limit.

The second check verifies progressive delivery.

The method is simple: call an endpoint that intentionally sends a slow response, measure when the first byte arrives, and compare that with the total response time.

If streaming works, the first byte arrives much earlier than the full response completes.

If something buffers the response, the first byte and the full response arrive at almost the same time.

That measurement is important because a response can be “successful” without being streamed. You might get the correct body eventually, but if it arrives all at once, your endpoint is still buffered from the client’s point of view.

This is also the check that catches a subtler problem: the managed runtime itself may not stream. Switching to `RequestStreamHandler` and setting `RESPONSE_STREAM` are necessary, but they do not guarantee that every layer in between flushes bytes as they are written rather than buffering the whole response first. The only way to know is to measure first-byte timing against a real deployed endpoint. If the bytes still arrive all at once after everything is wired correctly, that is a signal to look at the runtime and integration path, not your handler code.

These post-deploy tests answer the question:

> Does the deployed API behave like a streaming endpoint from the client’s perspective?

That is the test local code cannot replace.

## What changed in MockNest Serverless

The full implementation is open source [8]. The reusable parts were not specific to MockNest:

* switch JVM handlers to `RequestStreamHandler`
* parse the API Gateway request from `InputStream`
* write the streaming response metadata and delimiter
* stream the body through `OutputStream`
* flush chunks for slow delivery
* configure API Gateway/SAM for response streaming
* test the protocol, the handler, and the deployed endpoint separately

That is why I think the pattern is useful beyond this project. MockNest Serverless gave me the real use case, but the mechanics apply to any Java or Kotlin Lambda that needs to return larger or progressively delivered HTTP responses.

## Lessons learned

The first lesson is that response streaming is not just a handler change. The Lambda handler, response protocol, API Gateway integration, infrastructure configuration, and tests all need to agree.

The second lesson is that JVM developers need to be aware of the response format. In the JavaScript examples, AWS helpers hide the metadata and delimiter. With a Kotlin `RequestStreamHandler`, you may need to write that protocol yourself.

The third lesson is that streaming at the platform boundary does not automatically make your code memory-efficient. If you load the full file into memory and then write it to the output stream, you have increased the response limit, but you have not fixed the memory profile.

The fourth lesson is that the HTTP status code is committed early. Validate what you can before writing metadata.

The fifth lesson is that testing has to be layered. Unit tests prove byte correctness. Integration tests prove the handler can produce large and delayed responses. Post-deploy tests prove that the real API endpoint streams progressively.

The sixth lesson is that writing the metadata yourself means owning its edge cases. A string-to-string header map quietly drops repeated headers like `Set-Cookie`. If your responses can carry duplicates, represent them as multi-value headers before serializing.

And finally, naming matters. The `STREAM` and `RESPONSE_STREAM` distinction is not theoretical. It is the kind of small infrastructure detail that can make correct application code look broken after deployment.

## Conclusion

Adding response streaming to a Kotlin Lambda was worth it, but it was not a one-line change.

For JVM Lambdas behind API Gateway, the practical path is:

1. use `RequestStreamHandler`
2. parse the incoming API Gateway event from `InputStream`
3. write response metadata, eight null bytes, and then body bytes to `OutputStream`
4. enable response streaming in API Gateway or SAM
5. stream from the real source instead of buffering into memory
6. flush deliberately
7. test at three levels: protocol, handler, and deployed endpoint

The important part is not only getting past the 6 MB response limit.

The important part is making the whole path behave like streaming.

Do not stop when the code writes to an `OutputStream`.

Stop when a payload larger than 6 MB succeeds, the first byte arrives early, and your Lambda memory usage does not grow with the response size.

The full source is open source on GitHub [8]. If you want to see the handler, the protocol writer, and the layered tests in context, that is the place to start.

## References

[1] Salesforce Bulk API 2.0 and Bulk API Developer Guide
https://resources.docs.salesforce.com/latest/latest/en-us/sfdc/pdf/api_asynch.pdf

[2] Amazon API Gateway quotas
https://docs.aws.amazon.com/general/latest/gr/apigateway.html

[3] AWS Lambda quotas
https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html

[4] AWS Lambda documentation — Response streaming for Lambda functions
https://docs.aws.amazon.com/lambda/latest/dg/configuration-response-streaming.html

[5] API Gateway documentation — Stream the integration response for proxy integrations
https://docs.aws.amazon.com/apigateway/latest/developerguide/response-transfer-mode.html

[6] AWS SAM documentation — Api event source `ResponseTransferMode`
https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/sam-property-function-api.html

[7] AWS Compute Blog — Building responsive APIs with Amazon API Gateway response streaming
https://aws.amazon.com/blogs/compute/building-responsive-apis-with-amazon-api-gateway-response-streaming/

[8] MockNest Serverless repository
https://github.com/elenavanengelenmaslova/mocknest-serverless
