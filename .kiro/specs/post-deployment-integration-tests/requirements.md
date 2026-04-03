# Requirements Document

## Introduction

This document defines the requirements for a post-deployment integration test workflow that validates deployed MockNest Serverless applications in AWS. The workflow executes after successful deployments from both the main branch workflow (main-aws.yml) and on-demand deployments (deploy-on-demand.yml) to ensure the deployed application is functioning correctly with comprehensive API testing.

The integration tests validate the complete deployment including API Gateway configuration, Lambda function execution, S3 storage connectivity, and AI generation capabilities (when enabled). This provides confidence that the deployment is production-ready before users interact with it.

## Glossary

- **Integration_Test_Workflow**: GitHub Actions workflow that executes post-deployment validation tests
- **Deployment_Workflow**: GitHub Actions workflow that deploys MockNest Serverless to AWS (main-aws.yml or deploy-on-demand.yml)
- **CloudFormation_Stack**: AWS CloudFormation stack containing the deployed MockNest Serverless resources
- **API_Gateway**: AWS API Gateway endpoint that provides HTTP access to MockNest Serverless
- **API_Key**: AWS API Gateway API key used for authentication
- **Stack_Outputs**: CloudFormation stack outputs containing deployment information (API_URL, API_KEY_ID, BUCKET_NAME, STACK_NAME)
- **Health_Check**: HTTP request to validate service availability and configuration
- **Mock_Generation**: AI-assisted creation of WireMock mappings from API specifications
- **Mapping_Import**: Process of loading WireMock mappings into the runtime
- **Test_Timeout**: Maximum duration allowed for a single test operation

## Requirements

### Requirement 1: Workflow Triggering

**User Story:** As a DevOps engineer, I want integration tests to run automatically after successful deployments, so that I can verify the deployment is working correctly before users access it.

#### Acceptance Criteria

1. WHEN the main-aws.yml workflow completes successfully, THE Integration_Test_Workflow SHALL execute automatically
2. WHEN the deploy-on-demand.yml workflow completes successfully, THE Integration_Test_Workflow SHALL execute automatically
3. THE Integration_Test_Workflow SHALL NOT execute if the Deployment_Workflow fails
4. THE Integration_Test_Workflow SHALL NOT execute for the sar-test-pipeline.yml workflow

### Requirement 2: Stack Output Retrieval

**User Story:** As a test automation engineer, I want to retrieve deployment information from CloudFormation stack outputs, so that I can configure test requests with the correct endpoints and credentials.

#### Acceptance Criteria

1. WHEN the Integration_Test_Workflow starts, THE Integration_Test_Workflow SHALL retrieve the API_URL from CloudFormation_Stack outputs
2. WHEN the Integration_Test_Workflow starts, THE Integration_Test_Workflow SHALL retrieve the API_KEY_ID from CloudFormation_Stack outputs
3. WHEN the Integration_Test_Workflow starts, THE Integration_Test_Workflow SHALL retrieve the BUCKET_NAME from CloudFormation_Stack outputs
4. WHEN the Integration_Test_Workflow starts, THE Integration_Test_Workflow SHALL retrieve the STACK_NAME from CloudFormation_Stack outputs
5. IF any required stack output is missing, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message
6. THE Integration_Test_Workflow SHALL validate that API_URL starts with "https://"
7. THE Integration_Test_Workflow SHALL remove trailing slashes from API_URL

### Requirement 3: API Key Retrieval

**User Story:** As a test automation engineer, I want to retrieve the actual API key value from API Gateway, so that I can authenticate test requests.

#### Acceptance Criteria

1. WHEN the API_KEY_ID is retrieved, THE Integration_Test_Workflow SHALL retrieve the API_Key value from API_Gateway using the API_KEY_ID
2. WHEN the API_Key value is retrieved, THE Integration_Test_Workflow SHALL mask the API_Key value in GitHub Actions logs
3. IF the API_Key retrieval fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message
4. THE Integration_Test_Workflow SHALL validate that the API_Key value is not empty

### Requirement 4: Runtime Health Check

**User Story:** As a developer, I want to verify the runtime is healthy, so that I know the core MockNest service is operational.

#### Acceptance Criteria

1. WHEN executing health checks, THE Integration_Test_Workflow SHALL send a GET request to "/__admin/health"
2. WHEN sending the health check request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN the health check succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
4. WHEN the health check succeeds, THE Integration_Test_Workflow SHALL verify the response contains '"status": "healthy"'
5. IF the health check fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 5: AI Health Check

