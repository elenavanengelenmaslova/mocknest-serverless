# Requirements Document

## Introduction

This document defines the requirements for a GitHub Actions `workflow_dispatch` pipeline that performs load testing against a deployed MockNest Serverless stack to measure cold start and warm invocation performance of the runtime Lambda. The primary use case is comparing framework migrations (e.g., Spring Cloud Function → Koin) by running the pipeline on each version and comparing results.

The load test targets the `GET /__admin/health` endpoint on the runtime Lambda via API Gateway with no mock data loaded, measuring pure cold/warm start latency without data loading overhead. Results are reported as three percentile tables (all requests, warm invocations only, cold starts only) in the GitHub Actions Job Summary, with raw data uploaded as downloadable artifacts for offline comparison.

The pipeline operates within API Gateway rate limiting constraints (BurstLimit: 1, RateLimit: 100 req/s) by sending sequential requests at a configurable rate well below the throttle limit. Any 429 response invalidates the test run.

## Glossary

- **Load_Test_Workflow**: The reusable GitHub Actions workflow (`.github/workflows/workflow-load-test.yml`) that orchestrates the load test execution, metric collection, and report generation
- **Load_Test_Trigger_Workflow**: The standalone `workflow_dispatch` trigger workflow that invokes Load_Test_Workflow (similar to `deploy-on-demand.yml` pattern)
- **Load_Test_Script**: The bash script (`scripts/load-test.sh`) that sends sequential HTTP requests to the target endpoint, records per-request client-side latency, and outputs raw timing data as JSON
- **Report_Generator**: A python3 script (inline or file-based) that calculates percentile statistics from raw timing data and CloudWatch metrics, and produces a markdown report
- **Runtime_Lambda**: The MockNest Serverless runtime Lambda function (`{stack-name}-runtime`) that serves mock responses and the admin health endpoint
- **Health_Endpoint**: The `GET /__admin/health` endpoint on the Runtime_Lambda, used as the load test target
- **Client_Side_Latency**: The total round-trip time measured by curl for each HTTP request, including network transit, API Gateway processing, and Lambda execution
- **Cold_Start**: A Lambda invocation where SnapStart restore occurs, identified by the presence of `Restore Duration` in the CloudWatch REPORT log line
- **Warm_Invocation**: A Lambda invocation where no SnapStart restore occurs, identified by the absence of `Restore Duration` in the CloudWatch REPORT log line
- **Cold_Start_Duration**: The sum of Restore Duration and Duration from a CloudWatch REPORT log line for a cold start invocation
- **CloudWatch_Logs_Insights**: The AWS service used to query Lambda REPORT log lines for server-side duration and restore duration metrics after the load test completes
- **Test_Label**: A user-provided string (e.g., "spring-cloud-function", "koin") used to identify a test run for comparison purposes
- **Job_Summary**: The GitHub Actions Job Summary (`$GITHUB_STEP_SUMMARY`) where the markdown report is rendered for immediate visibility
- **Test_Artifact**: The GitHub Actions artifact containing raw JSON results and the markdown report, downloadable for offline comparison
- **API_Gateway_Throttle**: The usage plan rate limiting configuration (BurstLimit: 1, RateLimit: 100 req/s) that constrains the maximum request rate

## Requirements

### Requirement 1: Load Test Trigger Workflow

**User Story:** As a developer, I want a manually triggered GitHub Actions workflow for load testing, so that I can benchmark Lambda cold start and warm invocation performance on demand against any deployed stack.

#### Acceptance Criteria

1. THE Load_Test_Trigger_Workflow SHALL provide a `workflow_dispatch` trigger with the following input parameters: `stack-name` (string, required, the CloudFormation stack name of the deployed MockNest instance to test), `aws-region` (string, default `eu-west-1`), `test-label` (string, required, e.g., "spring-cloud-function" or "koin"), `request-rate` (string, default "5"), `duration-minutes` (string, default "10"), and `github-actions-role-name` (string, default "GitHubOIDCAdmin")
2. THE Load_Test_Trigger_Workflow SHALL invoke Load_Test_Workflow as a reusable workflow, passing all input parameters and the `AWS_ACCOUNT_ID` secret
3. THE Load_Test_Trigger_Workflow SHALL request `id-token: write` and `contents: read` permissions for OIDC authentication

### Requirement 2: Reusable Load Test Workflow

**User Story:** As a DevOps engineer, I want a reusable load test workflow, so that it can be called from the trigger workflow or composed into other pipelines.

#### Acceptance Criteria

