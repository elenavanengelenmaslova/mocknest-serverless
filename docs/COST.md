# MockNest Serverless - Cost Guide

MockNest Serverless is designed to operate within [AWS Free Tier](https://aws.amazon.com/free/) limits for typical development and testing scenarios. You pay only for the AWS resources you use in your own account.

## AWS Services Used

### Core Services (Always Deployed)

**[AWS Lambda](https://aws.amazon.com/lambda/pricing/)**
- **2 Lambda functions**: Runtime (mock serving) and Generation (AI features)
- **Memory**: [1024 MB default](https://docs.aws.amazon.com/lambda/latest/dg/configuration-memory.html) (configurable 512-10240 MB)
- **Timeout**: [120 seconds default](https://docs.aws.amazon.com/lambda/latest/dg/configuration-timeout.html) (configurable 3-900 seconds)
- **Concurrency**: Auto-scales within AWS account limits - designed for cost efficiency
- **Architecture**: [ARM64](https://docs.aws.amazon.com/lambda/latest/dg/foundation-arch.html) for 20% cost reduction (SnapStart optimization planned)
- **Java Runtime**: Java 25 with JVM optimizations for cold start performance

**[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)**
- **Type**: [REST API default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-basic-concept.html) (not HTTP API) for full WireMock compatibility
- **Authentication**: API key-based access control
- **Throttling**: [100 requests/second, 200 burst limit default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html)
- **Caching**: [Disabled default](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-caching.html) (to minimize costs)

**[Amazon S3](https://aws.amazon.com/s3/pricing/)**
- **Storage**: Mock definitions, response payloads, and metadata
- **Versioning**: [Enabled default](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) with [30-day lifecycle for old versions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-configuration-examples.html)
- **Access**: Private bucket with lifecycle rules for cleanup
- **Encryption**: [Server-side encryption at rest default](https://docs.aws.amazon.com/AmazonS3/latest/userguide/default-encryption-faq.html) (included)

**[Amazon SQS](https://aws.amazon.com/sqs/pricing/)**
- **Dead Letter Queue**: For failed Lambda invocations only
- **Retention**: [14 days message retention default](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html)
- **Usage**: Only receives messages on Lambda failures (minimal cost, not a core service)

**[Amazon CloudWatch](https://aws.amazon.com/cloudwatch/pricing/)**
- **Log Groups**: 2 log groups (Runtime and Generation functions)
- **Retention**: [7 days log retention default](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html)
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

## AWS Free Tier Alignment

MockNest Serverless is specifically architected to maximize AWS Free Tier usage:

### Free Tier Limits (Monthly)

MockNest Serverless is specifically architected to maximize [AWS Free Tier](https://aws.amazon.com/free/) usage. The free tier includes generous limits for all core services used by MockNest:

- **[AWS Lambda Free Tier](https://aws.amazon.com/lambda/pricing/)**: 1M requests and 400,000 GB-seconds of compute time monthly
- **[Amazon API Gateway Free Tier](https://aws.amazon.com/api-gateway/pricing/)**: 1M API calls monthly  
- **[Amazon S3 Free Tier](https://aws.amazon.com/s3/pricing/)**: 5 GB storage, 20,000 GET requests, and 2,000 PUT requests monthly
- **[Amazon SQS Free Tier](https://aws.amazon.com/sqs/pricing/)**: 1M requests monthly
- **[Amazon CloudWatch Free Tier](https://aws.amazon.com/cloudwatch/pricing/)**: 5 GB log ingestion monthly
- **[Amazon Bedrock Free Tier](https://aws.amazon.com/bedrock/pricing/)**: Pay-per-use only when calling AI endpoints (no base cost)

**Typical Development Usage**: Most development and testing scenarios stay well within these free tier limits.

## Cost Optimization Features

MockNest includes several built-in cost optimization features:

- **API Gateway throttling** prevents unexpected scaling costs
- **Lifecycle policies** automatically clean up old S3 object versions  
- **JVM optimizations** reduce Lambda cold start costs
- **Response externalization** keeps payloads in S3, not Lambda memory
- **ARM64 architecture** provides 20% Lambda cost reduction
- **SnapStart optimization** planned for improved performance and cost efficiency

## Cost Monitoring

**Recommended Tools**:
- **[AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/)** - Track actual usage against free tier limits
- **[CloudWatch billing alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/monitor_estimated_charges_with_cloudwatch.html)** - Set up alerts for unexpected costs
- **Resource tagging** - All resources tagged with `Application: MockNest-Serverless`

## Typical Cost Scenarios

### Development Team (5 developers)

**Monthly Usage Estimate**:
- API calls and Lambda invocations stay well within free tier limits
- S3 storage typically under 100 MB
- CloudWatch logs under 500 MB

**Expected Cost**: $0.00 (within free tier)

### CI/CD Integration Testing

**Monthly Usage Estimate**:
- Higher API call volume but still within free tier limits
- Moderate S3 storage usage
- Standard CloudWatch logging

**Expected Cost**: $0.00 (within free tier)

### Heavy AI Usage (100 AI generations/month)

**Monthly Usage Estimate**:
- Core services remain within free tier ($0.00)
- Bedrock usage varies based on specification complexity

**Total**: Bedrock costs only, core infrastructure free

### Production-Scale Testing (exceeding free tier)

## Usage Scenarios

**Typical Development**: Most development and testing scenarios stay within AWS Free Tier limits, resulting in $0 monthly cost.

**Heavy AI Usage**: Core infrastructure remains free, with costs only for Amazon Bedrock when generating mocks.

**Production-Scale Testing**: Costs scale predictably with usage beyond free tier limits, typically under $5/month.

## Getting Help with Costs

**AWS Resources**:
- [AWS Pricing Calculator](https://calculator.aws/) - Estimate costs for your usage
- [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/) - Monitor actual usage
- [AWS Free Tier Dashboard](https://console.aws.amazon.com/billing/home#/freetier) - Track free tier usage

**MockNest Resources**:
- [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues) - Report cost-related questions