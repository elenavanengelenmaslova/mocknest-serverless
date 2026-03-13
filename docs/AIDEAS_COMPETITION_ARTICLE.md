MockNest Serverless is an AWS-native API mocking platform that runs on AWS Lambda and uses Amazon Bedrock to generate realistic test mocks from API specifications and natural-language descriptions. It helps cloud-native teams test integrations even when external APIs are unavailable or difficult to control.

# App Category
**Workplace Efficiency**

# My Vision

MockNest Serverless is an open-source AWS Serverless Application Repository application that transforms how teams test cloud-native applications by combining a serverless mock runtime with AI-powered capabilities.

## What is Built Today

MockNest provides a WireMock[3]-compatible mock runtime running on AWS Lambda with S3-backed persistence, allowing mocks to remain available across cold starts while operating within AWS Free Tier limits. It uses Amazon Bedrock with Nova Pro to generate validated WireMock mappings from OpenAPI specifications or natural-language descriptions, supporting REST, GraphQL over HTTP, and SOAP APIs. Teams can deploy using AWS SAM templates or one-click installation from the AWS Serverless Application Repository.

## The Vision – Intelligent Mock Maintenance

The next phase of MockNest turns mock generation into **intelligent mock maintenance**. As APIs evolve and real traffic patterns emerge, MockNest aims to help teams keep their mocks accurate and comprehensive with minimal manual effort.

Planned capabilities include:

**AI-Assisted Mock Maintenance**
- **Traffic Analysis** – Analyze recorded requests to identify unmatched calls, near-miss patterns, and coverage gaps
- **Automated Mock Evolution** – Recommend or generate updated mocks when APIs change

**Advanced Interaction Support**
- **Webhook and Callback Simulation** – Support asynchronous interaction patterns commonly used in event-driven systems
- **Streaming Response Support** – Simulate streaming APIs using Server-Sent Events (SSE) and streaming HTTP responses

**AI Agent Testing**
- **MCP Protocol Mocking** – Simulate Model Context Protocol (MCP) servers to enable reliable testing of AI agents and tool-calling workflows

# Why This Matters

Modern cloud-native and serverless applications depend on external services—payment gateways, authentication providers, CRM systems, and many other APIs. Testing these integrations is where teams often struggle.

## The Availability Problem
External APIs are often inaccessible from test environments due to network restrictions or instability, causing integration testing to slow down or stop entirely.

## The Control Problem
Controlling test data across external APIs is difficult. Setting up specific scenarios and reproducing edge cases becomes complex and time-consuming.

## The Maintenance Problem
APIs evolve constantly. Over time mocks become outdated, tests pass with incorrect assumptions, and integration failures appear only in production.

MockNest addresses these challenges with a serverless mock runtime and AI-assisted mock generation today, and a roadmap toward intelligent mock maintenance.

## How MockNest Helps

MockNest runs entirely within your AWS account, deploying quickly via AWS SAR or SAM and operating within Free Tier limits for typical testing workloads. The AI-powered generation uses Amazon Nova Pro to create mocks from OpenAPI specifications or natural language descriptions, with organized namespaces for multi-team usage.

The roadmap extends these capabilities with traffic analysis to identify missing scenarios, automated mock evolution when APIs change, and proactive recommendations based on observed request patterns. This reduces integration testing friction today while building toward intelligent mock maintenance as systems evolve.

# Demo

The demo shows the complete workflow: generating WireMock mocks from natural language using Amazon Bedrock, importing them via the WireMock-compatible API, testing the mock endpoint directly, and finally using it in a pet adoption newsletter application.

## AI Mock Generation Request

