# MockNest Serverless User Guide

This comprehensive guide helps you get the most out of MockNest Serverless, from creating your first mock to advanced AI-assisted generation and troubleshooting.

## Getting Started

### Quick Start Options

**Option 1: Use Postman Collections (Recommended)**

The fastest way to get started is with our comprehensive Postman collections:

1. **Download from GitHub**: [docs/postman/](https://github.com/elenavanengelenmaslova/mocknest-serverless/tree/main/docs/postman)
   - `AWS MockNest Serverless.postman_collection.json` - Complete API with working examples
   - `AI Mock Generation.postman_collection.json` - AI-powered mock generation examples
   - `Mock Nest AWS.postman_environment.json` - Pre-configured environment template

2. **Import to Postman**: Import all three files into Postman

3. **Configure Environment**: Update the environment variables with your deployment details:
   - `MOCKNEST_URL`: Your API Gateway endpoint
   - `API_KEY`: Your API key from CloudFormation outputs

4. **Explore**: Run the examples to see MockNest in action with realistic scenarios

**Option 2: Manual Setup with cURL**

### Your First Mock

After deploying MockNest Serverless, follow these steps to create and test your first mock:

#### Step 1: Get Your API Details

Find your API Gateway endpoint and API key from the CloudFormation stack outputs:

1. Go to **CloudFormation** in your AWS Console
2. Find your MockNest stack (usually named `serverlessrepo-MockNest-Serverless-*`)
3. Click the **Outputs** tab
4. Copy the `ApiGatewayEndpoint` and `ApiKey` values

#### Step 2: Set Environment Variables

```bash
export MOCKNEST_URL="https://your-api-id.execute-api.region.amazonaws.com/prod"
export API_KEY="your-api-key-from-outputs"
```

#### Step 3: Verify Deployment

Test that MockNest is running:

```bash
curl "$MOCKNEST_URL/__admin/health" \
  -H "x-api-key: $API_KEY"
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00Z",
  "region": "us-east-1",
  "storage": {
    "bucket": "mocknest-storage-bucket-abc123",
    "connectivity": "ok"
  }
}
```

#### Step 4: Create Your First Mock

Create a simple Petstore API mock:

```bash
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
        "email": "john@example.com"
      }
    }
  }'
```

#### Step 5: Test Your Mock

```bash
curl "$MOCKNEST_URL/api/users/123" \
  -H "x-api-key: $API_KEY"
```

Expected response:
```json
{
  "id": 123,
  "name": "John Doe",
  "email": "john@example.com"
}
```

Congratulations! You've created and tested your first mock.

## Core Features

MockNest Serverless supports the full WireMock API with the following tested capabilities:

### Request Matching

**URL Matching**:
```json
{
  "request": {
    "method": "GET",
    "url": "/exact/path"           // Exact match
  }
}
```

```json
{
  "request": {
    "method": "GET",
    "urlPath": "/api/users",       // Path only (ignores query params)
    "queryParameters": {
      "status": {"equalTo": "active"}
    }
  }
}
```

```json
{
  "request": {
    "method": "GET",
    "urlPattern": "/api/users/[0-9]+"  // Regular expression
  }
}
```

**Header Matching**:
```json
{
  "request": {
    "method": "POST",
    "url": "/api/data",
    "headers": {
      "Content-Type": {"equalTo": "application/json"},
      "Authorization": {"matches": "Bearer .*"}
    }
  }
}
```

**Body Matching**:
```json
{
  "request": {
    "method": "POST",
    "url": "/api/users",
    "bodyPatterns": [
      {"matchesJsonPath": "$.name"},
      {"matchesJsonPath": "$.email"}
    ]
  }
}
```

### Response Generation

**Static Responses**:
```json
{
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json",
      "X-Custom-Header": "value"
    },
    "jsonBody": {
      "message": "Success"
    }
  }
}
```

**Response Templating**:
```json
{
  "response": {
    "status": 200,
    "headers": {"Content-Type": "application/json"},
    "jsonBody": {
      "requestedId": "{{request.pathSegments.[2]}}",
      "timestamp": "{{now}}"
    }
  }
}
```

**File-Based Responses**:
```json
{
  "response": {
    "status": 200,
    "headers": {"Content-Type": "application/json"},
    "bodyFileName": "user-response.json"
  }
}
```

### Stateful Behavior

**Scenarios**:
```json
{
  "request": {
    "method": "POST",
    "urlPath": "/petstore/user/login"
  },
  "response": {
    "status": 200,
    "jsonBody": {
      "token": "abc123"
    }
  },
  "newScenarioState": "Logged In"
}
```

```json
{
  "request": {
    "method": "GET",
    "urlPath": "/petstore/user/profile"
  },
  "requiredScenarioState": "Logged In",
  "response": {
    "status": 200,
    "jsonBody": {
      "id": 1,
      "username": "testuser",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com"
    }
  }
}
```

### Request Verification

**Check Received Requests**:
```bash
# Get all requests
curl "$MOCKNEST_URL/__admin/requests" \
  -H "x-api-key: $API_KEY"

# Find specific requests
curl "$MOCKNEST_URL/__admin/requests/find" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "POST",
    "url": "/api/users"
  }'
```

### Mock Management

**List All Mappings**:
```bash
curl "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY"
```

**Update a Mapping**:
```bash
curl -X PUT "$MOCKNEST_URL/__admin/mappings/{mapping-id}" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{...updated mapping...}'
```

**Delete a Mapping**:
```bash
curl -X DELETE "$MOCKNEST_URL/__admin/mappings/{mapping-id}" \
  -H "x-api-key: $API_KEY"
```

**Reset All Mappings**:
```bash
curl -X POST "$MOCKNEST_URL/__admin/reset" \
  -H "x-api-key: $API_KEY"
```

## AI-Assisted Mock Generation

If you enabled AI features during deployment, MockNest provides intelligent mock generation capabilities.

### Generate from API Specifications

Transform OpenAPI/Swagger specifications into comprehensive mock suites:

```bash
curl -X POST "$MOCKNEST_URL/ai/generation/from-spec" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {
      "apiName": "petstore",
      "client": "demo"
    },
    "specification": "openapi: 3.0.0\ninfo:\n  title: Pet Store API\n  version: 1.0.0\npaths:\n  /pets:\n    get:\n      parameters:\n        - name: status\n          in: query\n          schema:\n            type: string\n            enum: [available, pending, sold]\n      responses:\n        \"200\":\n          description: List of pets\n          content:\n            application/json:\n              schema:\n                type: array\n                items:\n                  type: object\n                  properties:\n                    id:\n                      type: integer\n                    name:\n                      type: string\n                    status:\n                      type: string",
    "format": "OPENAPI_3",
    "description": "Generate 10 realistic pets with different statuses, include edge cases for invalid status values",
    "options": {
      "enableValidation": true,
      "includeExamples": true,
      "generateErrorCases": true
    }
  }'
```

**Response**:
```json
{
  "mappings": [
    {
      "request": {
        "method": "GET",
        "urlPath": "/demo/petstore/pets",
        "queryParameters": {
          "status": {"equalTo": "available"}
        }
      },
      "response": {
        "status": 200,
        "jsonBody": [
          {"id": 1, "name": "Buddy", "status": "available"},
          {"id": 2, "name": "Max", "status": "available"}
        ],
        "headers": {"Content-Type": "application/json"}
      }
    },
    {
      "request": {
        "method": "GET",
        "urlPath": "/demo/petstore/pets",
        "queryParameters": {
          "status": {"equalTo": "invalid"}
        }
      },
      "response": {
        "status": 400,
        "jsonBody": {"error": "Invalid status value"},
        "headers": {"Content-Type": "application/json"}
      }
    }
  ]
}
```

### Namespace Organization

AI-generated mocks use namespaces to prevent conflicts:

- **Format**: `/namespace/client/apiName/endpoint`
- **Example**: `/demo/petstore/pets` or `/production/client-a/users`

This allows multiple teams and APIs to coexist safely.

### Import Generated Mocks

After generation, import the mocks into WireMock:

```bash
# Import all generated mappings
curl -X POST "$MOCKNEST_URL/__admin/mappings/import" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "mappings": [...mappings from AI response...]
  }'
```

## Common Workflows

### Integration Testing Workflow

**1. Set Up Test Environment**:
```bash
# Create environment-specific namespace
export TEST_ENV="integration-test"
export API_PREFIX="/test/myapp"
```

**2. Create Service Mocks**:
```bash
# Mock external payment service
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "POST",
      "urlPath": "'$API_PREFIX'/store/order",
      "bodyPatterns": [
        {"matchesJsonPath": "$.petId"},
        {"matchesJsonPath": "$.quantity"}
      ]
    },
    "response": {
      "status": 200,
      "headers": {"Content-Type": "application/json"},
      "jsonBody": {
        "id": "{{randomValue type=\"UUID\"}}",
        "petId": "{{jsonPath request.body \"$.petId\"}}",
        "quantity": "{{jsonPath request.body \"$.quantity\"}}",
        "shipDate": "{{now}}",
        "status": "placed",
        "complete": false
      }
    }
  }'

# Mock user service
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPathPattern": "'$API_PREFIX'/user/[0-9]+"
    },
    "response": {
      "status": 200,
      "headers": {"Content-Type": "application/json"},
      "jsonBody": {
        "id": "{{request.pathSegments.[3]}}",
        "username": "testuser",
        "firstName": "Test",
        "lastName": "User",
        "email": "test@example.com",
        "phone": "555-0123"
      }
    }
  }'
```

**3. Run Tests**:
```bash
# Configure your application to use MockNest
export PAYMENT_SERVICE_URL="$MOCKNEST_URL$API_PREFIX"
export USER_SERVICE_URL="$MOCKNEST_URL$API_PREFIX"

# Run your integration tests
npm test
```

**4. Verify Interactions**:
```bash
# Check that expected calls were made
curl "$MOCKNEST_URL/__admin/requests/find" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "POST",
    "urlPath": "'$API_PREFIX'/payments/charge"
  }'
```

**5. Clean Up**:
```bash
# Reset for next test run
curl -X POST "$MOCKNEST_URL/__admin/reset" \
  -H "x-api-key: $API_KEY"
```

### Error Scenario Testing

**Test Network Timeouts**:
```bash
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/slow-lookup"
    },
    "response": {
      "status": 200,
      "jsonBody": {
        "id": 999,
        "name": "Slow Pet",
        "status": "available",
        "photoUrls": ["https://example.com/slow-pet.jpg"],
        "category": {"id": 1, "name": "dog"},
        "tags": [{"id": 1, "name": "friendly"}, {"id": 2, "name": "new"}]
      },
      "fixedDelayMilliseconds": 10000
    }
  }'
```

**Test Service Failures**:
```bash
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "POST",
      "urlPath": "/petstore/store/order"
    },
    "response": {
      "status": 500,
      "headers": {"Content-Type": "application/json"},
      "jsonBody": {
        "error": "Internal server error",
        "code": "SERVICE_UNAVAILABLE",
        "message": "Pet store service is temporarily unavailable"
      }
    }
  }'
```

**Test Rate Limiting**:
```bash
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "urlPath": "/petstore/pet/findByStatus"
    },
    "response": {
      "status": 429,
      "headers": {
        "Content-Type": "application/json",
        "Retry-After": "60"
      },
      "jsonBody": {
        "error": "Rate limit exceeded",
        "retryAfter": 60,
        "message": "Too many requests to pet store API"
      }
    }
  }'
```

### Webhook and Callback Testing

**Set Up Webhook Endpoint**:
```bash
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "POST",
      "urlPath": "/petstore/webhooks/order-complete",
      "bodyPatterns": [
        {"matchesJsonPath": "$.orderId"}
      ]
    },
    "response": {
      "status": 200,
      "headers": {"Content-Type": "application/json"},
      "jsonBody": {
        "received": true,
        "timestamp": "{{now}}",
        "status": "processed"
      }
    }
  }'
```

**Simulate Webhook Call**:
```bash
# Your application would make this call
curl -X POST "$MOCKNEST_URL/petstore/webhooks/order-complete" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "12345",
    "status": "completed",
    "total": 29.99
  }'
```
    "transactionId": "txn_12345",
    "status": "completed",
    "amount": 100.00
  }'
