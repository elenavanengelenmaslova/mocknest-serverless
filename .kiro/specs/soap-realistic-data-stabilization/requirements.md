# Requirements Document

## Introduction

This spec stabilizes a single flaky Bedrock quality-eval scenario, `soap-notification-realistic-data`, that surfaced during the completed `prompt-injection-hardening` work. A targeted 5-iteration re-run confirmed the scenario is non-deterministic: it passed 3 of 5 runs at 100% structural validity every time, flipping only on the LLM-judge semantic verdict at the 0.7 threshold. The scenario's `semanticCheck` requires realistic, non-placeholder data — a `status` element with a realistic value (rejecting obvious placeholders such as `status1` or `test`) and `sentAt`/`deliveredAt` fields with ISO-8601 timestamp-like values.

The instability has two independent contributors, and this spec addresses both:

1. The SOAP generation prompt (`software/application/src/main/resources/prompts/soap/spec-with-description.txt`) has detailed structural, fault, namespace, urlPath, and XPath rules but no guidance instructing the model to populate SOAP response bodies with realistic, non-placeholder data values.
2. The eval harness defaults to a single iteration (`parseIterationCount` in `EvalMetrics.kt` returns 1 when `BEDROCK_EVAL_ITERATIONS` is unset), so borderline scenarios coin-flip at the judge threshold rather than averaging across runs.

The primary fix is the SOAP prompt improvement plus raising the default iteration count. Loosening the dataset `semanticCheck` for this scenario is explicitly a documented fallback of last resort, not the main approach. The work is scoped to the SOAP prompt and the offline eval config/tests; REST and GraphQL prompts and the injection hardening are out of scope and must not be weakened. Bedrock eval runs are cost-gated and require explicit user confirmation before running.

## Glossary

- **SOAP_Prompt**: The prompt template at `software/application/src/main/resources/prompts/soap/spec-with-description.txt` used to generate SOAP/WSDL mocks.
- **Security_Section**: The "SECURITY — INSTRUCTION HIERARCHY" block in the SOAP_Prompt, added by the `prompt-injection-hardening` spec, positioned before the `{{DESCRIPTION}}` placeholder.
- **Realistic_Data_Guidance**: The new, minimal guidance added to the SOAP_Prompt instructing the model to emit realistic, non-placeholder data values in SOAP response bodies.
- **Iteration_Parser**: The `parseIterationCount` function in `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/EvalMetrics.kt`, which resolves the eval iteration count from the `BEDROCK_EVAL_ITERATIONS` environment variable.
- **Default_Iteration_Count**: The iteration count returned by the Iteration_Parser when `BEDROCK_EVAL_ITERATIONS` is unset.
- **Eval_Suite**: The Bedrock prompt evaluation suite invoked via `./gradlew :software:infra:aws:generation:bedrockEval`, driven by `multi-protocol-eval-dataset.json`.
- **Target_Scenario**: The `soap-notification-realistic-data` eval scenario.
- **Quality_Dataset**: `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json`.
- **Eval_Report**: The documentation location recording before/after eval results (`docs/prompt-injection-hardening-eval.md` or a clearly-linked new doc, decided in design).
- **Eval_Guide**: `docs/PROMPT_EVAL.md`, the general guide for running the Eval_Suite.
- **Offline_Build**: The `./gradlew clean test` run plus `./gradlew koverVerify` coverage gate, which never calls Bedrock.

## Requirements

### Requirement 1: Add realistic-data guidance to the SOAP prompt

**User Story:** As a mock consumer, I want SOAP responses to contain realistic, non-placeholder data values, so that the generated mocks are usable for testing without manual editing and the Target_Scenario stops failing on placeholder-looking values.

#### Acceptance Criteria

1. THE SOAP_Prompt SHALL include Realistic_Data_Guidance instructing the model to populate SOAP response bodies with realistic, non-placeholder data values.
2. THE Realistic_Data_Guidance SHALL instruct the model to use realistic status and enumeration strings and to avoid placeholder values such as `status1`, `test`, and `string`.
3. THE Realistic_Data_Guidance SHALL instruct the model to use ISO-8601 timestamp values for date and time fields.
4. THE Realistic_Data_Guidance SHALL instruct the model to use realistic identifier values for identifier fields.
5. THE Realistic_Data_Guidance SHALL instruct the model to continue honoring the enhancement description and the WSDL schema when producing realistic values.
6. THE Realistic_Data_Guidance SHALL be scoped to data-value guidance only and SHALL NOT introduce new required output-format restrictions.

### Requirement 2: Preserve existing SOAP prompt rules and injection hardening

**User Story:** As a maintainer, I want the realistic-data change to leave all existing SOAP behavior and security hardening intact, so that no other SOAP scenario regresses and the injection protections remain in force.

#### Acceptance Criteria

