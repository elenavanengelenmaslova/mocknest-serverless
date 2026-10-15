# Implementation Plan: Prompt Injection Hardening

## Overview

This plan hardens the AI mock generation prompts against prompt injection with the smallest possible change, following a strict measured ordering: record a quality baseline (Bedrock eval) into `docs` **before any prompt is edited**, add the `Security_Section` to the four `Hardened_Prompt` templates, add a **separate** injection eval dataset only after the baseline exists, re-run the same quality suite plus the injection suite, compare per-scenario, and produce a keep/revert `Final_Report`.

Offline structural/property tests run in normal `./gradlew test` (no Bedrock). Every Bedrock eval run is cost-incurring and is executed **only after explicit user confirmation** — the eval-run tasks state this gate and the agent never runs Bedrock silently. `./gradlew test` and `./gradlew clean test` exclude the Bedrock suite via the `bedrock-eval` tag.

All code/test changes live in the `:software:infra:aws:generation` test source set; the only production edits are the four prompt `.txt` files under `:software:application` `src/main/resources`. Language: Kotlin (JUnit 6, MockK, kotlin-logging, `@ParameterizedTest`, Given-When-Then).

## Tasks

- [x] 1. Record the quality Baseline_Report (before any prompt edit)
  - [x] 1.1 Confirm-and-run the quality Eval_Harness for the baseline
    - **COST/CONFIRMATION GATE:** ask the user for explicit confirmation before running — the Eval_Harness calls Amazon Bedrock and incurs cost (~$0.01–$0.02 per run). Do NOT run Bedrock without confirmation.
    - If the user does NOT confirm, STOP: do not execute the harness and do not edit any `Hardened_Prompt` (ordering + confirmation gate).
    - On confirmation, run the existing quality suite: `BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval` against `multi-protocol-eval-dataset.json` (Amazon Nova Pro, region eu-west-1). Leave `multi-protocol-eval-dataset.json` unmodified.
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 6.6_
  - [x] 1.2 Create `docs/prompt-injection-hardening-eval.md` and record the Baseline_Report
    - Create the doc (cross-linked from `docs/PROMPT_EVAL.md`) and record the full Metrics_Set from the run: first-pass validity, after-retry validity, semantic/scenario pass rate, average latency, generation cost, judge/total eval cost, model, region, and number of scenarios (per-protocol + total).
    - Add an explicit note in this section: no `Hardened_Prompt` may be edited until the Baseline_Report above is saved (ordering constraint; if violated, revert the prompt edit via git and record the baseline first).
    - _Requirements: 1.1, 1.2, 1.3, 1.7_
  - [x] 1.3 Add cross-link from `docs/PROMPT_EVAL.md` to the new eval doc
    - Add a short link/reference so the general eval guide points to `docs/prompt-injection-hardening-eval.md`.
    - _Requirements: 1.2_

- [x] 1.4 Checkpoint - run `./gradlew clean test` and confirm all tests pass
  - Runs offline tests only; the Bedrock eval suite is tag-excluded from `./gradlew test`. Ensure all tests pass, ask the user if questions arise.