```

**Verify Webhook Received**:
```bash
curl "$MOCKNEST_URL/__admin/requests/find" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "POST",
    "url": "/webhooks/payment-complete"
  }'
```

## Troubleshooting

### Common Issues and Solutions

#### Authentication Problems

**Issue**: `{"message": "Unauthorized"}` (HTTP 401)

**Causes**:
- Missing API key header
- Incorrect API key value
- API key not properly URL-encoded

**Solutions**:
```bash
# Verify API key is correct
echo "API Key: $API_KEY"

# Check header format
curl -v "$MOCKNEST_URL/__admin/health" \
  -H "x-api-key: $API_KEY"

# Try with explicit header
curl "$MOCKNEST_URL/__admin/health" \
  -H "x-api-key: your-actual-api-key-here"
```

#### Mock Not Matching

**Issue**: Mock exists but requests return 404

**Debugging Steps**:

1. **Check Exact URL**:
```bash
# List all mappings to verify URL
curl "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" | jq '.mappings[].request.url'
```

2. **Check Request Details**:
```bash
# See what requests were actually received
curl "$MOCKNEST_URL/__admin/requests" \
  -H "x-api-key: $API_KEY" | jq '.requests[0]'
```

3. **Test with Exact Match**:
```bash
# Create a very specific mock for debugging
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {
      "method": "GET",
      "url": "/debug/test"
    },
    "response": {
      "status": 200,
      "body": "Debug response"
    }
  }'

