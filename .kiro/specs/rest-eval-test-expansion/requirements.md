# Requirements Document

## Introduction

The Bedrock prompt evaluation test suite currently covers GraphQL (2 scenarios) and SOAP (1 scenario) in the multi-protocol dataset, but has zero REST scenarios in that dataset. The separate Petstore and Bored API datasets use an older format incompatible with the multi-protocol test runner. As a result, REST prompt quality is unmeasured, and the overall eval suite produces binary pass/fail results (100% or 50%) rather than the granular quality signal needed to guide prompt tuning.

This feature expands the eval suite with diverse REST API scenarios across multiple OpenAPI specs and prompt complexity levels. The goal is to produce realistic quality metrics (e.g., 77% first-pass, 98% after retry) that reveal which API × prompt combinations need improvement, while keeping costs predictable and results presentable.

## Glossary

- **Eval_Suite**: The collection of all eval scenarios executed by `BedrockPromptEvalTest`, reading from the multi-protocol eval dataset JSON file
- **Eval_Scenario**: A single test case in the eval dataset, consisting of an API spec, a user prompt (description), and semantic check criteria
- **Multi_Protocol_Dataset**: The `multi-protocol-eval-dataset.json` file containing all eval scenarios consumed by `BedrockPromptEvalTest`
- **Prompt_Complexity_Level**: A classification of user prompts by difficulty — Basic (single entity/endpoint), Filtered (subset selection), Consistency (cross-entity data coherence), Error (error response generation), Realistic_Data (domain-specific realistic values), or Edge_Case (pagination, empty results, boundary conditions)
- **First_Pass_Valid_Rate**: The percentage of generated mocks that pass structural validation without a correction retry
- **After_Retry_Valid_Rate**: The percentage of generated mocks that pass structural validation after the correction retry
- **Scenario_Pass_Rate**: The percentage of scenarios where generation succeeded, all mocks are valid, and the LLM semantic judge passed
- **Scenario_Tag**: A label assigned to an Eval_Scenario for filtering and grouping (e.g., protocol, API name, complexity level)
- **Cost_Estimate**: The projected Bedrock API cost for running the full Eval_Suite, calculated from token pricing constants in CostCalculator
- **Summary_Table**: The formatted output table produced after all scenarios complete, grouped by protocol and showing aggregate metrics
- **Scenario_Detail_Table**: A per-scenario breakdown table showing individual API × prompt results
- **OpenAPI_Spec**: An OpenAPI 3.0 specification file in YAML format describing a REST API's endpoints, schemas, and operations
- **Synthetic_Spec**: An OpenAPI_Spec created specifically for eval testing, not sourced from a real public API, designed to exercise specific prompt challenges

## Requirements

### Requirement 1: Add Diverse REST OpenAPI Specifications

**User Story:** As a prompt engineer, I want the eval suite to test against multiple REST API specifications with varying complexity, so that I can measure prompt quality across different API shapes and sizes.

#### Acceptance Criteria

1. THE Eval_Suite SHALL include OpenAPI_Spec files for at least 4 distinct REST APIs with different domain characteristics (e.g., social/content, financial/payment, developer/repository, simple/utility)
2. WHEN a new OpenAPI_Spec is added, THE OpenAPI_Spec SHALL be placed in the `software/infra/aws/generation/src/test/resources/eval/` directory
3. THE Eval_Suite SHALL include at least one OpenAPI_Spec with nested object schemas and cross-entity relationships (e.g., an order referencing a product and a customer)
4. THE Eval_Suite SHALL include at least one OpenAPI_Spec with 10 or more endpoints to test prompt handling of larger specifications
5. THE Eval_Suite SHALL include at least one OpenAPI_Spec with 3 or fewer endpoints to test prompt handling of minimal specifications
6. WHEN an OpenAPI_Spec is added, THE OpenAPI_Spec SHALL be a valid OpenAPI 3.0 document parseable by the existing OpenAPISpecificationParser

### Requirement 2: Add REST Scenarios to Multi-Protocol Dataset

