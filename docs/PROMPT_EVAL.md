# Bedrock Prompt Eval

## Overview

The Bedrock Prompt Eval is a manual, local-only integration test that evaluates the quality of MockNest's initial REST `spec-with-description` generation prompt against real AWS Bedrock (Amazon Nova Pro). It runs the generation prompt N times against the Petstore OpenAPI specification, validates semantic correctness of generated mocks using the [Dokimos](https://github.com/dokimos-dev/dokimos) LLM evaluation framework, and produces a structured report with success rates, semantic scores, token usage, and estimated cost.

The eval exercises only the initial generation prompt in generation-only mode (`enableValidation = false`) — the correction/retry prompt path is explicitly out of scope and will be evaluated separately.

This test is **never** included in CI pipelines or normal `./gradlew test` runs. It requires real AWS credentials and makes real Bedrock API calls.

## Prerequisites

- **AWS credentials** with Bedrock access, resolved via the default credential chain (environment variables, AWS profile, or instance metadata)
- **Model access** to Amazon Nova Pro enabled in your AWS account for the target region
- **`AWS_REGION`** environment variable (defaults to `eu-west-1` if not set)
- **Java 25** and **Gradle** (see [BUILDING.md](BUILDING.md) for setup)

## Quick Start

Run a single-iteration eval:

```bash
BEDROCK_EVAL_ENABLED=true ./gradlew :software:infra:aws:generation:bedrockEval
```

Run a multi-iteration eval for statistical assessment:

```bash
BEDROCK_EVAL_ENABLED=true BEDROCK_EVAL_ITERATIONS=10 ./gradlew :software:infra:aws:generation:bedrockEval
```

## Configuration

| Environment Variable | Description | Default |
|---|---|---|
| `BEDROCK_EVAL_ENABLED` | Gate flag. Must be set to `true` for the eval test to run. Any other value or absence causes the test to be skipped. | Not set (test skipped) |
| `BEDROCK_EVAL_ITERATIONS` | Number of times the generation prompt is executed per eval run. Must be a positive integer. | `1` |
| `AWS_REGION` | AWS region for the Bedrock client. | `eu-west-1` |

## Reading Results

After the eval completes, a structured report is printed to the test log. The report contains:

- **Success Rate** — The percentage of iterations that produced a successful `GenerationResult` (e.g., `8/10 = 80.0%`)
- **Semantic Score** — The percentage of successful iterations that passed all semantic correctness checks (pet count, endpoint coverage, schema consistency, status distinctness, and LLM-as-a-judge faithfulness)
- **Total Mocks** — The total number of generated WireMock mappings across all iterations
- **Token Usage** — Input, output, and total token counts aggregated across all iterations
- **Estimated Cost** — Total estimated cost in USD based on Nova Pro pricing
- **Per-Iteration Breakdown** — Each iteration's success/failure status, mock count, semantic score, token usage, and cost

## Example Output

```
╔══════════════════════════════════════════════════════════════╗
║                  BEDROCK PROMPT EVAL REPORT                  ║
║                    (Generation-Only Mode)                    ║
╠══════════════════════════════════════════════════════════════╣
║ Model:            Amazon Nova Pro                            ║
║ Region:           eu-west-1                                  ║
║ Iterations:       10                                         ║
║ Duration:         45230 ms                                   ║
╠══════════════════════════════════════════════════════════════╣
║ Success Rate:     8/10 = 80.0%                               ║
║ Semantic Score:   7/10 = 70.0%                               ║
║ Total Mocks:      32                                         ║
╠══════════════════════════════════════════════════════════════╣
║ Token Usage:                                                 ║
║   Input Tokens:   12500                                      ║
║   Output Tokens:  8400                                       ║
║   Total Tokens:   20900                                      ║
║ Estimated Cost:   $0.0369                                    ║
╠══════════════════════════════════════════════════════════════╣
║ Per-Iteration Breakdown:                                     ║
║  #1  ✓  mocks=4  semantic=✓  in=1250 out=840  $0.0037       ║
║  #2  ✓  mocks=4  semantic=✓  in=1250 out=840  $0.0037       ║
║  #3  ✗  error: Model response parsing failed                 ║
║  ...                                                         ║
╚══════════════════════════════════════════════════════════════╝
```

## Cost Estimation

The eval uses Amazon Nova Pro on-demand pricing:

| Token Type | Price |
|---|---|
| Input tokens | $0.0008 per 1K tokens |
| Output tokens | $0.0032 per 1K tokens |

A typical single iteration consumes roughly 1,200–1,500 input tokens and 800–1,000 output tokens, resulting in an estimated cost of **~$0.003–$0.004 per iteration**.

Start with 1 iteration to verify setup and assess cost before scaling up. A 10-iteration run typically costs under $0.04.

## Troubleshooting

**Test not running / silently skipped**
- Ensure `BEDROCK_EVAL_ENABLED=true` is set. The test is gated by `@EnabledIfEnvironmentVariable` and will be skipped without this exact value.

**Authentication errors**
- Verify your AWS credentials are configured and not expired. The test uses the default credential chain — check `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` environment variables, `~/.aws/credentials`, or your SSO session.

**Wrong region / model not available**
- Set `AWS_REGION` to a region where Amazon Nova Pro is available. The default is `eu-west-1`.
- Ensure you have [requested model access](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html) for Amazon Nova Pro in your target region.

**Throttling or rate limit errors**
- If Bedrock returns throttling errors, individual iterations are recorded as failed but the eval continues. Reduce `BEDROCK_EVAL_ITERATIONS` or wait before retrying.

**`bedrockEval` task not found**
- Run from the project root: `./gradlew :software:infra:aws:generation:bedrockEval`
- Ensure the `software/infra/aws/generation/build.gradle.kts` has the `bedrockEval` task registered.

## Safety

The eval test is excluded from normal test runs and CI through two mechanisms:

1. **JUnit tag filtering** — The test class is annotated with `@Tag("bedrock-eval")`. The standard `test` task in `software/infra/aws/generation/build.gradle.kts` excludes this tag via `excludeTags("bedrock-eval")`, so `./gradlew test` never picks it up.
2. **Environment variable gating** — The test is annotated with `@EnabledIfEnvironmentVariable(named = "BEDROCK_EVAL_ENABLED", matches = "true")`. Even if the tag filter were bypassed, the test would still be skipped without the explicit environment variable.

The dedicated `bedrockEval` Gradle task includes only the `bedrock-eval` tag, providing a clear, intentional entry point for running the eval.
