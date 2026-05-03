# Performance Guide

## Lambda Configuration

All Lambda functions are deployed with the following configuration that affects performance:

- **Architecture**: ARM64 (Graviton2) — 20% better price-performance compared to x86_64
- **Runtime**: Java 25 with SnapStart enabled (`ApplyOn: PublishedVersions`) and priming for reduced cold start latency
- **JVM optimization**: `-XX:+TieredCompilation -XX:TieredStopAtLevel=1` — stops JIT compilation after C1 tier, trading peak throughput for faster startup. This is optimal for Lambda's short-lived execution model where functions rarely run long enough to benefit from C2 optimizations.
- **DI framework**: Koin (~1 MB footprint, no annotation processing) — replaced Spring Cloud Function in v0.7.0, reducing the deployment artifact from 83 MB to 63 MB

## Memory Configuration

Runtime and Generation functions default to **1024 MB** and **512 MB** respectively, RuntimeAsync defaults to **256 MB**, all based on AWS Lambda Power Tuner analysis using the `balanced` strategy.

### Runtime Function Results

Tested with 100 mocks loaded in the system. For results with 1000 mocks and scaling guidance, see [Runtime Scaling: 100 vs 1000 Mocks](#runtime-scaling-100-vs-1000-mocks).

| Metric | Value |
|---|---|
| Optimal power | 1024 MB |
| Cost at optimal | $0.000001632 |
| Duration at 1024 MB | 118.89 ms |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAIABAAGAAgADA==;rLwTQ4LH7UKJiO5C+YXsQqtj90I=;Sf2HNRgL2zVSSCQ2zTdZNqsgqzY=) |

### Generation Function Results

| Metric | Value |
|---|---|
| Optimal power | 512 MB |
| Duration at 512 MB | 3.21 ms |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAEAAgAEAAYACAAM;Om0aQYAjVEDYFVBAEoNQQMUgUEA1pUpA;vKEgM2+l6TJvpWkzEzyvM2+l6TMTPC80) |

### RuntimeAsync Function Results

| Metric | Value |
|---|---|
| Optimal power | 256 MB |
| Duration at 256 MB | 107.05 ms (includes outbound HTTP call latency) |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAEAAgAEAAYACAAM;phvWQqHMC0NXgA1D+/DYQtj8BkM3UKdC;lSPFNB6wgDXDmQE2KDkVNsY/eDbJu2g2) |

