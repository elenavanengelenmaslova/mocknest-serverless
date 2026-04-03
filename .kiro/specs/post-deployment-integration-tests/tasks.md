# Implementation Tasks

## Overview

This document outlines the implementation tasks for the post-deployment integration test workflow. Tasks are organized into incremental iterations that can be committed, tested, and merged separately for easier code review and validation.

## Incremental Development Strategy

Each iteration builds on the previous one and can be merged independently:
- **Iteration 1**: Health checks + manual workflow (minimal viable test)
- **Iteration 2**: Delete mappings + REST generation/import + mock invocation tests
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

- [ ] 11. Implement mapping cleanup test
- [ ] 11. Implement mapping cleanup test
  - [ ] 11.1 Implement `test_delete_all_mappings()` function for DELETE /__admin/mappings
  - [ ] 11.2 Add HTTP status code validation (expect 200)
  - [ ] 11.3 Add clear success/failure output messages

- [ ] 12. Implement REST/OpenAPI mock generation test
  - [ ] 12.1 Create REST generation request payload matching Postman collection
  - [ ] 12.2 Use Petstore API specification URL (https://petstore3.swagger.io/api/v3/openapi.json)
  - [ ] 12.3 Implement `test_rest_generation()` function for POST /ai/generation/from-spec
  - [ ] 12.4 Add HTTP status code validation (expect 200)
  - [ ] 12.5 Add response body validation (expect "mappings" array)
  - [ ] 12.6 Store generated mappings in variable for import test
  - [ ] 12.7 Add clear success/failure output messages

- [ ] 13. Implement REST mock import test
  - [ ] 13.1 Implement `test_rest_import()` function for POST /__admin/mappings/import
  - [ ] 13.2 Use mappings generated from REST generation test
  - [ ] 13.3 Add HTTP status code validation (expect 200)
  - [ ] 13.4 Add clear success/failure output messages

- [ ] 14. Implement REST mock invocation tests
  - [ ] 14.1 Identify 2 endpoints from Petstore API to test (e.g., GET /pet/{petId}, POST /pet)
  - [ ] 14.2 Implement `test_rest_mock_invocation()` function
  - [ ] 14.3 Call first generated mock endpoint and verify response
  - [ ] 14.4 Call second generated mock endpoint and verify response
  - [ ] 14.5 Add clear success/failure output messages

- [ ] 15. Update main() function to include new tests
  - [ ] 15.1 Add delete mappings test to main()
  - [ ] 15.2 Add REST generation test to main()
  - [ ] 15.3 Add REST import test to main()
  - [ ] 15.4 Add REST mock invocation test to main()

- [ ] 16. Test workflow from feature branch
  - [ ] 16.1 Create feature branch and push changes
  - [ ] 16.2 Manually trigger workflow from GitHub Actions UI
  - [ ] 16.3 Verify all tests pass in workflow execution
  - [ ] 16.4 Verify generated mocks work correctly

- [ ] 17. Create PR and merge Iteration 2
  - [ ] 17.1 Create pull request with detailed description
  - [ ] 17.2 Wait for CodeRabbit review
  - [ ] 17.3 Address review comments if any
  - [ ] 17.4 Merge to main branch after approval

### Iteration 3: GraphQL Generation/Import + Mock Invocation (Third PR)

- [ ] 18. Implement GraphQL mock generation test
  - [ ] 18.1 Create GraphQL generation request payload matching Postman collection
  - [ ] 18.2 Use Countries GraphQL API URL (https://countries.trevorblades.com/graphql)
  - [ ] 18.3 Implement `test_graphql_generation()` function for POST /ai/generation/from-spec
  - [ ] 18.4 Add HTTP status code validation (expect 200)
  - [ ] 18.5 Add response body validation (expect "mappings" array)
  - [ ] 18.6 Store generated mappings in variable for import test
  - [ ] 18.7 Add clear success/failure output messages

- [ ] 19. Implement GraphQL mock import test
  - [ ] 19.1 Implement `test_graphql_import()` function for POST /__admin/mappings/import
  - [ ] 19.2 Use mappings generated from GraphQL generation test
  - [ ] 19.3 Add HTTP status code validation (expect 200)
  - [ ] 19.4 Add clear success/failure output messages

- [ ] 20. Implement GraphQL mock invocation tests
  - [ ] 20.1 Identify 2 GraphQL queries to test from Countries API
  - [ ] 20.2 Implement `test_graphql_mock_invocation()` function
  - [ ] 20.3 Call first generated GraphQL mock and verify response
  - [ ] 20.4 Call second generated GraphQL mock and verify response
  - [ ] 20.5 Add clear success/failure output messages

- [ ] 21. Update main() function to include GraphQL tests
  - [ ] 21.1 Add GraphQL generation test to main()
  - [ ] 21.2 Add GraphQL import test to main()
  - [ ] 21.3 Add GraphQL mock invocation test to main()

- [ ] 22. Test workflow from feature branch
  - [ ] 22.1 Create feature branch and push changes
  - [ ] 22.2 Manually trigger workflow from GitHub Actions UI
  - [ ] 22.3 Verify all tests pass in workflow execution
  - [ ] 22.4 Verify GraphQL mocks work correctly

- [ ] 23. Create PR and merge Iteration 3
  - [ ] 23.1 Create pull request with detailed description
  - [ ] 23.2 Wait for CodeRabbit review
  - [ ] 23.3 Address review comments if any
  - [ ] 23.4 Merge to main branch after approval

### Iteration 4: SOAP/WSDL Generation/Import + Mock Invocation (Fourth PR)

- [ ] 24. Implement SOAP/WSDL mock generation test
  - [ ] 24.1 Create SOAP generation request payload matching Postman collection
  - [ ] 24.2 Use Calculator WSDL URL (http://www.dneonline.com/calculator.asmx?WSDL)
  - [ ] 24.3 Implement `test_soap_generation()` function for POST /ai/generation/from-spec
  - [ ] 24.4 Add HTTP status code validation (expect 200)
  - [ ] 24.5 Add response body validation (expect "mappings" array)
  - [ ] 24.6 Store generated mappings in variable for import test
  - [ ] 24.7 Add clear success/failure output messages

- [ ] 25. Implement SOAP mock import test
  - [ ] 25.1 Implement `test_soap_import()` function for POST /__admin/mappings/import
  - [ ] 25.2 Use mappings generated from SOAP generation test
  - [ ] 25.3 Add HTTP status code validation (expect 200)
  - [ ] 25.4 Add clear success/failure output messages

- [ ] 26. Implement SOAP mock invocation tests
  - [ ] 26.1 Identify 2 SOAP operations to test from Calculator WSDL (e.g., Add, Multiply)
  - [ ] 26.2 Implement `test_soap_mock_invocation()` function
  - [ ] 26.3 Call first generated SOAP mock and verify response
  - [ ] 26.4 Call second generated SOAP mock and verify response
  - [ ] 26.5 Add clear success/failure output messages

- [ ] 27. Update main() function to include SOAP tests
  - [ ] 27.1 Add SOAP generation test to main()
  - [ ] 27.2 Add SOAP import test to main()
  - [ ] 27.3 Add SOAP mock invocation test to main()

- [ ] 28. Test workflow from feature branch
  - [ ] 28.1 Create feature branch and push changes
  - [ ] 28.2 Manually trigger workflow from GitHub Actions UI
  - [ ] 28.3 Verify all tests pass in workflow execution
  - [ ] 28.4 Verify SOAP mocks work correctly

- [ ] 29. Create PR and merge Iteration 4
  - [ ] 29.1 Create pull request with detailed description
  - [ ] 29.2 Wait for CodeRabbit review
  - [ ] 29.3 Address review comments if any
  - [ ] 29.4 Merge to main branch after approval

### Iteration 5: Automatic Workflow Trigger + Documentation (Fifth PR)

- [ ] 30. Update workflow to automatic trigger
  - [ ] 30.1 Add workflow_run trigger for "CICD - Main Branch AWS" and "CD - Deploy On Demand"
  - [ ] 30.2 Add conditional execution (only run if triggering workflow succeeded)
  - [ ] 30.3 Keep workflow_dispatch trigger for manual testing
  - [ ] 30.4 Test automatic trigger by deploying to test environment

- [ ] 31. Update documentation
  - [ ] 31.1 Add section to README.md about post-deployment integration tests
  - [ ] 31.2 Document how to run tests locally
  - [ ] 31.3 Document workflow trigger conditions (automatic and manual)
  - [ ] 31.4 Document troubleshooting steps for test failures
  - [ ] 31.5 Add link to workflow file in documentation

- [ ] 32. Verify Postman collection alignment
  - [ ] 32.1 Compare test script payloads with Postman collection requests
  - [ ] 32.2 Verify REST generation payload matches Postman "Generate from OpenAPI" request
  - [ ] 32.3 Verify GraphQL generation payload matches Postman "Generate from GraphQL" request
  - [ ] 32.4 Verify SOAP generation payload matches Postman "Generate from WSDL" request
  - [ ] 32.5 Update test script if any mismatches are found

- [ ] 33. Final validation
  - [ ] 33.1 Test automatic workflow trigger with actual deployment
  - [ ] 33.2 Verify workflow runs successfully after deployment to main
  - [ ] 33.3 Review all code changes for quality and completeness

- [ ] 34. Create PR and merge Iteration 5
  - [ ] 34.1 Create pull request with detailed description
  - [ ] 34.2 Wait for CodeRabbit review
  - [ ] 34.3 Address review comments if any
  - [ ] 34.4 Merge to main branch after approval

## Notes

- All curl commands must be in the test script, not in the workflow YAML
- Test payloads must match the Postman collection exactly
- API keys must be masked immediately after retrieval
- 30-second timeout is required for AI generation calls (can take up to 20 seconds)
- Script must follow bash best practices (set -e, set -o pipefail)
- Script must return exit code 0 on success, non-zero on failure
- All tests must pass before proceeding to next task group
