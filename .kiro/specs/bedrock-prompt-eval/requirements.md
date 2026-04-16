# Requirements Document

## Introduction

This feature adds a manual, local-only Kotlin integration test that exercises the real AWS Bedrock prompt evaluation path in MockNest's AI mock generation pipeline. The test is designed to be run explicitly by a developer on their local machine using real AWS credentials. It must never execute in CI pipelines or during normal `./gradlew test` runs. The test evaluates only the initial REST `spec-with-description` generation prompt — the correction/retry prompt is explicitly out of scope and will be evaluated separately in a future effort. Each eval run executes the generation prompt N times (configurable via `BEDROCK_EVAL_ITERATIONS`, defaulting to 1), with each iteration making exactly one Bedrock call (validation and correction loops are disabled). Results are aggregated across iterations as a success percentage (e.g., 8/10 = 80%). The test validates semantic correctness of generated mocks using the Dokimos LLM evaluation framework and reports eval success metrics, token usage, and estimated cost. All code for this feature lives in the test source set — no production code changes are required.

## Glossary

- **Eval_Test**: The manual, local-only Kotlin integration test class that exercises real Bedrock prompt evaluation
- **Bedrock_Client**: The existing `BedrockRuntimeClient` bean configured via `BedrockConfiguration`
- **Koog_Agent**: The existing `MockGenerationFunctionalAgent` that orchestrates the multi-node mock generation strategy via the Koog framework
- **Token_Usage**: The input token count, output token count, and total token count returned by Bedrock in the model invocation response
- **Estimated_Cost**: A dollar-amount estimate derived from Token_Usage and per-token pricing for the configured Bedrock model
- **Eval_Report**: A structured summary printed to the test log containing generation success/failure, mock count, semantic evaluation scores, token usage, and estimated cost
- **Gate_Property**: A JVM system property or environment variable (`BEDROCK_EVAL_ENABLED`) that must be explicitly set to `true` for the Eval_Test to execute
- **Token_Usage_Capture**: A test-only decorator around `BedrockRuntimeClient` that captures and exposes Token_Usage from Bedrock responses; lives entirely in the test source set and does not affect production code
- **Test_Specification**: The Petstore OpenAPI 3.0 specification bundled as a test resource, used as input to the generation pipeline during evaluation
- **Dokimos**: A JVM-native LLM evaluation framework (`dev.dokimos:dokimos-koog` version 0.14.2) that integrates with JUnit and Koog, providing built-in evaluators for correctness, faithfulness, hallucination detection, and LLM-as-a-judge assessments
- **Semantic_Evaluator**: A Dokimos-based evaluator (built-in or custom via `BaseEvaluator`) that assesses whether generated mocks are semantically correct relative to the input specification and natural language description
- **Iteration_Count**: The number of times the generation prompt is executed per eval run, configured via the `BEDROCK_EVAL_ITERATIONS` environment variable, defaulting to 1
- **Eval_Iteration**: A single execution of the generation prompt against Bedrock, producing one `GenerationResult`; each Eval_Iteration makes exactly one Bedrock API call
- **Success_Rate**: The percentage of Eval_Iterations that produce a successful `GenerationResult` with semantically correct output, calculated as (successful iterations / Iteration_Count) × 100%

## Requirements

### Requirement 1: Test Gating

**User Story:** As a developer, I want the Bedrock eval test to be completely excluded from normal test runs, so that CI pipelines and local `./gradlew test` never accidentally invoke real AWS services.

#### Acceptance Criteria

1. WHEN the Gate_Property `BEDROCK_EVAL_ENABLED` is not set, THE Eval_Test SHALL be skipped with a descriptive message indicating the gate condition
2. WHEN the Gate_Property `BEDROCK_EVAL_ENABLED` is set to any value other than `true`, THE Eval_Test SHALL be skipped with a descriptive message
3. WHEN the Gate_Property `BEDROCK_EVAL_ENABLED` is set to `true`, THE Eval_Test SHALL execute the Bedrock evaluation
4. THE Eval_Test SHALL use JUnit 5 `@EnabledIfEnvironmentVariable` or equivalent assumption-based gating to enforce the gate condition
5. THE Eval_Test SHALL NOT be included in any Gradle test task filter that runs during CI builds (e.g., via a dedicated JUnit tag or source set exclusion)