**User Story:** As a developer, I want to verify the AI generation service is healthy, so that I know AI-assisted mock generation is operational.

#### Acceptance Criteria

1. WHEN executing health checks, THE Integration_Test_Workflow SHALL send a GET request to "/ai/generation/health"
2. WHEN sending the AI health check request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN the AI health check succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
4. WHEN the AI health check succeeds, THE Integration_Test_Workflow SHALL verify the response contains '"status": "healthy"'
5. IF the AI health check fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 6: Delete All Mappings

**User Story:** As a test automation engineer, I want to clear all existing mappings before running tests, so that tests start with a clean state.

#### Acceptance Criteria

1. WHEN preparing for integration tests, THE Integration_Test_Workflow SHALL send a DELETE request to "/__admin/mappings"
2. WHEN sending the delete request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN the delete succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
4. IF the delete fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 7: REST/OpenAPI Mock Generation

**User Story:** As a developer, I want to generate mocks from an OpenAPI specification, so that I can verify AI-assisted REST mock generation is working correctly.

#### Acceptance Criteria

1. WHEN testing REST mock generation, THE Integration_Test_Workflow SHALL send a POST request to "/ai/generation/from-spec"
2. WHEN sending the generation request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the generation request, THE Integration_Test_Workflow SHALL include a valid OpenAPI specification URL
4. WHEN sending the generation request, THE Integration_Test_Workflow SHALL set format to "OPENAPI_3"
5. WHEN sending the generation request, THE Integration_Test_Workflow SHALL include a description with generation instructions
6. WHEN the generation succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
7. WHEN the generation succeeds, THE Integration_Test_Workflow SHALL verify the response contains a "mappings" array
8. WHEN the generation request is sent, THE Integration_Test_Workflow SHALL allow up to 30 seconds for the request to complete
9. IF the generation fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 8: REST Mock Import

**User Story:** As a developer, I want to import generated REST mocks, so that I can verify the mock import functionality is working correctly.

#### Acceptance Criteria

1. WHEN REST mocks are generated, THE Integration_Test_Workflow SHALL send a POST request to "/__admin/mappings/import"
2. WHEN sending the import request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the import request, THE Integration_Test_Workflow SHALL include the generated mappings in the request body
4. WHEN the import succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
5. IF the import fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 9: GraphQL Mock Generation

**User Story:** As a developer, I want to generate mocks from a GraphQL schema, so that I can verify AI-assisted GraphQL mock generation is working correctly.

#### Acceptance Criteria

1. WHEN testing GraphQL mock generation, THE Integration_Test_Workflow SHALL send a POST request to "/ai/generation/from-spec"
2. WHEN sending the GraphQL generation request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the GraphQL generation request, THE Integration_Test_Workflow SHALL include a valid GraphQL endpoint URL
4. WHEN sending the GraphQL generation request, THE Integration_Test_Workflow SHALL set format to "GRAPHQL"
5. WHEN sending the GraphQL generation request, THE Integration_Test_Workflow SHALL include a description with generation instructions
6. WHEN the GraphQL generation succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
7. WHEN the GraphQL generation succeeds, THE Integration_Test_Workflow SHALL verify the response contains a "mappings" array
8. WHEN the GraphQL generation request is sent, THE Integration_Test_Workflow SHALL allow up to 30 seconds for the request to complete
9. IF the GraphQL generation fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 10: GraphQL Mock Import

**User Story:** As a developer, I want to import generated GraphQL mocks, so that I can verify GraphQL mock import functionality is working correctly.

#### Acceptance Criteria

1. WHEN GraphQL mocks are generated, THE Integration_Test_Workflow SHALL send a POST request to "/__admin/mappings/import"
2. WHEN sending the GraphQL import request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the GraphQL import request, THE Integration_Test_Workflow SHALL include the generated GraphQL mappings in the request body
4. WHEN the GraphQL import succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
5. IF the GraphQL import fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 11: SOAP/WSDL Mock Generation

**User Story:** As a developer, I want to generate mocks from a WSDL specification, so that I can verify AI-assisted SOAP mock generation is working correctly.

#### Acceptance Criteria

