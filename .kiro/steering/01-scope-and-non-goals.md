# Scope and Non-Goals

## In Scope
- Deployment of the full WireMock API on AWS Lambda and API Gateway.
- Support for persisting mock definitions across Lambda executions using Amazon S3.
- Support for HTTP-based APIs, specifically REST, SOAP, and GraphQL-over-HTTP.
- An AI-assisted component that can generate WireMock mappings based on natural language input and API specifications (opt-in deployment option).
- Operation within AWS Free Tier–appropriate usage limits for core runtime, with optional AI features that incur pay-per-use costs.

## Out of Scope
The following items are explicitly out of scope for this project and competition prototype:

- Analyzing historical mock traffic to automatically optimize or suggest new mocks
- Advanced performance optimizations for very large mock sets (e.g. alternative storage or indexing strategies beyond in-memory matching)
- Enterprise-grade authentication, authorization, or multi-tenant SaaS features
- Use of specialized AWS features such as Lambda durable functions or AWS Lambda Managed Instances for running MockNest Serverless.
- Providing a full graphical user interface for managing mocks

## Phase 1 Goals
The initial release focuses on validating the core idea with a working, deployable solution:

- A serverless WireMock runtime running on AWS Lambda
- Persistent availability of mocks between cold starts
- A functional AI-assisted mock generation capability that produces WireMock mappings from API specifications and natural language input.
- Deployment using AWS SAM with a path toward publishing in the AWS Serverless Application Repository (SAR)


## Future Phases
Planned future enhancements include:

### Core Enhancements
- Publishing and maintaining MockNest Serverless as a public SAR application.
- Improving AI-assisted mock generation with additional input sources and refinement options.
- Exploring optimizations for startup time and large mock sets.
- Implementing on-demand mapping loading to reduce cold start times (currently all mappings are loaded at startup).

### Advanced Integration Patterns
- Exploring support for asynchronous interaction patterns, such as event-driven responses using services like Amazon EventBridge.
- Supporting callback-based and webhook-style interactions to better model real-world integration flows.
- Exploring support for additional protocols and integration patterns such as gRPC, depending on feasibility and demand.
- Supporting security-aware mocks, such as configurable OAuth2/JWT-style requirements per endpoint.
- Exploring mTLS support for mock endpoints where feasible within AWS API Gateway/Lambda constraints.

## Non-Goals
MockNest Serverless is intentionally not designed to:

- Act as a full testing framework or test orchestration tool.
- Define or execute test assertions or validate business logic correctness.

## Constraints
The project is shaped by the following constraints:

- Mock definitions are loaded into memory at runtime, which may impact cold-start performance when working with very large numbers of persistent mocks (e.g. more than a few thousand).
- The solution is designed to remain simple, transparent, and deployable within typical Free Tier usage limits.
- The scope is intentionally limited to validate the core concept within the competition timeframe.
