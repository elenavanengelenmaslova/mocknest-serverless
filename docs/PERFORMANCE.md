# Performance Guide

## Memory Configuration

Runtime and Generation functions default to **1024 MB** and **512 MB** respectively, RuntimeAsync defaults to **256 MB**, all based on AWS Lambda Power Tuner analysis using the `balanced` strategy.

### Runtime Function Results

Tested with 100 mocks loaded in the system.

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

> At 1024 MB and ~119 ms average duration (with 100 mocks in the system), the Runtime function remains cost-efficient. Generation at 512 MB and ~3.2 ms fits comfortably within the [AWS Lambda free tier](https://aws.amazon.com/lambda/pricing/) (1M requests and 400,000 GB-seconds per month). RuntimeAsync at 256 MB is cost-efficient given its lightweight dispatch workload.

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
