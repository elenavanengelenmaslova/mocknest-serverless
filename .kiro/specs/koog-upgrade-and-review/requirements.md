# Requirements Document

## Introduction

This feature upgrades the Koog AI agent framework from version 0.6.2 to 0.8.0 and performs a comprehensive review of how MockNest Serverless uses Koog. The review covers six areas: (1) upgrading the Koog dependency and adapting to breaking changes across 0.7.x and 0.8.0, (2) reviewing the overall Koog usage patterns to ensure they follow current best practices, (3) reviewing the eval-driven development setup (Bedrock prompt eval tests) and how usage, costs, and time are collected for Bedrock calls, (4) verifying that AWS-specific code remains in the infrastructure module while non-AWS-specific code stays in application/domain modules, (5) reviewing how agent logic is unit tested, and (6) adopting the Koog `agents-test` module (`ai.koog:agents-test`) to enable unit testing of agent strategies with mock LLM responses instead of real Bedrock calls.

## Glossary

- **Koog_Framework**: The Kotlin-based AI agent framework (`ai.koog:koog-agents`) used by MockNest for agent orchestration and LLM interaction
- **Agents_Test_Module**: The Koog testing module (`ai.koog:agents-test`) that provides mocking capabilities and validation tools for unit testing agent behavior without real LLM calls
- **BedrockServiceAdapter**: The infrastructure-layer class (`software/infra/aws/generation`) that implements `AIModelServiceInterface` using Amazon Bedrock and Koog, wiring `BedrockLLMClient`, `SingleLLMPromptExecutor`, and `GraphAIAgent`
- **AIModelServiceInterface**: The application-layer interface that abstracts AI model access, defining `runStrategy()` and `parseModelResponse()` methods
- **MockGenerationFunctionalAgent**: The application-layer Koog agent that defines the mock generation strategy graph (setup → generate → validate → correct → finish) using the Koog strategy DSL
- **Eval_Test**: The `BedrockPromptEvalTest` class that exercises the real Koog + Bedrock generation path against REST, GraphQL, and SOAP specifications using the Dokimos LLM evaluation framework
- **TokenUsageCapturingClient**: A test-only decorator around `BedrockRuntimeClient` that intercepts `converse` calls to capture token usage from Bedrock responses
- **CostCalculator**: A test-only utility that estimates dollar cost from token usage using per-token pricing constants for Amazon Nova Pro
- **ModelConfiguration**: The infrastructure-layer Spring component that maps model names to Koog `LLModel` instances and applies inference profile prefixes
- **Clean_Architecture_Boundary**: The project rule that AWS-specific code (Bedrock, S3, Lambda) resides only in `software/infra/aws/` modules, while application logic and domain models remain cloud-agnostic
- **Mock_LLM**: A test double provided by the Agents_Test_Module that simulates LLM responses, enabling deterministic unit testing of agent strategies without network calls
- **Strategy_Graph**: The Koog `AIAgentGraphStrategy` that defines the directed graph of processing nodes (setup, generate, validate, correct) and edges with conditions for the mock generation workflow

## Requirements

### Requirement 1: Upgrade Koog Framework Version

**User Story:** As a developer, I want to upgrade Koog from 0.6.2 to 0.8.0, so that MockNest benefits from bug fixes, performance improvements, and new capabilities introduced across versions 0.7.x and 0.8.0.

#### Acceptance Criteria

1. THE Build_System SHALL declare Koog version `0.8.0` in the root `build.gradle.kts` dependency management block, replacing version `0.6.2`
2. WHEN the Koog version is updated, THE Build_System SHALL resolve all Koog artifacts (`koog-agents` and any new transitive dependencies) from Maven Central at version 0.8.0
3. WHEN the Koog version is updated, THE Build_System SHALL also declare `ai.koog:agents-test:0.8.0` as a `testImplementation` dependency in the modules that require agent unit testing
4. WHEN the upgrade introduces compilation errors due to breaking API changes, THE Developer SHALL adapt all affected source files to compile cleanly against Koog 0.8.0
5. WHEN the upgrade is complete, THE Build_System SHALL pass `./gradlew clean test` with all existing tests continuing to pass
6. IF the Koog 0.8.0 upgrade changes the `LLMProvider` singleton pattern (restored in 0.7.x), THEN THE BedrockServiceAdapter SHALL adapt its `SingleLLMPromptExecutor` and `BedrockLLMClient` initialization to the new pattern
7. IF the Koog 0.8.0 upgrade changes the `LLMClient` constructor to decouple from Ktor, THEN THE BedrockServiceAdapter SHALL update its `BedrockLLMClient` instantiation accordingly
8. IF the Koog 0.8.0 upgrade introduces a new `prepareEnvironment` abstraction for environment creation, THEN THE BedrockServiceAdapter SHALL adopt the new pattern where applicable

### Requirement 2: Review and Align Koog Usage Patterns

