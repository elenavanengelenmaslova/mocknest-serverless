# MockNest Serverless

MockNest Serverless is a serverless WireMock runtime for AWS that enables realistic integration testing without relying on live external services. It runs natively on AWS Lambda and persists mock definitions in Amazon S3, making mocks available across cold starts and deployments.

## Features

- **Serverless WireMock Runtime**: Full WireMock API running on AWS Lambda
- **Persistent Mock Storage**: Mock definitions stored in Amazon S3
- **Protocol Support**: REST, SOAP, and GraphQL-over-HTTP APIs
- **AI-Assisted Mock Generation**: Optional AI-powered mock creation from API specifications
- **AWS Free Tier Compatible**: Designed to operate within AWS Free Tier limits
- **Easy Deployment**: One-click deployment via AWS Serverless Application Repository (SAR)

## Quick Start

### Prerequisites

- AWS CLI configured with appropriate permissions
- AWS SAM CLI installed
- Docker (or equivalent such as Colima, for local testing)

### Deployment

**Option 1: AWS Serverless Application Repository (SAR)** - *Coming Soon*
```bash
# One-click deploy from AWS Console
# 1. Switch to your preferred AWS region in the console
# 2. Go to AWS Serverless Application Repository
# 3. Search for "MockNest Serverless"
# 4. Click "Deploy" and configure parameters
```

**Option 2: Direct SAM Deployment**
```bash
# Build and deploy from source (defaults to eu-west-1)
./gradlew build
cd deployment/aws/sam
sam build
sam deploy --guided
```

> **📍 Region Notes**: 
> - **SAR deployment**: Deploys to your current AWS Console region
> - **Direct SAM deployment**: Defaults to eu-west-1 (easily configurable)

**Quick Deploy with Defaults**:
```bash
sam build && sam deploy
```

**Default Configuration (Direct SAM):**
- **Region**: eu-west-1 (Ireland) - supports all features including AI
- **S3 Bucket**: Auto-generated unique name
- **AI Features**: Disabled (Free Tier friendly)
- **API Key**: Auto-generated

### Configuration

#### AWS Region Configuration

MockNest Serverless defaults to **eu-west-1** (Ireland) because:
- ✅ Supports all AWS services (Lambda, API Gateway, S3)
- ✅ Supports Amazon Bedrock for AI features
- ✅ Good global connectivity and performance
- ✅ GDPR-compliant for European users

**To deploy to a different region:**

1. **US East (most common alternative)**:
   ```bash
   sam deploy --region us-east-1
   ```

2. **US West (California)**:
   ```bash
   sam deploy --region us-west-1
   ```

3. **Edit SAM Config** (for permanent change):
   ```bash
   # Edit deployment/aws/sam/samconfig.toml
   region = "us-east-1"
   ```

**Supported Regions:**
- All AWS regions where Lambda, API Gateway, and S3 are available
- For AI features: Ensure Amazon Bedrock is available in your chosen region (see [AWS Bedrock regions](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-regions.html))

**For detailed deployment instructions and regional considerations, see [DEPLOYMENT.md](docs/DEPLOYMENT.md).**

#### Optional Customizations

**Enable AI Features** (requires Bedrock-supported region):
```bash
sam deploy --parameter-overrides EnableAI=true
```

**Custom S3 Bucket Name**:
```bash
sam deploy --parameter-overrides BucketName=my-custom-bucket-name
```

**Combined Customizations**:
```bash
sam deploy --region us-east-1 --parameter-overrides EnableAI=true BucketName=my-bucket
```

## Usage

### Basic Mock Management

Once deployed, MockNest Serverless exposes the standard WireMock admin API:

```bash
# Get your API Gateway endpoint from SAM output
export MOCKNEST_URL="https://your-api-id.execute-api.eu-west-1.amazonaws.com/prod"
export API_KEY="your-api-key"

# Create a mock
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "url": "/api/users/123"
    },
    "response": {
      "status": 200,
      "headers": {
        "Content-Type": "application/json"
      },
      "body": "{\"id\": 123, \"name\": \"John Doe\"}"
    }
  }'

# Test the mock
curl "$MOCKNEST_URL/api/users/123" \
  -H "x-api-key: $API_KEY"
```

### AI-Assisted Mock Generation (Optional)

If AI features are enabled during deployment, MockNest provides intelligent mock generation capabilities:

#### Generate from API Specification

```bash
# Generate mocks from OpenAPI specification
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
    "options": {
      "includeExamples": true,
      "generateErrorCases": true,
      "realisticData": true,
      "storeSpecification": true
    }
  }'
```

#### Generate from Natural Language

```bash
# Generate mocks from description
curl -X POST "$MOCKNEST_URL/ai/generation/from-description" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {
      "apiName": "user-service",
      "client": "demo"
    },
    "description": "Create a REST API for managing users with endpoints to get all users, get user by ID, create new user, update user, and delete user. Include proper error responses for not found (404) and validation errors (400).",
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

- **Simple API**: `mocknest/salesforce/` 
- **Client-specific**: `mocknest/client-a/payments/`
- **Multi-tenant**: `mocknest/tenant-b/users/`

This allows multiple teams and APIs to coexist without conflicts.

## Development

### Local Development Setup

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd mocknest-serverless
   ```

2. **Build the project**:
   ```bash
   ./gradlew build
   ```

3. **Run tests**:
   ```bash
   ./gradlew test
   ```

4. **Run integration tests** (requires Docker):
   ```bash
   ./gradlew :software:infra:aws:test
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

### Architecture

MockNest Serverless follows clean architecture principles:

- **Domain Layer**: Business models and rules
- **Application Layer**: Use cases and service interfaces
- **Infrastructure Layer**: AWS-specific adapters and implementations

For detailed architecture information, see [Architecture Documentation](.kiro/steering/02-architecture.md).

## Configuration Reference

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `AWS_REGION` | AWS region for deployment | `eu-west-1` |
| `MOCKNEST_S3_BUCKET_NAME` | S3 bucket for mock storage | Auto-generated |

### SAM Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `Region` | AWS region | `eu-west-1` |
| `BucketName` | S3 bucket name | Auto-generated |
| `EnableAI` | Enable AI features | `false` |

### Application Properties

```properties
# AWS Configuration
aws.region=eu-west-1
storage.bucket.name=${MOCKNEST_S3_BUCKET_NAME:mocknest-serverless-storage}

# Application Configuration
spring.application.name=mocknest-serverless
```

## Troubleshooting

### Common Issues

1. **Region Mismatch**: Ensure all AWS resources are in the same region
2. **Permissions**: Verify IAM roles have necessary S3 and Lambda permissions
3. **Cold Starts**: First requests may be slower due to Lambda cold starts

### Logs

View Lambda logs in CloudWatch:
```bash
sam logs -n MockNestFunction --stack-name mocknest-serverless --tail
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines and contribution process.

## License

This project is open source and available under the [MIT License](LICENSE).

## About

MockNest Serverless is an open-source project that provides AWS-native serverless mock runtime for integration testing. It will be available through the AWS Serverless Application Repository (SAR) for easy one-click deployment while remaining fully open source.

## Support

- **Issues**: Report bugs and feature requests via [GitHub Issues](https://github.com/your-org/mocknest-serverless/issues)
- **Documentation**: Additional documentation in the `docs/` directory
- **Architecture**: Design decisions documented in `.kiro/steering/`
- **Community**: Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)