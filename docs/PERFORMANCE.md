# Performance Guide

## Memory Configuration

Both Runtime and Generation functions default to **512 MB** based on AWS Lambda Power Tuner analysis using the `balanced` strategy.

### Runtime Function Results

| Metric | Value |
|---|---|
| Optimal power | 512 MB |
| Duration at 512 MB | 3.30 ms |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAIABAAGAAgADA==;Hz5TQIlBWEB56W5AfM1VQIAjVEA=;b6XpMm+laTMYC9szb6XpMxM8LzQ=) |

### Generation Function Results

| Metric | Value |
|---|---|
| Optimal power | 512 MB |
| Duration at 512 MB | 3.21 ms |
| Visualization | [View chart](https://lambda-power-tuning.show/#AAIABAAGAAgADA==;309NQMI8UUAZvVVAjlBWQD98TkA=;b6XpMm+laTMTPK8zb6XpMxM8LzQ=) |

> At 512 MB and ~3.3 ms average duration, both functions fit comfortably within the [AWS Lambda free tier](https://aws.amazon.com/lambda/pricing/) (1M requests and 400,000 GB-seconds per month).

## Tuning Your Deployment

To optimize memory for your workload, use [AWS Lambda Power Tuner](https://serverlessrepo.aws.amazon.com/applications/arn:aws:serverlessrepo:us-east-1:451282441545:applications~aws-lambda-power-tuning):

1. Deploy Power Tuner from AWS Serverless Application Repository
2. Go to Step Functions console and find `powerTuningStateMachine`
3. Start execution with this input (replace placeholders). These payloads invoke Lambda directly, bypassing API Gateway — no authentication headers are needed:

**Runtime Function:**
```json
{
  "lambdaARN": "arn:aws:lambda:REGION:ACCOUNT_ID:function:STACK_NAME-runtime",
  "powerValues": [512, 1024, 1536, 2048, 3072],
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

**Generation Function:**
```json
{
  "lambdaARN": "arn:aws:lambda:REGION:ACCOUNT_ID:function:STACK_NAME-generation",
  "powerValues": [512, 1024, 1536, 2048, 3072],
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

4. Review the visualization URL in the output to see cost vs. performance tradeoffs
5. Update your deployment: `sam deploy --parameter-overrides LambdaMemorySize=512`

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
