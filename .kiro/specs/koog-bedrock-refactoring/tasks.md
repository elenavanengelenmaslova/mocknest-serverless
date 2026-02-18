# Implementation Plan: Koog Bedrock Refactoring

## Overview

This implementation plan refactors the AI generation code to follow Kotlin coding standards and properly implement the Koog framework. The work is organized into discrete, incremental steps that build on each other, with testing integrated throughout.

## Tasks

- [ ] 1. Add ModelConfiguration class for centralized model selection
  - Create `ModelConfiguration.kt` in `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/core/ai/`
  - Implement model name to LLModel mapping using Kotlin reflection
  - Add fallback logic for invalid model names with warning logging
  - Use KotlinLogging for structured logging
  - _Requirements: 1.4, 1.5, 1.6, 1.7, 1.8, 4.1, 4.2, 4.3, 4.4, 4.6_

- [ ]* 1.1 Write unit tests for ModelConfiguration
  - Test valid model name mapping for common models (Claude, Nova, Llama)
  - Test fallback behavior for invalid model names
  - Test default value when environment variable not set
  - Verify warning logs for invalid model names
  - _Requirements: 1.6, 1.8, 4.6, 7.1_

- [ ] 2. Update SAM template with BedrockModelName parameter
  - Add `BedrockModelName` parameter with default "AnthropicClaude35SonnetV2"
  - Add `AllowedValues` list with all valid BedrockModels property names
  - Add parameter description explaining model choices
  - Map parameter to `BEDROCK_MODEL_NAME` environment variable in Lambda configuration
  - _Requirements: 1.1, 1.2, 1.3_

- [ ]* 2.1 Verify SAM template parameter configuration
  - Parse SAM template YAML and verify BedrockModelName parameter exists
  - Verify default value is "AnthropicClaude35SonnetV2"
  - Verify AllowedValues contains expected model names
  - Verify environment variable mapping exists
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 3. Refactor BedrockTestKoogAgent to use Koog properly
  - Remove direct BedrockRuntimeClient.invokeModel calls
  - Remove Jackson ObjectMapper usage for request construction
  - Add lazy initialization of Koog simpleBedrockExecutor
  - Add lazy initialization of Koog AIAgent with model from ModelConfiguration
  - Use runCatching for error handling instead of try-catch
  - Add structured logging with context (model name, region, instructions)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.1, 3.2, 3.3, 4.3, 4.4, 4.5_

- [ ]* 3.1 Write unit tests for refactored BedrockTestKoogAgent
  - Test agent initialization with different model configurations
  - Test agent execution with valid requests
  - Test error handling and logging for failed executions
  - Verify Koog AIAgent is used (not direct Bedrock calls)
  - _Requirements: 2.4, 2.9, 4.3, 4.5, 7.1_

- [ ] 4. Update BedrockServiceAdapter to use ModelConfiguration
  - Inject ModelConfiguration dependency
  - Replace hardcoded CLAUDE_MODEL_ID with modelConfiguration.getBedrockModel().id
  - Update all error handling to use runCatching with onFailure
  - Ensure all logging uses structured format with context
  - _Requirements: 1.4, 1.5, 3.1, 3.2, 3.3, 4.3, 4.4, 4.5_

- [ ]* 4.1 Write unit tests for updated BedrockServiceAdapter
  - Test model ID is read from ModelConfiguration
  - Test error handling with runCatching
  - Test logging includes proper context
  - _Requirements: 1.4, 1.5, 4.3, 4.4, 7.1_

- [ ] 5. Update Spring configuration to wire new components
  - Add ModelConfiguration bean in AIGenerationConfiguration
  - Update bedrockTestKoogAgent bean to inject ModelConfiguration and region
  - Update bedrockServiceAdapter bean to inject ModelConfiguration
  - Remove BedrockRuntimeClient injection from BedrockTestKoogAgent (uses Koog executor instead)
  - _Requirements: 1.4, 1.5, 2.1, 2.2_

- [ ]* 5.1 Write integration tests for Spring configuration
  - Test ModelConfiguration bean is created with correct default
  - Test BedrockTestKoogAgent bean is wired correctly
  - Test BedrockServiceAdapter bean is wired correctly
  - Test end-to-end agent execution through Spring context
  - _Requirements: 7.2_

- [ ] 6. Evaluate and document RealisticTestDataGenerator
  - Analyze all usages of RealisticTestDataGenerator in codebase
  - Document its purpose in class-level KDoc
  - Add justification for hardcoded sample data pools
  - Verify all tests still pass after documentation updates
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 7. Code quality review and cleanup
  - Replace any remaining try-catch blocks with runCatching
  - Verify all imports are proper (not fully qualified)
  - Check for `!!` operator usage and replace with safe alternatives
  - Verify enum.entries is used instead of enum.values()
  - Ensure all logging follows standards (KotlinLogging, structured, appropriate levels)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [ ] 8. Checkpoint - Run full test suite and verify coverage
  - Run all unit tests and verify they pass
  - Run all integration tests and verify they pass
  - Run Kover coverage report and verify 90%+ coverage maintained
  - Fix any failing tests or coverage gaps
  - _Requirements: 7.1, 7.2, 7.4_

- [ ]* 8.1 Add property tests for model configuration
  - Property test: For any valid model name in BedrockModels, configuration maps correctly
  - Property test: For any AWS region, executor initializes with that region
  - Property test: For any exception during agent execution, error is logged with context
  - Run minimum 100 iterations per property test
  - Tag tests with: Feature: koog-bedrock-refactoring, Property N
  - _Requirements: 1.7, 2.2, 4.3_

## Notes

- Tasks marked with `*` are optional test tasks that can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Integration tests validate component wiring and end-to-end flows
