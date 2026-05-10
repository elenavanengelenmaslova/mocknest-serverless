# Bugfix Requirements Document

## Introduction

The MockNest Serverless SAM template has an inconsistency in rate limiting configuration between the two API Gateway authentication modes. When `AuthMode=API_KEY`, a `UsagePlan` resource applies throttling (BurstLimit: 1, RateLimit: 100) to the API Gateway. When `AuthMode=IAM`, no usage plan or throttling is configured, leaving the API Gateway completely unprotected against excessive request rates. This means IAM-mode deployments have no rate limiting, allowing unlimited requests to hit the Lambda functions and potentially causing cost overruns or service degradation.

Additionally, the rate limiting values are hardcoded in the template rather than exposed as SAM parameters, preventing SAR users from customizing throttling behavior at deployment time.

A secondary issue exists in the README.md where the competition reference incorrectly includes "2026" in the name.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN AuthMode is set to IAM THEN the system deploys an API Gateway with no usage plan and no throttling configuration, allowing unlimited requests to reach the Lambda functions

1.2 WHEN AuthMode is set to API_KEY THEN the system applies rate limiting via a UsagePlan with hardcoded BurstLimit of 1 and RateLimit of 100 that cannot be customized by the deployer

1.3 WHEN a user deploys MockNest via SAR THEN the system provides no parameters to configure rate limiting values (BurstLimit and RateLimit), forcing all deployments to use the same hardcoded values regardless of workload requirements

1.4 WHEN a user reads the README.md competition reference THEN the system displays "AWS 10,000 AIdeas 2026 Competition" which incorrectly includes the year "2026" in the competition name

### Expected Behavior (Correct)

2.1 WHEN AuthMode is set to IAM THEN the system SHALL deploy a UsagePlan with throttling configuration attached to the IAM-mode API Gateway stage, applying the same rate limiting as API_KEY mode

2.2 WHEN AuthMode is set to API_KEY THEN the system SHALL apply rate limiting via a UsagePlan using configurable SAM parameters for BurstLimit and RateLimit with sensible defaults (BurstLimit: 1, RateLimit: 100)

2.3 WHEN a user deploys MockNest via SAR THEN the system SHALL expose BurstLimit and RateLimit as configurable SAM parameters so deployers can customize throttling to match their workload requirements

2.4 WHEN a user reads the README.md competition reference THEN the system SHALL display "AWS 10,000 AIdeas Competition" without the year in the competition name

### Unchanged Behavior (Regression Prevention)

3.1 WHEN AuthMode is set to API_KEY THEN the system SHALL CONTINUE TO create an API key and associate it with the usage plan via a UsagePlanKey resource

3.2 WHEN AuthMode is set to IAM THEN the system SHALL CONTINUE TO use AWS_IAM as the default authorizer for the API Gateway

3.3 WHEN AuthMode is set to API_KEY with default parameter values THEN the system SHALL CONTINUE TO apply BurstLimit of 1 and RateLimit of 100 (preserving current default behavior)

3.4 WHEN any AuthMode is configured THEN the system SHALL CONTINUE TO deploy all Lambda functions, S3 bucket, SQS queues, and CloudWatch log groups with their existing configurations unchanged

3.5 WHEN AuthMode is set to IAM THEN the system SHALL CONTINUE TO NOT create an API key or UsagePlanKey resource (API keys are only relevant for API_KEY mode)
