# Bugfix Requirements Document

## Introduction

The AI specification code in `BedrockServiceAdapter` (infrastructure layer) violates clean architecture principles by containing prompt building logic that should reside in the application layer. According to the architecture steering documents, the infrastructure layer should only contain cloud-specific AWS SDK integration code.

This architectural violation creates several problems:
- Prompt building logic is tightly coupled to AWS Bedrock implementation
- Future support for alternative AI providers (e.g., Azure OpenAI, Google Vertex AI) would require duplicating prompt building logic
- Prompt text is hardcoded in infrastructure code, making it difficult to maintain and test independently
- The codebase does not follow its own documented clean architecture principles

**Scope of this refactor**: Move prompt building logic and prompt text out of infrastructure layer into application layer. This refactor does NOT change response parsing, mock creation, or other business logic - those remain in their current locations.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN BedrockServiceAdapter is examined THEN the system contains prompt building logic (buildSpecWithDescriptionPrompt, buildCorrectionPrompt) in the infrastructure layer

1.2 WHEN BedrockServiceAdapter is examined THEN the system contains hardcoded prompt text strings embedded directly in infrastructure code

1.3 WHEN BedrockServiceAdapter.createAgent is examined THEN the system contains hardcoded system prompt text embedded directly in infrastructure code

1.4 WHEN AIModelServiceInterface is examined THEN the system exposes high-level operations (generateMockFromSpecWithDescription, correctMocks) that include prompt building in infrastructure layer

### Expected Behavior (Correct)

2.1 WHEN BedrockServiceAdapter is examined THEN the system SHALL contain only AWS Bedrock SDK integration code (BedrockRuntimeClient calls, Koog client initialization, low-level model invocation)

2.2 WHEN application layer is examined THEN the system SHALL contain prompt building logic in application layer services

2.3 WHEN application module resources are examined THEN the system SHALL store all prompt text in external resource files (software/application/src/main/resources/prompts/*.txt) with parameter placeholders for injection

2.4 WHEN system prompt for agent creation is examined THEN the system SHALL load system prompt text from external resource file (software/application/src/main/resources/prompts/system-prompt.txt)

2.5 WHEN AIModelServiceInterface is examined THEN the system SHALL expose low-level AI model operations (sendPrompt) without prompt building logic

2.6 WHEN application layer use cases are examined THEN the system SHALL orchestrate AI model calls by building prompts, invoking model, and processing responses

### Unchanged Behavior (Regression Prevention)

3.1 WHEN generateMockFromSpecWithDescription is called THEN the system SHALL CONTINUE TO generate mocks from API specifications with descriptions

3.2 WHEN correctMocks is called THEN the system SHALL CONTINUE TO correct invalid mocks based on validation errors

3.3 WHEN prompts are built THEN the system SHALL CONTINUE TO generate the same prompt content with the same parameter values as before externalization

3.4 WHEN AI model responses are received THEN the system SHALL CONTINUE TO parse responses in the same way (response parsing logic remains unchanged in this refactor)

3.5 WHEN mocks are created THEN the system SHALL CONTINUE TO create mocks in the same way (mock creation logic remains unchanged in this refactor)

3.6 WHEN mock generation fails THEN the system SHALL CONTINUE TO log errors and return empty lists without throwing exceptions
