# Implementation Plan: SAR Test Pipeline

## Overview

This implementation plan creates a GitHub Actions workflow that validates published MockNest Serverless applications by deploying them from the AWS Serverless Application Repository (SAR), executing comprehensive health checks, and cleaning up test resources. The pipeline will serve as both an automated quality gate (triggered after SAR publication) and a manual testing tool (triggered on-demand with region selection).

The implementation follows a sequential approach: first creating the workflow structure and triggers, then implementing each major component (deployment, output retrieval, health checks, cleanup, reporting), and finally integrating with existing workflows.

## Tasks

- [x] 1. Create GitHub Actions workflow file with triggers and authentication
  - Create `.github/workflows/sar-test-pipeline.yml` with workflow_call and workflow_dispatch triggers
  - Configure workflow_call inputs: aws-region (required), version (required)
  - Configure workflow_dispatch inputs: aws-region (choice dropdown with regions), version (optional)
  - Set default region to 'eu-west-1' for manual triggers
  - Include region options: us-east-1, us-west-2, eu-west-1, eu-central-1, ap-southeast-1, ap-northeast-1
  - Configure AWS OIDC authentication with id-token write permission
  - Set up AWS credentials using aws-actions/configure-aws-credentials action
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 6.1, 6.6, 6.7_

- [x] 2. Implement SAR deployment step
  - [x] 2.1 Create deployment job with ubuntu-latest runner
    - Generate unique stack name using pattern `mocknest-sar-test-${GITHUB_RUN_ID}`
    - Set SAR application URL as environment variable
    - Capture deployment start timestamp
    - _Requirements: 1.1, 1.3_
  
  - [x] 2.2 Implement SAR deployment using AWS CLI
    - Use `aws serverlessrepo create-cloud-formation-change-set` to create change set from SAR
    - Execute change set using `aws cloudformation execute-change-set`
    - Wait for stack creation using `aws cloudformation wait stack-create-complete` with 15-minute timeout
    - Capture and store stack name in GITHUB_ENV for subsequent steps
    - _Requirements: 1.1, 1.2, 1.4, 1.6_
  
  - [x] 2.3 Add deployment error handling
    - Capture CloudFormation stack events on deployment failure
    - Include stack events in error output for debugging
    - Fail pipeline with descriptive error message including CloudFormation error details
    - Calculate and log deployment duration
    - _Requirements: 1.5_

- [x] 3. Implement stack output retrieval step
  - [x] 3.1 Query CloudFormation stack outputs
    - Use `aws cloudformation describe-stacks` to retrieve stack outputs
    - Extract MockNestApiUrl output value using --query parameter
    - Extract MockNestApiKey output value (API key ID, not value)
    - Extract MockStorageBucket output value for cleanup verification
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [x] 3.2 Store outputs as environment variables
    - Store API_URL in GITHUB_ENV for health check steps
    - Store API_KEY_ID in GITHUB_ENV for API key retrieval step
    - Store BUCKET_NAME in GITHUB_ENV for cleanup verification
    - _Requirements: 2.4, 2.5_
  
  - [x] 3.3 Validate required outputs
    - Check that MockNestApiUrl is not empty and starts with https://
    - Check that MockNestApiKey is not empty and is alphanumeric
    - Fail pipeline with descriptive error message if any required output is missing
    - Include which specific output is missing in error message
    - _Requirements: 2.6_

- [x] 4. Implement secure API key retrieval step
  - [x] 4.1 Retrieve API key value from AWS API Gateway
    - Use `aws apigateway get-api-key` with API_KEY_ID and --include-value flag
    - Extract API key value from response using --query parameter
    - _Requirements: 3.1, 3.2, 3.3_
  
  - [x] 4.2 Mask and store API key securely
    - Use GitHub Actions `::add-mask::` to prevent API key from appearing in logs
    - Store API key value in GITHUB_ENV as API_KEY_VALUE
    - Never store API key in GitHub outputs or secrets
    - _Requirements: 3.4, 6.2, 6.3_
  
  - [x] 4.3 Add API key retrieval error handling
    - Fail pipeline if API key retrieval fails
    - Use generic error message without exposing API key ID
    - Ensure error messages never contain API key values
    - _Requirements: 3.5, 3.6, 6.4_

- [x] 5. Implement health check step with retry logic
  - [x] 5.1 Create health check function with retry and backoff
    - Implement bash function for HTTP requests with curl
    - Add retry logic: maximum 3 attempts per health check
    - Implement exponential backoff delays: 5s, 10s, 20s between retries
    - Retry on HTTP 5xx errors and timeouts
    - Do not retry on HTTP 4xx errors (authentication/authorization issues)
    - Set 30-second timeout per request using curl --max-time
    - _Requirements: 4.7, 4.8_
  
  - [x] 5.2 Implement admin health check
    - Call `{API_URL}/__admin/health` endpoint using curl
    - Include `x-api-key` header with API_KEY_VALUE
    - Use curl -f flag to fail on HTTP errors
    - Capture HTTP status code and response body
    - Verify response contains `"status": "healthy"` on HTTP 200
    - _Requirements: 4.1, 4.2, 4.3_
  
  - [x] 5.3 Implement AI generation health check
    - Call `{API_URL}/ai/generation/health` endpoint using curl
    - Include `x-api-key` header with API_KEY_VALUE
    - Use curl -f flag to fail on HTTP errors
    - Capture HTTP status code and response body
    - Verify response contains `"status": "healthy"` on HTTP 200
    - _Requirements: 4.4, 4.5_
  
  - [x] 5.4 Add health check error handling and logging
    - Capture and log HTTP status codes and response bodies on failure
    - Redact `x-api-key` header from any logged requests
    - Fail pipeline with descriptive error message including response details
    - Calculate and log health check duration
    - Include number of retry attempts in error messages
    - _Requirements: 4.6, 6.5_