> At 1024 MB and ~119 ms average duration (with 100 mocks), the Runtime function remains cost-efficient. With 1000 mocks, optimal memory shifts to 1536 MB at ~113 ms and $0.0000023 per invocation — still well within free tier for typical workloads. Generation at 512 MB and ~3.2 ms fits comfortably within the [AWS Lambda free tier](https://aws.amazon.com/lambda/pricing/) (1M requests and 400,000 GB-seconds per month). RuntimeAsync at 256 MB is cost-efficient given its lightweight dispatch workload. For detailed scaling guidance, see [Runtime Scaling: 100 vs 1000 Mocks](#runtime-scaling-100-vs-1000-mocks).

## Tuning Your Deployment

To optimize memory for your workload, use [AWS Lambda Power Tuner](https://serverlessrepo.aws.amazon.com/applications/arn:aws:serverlessrepo:us-east-1:451282441545:applications~aws-lambda-power-tuning):

1. Deploy Power Tuner from AWS Serverless Application Repository
2. Go to Step Functions console and find `powerTuningStateMachine`
3. Start execution with this input (replace placeholders). These payloads invoke Lambda directly, bypassing API Gateway — no authentication headers are needed.

> **Tip**: Use the ranges below to explore lower memory values than the current defaults — you may find a better balance for your workload.

**Runtime Function** (start lower to find your optimum):
```json
{
  "lambdaARN": "arn:aws:lambda:REGION:ACCOUNT_ID:function:STACK_NAME-runtime",
  "powerValues": [256, 512, 1024, 1536, 2048],
  "num": 50,
  "strategy": "balanced",
  "payload": {
    "resource": "/__admin/health",
    "path": "/__admin/health",
    "httpMethod": "GET",
    "headers": {},
    "requestContext": {
      "requestId": "power-tuner-test",
      "stage": "mocks"
    }
  },
  "parallelInvocation": false,
  "onlyColdStarts": false
}
```

**Generation Function** (start lower to find your optimum):
```json
{
  "lambdaARN": "arn:aws:lambda:REGION:ACCOUNT_ID:function:STACK_NAME-generation",
  "powerValues": [256, 512, 1024, 1536, 2048],
  "num": 50,
  "strategy": "balanced",
  "payload": {
    "resource": "/ai/generation/health",
    "path": "/ai/generation/health",
    "httpMethod": "GET",
    "headers": {},
    "requestContext": {
      "requestId": "power-tuner-test",
      "stage": "mocks"
    }
  },
  "parallelInvocation": false,
  "onlyColdStarts": false
}
```

**RuntimeAsync Function** (SQS webhook dispatcher — start low, it does minimal work):

The RuntimeAsync Lambda receives SQS events containing a fully rendered `AsyncEvent`. The payload below calls the MockNest admin health endpoint as the webhook target — replace the URL with your own callback endpoint for a more representative test:

```json
{
  "lambdaARN": "arn:aws:lambda:REGION:ACCOUNT_ID:function:STACK_NAME-runtime-async",
  "powerValues": [256, 512, 1024, 1536, 2048],
  "num": 50,
  "strategy": "balanced",
  "payload": {
    "Records": [
      {
        "messageId": "power-tuner-test-msg",
        "receiptHandle": "power-tuner-receipt",
        "body": "{\"actionType\":\"webhook\",\"url\":\"https://YOUR_API_ID.execute-api.REGION.amazonaws.com/mocks/__admin/health\",\"method\":\"GET\",\"headers\":{},\"body\":null,\"auth\":{\"type\":\"none\"}}",
        "attributes": {
          "ApproximateReceiveCount": "1",
          "SentTimestamp": "1234567890000",
          "SenderId": "AROAEXAMPLE",
          "ApproximateFirstReceiveTimestamp": "1234567890000"
        },
        "messageAttributes": {},
        "md5OfBody": "d41d8cd98f00b204e9800998ecf8427e",
        "eventSource": "aws:sqs",
        "eventSourceARN": "arn:aws:sqs:REGION:ACCOUNT_ID:STACK_NAME-webhook-queue",
        "awsRegion": "REGION"
      }
    ]
  },
  "parallelInvocation": false,
  "onlyColdStarts": false
}
```

4. Review the visualization URL in the output to see cost vs. performance tradeoffs
5. Update your deployment with the optimal values:
```bash
sam deploy --parameter-overrides \
  RuntimeLambdaMemorySize=1024 \
  RuntimeAsyncMemorySize=256
```

## Runtime Scaling: 100 vs 1000 Mocks

To understand how mock count affects runtime performance, we ran [AWS Lambda Power Tuner](https://github.com/alexcasalboni/aws-lambda-power-tuning) against the Runtime function with two different mock sets loaded in S3: 100 mappings and 1000 mappings.

### Test Setup

- **100-mock test**: 100 WireMock stub mappings loaded in S3 (test data: [`docs/perf-test-100-mappings.json`](perf-test-100-mappings.json))
- **1000-mock test**: 1000 WireMock stub mappings loaded in S3 (test data: [`docs/perf-test-1000-mappings-part1.json`](perf-test-1000-mappings-part1.json), [`part2`](perf-test-1000-mappings-part2.json), [`part3`](perf-test-1000-mappings-part3.json))
- **Payload**: `GET /__admin/health` (invoked directly via Lambda, bypassing API Gateway)
- **Strategy**: `balanced` (cost vs. speed)
- **Invocations per power level**: 50

### Results

| Metric | 100 Mocks | 1000 Mocks |
|---|---|---|
| Optimal memory | 1024 MB | 1536 MB |
| Duration at optimal | 118.89 ms | 113.02 ms |
| Cost per invocation | $0.000001632 | $0.000002326 |
| Lambda cost (tuning run) | $0.0019 | $0.0022 |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAIABAAGAAgADA==;rLwTQ4LH7UKJiO5C+YXsQqtj90I=;Sf2HNRgL2zVSSCQ2zTdZNqsgqzY=) | [View chart](https://lambda-power-tuning.show/#AAYACAAM;tAjiQhT16kI75u5C;gREcNoJkVzZSSKQ2) |

### Key Takeaways

- **Latency stays flat**: Average duration is comparable between 100 and 1000 mocks (~119 ms vs ~113 ms). WireMock's in-memory matching engine handles the 10× increase without meaningful latency degradation on warm invocations.
- **Optimal memory shifts up**: The 1000-mock workload benefits from 1536 MB (vs 1024 MB for 100 mocks). More mappings in memory means more CPU time for matching and more heap for the mapping data structures, so the extra vCPU allocation at 1536 MB pays off.
- **Cost increases ~43%**: Per-invocation cost rises from $0.0000016 to $0.0000023 — still well within Lambda free tier for typical development workloads.
- **Cold starts are the real cost**: The health-check durations above are warm invocations. Cold starts (SnapStart restore + first request) are where large mock sets have the most impact, because all mappings are loaded from S3 during restore. See [Measuring Cold Start Times](#measuring-cold-start-times) below for how to measure this.

### Scaling Recommendations

Based on these results, here are practical guidelines for managing mock count:

**Up to ~500 mocks**: The default 1024 MB configuration works well. No tuning needed.

**500–1000 mocks**: Consider increasing `RuntimeLambdaMemorySize` to 1536 MB. Run Power Tuner with your actual workload to confirm.

**1000+ mocks**: Consider splitting into multiple MockNest deployments rather than scaling a single instance. Benefits:

- **Shorter cold starts** — fewer mappings to load from S3 at restore time
- **Lower memory** — each deployment stays in the cost-efficient 1024–1536 MB range
- **Independent scaling** — high-traffic API groups don't compete with low-traffic ones
- **Separate access control** — different API key or IAM policies per deployment

**How to split**: Group APIs logically based on:

1. **Authentication method** — APIs sharing the same auth mode (`API_KEY` vs `IAM`) belong together, since `AuthMode` is a deployment-level setting
2. **Team or domain ownership** — e.g., `mocknest-payment-apis`, `mocknest-user-apis`
3. **Traffic volume** — isolate high-traffic mock sets so they don't inflate cold starts for lighter workloads
4. **Mock count** — keep each deployment under ~500–1000 mappings for optimal performance

```bash
# Example: deploy separate instances for different API groups
sam deploy --stack-name mocknest-payment-apis --parameter-overrides DeploymentName=payment
sam deploy --stack-name mocknest-user-apis --parameter-overrides DeploymentName=users
sam deploy --stack-name mocknest-notification-apis --parameter-overrides DeploymentName=notifications
```

## Measuring Cold Start Times

To measure cold start performance, use CloudWatch Logs Insights:

1. Go to **CloudWatch** → **Logs Insights**
2. Select log group: `/aws/lambda/STACK_NAME-runtime` or `/aws/lambda/STACK_NAME-generation`
3. Run this query:

```
filter @type = "REPORT"
| parse @message /Restore Duration: (?<restore_duration>.*?) ms/
| filter ispresent(restore_duration)
| fields @duration + restore_duration as cold_start_duration
| stats avg(cold_start_duration) as avg_cold_start,
        min(cold_start_duration) as min_cold_start,
        max(cold_start_duration) as max_cold_start,
        count(*) as cold_start_count
```

This shows average, minimum, and maximum SnapStart cold-start durations for cold-start invocations only. For SnapStart, cold-start duration is Restore Duration + Duration.
