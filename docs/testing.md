# Testing the AI Mock Generation Agent

This is developer documentation for understanding, running, debugging and extending the tests
around MockNest Serverless' AI mock generation agent. The agent is a [Koog](https://github.com/JetBrains/koog)
graph that turns an API specification plus a natural-language description into WireMock stub
mappings, backed by Amazon Bedrock (Amazon Nova Pro by default).

It is written for any Kotlin/JVM developer, not just for a demo. Each strategy section does,
however, include a short **Conference demonstration** box with a reproducible sequence you can
run on stage (originally prepared for J-Fall) plus a saved-results fallback.

> **Scope note.** This document describes the repository as it exists today and clearly labels
> anything that is a **proposed** change rather than an existing capability. The
> ["Evaluation reporting"](#evaluation-reporting-and-visualization) and
> ["Missing capabilities"](#prioritized-missing-capabilities) sections contain recommendations
> only — nothing in them is implemented yet.

## Contents

- [Testing strategy overview](#testing-strategy-overview)
- [Local setup and AWS credentials](#local-setup-and-aws-credentials)
- [The six testing strategies](#the-six-testing-strategies)
  1. [Graph topology testing](#1-graph-topology-testing)
  2. [Measuring first-pass generation quality](#2-measuring-first-pass-generation-quality)
  3. [Eval-driven prompt improvement](#3-eval-driven-prompt-improvement)
  4. [Testing the correction workflow](#4-testing-the-correction-workflow)
  5. [Calibrating the LLM judge](#5-calibrating-the-llm-judge)
  6. [Evaluating model alternatives](#6-evaluating-model-alternatives)
- [Evaluation reporting and visualization](#evaluation-reporting-and-visualization)
- [CI usage](#ci-usage)
- [Prioritized missing capabilities](#prioritized-missing-capabilities)

---

## Testing strategy overview

The agent is tested at two very different levels, with a deliberate cost/speed boundary between
them.

```mermaid
flowchart TB
    subgraph offline["Offline — no model inference, free, runs in CI"]
        unit["Unit tests<br/>(parsers, validators, prompt builder)"]
        topo["Graph topology tests<br/>MockGenerationGraphStructureTest"]
        strat["Strategy execution tests<br/>(mock LLM executor)"]
    end

    subgraph online["Online — real Bedrock calls, costs money, on-demand (local or remote)"]
        eval["Bedrock prompt eval suite<br/>BedrockPromptEvalTest"]
        judge["LLM-as-a-judge<br/>semantic verdicts"]
    end

    offline -->|"./gradlew test"| gate["Build / coverage gate (90%)"]
    online -->|"./gradlew :...:generation:bedrockEval<br/>BEDROCK_EVAL_ENABLED=true"| report["Console summary + detail tables"]
```

Key boundary: everything in the **offline** box runs on every `./gradlew test` and in CI, uses a
mock LLM executor (no network, no cost, deterministic). Everything in the **online** box calls
Amazon Bedrock with real specifications, costs a small amount per run, is gated behind the
`bedrock-eval` JUnit tag and the `BEDROCK_EVAL_ENABLED=true` environment variable, and is **not**
wired into any CI workflow.

The online suite is **on-demand**: a developer triggers it explicitly, and it runs the same way
whether that is **local** (your machine) or **remote** (any host with AWS credentials — e.g. a
manually dispatched cloud environment). It is identical in both places because it is just a Gradle
task plus environment variables; "remote" differs only in where the credentials and the JVM live,
not in what runs. No GitHub Actions workflow invokes `bedrockEval` today.

The six strategies documented below map onto this split:

| # | Strategy | Model inference? | Where |
|---|----------|------------------|-------|
| 1 | Graph topology testing | No (mock executor) | `:software:application` |
| 2 | First-pass generation quality | Yes (Bedrock) | `:software:infra:aws:generation` eval |
| 3 | Eval-driven prompt improvement | Yes (Bedrock) | eval + prompt files |
| 4 | Correction workflow | Yes (Bedrock) | eval, retries 0→1 |
| 5 | LLM judge calibration | Yes (Bedrock judge model) | eval datasets |
| 6 | Model alternatives | Yes (Bedrock) | eval, Nova Pro vs Nova Lite |

### The agent under test

`MockGenerationFunctionalAgent` defines a four-node Koog strategy named `"mock-generation"`:

```mermaid
flowchart LR
    start((start)) --> setup
    setup --> generate
    generate -->|"enableValidation = false"| finish((finish))
    generate -->|"enableValidation = true"| validate
    validate -->|"errors.isEmpty() OR attempt > maxRetries"| finish
    validate -->|"errors.isNotEmpty() AND attempt <= maxRetries"| correct
    correct --> validate
```

- **setup** — resolves spec content (inline or fetched by URL) and parses it into an
  `APISpecification`.
- **generate** — builds the generation prompt, calls the LLM, parses the response into
  `GeneratedMock`s.
- **validate** — runs the protocol-specific structural validator; records first-pass stats on
  attempt 1.
- **correct** — on validation failure (or parse failure), builds a correction prompt that carries
  the failing mocks and their errors, calls the LLM again, and loops back to validate.

The `validate → correct` versus `validate → finish` decision is governed by `maxRetries` (default
`1`, i.e. two attempts total). This retry boundary is the exact thing the topology tests pin down.

Relevant file: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationFunctionalAgent.kt`.

---

## Local setup and AWS credentials

### Prerequisites

- JDK 25 (the build targets JVM 25).
- The Gradle wrapper (`./gradlew`) — no separate Gradle install needed.
- For the offline tests (strategy 1): nothing else. No AWS, no network.
- For the eval suite (strategies 2–6): AWS credentials with Amazon Bedrock access, and the model
  you intend to use enabled in the Bedrock console for your region.

### AWS credentials

The eval tests use the AWS SDK for Kotlin, which reads the standard AWS credential chain. Any of
these work:

- **Shared credentials file** — `~/.aws/credentials` with a `[default]` profile and
  `~/.aws/config` setting `region`.
- **AWS SSO** — `aws sso login --profile <profile>`, then pass `AWS_PROFILE=<profile>`.
- **Environment variables** — `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and
  `AWS_SESSION_TOKEN` for temporary/STS credentials.

The default region is `eu-west-1`; override with `AWS_REGION`. The default generation model is
`eu.amazon.nova-pro-v1:0` (resolved from `AmazonNovaPro` with an inference-profile prefix) — it
must be enabled in your Bedrock console.

> The authoritative, always-up-to-date guide to running the eval suite and its environment
> variables is [`docs/PROMPT_EVAL.md`](./PROMPT_EVAL.md). This document references it rather than
> duplicating the full table.

### Cost awareness

The eval suite calls Bedrock for both generation and the LLM judge. A full default run (55 quality
scenarios × 3 iterations) is roughly **$0.66–$1.16**; a single filtered scenario is typically
**$0.01–$0.02**. Always prefer `BEDROCK_EVAL_FILTER` and an explicit `BEDROCK_EVAL_ITERATIONS`
when iterating.

> ⚠️ Before running any eval that calls Bedrock as part of a shared/automated task, get explicit
> confirmation — these calls incur real cost.

---

## The six testing strategies

Each section follows the same shape: **What are we testing**, **How it works**, **Relevant
files**, **How to run it**, **Practical experiment**, **Understanding the results**,
**Reproducibility**, **Troubleshooting**, and (where useful) a compact **Conference
demonstration**.

---

### 1. Graph topology testing

#### What are we testing?

That the agent's strategy graph is wired the way we intend: the expected nodes exist, every node
can reach `finish`, and — most importantly — the `validate → correct → validate` retry loop
honors the `maxRetries` budget exactly.

**Guarantees:** the graph's *shape* and its *routing conditions*. The retry-boundary parameterized
tests prove that for any `maxRetries = N`, `validate` routes to `correct` while there are errors
and `attempt <= N`, and to `finish` as soon as `attempt > N`.

**Limitations:** topology tests say nothing about *output quality*. They use a mock LLM executor
that returns a canned valid response, so they cannot tell you whether a real prompt produces good
mocks, whether validators are correct, or whether Bedrock behaves. They catch accidental rewiring,
off-by-one retry regressions, and unreachable nodes — not generation quality.

#### How it works

The tests use Koog's graph-testing feature (`testGraph`) with `getMockExecutor`, so the strategy
runs without any model inference. The graph assertions are declared inside the `testGraph` block;
the mock collaborators (MockK) and the mock executor exist only so `agent.run(...)` can complete
and trigger verification.

Two kinds of assertions:

- **Node/reachability** — `assertNodeByName(...)`, `assertReachable(a, b)`.
- **Edge routing** — `assertEdges { ... }` with `alwaysGoesTo`, and
  `node withOutput <context> goesTo <target>` to evaluate a specific edge condition against a
  constructed `MockGenerationContext`.

The retry-boundary behavior is checked with a `@ParameterizedTest` + `@CsvSource` table that
varies `(maxRetries, attempt)` and asserts the resulting target (`correct` or `finish`).

#### Relevant files

- Test: `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationGraphStructureTest.kt`
- Agent/graph under test: `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/agent/MockGenerationFunctionalAgent.kt`
- Key types: `MockGenerationContext`, `MockGenerationFunctionalAgent.mockGenerationStrategy`

#### How to run it

No AWS, no network, no cost.

```bash
# Just the topology tests:
./gradlew :software:application:test --tests \
  "nl.vintik.mocknest.application.generation.agent.MockGenerationGraphStructureTest"

# Or the whole application module:
./gradlew :software:application:test
```

#### Practical experiment — retry-boundary regression

This demonstrates both a passing state and a deliberately introduced failure, then restores it.

1. **Establish the baseline (green).**
   ```bash
   ./gradlew :software:application:test --tests \
     "nl.vintik.mocknest.application.generation.agent.MockGenerationGraphStructureTest"
   ```
   All tests pass, including the retry-budget parameterized rows such as
   `maxRetries=1, attempt=2 => finish`.

2. **Introduce a specific condition error.** In `MockGenerationFunctionalAgent.kt`, find the
   `validate → correct` edge:
   ```kotlin
   edge(validateNode forwardTo correctNode onCondition { ctx -> ctx.errors.isNotEmpty() && ctx.attempt <= maxRetries })
   ```
   Change the boundary from `<=` to `<`:
   ```kotlin
   edge(validateNode forwardTo correctNode onCondition { ctx -> ctx.errors.isNotEmpty() && ctx.attempt < maxRetries })
   ```
   This silently drops one correction attempt — exactly the kind of off-by-one the tests exist to
   catch.

3. **Rerun and inspect the failing assertion.**
   ```bash
   ./gradlew :software:application:test --tests \
     "nl.vintik.mocknest.application.generation.agent.MockGenerationGraphStructureTest"
   ```
   The parameterized row `maxRetries=1, attempt=1 => correct` now fails: with `<`, attempt 1 no
   longer routes to `correct` (it goes to `finish`), so the `validate withOutput ... goesTo correct`
   assertion fails. The report names the failing `@CsvSource` case.

4. **Restore the original condition** (change `<` back to `<=`) and rerun. The suite is green
   again.

#### Understanding the results

The meaningful assertions are the edge-routing ones. A failure like "expected edge to `correct`
but routed to `finish`" means the retry budget shrank; "node `correct` not reachable" means the
correction loop was disconnected entirely. Because the tests enumerate both the last correcting
attempt and the first attempt past budget for several budgets (0, 1, 2, 3), an off-by-one in
either direction is caught.

#### Reproducibility

These tests are fully deterministic: the mock executor always returns the same canned response,
and the graph is static. No seeds, models, or network involved. Committing the test file is enough
to reproduce.

#### Troubleshooting

- *"Parser not available" / compilation errors in the strategy DSL*: ensure the Koog version in
  `software/application/build.gradle.kts` matches the DSL used (the agent was reviewed against Koog
  0.8.0). No AWS involvement.
- Flakiness is not expected; if a topology test is flaky, suspect a non-deterministic mock
  collaborator setup rather than the graph.

#### Conference demonstration

- **Show on screen:** the strategy graph diagram + the `MockGenerationGraphStructureTest`
  retry-budget `@CsvSource` table.
- **Sequence:** run green → change `<=` to `<` → run red (point at the named failing case) →
  revert → run green.
- **Observation to explain:** a one-character change to a routing condition is invisible to
  execution-level tests but is pinned exactly by the topology table.
- **Fallback:** keep a saved terminal capture of the red run (the named failing parameterized
  case) in case live editing is risky.

---

### 2. Measuring first-pass generation quality

#### What are we testing?

How good the model's output is *before any correction*. First-pass quality is the honest signal of
prompt quality; final (after-correction) success can mask a weak prompt because the correction
retry patches mistakes at extra cost and latency.

**Guarantees:** per-scenario first-pass structural validity, semantic success (via the judge),
latency, token usage and cost, isolated from the correction loop.

**Limitations:** results are non-deterministic (real LLM); single runs vary. Use multiple
iterations to average out borderline scenarios.

#### How it works

`BedrockPromptEvalTest` loads scenarios from a dataset JSON, and for each scenario:

1. Runs the full generation pipeline against Bedrock (parse → prompt → LLM → parse response).
2. Runs the protocol-specific structural validator.
3. Optionally runs the correction retry (controlled by `BEDROCK_EVAL_MAX_RETRIES`).
4. Runs an LLM-as-a-judge semantic check.

Setting `BEDROCK_EVAL_MAX_RETRIES=0` disables correction entirely, so the reported `1st-pass valid`
and `After retry valid` columns are identical and reflect pure single-shot generation. Token usage
is captured by `TokenUsageCapturingClient`, a test-only decorator over the AWS SDK Bedrock client
that reads the `usage` block from each `converse` response; cost is computed by `CostCalculator`.

The agent records first-pass statistics in its result metadata (`firstPassValid`,
`firstPassMocksGenerated`, `firstPassMocksValid`) on attempt 1, which is what the eval reads.

#### Relevant files

- Eval driver: `software/infra/aws/generation/src/test/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/eval/BedrockPromptEvalTest.kt`
- Token capture: `.../ai/eval/TokenUsageCapturingClient.kt`, `.../ai/eval/TokenUsageStore.kt`, `.../ai/eval/TokenUsageRecord.kt`
- Cost: `.../ai/eval/CostCalculator.kt`
- Quality dataset: `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json`
- First-pass metadata source: `MockGenerationFunctionalAgent.kt` (validate node + `validateNode → nodeFinish` metadata map)

#### How to run it

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_MAX_RETRIES=0 \
BEDROCK_EVAL_FILTER=rest-petstore-basic-get \
BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain
```

- `BEDROCK_EVAL_MAX_RETRIES=0` — clean single-shot numbers.
- `BEDROCK_EVAL_FILTER` — case-insensitive substring on the scenario `input` name; keeps cost low.
- `BEDROCK_EVAL_ITERATIONS=3` — averages out variance.

> Caveat: the `bedrockEval` Gradle task currently forwards only a subset of eval environment
> variables to the forked test JVM (`BEDROCK_EVAL_ENABLED`, `BEDROCK_EVAL_ITERATIONS`,
> `BEDROCK_EVAL_FILTER`, `BEDROCK_EVAL_MAX_RETRIES`, `AWS_REGION`). Variables like
> `BEDROCK_EVAL_SUITE`, `BEDROCK_EVAL_DEMO`, `BEDROCK_JUDGE_MODEL`, `AWS_PROFILE`, and the cost
> overrides are **not** forwarded by the task. See
> [missing capabilities](#prioritized-missing-capabilities) — until that is fixed, export them so
> they are inherited by Gradle, or set `AWS_PROFILE` in your shell profile.

#### Understanding the results

The console summary and detail tables report, per scenario and per protocol:

- **1st-pass valid** — % of generated mocks that passed structural validation with no correction.
- **After retry valid** — same metric after the correction retry (equal to 1st-pass when retries = 0).
- **Scenario pass** — generation succeeded **and** all mocks valid **and** the semantic judge passed.
- **Gen cost / Judge cost / Total** — Bedrock cost, split by phase.
- **Avg latency** — generation-only wall clock (judge excluded).

A low 1st-pass rate with a high after-retry rate (when retries are on) is the classic signal that
the *prompt* is the weak link, not the pipeline.

#### Reproducibility

- Pin the model (`AmazonNovaPro`), region (`AWS_REGION`), retries (`BEDROCK_EVAL_MAX_RETRIES=0`),
  and iteration count explicitly.
- The dataset JSON and the prompt files under
  `software/application/src/main/resources/prompts/` are the inputs — commit them.
- Save the console output (the summary + detail tables) alongside your change. Existing worked
  examples live in [`docs/prompt-injection-hardening-eval.md`](./prompt-injection-hardening-eval.md)
  and [`docs/soap-realistic-data-stabilization-eval.md`](./soap-realistic-data-stabilization-eval.md).

#### Troubleshooting

- *Test is skipped:* `BEDROCK_EVAL_ENABLED` is not `true`, or the suite assumption skipped it
  (`BEDROCK_EVAL_SUITE`).
- *`AccessDeniedException` / model not enabled:* enable the model in the Bedrock console for your
  region, or switch region.
- *Credentials errors:* confirm the SDK credential chain resolves (profile, SSO session, or env
  vars). `AWS_PROFILE` may need to be exported in your shell since the task does not forward it.
- *Cleanup:* the eval writes nothing to S3 and creates no AWS resources; there is nothing to tear
  down beyond the local Gradle reports under `build/reports/tests/bedrockEval`.

#### Conference demonstration

- **Show on screen:** the per-scenario detail table with `BEDROCK_EVAL_MAX_RETRIES=0` and
  `BEDROCK_EVAL_DEMO=true`.
- **Sequence:** run one filtered REST scenario, point at `1st-pass == after-retry`, read off
  latency/tokens/cost.
- **Observation:** first-pass is the real prompt-quality signal.
- **Fallback:** a saved table from a prior run.

---

### 3. Eval-driven prompt improvement

#### What are we testing?

Whether a change to a single prompt node (the REST generation prompt) measurably improves
generation quality, without regressing other scenarios. This is *focused* evaluation of one prompt
file, in contrast to full-flow evaluation across all 55 scenarios and 3 protocols.

#### How it works

The eval harness does not have a separate "node-only" mode — it always runs the real production
pipeline end to end. "Focused" evaluation is achieved by:

- **Filtering scenarios** to the protocol/scenario that exercises the prompt you changed
  (`BEDROCK_EVAL_FILTER`), so the measurement is concentrated on that prompt while still running it
  inside the realistic production context (real parser, real validator, real schema reduction).
- Running a **baseline → change → re-run** comparison.

Can generation prompts be evaluated independently while preserving realistic production context?
**Partially.** You can isolate *which prompt runs* by filtering to scenarios that only touch that
prompt, and the prompt is still exercised through the genuine pipeline (not a stub). What the
harness does **not** support today is evaluating a prompt node against a fixed, captured upstream
context in isolation (e.g. replaying a frozen parsed spec) — every run re-parses and re-reduces the
spec live. For REST this is fine because the REST generation prompt
(`prompts/rest/spec-with-description.txt`) is the dominant variable for REST scenarios.

#### A real REST scenario to demonstrate with

`rest-petstore-consistency` (in `multi-protocol-eval-dataset.json`) is a good candidate: it asks
for cross-referenced data (`GET /pet/1`, `GET /store/order/1` whose `petId` matches the pet's `id`,
and `GET /user/john`). Its semantic check scores 0 if `order.petId` does not match `pet.id`, which
is sensitive to prompt wording around data consistency — exactly what the REST prompt's
consistency bullets target.

#### Relevant files

- REST generation prompt: `software/application/src/main/resources/prompts/rest/spec-with-description.txt`
- REST correction prompt: `software/application/src/main/resources/prompts/rest/correction.txt`
- Scenario + semantic check: `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json` (`input: rest-petstore-consistency`)
- Eval driver: `.../ai/eval/BedrockPromptEvalTest.kt`

#### How to run it

```bash
# 1. Baseline (no retries, so you measure the prompt, not the corrector):
BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=0 \
BEDROCK_EVAL_FILTER=rest-petstore-consistency BEDROCK_EVAL_ITERATIONS=5 \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain
```

#### Practical experiment — baseline, change, re-run, regression check

1. **Capture the baseline.** Run the command above and record the 1st-pass valid rate and scenario
   pass rate (5 iterations).
2. **Make a targeted prompt change.** In `prompts/rest/spec-with-description.txt`, strengthen the
   consistency guidance — for example make the cross-entity rule more explicit for path-parameter
   IDs. Keep the SECURITY block and output-format rules unchanged.
3. **Re-run the same filtered command.** Compare 1st-pass valid and scenario pass against the
   baseline for `rest-petstore-consistency`.
4. **Check for regressions.** Run a broader REST pass to confirm you did not hurt other scenarios:
   ```bash
   BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=0 \
   BEDROCK_EVAL_FILTER=rest BEDROCK_EVAL_ITERATIONS=3 \
     ./gradlew :software:infra:aws:generation:bedrockEval --console=plain
   ```
   Confirm the other REST scenarios hold their pass rates.

#### Understanding the results

A genuine improvement shows up as a higher 1st-pass valid / scenario pass rate for the targeted
scenario across iterations, with no drop elsewhere. Because LLM output varies run to run, treat a
one-iteration swing as noise; look at the averaged rate over several iterations. The existing eval
reports show that some borderline scenarios sit near the judge threshold and flip between runs —
do not over-interpret a single flip.

#### Reproducibility

- Keep the prompt file under version control; the diff *is* the experiment.
- Pin model, region, retries=0, and iteration count.
- Record both the targeted-scenario numbers and the broader REST non-regression numbers, following
  the format of the existing before/after reports in `docs/`.

#### Troubleshooting

- *Filter matched 0 scenarios:* the harness fails fast on a typo'd `BEDROCK_EVAL_FILTER`; the error
  lists available inputs.
- *Noisy results:* increase `BEDROCK_EVAL_ITERATIONS`.
- *Changed the wrong prompt:* REST uses `prompts/rest/`; GraphQL and SOAP have their own prompt
  directories. A REST scenario will not reflect a SOAP prompt change.

#### Conference demonstration

- **Show on screen:** the REST prompt diff and the before/after `rest-petstore-consistency` rows.
- **Sequence:** baseline → edit prompt → re-run filtered → show the delta → quick broad-REST
  non-regression.
- **Observation:** prompt engineering is measurable, and you check for collateral regressions.
- **Fallback:** saved before/after tables.

---

### 4. Testing the correction workflow

#### What are we testing?

The self-correction retry loop: when first-pass validation fails, the agent sends a correction
prompt carrying the failing mocks and their errors, and re-validates. We test the difference
between **first-pass structural validity** and **final workflow success**, and the overhead
(extra LLM call, latency, tokens, cost) the correction adds.

#### How it works

The same `BedrockPromptEvalTest`, run twice: once with `BEDROCK_EVAL_MAX_RETRIES=0` (no correction)
and once with `=1` (one correction attempt, the production default). The reported
`After retry valid` and `attempts` metadata reveal how much the corrector recovered.

In the agent, the `correct` node (`MockGenerationFunctionalAgent.kt`) builds the correction prompt
via `promptBuilder.buildCorrectionPrompt(invalidMocks = ..., specification = ..., format = ...)`.
The correction prompt (`prompts/rest/correction.txt`) receives generation context explicitly: the
**namespace/API name**, a **spec context**, and the **failing mocks paired with their validation
errors** (`{{MOCKS_WITH_ERRORS}}`). It instructs the model to fix every listed error and return
only a JSON array. After correction, the node keeps the already-valid mocks and appends the
corrected ones, increments `attempt`, and loops back to `validate`.

#### Relevant files

- Agent correction node + correction-prompt wiring: `MockGenerationFunctionalAgent.kt`
- Correction prompt: `software/application/src/main/resources/prompts/rest/correction.txt`
- Eval driver (phase-split token/cost capture): `.../ai/eval/BedrockPromptEvalTest.kt`

#### How to run it

```bash
# No correction:
BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=0 \
BEDROCK_EVAL_FILTER=rest-petstore-consistency BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain

# One correction retry (production default):
BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=1 \
BEDROCK_EVAL_FILTER=rest-petstore-consistency BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain
```

#### Practical experiment — before/after correction

1. Run the retries=0 command; record `1st-pass valid` (= `after-retry` here), latency, tokens, cost.
2. Run the retries=1 command on the same scenario and iterations.
3. Compare: `After retry valid` should be ≥ `1st-pass valid`; `attempts` becomes 2 on scenarios
   that needed correction; generation latency and token/cost rise because of the extra LLM call.
4. Confirm, by reading the logs, that the correction prompt received the failing mocks and their
   errors (the eval logs first-pass validation errors per scenario).

#### Understanding the results

- **First-pass structural validity** answers "did the model get it right the first time?"
- **Final workflow success** (`Scenario pass`) answers "did the agent, including correction,
  ultimately produce valid + semantically correct mocks?"
- The gap between them is the value the corrector adds — and the extra Gen cost / latency is its
  price. A prompt that needs frequent correction is more expensive and slower in production even if
  its final success looks good.

#### Reproducibility

Pin model/region/iterations; vary only `BEDROCK_EVAL_MAX_RETRIES` between the two runs. Save both
tables. The correction prompt file is part of the input set.

#### Troubleshooting

- *No change between 0 and 1 retries:* the scenario already passed first-pass (nothing to correct)
  — pick a harder scenario (e.g. a consistency or error scenario).
- *`attempts` never exceeds 1:* same reason, or validation is disabled.
- Range for `BEDROCK_EVAL_MAX_RETRIES` is 0–2 (values are coerced into that range).

#### Conference demonstration

- **Show on screen:** two detail tables side by side (retries 0 vs 1) for one scenario.
- **Sequence:** run retries=0 → run retries=1 → contrast `1st-pass` vs `after-retry`, and the extra
  cost/latency.
- **Observation:** correction trades money and latency for reliability.
- **Fallback:** saved paired tables.

---

### 5. Calibrating the LLM judge

#### What are we testing?

Whether the LLM-as-a-judge agrees with a human on whether a generated mock satisfies a scenario.
An LLM judge is itself a model and can be wrong — too lenient or too strict — so it must be checked
against human-labelled examples before you trust its verdicts as a quality gate.

#### How it works

For each scenario, `runSemanticJudge` builds a Dokimos `LLMJudgeEvaluator` with:

- the scenario's `semanticCheck` text as the **criteria**,
- `INPUT` (the scenario `description`) and `ACTUAL_OUTPUT` (the generated mappings JSON) as
  evaluation params,
- a **threshold of 0.7**,
- a **judge model independent of the generator** — default `OpenAIGptOss120B` (a first-party
  Bedrock foundation model), overridable via `BEDROCK_JUDGE_MODEL`. Using a different model than
  the Nova Pro generator reduces same-model self-judging bias.

The judge returns a score in `[0.0, 1.0]`; `passed = score >= threshold`. The eval logs
`score`, `threshold`, `passed`, and crucially the judge's **`reason`** (its explanation). That
reason is logged today but is **not** surfaced in the summary/detail tables (see
[reporting](#evaluation-reporting-and-visualization)).

```mermaid
flowchart LR
    desc["Scenario description<br/>(INPUT)"] --> judge
    mock["Generated mappings<br/>(ACTUAL_OUTPUT)"] --> judge
    crit["semanticCheck<br/>(criteria)"] --> judge
    judge["LLMJudgeEvaluator<br/>threshold 0.7<br/>independent judge model"] --> verdict["score + passed + reason"]
```

#### Inspecting existing REST cases for calibration candidates

Good calibration examples are scenarios whose `semanticCheck` encodes an *objective* property, so a
human verdict is defensible:

- `rest-petstore-consistency` — human can check by eye whether `order.petId == pet.id` and
  `user.username == "john"`.
- `rest-petstore-error` — human can check whether both a 404 and a 500 mock exist with non-empty
  bodies.
- `rest-social-consistency` — human can check whether `post.authorId` references user 1 and each
  comment's `postId == 1`.

These are objectively checkable, which is what makes them usable as human-labelled anchors.

If you want a *subtle* negative example and none of the above fits, propose a **new REST fixture**
with one small, objectively defensible semantic error — for example a petstore consistency mock
where `order.petId = 2` while `pet.id = 1` (everything else correct). A human labels this **FAIL**
(the consistency criterion is violated); a well-calibrated judge should also score it below 0.7.
Add it as a new dataset entry rather than editing an existing one.

#### Relevant files

- Judge wiring: `.../ai/eval/BedrockPromptEvalTest.kt` (`judgeLM`, `asJudge { ... }`, `runSemanticJudge`)
- Scenario criteria: `software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json`
- Judge model resolution: `resolveJudgeModel(...)` in the eval test (reflection against `BedrockModels`)

#### How to run it

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_FILTER=rest-petstore-consistency BEDROCK_EVAL_ITERATIONS=3 \
BEDROCK_JUDGE_MODEL=OpenAIGptOss120B \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain
```

> Remember the Gradle task does not forward `BEDROCK_JUDGE_MODEL` itself; export it in your shell
> so Gradle inherits it, or run until the forwarding gap is fixed.

#### Practical experiment — calibration

1. **Present a request and generated mock.** Pick a scenario (e.g. `rest-petstore-consistency`),
   and keep its `description` (request) and a generated mapping array (actual output) in front of
   you.
2. **Decide the human verdict first.** Read the `semanticCheck` and label PASS/FAIL yourself,
   writing down *why* (e.g. "FAIL: order.petId=2 but pet.id=1").
3. **Run the judge** with the command above and read the logged `score`, `passed`, and `reason`.
4. **Compare** the human label to the judge verdict.
5. **If they genuinely disagree, improve the rubric** — tighten or clarify the scenario's
   `semanticCheck` wording so the objective property is unambiguous. Do **not** change the expected
   human label to match the model, and do not manufacture a judge failure.
6. **Re-run multiple calibration examples** (several scenarios, several iterations) to confirm the
   rubric change did not introduce regressions elsewhere.

#### Understanding the results

A calibrated judge agrees with the human label on clear-cut cases consistently across iterations.
Disagreement on an *objective* case points to a vague `semanticCheck` (fixable) or a genuinely
unreliable judge model (consider a different `BEDROCK_JUDGE_MODEL`). Borderline scenarios that flip
around the 0.7 threshold between iterations are a known pattern — document them rather than tuning
the label to the model.

#### Reproducibility

Pin the judge model, generator model, region, and iterations. Keep the human labels in a small
table in your eval report. The `semanticCheck` text is the rubric — version it with the dataset.

#### Troubleshooting

- *Judge evaluation failed (logged warning, scored 0):* usually a transient Bedrock/judge-model
  error or the judge model not enabled in your region. Re-run; enable the model.
- *Judge seems too lenient/strict everywhere:* try a different independent `BEDROCK_JUDGE_MODEL`.
- *Reason not visible:* it is logged at INFO on the `nl.vintik.mocknest` logger; run without
  `BEDROCK_EVAL_DEMO` (demo mode raises log level to WARN), or watch the Gradle test output.

#### Conference demonstration

- **Show on screen:** the scenario description, a generated mapping, your written human verdict,
  then the judge's logged score + reason.
- **Sequence:** state the human verdict → run → reveal the judge verdict + reason → agree/disagree.
- **Observation:** the judge is a model too; we calibrate it, we don't blindly trust it.
- **Fallback:** a saved log snippet with the judge's `reason`.

---

### 6. Evaluating model alternatives

#### What are we testing?

Whether a cheaper model (Amazon Nova Lite) meets the application's quality bar compared with the
default (Amazon Nova Pro), holding everything else constant. This is a controlled A/B over the same
prompts, scenarios, retry settings, judge model, and evaluation criteria.

#### How it works

The generator model is set in `BedrockPromptEvalTest` via `ModelConfiguration(modelName = "AmazonNovaPro", ...)`.
To compare models you run the same filtered eval twice, changing only the generator model, and
compare the summary tables. The judge model (`BEDROCK_JUDGE_MODEL`) and the dataset must stay the
same so the comparison is fair.

> Current limitation: the generator `modelName` is **hard-coded** in the eval test (there is no
> `BEDROCK_EVAL_MODEL` environment variable yet). To switch to Nova Lite today you edit the
> `modelConfiguration` line (`"AmazonNovaPro"` → `"AmazonNovaLite"`) and re-run, then revert. Making
> this an environment variable is listed under [missing capabilities](#prioritized-missing-capabilities).
> Do not commit the temporary edit.

#### Relevant files

- Generator model setting: `.../ai/eval/BedrockPromptEvalTest.kt` (`modelConfiguration`)
- Model name → id resolution: `ModelConfiguration` + `DefaultInferencePrefixResolver` in
  `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/config/`
- Cost constants: `.../ai/eval/CostCalculator.kt` (override via `BEDROCK_EVAL_INPUT_PRICE_PER_TOKEN`
  / `BEDROCK_EVAL_OUTPUT_PRICE_PER_TOKEN` for a non–Nova-Pro model's pricing)

#### How to run it

```bash
# Run A — Nova Pro (default, as-is):
BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=1 \
BEDROCK_EVAL_FILTER=rest BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval --console=plain

# Edit modelConfiguration to "AmazonNovaLite", then Run B with identical flags.
# (Optionally set BEDROCK_EVAL_INPUT/OUTPUT_PRICE_PER_TOKEN to Nova Lite pricing for accurate cost.)
```

#### Practical experiment — Nova Pro vs Nova Lite

1. **Run A (Nova Pro)** on a fixed filtered set and iteration count; save the summary table.
2. **Switch to Nova Lite** (edit `modelConfiguration`), keep *everything else identical* (same
   filter, iterations, retries, judge model, dataset).
3. **Run B (Nova Lite)**; save its summary table.
4. **Compare** across: task completion (`Scenario pass`), structural correctness (`1st-pass valid`,
   `After retry valid`), semantic quality (judge pass rate), correction overhead (`attempts`,
   after-retry delta), latency, tokens, and cost.
5. **Revert** the model edit.

#### Understanding the results

A cheaper model "meets the bar" if its `Scenario pass` and `1st-pass valid` rates are within your
accepted tolerance of Nova Pro's while its cost (and possibly latency) is lower. Watch for a
cheaper model that leans harder on correction: if Nova Lite's first-pass validity is much lower but
after-retry catches up, its real cost/latency is higher than the headline per-token price suggests.
Decide against the application's quality requirement, not raw price.

#### Reproducibility

Change exactly one variable (the generator model) between runs. Pin everything else. Record both
summary tables and the exact flags. If you override pricing constants for Nova Lite, note them.

#### Troubleshooting

- *Nova Lite not available:* enable it in the Bedrock console / choose a region that offers it.
- *Cost looks identical:* you may still be on the Nova Pro pricing constants — set the price
  overrides, or treat token counts (not dollars) as the comparison for non-default models.
- *Forgot to revert:* the model edit is a source change — do not commit it.

#### Conference demonstration

- **Show on screen:** two summary tables (Nova Pro vs Nova Lite) for the same filtered REST set.
- **Sequence:** run A → edit model → run B → compare pass rate vs cost/latency.
- **Observation:** "cheaper" is only cheaper if quality holds and correction overhead doesn't eat
  the savings.
- **Fallback:** saved A/B tables.

---

## Evaluation reporting and visualization

### Current state (existing capability)

Reporting today is **console-only**, printed by `BedrockPromptEvalTest` at the end of a run:

- A **summary table** (per-protocol: runs, 1st-pass valid, after-retry valid, scenario pass, gen
  cost, judge cost, avg cost/run, avg latency, plus a TOTAL footer and a TOTAL EVAL COST line).
- A **per-scenario detail table** grouped by API spec (1st-pass, after-retry, pass, gen/judge/total
  cost, latency, failure reason).
- `BEDROCK_EVAL_DEMO=true` cleans this up for screen recording (tables to stdout, chatty logs
  silenced).
- Gradle also emits standard **JUnit XML** (`build/test-results/bedrockEval`) and an **HTML test
  report** (`build/reports/tests/bedrockEval`) — but these are pass/fail test reports, not the rich
  metrics tables.

What exists but is **not wired into the main eval flow**: `EvalReportBuilder`, `IterationResult`,
and `EvalMetrics` (`calculateSuccessRate` etc.) are present in the eval source set but are **not**
referenced by `BedrockPromptEvalTest` — the test builds its own inline tables instead. Treat them
as unused/legacy helpers, not the active reporting path.

**Judge explanations:** the judge's `reason` **is** already produced and **is** logged per scenario
in `runSemanticJudge`. It is simply not included in the summary/detail tables. So the capability to
show "LLM judge verdicts with concise, evidence-based explanations" exists at the data level today;
only the *presentation* is missing. An optional reporting setting to include the reason column is a
small, additive change rather than new data capture.

### Is the current reporting useful?

- **For debugging:** reasonably — the detail table plus the logged judge `reason` and first-pass
  validation errors give enough to diagnose a failing scenario.
- **For presenting / comparing runs:** limited — the tables are ephemeral console output, there is
  no machine-readable metrics JSON beyond JUnit XML, and comparing two runs means eyeballing two
  terminal captures. Judge reasons are not in the table a reviewer reads.

### Recommendations (proposed — not implemented)

Prefer **extending the existing reporting** and/or producing a **standalone HTML report**; do not
build a frontend. Concrete, prioritized:

1. **Emit a structured metrics JSON per run** (high priority). Serialize the already-computed
   per-scenario `ScenarioResult` list (plus model/region/retries/iterations header and judge
   reasons) to `build/reports/bedrockEval/eval-<timestamp>.json`. This unblocks run-to-run
   comparison and CI/automated analysis without touching the console tables.
   - Location: new writer invoked at the end of `runEvalSuite` in `BedrockPromptEvalTest.kt`.
2. **Add an optional judge-reason column/section** (high priority, small). Gate behind a setting
   (e.g. `BEDROCK_EVAL_SHOW_JUDGE_REASON=true`) so the already-logged `reason` appears in the detail
   table for failing scenarios. The data already exists.
3. **Generate a standalone HTML report from the JSON** (medium). A single self-contained HTML file
   (aggregate metrics, per-scenario pass/fail, first-pass vs after-correction, structural vs
   semantic, model config + latency/tokens/cost, judge verdicts with optional reasons). Keep it
   generated from the JSON so JSON stays the source of truth.
4. **Run-comparison view** (medium). Given two metrics JSON files, render a diff (per-scenario and
   per-protocol deltas) — this is what the manual before/after reports in `docs/` do by hand today.
5. **Fix the env-var forwarding gap** (high, enables the above to be driven from CI/scripts) — see
   next section.

Keep JSON output available for CI and automated analysis regardless of whether the HTML report is
built.

---

## CI usage

- **Offline tests (incl. graph topology)** run as part of the normal build. The repository enforces
  **90% aggregated coverage** via Kover (`koverVerify` with `minBound(90)` in the root
  `build.gradle.kts`), and `./gradlew test` runs every module's JUnit 5 suite. The graph topology
  tests live in `:software:application`, which uses a plain `useJUnitPlatform()` and therefore
  always runs them.
- **The Bedrock eval suite is excluded from CI.** It is tagged `bedrock-eval` and excluded from the
  default `test` task in `software/infra/aws/generation/build.gradle.kts`
  (`useJUnitPlatform { excludeTags("bedrock-eval") }`), and the dedicated `bedrockEval` task is
  gated by `BEDROCK_EVAL_ENABLED`. No workflow under `.github/workflows/` runs `bedrockEval` — it is
  a manual, cost-confirmed, **on-demand** activity that a developer runs either locally or on a
  remote host with AWS credentials. It is not part of any automated CI workflow. This is
  intentional: CI must stay free and deterministic.
- **Typical commands:**
  ```bash
  ./gradlew build                 # compile + offline tests
  ./gradlew koverVerify           # enforce 90% aggregated coverage
  ./gradlew :software:application:test  # fast loop for topology/strategy tests
  ```

---

## Prioritized missing capabilities

Proposed, not implemented. Ordered by impact.

1. **Forward all eval env vars in the `bedrockEval` Gradle task (high).** The task only forwards
   `BEDROCK_EVAL_ENABLED`, `BEDROCK_EVAL_ITERATIONS`, `BEDROCK_EVAL_FILTER`,
   `BEDROCK_EVAL_MAX_RETRIES`, `AWS_REGION`. It does **not** forward `BEDROCK_EVAL_SUITE`,
   `BEDROCK_EVAL_DEMO`, `BEDROCK_JUDGE_MODEL`, `AWS_PROFILE`, or the cost overrides, so those only
   work if Gradle already inherits them from the shell. Fixing the forwarding list removes a
   confusing footgun. File: `software/infra/aws/generation/build.gradle.kts`.
2. **Make the generator model configurable via env var (high).** The eval's `modelConfiguration`
   hard-codes `AmazonNovaPro`; model-comparison (strategy 6) currently requires a temporary source
   edit. Add `BEDROCK_EVAL_MODEL`. File: `BedrockPromptEvalTest.kt`.
3. **Structured metrics JSON per run (high).** See reporting recommendation #1 — enables run
   comparison and automated analysis.
4. **Optional judge-reason in reports (high, small).** The reason is already computed/logged; add a
   gated column/section. See reporting recommendation #2.
5. **Standalone HTML report + run-comparison from JSON (medium).** See reporting recommendations
   #3–#4.
6. **Replace or wire up the unused report helpers (low).** `EvalReportBuilder`, `IterationResult`,
   `EvalMetrics` are dead relative to the active eval path; either integrate them into the JSON/HTML
   work above or remove them to avoid confusion.
7. **Isolated prompt-node replay (low/medium).** A mode that evaluates a generation prompt against
   a captured, frozen upstream context (parsed spec) would make strategy 3 fully reproducible and
   cheaper, independent of live parsing/reduction.

---

## Appendix — quick command reference

| Goal | Command |
|------|---------|
| Topology tests (free, offline) | `./gradlew :software:application:test --tests "*MockGenerationGraphStructureTest"` |
| Full offline build + coverage | `./gradlew build koverVerify` |
| First-pass quality (single scenario) | `BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_MAX_RETRIES=0 BEDROCK_EVAL_FILTER=rest-petstore-basic-get ./gradlew :software:infra:aws:generation:bedrockEval --console=plain` |
| Correction before/after | run the above with `BEDROCK_EVAL_MAX_RETRIES=0` then `=1` |
| Judge calibration | `BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_FILTER=rest-petstore-consistency BEDROCK_JUDGE_MODEL=OpenAIGptOss120B ./gradlew :software:infra:aws:generation:bedrockEval --console=plain` |
| Demo-friendly output | add `BEDROCK_EVAL_DEMO=true` |

See [`docs/PROMPT_EVAL.md`](./PROMPT_EVAL.md) for the complete environment-variable reference and
the full golden-set scenario catalog.
