# Implementation Plan: Koog Upgrade and Review

## Overview

Upgrade the Koog AI agent framework from 0.6.2 to 0.8.0, adapt to breaking API changes, verify clean architecture boundaries, adopt the `agents-test` module for strategy-level unit testing, review the eval-driven development infrastructure, and document all findings. Production behavior remains identical — this is a non-functional upgrade.

## Tasks

- [x] 1. Upgrade Koog version in root build file and add agents-test dependency
  - [x] 1.1 Update `koogVersion` from `"0.6.2"` to `"0.8.0"` in `build.gradle.kts` root dependency management block
    - Change the line `val koogVersion = "0.6.2"` to `val koogVersion = "0.8.0"`
    - Add `dependency("ai.koog:agents-test:$koogVersion")` in the same dependency management block
    - _Requirements: 1.1, 1.2_
  - [x] 1.2 Add `testImplementation("ai.koog:agents-test")` to `software/application/build.gradle.kts`
    - This enables the `agents-test` module for strategy-level unit testing of `MockGenerationFunctionalAgent`
    - _Requirements: 1.3, 6.1_
  - [x] 1.3 Run `./gradlew clean compileKotlin` and collect all compilation errors
    - Document which files fail to compile and the specific error messages
    - _Requirements: 1.4_
  - [x] 1.4 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 1.5_

- [x] 2. Adapt BedrockServiceAdapter to Koog 0.8.0 API
  - [x] 2.1 Fix `BedrockLLMClient` constructor in `BedrockServiceAdapter`
    - File: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/BedrockServiceAdapter.kt`
    - The 0.8.0 release decoupled `LLMClient` constructors from Ktor (#1742) — adapt `BedrockLLMClient(bedrockClient, apiMethod = apiMethod)` to the new constructor signature
    - If `LLMProvider` singleton pattern changed, adapt `SingleLLMPromptExecutor` initialization accordingly
    - Evaluate whether the new `prepareEnvironment` abstraction (#1790) is applicable (likely not, since MockNest uses `GraphAIAgent` directly)
    - _Requirements: 1.6, 1.7, 1.8, 2.2_
  - [x] 2.2 Verify `GraphAIAgent`, `AIAgentConfig.withSystemPrompt`, and `ToolRegistry.EMPTY` API unchanged
    - If any of these changed, adapt the `runStrategy` method in `BedrockServiceAdapter`
    - _Requirements: 2.2, 2.3_
  - [x] 2.3 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 1.5_

- [x] 3. Adapt BedrockPromptEvalTest to Koog 0.8.0 API
  - [x] 3.1 Update `AIAgent` constructor in `BedrockPromptEvalTest` for the LLM judge
    - File: `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/BedrockPromptEvalTest.kt`
    - The current `AIAgent(promptExecutor, llmModel, systemPrompt, toolRegistry)` constructor may have changed in 0.8.0 — adapt to the new API (possibly `AIAgentConfig`-based construction)
    - _Requirements: 3.5, 2.3_
  - [x] 3.2 Verify Dokimos Koog integration (`dev.dokimos:dokimos-koog:0.14.2`) compiles against Koog 0.8.0
    - If incompatible, check for a newer Dokimos version or adapt the LLM judge to use Koog directly
    - _Requirements: 3.7_
  - [x] 3.3 Verify `ModelConfiguration` still works with `BedrockModels` lookup and `withInferenceProfile`
    - File: `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/config/ModelConfiguration.kt`
    - _Requirements: 2.4_
  - [x] 3.4 Verify `MockGenerationFunctionalAgent` strategy DSL (`strategy`, `node`, `edge`, `forwardTo`, `onCondition`, `transformed`, `llm.writeSession`) compiles unchanged
    - File: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationFunctionalAgent.kt`
    - _Requirements: 2.1_
  - [x] 3.5 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 1.5, 7.1, 7.3_

