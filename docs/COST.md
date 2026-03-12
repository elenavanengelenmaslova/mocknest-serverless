# MockNest Serverless - Cost Guide

MockNest Serverless is designed to operate within [AWS Free Tier](https://aws.amazon.com/free/) limits for typical development and testing scenarios. You pay only for the AWS resources you use in your own account.

## AWS Services Used

### Core Services (Always Deployed)

**[AWS Lambda](https://aws.amazon.com/lambda/pricing/)**
- **2 Lambda functions**: Runtime (mock serving) and Generation (AI features)
- **Memory**: 1024 MB default (configurable 512-10240 MB)
- **Timeout**: 120 seconds default (configurable 3-900 seconds)
- **Concurrency**: Runtime (10), Generation (5) - optimized for cost efficiency
- **Architecture**: x86_64 (ARM64 optimization planned)
- **Java Runtime**: Java 25 with JVM optimizations for cold start performance

**[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)**
- **Type**: REST API (not HTTP API) for full WireMock compatibility
- **Authentication**: API key-based access control
- **Throttling**: 100 requests/second, 200 burst limit
- **Caching**: Disabled (to minimize costs)

**[Amazon S3](https://aws.amazon.com/s3/pricing/)**
- **Storage**: Mock definitions, response payloads, and metadata
- **Versioning**: Enabled with 30-day lifecycle for old versions
- **Access**: Private bucket with lifecycle rules for cleanup
- **Encryption**: Server-side encryption at rest (included)

**[Amazon SQS](https://aws.amazon.com/sqs/pricing/)**
- **Dead Letter Queue**: For failed Lambda invocations
- **Retention**: 14 days message retention
- **Usage**: Only receives messages on Lambda failures (minimal cost)

**[Amazon CloudWatch](https://aws.amazon.com/cloudwatch/pricing/)**
- **Log Groups**: 2 log groups (Runtime and Generation functions)
- **Retention**: 30 days log retention
- **Metrics**: Basic Lambda and API Gateway metrics (included)
- **Alarms**: None configured (to minimize costs)

**[AWS IAM](https://aws.amazon.com/iam/pricing/)**
- **Cost**: Free service
- **Usage**: Least-privilege roles and policies for Lambda functions

### AI Services

**[Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/)**
- **Model**: Amazon Nova Pro (officially supported)
- **Usage**: Pay-per-request only when calling AI endpoints
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
- **Amazon Bedrock**: Pay-per-use only when calling AI endpoints (no base cost)

**Typical Development Usage**: Most development and testing scenarios stay well within these free tier limits.

## Cost Optimization Features

### Architecture Optimizations

**Lambda Efficiency**
- **Reserved concurrency**: Limits to prevent unexpected scaling costs
- **Memory optimization**: 1024 MB balances performance and cost
- **JVM tuning**: `-XX:+TieredCompilation -XX:TieredStopAtLevel=1` for faster cold starts
- **Planned**: ARM64 architecture for 20% cost reduction
- **Planned**: SnapStart for Java to reduce cold start latency

**Storage Efficiency**
- **Response externalization**: Large payloads stored in S3, not Lambda memory
- **Lifecycle policies**: Automatic cleanup of old S3 object versions
- **Compression**: Efficient JSON storage for mock definitions

**API Gateway Efficiency**
- **No caching**: Avoids caching charges for development/testing use cases
- **Throttling limits**: Prevents runaway costs from excessive requests
- **Regional deployment**: Avoids cross-region data transfer charges

### Cost Monitoring

**Built-in Cost Controls**
- **Concurrency limits**: Prevents unexpected Lambda scaling
- **Timeout limits**: Prevents long-running expensive operations
- **DLQ retention**: 14-day limit prevents indefinite message storage

**Monitoring Recommendations**
- **AWS Cost Explorer**: Track actual usage against free tier limits
- **CloudWatch billing alarms**: Set up alerts for unexpected costs
- **Resource tagging**: All resources tagged with `Application: MockNest-Serverless`

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

**Monthly Usage Estimate**:
- Costs scale predictably with usage beyond free tier limits
- Transparent AWS billing with detailed cost breakdown

**Total**: Minimal incremental costs, typically under $5/month

## Cost Optimization Tips

### Maximize Free Tier Usage

1. **Monitor usage**: Use AWS Cost Explorer to track free tier consumption
2. **Clean up regularly**: Remove unused mock definitions to minimize S3 storage
3. **Batch AI operations**: Generate multiple mocks in single requests
4. **Use namespaces**: Organize mocks efficiently to reduce storage overhead

### Beyond Free Tier

1. **Right-size Lambda memory**: Reduce memory if your mocks are simple
2. **Optimize mock storage**: Use compressed JSON, avoid large embedded payloads
3. **Monitor concurrency**: Adjust reserved concurrency based on actual needs
4. **Regional deployment**: Deploy in region closest to your team

### AI Cost Management

1. **Strategic AI usage**: Use AI for complex specifications, manual creation for simple mocks
2. **Batch generation**: Generate multiple related mocks in single requests
3. **Specification quality**: Well-structured OpenAPI specs generate better results with fewer retries
4. **Model selection**: Amazon Nova Pro provides best balance of quality and cost

## Regional Cost Considerations

**Supported Regions**:
- **us-east-1** (N. Virginia) - Lowest costs, all services available
- **eu-west-1** (Ireland) - EU data residency, slightly higher costs
- **ap-southeast-1** (Singapore) - APAC access, moderate costs

**Cost Variations**:
- **Lambda**: ~10-15% variation between regions
- **API Gateway**: Minimal regional variation
- **S3**: ~5-10% variation between regions
- **Bedrock**: Model availability and pricing varies by region

## Getting Help with Costs

**AWS Resources**:
- [AWS Pricing Calculator](https://calculator.aws/) - Estimate costs for your usage
- [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/) - Monitor actual usage
- [AWS Free Tier Dashboard](https://console.aws.amazon.com/billing/home#/freetier) - Track free tier usage

**MockNest Resources**:
- [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues) - Report cost-related questions
- [Architecture Documentation](.kiro/steering/02-architecture.md) - Technical cost optimization details

## Planned Cost Optimizations

**Short Term**:
- **ARM64 Lambda**: 20% cost reduction for compute
- **SnapStart**: Reduce cold start costs and improve performance
- **S3 Intelligent Tiering**: Automatic cost optimization for infrequently accessed mocks

**Medium Term**:
- **On-demand mapping loading**: Reduce Lambda memory usage for large mock sets
- **Compression improvements**: Better storage efficiency for mock definitions
- **Regional optimization**: Automatic region selection for cost optimization

**Long Term**:
- **Usage analytics**: Built-in cost tracking and optimization recommendations
- **Tiered deployment options**: Different configurations for development vs. production use cases