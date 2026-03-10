# MockNest Serverless

MockNest Serverless is a serverless WireMock runtime that enables realistic integration testing without relying on live external services. Deploy it directly into your AWS account for secure, cost-effective API mocking.

## Architecture Overview

<div style="text-align: center;">
  <img src="docs/images/SolutionDesign.png" alt="MockNest Serverless Architecture" width="600">
</div>

MockNest Serverless runs entirely within your AWS account using Lambda functions for compute, API Gateway for HTTP routing, and S3 for persistent mock storage. AI features leverage Amazon Bedrock for intelligent mock generation when called.

## Deployment Options

**Option 1 (Recommended): AWS Serverless Application Repository (SAR)**
- One-click deployment from AWS Console
- Pre-configured templates with best practices
- Automatic updates when new versions are published

**Option 2: AWS SAM (for developers)**
- Build and deploy from source code
- Full customization of infrastructure
- See [main README](README.md#deployment-for-developers) for detailed instructions

## How to Use

### Getting Your API Details

After deployment, find your API Gateway endpoint and API key in the CloudFormation stack outputs:

1. Go to **CloudFormation** in your AWS Console
2. Find your MockNest stack (usually named `serverlessrepo-MockNest-Serverless-*`)
3. Click the **Outputs** tab
4. Note the `MockNestApiUrl` and `MockNestApiKey` values

### Quick Start with Postman

The fastest way to explore MockNest Serverless is using our Postman collections:

1. **Download Collections**: Get the collections from [GitHub](https://github.com/your-org/mocknest-serverless/tree/main/docs/postman):
   - `AWS MockNest Serverless.postman_collection.json` - Complete API with examples
   - `AI Mock Generation.postman_collection.json` - AI-powered mock generation
   - `Mock Nest AWS.postman_environment.json` - Environment template

2. **Import to Postman**: Import both collections and the environment file

3. **Configure Environment**: Set these variables in your Postman environment:
   - `MOCKNEST_URL`: Your `MockNestApiUrl` from CloudFormation outputs
   - `API_KEY`: Your `MockNestApiKey` from CloudFormation outputs

4. **Start Testing**: Explore all endpoints with working examples and realistic data

### Basic Mock Management

Create and manage mocks using the standard WireMock admin API:

```bash
# Set your API details
export MOCKNEST_URL="https://your-api-id.execute-api.region.amazonaws.com/prod"
export API_KEY="your-api-key-from-outputs"

# Create a simple mock
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
      "jsonBody": {
        "id": 123,
        "name": "John Doe",
        "email": "john@example.com"
      }
    }
  }'

# Test your mock
curl "$MOCKNEST_URL/api/users/123" \
  -H "x-api-key: $API_KEY"
```

### Health Check

Verify your deployment is working:

```bash
# Check runtime health
curl "$MOCKNEST_URL/__admin/health" \
  -H "x-api-key: $API_KEY"

# Check AI health
curl "$MOCKNEST_URL/ai/health" \
  -H "x-api-key: $API_KEY"
```

### AI-Assisted Mock Generation

If you enabled AI features during deployment, you can generate mocks from API specifications:

```bash
# Generate mocks from OpenAPI specification
curl -X POST "$MOCKNEST_URL/ai/generation/from-spec" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {
      "apiName": "petstore",
      "client": null
    },
    "specificationUrl": "https://petstore3.swagger.io/api/v3/openapi.json",
    "format": "OPENAPI_3",
    "description": "Generate mocks for 3 pets from the Petstore OpenAPI specification, pets endpoints, only generate mocks for all GET endpoints of pets, return consistent data for these pets across endpoints"
  }'
```

## Input Parameters

When deploying from SAR, you can configure these parameters:

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| **DeploymentName** | Unique identifier for your deployment | `mocks` | `my-team-mocks` |
| **BucketName** | Custom S3 bucket name (optional) | Auto-generated | `my-mocknest-bucket` |
| **LambdaMemorySize** | Memory allocation in MB | `1024` | `2048` |
| **LambdaTimeout** | Function timeout in seconds | `120` | `300` |
| **BedrockModelName** | AI model for mock generation | `AmazonNovaPro` | `AmazonNovaPro` |
| **BedrockInferenceMode** | Inference profile selection | `AUTO` | `GLOBAL_ONLY` |
| **BedrockInferencePrefix** | Bedrock inference profile prefix | `eu` | `us` |
| **BedrockGenerationMaxRetries** | Max retry attempts for AI generation | `1` | `3` |

### BedrockInferenceMode Options

- **AUTO** (recommended): Tries cross-region inference profile first, then falls back to region-specific profile
  - **Best for**: Most users who want automatic optimization
  - **Behavior**: Maximizes model availability and performance
  
- **GLOBAL_ONLY**: Forces use of cross-region inference profile only
  - **Best for**: Users who need consistent global model behavior
  - **Behavior**: Uses shared cross-region capacity (may have higher latency but better availability)
  
- **GEO_ONLY**: Forces use of region-specific inference profile only
  - **Best for**: Users with data residency requirements
  - **Behavior**: Uses geo-specific profile (e.g., "eu" for eu-west-1, "us" for us-east-1)

### BedrockInferencePrefix

Controls the geographic prefix for cross-region Bedrock access:
- **eu**: For European regions (eu-west-1, eu-central-1, etc.)
- **us**: For US regions (us-east-1, us-west-2, etc.)
- **Other regions**: ca, mx, af, ap, il, me, sa supported

### BedrockGenerationMaxRetries

Number of retry attempts if AI mock generation fails validation:
- **Default**: 1 retry attempt
- **Range**: 0-5 retries
- **Use case**: Increase for better success rates with complex specifications

## Common Use Cases

### Integration Testing

Mock external APIs for reliable integration tests using the Petstore API:

```bash
# Mock a pet lookup endpoint
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
        "photoUrls": ["https://example.com/buddy.jpg"]
      }
    }
  }'
```

### Error Scenario Testing

Test how your application handles API failures:

```bash
# Mock a pet not found scenario
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/999"
    },
    "response": {
      "status": 404,
      "headers": {
        "Content-Type": "application/json"
      },
      "jsonBody": {
        "code": 1,
        "type": "error",
        "message": "Pet not found"
      }
    }
  }'
```

### Multi-API Testing

Group multiple APIs in one MockNest instance using URL prefixes:

```bash
# Mock Petstore API
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/findByStatus",
      "queryParameters": {
        "status": {"equalTo": "available"}
      }
    },
    "response": {
      "status": 200,
      "headers": {
        "Content-Type": "application/json"
      },
      "jsonBody": [
        {
          "id": 1,
          "name": "Buddy",
          "status": "available",
          "photoUrls": ["https://example.com/buddy.jpg"]
        }
      ]
    }
  }'

# Mock User API in the same instance
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/userstore/user/testuser"
    },
    "response": {
      "status": 200,
      "headers": {
        "Content-Type": "application/json"
      },
      "jsonBody": {
        "id": 1,
        "username": "testuser",
        "firstName": "Test",
        "lastName": "User",
        "email": "test@example.com"
      }
    }
  }'
```

### Multi-Client Testing

Create client-specific mocks for the same API using headers or patterns:

```bash
# Mock for mobile client
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/123",
      "headers": {
        "User-Agent": {"contains": "Mobile"}
      }
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
        "photoUrls": ["https://mobile.example.com/buddy-small.jpg"]
      }
    }
  }'

# Mock for web client (different response format)
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/123",
      "headers": {
        "User-Agent": {"contains": "Web"}
      }
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
        "photoUrls": [
          "https://web.example.com/buddy-large.jpg",
          "https://web.example.com/buddy-thumb.jpg"
        ],
        "tags": [{"id": 1, "name": "friendly"}, {"id": 2, "name": "new"}],
        "category": {"id": 1, "name": "dog"}
      }
    }
  }'
```

## Error Handling

### Common Errors and Solutions

**Authentication Error (401)**
```json
{"message": "Unauthorized"}
```
**Solution**: Check that you're including the correct API key in the `x-api-key` header.

**Bedrock Access Error (when using AI features)**
```json
{"error": "Failed to configure model AmazonNovaPro in region us-east-1"}
```
**Solution**: Ensure Amazon Bedrock model access is enabled in your AWS account. Go to Amazon Bedrock console → Model access → Enable access for Amazon Nova Pro.

**S3 Permission Error**
```json
{"error": "Access denied to S3 bucket"}
```
**Solution**: This indicates an IAM permission issue. The Lambda function should have been granted S3 access automatically during deployment.

**Lambda Timeout Error**
```json
{"errorType": "Task timed out"}
```
**Solution**: Increase the `LambdaTimeout` parameter during deployment or reduce the complexity of your mock operations.

**Invalid JSON in Mock Definition**
```json
{"errors": [{"code": 10, "source": {"pointer": "/request"}, "title": "Error parsing JSON"}]}
```
**Solution**: Validate your JSON syntax before sending mock creation requests.

### Viewing Logs

To troubleshoot issues, view CloudWatch logs:

1. Go to **CloudWatch** in your AWS Console
2. Click **Log groups**
3. Find the log group for your MockNest Lambda function (usually `/aws/lambda/mocknest-serverless-*`)
4. View recent log streams for error details

### Getting Help

- **Documentation**: See the [User Guide](docs/USER_GUIDE.md) for detailed usage instructions
- **API Reference**: Complete API documentation in the [OpenAPI specification](docs/api/mocknest-openapi.yaml)
- **Issues**: Report bugs or ask questions at [GitHub Issues](https://github.com/your-org/mocknest-serverless/issues)

## Cost

MockNest Serverless is designed to run within [AWS Free Tier](https://aws.amazon.com/free/) limits for typical development and testing scenarios. You pay only for the AWS resources you use in your own account.

### AWS Service Costs

MockNest uses these AWS services - see current pricing at the official AWS pricing pages:

**Core Services (Always Used)**:
- **[AWS Lambda](https://aws.amazon.com/lambda/pricing/)** - Serverless compute for mock runtime
- **[Amazon API Gateway](https://aws.amazon.com/api-gateway/pricing/)** - HTTP API endpoints  
- **[Amazon S3](https://aws.amazon.com/s3/pricing/)** - Mock storage and response files

**AI Services**:
- **[Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/)** - AI-powered mock generation (you only pay when calling AI endpoints)

### Free Tier Alignment

MockNest Serverless is specifically designed to operate within AWS Free Tier limits:

**AWS Free Tier Includes**:
- **Lambda**: 1M requests/month + 400,000 GB-seconds compute
- **API Gateway**: 1M API calls/month  
- **S3**: 5GB storage + 20,000 GET requests + 2,000 PUT requests/month
- **Bedrock**: Pay-per-use only when calling AI endpoints (no base cost)

**Typical Development Usage**: Most development and testing scenarios stay well within these free tier limits.

### Cost Optimization Tips

1. **Leverage Free Tier**: MockNest's architecture maximizes free tier usage for core functionality
2. **Clean Up Unused Mocks**: Regularly remove old mock definitions to minimize S3 storage
3. **Monitor Usage**: Use [AWS Cost Explorer](https://aws.amazon.com/aws-cost-management/aws-cost-explorer/) to track your usage
4. **AI Usage**: Use AI features judiciously - generate mocks in batches rather than individually
5. **Right-Size Lambda**: Adjust memory allocation based on your mock complexity needs

For current pricing details, always refer to the official AWS pricing pages linked above.

## Security

MockNest Serverless uses **API key authentication** as the primary security mechanism. All endpoints require a valid API key in the `x-api-key` header.

### API Key Security

**Default Protection**:
- All endpoints require API key authentication
- API key is automatically generated during deployment
- Found in CloudFormation stack outputs after deployment

**Best Practices**:
- **Rotate API Keys**: Regularly rotate your API Gateway API keys
- **Restrict Access**: Only share API keys with authorized team members
- **Monitor Usage**: Use CloudTrail to monitor API access patterns

### AWS Security

MockNest follows AWS security best practices with least-privilege IAM permissions:

- **S3 Access**: Limited to the specific MockNest bucket only
- **Bedrock Access**: Limited to AI model inference 
- **CloudWatch**: Standard logging permissions only
- **Encryption**: All data encrypted in transit (HTTPS) and at rest (S3)

### Data Isolation

- Each deployment creates its own isolated S3 bucket
- No data sharing between different MockNest deployments
- All data stays within your selected AWS region

For security questions or to report security issues, please contact us through [GitHub Issues](https://github.com/your-org/mocknest-serverless/issues) with the "security" label.