# Requirements Document

## Introduction

This document defines the requirements for a SAR (Serverless Application Repository) test pipeline that validates published MockNest Serverless applications by deploying them from SAR, retrieving deployment outputs, and executing health checks to ensure the application functions correctly.

The pipeline addresses the critical need to validate that SAR-published applications work correctly for end users before making them publicly available. It ensures that the deployment process, API key retrieval, and basic functionality all work as expected in a real AWS environment.

## Glossary

- **SAR**: AWS Serverless Application Repository - AWS service for publishing and deploying serverless applications
- **Pipeline**: GitHub Actions workflow that automates SAR deployment testing
- **Stack**: AWS CloudFormation stack created by SAR deployment
- **API_Gateway**: AWS API Gateway service that exposes MockNest endpoints
- **API_Key**: AWS API Gateway API key used for authentication
- **Health_Check**: HTTP endpoint that validates service availability and functionality
- **Secrets_Manager**: AWS service for securely storing sensitive values like API keys
- **CloudFormation**: AWS infrastructure-as-code service used by SAR for deployments
- **OIDC**: OpenID Connect - authentication protocol used for GitHub Actions AWS access

## Requirements

### Requirement 1: Deploy MockNest from SAR

**User Story:** As a CI/CD pipeline, I want to deploy MockNest Serverless from the AWS Serverless Application Repository, so that I can validate the published application works correctly for end users.

#### Acceptance Criteria

1. WHEN the pipeline executes, THE Pipeline SHALL deploy MockNest Serverless from the SAR URL https://serverlessrepo.aws.amazon.com/applications/eu-west-1/021259937026/MockNest-Serverless
2. THE Pipeline SHALL use AWS CloudFormation to create the SAR application stack
3. THE Pipeline SHALL provide required deployment parameters including DeploymentName
4. WHEN deployment completes successfully, THE Pipeline SHALL capture the CloudFormation stack name
5. IF deployment fails, THEN THE Pipeline SHALL fail with a descriptive error message
6. THE Pipeline SHALL wait for stack creation to reach CREATE_COMPLETE status before proceeding

### Requirement 2: Retrieve Deployment Outputs

**User Story:** As a CI/CD pipeline, I want to retrieve deployment outputs from the CloudFormation stack, so that I can obtain the API Gateway URL and API key name needed for testing.

#### Acceptance Criteria

1. WHEN the stack reaches CREATE_COMPLETE status, THE Pipeline SHALL query CloudFormation stack outputs
2. THE Pipeline SHALL retrieve the MockNestApiUrl output value
3. THE Pipeline SHALL retrieve the MockNestApiKey output value
4. THE Pipeline SHALL store the API Gateway URL as an environment variable for subsequent steps
5. THE Pipeline SHALL store the API key name as an environment variable for subsequent steps
6. IF any required output is missing, THEN THE Pipeline SHALL fail with a descriptive error message

### Requirement 3: Retrieve API Key Value Securely

**User Story:** As a CI/CD pipeline, I want to securely retrieve the API key value from AWS, so that I can authenticate health check requests without exposing credentials.

#### Acceptance Criteria

1. WHEN the API key name is available, THE Pipeline SHALL use AWS CLI to retrieve the API key value
2. THE Pipeline SHALL execute `aws apigateway get-api-key` with the API key ID and `--include-value` flag
3. THE Pipeline SHALL extract the API key value from the AWS CLI response
4. THE Pipeline SHALL store the API key value as a masked secret environment variable
5. THE Pipeline SHALL NOT log or print the API key value in pipeline output
6. IF API key retrieval fails, THEN THE Pipeline SHALL fail with a descriptive error message

### Requirement 4: Execute Health Checks

**User Story:** As a CI/CD pipeline, I want to execute health checks against the deployed MockNest instance, so that I can validate the application is functional before considering deployment successful.

#### Acceptance Criteria