### Requirement 2: Real Bedrock Integration Path

**User Story:** As a developer, I want the eval test to exercise the real Koog + Bedrock initial generation prompt path, so that I can validate first-pass prompt quality against the actual model.

#### Acceptance Criteria

1. THE Eval_Test SHALL use the existing `BedrockServiceAdapter` and `MockGenerationFunctionalAgent` to execute the generation pipeline
2. THE Eval_Test SHALL use real AWS credentials resolved via the default credential chain (environment variables, AWS profile, or instance metadata)
3. THE Eval_Test SHALL configure the `BedrockRuntimeClient` with the region from the `AWS_REGION` environment variable, defaulting to `eu-west-1`
4. THE Eval_Test SHALL send a real `SpecWithDescriptionRequest` containing a Test_Specification and a natural language description to the generation pipeline
5. THE Eval_Test SHALL NOT use LocalStack, mocks, or any simulated Bedrock endpoint
6. THE Eval_Test SHALL exercise only the initial generation prompt (REST `spec-with-description`), not the correction or retry prompt path
7. WHEN executing each Eval_Iteration, THE Eval_Test SHALL make exactly one Bedrock API call for the initial generation prompt

### Requirement 3: Test Specification Input

**User Story:** As a developer, I want the eval test to use the well-known Petstore OpenAPI specification, so that the evaluation reflects realistic prompt behavior against a widely-recognized API contract.

#### Acceptance Criteria

1. THE Eval_Test SHALL load the Test_Specification from a classpath resource file in `src/test/resources/`
2. THE Test_Specification SHALL be the Petstore OpenAPI 3.0 specification (from swagger-api/swagger-petstore), covering GET, POST, PUT, and DELETE endpoints for pets, orders, and users
3. THE Eval_Test SHALL provide a specific natural language description requesting generation of mocks for 4 pets with different statuses, mocking the `GET /pet/{petId}` and `GET /pet/findByStatus` endpoints, to exercise the full `SpecWithDescriptionRequest` path and enable semantic verification of the output
4. THE Eval_Test SHALL use the `OPENAPI_3` specification format
5. THE Test_Specification SHALL be downloaded from the swagger-api/swagger-petstore repository and bundled as a test resource

### Requirement 4: Eval Success Metrics

**User Story:** As a developer, I want the eval test to report prompt effectiveness as a success percentage across multiple iterations, so that I can assess prompt quality statistically.

#### Acceptance Criteria

1. WHEN an Eval_Iteration completes, THE Eval_Test SHALL record whether the `GenerationResult` indicates success or failure for that iteration
2. WHEN an Eval_Iteration completes successfully, THE Eval_Test SHALL record the number of `GeneratedMock` instances produced in that iteration
3. WHEN all Eval_Iterations complete, THE Eval_Test SHALL calculate the Success_Rate as (successful iterations / Iteration_Count) × 100%
4. WHEN all Eval_Iterations complete, THE Eval_Test SHALL log per-iteration results including success/failure status, mock count, and mock IDs with endpoint paths
5. WHEN all Eval_Iterations complete, THE Eval_Test SHALL log the aggregate Success_Rate as a percentage (e.g., `8/10 = 80.0%`)
6. IF an Eval_Iteration fails, THEN THE Eval_Test SHALL log the error message from the `GenerationResult` for that iteration and continue with remaining iterations
7. WHEN all Eval_Iterations complete, THE Eval_Test SHALL assert that the aggregate Success_Rate meets or exceeds a configurable threshold (defaulting to 100% for single-iteration runs)

### Requirement 5: Token Usage Observability

**User Story:** As a developer, I want to see token usage from Bedrock responses, so that I can monitor prompt cost and efficiency.

#### Acceptance Criteria

1. THE Token_Usage_Capture SHALL capture Token_Usage (input tokens, output tokens, total tokens) from Bedrock model invocation responses
2. THE Token_Usage_Capture SHALL store captured Token_Usage in a thread-safe, retrievable structure accessible after the generation pipeline completes
3. THE Token_Usage_Capture SHALL NOT alter the existing production request/response flow or change any return values
4. THE Token_Usage_Capture SHALL reside entirely in the test source set (`src/test/`) and SHALL NOT be included in the production JAR
5. WHEN no Token_Usage data is available in the Bedrock response, THE Token_Usage_Capture SHALL record zero values rather than failing

