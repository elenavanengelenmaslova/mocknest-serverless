# Bugfix Requirements Document

## Introduction

The SAM template and build configuration for MockNest Serverless have four critical issues that prevent proper deployment and security:

1. **Missing API Key Configuration**: No API keys, usage plans, or usage plan keys are defined, leaving the API endpoints unsecured
2. **Multiple Unwanted Stages**: After deployment, API Gateway shows multiple stages (Stage, v1, and possibly others) instead of just the configured stage
3. **Misleading Parameter Naming**: The "StageName" parameter with default "v1" suggests versioning rather than deployment instance identification, which conflicts with the use case of deploying multiple independent MockNest instances
4. **Shadow Plugin Minimization Removing Spring Cloud Adapter**: The Gradle Shadow plugin's `minimize()` feature is excluding the Spring Cloud Function adapter classes, causing Lambda invocations to fail with `ClassNotFoundException: org.springframework.cloud.function.adapter.aws.FunctionInvoker`

These issues impact security (no API key protection), operational clarity (multiple confusing stages), user experience (misleading parameter semantics), and runtime functionality (Lambda functions cannot start).

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the SAM template is deployed THEN no AWS::ApiGateway::ApiKey resources are created

1.2 WHEN the SAM template is deployed THEN no AWS::ApiGateway::UsagePlan resources are created

1.3 WHEN the SAM template is deployed THEN no AWS::ApiGateway::UsagePlanKey resources are created

1.4 WHEN the SAM template is deployed THEN API Gateway endpoints are accessible without API key authentication

1.5 WHEN the SAM template is deployed THEN API Gateway shows multiple stages including "Stage", the configured stage name, and potentially others

1.6 WHEN users review the template parameters THEN the "StageName" parameter with default "v1" suggests API versioning semantics

1.7 WHEN users want to deploy multiple independent MockNest instances THEN the "StageName" parameter name does not clearly communicate its purpose as a deployment instance identifier

1.8 WHEN the Shadow plugin minimizes the Lambda JAR THEN the Spring Cloud Function adapter classes are removed

1.9 WHEN AWS Lambda attempts to invoke the function THEN it fails with ClassNotFoundException for org.springframework.cloud.function.adapter.aws.FunctionInvoker

1.10 WHEN the Lambda function fails to start THEN all API requests return 502 Bad Gateway errors

### Expected Behavior (Correct)

2.1 WHEN the SAM template is deployed THEN an AWS::ApiGateway::ApiKey resource SHALL be created automatically

2.2 WHEN the SAM template is deployed THEN an AWS::ApiGateway::UsagePlan resource SHALL be created and associated with the API and stage

2.3 WHEN the SAM template is deployed THEN an AWS::ApiGateway::UsagePlanKey resource SHALL be created to link the API key with the usage plan

2.4 WHEN the SAM template is deployed THEN API Gateway endpoints SHALL require API key authentication via the x-api-key header

2.5 WHEN the SAM template is deployed THEN API Gateway SHALL show only the explicitly configured stage

2.6 WHEN the SAM template is deployed THEN no "Stage" stage or other unwanted stages SHALL exist in API Gateway

2.7 WHEN users review the template parameters THEN the parameter name SHALL clearly indicate it represents a deployment instance identifier (e.g., "DeploymentName" or "InstanceName")

2.8 WHEN users deploy multiple MockNest instances THEN each instance SHALL use a distinct deployment identifier (e.g., "team-alpha", "payment-apis", "test") rather than version numbers

2.9 WHEN the SAM template is deployed THEN the API key value SHALL be output so users can access it for API requests

2.10 WHEN the Shadow plugin minimizes the Lambda JAR THEN the Spring Cloud Function adapter classes SHALL be preserved

2.11 WHEN AWS Lambda invokes the function THEN the FunctionInvoker class SHALL be found and loaded successfully

2.12 WHEN API requests are made to the deployed endpoints THEN they SHALL be processed successfully by the Lambda functions

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the SAM template is deployed THEN the Lambda functions SHALL CONTINUE TO be integrated with API Gateway correctly

3.2 WHEN the SAM template is deployed THEN the /__admin/{proxy+} routes SHALL CONTINUE TO route to the runtime Lambda function

3.3 WHEN the SAM template is deployed THEN the /mocknest/{proxy+} routes SHALL CONTINUE TO route to the runtime Lambda function

3.4 WHEN the SAM template is deployed THEN the /ai/{proxy+} routes SHALL CONTINUE TO route to the generation Lambda function

3.5 WHEN the SAM template is deployed THEN CloudWatch logging and metrics SHALL CONTINUE TO function as configured

3.6 WHEN the SAM template is deployed THEN API Gateway throttling settings SHALL CONTINUE TO apply (200 burst, 100 rate)

3.7 WHEN the SAM template is deployed THEN all existing outputs SHALL CONTINUE TO be available

3.8 WHEN users provide a custom stage/deployment name THEN it SHALL CONTINUE TO accept alphanumeric characters, hyphens, and underscores

3.9 WHEN the SAM template is deployed THEN S3 bucket, Lambda functions, and IAM roles SHALL CONTINUE TO be created as before
