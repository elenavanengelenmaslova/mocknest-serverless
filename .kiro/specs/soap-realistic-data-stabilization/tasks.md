# Implementation Plan: SOAP Realistic Data Stabilization

## Overview

This plan stabilizes the flaky `soap-notification-realistic-data` Bedrock eval scenario by (a) adding a minimal, data-value-only realistic-data guidance subsection to the SOAP prompt, and (b) raising the default eval iteration count from 1 to 3. It also records the confirmed re-run data, before/after results, and the methodology change in documentation.

All changed/added Kotlin lives in the `:software:infra:aws:generation` **test** source set; the SOAP prompt is an application-layer **resource**. No production Kotlin, domain models, DI wiring, or interfaces change.

The Bedrock measurement tasks (baseline, after, SOAP non-regression) are **manual and cost-incurring**. They are marked optional (`*`) and require **explicit user confirmation before running** per the `docs/PROMPT_EVAL.md` cost policy. An automated runner MUST NOT execute them without confirmation.

**Ordering constraint (REQ 4.5 validity guard):** the baseline (before) Bedrock run MUST be captured BEFORE the SOAP prompt edit; the after run MUST happen AFTER the prompt edit. The task ordering below enforces this.

## Tasks

- [ ] 1. Capture baseline measurement for the target scenario (cost-gated, confirm first)
  - [ ]* 1.1 Obtain explicit user confirmation, then run the baseline Bedrock eval for the target scenario (prompt UNCHANGED)
    - **MANUAL / COST-INCURRING — requires EXPLICIT user confirmation before running.** Ask: "The prompt eval tests call Bedrock and cost ~$0.01–$0.02 per run. Run them before and after the change to measure impact?" If the user does not confirm, DO NOT run.
    - This baseline MUST be captured BEFORE any SOAP prompt edit (Task 3), or the after comparison is invalid (REQ 4.5).
    - Exact command (target scenario, 5 iterations, prompt unchanged):
      ```bash
      BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_DEMO=true \
      BEDROCK_EVAL_FILTER=soap-notification-realistic-data BEDROCK_EVAL_ITERATIONS=5 \
        ./gradlew :software:infra:aws:generation:bedrockEval --rerun-tasks \
        --tests "*BedrockPromptEvalTest*multi-protocol*"
      ```
    - Capture: scenario pass rate (x/5), first-pass structural validity %, semantic verdict outcomes, avg latency, avg cost/run.
    - _Requirements: 4.1, 4.2, 4.3_
  - [ ] 1.2 Create `docs/soap-realistic-data-stabilization-eval.md` with the offline-authorable sections and the recorded baseline
    - Add Context/background (link this spec and the `prompt-injection-hardening` work that surfaced the flake).
    - Record confirmed re-run results: `soap-notification-realistic-data` 3 of 5 pass at 100% structural validity; `rest-payment-basic` 5 of 5 pass (non-regression context).
    - Add Methodology-change section: default iteration count is now 3 when `BEDROCK_EVAL_ITERATIONS` is unset, and why; note the ~3× full-suite cost implication.
    - Add a Before/After results table (baseline row filled from Task 1.1 if confirmed; after row left pending) with the REQ 4.5 validity-guard note.
    - Add placeholder sections for the SOAP non-regression guardrail outcome and the last-resort fallback note.
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ] 1.3 Checkpoint - run `./gradlew clean test` and confirm all tests pass
    - Ensure all tests pass, ask the user if questions arise.

- [ ] 2. Raise the default eval iteration count (parser change)
  - [ ] 2.1 Add `DEFAULT_EVAL_ITERATIONS` and change the null-branch default in `EvalMetrics.kt`
    - In `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/EvalMetrics.kt`, add `const val DEFAULT_EVAL_ITERATIONS = 3`.
    - Change the null-branch of `parseIterationCount` from `return 1` to `return DEFAULT_EVAL_ITERATIONS`.
    - Update the KDoc `@return` line to reference the new default; leave all validation (non-blank, numeric, positive) unchanged.
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [ ]* 2.2 Write property/unit tests for `parseIterationCount` in a new `EvalMetricsTest.kt`
    - **Feature: soap-realistic-data-stabilization, Property 2: parseIterationCount behavior**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
    - Create `EvalMetricsTest.kt` in package `nl.vintik.mocknest.infra.aws.generation.ai.eval` (generation test source set). If a pre-existing parser test is found, update its "unset → default" assertion to 3 and add missing cases instead of duplicating.
    - Given-When-Then, JUnit 6. Cover: unset → default 3 (focused case); `@ParameterizedTest` over several valid positive integers → returns that value; `@ParameterizedTest` over blank/empty, non-numeric, and zero/negative → throws `IllegalArgumentException` with a descriptive message.
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  - [ ] 2.3 Checkpoint - run `./gradlew clean test` and confirm all tests pass
    - Ensure all tests pass, ask the user if questions arise.

