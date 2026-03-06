# AIdeas: MockNest Serverless

## App Category
**Workplace Efficiency**

## My Vision

MockNest Serverless is an open-source AWS Serverless Application Repository app that transforms how teams test cloud-native applications by combining a serverless mock runtime with AI-powered capabilities.

**What's Built Today:**

A WireMock-compatible API runtime running on AWS Lambda with mock definitions persisted in Amazon S3. Mocks remain available across Lambda cold starts while operating within AWS Free Tier limits.

Current capabilities:
- **Serverless Mock Runtime**: WireMock-compatible API on AWS Lambda with S3 persistence
- **AI-Powered Mock Generation**: Using Amazon Bedrock to generate comprehensive mocks from OpenAPI specifications and natural language descriptions
- **Protocol Support**: REST, GraphQL, and SOAP with synchronous request-response patterns
- **AWS-Native Deployment**: SAM templates for deployment within customer AWS accounts

**The Vision - Intelligent Mock Maintenance:**

The roadmap extends MockNest into an intelligent mock management platform:
- **Traffic Analysis**: Identify unmatched requests, near-miss patterns, and coverage gaps
- **API Change Detection**: Compare specification versions and suggest mock updates
- **Automated Mock Evolution**: Keep test suites synchronized with evolving APIs
- **Webhook/Callback Support**: Asynchronous interaction patterns
- **Streaming Response Support**: SSE and streaming HTTP for real-time and AI application testing
- **MCP Protocol Support**: Model Context Protocol mocking for AI agent testing

## Why This Matters

Modern serverless applications integrate with payment gateways, authentication services, CRM systems, and countless external APIs. Testing these integrations is where teams struggle.

**The Availability Problem:**
External APIs are often unavailable in test environments—no outbound internet access, unreliable sandboxes, or rate-limited test environments. You can't test integrations when dependencies aren't accessible.

**The Control Problem:**
Even when APIs are accessible, controlling test data is difficult. Synchronizing state across systems, setting up specific scenarios, and reproducing edge cases reliably is a nightmare.

**The Maintenance Problem (Future Focus):**
APIs evolve. Third-party services update contracts, add fields, deprecate endpoints. Mocks become stale, tests pass with outdated assumptions, and production breaks. MockNest's roadmap addresses this with traffic analysis and automated evolution.

### How MockNest Solves This

**Serverless-Native Architecture (Implemented):**
- Runs entirely within your AWS account on Lambda
- Deploys in minutes via AWS SAM
- Operates within AWS Free Tier limits
- No external network dependencies

**AI-Powered Mock Generation (Implemented):**
- **From API Specifications**: Provide an OpenAPI spec, get complete WireMock mappings with realistic data and error cases
- **From Natural Language**: Describe what you need in plain English, get comprehensive mocks
- **Namespace Organization**: Mocks organized by API and client for multi-tenant scenarios

**Intelligent Mock Maintenance (Roadmap):**
- **Traffic Analysis**: Identify coverage gaps from real request patterns
- **Automated Evolution**: Detect API changes and suggest mock updates
- **Proactive Recommendations**: Suggest new mocks based on usage patterns

MockNest makes mock creation easier with AI today, with plans for automated maintenance tomorrow—helping teams catch integration issues before production without complex infrastructure or vendor costs.

## How I Built This

### Development with Kiro AI

I built MockNest using Kiro AI as a development partner through an iterative, steering document-driven approach.

**Steering Documents as Foundation:**
I started by creating comprehensive steering documents that serve as context for Kiro:
- **00-vision.md**: Product vision and value proposition
- **01-scope-and-non-goals.md**: What's in scope and explicit non-goals
- **02-architecture.md**: System architecture and design decisions
- **03-aws-services.md**: AWS service usage and configuration
- **04-market-impact.md**: Market analysis and competitive positioning
- **05-kiro-usage.md**: Development workflow and coding standards

**Iterative Refinement:**
Every time I corrected Kiro's output, I updated the relevant steering document. For example, when Kiro generated code that didn't follow my preferred patterns, I updated `05-kiro-usage.md` with specific examples. This created a feedback loop where Kiro's output improved continuously.

**Spec-Driven Workflow:**
For each feature, I followed a structured process:
1. **Requirements**: Define what needs to be built
2. **Design**: Create technical design with architecture decisions
3. **Tasks**: Break down into specific implementation steps
4. **Implementation**: Kiro generates code following steering documents

**Small Deliverables:**
I focused on incremental development with small, complete features. Smaller features are easier to change—if requirements shift during task definition, it's manageable. Large features become unwieldy when you need to revise requirements after defining detailed tasks.

### Technology Choices

**Kotlin**: Chosen for its concise syntax, null safety, and multiplatform capabilities. Kotlin can target JVM, Node.js, and native platforms, providing flexibility for future optimizations.

**Koog**: Kotlin-based AI agent framework for implementing mock generation logic. Integrates cleanly with Amazon Bedrock while keeping AI logic decoupled from infrastructure.

**Gradle with Kotlin DSL**: Type-safe build configuration in the same language as application code.

**WireMock-compatible API**: Maintains compatibility with the proven WireMock engine while allowing future extensions for streaming and MCP support.

### Architecture

**Clean Architecture for Serverless:**

![Clean Architecture Diagram](images/CleanArchitecture.png)

Three-layer architecture with strict dependency flow:

**Domain Layer**: Business models and rules with no framework dependencies. Pure business logic testable in isolation.

**Application Layer**: Use cases and orchestration. Defines interfaces for persistence and AI services. Contains the Koog-based AI agent for mock generation.

