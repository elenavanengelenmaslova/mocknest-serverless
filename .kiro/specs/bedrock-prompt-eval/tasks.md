# Implementation Plan: Bedrock Prompt Eval

## Overview

This plan implements a manual, local-only Kotlin integration test for evaluating MockNest's initial REST `spec-with-description` generation prompt against real AWS Bedrock (Amazon Nova Pro). The implementation is split into: (1) test-only token usage capture classes, (2) Gradle configuration for test gating, (3) test-only utility classes (cost calculator, report builder, data models), (4) Dokimos-based semantic evaluators, and (5) the main gated eval test class. All code lives in the test source set (`src/test/`) so nothing ends up in the deployment JAR. Each task builds incrementally on the previous, with property-based tests validating pure logic components.

## Tasks

- [x] 1. Create test-only token usage capture classes
  - [x] 1.1 Create `TokenUsageRecord` data class
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/TokenUsageRecord.kt`
    - Simple data class with `inputTokens: Int = 0`, `outputTokens: Int = 0`, `totalTokens: Int = 0`
    - _Requirements: 5.1_

  - [x] 1.2 Create `TokenUsageStore` thread-safe collector
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/TokenUsageStore.kt`
    - Use `ConcurrentLinkedQueue<TokenUsageRecord>` for lock-free thread safety
    - Implement `record()`, `getRecords()`, `getTotalInputTokens()`, `getTotalOutputTokens()`, `getTotalTokens()`, `clear()`
    - _Requirements: 5.2, 5.4_

  - [x] 1.3 Create `TokenUsageCapturingClient` decorator
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/TokenUsageCapturingClient.kt`
    - Decorator around `BedrockRuntimeClient` using Kotlin `by` delegation
    - Override `converse` to extract `response.usage` and store in `TokenUsageStore`
    - Record `TokenUsageRecord(0, 0, 0)` when usage data is null (Requirement 5.5)
    - All other methods delegate to the wrapped client unchanged (Requirement 5.3)
    - _Requirements: 5.1, 5.2, 5.3, 5.5, 8.1_

  - [ ]* 1.4 Write unit tests for `TokenUsageStore`
    - Test `record()` and retrieval methods with known data
    - Test `clear()` resets all state
    - Test `getTotalInputTokens()` / `getTotalOutputTokens()` / `getTotalTokens()` accumulation
    - Test empty store returns zero values
    - _Requirements: 5.2_

  - [ ]* 1.5 Write property test for `TokenUsageStore` thread safety
    - **Property 8: Token usage store thread safety**
    - Record N `TokenUsageRecord` instances concurrently from multiple coroutines
    - Assert store contains all N records with no data loss
    - Assert `getTotalInputTokens()` equals sum of all individual `inputTokens` values
    - Use `@ParameterizedTest` with `@MethodSource` providing 10+ diverse concurrency scenarios
    - **Validates: Requirements 5.2**

  - [x] 1.6 Run `./gradlew :software:infra:aws:generation:test` and confirm all tests pass

- [x] 2. Configure Gradle for eval test gating and Dokimos dependencies
  - [x] 2.1 Update `software/infra/aws/generation/build.gradle.kts`
    - Add `excludeTags("bedrock-eval")` to the existing `tasks.test` block
    - Register new `bedrockEval` test task with `includeTags("bedrock-eval")`
    - Add Dokimos `testImplementation` dependencies: `dev.dokimos:dokimos-core:0.14.2`, `dev.dokimos:dokimos-kotlin:0.14.2`, `dev.dokimos:dokimos-junit:0.14.2`, `dev.dokimos:dokimos-koog:0.14.2`
    - _Requirements: 1.5, 9.4, 10.1_

  - [x] 2.2 Run `./gradlew :software:infra:aws:generation:test` and confirm all existing tests still pass with the tag exclusion
    - _Requirements: 1.5, 8.2_

- [x] 3. Create test-only data models and utility classes
  - [x] 3.1 Create `IterationResult` and `SemanticScore` data classes
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/IterationResult.kt`
    - `IterationResult` with fields: `iterationNumber`, `success`, `mockCount`, `mockIds`, `endpointPaths`, `errorMessage`, `semanticScore`, `tokenUsage`, `estimatedCost`
    - `SemanticScore` with fields: `petCountCorrect`, `endpointsCovered`, `schemaConsistent`, `statusesDistinct`, `llmJudgeScore`, `passed`
    - _Requirements: 4.1, 4.2, 11.1, 11.2, 11.3, 11.4_

  - [x] 3.2 Create `CostCalculator` object
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/CostCalculator.kt`
    - Constants for Amazon Nova Pro pricing: `NOVA_PRO_INPUT_PRICE_PER_TOKEN = 0.0000008`, `NOVA_PRO_OUTPUT_PRICE_PER_TOKEN = 0.0000032`
    - Pure function `calculateCost(inputTokens, outputTokens, inputPricePerToken, outputPricePerToken): Double`
    - _Requirements: 6.1, 6.2_

  - [ ]* 3.3 Write property test for `CostCalculator`
    - **Property 4: Cost calculation correctness**
    - For any non-negative integer input/output token counts, assert cost equals `inputTokens × inputPricePerToken + outputTokens × outputPricePerToken`
    - Assert result is always non-negative
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse examples (zero tokens, large counts, max int boundary)
    - **Validates: Requirements 6.1**

  - [x] 3.4 Create `EvalReportBuilder` class
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/EvalReportBuilder.kt`
    - `buildReport()` method accepting model name, region, iteration count, results list, duration, total token usage, total cost
    - Bordered format with labeled fields matching the design's report template
    - Include "Generation-Only Mode" indicator (Requirement 13.6)
    - Include per-iteration breakdown with success/failure, mock count, semantic score, token usage, cost
    - Format cost to 4 decimal places in USD
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 13.6_

  - [ ]* 3.5 Write property test for `EvalReportBuilder` completeness
    - **Property 5: Eval report completeness**
    - For any valid set of eval results, assert the formatted report contains: model name, region, iteration count, success rate percentage, total mock count, semantic score percentage, input/output/total token counts, estimated cost, and exactly N per-iteration entries
    - Use `@ParameterizedTest` with `@MethodSource` providing 10+ diverse scenarios (single iteration, multiple iterations, all success, all failure, mixed)
    - **Validates: Requirements 7.2, 7.5**

  - [x] 3.6 Run `./gradlew :software:infra:aws:generation:test` and confirm all tests pass