- [ ] 3. Add Realistic_Data_Guidance to the SOAP prompt and guard structure
  - [ ] 3.1 Add the `Realistic data values` bullet sub-group to the SOAP prompt
    - Edit `software/application/src/main/resources/prompts/soap/spec-with-description.txt`.
    - Append the `Realistic data values (response body content):` bullet sub-group at the END of the `SOAP 1.2 response format rules (IMPORTANT):` section, immediately after the existing `Response HTTP status code is always 200 for successful SOAP responses` bullet, and BEFORE the `SOAP 1.2 fault format (for error scenarios):` heading. Use the exact bullet text from design section (a).
    - Do NOT touch the `SECURITY — INSTRUCTION HIERARCHY` block (must stay unchanged and before `{{DESCRIPTION}}`) or any existing structural/fault/namespace/urlPath/XPath rule. Data-value-only; no new output-format restriction.
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.4_
  - [ ]* 3.2 Extend `PromptHardeningStructureTest` with the SOAP structural-regression assertion (Property 1)
    - **Feature: soap-realistic-data-stabilization, Property 1: SOAP prompt structural regression guard**
    - **Validates: Requirements 1.1, 1.6, 2.1, 2.2, 2.4**
    - In `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/prompt/PromptHardeningStructureTest.kt`, add a focused SOAP-only assertion: `SECURITY — INSTRUCTION HIERARCHY` precedes the first `{{DESCRIPTION}}`; all pre-existing section anchors are present (`API Specification Summary`, `Namespace`, `Requirements:`, `SOAP 1.2 request matching rules`, `WHICH OPERATIONS TO GENERATE`, `SOAP 1.2 response format rules`, `SOAP 1.2 fault format`, `{{WIREMOCK_SCHEMA}}`); the new `Realistic data values` marker is present and positioned after `SOAP 1.2 response format rules` and before `SOAP 1.2 fault format`.
    - Keep the existing Property 1/Property 3 assertions in this test green.
    - _Requirements: 1.1, 1.6, 2.1, 2.2, 2.4_
  - [ ]* 3.3 Add the non-SOAP-unchanged assertion (Property 3)
    - **Feature: soap-realistic-data-stabilization, Property 3: Non-SOAP prompts unchanged**
    - **Validates: Requirements 2.3**
    - In `PromptHardeningStructureTest.kt`, assert the REST and GraphQL `spec-with-description.txt` prompts do NOT contain the SOAP `Realistic data values` marker.
    - _Requirements: 2.3_
  - [ ] 3.4 Checkpoint - run `./gradlew clean test` and confirm all tests pass
    - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. After measurement and SOAP non-regression guardrail (cost-gated, confirm)
  - [ ]* 4.1 Obtain explicit user confirmation, then run the after Bedrock eval for the target scenario (prompt CHANGED)
    - **MANUAL / COST-INCURRING — requires EXPLICIT user confirmation before running.** If the user does not confirm, DO NOT run.
    - MUST run AFTER the SOAP prompt edit (Task 3.1) and AFTER the iteration-count change (Task 2.1), and only if the baseline (Task 1.1) was captured — otherwise the comparison is invalid (REQ 4.5).
    - Same command as the baseline (target scenario, 5 iterations):
      ```bash
      BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_DEMO=true \
      BEDROCK_EVAL_FILTER=soap-notification-realistic-data BEDROCK_EVAL_ITERATIONS=5 \
        ./gradlew :software:infra:aws:generation:bedrockEval --rerun-tasks \
        --tests "*BedrockPromptEvalTest*multi-protocol*"
      ```
    - The after pass rate MUST be improved or no worse than the baseline.
    - _Requirements: 4.1, 4.2, 4.4, 4.5_
  - [ ]* 4.2 Obtain explicit user confirmation, then run the SOAP non-regression guardrail
    - **MANUAL / COST-INCURRING — requires EXPLICIT user confirmation before running.** If the user does not confirm, DO NOT run.
    - SOAP subset filter, 3 iterations:
      ```bash
      BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_DEMO=true \
      BEDROCK_EVAL_FILTER=soap- BEDROCK_EVAL_ITERATIONS=3 \
        ./gradlew :software:infra:aws:generation:bedrockEval --rerun-tasks \
        --tests "*BedrockPromptEvalTest*multi-protocol*"
      ```
    - Compare each SOAP scenario's pass status against its baseline; none should drop from pass to fail.
    - _Requirements: 4.1, 4.2, 4.6_
  - [ ] 4.3 Record before/after results and the guardrail outcome in the eval doc
    - Fill the Before/After table (baseline vs after target-scenario pass rate) in `docs/soap-realistic-data-stabilization-eval.md`, including the REQ 4.5 validity-guard note.
    - Record the per-SOAP-scenario non-regression outcome from Task 4.2.
    - _Requirements: 5.2, 5.3_
  - [ ]* 4.4 Last-resort fallback (conditional — ONLY if the after-run still fails)
    - ONLY if, after both the prompt change (Task 3.1) and the iteration-count change (Task 2.1), the target scenario still fails the after measurement: consider loosening the `soap-notification-realistic-data` `semanticCheck` in `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json`.
    - This is a documented fallback of last resort, NOT the default. Record exactly what changed and why in the fallback section of the eval doc.
    - _Requirements: 4.7_
  - [ ] 4.5 Checkpoint - run `./gradlew clean test` and confirm all tests pass
    - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Finalize documentation
  - [ ] 5.1 Update `docs/PROMPT_EVAL.md` for the new default and cross-link
    - Update the `BEDROCK_EVAL_ITERATIONS` row's Default column to reflect the new default (a textual update describing the changed default satisfies this; a specific numeric value need not be restated).
    - Add a cross-link to `./soap-realistic-data-stabilization-eval.md` near the existing worked-example / prompt-injection-hardening report reference.
    - _Requirements: 5.4_
  - [ ] 5.2 Verify the new eval doc is complete
    - Confirm `docs/soap-realistic-data-stabilization-eval.md` contains: context, confirmed re-run results, methodology change, before/after table, SOAP non-regression outcome, and fallback note.
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ] 5.3 Checkpoint - run `./gradlew clean test` and confirm all tests pass
    - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Final verification - coverage and quality
  - [ ] 6.1 Run `./gradlew koverHtmlReport` and verify 90%+ coverage for new code
    - Confirm the new parser branches are covered by `EvalMetricsTest`.
    - _Requirements: 6.1, 6.2, 6.4_
  - [ ] 6.2 Run `./gradlew koverVerify` to enforce the aggregated 90% threshold
    - _Requirements: 6.2_
  - [ ] 6.3 Review test quality
    - Verify Given-When-Then naming, meaningful assertions (not `assertTrue` on equality), edge-case coverage, and that each property test references its design property via the tag format.
    - Confirm changes stay within clean-architecture boundaries: prompt as application-layer resource, parser/tests in the `:software:infra:aws:generation` test source set.
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

## Notes

- Tasks marked with `*` are optional. The `*` Bedrock tasks (1.1, 4.1, 4.2, 4.4) are MANUAL and COST-INCURRING and require explicit user confirmation before running — an automated runner must not execute them.
- The `*` test sub-tasks (2.2, 3.2, 3.3) are offline and required for a complete implementation; they run inside `./gradlew clean test`.
- Ordering matters for validity: baseline (Task 1) before the prompt edit (Task 3); after run (Task 4) after the prompt edit.
- Each top-level task ends with a `./gradlew clean test` checkpoint.
- Property tests validate the design's correctness properties; the Bedrock before/after measurement is manual and non-deterministic and is intentionally excluded from property tests.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["2.2", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3"] },
    { "id": 4, "tasks": ["4.1", "4.2"] },
    { "id": 5, "tasks": ["4.3", "5.1", "5.2"] },
    { "id": 6, "tasks": ["4.4"] },
    { "id": 7, "tasks": ["6.1"] },
    { "id": 8, "tasks": ["6.2", "6.3"] }
  ]
}
```
