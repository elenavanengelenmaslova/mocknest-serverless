# MockNest Serverless

MockNest Serverless is a serverless WireMock compatible runtime for AWS that enables realistic integration testing without relying on live external services, with AI-powered mock generation using Amazon Bedrock. It runs natively on AWS Lambda and persists mock definitions in Amazon S3, making mocks available across cold starts and deployments.

<p align="center">
  <img src="docs/images/MockNestServerlessLogo.png" alt="MockNest Serverless Logo" width="400">
</p>

## Architecture Overview

<div style="text-align: center;">
  <img src="docs/images/SolutionDesign.png" alt="MockNest Serverless Architecture" width="600">
</div>

MockNest Serverless consists of AWS Lambda functions that serve both the WireMock admin API and mocked endpoints, with persistent storage in Amazon S3. AI features use Amazon Bedrock for intelligent mock generation when called.

## Features

- **Serverless WireMock Runtime**: Full WireMock API running on AWS Lambda
- **Persistent Mock Storage**: Mock definitions stored in Amazon S3
- **Protocol Support**: REST, SOAP, and GraphQL-over-HTTP APIs
- **AI-Assisted Mock Generation**: Intelligent mock creation from API specifications using Amazon Nova Pro
- **AWS Free Tier Compatible**: Designed to operate within AWS Free Tier limits for typical development and testing scenarios
- **Cost Optimized**: Architecture optimized for minimal AWS costs with planned ARM64 and SnapStart support
- **Easy Deployment**: One-click deployment via AWS Serverless Application Repository (SAR)

## Quick Start for SAR Users

**Recommended Path**: Deploy MockNest Serverless directly from the AWS Serverless Application Repository for the easiest setup experience.

### Prerequisites

- AWS account with appropriate permissions
- Access to AWS Console

### Deployment from AWS Serverless Application Repository