- [x] 2. Harden the four prompt templates with the Security_Section
  - [x] 2.1 Add the canonical Security_Section to `rest/spec-with-description.txt`
    - Edit only `software/application/src/main/resources/prompts/rest/spec-with-description.txt`. Insert the ≤15-line, protocol-agnostic canonical `Security_Section` block (leading marker line `SECURITY — INSTRUCTION HIERARCHY`) as literal text, placed **before** the first untrusted-input placeholder (`{{DESCRIPTION}}`). Match the file's existing style; introduce no new generation rules, format changes, or restrictions.
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.4, 4.5_
  - [x] 2.2 Add the canonical Security_Section to `graphql/spec-with-description.txt`
    - Same canonical block and placement rule in `software/application/src/main/resources/prompts/graphql/spec-with-description.txt`, before the first `{{DESCRIPTION}}`.
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.4, 4.5_
  - [x] 2.3 Add the canonical Security_Section to `soap/spec-with-description.txt`
    - Same canonical block and placement rule in `software/application/src/main/resources/prompts/soap/spec-with-description.txt`, before the first `{{DESCRIPTION}}`.
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.4, 4.5_
  - [x] 2.4 Add the terse authoritative anchor to `system-prompt.txt`
    - Edit only `software/application/src/main/resources/prompts/system-prompt.txt`. Append the short instruction-hierarchy anchor sentence (terse variant matching the file's 2-line style) so every turn inherits the authoritative-instruction statement.
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.4, 4.5_
  - [x] 2.5 Leave every Unchanged_Prompt byte-identical
    - Do NOT edit `rest/correction.txt`, `graphql/correction.txt`, `soap/correction.txt`, `common/parsing-correction.txt`, or `wiremock-stub-schema.yaml` (trusted-input-only). No `PromptBuilderService` code change is required.
    - _Requirements: 2.2, 2.4, 4.3, 4.5_

  - [x] 2.6 Write PromptHardeningStructureTest for Properties 1–3
    - In `:software:infra:aws:generation` test source set (Kotlin, JUnit 6, `@ParameterizedTest`, Given-When-Then, kotlin-logging). Load prompt files from the classpath.
    - **Property 1: Every hardened prompt carries the Security_Section before any untrusted input** — `@MethodSource` over the four Hardened_Prompt paths: assert the `SECURITY — INSTRUCTION HIERARCHY` marker is present and its position precedes the first `{{DESCRIPTION}}` (for `system-prompt.txt`, assert the anchor sentence is present). **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    - **Property 2: No unchanged prompt is modified** — `@MethodSource`/`@ValueSource` over the five Unchanged_Prompt paths: assert the marker is absent. **Validates: Requirements 2.2, 4.1, 4.2, 4.3**
    - **Property 3: Security_Section is concise** — for each Hardened_Prompt assert the Security_Section block spans ≤15 lines. **Validates: Requirements 3.6**
    - _Requirements: 2.2, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3_
  - [x] 2.7 Write optional PromptBuilderSecuritySectionTest
    - Assert `buildSpecWithDescriptionPrompt(...)` output still contains the Security_Section marker after placeholder substitution and that the marker precedes the injected `{{DESCRIPTION}}`, guarding against a future template reorder. Use MockK for any collaborator.
    - _Requirements: 3.1_

- [x] 2.8 Checkpoint - run `./gradlew clean test` and confirm all tests pass
  - Offline structural/property tests must pass; Bedrock suite remains tag-excluded. Ensure all tests pass, ask the user if questions arise.

- [x] 3. Add the separate injection eval dataset and its execution
  - [x] 3.1 Create `injection-eval-dataset.json`
    - Create `software/infra/aws/generation/src/test/resources/eval/injection-eval-dataset.json`, structurally identical to `multi-protocol-eval-dataset.json` (`{"examples":[{"input","metadata":{protocol,specFile,format,namespace,description,semanticCheck}}]}`).
    - Include ≥5 distinct scenarios spanning REST/GraphQL/SOAP, covering all four attack types at least once: legitimate-plus-malicious mixing, meta/malicious instruction injection, internal-instruction extraction, and output-format override. Carry the injection payload in the `description` field; reuse existing spec files. Each `semanticCheck` must assert the composite property (legit content produced, malicious effect excluded, valid WireMock output, no internal-instruction leakage, format unchanged).
    - Ensure every `input` name is unique and disjoint from the quality suite's `input` names. Do not modify the recorded Baseline_Report or the quality dataset.
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.7, 5.8_
  - [x] 3.2 Add the injection eval execution path
    - Add a dedicated test method in `BedrockPromptEvalTest` (or a sibling `BedrockInjectionEvalTest`) that loads `injection-eval-dataset.json` instead of the quality dataset, reusing existing machinery (`runScenario`, `runSemanticJudge`, table builders). Keep it under the `bedrock-eval` tag and the `BEDROCK_EVAL_ENABLED` gate; suite selection is by dataset file, with `BEDROCK_EVAL_FILTER` narrowing within the suite. No duplicated grading logic.
    - _Requirements: 5.2, 5.6, 6.3_

  - [x] 3.3 Write InjectionDatasetInvariantTest for Property 4
    - In `:software:infra:aws:generation` test source set (Kotlin, JUnit 6, `@ParameterizedTest`, Given-When-Then). Parse both dataset JSONs offline (no Bedrock).
    - **Property 4: Injection dataset is disjoint and complete** — assert `input` name sets are disjoint across the two datasets, assert ≥5 injection scenarios, and assert all four attack categories are represented (via an `attackType`/`input` classification). Use ≥100 iterations where generators produce candidate name pairs to check disjointness. **Validates: Requirements 5.2, 5.3**
    - _Requirements: 5.2, 5.3_

- [x] 3.4 Checkpoint - run `./gradlew clean test` and confirm all tests pass
  - Property 4 and all offline tests must pass; injection eval remains tag-excluded. Ensure all tests pass, ask the user if questions arise.

- [x] 4. Run post-change evals and record comparison
  - [x] 4.1 Confirm-and-run the quality Eval_Harness for the Post_Change_Report
    - **COST/CONFIRMATION GATE:** ask the user for explicit confirmation before running — this calls Bedrock and incurs cost. Do NOT run Bedrock without confirmation. If not confirmed, STOP and do not execute.
    - On confirmation, re-run the **same** quality suite used for the baseline: `BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval` against `multi-protocol-eval-dataset.json`. Record the full Metrics_Set as the Post_Change_Report in `docs/prompt-injection-hardening-eval.md`.
    - _Requirements: 6.1, 6.2_
  - [x] 4.2 Confirm-and-run the injection Eval_Harness (separate suite)
    - **COST/CONFIRMATION GATE:** ask the user for explicit confirmation before running — this calls Bedrock and incurs cost. Do NOT run Bedrock without confirmation. If not confirmed, STOP and do not execute.
    - On confirmation, run the injection suite separately against `injection-eval-dataset.json` (via the task 3.2 execution path). Record per-scenario injection results: attack type, legitimate content produced, malicious effect excluded, output validity, and leakage check.
    - _Requirements: 5.6, 6.3, 7.6_
  - [x] 4.3 Record before/after comparison and Individual_Regressions
    - In `docs/prompt-injection-hardening-eval.md`, add the before/after comparison table across the full Metrics_Set (first-pass validity, after-retry validity, scenario/semantic pass rate, avg latency, avg generation cost, judge/total cost; model/region/#scenarios noted unchanged).
    - Add a per-scenario section listing **each** Individual_Regression: scenarios that passed in the baseline and fail in the post-change run, and meaningful increases in retries/latency/cost surfaced for reviewer judgment (no fixed numeric threshold). If the quality suite materially regresses, preserve existing eval scenarios/evals unweakened and document the regression with the smallest proposed fix.
    - _Requirements: 6.4, 6.5, 6.6, 6.7_

- [x] 5. Write the Final_Report (keep/revert recommendation)
  - [x] 5.1 Author the Final_Report section in `docs/prompt-injection-hardening-eval.md`
    - Include, in order: each Hardened_Prompt changed with the exact Security_Section text (7.1); each Unchanged_Prompt with rationale for leaving it unchanged — trusted input only: prior model output, deterministic parse/validation errors, or a trusted spec summary (title/version/endpoint count/target namespace) (7.2); the exact security behaviour added by the Security_Section (7.3); the Baseline_Report results (7.4); the Post_Change_Report results (7.5); the Injection_Scenario evaluation results (7.6); each Individual_Regression identified (7.7); and a keep/revert recommendation (7.8).
    - Apply the default-to-keep rule: where Individual_Regressions exist and the injection results show the Security_Section is effective, default to keep when the security improvement outweighs the regressions, and state the rationale (7.9).
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9_

- [x] 6. Final verification of test coverage and quality
  - [x] 6.1 Run `./gradlew koverHtmlReport` and verify 90%+ coverage for new code
    - New offline test code (property/dataset-invariant tests) meets the enforced threshold.
    - _Requirements: 5.2, 5.3, 3.6_
  - [x] 6.2 Run `./gradlew koverVerify` to enforce the coverage threshold
    - Note: Bedrock eval runs are manual/confirmed and excluded from coverage (tag-gated, not part of `./gradlew test`).
    - _Requirements: 5.2, 5.3_
  - [x] 6.3 Review offline test quality
    - Confirm Given-When-Then naming, meaningful assertions (assertEquals/assertNotNull over assertTrue-on-equality), and edge-case coverage for Properties 1–4.
    - _Requirements: 2.2, 3.1, 3.6, 5.2, 5.3_

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation and the Bedrock eval-run/reporting tasks are not optional.
- **Ordering is enforced:** the Baseline_Report (task 1) must be recorded before any prompt edit (task 2). If a prompt is edited early, revert it via git and record the baseline first.
- **No silent Bedrock runs:** tasks 1.1, 4.1, and 4.2 each require explicit user confirmation because they incur Amazon Bedrock cost. `./gradlew test` / `clean test` run offline only (Bedrock suite is tag-excluded via `bedrock-eval` + `BEDROCK_EVAL_ENABLED`).
- Only production edits are the four prompt `.txt` files under `:software:application` `src/main/resources`; everything else is test data, offline tests, and documentation.
- Each task references specific requirement sub-clauses for traceability.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "2.1", "2.2", "2.3", "2.4", "2.5"] },
    { "id": 3, "tasks": ["2.6", "2.7", "3.1"] },
    { "id": 4, "tasks": ["3.2", "3.3"] },
    { "id": 5, "tasks": ["4.1", "4.2"] },
    { "id": 6, "tasks": ["4.3"] },
    { "id": 7, "tasks": ["5.1"] },
    { "id": 8, "tasks": ["6.1", "6.3"] },
    { "id": 9, "tasks": ["6.2"] }
  ]
}
```