- [x] 4. Implement success rate calculation and iteration count parsing logic
  - [x] 4.1 Create helper functions for success rate calculation and threshold assertion
    - Add to a utility file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/EvalMetrics.kt`
    - `calculateSuccessRate(results: List<IterationResult>): Double` — returns `(successful / total) × 100.0`
    - `calculateSemanticSuccessRate(results: List<IterationResult>): Double` — returns `(semantically passed / total) × 100.0`
    - `assertThreshold(rate: Double, threshold: Double, label: String)` — fails with descriptive message if rate < threshold
    - `parseIterationCount(envValue: String?): Int` — parses env var, defaults to 1, throws `IllegalArgumentException` for invalid values
    - _Requirements: 4.3, 4.7, 11.7, 11.8, 12.1, 12.2, 12.3, 12.4_

  - [ ]* 4.2 Write property test for success rate calculation
    - **Property 2: Success rate calculation**
    - For any list of `IterationResult` instances, assert success rate equals `(count where success == true) / total × 100.0`
    - Assert semantic success rate equals `(count where semanticScore?.passed == true) / total × 100.0`
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse examples (all success, all failure, mixed, single item)
    - **Validates: Requirements 4.3, 11.7, 12.7**

  - [ ]* 4.3 Write property test for threshold assertion correctness
    - **Property 3: Threshold assertion correctness**
    - For any success rate (0.0 to 100.0) and threshold (0.0 to 100.0), assert the threshold check passes iff rate >= threshold
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse boundary examples
    - **Validates: Requirements 4.7, 11.8**

  - [ ]* 4.4 Write property test for iteration count validation
    - **Property 7: Invalid iteration count validation**
    - For any string that is not a positive integer (negative, zero, non-numeric, empty, blank), assert `parseIterationCount` throws `IllegalArgumentException` with descriptive message
    - For any positive integer string, assert it returns the correct integer
    - Use `@ParameterizedTest` with `@MethodSource` providing 15+ diverse examples
    - **Validates: Requirements 12.4**

  - [ ]* 4.5 Write property test for iteration result recording
    - **Property 1: Iteration result recording preserves GenerationResult data**
    - For any `GenerationResult` (success or failure, varying mock counts), converting to `IterationResult` correctly captures success status, mock count equal to `mocks.size`, and all mock IDs and endpoint paths
    - Use `@ParameterizedTest` with `@MethodSource` providing 10+ diverse examples
    - **Validates: Requirements 4.1, 4.2**

  - [x] 4.6 Run `./gradlew :software:infra:aws:generation:test` and confirm all tests pass

- [x] 5. Create Dokimos semantic evaluators
  - [x] 5.1 Create `PetCountEvaluator`
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/evaluators/PetCountEvaluator.kt`
    - Extends Dokimos `BaseEvaluator`
    - Parses mock response bodies, counts distinct pet entities
    - Returns score 1.0 if count matches expected (4), 0.0 otherwise
    - _Requirements: 11.1, 10.4_

  - [x] 5.2 Create `EndpointCoverageEvaluator`
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/evaluators/EndpointCoverageEvaluator.kt`
    - Extends Dokimos `BaseEvaluator`
    - Checks mock endpoint paths against required endpoints (`GET /pet/{petId}`, `GET /pet/findByStatus`)
    - Returns score 1.0 if all required endpoints covered, 0.0 otherwise
    - _Requirements: 11.2, 10.4_

  - [x] 5.3 Create `SchemaConsistencyEvaluator`
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/evaluators/SchemaConsistencyEvaluator.kt`
    - Extends Dokimos `BaseEvaluator`
    - Parses response bodies, checks for required fields (`id`, `name`, `status`)
    - Returns score 1.0 if all mocks have required fields, 0.0 otherwise
    - _Requirements: 11.3, 10.4_

  - [x] 5.4 Create `StatusDistinctnessEvaluator`
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/evaluators/StatusDistinctnessEvaluator.kt`
    - Extends Dokimos `BaseEvaluator`
    - Extracts status values from pet response bodies
    - Returns score 1.0 if all statuses are distinct, 0.0 otherwise
    - _Requirements: 11.4, 10.4_

  - [ ]* 5.5 Write unit tests for all four custom evaluators
    - Test each evaluator with known mock JSON inputs (valid and invalid scenarios)
    - Test edge cases: empty response bodies, missing fields, malformed JSON
    - Use Given-When-Then naming convention
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [x] 5.6 Run `./gradlew :software:infra:aws:generation:test` and confirm all tests pass

- [x] 6. Bundle test resources (Petstore spec and Dokimos eval dataset)
  - [x] 6.1 Download and bundle the Petstore OpenAPI 3.0 specification
    - Download from `swagger-api/swagger-petstore` repository
    - Save to `software/infra/aws/generation/src/test/resources/eval/petstore-openapi-3.0.yaml`
    - Verify it covers GET, POST, PUT, DELETE endpoints for pets, orders, and users
    - _Requirements: 3.1, 3.2, 3.5_

  - [x] 6.2 Create the Dokimos eval dataset JSON
    - Create file `software/infra/aws/generation/src/test/resources/eval/petstore-eval-dataset.json`
    - Single test case with input describing the mock generation request (4 pets, different statuses, GET /pet/{petId} and GET /pet/findByStatus)
    - Include expected output description for evaluator reference
    - _Requirements: 3.3, 10.2_

- [x] 7. Implement the main `BedrockPromptEvalTest` class and wire everything together
  - [x] 7.1 Create `BedrockPromptEvalTest`
    - Create file `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/BedrockPromptEvalTest.kt`
    - Annotate with `@Tag("bedrock-eval")` and `@EnabledIfEnvironmentVariable(named = "BEDROCK_EVAL_ENABLED", matches = "true")`
    - Manual wiring: `BedrockRuntimeClient` (with region from `AWS_REGION` env var, default `eu-west-1`), `TokenUsageStore`, `TokenUsageCapturingClient` wrapping the real client, `ModelConfiguration`, `PromptBuilderService`, `BedrockServiceAdapter`, `MockGenerationFunctionalAgent`
    - No Spring Boot context — all manual construction
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.5, 8.2, 8.4_

  - [x] 7.2 Implement iteration loop and result collection
    - Read `BEDROCK_EVAL_ITERATIONS` env var via `parseIterationCount()`
    - For each iteration: build `SpecWithDescriptionRequest` with `enableValidation = false` (Requirement 13.1), load Petstore spec from classpath, execute via `MockGenerationFunctionalAgent.generateFromSpecWithDescription()`
    - Capture token usage from `TokenUsageStore` per iteration
    - Calculate cost per iteration via `CostCalculator`
    - Run semantic evaluators on successful iterations
    - Record `IterationResult` for each iteration (continue on failure)
    - _Requirements: 2.4, 2.6, 2.7, 3.3, 3.4, 4.1, 4.2, 4.4, 4.6, 12.3, 12.5, 12.6, 13.1, 13.2, 13.3, 13.4, 13.5_

  - [x] 7.3 Implement LLM-as-a-judge faithfulness evaluator
    - Configure Dokimos `LLMJudgeEvaluator` using the same Bedrock model
    - Criteria: evaluate whether generated mocks are a faithful and complete representation of the requested scenario
    - Threshold: 0.7
    - Handle LLM judge failures gracefully (record `llmJudgeScore` as null, log warning)
    - _Requirements: 11.5, 10.3, 10.5_

  - [x] 7.4 Implement aggregate metrics and assertions
    - Calculate aggregate success rate and semantic success rate
    - Build and log `EvalReport` via `EvalReportBuilder`
    - Assert success rate >= configurable threshold (default 100% for single iteration)
    - Assert semantic success rate >= configurable threshold
    - Log per-iteration results with success/failure, mock count, mock IDs, endpoint paths
    - _Requirements: 4.3, 4.5, 4.7, 6.3, 6.4, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 11.6, 11.7, 11.8_

  - [x] 7.5 Use Dokimos `@ParameterizedTest` with `@DatasetSource` for test structure
    - Load eval dataset from `classpath:eval/petstore-eval-dataset.json`
    - Use Dokimos JUnit integration for structuring evaluation test cases
    - _Requirements: 10.2_

- [x] 8. Create developer documentation for running the eval test
  - [x] 8.1 Create `docs/PROMPT_EVAL.md`
    - Create file `docs/PROMPT_EVAL.md` with the following sections:
    - **Overview**: What the eval test does (evaluates REST generation prompt quality against real Bedrock)
    - **Prerequisites**: AWS credentials with Bedrock access, `AWS_REGION` env var (default `eu-west-1`), model access to Amazon Nova Pro
    - **Quick Start**: Single-iteration run command: `BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval`
    - **Configuration**: Table of environment variables (`BEDROCK_EVAL_ENABLED`, `BEDROCK_EVAL_ITERATIONS`, `AWS_REGION`) with descriptions and defaults
    - **Reading Results**: Explain the eval report format — success rate percentage, semantic score, token usage, estimated cost, per-iteration breakdown
    - **Example Output**: Include the bordered report template from the design document as a sample
    - **Cost Estimation**: Nova Pro pricing ($0.0008/1K input, $0.0032/1K output), typical cost per iteration, recommendation to start with 1 iteration
    - **Troubleshooting**: Common issues (missing credentials, wrong region, model access not enabled, test not running)
    - **Safety**: Explain that the test is excluded from `./gradlew test` and CI via JUnit tag filtering

- [x] 9. Final checkpoint — verify full build and eval test gating
  - [x] 9.1 Run `./gradlew :software:infra:aws:generation:test` and confirm all tests pass (eval test is skipped due to missing env var)
    - Verify the eval test is excluded from normal test runs via tag filtering
    - _Requirements: 1.1, 1.5_

  - [x] 9.2 Run `./gradlew clean test` from root and confirm the full project build passes
    - Ensure no regressions in any module
    - Ensure Kover coverage thresholds are still met
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 9.3 Verify eval test can be invoked manually (dry run)
    - Confirm `./gradlew :software:infra:aws:generation:bedrockEval` task exists and targets the correct tag
    - Without `BEDROCK_EVAL_ENABLED=true`, the test should be skipped with a descriptive message
    - Ensure all tests pass, ask the user if questions arise.
    - _Requirements: 1.1, 1.2, 1.4_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major task group
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The `TokenUsageRecord`, `TokenUsageStore`, and `TokenUsageCapturingClient` all live in the test source set (`src/test/`) — nothing from this feature ends up in the deployment JAR
- The eval test requires `BEDROCK_EVAL_ENABLED=true` and valid AWS credentials to run: `BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_ITERATIONS=10 ./gradlew :software:infra:aws:generation:bedrockEval`
