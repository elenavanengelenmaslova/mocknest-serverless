# Requirements Document

## Introduction

MockNest Serverless currently protects all API Gateway endpoints with API key authentication. This feature adds AWS IAM (SigV4) as a supported alternative authentication mode, selectable at deployment time. One authentication mode applies to the entire deployment. API key authentication remains the default so existing deployments are unaffected. The SAR release validation pipeline is extended to validate both modes independently, with clear result reporting and cleanup after each run. For this feature, SAR release validation scope is limited to the existing health endpoint checks and does not include multi-region validation.

---

## Glossary

- **Auth_Mode**: The authentication mechanism applied to all API Gateway endpoints in a deployment. Supported values: `API_KEY` (default) and `IAM`.
- **API_Key_Mode**: The existing authentication mode where callers supply an `x-api-key` header, enforced via API Gateway API keys and usage plans.
- **IAM_Mode**: An alternative authentication mode where callers sign requests using AWS Signature Version 4 with an IAM identity that has `execute-api:Invoke` permission.
- **Deployer**: A person or automated process that deploys MockNest Serverless into an AWS account.
- **Caller**: A person, CI system, or application that sends HTTP requests to the deployed API Gateway endpoints.
- **SAR_Validation_Pipeline**: The release validation workflow that deploys a published SAR release, runs health checks, and cleans up resources.
- **Validation_Run**: A single execution of the SAR_Validation_Pipeline for one Auth_Mode.
- **Test_Stack**: The temporary CloudFormation stack created during a Validation_Run.

---

## Requirements

### Requirement 1: Auth Mode Selection at Deployment Time

**User Story:** As a deployer, I want to select the authentication mode when deploying MockNest Serverless, so that I can choose API key or IAM authentication to match my environment's access control requirements.

#### Acceptance Criteria

1. THE SAM_Template SHALL expose a parameter named `AuthMode` that accepts exactly two values: `API_KEY` and `IAM`.
2. THE SAM_Template SHALL set the default value of `AuthMode` to `API_KEY`.
3. WHEN `AuthMode` is set to `API_KEY`, THE SAM_Template SHALL configure all API Gateway endpoints with API key authentication.
4. WHEN `AuthMode` is set to `IAM`, THE SAM_Template SHALL configure all API Gateway endpoints with AWS IAM authentication.
5. THE SAM_Template SHALL apply the selected `AuthMode` to all API Gateway endpoints in the deployment, with no endpoint using a different auth mode than the others.

---

### Requirement 2: API Key Mode Behavior

**User Story:** As an existing deployer using API key authentication, I want my deployment to continue working without any changes after this feature is released, so that I am not affected by the introduction of IAM mode.

#### Acceptance Criteria

1. WHEN `AuthMode` is not specified during deployment, THE deployment SHALL behave identically to a deployment where `AuthMode` is explicitly set to `API_KEY`.
2. WHEN `AuthMode` is `API_KEY`, THE API_Gateway SHALL reject requests that do not include a valid `x-api-key` header, returning HTTP 403.
3. WHEN `AuthMode` is `API_KEY`, THE API_Gateway SHALL accept requests that include a valid `x-api-key` header and SHALL forward them to the Lambda function.
4. WHEN `AuthMode` is `API_KEY`, THE SAM_Template SHALL create an API key resource, a usage plan resource, and associate them with the deployed API stage.
5. WHEN `AuthMode` is `API_KEY`, THE deployment SHALL continue to expose the existing CloudFormation outputs required by current API-key-based workflows.

---

### Requirement 3: IAM Mode Behavior

**User Story:** As a deployer who operates in an AWS environment where IAM-based access control is required, I want to select IAM authentication when deploying MockNest Serverless, so that callers can authenticate using their IAM identities without needing API keys.

#### Acceptance Criteria

1. WHEN `AuthMode` is `IAM`, THE API_Gateway SHALL reject requests that are not signed with AWS Signature Version 4, returning HTTP 403.
2. WHEN `AuthMode` is `IAM`, THE API_Gateway SHALL reject requests signed with an IAM identity that does not have `execute-api:Invoke` permission on the deployed API, returning HTTP 403.
3. WHEN `AuthMode` is `IAM`, THE API_Gateway SHALL accept requests signed with an IAM identity that has `execute-api:Invoke` permission on the deployed API, and SHALL forward them to the Lambda function.
4. WHEN `AuthMode` is `IAM`, THE SAM_Template SHALL NOT create an API Gateway API key resource or usage plan resource.

