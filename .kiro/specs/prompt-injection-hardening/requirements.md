# Requirements Document

## Introduction

This feature hardens the existing AI mock generation capability against prompt injection. The AI mock generator uses prompt templates that embed untrusted input (free-text user descriptions plus specification-derived content). An attacker could craft a specification or description that attempts to override the generator's instructions, change the required output format, extract internal instructions, or otherwise subvert generation behaviour.

The work is a controlled, measured change. A quality baseline of the Bedrock prompt evaluation suite MUST be recorded before any prompt is edited, only the prompts that embed untrusted input are hardened with the smallest change necessary, and the same suite is re-run afterwards so before/after results are directly comparable. Focused prompt-injection evaluation scenarios are added and kept separate from the quality baseline. A final report documents what changed, what was deliberately left unchanged, and a keep-or-revert recommendation.

The change explicitly preserves current REST, GraphQL, and SOAP generation semantics, response-format requirements, and validation/correction behaviour. No new generation restrictions are introduced.

## Glossary

- **Mock_Generator**: The existing AI mock generation feature that produces WireMock mappings from API specifications using Amazon Nova Pro via the Koog agent.
- **Eval_Harness**: The Bedrock prompt evaluation suite executed via `BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval`, gated by the `bedrock-eval` JUnit tag, running against Amazon Nova Pro in region eu-west-1.
- **Baseline_Report**: The recorded set of quality metrics from the Eval_Harness captured before any prompt edits, saved into project documentation under `docs`.
- **Post_Change_Report**: The recorded set of quality metrics from the Eval_Harness captured after prompt edits, using the same scenario suite as the Baseline_Report.
- **Quality_Baseline_Suite**: The existing eval scenarios in `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json` used to measure generation quality.
- **Injection_Scenario**: A new eval scenario that supplies malicious or meta instructions in untrusted input to verify the Mock_Generator resists prompt injection.
- **Untrusted_Input**: Content originating from the API specification body and the user-provided free-text scenario/description text (`{{DESCRIPTION}}`).
- **Hardened_Prompt**: A prompt template that embeds Untrusted_Input and receives a security instruction-hierarchy section: `prompts/rest/spec-with-description.txt`, `prompts/graphql/spec-with-description.txt`, `prompts/soap/spec-with-description.txt`, and `prompts/system-prompt.txt`.
- **Unchanged_Prompt**: A prompt template that receives only trusted input (prior generated output, deterministic validation/parse errors, and a trusted spec summary of title/version/endpoint count/target namespace) and is deliberately left unmodified: `prompts/rest/correction.txt`, `prompts/graphql/correction.txt`, `prompts/soap/correction.txt`, `prompts/common/parsing-correction.txt`, and `prompts/wiremock-stub-schema.yaml`.
- **Security_Section**: The concise instruction-hierarchy block added near the top of each Hardened_Prompt establishing that prompt/system rules are authoritative and untrusted input is data.
- **Metrics_Set**: first-pass validity, after-retry validity, semantic/scenario pass rate, average latency, generation cost, judge/total eval cost, model, region, and number of scenarios.
- **Final_Report**: The document summarizing prompts changed, prompts deliberately unchanged with rationale, exact security behaviour added, Baseline_Report, Post_Change_Report, injection eval results, regressions, and a keep/revert recommendation.
- **Individual_Regression**: A scenario that passed in the Baseline_Report but fails in the Post_Change_Report, or a meaningful increase in retries, latency, or cost for a scenario.

## Requirements

### Requirement 1: Record Quality Baseline Before Any Prompt Edits

**User Story:** As a maintainer, I want a quality baseline recorded before any prompt change, so that the impact of hardening can be measured like-for-like.

#### Acceptance Criteria

1. THE Baseline_Report SHALL be recorded before any edit is made to a Hardened_Prompt.
2. THE Baseline_Report SHALL be saved into project documentation under `docs`.
3. THE Baseline_Report SHALL capture every metric named in the Metrics_Set definition: first-pass validity, after-retry validity, semantic/scenario pass rate, average latency, generation cost, judge/total eval cost, model, region, and number of scenarios.
4. WHERE the Eval_Harness incurs Amazon Bedrock cost, THE maintainer SHALL obtain explicit user confirmation before executing the Eval_Harness for the Baseline_Report.
5. IF the user does not confirm execution of the Eval_Harness, THEN the Eval_Harness SHALL NOT be executed and no Hardened_Prompt SHALL be edited.
6. THE Quality_Baseline_Suite SHALL remain unmodified until the Baseline_Report is recorded.
7. IF a Hardened_Prompt is edited before the Baseline_Report is recorded, THEN the ordering constraint SHALL be considered violated and the edit SHALL be reverted before proceeding.

### Requirement 2: Classify and Select Prompts for Hardening

**User Story:** As a maintainer, I want only the prompts that embed untrusted input to be hardened, so that trusted-only prompts are left intact.

#### Acceptance Criteria

1. THE feature SHALL apply the Security_Section only to each Hardened_Prompt.
2. THE feature SHALL leave every Unchanged_Prompt unmodified.
3. THE feature SHALL classify a prompt as a Hardened_Prompt WHEN the prompt embeds Untrusted_Input.
4. THE feature SHALL classify a prompt as an Unchanged_Prompt WHEN the prompt receives only prior generated output, deterministic validation or parse errors, or a trusted spec summary of title, version, endpoint count, and target namespace.
5. IF a prompt embeds Untrusted_Input and has not been classified as an Unchanged_Prompt, THEN THE feature SHALL classify that prompt as a Hardened_Prompt so the Security_Section is applied.