1. WHEN the Realistic_Data_Guidance is added, THE SOAP_Prompt SHALL retain its existing structural, fault, namespace, urlPath, and XPath rules unchanged.
2. THE SOAP_Prompt SHALL retain the Security_Section unchanged in wording and SHALL keep it positioned before the `{{DESCRIPTION}}` placeholder.
3. THE change SHALL modify only the SOAP_Prompt and SHALL leave the REST and GraphQL prompt templates unchanged.
4. IF adding the Realistic_Data_Guidance would require weakening, removing, or reordering any existing SOAP_Prompt rule or the Security_Section, THEN the change SHALL be rejected.

### Requirement 3: Raise the default eval iteration count

**User Story:** As a prompt engineer, I want the eval harness to run multiple iterations by default, so that borderline scenarios average out across runs instead of coin-flipping on a single-shot judge verdict.

#### Acceptance Criteria

1. WHEN `BEDROCK_EVAL_ITERATIONS` is unset, THE Iteration_Parser SHALL return a Default_Iteration_Count of at least 3.
2. WHEN `BEDROCK_EVAL_ITERATIONS` is set to a valid positive integer, THE Iteration_Parser SHALL return that provided value.
3. IF `BEDROCK_EVAL_ITERATIONS` is empty or blank, THEN THE Iteration_Parser SHALL reject the value with a descriptive error.
4. IF `BEDROCK_EVAL_ITERATIONS` is non-numeric, THEN THE Iteration_Parser SHALL reject the value with a descriptive error.
5. IF `BEDROCK_EVAL_ITERATIONS` is zero or negative, THEN THE Iteration_Parser SHALL reject the value with a descriptive error.
6. THE Iteration_Parser SHALL have Given-When-Then unit tests covering the new Default_Iteration_Count when the variable is unset, the explicit override path, and each rejection case.

### Requirement 4: Before/after Bedrock measurement of the SOAP fix (cost-gated)

**User Story:** As a reviewer, I want measured before/after eval results for the Target_Scenario with a regression guardrail on other SOAP scenarios, so that I can confirm the prompt change helped without harming the rest of the suite.

#### Acceptance Criteria

1. THE Eval_Suite runs call Amazon Bedrock and incur cost, so WHEN an Eval_Suite run is required, THE workflow SHALL obtain explicit user confirmation before running per the project prompt-eval cost policy and the Eval_Guide.
2. IF the user does not confirm an Eval_Suite run, THEN THE run SHALL NOT be executed.
3. THE before measurement SHALL establish a baseline pass rate for the Target_Scenario over multiple iterations prior to the SOAP_Prompt change.
4. WHEN the SOAP_Prompt change is applied, THE after measurement SHALL record the Target_Scenario pass rate over multiple iterations and SHALL show a pass rate that is improved or no worse than the baseline.
5. IF a baseline was not established OR the SOAP_Prompt change was not applied, THEN THE after measurement SHALL be treated as invalid and SHALL NOT be reported as a valid before/after comparison.
6. THE after measurement SHALL confirm that other SOAP eval scenarios do not regress relative to their baseline pass status.
7. IF the after measurement shows the Target_Scenario still failing after the SOAP_Prompt change and iteration-count change, THEN loosening the Quality_Dataset `semanticCheck` for the Target_Scenario SHALL be considered only as a documented fallback of last resort, after the prompt and methodology fixes.

### Requirement 5: Document results and the methodology change

**User Story:** As a future maintainer, I want the confirmed re-run data, the before/after results, and the iteration-default change recorded, so that the stabilization work is traceable and the eval methodology is documented.

#### Acceptance Criteria

1. THE Eval_Report SHALL record the confirmed re-run results for the Target_Scenario (3 of 5 pass at 100% structural validity) and the confirmed non-regression of `rest-payment-basic` (5 of 5 pass) as context.
2. THE Eval_Report SHALL record the before and after pass rates for the Target_Scenario from Requirement 4.
3. THE Eval_Report SHALL record the SOAP-scenario non-regression guardrail outcome from Requirement 4.
4. THE Eval_Guide SHALL be updated to reflect the new Default_Iteration_Count for `BEDROCK_EVAL_ITERATIONS` when unset; any textual update to the Eval_Guide describing the changed default SHALL satisfy this criterion (it need not restate a specific numeric value).

### Requirement 6: Keep the offline build and coverage green

**User Story:** As a maintainer, I want all offline verification to pass after these changes, so that the build stays releasable without incurring any Bedrock cost.

#### Acceptance Criteria

1. WHEN the offline test suite is run via `./gradlew clean test`, THE Offline_Build SHALL pass without calling Bedrock.
2. WHEN coverage is verified via `./gradlew koverVerify`, THE aggregated project coverage SHALL remain at or above 90%.
3. THE SOAP_Prompt and Iteration_Parser changes SHALL keep code within its clean-architecture layer, with the prompt as an application-layer resource and the Iteration_Parser in the `:software:infra:aws:generation` test source set.
4. THE Offline_Build verification SHALL NOT require any Amazon Bedrock call.