1. WHEN the API Gateway URL and API key are available, THE Pipeline SHALL call the admin health check endpoint at `{MockNestApiUrl}/__admin/health`
2. THE Pipeline SHALL include the API key in the `x-api-key` HTTP header
3. WHEN the health check returns HTTP 200, THE Pipeline SHALL verify the response contains `"status": "healthy"`
4. THE Pipeline SHALL call the AI generation health check endpoint at `{MockNestApiUrl}/ai/generation/health`
5. WHEN the AI health check returns HTTP 200, THE Pipeline SHALL verify the response contains `"status": "healthy"`
6. IF any health check fails or returns non-200 status, THEN THE Pipeline SHALL fail with a descriptive error message including the response details
7. THE Pipeline SHALL execute health checks with a timeout of 30 seconds per request
8. THE Pipeline SHALL retry failed health checks up to 3 times with exponential backoff

### Requirement 5: Clean Up Test Resources

**User Story:** As a CI/CD pipeline, I want to clean up test resources after validation, so that I don't incur unnecessary AWS costs from test deployments.

#### Acceptance Criteria

1. WHEN health checks complete successfully, THE Pipeline SHALL delete the CloudFormation stack
2. THE Pipeline SHALL delete the CloudFormation stack even if health checks fail
3. THE Pipeline SHALL wait for stack deletion to reach DELETE_COMPLETE status
4. THE Pipeline SHALL verify the S3 bucket created by the stack is deleted
5. IF stack deletion fails, THEN THE Pipeline SHALL log a warning but not fail the pipeline
6. THE Pipeline SHALL execute cleanup steps in a finally block to ensure they run regardless of previous step outcomes

### Requirement 6: Secure Credential Management

**User Story:** As a security-conscious team, I want API credentials to be handled securely throughout the pipeline, so that sensitive values are never exposed in logs or artifacts.

#### Acceptance Criteria

1. THE Pipeline SHALL use GitHub Actions OIDC authentication to access AWS services
2. THE Pipeline SHALL NOT store API keys in GitHub secrets or repository variables
3. THE Pipeline SHALL mark API key environment variables as secrets using GitHub Actions masking
4. THE Pipeline SHALL NOT include API key values in any log output or error messages
5. WHEN logging HTTP requests, THE Pipeline SHALL redact the `x-api-key` header value
6. THE Pipeline SHALL use temporary AWS credentials with minimum required permissions
7. THE Pipeline SHALL configure AWS credentials to expire within 1 hour

### Requirement 7: Pipeline Execution Triggers

**User Story:** As a developer, I want the SAR test pipeline to run automatically after SAR publication and support manual triggering with region selection, so that I can validate deployments without manual intervention and test in different AWS regions.

#### Acceptance Criteria

1. WHEN the SAR deploy pipeline completes successfully, THE Pipeline SHALL execute automatically
2. WHEN triggered from the SAR deploy pipeline, THE Pipeline SHALL receive and use the same AWS region where SAR was deployed
3. THE Pipeline SHALL support manual triggering via workflow_dispatch
4. WHEN manually triggered, THE Pipeline SHALL provide a dropdown parameter for AWS region selection
5. THE Pipeline SHALL include the following Nova Pro supported regions in the region dropdown: us-east-1, us-west-2, eu-west-1, eu-central-1, ap-southeast-1, ap-northeast-1
6. THE Pipeline SHALL execute in the eu-west-1 region by default when no region is specified
7. THE Pipeline SHALL use the published SAR application version from the triggering event

### Requirement 8: Pipeline Reporting

**User Story:** As a developer, I want clear pipeline execution reports, so that I can quickly understand test results and diagnose failures.

#### Acceptance Criteria

1. WHEN the pipeline completes, THE Pipeline SHALL generate a summary report in GitHub Actions summary
2. THE Pipeline SHALL include deployment status, health check results, and cleanup status in the report
3. THE Pipeline SHALL include the deployed API Gateway URL in the report
4. THE Pipeline SHALL NOT include API key values in the report
5. WHEN health checks fail, THE Pipeline SHALL include response status codes and error messages in the report
6. THE Pipeline SHALL include execution duration for each major step
7. THE Pipeline SHALL use emoji indicators for success/failure status in the report