### Requirement 6: Cost Estimation

**User Story:** As a developer, I want to see an estimated dollar cost for the Bedrock invocation, so that I can track prompt evaluation expenses.

#### Acceptance Criteria

1. THE Eval_Test SHALL calculate Estimated_Cost from Token_Usage using per-token pricing constants for the configured model
2. THE Eval_Test SHALL support pricing constants for at least Amazon Nova Pro (the default model)
3. THE Eval_Test SHALL log the Estimated_Cost formatted to four decimal places in USD
4. IF Token_Usage is zero or unavailable, THEN THE Eval_Test SHALL log the Estimated_Cost as `$0.0000` with a warning that token data was not captured

### Requirement 7: Eval Report Output

**User Story:** As a developer, I want a clear, structured summary of the evaluation results in the test log, so that I can quickly assess prompt quality, cost, and consistency across iterations.

#### Acceptance Criteria

1. WHEN the Eval_Test completes all Eval_Iterations, THE Eval_Test SHALL print a structured Eval_Report to the test log
2. THE Eval_Report SHALL include: model name, region, Iteration_Count, aggregate Success_Rate as a percentage, total number of mocks generated across all iterations, semantic evaluation scores (aggregate percentage), total input token count, total output token count, total token count, and total estimated cost in USD across all iterations
3. THE Eval_Report SHALL use a clearly delimited format (e.g., bordered section with labeled fields) for easy visual scanning
4. THE Eval_Report SHALL include the wall-clock duration of the entire eval run (all iterations) in milliseconds
5. THE Eval_Report SHALL include a per-iteration breakdown showing each iteration's success/failure status, mock count, semantic score, token usage, and estimated cost
6. THE Eval_Report SHALL display the aggregate Success_Rate prominently (e.g., `Success Rate: 8/10 = 80.0%`)

### Requirement 8: No Production Behavior Changes

**User Story:** As a developer, I want the production mock generation flow to remain unchanged, so that this eval test does not introduce regressions.

#### Acceptance Criteria

1. THE Eval_Test SHALL NOT modify any existing production source files, method signatures, return types, or control flow
2. THE Eval_Test SHALL NOT require changes to the `MockGenerationFunctionalAgent` strategy graph or node logic
3. THE Eval_Test SHALL NOT require upgrading the Koog framework or any other production dependency version
4. THE Eval_Test SHALL reside in the test source set of the `software/infra/aws/generation` module alongside existing Bedrock adapter tests

### Requirement 9: Dependency Constraints

**User Story:** As a developer, I want the eval test to work within the existing production dependency versions while allowing test-scope additions, so that no production upgrade risk is introduced.

#### Acceptance Criteria

1. THE Eval_Test SHALL use Koog framework version 0.6.2 as declared in the root `build.gradle.kts`
2. THE Eval_Test SHALL use AWS SDK Kotlin version 1.6.56 as declared in the root `build.gradle.kts`
3. THE Eval_Test SHALL use JUnit 6 test APIs consistent with the existing test infrastructure
4. THE Eval_Test MAY introduce new `testImplementation`-scope dependencies, since test-scope dependencies do not affect the deployment artifact

### Requirement 10: Dokimos LLM Eval Framework Integration

**User Story:** As a developer, I want to use the Dokimos LLM evaluation framework for structured prompt evaluation, so that I can leverage built-in evaluators and Koog integration instead of writing custom assertion logic.

#### Acceptance Criteria

1. THE Eval_Test SHALL declare `dev.dokimos:dokimos-koog` version 0.14.2 as a `testImplementation` dependency in the `software/infra/aws/generation` module
2. THE Eval_Test SHALL use Dokimos JUnit integration with `@ParameterizedTest` and `@DatasetSource` for structuring evaluation test cases
3. THE Eval_Test SHALL use Dokimos built-in evaluators (correctness, faithfulness, or LLM-as-a-judge) to assess the quality of generated mocks
4. THE Eval_Test SHALL support defining custom evaluators via Dokimos `BaseEvaluator` for domain-specific mock validation logic
5. THE Eval_Test SHALL use the Dokimos Kotlin DSL for configuring evaluators

### Requirement 11: Semantic Correctness Validation

**User Story:** As a developer, I want the eval test to validate that generated mocks are semantically correct relative to the input specification and description on each iteration, so that I can detect prompt quality regressions as an aggregate percentage across iterations.

