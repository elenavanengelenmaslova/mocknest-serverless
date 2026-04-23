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
- These tests incur a small cost per run (see [Cost Considerations](#cost-considerations))

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
| `BEDROCK_EVAL_FILTER` | — | Case-insensitive substring filter on scenario `input` names. Only matching scenarios run. When unset or empty, all scenarios run |
| `AWS_REGION` | `eu-west-1` | AWS region for Bedrock API calls |

### Example: Multiple Iterations for Statistical Confidence

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_ITERATIONS=3 \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

### Example: Filter to REST Scenarios Only

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_FILTER=rest \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

### Example: Filter to a Single Scenario

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_FILTER=rest-petstore-consistency \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

### Example: Filter by API Name

```bash
BEDROCK_EVAL_ENABLED=true \
BEDROCK_EVAL_FILTER=social \
  ./gradlew :software:infra:aws:generation:bedrockEval
```

The filter matches against the scenario `input` field using case-insensitive substring matching. This is useful for re-running only failing scenarios after prompt fixes without waiting for the entire suite.

## What the Tests Measure

Each scenario in the eval dataset goes through:

1. **Mock generation** — The full generation pipeline runs against Bedrock, including specification parsing, schema reduction, prompt construction, and AI response parsing
2. **Structural validation** — Protocol-specific validators check the generated mocks (e.g., GraphQL response structure, SOAP envelope format, REST status codes and response schemas)
3. **Correction retry** — If validation fails on the first pass, the correction prompt is sent to fix issues (retry budget = 1)
4. **LLM-as-a-judge** — A separate Bedrock call evaluates whether the generated mocks semantically match the scenario description

### Metrics Reported

| Metric | Description |
|--------|-------------|
| **1st-pass valid** | Percentage of mocks that passed validation without correction |
| **After retry valid** | Percentage of mocks valid after the correction retry |
| **Scenario pass** | Whether the scenario passed all checks (generation + validation + semantic) |
| **Gen cost** | Bedrock API cost for the mock generation call(s) |
| **Judge cost** | Bedrock API cost for the LLM-as-a-judge semantic evaluation call |
| **Avg cost/run** | Average total Bedrock API cost per scenario run (generation + judge) |
| **Avg latency** | Average wall-clock time for generation + validation |

## Protocol Breakdown

The eval suite covers 46 scenarios across 3 protocols and 12 API specifications:

| Protocol | Scenarios | API Specifications |
|----------|-----------|-------------------|
| REST | 16 | Petstore (20+ endpoints), Bored API (3 endpoints), Social Content (8-10 endpoints), Payment Financial (6-8 endpoints), Weather Utility (2-3 endpoints) |
| GraphQL | 15 | Pokemon (2 queries), Books (3 queries), E-commerce (4 queries + 4 mutations), Task Management (4 queries + 3 mutations) |
| SOAP | 15 | Calculator (3 operations), Banking Service (5 operations), Inventory Warehouse (6 operations), Notification Messaging (4 operations) |

### REST Scenario Complexity Levels

REST scenarios are distributed across 6 prompt complexity levels to measure quality at different difficulty tiers:

| Complexity Level | Scenarios | Description |
|-----------------|-----------|-------------|
| Basic | 5 | Generate mocks for all GET endpoints in a spec |
| Filtered | 2 | Generate mocks for a subset of endpoints only |
| Consistency | 2 | Cross-entity data coherence (e.g., order.petId matches pet.id) |
| Error | 2 | Error response generation (404, 500, 402, 422) |
| Realistic Data | 3 | Domain-specific realistic values (European names, EUR amounts) |
| Edge Case | 2 | Pagination with multiple pages of results, tag-based filtering |

### GraphQL Scenario Complexity Levels

GraphQL scenarios are distributed across 6 prompt complexity levels to measure quality at different difficulty tiers:

| Complexity Level | Scenarios | Description |
|-----------------|-----------|-------------|
| Basic | 7 | Generate mocks for queries, mutations, or all operations in a schema |
| Filtered | 1 | Generate mocks for a subset of operations only (e.g., order-related only) |
| Consistency | 2 | Cross-entity data coherence (e.g., order.customerId matches customer.id) |
| Error | 1 | GraphQL error responses with errors array and message fields |
| Realistic Data | 2 | Domain-specific realistic values (European product names, EUR prices) |
| Edge Case | 2 | Mutations with INPUT_OBJECT arguments, enum-filtered queries |

### SOAP Scenario Complexity Levels

SOAP scenarios are distributed across 6 prompt complexity levels to measure quality at different difficulty tiers:

| Complexity Level | Scenarios | Description |
|-----------------|-----------|-------------|
| Basic | 4 | Generate mocks for all operations in a WSDL |
| Filtered | 3 | Generate mocks for a subset of operations only (e.g., account-related, shipment-related) |
| Consistency | 2 | Cross-entity data coherence (e.g., accountId in transactions matches account) |
| Error | 2 | SOAP fault responses for error scenarios (insufficient funds, multi-fault) |
| Realistic Data | 3 | Domain-specific realistic values (European banking data, warehouse data) |
| Edge Case | 1 | XPath body matchers for operation-specific request matching |

## Summary Table

After all scenarios complete, a summary table is printed to the test output showing per-protocol aggregate metrics with cost breakdown. The latest results are published in the [README Generation Quality section](../README.md#generation-quality).

Example output format:

```
╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗
║                             MULTI-PROTOCOL BEDROCK PROMPT EVAL SUMMARY                                               ║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ Model: AmazonNovaPro                                                                                                 ║
║ Region: eu-west-1                                                                                                    ║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ Protocol  │ Runs │ 1st-pass valid │ After retry valid │ Scenario pass │ Gen cost │ Judge cost │ Avg cost/run │ Avg lat║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ REST      │ 16   │ 77%            │ 98%               │ 85%           │ $0.0480  │ $0.0240    │ $0.0045      │ 3.8s   ║
║ GraphQL   │ 15   │ 50%            │ 100%              │ 100%          │ $0.0750  │ $0.0390    │ $0.0053      │ 2.6s   ║
║ SOAP      │ 15   │ 100%           │ 100%              │ 100%          │ $0.0480  │ $0.0210    │ $0.0046      │ 3.2s   ║
╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ TOTAL     │ 46   │                │                   │               │ $0.1710  │ $0.0840    │ $0.0048      │        ║
╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝
```

The summary table includes:
- Per-protocol rows with generation cost, judge cost, average cost per run, and average latency
- A **TOTAL** footer row showing total generation cost, total judge cost, and total combined cost across all protocols

## Scenario Detail Table

A per-scenario breakdown table is also printed, grouped by API specification name:

```
╔════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗
║                                                    SCENARIO DETAIL TABLE                                                                           ║
╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ Scenario                           │ 1st-pass │ After-retry │ Pass │ Gen cost │ Judge cost │ Total  │ Latency │ Failure reason                     ║
╠════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣
║ [petstore]                                                                                                                                         ║
╠────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╣
║ rest-petstore-basic-get            │ 100%     │ 100%        │ ✓    │ 0.0035   │ 0.0015     │ 0.0050 │ 3.2s    │                                    ║
║ rest-petstore-filtered-pets        │ 80%      │ 100%        │ ✓    │ 0.0038   │ 0.0016     │ 0.0054 │ 4.1s    │                                    ║
║ rest-petstore-consistency          │ 67%      │ 100%        │ ✓    │ 0.0042   │ 0.0018     │ 0.0060 │ 4.5s    │                                    ║
║ rest-petstore-error                │ 50%      │ 100%        │ ✗    │ 0.0030   │ 0.0014     │ 0.0044 │ 3.0s    │ Semantic check failed              ║
║ rest-petstore-pagination           │ 33%      │ 67%         │ ✗    │ 0.0045   │ 0.0020     │ 0.0065 │ 5.2s    │ Validation failed                  ║
╠────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╣
║ [social-content]                                                                                                                                   ║
╠────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╣
║ rest-social-basic-get              │ 100%     │ 100%        │ ✓    │ 0.0033   │ 0.0015     │ 0.0048 │ 3.0s    │                                    ║
║ ...                                                                                                                                                ║
╚════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝
```

The detail table shows per-scenario: input name, first-pass valid rate, after-retry valid rate, pass/fail status, generation cost, judge cost, total cost, latency, and failure reason for failed scenarios.

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

### API Specification Files

| File | Protocol | Domain | Endpoints |
|------|----------|--------|-----------|
| `petstore-openapi-3.0.yaml` | REST | Pet store | 20+ |
| `bored-api-openapi-3.0.yaml` | REST | Activities | 3 |
| `social-content-openapi-3.0.yaml` | REST | Social/Content | 8-10 |
| `payment-financial-openapi-3.0.yaml` | REST | Payment/Financial | 6-8 |
| `weather-utility-openapi-3.0.yaml` | REST | Weather/Utility | 2-3 |
| `pokemon-graphql-introspection.json` | GraphQL | Pokemon | 2 queries |
| `books-graphql-introspection.json` | GraphQL | Books | 3 queries |
| `ecommerce-graphql-introspection.json` | GraphQL | E-commerce | 4 queries + 4 mutations, ENUMs (OrderStatus, ProductCategory), INPUT_OBJECTs, nested types, multi-field types |
| `taskmanagement-graphql-introspection.json` | GraphQL | Task Management | 4 queries + 3 mutations, ENUMs (TaskStatus, TaskPriority), INPUT_OBJECT, nested types, cross-entity relationships |
| `calculator-soap12.wsdl` | SOAP | Calculator | 3 operations |
| `banking-service-soap12.wsdl` | SOAP | Banking | 5 operations, nested types, enumerated TransactionStatus, cross-entity relationships |
| `inventory-warehouse-soap12.wsdl` | SOAP | Inventory/Warehouse | 6 operations, nested types, enumerated fields (ItemCategory, StockStatus), multi-field types |
| `notification-messaging-soap12.wsdl` | SOAP | Notification/Messaging | 4 operations, cross-entity relationships, multiple message types |

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

Each eval run makes multiple Bedrock API calls per scenario:
- **Generation call** — One generation call per scenario (input tokens: ~2,500–3,000, output tokens: ~200–800)
- **Correction call** — One correction call per scenario if first-pass validation fails
- **Judge call** — One LLM-as-a-judge call per scenario for semantic evaluation

### Cost Breakdown

| Phase | Description | Typical cost per scenario |
|-------|-------------|--------------------------|
| Generation | Mock generation Bedrock call(s) | $0.003–$0.005 |
| Judge | LLM-as-a-judge semantic evaluation | $0.001–$0.002 |
| **Total per scenario** | | **$0.004–$0.007** |

### Estimated Cost per Full Suite Run

| Suite | Scenarios | Estimated cost (1 iteration) | Estimated cost (3 iterations) |
|-------|-----------|------------------------------|-------------------------------|
| Full suite (all protocols) | 46 | $0.18–$0.32 | $0.55–$0.97 |
| REST only | 16 | $0.06–$0.11 | $0.19–$0.34 |
| GraphQL only | 15 | $0.06–$0.11 | $0.18–$0.32 |
| SOAP only | 15 | $0.06–$0.11 | $0.18–$0.32 |

Use `BEDROCK_EVAL_FILTER` to run subsets and reduce cost during iterative prompt tuning.

## Viewing Detailed Results

Beyond the summary table in stdout, you can find:
- **JUnit XML report**: `software/infra/aws/generation/build/test-results/bedrockEval/`
- **HTML report**: `software/infra/aws/generation/build/reports/tests/bedrockEval/`

The XML report includes the full test output with per-scenario logs, token usage, validation errors, and both the summary and detail tables.
