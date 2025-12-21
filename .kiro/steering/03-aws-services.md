# AWS Services

## Core Services
MockNest is built around these essential AWS services:

- **AWS Lambda** - Serverless compute runtime for the WireMock engine and AI-assisted mock generation
- **Amazon API Gateway** - HTTP ingress and API key-based access control for both admin API and mocked endpoints
- **Amazon S3** - Persistent storage for WireMock mappings and response payloads
- **Amazon Bedrock** - AI model access for the Koog-based mock generation agent

## Compute Services
- **AWS Lambda** (Java runtime) - Hosts the WireMock runtime and handles all HTTP requests
  - Cold start optimization is critical due to mapping loading at startup
  - Concurrency settings control horizontal scaling
  - Memory allocation impacts performance with large mock sets

## Storage Services
- **Amazon S3** - Primary storage for all persistent data
  - Mock definitions (WireMock mappings) stored as JSON files
  - Response payloads stored separately to reduce memory usage
  - Bucket organization supports efficient loading and caching strategies

## AI/ML Services
- **Amazon Bedrock** - Provides access to foundation models for AI-assisted mock generation
  - Integrated through the Koog framework
  - Used for generating WireMock mappings from API specifications and natural language input
  - Operates outside the request/response path to avoid runtime latency

## Networking Services
- **Amazon API Gateway** - HTTP ingress and request routing
  - Handles both admin API calls and mocked endpoint requests
  - Provides API key-based authentication and rate limiting
  - Integrates with Lambda for serverless request processing

## Security Services
- **AWS IAM** - Service-to-service authentication and authorization
  - Lambda execution roles for S3 and Bedrock access
  - API Gateway integration roles
- **API Gateway API Keys** - Default access control mechanism for MockNest endpoints
- **AWS Secrets Manager** (future) - Secure storage for API keys and configuration

## Monitoring & Logging
- **Amazon CloudWatch** - Logging and basic metrics
  - Lambda function logs for debugging and monitoring
  - API Gateway access logs and metrics
  - Custom metrics for mock usage patterns (future enhancement)
- **AWS X-Ray** (optional) - Distributed tracing for performance analysis

## Deployment & CI/CD
- **AWS SAM** - Infrastructure-as-code and deployment packaging
  - Supports conditional deployment parameters for AI features
  - Default deployment excludes AI components (Free Tier compliant)
  - Users can opt-in to AI generation during SAR deployment
- **AWS Serverless Application Repository (SAR)** - Target publication platform
- **AWS CloudFormation** - Underlying infrastructure provisioning (via SAM)

## Cost Optimization
- **Free Tier Alignment** - Architecture designed to operate within AWS Free Tier limits
  - Lambda: 1M requests/month, 400,000 GB-seconds compute
  - API Gateway: 1M API calls/month
  - S3: 5GB storage, 20,000 GET requests, 2,000 PUT requests
  - Bedrock: Pay-per-use model for AI generation (no free tier, costs separate from runtime)
- **Efficient Resource Usage**
  - Response payloads stored in S3 to minimize Lambda memory usage
  - All mappings loaded at startup (on-demand loading is a future optimization)
  - Horizontal scaling only when needed

## Service Limits & Quotas
Key AWS limits that may impact MockNest:

- **Lambda Limits**
  - 15-minute maximum execution time (not typically relevant for HTTP APIs)
  - 10GB maximum memory allocation
  - 1000 concurrent executions (default, can be increased)
  - 6MB request/response payload size limit

- **API Gateway Limits**
  - 10MB maximum payload size
  - 30-second timeout for Lambda integration
  - 10,000 requests per second (default, can be increased)

- **S3 Limits**
  - No practical limits for MockNest use cases
  - 5GB maximum object size (sufficient for response payloads)

- **Bedrock Limits**
  - Model-specific rate limits and quotas
  - Regional availability varies by model

## Regional Considerations
- **Primary Region**: eu-west-1 (closest region to development team)
- **Bedrock Availability**: Ensure chosen region supports required foundation models
- **Multi-region Support**: Not in scope for Phase 1, but architecture supports future expansion