1. WHEN testing SOAP mock generation, THE Integration_Test_Workflow SHALL send a POST request to "/ai/generation/from-spec"
2. WHEN sending the SOAP generation request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the SOAP generation request, THE Integration_Test_Workflow SHALL include a valid WSDL URL
4. WHEN sending the SOAP generation request, THE Integration_Test_Workflow SHALL set format to "WSDL"
5. WHEN sending the SOAP generation request, THE Integration_Test_Workflow SHALL include a description with generation instructions
6. WHEN the SOAP generation succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
7. WHEN the SOAP generation succeeds, THE Integration_Test_Workflow SHALL verify the response contains a "mappings" array
8. WHEN the SOAP generation request is sent, THE Integration_Test_Workflow SHALL allow up to 30 seconds for the request to complete
9. IF the SOAP generation fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 12: SOAP Mock Import

**User Story:** As a developer, I want to import generated SOAP mocks, so that I can verify SOAP mock import functionality is working correctly.

#### Acceptance Criteria

1. WHEN SOAP mocks are generated, THE Integration_Test_Workflow SHALL send a POST request to "/__admin/mappings/import"
2. WHEN sending the SOAP import request, THE Integration_Test_Workflow SHALL include the API_Key in the "x-api-key" header
3. WHEN sending the SOAP import request, THE Integration_Test_Workflow SHALL include the generated SOAP mappings in the request body
4. WHEN the SOAP import succeeds, THE Integration_Test_Workflow SHALL verify the response status is 200
5. IF the SOAP import fails, THEN THE Integration_Test_Workflow SHALL fail with a descriptive error message

### Requirement 13: Test Result Reporting

**User Story:** As a DevOps engineer, I want clear test results in the GitHub Actions summary, so that I can quickly understand what passed or failed.

#### Acceptance Criteria

1. WHEN all tests pass, THE Integration_Test_Workflow SHALL create a GitHub Actions summary with success status
2. WHEN any test fails, THE Integration_Test_Workflow SHALL create a GitHub Actions summary with failure details
3. WHEN creating the summary, THE Integration_Test_Workflow SHALL include the API Gateway URL
4. WHEN creating the summary, THE Integration_Test_Workflow SHALL include the test execution duration
5. WHEN creating the summary, THE Integration_Test_Workflow SHALL list all executed tests with pass/fail status
6. THE Integration_Test_Workflow SHALL NOT include API_Key values in the summary

### Requirement 14: Error Handling and Timeouts

**User Story:** As a test automation engineer, I want appropriate timeouts and error handling, so that tests don't hang indefinitely and provide clear failure information.

#### Acceptance Criteria

1. WHEN sending any HTTP request, THE Integration_Test_Workflow SHALL set a Test_Timeout of 30 seconds
2. IF a request times out, THEN THE Integration_Test_Workflow SHALL fail with a timeout error message
3. IF a request returns a non-success HTTP status, THEN THE Integration_Test_Workflow SHALL fail with the HTTP status code and response body
4. WHEN any test fails, THE Integration_Test_Workflow SHALL stop execution and report the failure
5. THE Integration_Test_Workflow SHALL capture and log HTTP response bodies for failed requests

### Requirement 15: Security and Credential Management

**User Story:** As a security engineer, I want API keys to be properly masked in logs, so that credentials are not exposed in GitHub Actions output.

#### Acceptance Criteria

1. WHEN the API_Key value is retrieved, THE Integration_Test_Workflow SHALL immediately mask it using GitHub Actions masking
2. THE Integration_Test_Workflow SHALL NOT log the API_Key value in plain text
3. THE Integration_Test_Workflow SHALL NOT include the API_Key value in error messages
4. THE Integration_Test_Workflow SHALL NOT include the API_Key value in the GitHub Actions summary
5. WHEN logging HTTP requests, THE Integration_Test_Workflow SHALL mask the "x-api-key" header value

### Requirement 16: Script-Based Test Execution

**User Story:** As a developer, I want test logic to be in a reusable shell script rather than embedded in the workflow YAML, so that tests can be maintained independently and run locally for debugging.

#### Acceptance Criteria

1. THE Integration_Test_Workflow SHALL execute tests using a shell script located at `scripts/post-deploy-test.sh`
2. THE shell script SHALL accept API_URL and API_KEY as command-line arguments or environment variables
3. THE shell script SHALL contain all curl commands for executing integration tests
4. THE curl command payloads in the shell script SHALL match the payloads defined in the Postman collection (`docs/postman/AWS MockNest Serverless.postman_collection.json`)
5. THE GitHub Actions workflow YAML SHALL NOT contain inline curl commands
6. THE shell script SHALL be executable and follow bash best practices (set -e, set -o pipefail)
7. THE shell script SHALL return exit code 0 on success and non-zero on failure
8. THE shell script SHALL provide clear output messages indicating which test is being executed