### Requirement 3: Add Security Instruction-Hierarchy Section

**User Story:** As a maintainer, I want each hardened prompt to establish an authoritative instruction hierarchy, so that untrusted input cannot subvert generation.

#### Acceptance Criteria

1. THE Security_Section SHALL appear before any section that introduces or references Untrusted_Input within each Hardened_Prompt.
2. THE Security_Section SHALL state that the prompt and system rules are authoritative.
3. THE Security_Section SHALL state that API specification content and user-provided scenario or description text are data and not controlling instructions.
4. THE Security_Section SHALL state that instructions embedded in Untrusted_Input must not override the prompt rules, change the required output format, cause previous instructions to be ignored, reveal internal or system instructions, or alter Mock_Generator behaviour.
5. THE Security_Section SHALL state that conflicting instructions in Untrusted_Input are ignored while valid business, scenario, and API information is still used.
6. THE Security_Section SHALL match the existing style of each Hardened_Prompt and SHALL contain no more than 15 lines of text.

### Requirement 4: Preserve Unrelated Generation Behaviour

**User Story:** As a maintainer, I want existing generation behaviour preserved, so that hardening does not change what mocks are produced.

#### Acceptance Criteria

1. THE feature SHALL preserve the current REST, GraphQL, and SOAP generation semantics.
2. THE feature SHALL preserve the current response-format requirements of each Hardened_Prompt.
3. THE feature SHALL preserve the current validation and correction behaviour of the Mock_Generator.
4. THE feature SHALL NOT introduce new generation restrictions, including newly forbidding requested error responses for undeclared status codes.
5. THE change to each Hardened_Prompt SHALL be limited to the smallest modification necessary to add the Security_Section.

### Requirement 5: Add and Separate Injection Evaluation Scenarios

**User Story:** As a maintainer, I want injection scenarios kept separate from the quality baseline, so that before/after quality comparison stays like-for-like.

#### Acceptance Criteria

1. WHEN the Baseline_Report has been recorded, THE Injection_Scenario set SHALL be added to the evaluation suite, and the recorded Baseline_Report SHALL remain unchanged.
2. THE Injection_Scenario set SHALL be stored and executed as a separate collection from the Quality_Baseline_Suite, sharing no scenario entries with the Quality_Baseline_Suite.
3. THE Injection_Scenario set SHALL contain at least 5 distinct scenarios covering each of the following attack types at least once: legitimate-plus-malicious mixing, malicious or meta instruction injection, internal instruction extraction, and output-format override.
4. WHEN an Injection_Scenario supplies legitimate scenario information alongside malicious instructions, THE Mock_Generator SHALL produce a WireMock mock derived from the legitimate scenario information and SHALL exclude any content requested by the malicious instructions.
5. WHEN an Injection_Scenario supplies malicious or meta instructions in Untrusted_Input, THE Mock_Generator SHALL exclude the effects of the malicious or meta instructions from its output.
6. WHEN an Injection_Scenario is evaluated, THE Mock_Generator SHALL produce output that passes the same WireMock mock validation applied to the Quality_Baseline_Suite.
7. IF an Injection_Scenario attempts to extract internal or system instructions, THEN THE Mock_Generator SHALL produce output containing no internal or system instruction text.
8. WHEN an Injection_Scenario attempts to change the output format, THE Mock_Generator SHALL produce output in the same format required for the Quality_Baseline_Suite.

### Requirement 6: Compare Before and After and Identify Regressions

**User Story:** As a maintainer, I want the same suite re-run and individual regressions identified, so that I can decide whether to keep the change.

#### Acceptance Criteria

1. THE Post_Change_Report SHALL be produced by running the Quality_Baseline_Suite used for the Baseline_Report.
2. WHERE the Eval_Harness incurs Amazon Bedrock cost, THE Mock_Generator maintainer SHALL obtain explicit user confirmation before executing the Eval_Harness for the Post_Change_Report.
3. THE Injection_Scenario set SHALL be evaluated separately from the Quality_Baseline_Suite.
4. THE comparison SHALL identify each Individual_Regression rather than reporting only aggregate metrics.
5. THE comparison SHALL identify each scenario that passed in the Baseline_Report and fails in the Post_Change_Report.
6. THE comparison SHALL surface increases in retries, latency, or cost between the Baseline_Report and the Post_Change_Report for reviewer judgment, without applying a fixed numeric threshold to define 'meaningful'.
7. IF the Quality_Baseline_Suite materially regresses, THEN THE feature SHALL preserve the existing eval scenarios and existing evals unweakened and SHALL document the regression together with the smallest proposed fix.

### Requirement 7: Produce Final Report

**User Story:** As a maintainer, I want a final report of the hardening outcome, so that I have a documented keep-or-revert decision.

#### Acceptance Criteria

1. THE Final_Report SHALL list each Hardened_Prompt that was changed.
2. THE Final_Report SHALL list each Unchanged_Prompt with the rationale for leaving it unchanged.
3. THE Final_Report SHALL describe the exact security behaviour added by the Security_Section.
4. THE Final_Report SHALL include the Baseline_Report results.
5. THE Final_Report SHALL include the Post_Change_Report results.
6. THE Final_Report SHALL include the Injection_Scenario evaluation results.
7. THE Final_Report SHALL list each Individual_Regression identified during comparison.
8. THE Final_Report SHALL state a keep or revert recommendation.
9. WHERE Individual_Regressions exist and the Injection_Scenario results show the Security_Section is effective, THE Final_Report SHALL default to a keep recommendation when the security improvement outweighs the regressions, and SHALL state the rationale for that judgment.