**User Story:** As a prompt engineer, I want REST eval scenarios in the multi-protocol dataset, so that REST prompt quality is measured alongside GraphQL and SOAP in the same test run.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 12 REST Eval_Scenario entries covering the added OpenAPI_Spec files
2. WHEN a REST Eval_Scenario is added to the Multi_Protocol_Dataset, THE Eval_Scenario SHALL use protocol "REST", format "OPENAPI_3", and reference a valid specFile path
3. THE Multi_Protocol_Dataset SHALL contain REST Eval_Scenario entries for the existing petstore-openapi-3.0.yaml specification
4. THE Multi_Protocol_Dataset SHALL contain REST Eval_Scenario entries for the existing bored-api-openapi-3.0.yaml specification
5. WHEN the Eval_Suite runs, THE Summary_Table SHALL include a "REST" row with aggregate metrics alongside the existing "GraphQL" and "SOAP" rows

### Requirement 3: Cover Multiple Prompt Complexity Levels

**User Story:** As a prompt engineer, I want eval scenarios at different prompt complexity levels, so that I can identify which types of prompts the model handles well and which need improvement.

#### Acceptance Criteria

1. THE Multi_Protocol_Dataset SHALL contain at least 2 REST Eval_Scenario entries at the Basic Prompt_Complexity_Level (e.g., "Generate mocks for all GET endpoints")
2. THE Multi_Protocol_Dataset SHALL contain at least 2 REST Eval_Scenario entries at the Filtered Prompt_Complexity_Level (e.g., "Generate mocks only for pet-related endpoints")
3. THE Multi_Protocol_Dataset SHALL contain at least 2 REST Eval_Scenario entries at the Consistency Prompt_Complexity_Level (e.g., "Generate mocks where GET /orders/1 references the pet from GET /pets/1")
4. THE Multi_Protocol_Dataset SHALL contain at least 2 REST Eval_Scenario entries at the Error Prompt_Complexity_Level (e.g., "Generate 404 and 500 error responses for all endpoints")
5. THE Multi_Protocol_Dataset SHALL contain at least 1 REST Eval_Scenario at the Realistic_Data Prompt_Complexity_Level (e.g., "Generate mocks with realistic European names and addresses")
6. THE Multi_Protocol_Dataset SHALL contain at least 1 REST Eval_Scenario at the Edge_Case Prompt_Complexity_Level (e.g., "Generate mocks for pagination with 3 pages of results")
7. WHEN the Eval_Suite produces results, THE First_Pass_Valid_Rate for REST scenarios SHALL vary across Prompt_Complexity_Level entries rather than producing uniform 100% or 0% results

### Requirement 4: Granular Per-Scenario Failure Reporting

**User Story:** As a prompt engineer, I want to see results broken down by individual API × prompt combination, so that I can identify exactly which combinations need prompt tuning.

#### Acceptance Criteria

1. WHEN the Eval_Suite completes, THE BedrockPromptEvalTest SHALL output a Scenario_Detail_Table listing each Eval_Scenario with its individual First_Pass_Valid_Rate, After_Retry_Valid_Rate, Scenario_Pass_Rate, cost, and latency
2. THE Scenario_Detail_Table SHALL group results by API specification name so that per-API patterns are visible
3. WHEN an Eval_Scenario fails validation or semantic check, THE Scenario_Detail_Table SHALL display the scenario input name and failure reason
4. THE Scenario_Detail_Table SHALL be printed to the test output log alongside the existing Summary_Table

### Requirement 5: Cost Estimation and Reporting

**User Story:** As a developer, I want to see a breakdown of Bedrock API costs separated by mock generation cost and LLM judge cost, so that I can understand where money is being spent and budget for eval runs.

#### Acceptance Criteria

