# Bedrock Prompt Evaluation Tests

MockNest Serverless includes a prompt evaluation suite that measures the quality of AI-generated mocks across protocols. These tests call Amazon Bedrock with real API specifications and evaluate the output using both structural validation and an LLM-as-a-judge semantic check.

## Why Run Eval Tests

Prompt templates directly affect the quality of generated mocks. When you change a prompt file under `software/application/src/main/resources/prompts/`, the eval tests help you measure whether the change improved or regressed generation quality.

The recommended workflow for any prompt change:

1. Run the eval suite **before** the change to establish a baseline
2. Make the prompt change
3. Run the eval suite **after** the change
4. Compare the results (scenario pass rate, first-pass valid rate, latency, cost)

This before/after comparison gives you concrete data on the impact of your change.

## Prerequisites

- AWS credentials configured with access to Amazon Bedrock in the target region
- The `eu.amazon.nova-pro-v1:0` model (or your configured model) must be enabled in your Bedrock console
- These tests incur a small cost per run (typically $0.01–$0.02 for the full suite)

## Running the Eval Suite

```bash
BEDROCK_EVAL_ENABLED=true \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BEDROCK_EVAL_ENABLED` | — | Must be set to `true` to run eval tests |
| `BEDROCK_EVAL_ITERATIONS` | `1` | Number of iterations per scenario (higher values reduce variance) |
| `AWS_REGION` | `eu-west-1` | AWS region for Bedrock API calls |

### Example: Multiple Iterations for Statistical Confidence

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

## What the Tests Measure

Each scenario in the eval dataset goes through:

1. **Mock generation** — The full generation pipeline runs against Bedrock, including specification parsing, schema reduction, prompt construction, and AI response parsing
2. **Structural validation** — Protocol-specific validators check the generated mocks (e.g., GraphQL response structure, SOAP envelope format, REST status codes)
3. **Correction retry** — If validation fails on the first pass, the correction prompt is sent to fix issues (retry budget = 1)
4. **LLM-as-a-judge** — A separate Bedrock call evaluates whether the generated mocks semantically match the scenario description

### Metrics Reported

| Metric | Description |
|--------|-------------|
| **1st-pass valid** | Percentage of mocks that passed validation without correction |
| **After retry valid** | Percentage of mocks valid after the correction retry |
| **Scenario pass** | Whether the scenario passed all checks (generation + validation + semantic) |
| **Avg cost/run** | Estimated Bedrock API cost per scenario run |
| **Avg latency** | Average wall-clock time for generation + validation |

## Summary Table

After all scenarios complete, a summary table is printed to the test output:

```
╔══════════════════════════════════════════════════════════════════════════════════════════════════════╗
║                         MULTI-PROTOCOL BEDROCK PROMPT EVAL SUMMARY                                  ║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ Protocol  │ Runs │ 1st-pass valid │ After retry valid │ Scenario pass │ Avg cost/run │ Avg latency ║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ GraphQL   │ 2    │ 50%            │ 100%              │ 100%          │ $0.0053      │ 2.6s        ║
║ SOAP      │ 1    │ 100%           │ 100%              │ 100%          │ $0.0046      │ 3.2s        ║
╚══════════════════════════════════════════════════════════════════════════════════════════════════════╝
```

## Eval Dataset

Scenarios are defined in:

```
software/infra/aws/generation/src/test/resources/eval/multi-protocol-eval-dataset.json
```

Each scenario specifies:
- **protocol** — REST, GraphQL, or SOAP
- **specFile** — Path to the API specification file (under `src/test/resources/eval/`)
- **format** — Specification format (OPENAPI_3, GRAPHQL, WSDL)
- **description** — Natural language instructions for mock generation
- **semanticCheck** — Criteria for the LLM judge to evaluate the output

### Adding New Scenarios

To add a new eval scenario:

1. Place the API specification file in `software/infra/aws/generation/src/test/resources/eval/`
2. Add an entry to the `examples` array in `multi-protocol-eval-dataset.json`:

```json
{
  "input": "my-new-scenario",
  "metadata": {
    "protocol": "REST",
    "specFile": "eval/my-api-spec.yaml",
    "format": "OPENAPI_3",
    "namespace": "my-api-eval",
    "description": "Generate mocks for the GET /users endpoint returning 2 users.",
    "semanticCheck": "Verify there is a GET /users mock returning an array of 2 users."
  }
}
```

3. Run the eval suite to verify the new scenario works

## Test Isolation

The eval tests are excluded from the normal `./gradlew test` run via the `bedrock-eval` JUnit tag. They only run through the dedicated `bedrockEval` Gradle task and require the `BEDROCK_EVAL_ENABLED` environment variable to be set.

This ensures:
- Normal CI/CD builds are not affected
- No unexpected Bedrock costs from routine test runs
- Eval tests can be run on-demand when prompt quality needs to be measured

## Cost Considerations

Each eval run makes multiple Bedrock API calls:
- One generation call per scenario (input tokens: ~2,500–3,000, output tokens: ~200–800)
- One correction call per scenario if first-pass validation fails
- One LLM judge call per scenario

Typical cost per full suite run: **$0.01–$0.02** (3 scenarios, 1 iteration each).

With `BEDROCK_EVAL_ITERATIONS=3`, expect roughly 3x the cost.

## Viewing Detailed Results

Beyond the summary table in stdout, you can find:
- **JUnit XML report**: `software/infra/aws/generation/build/test-results/bedrockEval/`
- **HTML report**: `software/infra/aws/generation/build/reports/tests/bedrockEval/`

The XML report includes the full test output with per-scenario logs, token usage, validation errors, and the summary table.
