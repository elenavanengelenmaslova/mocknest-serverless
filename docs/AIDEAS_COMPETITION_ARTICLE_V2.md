# AIdeas: MockNest Serverless

## App Category
**Workplace Efficiency**

## My Vision

Testing cloud-native applications becomes difficult when they depend on external APIs.

External APIs may not even be accessible from development or test environments. Even when reachable, their test environments are often unreliable, test data is difficult to control, and maintaining mocks becomes a burden as APIs evolve.

MockNest Serverless is an open-source AWS Serverless Application Repository application that transforms how teams test cloud-native applications by combining a serverless mock runtime with AI-powered capabilities.

### What is Built Today

MockNest provides a WireMock-compatible mock runtime running on AWS Lambda, with mock definitions persisted in Amazon S3. This allows mocks to remain available across Lambda cold starts while keeping the solution simple and cost-efficient within the AWS Free Tier.

Current capabilities include:

- **Serverless Mock Runtime** – A WireMock-compatible API running on AWS Lambda with S3-backed persistence for mock definitions
- **AI-Powered Mock Generation** – Uses Amazon Bedrock (with Amazon Nova Pro as the default model) to generate WireMock mappings from OpenAPI specifications or natural-language descriptions
- **Protocol Support** – REST, GraphQL over HTTP, and SOAP APIs with synchronous request-response patterns
- **AWS-Native Deployment** – AWS SAM templates for deploying the runtime directly into a customer’s AWS account

### The Vision – Intelligent Mock Maintenance

The next phase of MockNest turns mock generation into **intelligent mock maintenance**. As APIs evolve and real traffic patterns emerge, MockNest aims to help teams keep their mocks accurate and comprehensive with minimal manual effort.

Planned capabilities include:

**AI-Assisted Mock Maintenance**
- **Traffic Analysis** – Analyze recorded requests to identify unmatched calls, near-miss patterns, and coverage gaps
- **API Change Detection** – Compare API specification versions and suggest updates to existing mocks
- **Automated Mock Evolution** – Recommend or generate updated mocks when APIs change

**Advanced Interaction Support**
- **Webhook and Callback Simulation** – Support asynchronous interaction patterns commonly used in event-driven systems
- **Streaming Response Support** – Simulate streaming APIs using Server-Sent Events (SSE) and streaming HTTP responses

**AI Agent Testing**
- **MCP Protocol Mocking** – Simulate Model Context Protocol (MCP) servers to enable reliable testing of AI agents and tool-calling workflows

## Why This Matters

Modern cloud-native and serverless applications depend on external services—payment gateways, authentication providers, CRM systems, and many other APIs. Testing these integrations is where teams often struggle.

### The Availability Problem
External APIs are not always accessible from development or test environments. Corporate networks may block outbound internet access, sandboxes may be unstable, and rate limits often make automated testing unreliable. When dependencies are unreachable, integration testing slows down or stops entirely.

### The Control Problem
Even when external APIs are accessible, controlling test data is difficult. Setting up specific scenarios, synchronizing state across multiple systems, and reliably reproducing edge cases quickly becomes complex and time-consuming.

### The Maintenance Problem
APIs evolve constantly. Third-party services add fields, change response formats, or deprecate endpoints. Over time mocks become outdated, tests pass with incorrect assumptions, and integration failures appear only in production.

MockNest addresses these challenges with a serverless mock runtime and AI-assisted mock generation today, and a roadmap toward intelligent mock maintenance.

### How MockNest Helps

**Serverless-Native Runtime (Available Today)**
- Runs entirely within your AWS account on AWS Lambda
- Deploys quickly using AWS SAM
- Persists mocks in Amazon S3
- Operates within AWS Free Tier limits

**AI-Powered Mock Generation (Available Today)**
- **Amazon Nova Pro as Default** – Uses Amazon Nova Pro as the primary supported model for high-quality mock generation
- **Generate from API specifications** – Provide an OpenAPI specification and receive complete WireMock mappings
- **Generate from natural language** – Describe the API behavior and generate realistic mock responses
- **Organized namespaces** – Structure mocks by API and client for multi-team usage

**Intelligent Mock Maintenance (Roadmap)**
- **Traffic analysis** to identify missing scenarios
- **Automated mock evolution** when APIs change
- **Proactive recommendations** based on observed request patterns

MockNest reduces integration testing friction today while laying the foundation for automated mock maintenance as systems evolve.

## Demo time!
PLACEHOLDER YOUTUBE DEMO LINK HERE

## How I Built This

### Building with Kiro AI

I built MockNest with Kiro AI as a development partner. When I started exploring Kiro, I learned that it works best when the project context is clearly defined, so the first step was creating a set of **steering documents**.

These documents provide long-lived context for the project and guide how the AI generates requirements, design and code. In MockNest they include:

- **Product vision** – the problem the project solves and the long-term direction
- **Scope and non-goals** – what the project intentionally does and does not try to solve
- **Architecture** – system structure, clean architecture boundaries, and package layout
- **AWS services** – how cloud components such as Lambda, API Gateway, and S3 are used
- **Development guidelines** – coding standards and workflows for working with Kiro

Writing these documents turned out to be very valuable because it forced me to think carefully about the product direction and system design before writing code.

Kiro also provides a structured workflow where changes are planned using **requirements, design, and tasks** before code generation begins. Each feature, bugfix or refactoring includes checkpoints to verify that acceptance criteria are met.

In practice, I discovered that this workflow works best with **small, focused scope**. When scope became too large and requirements changed during implementation, keeping requirements, design, and tasks aligned became difficult. Breaking work down into smaller, story-sized changes made the process much easier to manage.

