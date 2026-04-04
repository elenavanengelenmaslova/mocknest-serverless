# Implementation Tasks

## Overview

This document outlines the implementation tasks for the post-deployment integration test workflow. Tasks are organized into incremental iterations that can be committed, tested, and merged separately for easier code review and validation.

## Incremental Development Strategy

Each iteration builds on the previous one and can be merged independently:
- **Iteration 1**: Health checks + manual workflow (minimal viable test)
- **Iteration 2**: Delete mappings + REST generation/import + mock invocation tests
- **Iteration 2.5**: GitHub Actions job-level parallel execution (performance enhancement)
- **Iteration 3**: GraphQL generation/import + mock invocation tests
- **Iteration 4**: SOAP/WSDL generation/import + mock invocation tests
- **Iteration 5**: Automatic workflow trigger + documentation

## Task List

### Iteration 1: Health Checks + Manual Workflow (First PR)

- [x] 1. Create test script foundation
  - [x] 1.1 Create `scripts/post-deploy-test.sh` with basic structure (shebang, set -e, set -o pipefail)
  - [x] 1.2 Add input validation for API_URL and API_KEY arguments/environment variables
  - [x] 1.3 Add common curl options configuration (--fail, --silent, --show-error, --max-time 30)
  - [x] 1.4 Add helper function for HTTP response parsing (status code and body separation)
  - [x] 1.5 Make script executable (chmod +x)

- [x] 2. Implement health check tests
  - [x] 2.1 Implement `test_runtime_health()` function for GET /__admin/health
  - [x] 2.2 Add HTTP status code validation (expect 200)
  - [x] 2.3 Add response body validation (expect "status": "healthy")
  - [x] 2.4 Implement `test_ai_health()` function for GET /ai/generation/health
  - [x] 2.5 Add HTTP status code and body validation for AI health check
  - [x] 2.6 Add clear success/failure output messages

- [x] 3. Implement main test execution flow (health checks only)
  - [x] 3.1 Create `main()` function that calls health check test functions
  - [x] 3.2 Add test execution header with API URL (without API key)
  - [x] 3.3 Add test completion summary message
  - [x] 3.4 Call main function at end of script

- [x] 4. Create GitHub Actions workflow file (manual trigger only)
  - [x] 4.1 Create `.github/workflows/post-deploy-integration-test.yml`
  - [x] 4.2 Add workflow_dispatch trigger (manual trigger only for now)
  - [x] 4.3 Add workflow inputs for stack name or use default from samconfig.toml
  - [x] 4.4 Add permissions (id-token: write, contents: read)
  - [x] 4.5 Add ubuntu-latest runner configuration