# Test the debug mock
curl "$MOCKNEST_URL/debug/test" \
  -H "x-api-key: $API_KEY"
```

#### AI Generation Errors

**Issue**: `{"error": "Failed to configure model AmazonNovaPro in region us-east-1"}`

**Solutions**:
1. **Enable Bedrock Access**:
   - Go to Amazon Bedrock console
   - Click "Model access" in left sidebar
   - Enable access for "Amazon Nova Pro"
   - Wait for access to be granted (can take a few minutes)

2. **Check Region Support**:
   - Verify Amazon Bedrock is available in your deployment region
   - See [AWS Bedrock regions](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-regions.html)

3. **Try Different Inference Mode**:
```bash
# Check AI health status
curl "$MOCKNEST_URL/ai/health" \
  -H "x-api-key: $API_KEY"
```

#### Performance Issues

**Issue**: Slow response times or timeouts

**Causes**:
- Lambda cold starts
- Large mock definitions
- Complex request matching

**Solutions**:

1. **Increase Lambda Memory**:
   - Redeploy with higher `LambdaMemorySize` parameter (e.g., 2048 MB)

2. **Increase Timeout**:
   - Redeploy with higher `LambdaTimeout` parameter (e.g., 300 seconds)

3. **Optimize Mock Definitions**:
```bash
# Use simpler matching patterns
{
  "request": {
    "method": "GET",
    "url": "/simple/path"  // Better than complex regex
  }
}

