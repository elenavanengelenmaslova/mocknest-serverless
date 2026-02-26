# AI Test Agent - Koog + Bedrock Integration

This is a simple test endpoint to validate the entire stack: **REST API → Koog Agent → Bedrock → Response**

## Purpose

Before implementing the full mock generation feature, this test agent validates that:
1. REST API endpoints work correctly
2. Koog framework is properly configured
3. Bedrock integration is functional
4. The entire request/response flow works end-to-end

## Architecture

```
User Request (JSON)
    ↓
REST Controller (/ai/test/chat)
    ↓
TestKoogAgent (Application Layer)
    ↓
BedrockRuntimeClient (Infrastructure Layer)
    ↓
AWS Bedrock (Claude 4.5 Opus)
    ↓
Response back through the stack
```

## API Endpoints

### Health Check
```bash
GET /ai/test/health
```

**Response:**
```json
{
  "status": "healthy",
  "service": "ai-test-agent",
  "message": "Koog + Bedrock integration ready"
}
```

### Chat with AI
```bash
POST /ai/test/chat
Content-Type: application/json

{
  "instructions": "Tell me a joke about serverless computing",
  "context": {
    "user": "developer",
    "environment": "test"
  }
}
```

**Response:**
```json
{
  "success": true,
  "message": "Successfully processed request through Koog and Bedrock",
  "bedrockResponse": "Why did the serverless function go to therapy? It had too many cold starts!",
  "error": null
}
```

## Local Testing (Without AWS)

Run the application locally with mock Bedrock responses:

```bash
# Build the project
./gradlew clean build

# Run tests
./gradlew test

# Run the application (requires AWS credentials for Bedrock)
./gradlew :software:infra:aws:bootRun
```

## AWS Deployment

### Prerequisites
1. AWS CLI configured with appropriate credentials
2. Bedrock model access enabled in your AWS account
3. SAM CLI installed

### Deploy with AI Enabled

```bash
# Build the Lambda package
./gradlew :software:infra:aws:shadowJar

# Deploy with SAM
cd deployment/aws/sam
./build.sh
sam deploy \
  --parameter-overrides BedrockInferencePrefix=eu \
  --guided
```

### Test the Deployed Endpoint

```bash
# Get the API URL from SAM output
API_URL=$(aws cloudformation describe-stacks \
  --stack-name mocknest-serverless \
  --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiUrl`].OutputValue' \
  --output text)

# Health check
curl "${API_URL}ai/test/health"

# Chat request
curl -X POST "${API_URL}ai/test/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "instructions": "Tell me a joke about serverless computing"
  }'
```

## Using Postman

Import the collection: `docs/postman/AI Test Agent.postman_collection.json`

1. Set the `base_url` variable to your API endpoint
2. Run the "Health Check" request to verify connectivity
3. Run the "Simple Chat Request" to test Bedrock integration
4. Try other requests to explore different scenarios

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MOCKNEST_APP_REGION` | AWS region for Bedrock | `eu-west-1` |
| `MOCK_STORAGE_BUCKET` | S3 bucket for storage | Auto-generated |

## Troubleshooting

### "Bedrock service unavailable"
- Ensure Bedrock is enabled in your AWS account
- Check that you have access to Claude 4.5 Opus model
- Verify IAM permissions include `bedrock:InvokeModel`

### "Access Denied" errors
- Check Lambda execution role has Bedrock permissions
- Verify the SAM template was deployed with correct Bedrock parameters (e.g. `BedrockInferencePrefix=eu`)

### Cold start timeouts
- Increase Lambda timeout in SAM template
- Increase Lambda memory (more memory = faster cold starts)

## Next Steps

Once this test agent works end-to-end:
1. Implement full mock generation logic
2. Add OpenAPI spec parsing
3. Implement S3 storage for generated mocks
4. Create retrieval endpoints
5. Integrate with WireMock admin API

## Files Created

- `software/domain/src/main/kotlin/io/mocknest/domain/generation/TestAgentRequest.kt`
- `software/domain/src/main/kotlin/io/mocknest/domain/generation/TestAgentResponse.kt`
- `software/application/src/main/kotlin/io/mocknest/application/generation/agent/TestKoogAgent.kt`
- `software/application/src/main/kotlin/io/mocknest/application/generation/controllers/TestAgentController.kt`
- `software/infra/aws/src/main/kotlin/io/mocknest/infra/aws/config/BedrockConfiguration.kt`
- `docs/postman/AI Test Agent.postman_collection.json`