Another important part of the workflow was continuously improving the steering documents. Whenever generated code did not match the intended design, I updated the relevant document—refining architecture rules, coding guidelines, or scope decisions. Over time this created a feedback loop where better documentation produced better AI-generated output.

Bug fixing and refactoring also worked well in this workflow because Kiro encourages a **test-first approach**. When fixing issues Kiro would first reproduce the bug with a test or add tests before refactoring to ensure behavior stayed correct.

One practical lesson was to keep the documentation structure manageable. The number of requirements, design, and task documents can grow quickly, so after completing bugfixes or refactorings I often archived those files and kept only documentation that helped explain the product functionality and architecture.

Overall the experience was close to **pair programming**, whereby I preferred working on and reviewing one task at a time.

### Architecture

MockNest uses a simplified clean architecture [3] adapted for serverless systems.
![CleanArchitecture.png](images/CleanArchitecture.png)
The system is organized into three layers:

**Domain layer**

Contains business models and rules related to mock behavior.  
This layer has no framework or cloud dependencies and can be tested in isolation.

**Application layer**

Contains use cases and orchestration logic.  
It defines interfaces for persistence and AI services and coordinates mock generation workflows.

**Infrastructure layer**

Provides cloud-specific implementations such as AWS Lambda handlers, S3 storage adapters, API Gateway integration, and Bedrock access.

Dependencies flow inward from infrastructure to application to domain, keeping the core logic portable and testable.

### AWS Solution Design

[IMAGE PLACEHOLDER – AWS Architecture Diagram]

The current implementation uses a small set of AWS services:

- **AWS Lambda** – serverless runtime hosting the WireMock engine
- **Amazon API Gateway** – HTTP ingress and API key protection
- **Amazon S3** – persistent storage for mock definitions and payloads
- **Amazon Bedrock** – Provides access to Amazon Nova Pro, used for intelligent mock generation and validation

This architecture keeps the runtime lightweight while allowing mocks to persist across Lambda cold starts and deployments.


### Key Development Milestones

MockNest was built in two main milestones.

**1. Serverless Mock Runtime**

The first milestone was creating a WireMock-compatible runtime running on AWS Lambda behind API Gateway.

Mock definitions are persisted in Amazon S3 so they remain available across cold starts and deployments.

This provides a fully serverless mock environment that can run directly inside a developer’s AWS account.

**2. AI-Powered Mock Generation**

The second milestone added an AI interface powered by Amazon Nova Pro on Amazon Bedrock, capable of generating mocks from OpenAPI specifications or natural-language descriptions.

Generated mappings are automatically validated.  
If validation fails, the errors are sent back into the generation workflow so the AI can correct the mappings before returning the final result.

### Quality and Delivery

To maintain reliability and code quality:

- **90% code coverage** is enforced using Kover
- **Integration tests** run with TestContainers and LocalStack
- **GitHub Actions** provide automated build and validation pipelines

Each milestone was implemented incrementally and validated before moving to the next.

### Runtime Design Considerations

The runtime prioritizes predictable behavior for integration testing workloads.

Mock definitions are stored in Amazon S3 and loaded during startup so the runtime can behave deterministically when mocks are created or updated.

Future versions will introduce configurable scaling options and additional runtime optimizations as the project evolves.

## What I Learned

Building MockNest with AI assistance changed how I approach software development.

### Context matters more than prompts

The most important lesson was that AI works best with strong context. Instead of relying on individual prompts, I started the project by writing steering documents that describe the product vision, architecture, AWS usage, and development guidelines.

These documents provided persistent context for Kiro so it could generate code and design proposals aligned with the intended system architecture. When generated output did not fully match my expectations, improving the steering documents consistently improved the next results.

### Smaller features work better with AI

Breaking work into smaller, clearly defined features made the development process much smoother. Smaller deliverables are easier to review, adjust, and regenerate when requirements change.

Trying to generate large features at once makes it harder to maintain oversight and often leads to more corrections later.

### Clear architecture improves AI output

Using a clean architecture structure turned out to be very helpful when working with AI-assisted development. Clear boundaries between domain logic, application orchestration, and infrastructure implementations made it easier for Kiro to understand where new code should live.

This helped keep business logic independent from AWS-specific code and made the system easier to test.

### Integration tests reveal real problems

Integration tests using TestContainers and LocalStack proved extremely valuable. They validate real interactions with AWS services such as S3 and Lambda and often expose issues that unit tests alone would not detect.

Testing the system against real service behavior increased confidence that the runtime would behave correctly once deployed.

### Build the foundation before adding AI

One important product lesson was to build the core runtime first. Once the serverless mock runtime was stable and deployed, adding AI-powered mock generation became much easier and more meaningful.

This approach ensured that AI features were built on top of a working system instead of theoretical workflows.

## What’s Next

The current version of MockNest focuses on the core serverless runtime and AI-assisted mock generation. The next phase will extend the platform toward intelligent mock management.

Planned improvements include:

**Traffic Analysis and Coverage Insights**  
Analyze recorded request traffic to identify missing mocks, near-miss patterns, and gaps in API coverage.

**Automated Mock Evolution**  
Detect changes in API specifications and suggest updates to existing mocks so test environments stay synchronized with evolving APIs.

**Support for Additional Interaction Patterns**  
Expand support for asynchronous and streaming interactions such as webhooks, callbacks, and Server-Sent Events (SSE).

**MCP (Model Context Protocol) Mocking**  
Add support for mocking MCP servers and tools to help teams test AI agents and LLM-based systems.

The long-term goal is to evolve MockNest from a serverless mock runtime into an intelligent platform that helps teams keep their integration tests accurate as APIs and systems continue to evolve.

References
[1] WireMock
[2] Kiro
[3] Clean Architecture for Serverless