**User Story:** As a developer, I want to review how MockNest uses Koog and ensure the usage follows current 0.8.0 best practices, so that the codebase takes advantage of framework improvements and avoids deprecated patterns.

#### Acceptance Criteria

1. WHEN the upgrade is complete, THE MockGenerationFunctionalAgent SHALL use the recommended Koog 0.8.0 API for defining strategy graphs, nodes, edges, and conditions
2. WHEN the upgrade is complete, THE BedrockServiceAdapter SHALL use the recommended Koog 0.8.0 API for creating and running agents (`GraphAIAgent`, `AIAgentConfig`, `ToolRegistry`)
3. IF Koog 0.8.0 deprecates any API currently used by MockNest (e.g., `AIAgent` constructor, `AIAgentConfig.withSystemPrompt`, strategy DSL methods), THEN THE affected code SHALL migrate to the recommended replacement API
4. WHEN the upgrade is complete, THE ModelConfiguration SHALL use the recommended Koog 0.8.0 API for `BedrockModels` lookup and `withInferenceProfile` configuration
5. IF Koog 0.8.0 introduces Bedrock prompt caching support (added in 0.7.3), THEN THE review SHALL document whether MockNest can benefit from prompt caching and recommend adoption if applicable
6. WHEN the review is complete, THE Developer SHALL document any Koog usage pattern changes in a summary comment or commit message for future reference

### Requirement 3: Review Eval-Driven Development Setup

**User Story:** As a developer, I want to review the Bedrock prompt eval test infrastructure and how it collects usage, costs, and time, so that I can confirm the approach is sound and identify improvements.

#### Acceptance Criteria

1. WHEN the review is complete, THE Eval_Test SHALL continue to capture token usage via the TokenUsageCapturingClient decorator pattern, intercepting `converse` calls on `BedrockRuntimeClient`
2. WHEN the review is complete, THE CostCalculator SHALL continue to calculate estimated cost using per-token pricing constants for the configured model
3. WHEN the review is complete, THE Eval_Test SHALL continue to measure latency using `measureTimeMillis` around the generation call
4. IF the Koog 0.8.0 upgrade introduces built-in observability or token usage tracking (e.g., DataDog LLM Observability integration), THEN THE review SHALL evaluate whether the custom TokenUsageCapturingClient can be simplified or replaced
5. IF the Eval_Test uses any Koog API that changed in 0.8.0 (e.g., `AIAgent` constructor, `SingleLLMPromptExecutor`), THEN THE Eval_Test SHALL be updated to use the new API while preserving existing eval functionality
6. WHEN the review is complete, THE Eval_Test SHALL pass when run with `BEDROCK_EVAL_ENABLED=true` against the upgraded Koog 0.8.0 framework
7. THE review SHALL verify that the Dokimos integration (`dev.dokimos:dokimos-koog`) remains compatible with Koog 0.8.0 and recommend version updates if needed

### Requirement 4: Verify Clean Architecture Boundaries

**User Story:** As a developer, I want to verify that AWS-specific code is in the infrastructure module and non-AWS-specific code is in application/domain modules, so that the clean architecture boundaries are maintained after the upgrade.

#### Acceptance Criteria

1. THE Domain_Layer (`software/domain/`) SHALL NOT contain any imports from `ai.koog`, `aws.sdk.kotlin`, or `com.amazonaws` packages
2. THE Application_Layer (`software/application/`) SHALL NOT contain any imports from `aws.sdk.kotlin`, `com.amazonaws`, or `ai.koog.prompt.executor.clients.bedrock` packages
3. THE Application_Layer SHALL be permitted to import from `ai.koog.agents.core` and `ai.koog.prompt.message` packages, since the Koog strategy DSL and agent abstractions are cloud-agnostic
4. THE Infrastructure_Layer (`software/infra/aws/generation/`) SHALL be the only module that imports from `ai.koog.prompt.executor.clients.bedrock` and `aws.sdk.kotlin.services.bedrockruntime` packages
5. WHEN the upgrade is complete, THE AIModelServiceInterface SHALL remain free of any Bedrock-specific or AWS-specific types in its method signatures
6. WHEN the upgrade is complete, THE MockGenerationFunctionalAgent SHALL remain free of any Bedrock-specific or AWS-specific imports
7. IF the Koog 0.8.0 upgrade introduces new packages or moves existing classes, THEN THE import statements SHALL be updated to maintain the same clean architecture boundaries

### Requirement 5: Review Current Agent Unit Testing Approach

**User Story:** As a developer, I want to review how agent logic is currently unit tested, so that I can identify gaps where the Koog `agents-test` module would provide better coverage.

#### Acceptance Criteria