---

### Requirement 4: SAR Deployment Parameter Exposure

**User Story:** As a deployer using the AWS Serverless Application Repository, I want to select the authentication mode during SAR deployment, so that I can choose the mode that fits my environment without modifying the template after deployment.

#### Acceptance Criteria

1. THE SAR_Application SHALL expose the `AuthMode` parameter in the SAR deployment UI with a description that explains the two supported values and their behavior.
2. THE SAR_Application SHALL set the default value of `AuthMode` to `API_KEY` in the SAR deployment UI.
3. WHEN a deployer selects `IAM` in the SAR deployment UI and completes deployment, THE resulting stack SHALL have all API Gateway endpoints configured with IAM authentication.
4. WHEN a deployer selects `API_KEY` in the SAR deployment UI and completes deployment, THE resulting stack SHALL have all API Gateway endpoints configured with API key authentication.
5. WHEN `AuthMode` is `API_KEY`, THE SAR_Application SHALL expose the API-key-specific deployment output required for API-key-based validation.
6. WHEN `AuthMode` is `IAM`, THE SAR_Application SHALL NOT require an API-key-specific output for successful deployment or validation.

---

### Requirement 5: SAR Release Validation for Both Modes

**User Story:** As a maintainer releasing a new version of MockNest Serverless, I want the SAR release validation pipeline to test both API key mode and IAM mode independently, so that I have confidence both modes work correctly before the release is made public.

#### Acceptance Criteria

1. THE SAR_Validation_Pipeline SHALL execute a Validation_Run for `API_KEY` mode and a Validation_Run for `IAM` mode as part of every release validation.
2. WHEN the `API_KEY` Validation_Run deploys a Test_Stack, THE pipeline SHALL deploy with `AuthMode` set to `API_KEY`.
3. WHEN the `IAM` Validation_Run deploys a Test_Stack, THE pipeline SHALL deploy with `AuthMode` set to `IAM`.
4. THE health check validation for each Validation_Run SHALL cover exactly two endpoints: `/__admin/health` and `/ai/generation/health`.
5. WHEN the `API_KEY` Validation_Run executes health checks, THE pipeline SHALL authenticate all requests using the `x-api-key` header with the API key value retrieved from the deployed stack.
6. WHEN the `IAM` Validation_Run executes health checks, THE pipeline SHALL authenticate all requests using AWS Signature Version 4 signed with the pipeline's IAM credentials.
7. THE SAR_Validation_Pipeline SHALL treat the release as failed if either Validation_Run fails.
8. THE failure of one Validation_Run SHALL NOT prevent the other Validation_Run from executing and reporting its result.

---

### Requirement 6: Validation Result Reporting

**User Story:** As a maintainer reviewing a release validation run, I want the pipeline to produce a clear summary showing which auth modes were tested and what passed or failed, so that I can quickly determine whether the release is safe to publish.

#### Acceptance Criteria

1. WHEN a Validation_Run completes, THE SAR_Validation_Pipeline SHALL produce a summary that includes: the Auth_Mode tested and the overall pass or fail status.
2. WHEN a Validation_Run fails, THE summary SHALL identify which phase failed and SHALL include the HTTP status code or error message that caused the failure.
3. THE SAR_Validation_Pipeline SHALL produce a combined summary that shows the result of both Validation_Runs, so that a reviewer can assess both modes together.
4. THE SAR_Validation_Pipeline SHALL NOT include API key values or IAM credentials in any summary output or log.

---

### Requirement 7: Test Resource Cleanup After Validation

**User Story:** As a maintainer running release validation, I want all AWS resources created during validation to be deleted after each run regardless of whether the run succeeded or failed, so that I do not accumulate orphaned stacks or incur unexpected costs.

#### Acceptance Criteria

1. WHEN a Validation_Run completes, regardless of whether it succeeded or failed, THE SAR_Validation_Pipeline SHALL attempt to delete the Test_Stack created during that run.
2. WHEN the Test_Stack deletion completes, THE SAR_Validation_Pipeline SHALL verify that the S3 bucket created by the stack no longer exists.
3. WHEN the Test_Stack deletion fails, THE SAR_Validation_Pipeline SHALL record a cleanup warning in the summary.
4. THE SAR_Validation_Pipeline SHALL ensure Validation_Runs are isolated so that concurrent or repeated executions do not conflict with each other.
