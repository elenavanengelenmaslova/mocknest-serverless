# Scope and Non-Goals

## In Scope
- Deployment of the full WireMock API on AWS Lambda and API Gateway
- Support for persisting mock definitions across Lambda executions using Amazon S3
- Support for HTTP-based APIs, specifically REST, SOAP, and GraphQL-over-HTTP
- Supporting callback-based and webhook-style interactions to better model real-world integration flows

**AI-Powered Mock Intelligence (Core Features):**
- Traffic recording and analysis capabilities leveraging WireMock's built-in request logging
- On-demand traffic analysis for user-specified timeframes to identify mock coverage gaps
- Mock suggestion engine that analyzes traffic patterns, near-misses, and unmatched requests
- Consumer-side contract coverage analysis with configurable scope (defaults to full API specification)
- Automated mock evolution through API specification updates and traffic pattern analysis
- Mock optimization recommendations based on real usage patterns

**AI-Assisted Mock Generation:**
- AI-powered mock generation from API specifications and natural language descriptions 
- Intelligent mock refinement and enhancement suggestions

**Traditional Mock Management:**
- Manual mock creation and management through standard WireMock admin API
- Import/export capabilities for mock definitions
- Mock versioning and rollback capabilities

## Out of Scope
The following items are explicitly out of scope for the first phases of the project:

- Advanced performance optimizations for very large response payload (e.g. more than 1 GB)
- Enterprise-grade authentication, authorization, or multi-tenant SaaS features
- Providing a full graphical user interface for managing mocks
- gRPC protocol

## Phase 1 Goals
The initial release focuses on validating the core idea with a working, deployable solution that demonstrates intelligent mock management:

**Core Runtime:**
- A serverless WireMock runtime running on AWS Lambda
- Persistent availability of mocks between cold starts
- Supporting callback-based and webhook-style interactions to better model real-world integration flows
- Deployment using AWS SAM with a path toward publishing in the AWS Serverless Application Repository (SAR)

**AI-Powered Intelligence:**
- Traffic recording and basic analysis capabilities
- On-demand mock gap analysis for specified timeframes
- Mock suggestion engine based on unmatched requests and traffic patterns
- AI-assisted mock generation from API specifications 

**User Experience:**
- Intuitive admin API for triggering analysis and retrieving suggestions
- Seamless integration with existing WireMock workflows

## Future Phases
Planned future enhancements include:

### Core Enhancements
- Improving AI-assisted mock generation with additional input sources and refinement options.
- AI-assisted contract coverage analysis
- Storage retention policies and automated cleanup for request logs and mock definitions
- Exploring optimizations for startup time and large mock sets.
- Implementing on-demand mapping loading to reduce cold start times (currently all mappings are loaded at startup).

### Advanced Integration Patterns
- Support for asynchronous interaction patterns, such as event-driven responses using services like Amazon EventBridge.
- Supporting security-aware mocks, such as configurable OAuth2/JWT-style requirements per endpoint.
- Supporting rate limiting simulation to test application resilience and retry logic.
- mTLS support for mock endpoints where feasible within AWS API Gateway/Lambda constraints.
- MCP (Model Context Protocol) mocking leveraging AWS API Gateway's native MCP proxy support and Lambda durable functions for AI agent testing scenarios.

## Non-Goals
MockNest Serverless is intentionally not designed to:

- Act as a full testing framework or test orchestration tool.
- Define or execute test assertions or validate business logic correctness.

## Constraints
The project is shaped by the following constraints:

- Mock definitions are loaded into memory at runtime, which may impact cold-start performance when working with very large numbers of persistent mocks (e.g. more than a few thousand).
- The solution is designed to remain simple, transparent, and deployable within typical Free Tier usage limits.
- Performance targets and benchmarks will be established based on initial release testing (TODO: placeholder for performance test results).