**Infrastructure Layer**: AWS-specific implementations. Lambda handlers, S3 adapters, Bedrock integration. Only layer depending on AWS SDKs.

Dependencies flow inward: infrastructure → application → domain. This keeps business logic portable and testable without AWS dependencies.

**AWS Solution Design:**

![AWS Architecture](images/aws-architecture-diagram.png)

- **AWS Lambda**: Serverless compute for mock runtime
- **Amazon API Gateway**: HTTP ingress with API key authentication
- **Amazon S3**: Persistent storage for mocks and payloads
- **Amazon Bedrock**: AI model access for mock generation

### Quality & CI/CD

- **90% Code Coverage**: Enforced via Kover across the entire project
- **TestContainers with LocalStack**: Integration testing against containerized AWS services
- **GitHub Actions**: Automated build, test, and deployment pipelines
- **Incremental Delivery**: Each feature fully tested and validated before moving to the next

### Current Limitations

**Cold Start Performance**: All mock mappings are loaded into memory at startup, impacting cold start time with large mock sets. Future optimizations include on-demand loading and caching strategies.

**JVM Runtime**: Currently runs on JVM, which adds to cold start time. Plans include AWS Lambda SnapStart support and exploring Kotlin Native compilation for faster cold starts.

These limitations are acceptable for typical integration testing scenarios (dozens to hundreds of mocks) and will be addressed as usage scales.

## Demo

### Deployment

```bash
# Build and deploy
./gradlew build
cd deployment/aws/sam
sam build && sam deploy --guided
```

### Basic Mock Creation

```bash
export MOCKNEST_URL="https://your-api.execute-api.eu-west-1.amazonaws.com/prod"
export API_KEY="your-api-key"

# Create a mock
curl -X POST "$MOCKNEST_URL/__admin/mappings" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "request": {"method": "GET", "url": "/api/users/123"},
    "response": {
      "status": 200,
      "headers": {"Content-Type": "application/json"},
      "body": "{\"id\": 123, \"name\": \"John Doe\"}"
    }
  }'

# Test the mock
curl "$MOCKNEST_URL/api/users/123" -H "x-api-key: $API_KEY"
```

### AI-Powered Mock Generation

```bash
# Generate from OpenAPI spec
curl -X POST "$MOCKNEST_URL/ai/generation/from-spec" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {"apiName": "petstore", "client": "demo"},
    "specification": "openapi: 3.0.0...",
    "format": "OPENAPI_3",
    "description": "Generate 5 realistic pets with error cases"
  }'
```

Response includes generated WireMock mappings with realistic data, proper error cases, and organized namespaces.

### Natural Language Generation

```bash
# Generate from description
curl -X POST "$MOCKNEST_URL/ai/generation/from-description" \
  -H "x-api-key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": {"apiName": "payment-gateway", "client": "demo"},
    "description": "Create payment API with create, status, refund endpoints. Include success cases for credit card, PayPal, bank transfer. Include error cases for insufficient funds, invalid card, expired card."
  }'
```

MockNest generates comprehensive mappings with multiple payment methods, realistic data, proper error responses, and correct HTTP status codes.

## What I Learned

### AI-Assisted Development

**Steering Documents are Critical**: Clear, comprehensive steering documents make all the difference. When Kiro has good context about architecture, coding standards, and project goals, generated code quality improves dramatically.

**Continuous Training**: Every correction is an opportunity to improve Kiro's context. Updating steering documents after corrections creates a feedback loop where subsequent generations get better.

**Small Features Win**: Smaller features are easier to manage with AI assistance. When requirements change during task definition, small features remain flexible. Large features become difficult to oversee and adjust.

**Spec-Driven Development Works**: The structured workflow (requirements → design → tasks → implementation) provides clear checkpoints. It's easier to correct course at the requirements stage than after code is written.

### Technical Insights

**Clean Architecture in Serverless Pays Off**: Initially felt like overhead, but as the codebase grew, the benefits became clear. Testing business logic without AWS dependencies is faster and more reliable. AI code generation works better with clear layer separation.

**Integration Tests > Unit Tests**: Integration tests with TestContainers and LocalStack catch real issues. Testing actual S3 operations and Lambda invocations reveals problems unit tests miss.

**90% Coverage Drives Quality**: Targeting 90% coverage forces thinking about edge cases and error handling. Integration tests provide more value than artificial per-module coverage targets.

### Product Insights

**Start with Core, Add AI Later**: Building the core runtime first, then adding AI features, was the right approach. Having a solid foundation makes AI features more valuable.

**Traffic Analysis Will Drive Value**: The next phase (traffic analysis) will likely provide more value than initial generation. Identifying coverage gaps and suggesting improvements based on real usage addresses the maintenance problem teams struggle with most.

**Free Tier Compatibility Matters**: Designing for AWS Free Tier removes adoption barriers. Teams can experiment without budget approval.

---

**What's Live Today:**
- WireMock-compatible API runtime on AWS Lambda with S3 persistence
- AI-powered mock generation from OpenAPI specs and natural language
- Clean architecture with 90%+ test coverage
- GitHub Actions CI/CD pipeline
- AWS SAM deployment ready for AWS Serverless Application Repository

**What's Next:**
- Traffic analysis and coverage gap detection
- Automated mock evolution with API change detection
- Webhook/callback support for asynchronous patterns
- Streaming response support for real-time applications
- MCP protocol support for AI agent testing

The foundation is solid, the vision is clear, and the roadmap addresses real problems teams face every day.