1. THE Load_Test_Workflow SHALL accept `workflow_call` inputs matching the parameters defined in Requirement 1 acceptance criterion 1
2. THE Load_Test_Workflow SHALL resolve the API Gateway URL, API key, auth mode, and runtime Lambda function name from CloudFormation stack outputs using the same pattern as Integration_Test_Workflow
3. THE Load_Test_Workflow SHALL resolve the runtime Lambda memory size from the CloudFormation stack parameters for inclusion in the report metadata
4. THE Load_Test_Workflow SHALL configure AWS credentials using the OIDC role assumption pattern with the provided `github-actions-role-name` and `AWS_ACCOUNT_ID` secret

### Requirement 3: Pre-Test Cleanup

**User Story:** As a developer, I want the load test to clear all mock mappings before running, so that the health endpoint measures pure cold/warm start performance without data loading latency.

#### Acceptance Criteria

1. WHEN the load test begins, THE Load_Test_Workflow SHALL send a `DELETE /__admin/mappings` request to the target API to remove all existing mock mappings
2. WHEN the cleanup request succeeds, THE Load_Test_Workflow SHALL verify the response status code is 200 before proceeding with the load test
3. IF the cleanup request fails, THEN THE Load_Test_Workflow SHALL exit with a non-zero exit code and report the failure

### Requirement 4: Load Test Execution

**User Story:** As a developer, I want the load test to send sequential HTTP requests at a controlled rate for a configurable duration, so that I can measure realistic client-side latency including both cold and warm invocations.

#### Acceptance Criteria

1. THE Load_Test_Script SHALL send sequential HTTP GET requests to the Health_Endpoint at the rate specified by the `request-rate` parameter (requests per second)
2. THE Load_Test_Script SHALL continue sending requests for the duration specified by the `duration-minutes` parameter
3. THE Load_Test_Script SHALL record the following for each request: HTTP status code, Client_Side_Latency in milliseconds (using curl timing), and a timestamp
4. THE Load_Test_Script SHALL use curl with timing output to measure Client_Side_Latency for each request
5. THE Load_Test_Script SHALL include the API key header (`x-api-key`) in each request when the stack uses API_KEY authentication mode
6. THE Load_Test_Script SHALL output all per-request timing data as a JSON file

### Requirement 5: API Gateway Throttle Compliance

**User Story:** As a developer, I want the load test to operate within API Gateway rate limits, so that throttling does not invalidate the performance measurements.

#### Acceptance Criteria

1. THE Load_Test_Script SHALL send requests sequentially (one at a time) to respect the BurstLimit of 1
2. THE Load_Test_Script SHALL enforce a minimum delay between requests based on the configured `request-rate` to stay below the RateLimit of 100 req/s
3. THE Load_Test_Script SHALL track the count of HTTP 429 responses received during the test
4. IF any HTTP 429 response is received during the test, THEN THE Load_Test_Script SHALL flag the test run as invalid in the output data
5. WHEN the test completes, THE Report_Generator SHALL include the 429 count in the report and display a warning when the count is greater than zero

### Requirement 6: CloudWatch Logs Insights Query for Server-Side Metrics

**User Story:** As a developer, I want server-side Lambda metrics collected from CloudWatch Logs after the test, so that I can separately analyze cold start and warm invocation performance from the Lambda perspective.

#### Acceptance Criteria

1. WHEN the load test completes, THE Load_Test_Workflow SHALL wait at least 60 seconds for CloudWatch logs to flush before querying
2. THE Load_Test_Workflow SHALL execute a CloudWatch Logs Insights query against the `/aws/lambda/{stack-name}-runtime` log group for REPORT lines within the test time window
3. THE Load_Test_Workflow SHALL use `aws logs start-query` and `aws logs get-query-results` to execute the CloudWatch Logs Insights query
4. THE Load_Test_Workflow SHALL extract `@duration` and `Restore Duration` fields from REPORT log lines to distinguish cold starts from warm invocations
5. THE Load_Test_Workflow SHALL calculate Cold_Start_Duration as the sum of Restore Duration and Duration for each cold start invocation
6. THE Load_Test_Workflow SHALL separate query results into two datasets: cold start invocations (Restore Duration present) and warm invocations (Restore Duration absent)

### Requirement 7: Percentile Report Generation

**User Story:** As a developer, I want a percentile-based latency report with three separate tables, so that I can understand overall performance, warm baseline, and cold start impact independently.

#### Acceptance Criteria