#### Acceptance Criteria

1. WHEN an Eval_Iteration completes successfully, THE Semantic_Evaluator SHALL verify that the number of generated pet entities matches the count requested in the natural language description (e.g., 4 pets)
2. WHEN an Eval_Iteration completes successfully, THE Semantic_Evaluator SHALL verify that the generated WireMock mappings include mocks for the endpoints specified in the natural language description (e.g., `GET /pet/{petId}` and `GET /pet/findByStatus`)
3. WHEN an Eval_Iteration completes successfully, THE Semantic_Evaluator SHALL verify that the response data in generated mocks is consistent with the Petstore specification schema (e.g., pet objects contain id, name, status fields)
4. WHEN an Eval_Iteration completes successfully, THE Semantic_Evaluator SHALL verify that the pet statuses in generated mocks are distinct as requested in the natural language description
5. THE Eval_Test SHALL use Dokimos LLM-as-a-judge evaluators to assess whether the overall generated output of each Eval_Iteration is a faithful and complete representation of the requested mock scenario
6. THE Eval_Test SHALL log individual semantic evaluation scores per Eval_Iteration alongside the Eval_Report
7. WHEN all Eval_Iterations complete, THE Eval_Test SHALL calculate an aggregate semantic success percentage as (iterations passing all semantic checks / Iteration_Count) × 100%
8. IF the aggregate semantic success percentage falls below a configurable threshold, THEN THE Eval_Test SHALL fail with a descriptive message indicating the achieved percentage and which iterations did not pass

### Requirement 12: Configurable Eval Iterations

**User Story:** As a developer, I want to configure how many times the generation prompt is executed per eval run, so that I can first assess cost with a single call and later increase iterations for statistical evaluation of prompt quality.

#### Acceptance Criteria

1. THE Eval_Test SHALL read the Iteration_Count from the `BEDROCK_EVAL_ITERATIONS` environment variable
2. WHEN the `BEDROCK_EVAL_ITERATIONS` environment variable is not set, THE Eval_Test SHALL default to an Iteration_Count of 1
3. WHEN the `BEDROCK_EVAL_ITERATIONS` environment variable is set to a positive integer, THE Eval_Test SHALL execute the generation prompt that many times
4. IF the `BEDROCK_EVAL_ITERATIONS` environment variable is set to a non-positive integer or non-numeric value, THEN THE Eval_Test SHALL fail with a descriptive error message indicating the invalid configuration
5. WHEN the Iteration_Count is 1, THE Eval_Test SHALL make exactly one Bedrock API call (the initial generation call only)
6. WHEN the Iteration_Count is greater than 1, THE Eval_Test SHALL execute each Eval_Iteration sequentially, making exactly one Bedrock API call per iteration
7. THE Eval_Test SHALL aggregate results across all Eval_Iterations and express the outcome as a Success_Rate percentage (e.g., `8/10 = 80.0%`)

### Requirement 13: Generation-Only Mode (No Correction Loop)

**User Story:** As a developer, I want the eval test to exercise only the initial generation prompt without the validation and correction loop, so that I can isolate first-pass prompt quality and guarantee exactly one Bedrock call per iteration.

#### Acceptance Criteria

1. THE Eval_Test SHALL configure the `SpecWithDescriptionRequest` with `GenerationOptions(enableValidation = false)` to disable the validation and correction loop
2. WHEN `enableValidation` is set to `false`, THE Koog_Agent SHALL skip the validation and correction nodes and proceed directly from generation to the finish node
3. WHEN `enableValidation` is set to `false`, THE Eval_Test SHALL make exactly one Bedrock API call per Eval_Iteration (the initial generation call only)
4. THE Eval_Test SHALL NOT exercise the correction prompt (`parsing-correction` or format-specific `correction` prompts) during any Eval_Iteration
5. THE Eval_Test SHALL NOT construct the `MockGenerationFunctionalAgent` with a non-default `maxRetries` value to disable retries; instead, the `enableValidation = false` flag on `GenerationOptions` SHALL be the sole mechanism for disabling the correction loop
6. THE Eval_Report SHALL clearly indicate that the eval was run in generation-only mode (no correction loop)
7. THE correction prompt evaluation SHALL be explicitly out of scope for this feature and deferred to a separate future effort
