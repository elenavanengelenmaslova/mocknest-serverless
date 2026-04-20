# Tasks: REST Eval Test Expansion

## Task List

- [x] 1. Create new synthetic OpenAPI specification files
  - [x] 1.1 Create `social-content-openapi-3.0.yaml` in `software/infra/aws/generation/src/test/resources/eval/` with ~8-10 endpoints covering posts, comments, and users with cross-entity relationships (post.authorId → user.id, comment.postId → post.id), nested objects, and arrays
  - [x] 1.2 Create `payment-financial-openapi-3.0.yaml` in `software/infra/aws/generation/src/test/resources/eval/` with ~6-8 endpoints covering payments, customers, and invoices with nested line items, enum statuses (pending/completed/failed), decimal amounts, and date fields
  - [x] 1.3 Create `weather-utility-openapi-3.0.yaml` in `software/infra/aws/generation/src/test/resources/eval/` with 2-3 simple endpoints for current weather and forecast, minimal schemas
  - [x] 1.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 2. Add REST scenarios to the multi-protocol eval dataset
  - [x] 2.1 Add 14+ REST scenario entries to `multi-protocol-eval-dataset.json` covering all 5 REST APIs (petstore, bored-api, social-content, payment-financial, weather-utility) across 6 complexity levels (Basic, Filtered, Consistency, Error, Realistic_Data, Edge_Case). Update the dataset name and description to reflect the expanded scope. Each scenario must have protocol "REST", format "OPENAPI_3", a valid specFile path, and a precise semanticCheck with concrete verifiable criteria
  - [x] 2.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 3. Add phase-tagged token usage tracking
  - [x] 3.1 Add `TokenPhase` enum (`GENERATION`, `JUDGE`) and add a `phase: TokenPhase` field (default `GENERATION`) to `TokenUsageRecord` in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/`
  - [x] 3.2 Add `currentPhase` property and phase-aware query methods (`getRecordsByPhase()`, `getGenerationRecords()`, `getJudgeRecords()`) to `TokenUsageStore` in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/`
  - [x] 3.3 Update `TokenUsageCapturingClient.converse()` in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/` to tag each recorded `TokenUsageRecord` with `tokenUsageStore.currentPhase`
  - [x] 3.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 4. Add cost breakdown to ScenarioResult and update runScenario
  - [x] 4.1 Add `generationCost` and `judgeCost` fields to `ScenarioResult` data class in `BedrockPromptEvalTest.kt` (keep existing `estimatedCost` as total = generationCost + judgeCost)
  - [x] 4.2 Update `runScenario()` to set `tokenUsageStore.currentPhase = TokenPhase.GENERATION` before generation and `TokenPhase.JUDGE` before the semantic judge call. Compute `generationCost` and `judgeCost` separately using `CostCalculator` and the phase-filtered token records
  - [x] 4.3 Update `logScenarioResult()` to include generation cost and judge cost in the per-scenario log line
  - [x] 4.4 Run `./gradlew clean test` and confirm all tests pass

- [x] 5. Add scenario detail table
  - [x] 5.1 Implement `buildScenarioDetailTable(results: List<ScenarioResult>): String` method in `BedrockPromptEvalTest` that produces a per-scenario breakdown table grouped by API spec name, showing: scenario input, 1st-pass valid rate, after-retry valid rate, pass/fail, generation cost, judge cost, total cost, latency, and failure reason for failed scenarios. Use box-drawing characters for monospace/Markdown compatibility
  - [x] 5.2 Call `buildScenarioDetailTable()` in the main test method and print it alongside the existing summary table
  - [x] 5.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 6. Add cost totals to summary table
  - [x] 6.1 Update `buildSummaryTable()` to add generation cost and judge cost columns to the per-protocol rows, and a total cost footer row at the bottom showing total generation cost, total judge cost, and total combined cost across all protocols
  - [x] 6.2 Run `./gradlew clean test` and confirm all tests pass

- [x] 7. Add BEDROCK_EVAL_FILTER environment variable support
  - [x] 7.1 Update `loadScenarios()` (or add a `filterScenarios()` helper) in `BedrockPromptEvalTest` to read `BEDROCK_EVAL_FILTER` env var and filter scenarios by case-insensitive substring match on the `input` field. When the env var is not set or empty, return all scenarios. Log the filter value and resulting scenario count
  - [x] 7.2 Forward `BEDROCK_EVAL_FILTER` env var in the `bedrockEval` Gradle task configuration in `software/infra/aws/generation/build.gradle.kts`
  - [x] 7.3 Run `./gradlew clean test` and confirm all tests pass

- [x] 8. Update documentation
  - [x] 8.1 Update `docs/PROMPT_EVAL.md` to reflect the expanded suite: update scenario count (from 3 to 17+), add REST to the protocol list, update cost estimates with generation/judge/total breakdown, add example summary and detail table output with REST row, document `BEDROCK_EVAL_FILTER` env var with usage examples in the environment variables table, add protocol breakdown section
  - [x] 8.2 Run `./gradlew clean test` and confirm all tests still pass