1. THE Report_Generator SHALL produce three percentile tables: "All Requests (Client-Side)" from Load_Test_Script data, "Warm Invocations Only (Lambda-Side)" from CloudWatch warm invocation data, and "Cold Starts Only (Lambda-Side)" from CloudWatch cold start data
2. EACH percentile table SHALL include the following columns: p50, p95, p99, max, and count
3. THE Report_Generator SHALL include a metadata section in the report containing: Test_Label, stack name, AWS region, runtime Lambda memory size, test duration, request rate, target endpoint, total requests sent, total errors (non-2xx excluding 429), and 429 count
4. THE Report_Generator SHALL use python3 for percentile calculations
5. THE Report_Generator SHALL output the report as a markdown-formatted file

### Requirement 8: GitHub Actions Job Summary

**User Story:** As a developer, I want the load test report rendered in the GitHub Actions Job Summary, so that I can view results immediately on the workflow run page without downloading artifacts.

#### Acceptance Criteria

1. WHEN the report is generated, THE Load_Test_Workflow SHALL write the markdown report content to `$GITHUB_STEP_SUMMARY`
2. THE Job_Summary SHALL display all three percentile tables and the metadata section
3. WHEN the 429 count is greater than zero, THE Job_Summary SHALL display a prominent warning indicating the test run is invalid due to API Gateway throttling

### Requirement 9: Artifact Upload

**User Story:** As a developer, I want raw results and the markdown report uploaded as GitHub Actions artifacts, so that I can download and compare results from different test runs offline.

#### Acceptance Criteria

1. WHEN the load test and report generation complete, THE Load_Test_Workflow SHALL upload the following files as a GitHub Actions artifact: the raw JSON timing data from Load_Test_Script, the CloudWatch query results as JSON, and the markdown report file
2. THE artifact name SHALL include the Test_Label and a timestamp for easy identification (e.g., `load-test-koin-20240115-143022`)
3. THE artifact SHALL be retained for the default GitHub Actions artifact retention period

### Requirement 10: Load Test Script Structure

**User Story:** As a developer, I want the load test script to follow the same patterns as the existing post-deploy-test.sh, so that the codebase remains consistent and maintainable.

#### Acceptance Criteria

1. THE Load_Test_Script SHALL be located at `scripts/load-test.sh` and follow the same coding patterns as `scripts/post-deploy-test.sh`
2. THE Load_Test_Script SHALL accept the following environment variables: `API_URL`, `API_KEY`, `AUTH_MODE`, `REQUEST_RATE`, `DURATION_MINUTES`, and `TEST_LABEL`
3. THE Load_Test_Script SHALL use `set -e` and `set -o pipefail` for strict error handling
4. THE Load_Test_Script SHALL support both API_KEY and IAM authentication modes using the same curl options pattern as `scripts/post-deploy-test.sh`
5. IF a required environment variable is missing, THEN THE Load_Test_Script SHALL print a usage message and exit with a non-zero exit code

### Requirement 11: Performance Documentation

**User Story:** As a developer, I want the load test methodology documented in PERFORMANCE.md, so that contributors understand how to run benchmarks, interpret results, and compare framework migrations.

#### Acceptance Criteria

1. THE documentation SHALL add a new section to `docs/PERFORMANCE.md` titled "Load Test Benchmarking"
2. THE documentation section SHALL describe the load test methodology including the target endpoint, request pattern, and rate limiting constraints
3. THE documentation section SHALL explain how to trigger the load test workflow and describe each input parameter
4. THE documentation section SHALL explain how to interpret the three percentile tables and what each metric means
5. THE documentation section SHALL describe the comparison workflow for framework migrations (run on version A, run on version B, compare artifacts)
6. THE documentation section SHALL document the API Gateway throttle constraints and why the test uses sequential requests at a low rate

### Requirement 12: Error Handling and Validation

**User Story:** As a developer, I want robust error handling throughout the load test pipeline, so that failures are clearly reported and do not produce misleading results.

#### Acceptance Criteria

1. IF the CloudFormation stack does not exist or outputs cannot be resolved, THEN THE Load_Test_Workflow SHALL exit with a non-zero exit code and a descriptive error message
2. IF the Health_Endpoint returns a non-2xx status code (excluding 429) for more than 10 consecutive requests, THEN THE Load_Test_Script SHALL abort the test and report the failure
3. IF the CloudWatch Logs Insights query fails or returns no results, THEN THE Load_Test_Workflow SHALL generate the report with only the client-side latency table and include a warning that server-side metrics are unavailable
4. WHEN any step fails, THE Load_Test_Workflow SHALL ensure partial results are still uploaded as artifacts for debugging purposes
5. IF the `request-rate` parameter exceeds 50, THEN THE Load_Test_Script SHALL reject the value and exit with an error message explaining the API Gateway throttle constraint
