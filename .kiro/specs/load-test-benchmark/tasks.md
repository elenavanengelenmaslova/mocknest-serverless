# Tasks

## Task 1: Create the load test bash script (`scripts/load-test.sh`)

- [x] 1.1 Create `scripts/load-test.sh` with shebang, `set -e`, `set -o pipefail`, and header comment block following `post-deploy-test.sh` style
- [x] 1.2 Implement environment variable validation: require `API_URL`, `AUTH_MODE`, `REQUEST_RATE`, `DURATION_MINUTES`, `TEST_LABEL`; require `API_KEY` when `AUTH_MODE=API_KEY`; print usage message and exit 2 if missing
- [x] 1.3 Implement `REQUEST_RATE` validation: reject values > 50 with error message explaining API Gateway throttle constraint; exit 1
- [x] 1.4 Build curl options array for both API_KEY and IAM auth modes, matching `post-deploy-test.sh` patterns (without `--fail` flag, since we need to capture non-2xx status codes)
- [x] 1.5 Implement the sequential request loop: calculate total requests (`REQUEST_RATE × DURATION_MINUTES × 60`), calculate delay (`1 / REQUEST_RATE`), send GET requests to `{API_URL}/__admin/health` using `curl --write-out` to capture `time_total` and `http_code`, sleep between requests
- [x] 1.6 Implement per-request tracking: record timestamp, HTTP status code, and latency_ms for each request; track 429 count and consecutive non-2xx error count; abort if >10 consecutive non-2xx (excluding 429)
- [x] 1.7 Implement JSON output: write all results to `load-test-results.json` with metadata (test_label, start_time, end_time, request_rate, duration_minutes, total_requests, throttled_count, error_count, is_valid) and per-request array
- [x] 1.8 Make the script executable (`chmod +x`)
- [~] 1.9 Test the script locally against a deployed stack with a short run (1 min, 2 req/s) to verify JSON output, timing capture, and error handling

## Task 2: Create the reusable load test workflow (`workflow-load-test.yml`)

- [x] 2.1 Create `.github/workflows/workflow-load-test.yml` with `workflow_call` inputs matching Requirement 1 parameters: `stack-name` (required), `aws-region` (default `eu-west-1`), `test-label` (required), `request-rate` (default `"5"`), `duration-minutes` (default `"10"`), `github-actions-role-name` (default `"GitHubOIDCAdmin"`), and `AWS_ACCOUNT_ID` secret
- [x] 2.2 Add the checkout and AWS credentials configuration steps (OIDC role assumption pattern matching `workflow-integration-test.yml`)
- [x] 2.3 Add the stack resolution step: resolve API URL, API key ID, auth mode from CloudFormation stack outputs; resolve API key value via `aws apigateway get-api-key` (API_KEY mode only); resolve runtime Lambda memory size from stack parameters; exit with descriptive error if stack not found
- [x] 2.4 Add the pre-test cleanup step: send `DELETE /__admin/mappings` to the API, verify 200 response, exit on failure
- [x] 2.5 Add the load test execution step: set environment variables (`API_URL`, `API_KEY`, `AUTH_MODE`, `REQUEST_RATE`, `DURATION_MINUTES`, `TEST_LABEL`), run `scripts/load-test.sh`
- [x] 2.6 Add the CloudWatch wait step: sleep 60 seconds for log flush
- [x] 2.7 Add the CloudWatch Logs Insights query step: execute `aws logs start-query` against `/aws/lambda/{stack-name}-runtime` log group for REPORT lines within the test time window, poll with `aws logs get-query-results` (max 60s, 5s intervals), save results to `cloudwatch-results.json`; handle query failure gracefully (set a flag for the report generator)
- [x] 2.8 Add the report generation step: inline python3 script that reads `load-test-results.json` and `cloudwatch-results.json`, calculates percentiles using `statistics.quantiles()`, separates cold starts (restore_duration present) from warm invocations, produces three percentile tables (All Requests client-side, Warm Only lambda-side, Cold Starts Only lambda-side), includes metadata section, includes 429 warning when throttled_count > 0, includes server-side unavailable warning when CloudWatch data is missing, writes output to `load-test-report.md`
- [x] 2.9 Add the Job Summary step: write `load-test-report.md` content to `$GITHUB_STEP_SUMMARY`
- [x] 2.10 Add the artifact upload step: use `actions/upload-artifact@v4` to upload `load-test-results.json`, `cloudwatch-results.json`, and `load-test-report.md` with artifact name `load-test-{test-label}-{timestamp}`; use `if: always()` to ensure partial results are uploaded on failure

## Task 3: Create the trigger workflow (`load-test-on-demand.yml`)

- [x] 3.1 Create `.github/workflows/load-test-on-demand.yml` with `workflow_dispatch` trigger and input parameters: `stack-name` (string, required), `aws-region` (string, default `eu-west-1`), `test-label` (string, required), `request-rate` (string, default `"5"`), `duration-minutes` (string, default `"10"`), `github-actions-role-name` (string, default `"GitHubOIDCAdmin"`)
- [x] 3.2 Add `permissions: id-token: write, contents: read`
- [x] 3.3 Add single job that invokes `workflow-load-test.yml` as a reusable workflow, passing all inputs and `AWS_ACCOUNT_ID` secret

## Task 4: Update performance documentation

- [x] 4.1 Add a new "Load Test Benchmarking" section to `docs/PERFORMANCE.md` describing the load test methodology (target endpoint, sequential request pattern, rate limiting constraints)
- [x] 4.2 Document how to trigger the load test workflow and describe each input parameter
- [x] 4.3 Document how to interpret the three percentile tables (all requests client-side, warm only lambda-side, cold starts only lambda-side) and what each metric means
- [x] 4.4 Document the comparison workflow for framework migrations (run on version A, run on version B, download artifacts, compare side by side)
- [x] 4.5 Document the API Gateway throttle constraints (BurstLimit: 1, RateLimit: 100 req/s) and why the test uses sequential requests at a low rate

## Task 5: End-to-end validation

- [~] 5.1 Trigger the workflow via `workflow_dispatch` against a test stack with a short duration (2 min, 3 req/s) and verify: Job Summary renders all three tables, artifacts are uploaded with correct naming, no 429s at the configured rate
- [~] 5.2 Verify error handling: test with a non-existent stack name to confirm descriptive error message; test with `request-rate` > 50 to confirm rejection
