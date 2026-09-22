# SOAP Realistic-Data Stabilization — Evaluation Report

This document records the measured results for the SOAP realistic-data stabilization work. It
is the authoritative results location referenced by the
[soap-realistic-data-stabilization spec](../.kiro/specs/soap-realistic-data-stabilization/).

For how to run the eval suite itself, see the general guide: [PROMPT_EVAL.md](./PROMPT_EVAL.md).

## Context

This stabilization work follows the [Prompt Injection Hardening evaluation](./prompt-injection-hardening-eval.md),
which surfaced `soap-notification-realistic-data` as a flaky scenario: it passed the structural
validators cleanly but flipped below the LLM-as-a-judge 0.7 semantic threshold from run to run.
The goal of this effort was to reduce that flakiness by improving the realism of generated SOAP
response bodies and by adjusting the eval methodology so borderline judge verdicts are averaged
rather than decided by a single coin-flip. The full spec, requirements, design, and tasks live
under [.kiro/specs/soap-realistic-data-stabilization/](../.kiro/specs/soap-realistic-data-stabilization/).

Every run in this report used **AmazonNovaPro** in region **eu-west-1** with **max retries 1**
throughout.

## Confirmed prior re-run results (context, from the injection-hardening analysis)

Carried over from the injection-hardening analysis as the starting point for this work:

- **soap-notification-realistic-data** — 3/5 pass at **100% structural validity every run**.
  The scenario is flaky specifically at the LLM-judge 0.7 threshold: the generated output is
  fully valid WireMock, but the composite semantic verdict sits near the threshold and flips
  between runs.
- **rest-payment-basic** — 5/5 pass. Confirmed **NOT a regression**; the earlier single-run
  failure did not reproduce.

## Methodology change

- The eval harness default iteration count when `BEDROCK_EVAL_ITERATIONS` is unset is now **3**
  (was 1), via `DEFAULT_EVAL_ITERATIONS` in `EvalMetrics.kt`. Rationale: average out borderline
  judge verdicts instead of relying on single-shot coin-flips.
- **Cost note:** an unset full-suite run now performs roughly **3x** the Bedrock calls
  (~$0.66–$1.16 vs ~$0.22–$0.39 per full run). For cheaper targeted runs, combine
  `BEDROCK_EVAL_FILTER` with an explicit `BEDROCK_EVAL_ITERATIONS` value.

## SOAP prompt change

A "Realistic data values (response body content)" bullet sub-group was added at the end of the
"SOAP 1.2 response format rules" section of
`software/application/src/main/resources/prompts/soap/spec-with-description.txt`. The guidance
directs the model to:

- Populate response body elements with realistic, non-placeholder values.
- Use realistic status and enumeration strings (e.g., `delivered`, `pending`, `active`), choosing
  a valid member when the WSDL constrains a field to an enumeration.
- Use ISO-8601 timestamps.
- Use realistic identifiers.
- Continue honoring the description and the WSDL schema.

This is **data-value guidance only** — existing SOAP response format rules and the SECURITY block
are unchanged.

## Before/after measurement — target scenario `soap-notification-realistic-data`

Five iterations each, this session:

| Measurement | Prompt | Pass rate | Structural validity |
|-------------|--------|-----------|---------------------|
| Baseline (before) | unchanged | 4/5 (80%) | 100% all runs |
| After | realistic-data guidance added | 4/5 (80%) | 100% all runs |

**REQ 4.5 validity-guard note:** the baseline was captured **before** the prompt edit and the
after-run **after** it, so the before/after comparison is valid and like-for-like.

**SOAP-subset guardrail observation:** in the 3-iteration guardrail run (below), this same
scenario scored **2/3**.

## Honest interpretation

- Combining the measured runs: **before ~7/10** (baseline 4/5 + the earlier 3/5) and
  **after ~6/8** (after 4/5 + guardrail 2/3). This is within noise — the realistic-data guidance
  did **not** produce a clear, measurable improvement in this scenario's pass rate. The scenario
  remains borderline at the judge threshold.
- What **did** hold cleanly: no regression across the other 14 SOAP scenarios (all 3/3 pass at
  100% structural validity in the guardrail run), and the iteration-count methodology change is
  now in place so future runs average out borderline verdicts.
- The realistic-data guidance is still worthwhile as **generation-quality guidance** — it reduces
  obvious placeholder values in SOAP bodies — even though it did not move this particular flaky
  judge verdict decisively.

## SOAP non-regression guardrail

3 iterations × 15 SOAP scenarios = 45 runs, this session.

Every SOAP scenario passed all 3 iterations **except** `soap-notification-realistic-data`, which
scored **2/3**. All 14 other SOAP scenarios passed **3/3 at 100% structural validity**. There is
**no regression** from the prompt change.

Scenario groups covered:

- **calculator** (2)
- **banking-service** (6)
- **inventory-warehouse** (4)
- **notification-messaging** (3, including the target `soap-notification-realistic-data`)

## Last-resort fallback (REQ 4.7)

The last-resort fallback was **NOT applied**: the `semanticCheck` for
`soap-notification-realistic-data` was left unchanged. Since structural validity is 100% and the
scenario is only borderline at the judge threshold, the recommended path is the now-default
3-iteration averaging rather than loosening the check. Loosening the `semanticCheck` remains
available as a documented fallback if the flake later proves disruptive.
