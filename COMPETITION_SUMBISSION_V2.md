# Which category best describes your idea?

Workplace Efficiency

# In one or two sentences, what's your big idea?

MockNest Serverless is an open-source AWS Serverless Application Repository app that uses AI to generate mocks from API specs and natural language, analyze traffic for coverage gaps, and maintain mocks as APIs evolve. It supports REST, GraphQL, SOAP, MCP, and agent-to-agent protocols with synchronous request-response patterns as well as asynchronous interactions.

# Tell us about your vision – what exactly will you build?

MockNest Serverless provides a serverless mock runtime built on WireMock, maintaining API compatibility while adding AI-powered APIs. It runs on AWS Lambda with mock definitions and traffic stored in S3, ensuring persistence across invocations.

MockNest supports REST, GraphQL, SOAP, and AI agent protocols - MCP and A2A. It handles synchronous and asynchronous interactions through webhooks and callbacks to AWS messaging services such as EventBridge.

AI capabilities powered by Amazon Bedrock : 
- Generate mocks from API specifications, e.g. OpenAPI, GraphQL schemas, WSDL, and natural language, - Analyze traffic to identify unmatched requests, near-miss patterns, and coverage gaps
- Detect API specification changes to suggest mock updates.

The solution deploys through AWS Serverless Application Repository, CDK, SAM, and Terraform, running entirely within the customer's AWS account. It is designed to operate within AWS Free Tier limits.

# How will your solution make a difference?

MockNest Serverless transforms integration testing for cloud-native applications by addressing two main challenges: creating mocks for complex dependencies and maintaining them as APIs evolve.

Serverless applications emphasize integration testing with third-party systems. MockNest enables testing when these systems are inaccessible, have difficult-to-configure test data, or require synchronized state. Teams define controlled mock scenarios to test complex flows and edge cases.

AI capabilities reduce the effort to build and maintain mock suites. Teams generate mocks from API specifications and natural language. MockNest analyzes traffic to identify coverage gaps and suggests new mocks. As APIs evolve, it detects changes and suggests updates.

The solution supports AI agent protocols - MCP and agent-to-agent communication.

Developers, test engineers, and QA teams benefit from faster setup, reduced maintenance, and comprehensive testing without depending on third-party services.

# What's your game plan for building this?

The project follows an incremental development approach using Kiro AI as a development partner. Each feature will be fully completed, tested, and deployed before moving to the next, ensuring a reliable MVP is released early.

Development with Kiro: Kiro AI assists throughout the lifecycle using a spec-driven workflow, helping generate requirements, create designs, and produce tasks. Kiro assists with Kotlin code generation following clean architecture principles, test creation achieving 90 procent coverage, and documentation. As I review and correct Kiro's work, I refine steering documents, improving context for future development.

Development Infrastructure: Hosted on GitHub with GitHub Actions for CI and CD. GitHub Issues and Projects track tasks and bugs. SAM for Infrastructure as Code.

Phase 1 - Core Runtime and AI Generation: Serverless WireMock runtime in Kotlin supporting REST, GraphQL, SOAP, and webhooks. Mock persistence via S3 and AI-powered generation using Amazon Bedrock and Koog framework. Published to AWS SAR.

Phase 2 - Mock Evolution and MCP: Automated API change detection and mock update suggestions. MCP protocol and asynchronous patterns with EventBridge. AWS CDK deployment.

Phase 3 - Traffic Intelligence and A2A: Traffic analysis to identify coverage gaps and near-misses. Agent-to-agent protocol support. Terraform deployment.

Each phase will be fully deployed and validated before proceeding, keeping the solution reliable and AWS Free Tier compatible.

# Which AWS AI services will power your solution? (optional)

Amazon Bedrock (optional, for AI-assisted mock generation and analysis)

# What other AWS Free Tier Services will you employ? (optional)

AWS Lambda

Amazon API Gateway

Amazon S3

Amazon CloudWatch