1. WHEN the Eval_Suite completes, THE Scenario_Detail_Table SHALL display three cost columns per Eval_Scenario: generation cost (the mock generation Bedrock call(s)), judge cost (the LLM-as-a-judge semantic evaluation call), and total cost (generation + judge)
2. WHEN the Eval_Suite completes, THE Summary_Table SHALL display the total generation cost, total judge cost, and total combined cost for the entire eval run across all protocols
3. THE TokenUsageStore SHALL track generation token usage and judge token usage separately so that costs can be attributed to the correct phase
4. THE docs/PROMPT_EVAL.md documentation SHALL include an updated cost estimate reflecting the expanded REST scenario count, broken down by generation cost and judge cost
5. THE cost estimation SHALL use the token pricing constants defined in CostCalculator (NOVA_PRO_INPUT_PRICE_PER_TOKEN and NOVA_PRO_OUTPUT_PRICE_PER_TOKEN)
6. WHEN the Eval_Suite completes, THE Summary_Table SHALL display a total cost line at the bottom showing the combined cost of the entire test run (all scenarios × all iterations)

### Requirement 6: Re-Runnable Scenario Subsets

**User Story:** As a prompt engineer, I want to re-run only the failing or specific scenarios after making prompt fixes, so that I can iterate quickly without running the entire suite.

#### Acceptance Criteria

1. WHEN the environment variable `BEDROCK_EVAL_FILTER` is set, THE BedrockPromptEvalTest SHALL run only Eval_Scenario entries whose input name contains the filter value (case-insensitive substring match)
2. WHEN the environment variable `BEDROCK_EVAL_FILTER` is not set, THE BedrockPromptEvalTest SHALL run all Eval_Scenario entries in the Multi_Protocol_Dataset
3. WHEN a filter is applied, THE Summary_Table and Scenario_Detail_Table SHALL reflect only the filtered subset of scenarios
4. THE docs/PROMPT_EVAL.md documentation SHALL document the `BEDROCK_EVAL_FILTER` environment variable with usage examples

### Requirement 7: README-Presentable Results

**User Story:** As a project maintainer, I want eval results formatted for inclusion in documentation, so that I can demonstrate prompt quality metrics to users and contributors.

#### Acceptance Criteria

1. THE docs/PROMPT_EVAL.md documentation SHALL include a section showing example eval results with the expanded REST scenarios
2. THE Summary_Table format SHALL remain compatible with monospace rendering in Markdown code blocks
3. THE Scenario_Detail_Table format SHALL be compatible with monospace rendering in Markdown code blocks
4. THE docs/PROMPT_EVAL.md documentation SHALL include the total scenario count, protocol breakdown, and expected cost range for the expanded suite

### Requirement 8: Semantic Check Quality for REST Scenarios

**User Story:** As a prompt engineer, I want each REST eval scenario to have a precise semantic check, so that the LLM judge can meaningfully evaluate whether the generated mocks match the prompt intent.

#### Acceptance Criteria

1. WHEN a REST Eval_Scenario is added, THE semanticCheck field SHALL specify concrete, verifiable criteria (e.g., exact endpoint paths, expected response field names, expected HTTP status codes)
2. THE semanticCheck field SHALL avoid vague criteria such as "looks correct" or "reasonable output"
3. WHEN the Eval_Scenario uses the Consistency Prompt_Complexity_Level, THE semanticCheck SHALL verify cross-entity data consistency (e.g., "the petId in the order response matches the id in the pet response")
4. WHEN the Eval_Scenario uses the Error Prompt_Complexity_Level, THE semanticCheck SHALL verify that error status codes and error response bodies are present

### Requirement 9: Backward Compatibility with Existing Eval Infrastructure

**User Story:** As a developer, I want the expanded eval suite to work with the existing test infrastructure, so that no existing tests or CI pipelines break.

#### Acceptance Criteria

1. THE BedrockPromptEvalTest SHALL continue to load scenarios from the Multi_Protocol_Dataset without changes to the dataset JSON schema (input, metadata with protocol, specFile, format, namespace, description, semanticCheck)
2. THE existing GraphQL and SOAP Eval_Scenario entries in the Multi_Protocol_Dataset SHALL remain unchanged and continue to pass
3. THE eval tests SHALL remain gated behind the `BEDROCK_EVAL_ENABLED=true` environment variable and the `bedrock-eval` JUnit tag
4. THE eval tests SHALL remain excluded from the normal `./gradlew test` run
5. IF the `BEDROCK_EVAL_FILTER` environment variable is not set, THEN THE BedrockPromptEvalTest SHALL behave identically to the current implementation for existing scenarios