![AI Mock Generation Request](https://raw.githubusercontent.com/elenavanengelenmaslova/mocknest-serverless/refs/heads/main/docs/images/demo/ai-request.png)
*Requesting AI-generated mocks using a natural language description in Postman*

We send a natural language description to MockNest's AI endpoint, asking it to generate mocks for a pet adoption API with realistic data.

## Generated WireMock Mappings

![Generated WireMock Mappings](https://raw.githubusercontent.com/elenavanengelenmaslova/mocknest-serverless/refs/heads/main/docs/images/demo/ai-response.png)
*Amazon Nova Pro generates validated WireMock mappings with realistic pet data*

The AI generates complete mappings with persistent storage enabled by default. If validation fails, the system automatically retries to produce correct output.

## Importing via WireMock-Compatible API

When satisfied with the generated mocks, we import them using the WireMock-compatible API, demonstrating compatibility with existing WireMock tooling and workflows.

## Testing the Mock Endpoint

We test the endpoint directly to verify it returns realistic pet adoption data, confirming the mock behaves correctly before client integration.

## Application in Action

![Pet Newsletter Application](https://raw.githubusercontent.com/elenavanengelenmaslova/mocknest-serverless/refs/heads/main/docs/images/demo/newsletter.png)
*Pet adoption newsletter application using AI-generated mocks*

The application calls the mock endpoint to generate a newsletter email showing available pets for adoption.

## Full Demo Video

PLACEHOLDER YOUTUBE DEMO LINK HERE

**Want to try MockNest yourself?** Get it from the AWS Serverless Application Repository[2] or deploy with SAM from GitHub[1].

# How I Built This

## Building with Kiro AI

I built MockNest with Kiro[4] as a development partner. I created steering documents to provide long-lived context that guide Kiro's code generation:

- **Product vision** – the problem the project solves and long-term direction
- **Scope and non-goals** – what the project intentionally does and does not solve
- **Architecture** – system structure, clean architecture boundaries, and package layout
- **AWS services** – how Lambda, API Gateway, S3, and Bedrock are used
- **Market impact** – target users, use cases, and value proposition
- **Development guidelines** – coding standards and work instructions for Kiro

Kiro's structured workflow plans changes through requirements, design, and tasks before code generation, with checkpoints to verify acceptance criteria are met. Using this workflow, I built the serverless runtime with WireMock integration, S3 persistence, and AI-powered generation. MockNest uses Kotlin[6] for its multiplatform capabilities and AWS SDK support[7], with Koog[8] providing Kotlin-native AI agent orchestration for Bedrock integration.

## Architecture

MockNest uses a simplified clean architecture [5] adapted for serverless systems.
The system is organized into three layers:

![Clean Architecture](https://raw.githubusercontent.com/elenavanengelenmaslova/mocknest-serverless/refs/heads/main/docs/images/CleanArchitecture.png)

**Domain layer**

Contains business models and rules related to mock behavior.
This layer has no framework or cloud dependencies and can be tested in isolation.

**Application layer**

Contains use cases and orchestration logic.
It defines interfaces for persistence and AI services and coordinates mock generation workflows.

**Infrastructure layer**

Provides cloud-specific implementations such as AWS Lambda handlers, S3 storage adapters, API Gateway integration, and Bedrock access.

Dependencies flow inward from infrastructure to application to domain, keeping the core logic portable and testable.

## AWS Solution Design

![AWS Solution Design](https://raw.githubusercontent.com/elenavanengelenmaslova/mocknest-serverless/refs/heads/main/docs/images/SolutionDesign.png)

The implementation uses AWS Lambda for the serverless runtime, API Gateway for HTTP ingress with API key protection, S3 for persistent storage, and Bedrock for AI-powered generation. This keeps the runtime lightweight while ensuring mocks persist across deployments.

The runtime uses ARM64 architecture for improved price/performance. Cold start performance currently varies based on the number of mock definitions loaded at startup, with SnapStart optimization planned to further reduce latency.

## Security Considerations

All endpoints are protected with API key authentication through Amazon API Gateway. Since MockNest is a mock API intended for test environments, I chose to start with basic API key security with planned expansion for additional authentication mechanisms in the future.

For the AWS Serverless Application Repository publication, the SAM templates follow least privilege principles, ensuring Lambda functions and other components receive only the minimum IAM permissions required for their specific operations.

## Current Limitations

The current release is optimized for integration testing with moderate mock catalogs. The runtime uses API Gateway throttling to comply with AWS Free Tier terms, keeping the solution simple and cost-efficient. Mock definitions are loaded from S3 at startup, affecting cold start performance with large catalogs. Request logging data is in-memory and only available during the current Lambda instance lifecycle. Future releases will implement shared cache storage to eliminate startup loading and enable persistent request tracking, unlocking capabilities like comprehensive coverage analysis and intelligent mock evolution.

## Key Development Milestones

MockNest was built across three milestones. The first established the WireMock-compatible runtime on AWS Lambda with S3 persistence. The second added AI-powered mock generation using Amazon Nova Pro, with automatic validation and error correction in the generation workflow. The third prepared the public release through the AWS Serverless Application Repository, including comprehensive documentation, SAR metadata, security validation ensuring least privilege IAM policies, and multi-region deployment testing.

## Quality and Delivery

To maintain reliability and code quality:

- **80% code coverage** is enforced using Kover
- **Integration tests** run with TestContainers and LocalStack[9]
- **GitHub Actions** provide automated build and validation pipelines

Each milestone was implemented incrementally and validated before moving to the next.

# What I Learned

Building MockNest with AI assistance changed how I approach software development.

## Context matters more than prompts

The most important lesson was that AI works best with strong unambiguous context with boundaries. Instead of relying on individual prompts, I started the project by writing steering documents that describe the product vision, architecture, AWS usage, and development guidelines.

These documents provided persistent context for Kiro so it could generate requirements, design and code aligned with the intended system architecture and vision. When generated output did not fully match my expectations, improving the steering documents consistently improved the next results. Over time this created a feedback loop where better documentation produced better AI-generated output.

## Smaller features work better with AI

Breaking work into smaller, clearly defined features made the development process much smoother. Smaller deliverables are easier to review, adjust, and regenerate when requirements change.

In practice, I discovered that this workflow works best with small, focused scope. When scope became too large and requirements changed during implementation, keeping requirements, design, and tasks aligned became complicated. Breaking work down into smaller, story-sized changes made the process much easier to manage.

Trying to generate large features at once makes it harder to maintain oversight and often leads to more corrections later. The experience was close to pair programming, where I preferred working on and reviewing one task at a time.

## Clear architecture improves AI-generated code

Using a clean architecture structure turned out to be very helpful when working with AI-assisted development. Clear boundaries between domain logic, application orchestration, and infrastructure implementations made it easier for Kiro to understand where new code should live.

This helped keep business logic independent from AWS-specific code and made the system easier to test.

## Test-first approach catches issues early

Bug fixing and refactoring worked well because Kiro encourages a test-first approach, reproducing bugs with tests before fixing them to ensure behavior stays correct. Integration tests using TestContainers and LocalStack[9] proved extremely valuable, validating real interactions with AWS services and exposing issues that unit tests alone would not detect.

## Requirements review is easier than code review

One of the most valuable insights was discovering that reviewing requirements is much easier than reviewing code. I preferred the requirements-first flow over "vibe coding" for almost everything - not just features, but also bugs and refactorings.

Vibe coding happened only in very rare cases to fix something very small. Code review is still mandatory, but it's much easier to conduct when requirements are sound because you've already prevented many issues from happening.

Reviewing tasks was also very helpful because you see the concrete steps that will be taken to implement requirements. This gives you a chance to catch potential problems before any code is written.

Setting guardrails proved important - usually in steering documents, but also in requirements themselves. Specifying what to avoid, what patterns to use, and what to check after implementation helped keep the AI on track and reduced the need for corrections.

# What's Next

The roadmap[10] focuses on evolving MockNest toward intelligent mock management, with traffic analysis to identify missing mocks and coverage gaps, automated mock evolution when APIs change, support for asynchronous patterns like webhooks and Server-Sent Events, and MCP protocol mocking for AI agent testing. The long-term goal is creating an intelligent platform that helps teams keep integration tests accurate as APIs and systems evolve.

# References
[1] MockNest Serverless GitHub Repository - https://github.com/elenavanengelenmaslova/mocknest-serverless

[2] MockNest Serverless on AWS SAR - https://serverlessrepo.aws.amazon.com/applications/eu-west-1/021259937026/MockNest-Serverless

[3] WireMock - https://github.com/wiremock/wiremock

[4] Kiro - https://kiro.dev/

[5] Clean Architecture for Serverless - https://medium.com/nntech/keeping-business-logic-portable-in-serverless-functions-with-clean-architecture-bd1976276562

[6] Kotlin - https://kotlinlang.org/

[7] AWS SDK for Kotlin - https://aws.amazon.com/sdk-for-kotlin/

[8] Koog - https://github.com/JetBrains/koog

[9] LocalStack - https://www.localstack.cloud/

[10] MockNest Roadmap - https://github.com/users/elenavanengelenmaslova/projects/3

[10] MockNest Roadmap - https://github.com/users/elenavanengelenmaslova/projects/3