- [x] 5. Write MockGenerationStrategyTest using agents-test module
  - [x] 5.1 Create `MockGenerationStrategyTest.kt` in `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/agent/`
    - Use `getMockExecutor()` from `ai.koog:agents-test` to create a mock LLM executor
    - Mock non-LLM dependencies (`SpecificationParserInterface`, `MockValidatorInterface`, `PromptBuilderService`) with MockK
    - Follow Given-When-Then naming convention
    - All tests must run without AWS credentials or network access
    - _Requirements: 6.2, 6.8, 6.9_
  - [x] 5.2 Implement happy path test: validation disabled → setup → generate → finish
    - Configure mock LLM to return valid JSON WireMock mapping
    - Verify `GenerationResult.success == true` and `metadata["validationSkipped"] == true`
    - _Requirements: 6.3_
  - [x] 5.3 Implement correction path test: validation enabled, first attempt has errors → setup → generate → validate → correct → validate → finish
    - Configure mock LLM to return invalid JSON on first call, valid JSON on correction call
    - Mock validator to return errors on first pass, valid on second pass
    - Verify `metadata["attempts"] > 1` and `metadata["firstPassValid"] == false`
    - _Requirements: 6.4_
  - [x] 5.4 Implement parse failure test: LLM returns non-JSON → parseFailure=true → correction path
    - Configure mock LLM to return non-parseable text on first call
    - Verify the agent sets `parseFailure = true` and traverses to the correction node
    - _Requirements: 6.6_
  - [x] 5.5 Implement prompt content verification test
    - Verify the prompt sent to the LLM during the generate node contains the specification title, user-provided description, and namespace
    - _Requirements: 6.5_
  - [x] 5.6 Implement metadata completeness test
    - Verify `GenerationResult.metadata` contains the expected keys: `totalGenerated`, `attempts`, and either `validationSkipped` (when disabled) or `allValid`, `firstPassValid`, `firstPassMocksGenerated`, `firstPassMocksValid`, `mocksDropped`, `validationErrors` (when enabled)
    - _Requirements: 6.7_
  - [ ]* 5.7 Write property test: Strategy graph traversal follows correct node sequence
    - **Property 4: Strategy graph traversal follows correct node sequence**
    - **Validates: Requirements 6.3, 6.4**
  - [ ]* 5.8 Write property test: Prompt content includes specification context
    - **Property 5: Prompt content includes specification context**
    - **Validates: Requirements 6.5**
  - [ ]* 5.9 Write property test: Parse failures trigger correction path
    - **Property 6: Parse failures trigger correction path**
    - **Validates: Requirements 6.6**
  - [ ]* 5.10 Write property test: GenerationResult contains complete metadata
    - **Property 7: GenerationResult contains complete metadata**
    - **Validates: Requirements 6.7, 8.1**
  - [x] 5.11 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 6.10, 7.1_

- [x] 6. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Review eval test infrastructure and document findings
  - [x] 7.1 Verify `TokenUsageCapturingClient` decorator pattern still works with Koog 0.8.0
    - Confirm `converse` interception on `BedrockRuntimeClient` captures token usage correctly
    - _Requirements: 3.1_
  - [x] 7.2 Verify `CostCalculator` arithmetic is correct for Nova Pro pricing
    - _Requirements: 3.2_
  - [x] 7.3 Verify latency measurement via `measureTimeMillis` is unaffected by the upgrade
    - _Requirements: 3.3_
  - [x] 7.5 Evaluate Bedrock prompt caching (added in 0.7.3) — document whether MockNest can benefit from caching the system prompt
    - Recommend deferring adoption to a future iteration
    - _Requirements: 2.5_
  - [x] 7.6 Document review findings as code comments in adapted files and in the CHANGELOG entry
    - Include: which APIs changed, which remained stable, prompt caching opportunity, DataDog evaluation
    - _Requirements: 2.6, 3.4, 5.1, 5.2, 5.3, 5.4_

- [x] 8. Update CHANGELOG.md
  - [x] 8.1 Add a new version entry documenting the Koog 0.8.0 upgrade
    - Follow the existing Keep a Changelog format
    - Include: version bump, breaking changes adapted, agents-test adoption, clean architecture boundary tests, eval infrastructure review findings
    - _Requirements: 2.6_

- [x] 9. Run Bedrock prompt eval tests across all 3 protocols (REST, GraphQL, SOAP)
  - [x] 9.1 Ask the user for confirmation before running — these tests call Amazon Bedrock and incur ~$0.01–$0.02 per run
  - [x] 9.2 Run `BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval` and verify all scenarios pass
    - Confirm REST (OpenAPI), GraphQL, and SOAP (WSDL) scenarios all generate valid mocks
    - Verify the summary table shows scenario pass rates, 1st-pass valid rates, cost, and latency
    - Confirm token usage capture, cost calculation, and latency measurement work correctly with Koog 0.8.0
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6, 7.2_
  - [x] 9.3 Compare results against pre-upgrade baseline (if available) to confirm no quality regression
    - _Requirements: 8.1, 8.5_

- [ ] 10. Final verification — coverage and full test suite
  - [ ] 10.1 Run `./gradlew clean test` and confirm all tests pass
    - _Requirements: 7.1, 7.2, 7.3, 7.5_
  - [ ] 10.2 Run `./gradlew koverHtmlReport` and verify 80%+ coverage maintained (aim for 90%+)
    - _Requirements: 7.5_
  - [ ] 10.3 Run `./gradlew koverVerify` to enforce the coverage threshold
    - _Requirements: 7.5_
  - [ ] 10.4 Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The eval tests (`BedrockPromptEvalTest`) require `BEDROCK_EVAL_ENABLED=true` and incur Bedrock costs — do NOT run them automatically. Ask the user for confirmation before running.
- Properties 1 (cost calculation linearity), 8 (JSON extraction format-agnostic), 9 (GeneratedMock naming), and 10 (model name fallback) are already covered by existing tests in `CostCalculator`, `BedrockServiceAdapterTest`, and `ModelConfigurationTest` — no new tasks needed for these.