# Avoid large inline bodies
{
  "response": {
    "bodyFileName": "large-response.json"  // Better than inline JSON
  }
}
```

#### Storage Issues

**Issue**: `{"error": "Access denied to S3 bucket"}`

**Causes**:
- IAM permission issues
- S3 bucket policy problems
- Cross-region access attempts

**Solutions**:

1. **Check CloudFormation Stack**:
   - Verify stack deployed successfully
   - Check for any failed resources

2. **Verify IAM Roles**:
   - Lambda execution role should have S3 permissions
   - Check CloudFormation template for IAM policies

3. **Check S3 Bucket**:
   - Verify bucket exists in correct region
   - Check bucket permissions in S3 console

### Viewing Detailed Logs

**CloudWatch Logs**:
1. Go to CloudWatch in AWS Console
2. Click "Log groups"
3. Find `/aws/lambda/MockNest-*` log group
4. View recent log streams

**Useful Log Patterns**:
- `ERROR` - Application errors
- `WARN` - Configuration warnings
- `DEBUG` - Detailed execution information

**Enable Debug Logging**:
```bash
# Add debug parameter during deployment
sam deploy --parameter-overrides LogLevel=DEBUG
```

### Performance Optimization

**Reduce Cold Start Time**:
- Use higher Lambda memory allocation
- Keep mock definitions small
- Use provisioned concurrency for production workloads

**Optimize Mock Matching**:
- Use exact URL matches when possible
- Avoid complex regular expressions
- Use simple header matching

**Manage Mock Storage**:
- Regularly clean up unused mocks
- Use file-based responses for large payloads
- Organize mocks with clear namespaces

## Limitations

Understanding MockNest's limitations helps you use it effectively:

### Serverless Environment Constraints

**Lambda Execution Limits**:
- Maximum 15-minute execution time per request
- Maximum 10GB memory allocation
- 6MB request/response payload size limit

**Cold Start Impact**:
- First request after idle period may be slower
- Mock definitions loaded at startup
- Affects applications requiring sub-100ms response times

**Stateful Behavior**:
- State is not shared between Lambda instances
- Scenarios reset on cold starts
- Use external state storage for persistent scenarios

### WireMock Feature Limitations

**Tested Features** (fully supported):
- HTTP request/response mocking
- JSON and XML body matching
- Request verification and admin API
- Response templating
- File serving for response bodies
- Basic stateful scenarios

**Untested Features** (may not work):
- HTTPS client certificate authentication
- Custom WireMock extensions
- Advanced proxy features
- Binary file handling beyond basic file serving

**Not Supported**:
- TCP/UDP protocol mocking
- WebSocket connections
- Server-Sent Events (SSE)
- Persistent state across deployments

### AI Feature Limitations

**Model Support**:
- Only Amazon Nova Pro is officially supported
- Other models are experimental
- Regional availability varies

**Generation Constraints**:
- Limited to HTTP-based API specifications
- Best results with well-structured OpenAPI specs
- May require manual refinement for complex scenarios

**Cost Considerations**:
- AI features incur additional [Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/) charges
- Token usage varies by specification complexity
- No free tier for Bedrock usage

### Scaling Limitations

**Concurrent Requests**:
- Limited by Lambda concurrency settings
- Default 1000 concurrent executions per region
- May need adjustment for high-traffic scenarios

**Storage Limits**:
- [S3 storage costs](https://aws.amazon.com/s3/pricing/) increase with mock volume
- Large mock sets may impact startup time
- No built-in cleanup or retention policies

### Network and Security

**API Gateway Limits**:
- 30-second timeout for Lambda integration
- 10MB maximum payload size
- Regional deployment only

**Security Model**:
- API key authentication only
- No built-in IP whitelisting
- No custom authentication providers

## API Reference

For complete API documentation, see the [OpenAPI specification](api/mocknest-openapi.yaml).

### Core Endpoints

**Health Check**:
- `GET /__admin/health` - Runtime health status
- `GET /ai/health` - AI features health status (if enabled)

**Mock Management**:
- `GET /__admin/mappings` - List all mappings
- `POST /__admin/mappings` - Create new mapping
- `PUT /__admin/mappings/{id}` - Update mapping
- `DELETE /__admin/mappings/{id}` - Delete mapping
- `POST /__admin/reset` - Reset all mappings

**Request Verification**:
- `GET /__admin/requests` - List received requests
- `POST /__admin/requests/find` - Find specific requests
- `POST /__admin/requests/reset` - Clear request log

**AI Generation** (if enabled):
- `POST /ai/generation/from-spec` - Generate from API specification

### Authentication

All endpoints require API key authentication:
```bash
curl -H "x-api-key: your-api-key" "$MOCKNEST_URL/endpoint"
```

### Response Formats

All responses use standard WireMock JSON formats. See the OpenAPI specification for detailed schemas and examples.