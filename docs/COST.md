# MockNest Serverless - Cost Guide

MockNest Serverless uses a serverless, pay-as-you-go architecture — you only pay for the AWS resources you consume in your own account.

## AWS Services Used

### Core Services (Always Deployed)

**[AWS Lambda](https://aws.amazon.com/lambda/pricing/)**
- **3 Lambda functions**: Runtime (mock serving), Generation (AI features), RuntimeAsync (async webhook dispatch)
- **Memory**: Runtime and Generation default to [512 MB](https://docs.aws.amazon.com/lambda/latest/dg/configuration-memory.html) (configurable 512–10240 MB); RuntimeAsync defaults to 256 MB (configurable 128–10240 MB)
- **Timeout**: [30 seconds default](https://docs.aws.amazon.com/lambda/latest/dg/configuration-timeout.html) (configurable 3-900 seconds via `LambdaTimeout` parameter). The synchronous API Gateway timeout (~29 seconds) is the practical constraint for request duration.
- **Concurrency**: Auto-scales within AWS account limits - designed for cost efficiency
- **Architecture**: [ARM64](https://docs.aws.amazon.com/lambda/latest/dg/foundation-arch.html) for 20% cost reduction (SnapStart enabled for reduced cold start latency)
- **Java Runtime**: Java 25 with JVM optimizations for cold start performance

**[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)**
- **Type**: [REST API default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-basic-concept.html) (not HTTP API) for full WireMock compatibility
- **Authentication**: API key-based access control
- **Throttling**: [100 requests/second, 1 burst limit default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html)
- **Caching**: [Disabled default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-caching.html) (to minimize costs)

**[Amazon S3](https://aws.amazon.com/s3/pricing/)**
- **Storage**: Mock definitions, response payloads, and metadata
- **Versioning**: [Enabled default](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) with [30-day lifecycle for old versions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-configuration-examples.html)
- **Access**: Private bucket with lifecycle rules for cleanup
- **Encryption**: S3 applies server-side encryption by default (AWS S3-managed keys) — not explicitly configured in the SAM template, but enabled by AWS S3's default bucket encryption behavior

**[Amazon SQS](https://aws.amazon.com/sqs/pricing/)**
- **Webhook dispatch**: One message per webhook-enabled mock match, consumed by the async Lambda to dispatch the outbound HTTP call
- **Dead letter queues**: For failed webhook dispatch messages and failed Lambda invocations
- **Retention**: [14 days message retention default](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html)
- **Usage**: Webhook queue cost is proportional to webhook usage; DLQ cost is minimal (only on failures)

**[Amazon CloudWatch](https://aws.amazon.com/cloudwatch/pricing/)**
- **Log Groups**: 3 log groups (Runtime, Generation, and RuntimeAsync functions)
- **Retention**: [7 days log retention default](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html) (configurable via `LogRetentionDays` parameter)
- **Metrics**: [Basic Lambda and API Gateway metrics default](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics.html) (included)
- **Alarms**: [None configured default](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html) (to minimize costs)

**[AWS IAM](https://aws.amazon.com/iam/pricing/)**
- **Cost**: Free service
- **Usage**: Least-privilege roles and policies for Lambda functions

### AI Services

**[Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/)**
- **Model**: [Amazon Nova Pro](https://aws.amazon.com/bedrock/nova/) (officially supported)
- **Pricing**: [Pay-per-request pricing](https://aws.amazon.com/bedrock/pricing/) only when calling AI endpoints
- **No base cost**: Only charged when generating mocks
- **Inference Profiles**: Supports cross-region and geo-specific profiles

## Core Services and Free Tier

The following AWS services are used by MockNest. See each service's pricing page for current free tier eligibility and limits:

- **[AWS Lambda](https://aws.amazon.com/lambda/pricing/)**: Compute runtime for serving mocks
- **[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)**: HTTP routing with API key or IAM authentication (configurable via `AuthMode`)
- **[Amazon S3](https://aws.amazon.com/s3/pricing/)**: Persistent storage for mock definitions and response payloads
- **[Amazon SQS](https://aws.amazon.com/sqs/pricing/)**: Webhook dispatch and dead letter queues
- **[Amazon CloudWatch](https://aws.amazon.com/cloudwatch/pricing/)**: Logging and monitoring

## AI Services (Pay-Per-Use)

- **[Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/)**: AI-powered mock generation
  - Pay-as-you-go — you pay nothing for Bedrock if you do not use MockNest's AI generation endpoints
  - See [Amazon Bedrock pricing](https://aws.amazon.com/bedrock/pricing/) for current rates

## Cost Optimization Features

MockNest includes several built-in cost optimization features:

- **API Gateway throttling** prevents unexpected scaling costs
- **Lifecycle policies** automatically clean up old S3 object versions  
- **JVM optimizations** reduce Lambda cold start costs
- **Response externalization** keeps payloads in S3, not Lambda memory
- **ARM64 architecture** provides 20% Lambda cost reduction
- **SnapStart enabled** for improved cold start performance and cost efficiency
- **Power-tuned memory defaults** — default memory sizes (512 MB for Runtime/Generation, 256 MB for RuntimeAsync) were determined using [AWS Lambda Power Tuning](https://github.com/alexcasalboni/aws-lambda-power-tuning) to find the optimal cost/performance balance

## Cost Monitoring

**Recommended Tools**:
- **[AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/)** - Track actual usage against free tier limits
- **[CloudWatch billing alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/monitor_estimated_charges_with_cloudwatch.html)** - Set up alerts for unexpected costs
- **Resource tagging** - All resources tagged with `Application: MockNest-Serverless`

## Typical Cost Scenarios

### Development Team (5 developers)

- API calls and Lambda invocations within typical development patterns
- S3 storage typically under 100 MB
- CloudWatch logs under 500 MB
- Check [AWS Free Tier](https://aws.amazon.com/free/) for current eligibility

### CI/CD Integration Testing

- Higher API call volume from automated test runs
- Monitor usage via [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/)

### AI Mock Generation

- Bedrock costs scale with the number and complexity of generation requests
- See [Amazon Bedrock pricing](https://aws.amazon.com/bedrock/pricing/) for per-token rates
- You pay nothing for Bedrock if you don't call AI generation endpoints

## Estimating and Monitoring Your Costs

MockNest doesn't return cost data in its API, but you can use these AWS tools to understand and track your spending:

- [AWS Pricing Calculator](https://calculator.aws/) — estimate costs before you deploy, based on expected usage of Lambda, API Gateway, S3, and Bedrock
- [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/) — track actual costs after deployment, broken down by service
- [AWS Free Tier Dashboard](https://console.aws.amazon.com/billing/home#/freetier) — monitor your free tier usage and see how close you are to limits
- [AWS Lambda Power Tuning](https://github.com/alexcasalboni/aws-lambda-power-tuning) — optimize your Lambda memory/cost tradeoff by running real invocations and comparing actual cost per invocation across memory configurations
- [CloudWatch billing alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/monitor_estimated_charges_with_cloudwatch.html) — set up alerts so you're notified if costs exceed a threshold

All MockNest resources are tagged with `Application: MockNest-Serverless` for easy filtering in Cost Explorer.

## Summary

- **Core runtime**: Pay-as-you-go. See [AWS Free Tier](https://aws.amazon.com/free/) for eligibility.
- **AI generation**: Pay-as-you-go via [Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/). You pay nothing if you don't use AI endpoints.
- **Monitor costs**: Use [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/) and [AWS Pricing Calculator](https://calculator.aws/).

## Getting Help

- [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues) — report cost-related questions or concerns