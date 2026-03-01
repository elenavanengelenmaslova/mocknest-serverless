# Implementation Plan

## Overview

This task list implements the refactoring to move prompt building logic and prompt text from infrastructure layer (BedrockServiceAdapter) to application layer (PromptBuilderService) with externalized templates. Response parsing and mock creation logic remain unchanged in this refactor.

## Task List

- [x] 1. Write bug condition exploration test
  - **Property 1: Fault Condition** - Prompt Building in Infrastructure Layer Detection
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the architectural violation exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate prompt building logic exists in infrastructure layer
  - Test that BedrockServiceAdapter does NOT contain prompt building methods (buildSpecWithDescriptionPrompt, buildCorrectionPrompt)
  - Test that BedrockServiceAdapter does NOT contain hardcoded prompt text
  - Test that PromptBuilderService exists in application layer
  - Test that prompt template files exist in application resources (system-prompt.txt, spec-with-description.txt, correction.txt)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the architectural violation exists)
  - Document counterexamples found (methods like buildSpecWithDescriptionPrompt, buildCorrectionPrompt in BedrockServiceAdapter)
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Prompt Content Equivalence
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for prompt building operations
  - Write property-based tests capturing observed prompt content from Preservation Requirements
  - Property-based testing generates many test cases for stronger guarantees
  - Test system prompt preservation: createAgent system prompt is identical
  - Test spec prompt preservation: buildSpecWithDescriptionPrompt produces identical prompts
  - Test correction prompt preservation: buildCorrectionPrompt produces identical prompts
  - Test optional parameter handling: namespace.client and specification optional parameters
  - Test prompt content byte-for-byte equality across many input combinations
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. Refactor prompt building to application layer

  - [x] 3.1 Create prompt template resource files
    - Create `software/application/src/main/resources/prompts/system-prompt.txt`
    - Extract system prompt text from BedrockServiceAdapter.createAgent()
    - Content: "You are an expert API mock generator.\nYou generate WireMock JSON mappings based on user instructions and specifications."
    - Create `software/application/src/main/resources/prompts/spec-with-description.txt`
    - Extract prompt text from BedrockServiceAdapter.buildSpecWithDescriptionPrompt()
    - Use placeholders: {{SPEC_TITLE}}, {{SPEC_VERSION}}, {{ENDPOINT_COUNT}}, {{KEY_ENDPOINTS}}, {{API_NAME}}, {{CLIENT_SECTION}}, {{DESCRIPTION}}, {{NAMESPACE}}
    - Create `software/application/src/main/resources/prompts/correction.txt`
    - Extract prompt text from BedrockServiceAdapter.buildCorrectionPrompt()
    - Use placeholders: {{SPEC_CONTEXT}}, {{API_NAME}}, {{CLIENT_SECTION}}, {{MOCKS_WITH_ERRORS}}, {{NAMESPACE}}
    - Maintain exact same text content as current implementation
    - _Bug_Condition: isBugCondition(BedrockServiceAdapter) contains hardcoded prompt text_
    - _Expected_Behavior: Prompt text stored in external resource files_
    - _Preservation: Prompt content remains identical after externalization_
    - _Requirements: 1.2, 1.3, 2.3, 2.4, 3.3_

  - [x] 3.2 Create application layer prompt builder service
    - Create `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/services/PromptBuilderService.kt`
    - Implement `loadSystemPrompt(): String` method - loads from `prompts/system-prompt.txt`
    - Implement `buildSpecWithDescriptionPrompt(specification: APISpecification, description: String, namespace: MockNamespace): String` method
    - Implement `buildCorrectionPrompt(invalidMocks: List<Pair<GeneratedMock, List<String>>>, namespace: MockNamespace, specification: APISpecification?): String` method
    - Implement template loading from classpath resources using `javaClass.getResourceAsStream()`
    - Implement parameter injection using string replacement with {{PARAM_NAME}} placeholders
    - Handle optional parameters (namespace.client, specification) by conditionally including/excluding template sections
    - Add comprehensive unit tests for PromptBuilderService
    - Test template loading, parameter injection, optional parameter handling
    - _Bug_Condition: isBugCondition(BedrockServiceAdapter.buildSpecWithDescriptionPrompt) returns true_
    - _Expected_Behavior: Prompt building logic exists in application layer (PromptBuilderService)_
    - _Preservation: Prompt content remains identical after externalization_
    - _Requirements: 1.1, 2.2, 2.3, 2.4, 3.3_

  - [x] 3.3 Update BedrockServiceAdapter to use PromptBuilderService
    - Update `software/infra/aws/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt`
    - Inject `PromptBuilderService` as constructor dependency
    - Update `createAgent()` method to load system prompt from `promptBuilder.loadSystemPrompt()`
    - Remove `buildSpecWithDescriptionPrompt()` method
    - Remove `buildCorrectionPrompt()` method
    - Update `generateMockFromSpecWithDescription()` to accept pre-built prompt as parameter
    - Update `correctMocks()` to accept pre-built prompt as parameter
    - Keep `parseModelResponse()` method unchanged (out of scope)
    - Keep `createGeneratedMock()` method unchanged (out of scope)
    - Update unit tests to verify infrastructure layer only contains AWS SDK integration
    - _Bug_Condition: isBugCondition(BedrockServiceAdapter) contains prompt building logic_
    - _Expected_Behavior: BedrockServiceAdapter contains only AWS SDK integration code_
    - _Preservation: Mock generation continues to work with pre-built prompts_
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.5_

  - [x] 3.4 Update MockGenerationFunctionalAgent to build prompts
    - Update `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationFunctionalAgent.kt`
    - Inject `PromptBuilderService` dependency
    - Before calling `aiModelService.generateMockFromSpecWithDescription()`, build prompt:
      - Call `promptBuilder.buildSpecWithDescriptionPrompt(specification, description, namespace)`
      - Pass built prompt to AI model service method
    - Before calling `aiModelService.correctMocks()`, build prompt:
      - Call `promptBuilder.buildCorrectionPrompt(invalidMocks, namespace, specification)`
      - Pass built prompt to AI model service method
    - Update unit tests to verify orchestration logic
    - _Bug_Condition: N/A (use case orchestration)_
    - _Expected_Behavior: Use cases build prompts before invoking AI model service_
    - _Preservation: Mock generation and correction produce identical results_
    - _Requirements: 2.6, 3.1, 3.2_

  - [x] 3.5 Update Spring configuration for PromptBuilderService
    - Update Spring configuration to register PromptBuilderService as a bean
    - Update dependency injection configuration for BedrockServiceAdapter
    - Update dependency injection configuration for MockGenerationFunctionalAgent
    - Verify all services are properly wired in application context
    - Add integration tests verifying Spring configuration
    - _Bug_Condition: N/A (configuration)_
    - _Expected_Behavior: All services properly configured and injected_
    - _Preservation: Application startup and dependency injection work correctly_
    - _Requirements: All requirements (configuration support)_

  - [x] 3.6 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Clean Architecture Compliance for Prompts
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the architectural violation is fixed
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms architectural violation is fixed)
    - Verify BedrockServiceAdapter does NOT contain prompt building methods
    - Verify BedrockServiceAdapter does NOT contain hardcoded prompt text
    - Verify PromptBuilderService exists in application layer
    - Verify prompt template files exist in application resources
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.7 Verify preservation tests still pass
    - **Property 2: Preservation** - Prompt Content Equivalence
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Verify system prompt preservation
    - Verify spec prompt preservation
    - Verify correction prompt preservation
    - Verify optional parameter handling
    - Confirm all tests still pass after refactoring (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint - Ensure all tests pass
  - Run full test suite across all modules
  - Verify architectural tests pass (clean architecture compliance for prompts)
  - Verify preservation tests pass (prompt content equivalence)
  - Verify unit tests pass for PromptBuilderService
  - Verify integration tests pass for full mock generation flow
  - Ensure code coverage meets project standards (90%+)
  - Ask the user if questions arise

## Success Criteria

- [x] BedrockServiceAdapter contains only AWS SDK integration code (no prompt building methods)
- [x] PromptBuilderService exists in application layer with prompt building logic
- [x] All prompt text externalized to resource files (system-prompt.txt, spec-with-description.txt, correction.txt)
- [x] Prompt content is byte-for-byte identical to original implementation
- [x] All existing tests pass without modification
- [x] Test coverage remains at 90%+
- [x] Response parsing and mock creation logic remain unchanged

## Notes

- This refactor focuses ONLY on moving prompt building logic out of infrastructure
- Response parsing (parseModelResponse) and mock creation (createGeneratedMock) remain in BedrockServiceAdapter
- All tasks should be completed in order
- Testing tasks (steps 1-2) are critical and must be done BEFORE implementation
- Preservation tests must pass on both unfixed and fixed code