1. **Navigate to SAR**: Go to the [AWS Serverless Application Repository](https://console.aws.amazon.com/serverlessrepo/home) in your AWS Console
2. **Select Region**: Choose your preferred deployment region (us-east-1, eu-west-1, or ap-southeast-1 recommended)
3. **Search**: Search for "MockNest-Serverless"
4. **Deploy**: Click "Deploy" and configure parameters:
   - **DeploymentName**: Unique identifier for your deployment (default: "mocks")
   - **BedrockModelName**: AI model for mock generation (default: "AmazonNovaPro")
   - **BedrockInferenceMode**: Inference profile selection (default: "AUTO" - recommended)
   - **LambdaMemorySize**: Memory allocation in MB (default: 1024)
   - **LambdaTimeout**: Function timeout in seconds (default: 120)

### Getting Started After Deployment

**Quick Start with Postman**: Import our ready-to-use Postman collections from [`docs/postman/`](docs/postman/) for instant access to all API endpoints with working examples.

**Manual Setup**: Follow the [SAR User Guide](README-SAR.md) for step-by-step instructions using cURL.

#### Region Selection and Model Availability

**Choose Your Deployment Region**: When deploying from SAR, you select the deployment region in the AWS Console. MockNest automatically configures itself for that region.

**Bedrock Model Availability**: Amazon Bedrock model availability varies by region. Before deploying with AI features:

1. **Check Model Availability**: Verify Amazon Nova Pro is available in your chosen region using the [AWS Bedrock model availability documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-regions.html)
2. **Enable Model Access**: In the Amazon Bedrock console, go to "Model access" and enable access for Amazon Nova Pro
3. **Wait for Approval**: Model access requests may take a few minutes to be approved

#### BedrockInferenceMode Configuration

The `BedrockInferenceMode` parameter controls how MockNest selects Bedrock inference profiles:

- **AUTO** (recommended): Tries cross-region inference profile first, then falls back to region-specific profile
  - **Best for**: Most users who want automatic optimization
  - **Behavior**: Maximizes model availability and performance
  
- **GLOBAL_ONLY**: Forces use of cross-region inference profile only
  - **Best for**: Users who need consistent global model behavior
  - **Behavior**: Uses shared cross-region capacity (may have higher latency but better availability)
  
- **GEO_ONLY**: Forces use of region-specific inference profile only
  - **Best for**: Users with data residency requirements
  - **Behavior**: Uses geo-specific profile (e.g., "eu" for eu-west-1, "us" for us-east-1)

**Recommendation**: Use AUTO mode for most use cases as it provides the best balance of availability and performance.

#### When to Use Each Mode

**Use GLOBAL_ONLY when**:
- You need consistent model behavior across all regions
- Your application requires the latest model capabilities
- Data residency is not a concern (uses cross-region capacity)

**Use GEO_ONLY when**:
- You have strict data residency requirements
- You want to ensure data stays within a specific geographic region
- Compliance requires regional data processing

**Use AUTO when** (recommended):
- You want the best availability and performance
- You're not sure which mode to choose
- You want automatic fallback behavior

#### Support and Troubleshooting

**Getting Help**:
- **Issues**: Report problems via [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues)
- **Documentation**: See the [SAR User Guide](README-SAR.md) for detailed deployment and usage instructions
- **API Reference**: Complete API documentation in the [OpenAPI specification](docs/api/mocknest-openapi.yaml)

**Common Deployment Issues**:
- **Bedrock Access Denied**: Ensure model access is enabled in Amazon Bedrock console
- **Region Not Supported**: Verify Amazon Bedrock is available in your deployment region
- **CloudFormation Failures**: Check CloudFormation events for detailed error messages

## Tested Configuration

MockNest Serverless has been thoroughly tested in the following configurations:

### Officially Supported Regions
- **us-east-1** (N. Virginia)
- **eu-west-1** (Ireland) 
- **ap-southeast-1** (Singapore)

### Core Runtime Compatibility
- **Works in any AWS region** with Lambda, API Gateway, and S3 support
- **Deployment to other regions** is possible but not officially supported

### AI Features Support
- **Officially supported**: Amazon Nova Pro model in the three tested regions above
- **Other Bedrock models**: May work but are experimental and not officially supported
- **Other regions**: AI features may work but are not officially tested

### Tested WireMock Features
The following WireMock capabilities have been validated in the serverless environment:
- Request matching (URL, headers, body, query parameters)
- Response templating and transformation
- JSON and XML body matching
- Stateful behavior and scenarios
- Request verification and admin API
- File serving for response bodies
- Callback and webhook simulation

**Note**: MockNest does not claim support for WireMock features that have not been explicitly tested in serverless environments.

## Usage

### Basic Mock Management

Once deployed, MockNest Serverless exposes the standard WireMock admin API:

```bash
# Get your API Gateway endpoint from SAM output
export MOCKNEST_URL="https://your-api-id.execute-api.eu-west-1.amazonaws.com/prod"
export API_KEY="your-api-key"

# Create a mock for Petstore API
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/123"
    },
    "response": {
      "status": 200,
      "headers": {
        "Content-Type": "application/json"
      },
      "jsonBody": {
        "id": 123,
        "name": "Buddy",
        "status": "available",
        "photoUrls": ["https://example.com/buddy.jpg"],
        "category": {"id": 1, "name": "dog"},
        "tags": [{"id": 1, "name": "friendly"}, {"id": 2, "name": "new"}]
      }
    }
  }'

# Test the mock
curl "$MOCKNEST_URL/petstore/pet/123" \
  -H "x-api-key: $API_KEY"
```

### AI-Assisted Mock Generation

MockNest provides intelligent mock generation capabilities using Amazon Bedrock:

#### Generate from API Specification with Description

Generate WireMock mappings from an OpenAPI or Swagger specification enhanced with natural language instructions.

```bash
# Generate mocks from OpenAPI specification + natural language description
curl -X POST "$MOCKNEST_URL/ai/generation/from-spec" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {
      "apiName": "petstore",
      "client": "demo"
    },
    "specification": "openapi: 3.0.0\ninfo:\n  title: Pet Store API\n  version: 1.0.0\npaths:\n  /pets:\n    get:\n      responses:\n        \"200\":\n          description: Success",
    "format": "OPENAPI_3",
    "description": "Generate 5 realistic pets, include error cases for invalid IDs",
    "options": {
      "enableValidation": true
    }
  }'
```

The response contains an array of generated WireMock mappings:

```json
{
  "mappings": [
    {
      "request": {
        "method": "GET",
        "urlPath": "/petstore/pet/findByStatus",
        "queryParameters": {
          "status": {"equalTo": "available"}
        }
      },
      "response": {
        "status": 200,
        "jsonBody": [
          {
            "id": 1,
            "name": "Buddy",
            "status": "available",
            "photoUrls": ["https://example.com/buddy.jpg"],
            "category": {"id": 1, "name": "dog"},
            "tags": [{"id": 1, "name": "friendly"}, {"id": 2, "name": "new"}]
          },
          {
            "id": 2,
            "name": "Max",
            "status": "available",
            "photoUrls": ["https://example.com/max.jpg"],
            "category": {"id": 1, "name": "dog"},
            "tags": [{"id": 1, "name": "friendly"}]
          }
        ],
        "headers": {
          "Content-Type": "application/json"
        }
      }
    }
  ]
}
```

Mocks are automatically organized using namespaces (e.g., `/petstore/`). You can then import these mocks using the standard WireMock `/__admin/mappings/import` endpoint.

#### Generate from Natural Language

```bash
# Generate mocks from description
curl -X POST "$MOCKNEST_URL/ai/generation/from-description" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {
      "apiName": "petstore",
      "client": "demo"
    },
    "description": "Create a REST API for managing pets with endpoints to get all pets, get pet by ID, create new pet, update pet, and delete pet. Include proper error responses for not found (404) and validation errors (400). Use realistic pet data with dogs and cats.",
    "useExistingSpec": false,
    "options": {
      "includeExamples": true,
      "generateErrorCases": true,
      "realisticData": true
    }
  }'
```

#### Retrieve Generated Mocks

```bash
# Get generated mocks (use jobId from generation response)
curl "$MOCKNEST_URL/ai/generation/jobs/{jobId}/mocks" \
  -H "x-api-key: $API_KEY"

# Create selected mocks in WireMock (copy wireMockMapping from response)
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{...wireMockMapping from AI response...}'
```

#### Namespace Organization

AI-generated mocks are organized using namespaces for better management:

- **Simple API**: `petstore/` 
- **Client-specific**: `client-a/petstore/`
- **Multi-tenant**: `tenant-b/petstore/`

This allows multiple teams and APIs to coexist without conflicts.

## Deployment for Developers

For developers who want to build from source or contribute to MockNest Serverless.

### Prerequisites

- AWS CLI configured with appropriate permissions
- AWS SAM CLI installed
- Docker (or equivalent such as Colima, for local testing)
- Java 21+ and Gradle (or use included Gradle wrapper)

### Build and Deploy from Source

1. **Clone and Build**:
   ```bash
   git clone <repository-url>
   cd mocknest-serverless
   ./gradlew build
   ```

2. **Deploy with SAM**:
   ```bash
   cd deployment/aws/sam
   sam build
   sam deploy --guided
   ```

3. **Quick Deploy with Defaults**:
   ```bash
   sam build && sam deploy
   ```

### Development Configuration

**Default SAM Configuration:**
- **Region**: eu-west-1 (Ireland) - supports all features including AI
- **S3 Bucket**: Auto-generated unique name
- **AI Features**: Enabled with Amazon Nova Pro
- **API Key**: Auto-generated

**Deploy to Different Region**:
```bash
sam deploy --region us-east-1
```

**Custom Parameters**:
```bash
sam deploy --parameter-overrides \
  BedrockModelName=AmazonNovaPro \
  BedrockInferenceMode=AUTO \
  BucketName=my-custom-bucket
```

### Local Development

1. **Run Tests**:
   ```bash
   ./gradlew test
   ```

2. **Run Integration Tests** (requires Docker):
   ```bash
   ./gradlew :software:infra:aws:test
   ```

3. **Local SAM Testing**:
   ```bash
   cd deployment/aws/sam
   sam local start-api
   ```

### Project Structure

```
mocknest-serverless/
├── software/                    # Business logic and application code
│   ├── domain/                  # Domain models and business rules
│   ├── application/             # Use cases and WireMock orchestration
│   └── infra/aws/              # AWS-specific implementations
├── deployment/                 # Deployment configurations
│   ├── sam/                    # SAM templates and scripts
│   ├── sar/                    # SAR deployment scripts
│   └── shared/                 # Shared deployment utilities
├── docs/                       # Documentation and examples
└── .kiro/steering/            # Architecture and design decisions
```

For detailed architecture information, see [Architecture Documentation](.kiro/steering/02-architecture.md).

## Configuration Reference

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MOCKNEST_APP_REGION` | AWS region for application routing | `eu-west-1` |
| `BEDROCK_INFERENCE_PREFIX` | Bedrock inference profile prefix | `eu` |
| `BEDROCK_MODEL_NAME` | Bedrock model name (Amazon Nova Pro is officially supported) | `AmazonNovaPro` |
| `MOCKNEST_S3_BUCKET_NAME` | S3 bucket for mock storage | Auto-generated |

### SAM Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `AppRegion` | AWS region for deployment | `eu-west-1` |
| `BedrockInferencePrefix` | Bedrock inference profile prefix | `eu` |
| `BedrockModelName` | Bedrock model name (Amazon Nova Pro is officially supported) | `AmazonNovaPro` |
| `BucketName` | S3 bucket name | Auto-generated |

### Application Properties

```properties
# AWS Configuration
aws.region=${MOCKNEST_APP_REGION:${AWS_REGION:eu-west-1}}
bedrock.inference.prefix=${BEDROCK_INFERENCE_PREFIX:eu}
bedrock.model.name=${BEDROCK_MODEL_NAME:AmazonNovaPro}

# S3 Configuration
storage.bucket.name=${MOCKNEST_S3_BUCKET_NAME:mocknest-serverless-storage}

# Application Configuration
spring.application.name=mocknest-serverless
```

## Cost Information

MockNest Serverless is designed to operate within [AWS Free Tier](https://aws.amazon.com/free/) limits for typical development and testing scenarios.

### AWS Services Used

**Core Services (Always Used)**:
- **[AWS Lambda](https://aws.amazon.com/lambda/pricing/)** - Serverless compute for mock runtime (2 functions)
- **[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)** - HTTP API endpoints with API key authentication
- **[Amazon S3](https://aws.amazon.com/s3/pricing/)** - Mock storage and response files
- **[Amazon SQS](https://aws.amazon.com/sqs/pricing/)** - Dead Letter Queue for failed invocations
- **[Amazon CloudWatch](https://aws.amazon.com/cloudwatch/pricing/)** - Application logging (30-day retention)
- **[AWS IAM](https://aws.amazon.com/iam/pricing/)** - Access control (free service)

**AI Services (Optional)**:
- **[Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/)** - AI-powered mock generation (pay-per-use only when calling AI endpoints)

### Free Tier Alignment

MockNest's architecture maximizes AWS Free Tier usage:
- **Lambda**: 1M requests/month + 400,000 GB-seconds compute
- **API Gateway**: 1M API calls/month
- **S3**: 5GB storage + 20,000 GET + 2,000 PUT requests/month
- **SQS**: 1M requests/month (minimal usage - only on failures)
- **CloudWatch**: 5GB log ingestion/month

**Typical development and testing scenarios stay well within these free tier limits.**

For detailed cost analysis, optimization tips, and usage scenarios, see our comprehensive [Cost Guide](docs/COST.md).

## Troubleshooting

### Common Issues

1. **Region Mismatch**: Ensure all AWS resources are in the same region
2. **Permissions**: Verify IAM roles have necessary S3 and Lambda permissions
3. **Cold Starts**: First requests may be slower due to Lambda cold starts

### Logs

MockNest Serverless provides comprehensive logging through CloudWatch:

**Log Groups Created:**
- `/aws/lambda/{stack-name}-runtime` - WireMock runtime and mock serving
- `/aws/lambda/{stack-name}-generation` - AI-powered mock generation
- **Retention**: 30 days (configurable in SAM template)

**View logs via SAM CLI:**
```bash
# Runtime function logs
sam logs -n MockNestRuntimeFunction --stack-name mocknest-serverless --tail

# Generation function logs  
sam logs -n MockNestGenerationFunction --stack-name mocknest-serverless --tail
```

**View logs in AWS Console:**
1. Go to CloudWatch → Log groups
2. Find `/aws/lambda/mocknest-serverless-*` log groups
3. View recent log streams

**Note**: API Gateway access logs are disabled to simplify deployment. Lambda logs provide comprehensive application monitoring.

## Contributing

We welcome contributions to MockNest Serverless! Whether you're fixing bugs, adding features, or improving documentation, your help makes the project better.

### How to Contribute

1. **Report Issues**: Use [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues) to report bugs or request features
2. **Submit Pull Requests**: Fork the repository, make your changes, and submit a pull request
3. **Improve Documentation**: Help us keep documentation accurate and helpful
4. **Share Use Cases**: Tell us how you're using MockNest Serverless

### Development Guidelines

- Follow the clean architecture principles outlined in our [Architecture Documentation](.kiro/steering/02-architecture.md)
- Ensure all tests pass before submitting PRs
- Add tests for new functionality
- Update documentation for user-facing changes

For detailed development guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

This project is open source and available under the [MIT License](LICENSE).

## About

MockNest Serverless is an open-source project that provides AWS-native serverless mock runtime for integration testing. It will be available through the AWS Serverless Application Repository (SAR) for easy one-click deployment while remaining fully open source.

## Support

- **Issues**: Report bugs and feature requests via [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues)
- **Documentation**: Additional documentation in the `docs/` directory
- **Architecture**: Design decisions documented in `.kiro/steering/`
- **Community**: Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)