- [x] 6. Implement resource cleanup step
  - [x] 6.1 Create cleanup job with always() condition
    - Configure cleanup to run even if previous steps fail
    - Use `if: always()` condition on cleanup step
    - Capture cleanup start timestamp
    - _Requirements: 5.2, 5.6_
  
  - [x] 6.2 Implement CloudFormation stack deletion
    - Use `aws cloudformation delete-stack` with stack name
    - Wait for deletion using `aws cloudformation wait stack-delete-complete`
    - Set 10-minute timeout for deletion wait
    - _Requirements: 5.1, 5.3_
  
  - [x] 6.3 Verify S3 bucket deletion
    - Query stack outputs for MockStorageBucket name before deletion
    - After stack deletion, check if bucket still exists using `aws s3 ls`
    - Log warning if bucket still exists after stack deletion
    - _Requirements: 5.4_
  
  - [x] 6.4 Add graceful cleanup error handling
    - Log cleanup failures as warnings, not errors
    - Continue pipeline execution to generate report even if cleanup fails
    - Include cleanup status in final report
    - Calculate and log cleanup duration
    - _Requirements: 5.5_

- [x] 7. Implement report generation step
  - [x] 7.1 Create GitHub Actions summary report
    - Use `echo >> $GITHUB_STEP_SUMMARY` to generate markdown report
    - Include report title: "🧪 SAR Test Pipeline Results"
    - Add deployment information section with region and version
    - Use emoji indicators for success (✅) and failure (❌) status
    - _Requirements: 8.1, 8.7_
  
  - [x] 7.2 Add deployment status section to report
    - Include stack name and AWS region
    - Include deployment duration in seconds or minutes
    - Include deployment status (success/failure)
    - _Requirements: 8.2_
  
  - [x] 7.3 Add health check results section to report
    - Include admin API health check status and response time
    - Include AI API health check status and response time
    - Include number of retry attempts if applicable
    - Include HTTP status codes and error messages for failures
    - _Requirements: 8.2, 8.5_
  
  - [x] 7.4 Add cleanup status section to report
    - Include stack deletion status (complete/failed/in-progress)
    - Include S3 bucket deletion verification status
    - Include cleanup duration
    - _Requirements: 8.2_
  
  - [x] 7.5 Add API Gateway URL section to report
    - Include deployed API Gateway endpoint URL for manual testing reference
    - Never include API key values or API key IDs in report
    - _Requirements: 8.3, 8.4_
  
  - [x] 7.6 Add execution duration tracking
    - Calculate total pipeline execution time
    - Include duration for each major step (deployment, health checks, cleanup)
    - Format durations in human-readable format (minutes and seconds)
    - _Requirements: 8.6_

- [x] 8. Integrate with existing SAR publish workflow
  - [x] 8.1 Update sar-beta-test.yml to call test pipeline
    - Add test job that depends on publish job
    - Use `uses: ./.github/workflows/sar-test-pipeline.yml` to call test pipeline
    - Pass aws-region input from publish workflow
    - Pass version input from publish workflow
    - Pass AWS_ACCOUNT_ID secret to test pipeline
    - _Requirements: 7.1, 7.2, 7.7_
  
  - [x] 8.2 Update summary job to include test results
    - Make summary job depend on both publish and test jobs
    - Include test pipeline status in summary report
    - Add link to test pipeline run for detailed results
    - _Requirements: 8.1_

- [x] 9. Final checkpoint - Validate complete pipeline
  - Test automatic trigger from SAR publish workflow
  - Test manual trigger with different regions
  - Verify API key masking in logs
  - Verify cleanup executes on failure scenarios
  - Verify report generation for both success and failure cases
  - Ensure no secrets appear in GitHub Actions logs or summary

## Notes

- All bash scripts should use `set -e` to fail fast on errors
- Use `set -o pipefail` to catch errors in piped commands
- API key masking must be applied before any operations that might log the value
- Cleanup step must use `if: always()` to ensure it runs even on failure
- Health check retry logic should distinguish between retryable (5xx, timeout) and non-retryable (4xx) errors
- All durations should be captured using `date +%s` for start/end timestamps
- CloudFormation wait commands should have explicit timeouts to prevent indefinite waiting
- Error messages should be descriptive but never expose sensitive values (API keys, credentials)