- [ ] 5. Implement stack output retrieval step
  - [x] 5.1 Add checkout step (actions/checkout@v6)
  - [x] 5.2 Add AWS credentials configuration step (aws-actions/configure-aws-credentials@v6)
  - [x] 5.3 Add step to read stack name from samconfig.toml using Python tomllib
  - [x] 5.4 Add step to retrieve API_URL from CloudFormation stack outputs
  - [x] 5.5 Add step to retrieve API_KEY_ID from CloudFormation stack outputs
  - [x] 5.6 Add validation for stack outputs (not empty, API_URL starts with https://)
  - [x] 5.7 Add step to remove trailing slash from API_URL
  - [x] 5.8 Store outputs in step outputs for use in subsequent steps

- [ ] 6. Implement API key retrieval step
  - [x] 6.1 Add step to retrieve API key value using AWS CLI
  - [x] 6.2 Add immediate API key masking using ::add-mask::
  - [x] 6.3 Add validation that API key is not empty
  - [x] 6.4 Store API key in step output for use in test execution step

- [ ] 7. Implement test execution step
  - [x] 7.1 Add step to make test script executable
  - [x] 7.2 Add step to execute test script with API_URL and API_KEY from environment variables
  - [x] 7.3 Configure environment variables from previous step outputs
  - [x] 7.4 Ensure step fails if script exits with non-zero code

- [ ] 8. Implement test result reporting
  - [x] 8.1 Add step to create GitHub Actions summary (runs always, even on failure)
  - [x] 8.2 Add success summary with deployment details and test results
  - [x] 8.3 Add failure summary with error details (if test step failed)
  - [x] 8.4 Ensure API key is never included in summary

### Iteration 2: Delete Mappings + REST Generation/Import + Mock Invocation (Second PR)

- [x] 11. Implement mapping cleanup test
- [ ] 11. Implement mapping cleanup test
  - [x] 11.1 Implement `test_delete_all_mappings()` function for DELETE /__admin/mappings
  - [x] 11.2 Add HTTP status code validation (expect 200)
  - [x] 11.3 Add clear success/failure output messages

- [x] 12. Implement REST/OpenAPI mock generation test
  - [x] 12.1 Create REST generation request payload matching Postman collection
  - [x] 12.2 Use Petstore API specification URL (https://petstore3.swagger.io/api/v3/openapi.json)
  - [x] 12.3 Implement `test_rest_generation()` function for POST /ai/generation/from-spec
  - [x] 12.4 Add HTTP status code validation (expect 200)
  - [x] 12.5 Add response body validation (expect "mappings" array)
  - [x] 12.6 Store generated mappings in variable for import test
  - [x] 12.7 Add clear success/failure output messages

- [x] 13. Implement REST mock import test
  - [x] 13.1 Implement `test_rest_import()` function for POST /__admin/mappings/import
  - [x] 13.2 Use mappings generated from REST generation test
  - [x] 13.3 Add HTTP status code validation (expect 200)
  - [x] 13.4 Add clear success/failure output messages

- [x] 14. Implement REST mock invocation tests
  - [x] 14.1 Identify 2 endpoints from Petstore API to test (e.g., GET /pet/{petId}, POST /pet)
  - [x] 14.2 Implement `test_rest_mock_invocation()` function
  - [x] 14.3 Call first generated mock endpoint and verify response
  - [x] 14.4 Call second generated mock endpoint and verify response
  - [x] 14.5 Add clear success/failure output messages

### Iteration 2.5: GitHub Actions Job-Level Parallel Execution (Performance Enhancement PR)

- [-] 15. Refactor test script to support test suite selection
  - [x] 15.1 Add TEST_SUITE parameter as first argument (setup, rest, graphql, soap, all)
  - [x] 15.2 Implement case statement to route to appropriate test functions
  - [x] 15.3 Update script usage documentation to include test suite options

- [x] 16. Create setup job in workflow
  - [x] 16.1 Create new job named "setup" with health checks and cleanup
  - [x] 16.2 Add job outputs for api-url and api-key
  - [x] 16.3 Move stack output retrieval and API key retrieval to setup job
  - [x] 16.4 Call test script with "setup" argument
  - [x] 16.5 Ensure API key is masked in job outputs

- [x] 17. Create REST test job
  - [x] 17.1 Create new job named "test-rest" that depends on setup job (needs: setup)
  - [x] 17.2 Use api-url and api-key from setup job outputs
  - [x] 17.3 Call test script with "rest" argument
  - [x] 17.4 Add conditional execution based on workflow_dispatch input

- [x] 18. Add workflow_dispatch inputs for selective test execution
  - [x] 18.1 Add workflow_dispatch trigger with test_suite input
  - [x] 18.2 Add choice options: all, rest, graphql, soap
  - [x] 18.3 Set default to "all"
  - [x] 18.4 Update job conditionals to respect input

### Iteration 3: GraphQL Generation/Import + Mock Invocation (Third PR)

- [x] 24. Implement GraphQL mock generation test
  - [x] 24.1 Create GraphQL generation request payload matching Postman collection
  - [x] 24.2 Use Countries GraphQL API URL (https://countries.trevorblades.com/graphql)
  - [x] 24.3 Implement `test_graphql_generation()` function for POST /ai/generation/from-spec
  - [x] 24.4 Add HTTP status code validation (expect 200)
  - [x] 24.5 Add response body validation (expect "mappings" array)
  - [x] 24.6 Store generated mappings in variable for import test
  - [x] 24.7 Add clear success/failure output messages

- [x] 25. Implement GraphQL mock import test
  - [x] 25.1 Implement `test_graphql_import()` function for POST /__admin/mappings/import
  - [x] 25.2 Use mappings generated from GraphQL generation test
  - [x] 25.3 Add HTTP status code validation (expect 200)
  - [x] 25.4 Add clear success/failure output messages

- [x] 26. Implement GraphQL mock invocation tests
  - [x] 26.1 Identify 2 GraphQL queries to test from Countries API
  - [x] 26.2 Implement `test_graphql_mock_invocation()` function
  - [x] 26.3 Call first generated GraphQL mock and verify response
  - [x] 26.4 Call second generated GraphQL mock and verify response
  - [x] 26.5 Add clear success/failure output messages

- [x] 27. Create GraphQL test job in workflow
  - [x] 27.1 Create new job named "test-graphql" that depends on setup job (needs: setup)
  - [x] 27.2 Use api-url and api-key from setup job outputs
  - [x] 27.3 Call test script with "graphql" argument
  - [x] 27.4 Add conditional execution based on workflow_dispatch input

### Iteration 4: SOAP/WSDL Generation/Import + Mock Invocation (Fourth PR)

- [x] 30. Implement SOAP/WSDL mock generation test
  - [x] 30.1 Create SOAP generation request payload matching Postman collection
  - [x] 30.2 Use Calculator WSDL URL (http://www.dneonline.com/calculator.asmx?WSDL)
  - [x] 30.3 Implement `test_soap_generation()` function for POST /ai/generation/from-spec
  - [x] 30.4 Add HTTP status code validation (expect 200)
  - [x] 30.5 Add response body validation (expect "mappings" array)
  - [x] 30.6 Store generated mappings in variable for import test
  - [x] 30.7 Add clear success/failure output messages

- [x] 31. Implement SOAP mock import test
  - [x] 31.1 Implement `test_soap_import()` function for POST /__admin/mappings/import
  - [x] 31.2 Use mappings generated from SOAP generation test
  - [x] 31.3 Add HTTP status code validation (expect 200)
  - [x] 31.4 Add clear success/failure output messages

- [ ] 32. Implement SOAP mock invocation tests
  - [ ] 32.1 Identify 2 SOAP operations to test from Calculator WSDL (e.g., Add, Multiply)
  - [ ] 32.2 Implement `test_soap_mock_invocation()` function
  - [ ] 32.3 Call first generated SOAP mock and verify response
  - [ ] 32.4 Call second generated SOAP mock and verify response
  - [ ] 32.5 Add clear success/failure output messages

- [x] 33. Create SOAP test job in workflow
  - [x] 33.1 Create new job named "test-soap" that depends on setup job (needs: setup)
  - [x] 33.2 Use api-url and api-key from setup job outputs
  - [x] 33.3 Call test script with "soap" argument
  - [x] 33.4 Add conditional execution based on workflow_dispatch input

- [ ] 34. Test workflow from feature branch
  - [ ] 34.1 Create feature branch and push changes
  - [ ] 34.2 Manually trigger workflow from GitHub Actions UI
  - [ ] 34.3 Verify all tests pass in workflow execution
  - [ ] 34.4 Verify SOAP mocks work correctly

- [ ] 35. Create PR and merge Iteration 4
  - [ ] 35.1 Create pull request with detailed description
  - [ ] 35.2 Wait for CodeRabbit review
  - [ ] 35.3 Address review comments if any
  - [ ] 35.4 Merge to main branch after approval
  - [ ] 29.4 Merge to main branch after approval

### Iteration 5: Automatic Workflow Trigger + Documentation (Fifth PR)

- [ ] 36. Update workflow to automatic trigger
  - [ ] 36.1 Add workflow_run trigger for "CICD - Main Branch AWS" and "CD - Deploy On Demand"
  - [ ] 36.2 Add conditional execution (only run if triggering workflow succeeded)
  - [ ] 36.3 Keep workflow_dispatch trigger for manual testing
  - [ ] 36.4 Test automatic trigger by deploying to test environment

- [ ] 37. Update documentation
  - [ ] 37.1 Add section to README.md about post-deployment integration tests
  - [ ] 37.2 Document how to run tests locally
  - [ ] 37.3 Document workflow trigger conditions (automatic and manual)
  - [ ] 37.4 Document configuration options (MAX_RETRIES, PARALLEL_EXECUTION)
  - [ ] 37.5 Document troubleshooting steps for test failures
  - [ ] 37.6 Add link to workflow file in documentation

- [ ] 38. Verify Postman collection alignment
  - [ ] 38.1 Compare test script payloads with Postman collection requests
  - [ ] 38.2 Verify REST generation payload matches Postman "Generate from OpenAPI" request
  - [ ] 38.3 Verify GraphQL generation payload matches Postman "Generate from GraphQL" request
  - [ ] 38.4 Verify SOAP generation payload matches Postman "Generate from WSDL" request
  - [ ] 38.5 Update test script if any mismatches are found

- [ ] 39. Final validation
  - [ ] 39.1 Test automatic workflow trigger with actual deployment
  - [ ] 39.2 Verify workflow runs successfully after deployment to main
  - [ ] 39.3 Verify parallel execution reduces test time (~30s vs ~90s)
  - [ ] 39.4 Verify retry logic handles transient failures
  - [ ] 39.5 Review all code changes for quality and completeness

- [ ] 40. Create PR and merge Iteration 5
  - [ ] 40.1 Create pull request with detailed description
  - [ ] 40.2 Wait for CodeRabbit review
  - [ ] 40.3 Address review comments if any
  - [ ] 40.4 Merge to main branch after approval

## Notes

- All curl commands must be in the test script, not in the workflow YAML
- Test payloads must match the Postman collection exactly
- API keys must be masked immediately after retrieval
- 30-second timeout is required for AI generation calls (can take up to 20 seconds)
- Script must follow bash best practices (set -e, set -o pipefail)
- Script must return exit code 0 on success, non-zero on failure
- All tests must pass before proceeding to next task group

## GitHub Actions Job-Level Parallelism

- **Job-level parallelism** uses GitHub Actions native job dependencies (needs:) for parallel execution
- **Setup job** runs first with health checks and cleanup, outputs API credentials
- **Test jobs** (REST, GraphQL, SOAP) run in parallel after setup job completes
- **Manual retry** is available via GitHub Actions "Re-run failed jobs" button
- **Selective execution** via workflow_dispatch allows running individual test suites for debugging
- **No automatic retry** - investigate failures before manually retrying
- **Execution time** reduced from ~90s (sequential) to ~30s (parallel)
- **Independent jobs** use unique namespaces to avoid conflicts