1. WHEN the review is complete, THE Developer SHALL document which aspects of the MockGenerationFunctionalAgent strategy graph are currently covered by unit tests (e.g., mocking `AIModelServiceInterface` via MockK)
2. WHEN the review is complete, THE Developer SHALL document which aspects of the strategy graph are NOT currently testable without real LLM calls (e.g., the `llm.writeSession` interactions inside strategy nodes, prompt construction passed to the LLM, multi-node graph traversal with conditional edges)
3. WHEN the review is complete, THE Developer SHALL identify specific test scenarios that the Agents_Test_Module would enable, such as: testing that the strategy graph follows the correct path for valid input, testing that the correction node is triggered when validation fails, and testing prompt content sent to the LLM
4. THE review SHALL confirm that the current unit tests mock at the `AIModelServiceInterface` boundary (testing the agent as a black box) but cannot test the internal strategy graph behavior (node transitions, LLM interactions, prompt content)

### Requirement 6: Adopt Koog agents-test Module for Agent Unit Testing

**User Story:** As a developer, I want to adopt the Koog `agents-test` module for unit testing agent strategies with mock LLM responses, so that I can test the internal behavior of the mock generation strategy graph without making real Bedrock calls.

#### Acceptance Criteria

1. THE Build_System SHALL declare `ai.koog:agents-test:0.8.0` as a `testImplementation` dependency in the `software/application/build.gradle.kts` module, since the MockGenerationFunctionalAgent lives in the application layer
2. WHEN the Agents_Test_Module is added, THE Developer SHALL write unit tests for the MockGenerationFunctionalAgent strategy graph that use Mock_LLM responses instead of real Bedrock calls
3. THE new agent unit tests SHALL verify that the strategy graph follows the expected node sequence (setup → generate → validate → finish) for a valid generation request with validation disabled
4. THE new agent unit tests SHALL verify that the strategy graph follows the expected node sequence (setup → generate → validate → correct → validate → finish) when validation is enabled and the first generation attempt produces validation errors
5. THE new agent unit tests SHALL verify that the LLM receives the expected prompt content constructed by `PromptBuilderService` during the generate node
6. THE new agent unit tests SHALL verify that parse failures in the generate node result in the correct error state and graph traversal to the correction node
7. THE new agent unit tests SHALL verify that the `GenerationResult` returned by the strategy contains the expected metadata (totalGenerated, attempts, firstPassValid, validationErrors)
8. THE new agent unit tests SHALL follow the project's Given-When-Then naming convention and use MockK for non-LLM dependencies (e.g., `SpecificationParserInterface`, `MockValidatorInterface`, `PromptBuilderService`)
9. THE new agent unit tests SHALL NOT require AWS credentials, network access, or any external service calls
10. WHEN the new agent unit tests are complete, THE Build_System SHALL pass `./gradlew clean test` with all tests (existing and new) passing

### Requirement 7: Preserve Existing Test Infrastructure

**User Story:** As a developer, I want the existing test infrastructure (unit tests, integration tests, eval tests) to continue working after the upgrade, so that no test coverage is lost.

#### Acceptance Criteria

1. WHEN the upgrade is complete, THE existing unit tests that mock `AIModelServiceInterface` via MockK SHALL continue to pass without modification (unless Koog API changes require signature updates)
2. WHEN the upgrade is complete, THE existing Bedrock eval tests (`BedrockPromptEvalTest`) SHALL continue to function when run with `BEDROCK_EVAL_ENABLED=true`
3. WHEN the upgrade is complete, THE existing integration tests SHALL continue to pass
4. IF the Koog 0.8.0 upgrade changes the `AIAgentGraphStrategy` type signature or the `runStrategy` method signature, THEN THE AIModelServiceInterface and all implementations SHALL be updated consistently
5. THE Kover code coverage SHALL remain at or above the enforced 80% threshold after the upgrade, with a goal of maintaining 90%+ coverage

### Requirement 8: No Production Behavior Changes

**User Story:** As a developer, I want the production mock generation behavior to remain functionally identical after the upgrade, so that end users experience no regressions.

#### Acceptance Criteria

1. WHEN the upgrade is complete, THE MockGenerationFunctionalAgent SHALL produce the same `GenerationResult` structure (success/failure, mocks, metadata) for identical inputs
2. WHEN the upgrade is complete, THE BedrockServiceAdapter SHALL continue to parse model responses using the same JSON extraction logic (raw JSON, Markdown code blocks, regex fallback)
3. WHEN the upgrade is complete, THE BedrockServiceAdapter SHALL continue to create `GeneratedMock` instances with the same ID format, naming convention, and metadata structure
4. WHEN the upgrade is complete, THE ModelConfiguration SHALL continue to resolve model names to `LLModel` instances using the same reflection-based lookup with fallback to `AmazonNovaPro`
5. IF the Koog 0.8.0 upgrade changes the format or structure of LLM responses (e.g., `Message.Assistant` content), THEN THE BedrockServiceAdapter SHALL adapt its response parsing to maintain identical output behavior
