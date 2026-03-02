# Requirements Document

## Introduction

This document defines requirements for hardening MockNest Serverless for public AWS Serverless Application Repository (SAR) publication while maintaining full Koog AI framework integration. The feature addresses critical issues with region parameter handling, simplifies user-facing configuration, implements intelligent inference prefix selection, tightens security permissions, and adds necessary SAR metadata.

The primary goal is to make MockNest Serverless production-ready for public SAR distribution by removing misleading configuration parameters, automating complex AWS Bedrock inference profile selection, and ensuring least-privilege security posture.

## Glossary

- **SAR**: AWS Serverless Application Repository - AWS service for publishing and discovering serverless applications
- **Koog**: Kotlin-based AI agent framework used for mock generation, integrating with Amazon Bedrock
- **Inference_Profile**: AWS Bedrock feature for cross-region model routing using prefixes (global, eu, us, etc.)
- **BedrockModels**: Koog enum containing supported Bedrock model definitions
- **CloudFormation**: AWS infrastructure-as-code service that deploys SAR applications
- **Deploy_Region**: The AWS region where the CloudFormation stack is deployed (selected by user in AWS Console)
- **Model_Capability**: Whether a specific Bedrock model supports global inference profiles or only geo-specific profiles
- **Geo_Prefix**: Region-specific inference profile prefix (eu, us, ap, etc.) derived from deploy region
- **InferencePrefixResolver**: Component that automatically determines the correct inference profile prefix based on deploy region and model capabilities

## Requirements

### Requirement 1: Remove Misleading Region Parameters

**User Story:** As a SAR user, I want the application to automatically use the region I selected during deployment, so that I don't have to configure region parameters that don't actually control deployment location.

#### Acceptance Criteria

1. THE SAM_Template SHALL NOT contain an `AppRegion` parameter
2. THE SAM_Template SHALL NOT set `MOCKNEST_APP_REGION` environment variable in Lambda configuration
3. WHEN the Lambda runtime needs the deployment region, THE Runtime SHALL read the `AWS_REGION` environment variable
4. THE Bedrock_Client SHALL use the deployment region from `AWS_REGION` for all API calls
5. WHEN deployed to any AWS region, THE Application SHALL function correctly without requiring manual region configuration

### Requirement 2: Simplify Bedrock Model Selection

**User Story:** As a SAR user, I want to select a Bedrock model by name without understanding inference profile prefixes, so that I can deploy MockNest without deep AWS Bedrock knowledge.

#### Acceptance Criteria

1. THE SAM_Template SHALL contain a `BedrockModelName` parameter with allowed values matching Koog `BedrockModels` enum names
2. THE SAM_Template SHALL NOT expose `BedrockInferencePrefix` as a user-selectable parameter in the SAR deployment UI
3. THE Lambda_Configuration SHALL set `BEDROCK_MODEL_NAME` environment variable from the `BedrockModelName` parameter
4. THE ModelConfiguration SHALL map `BEDROCK_MODEL_NAME` to the corresponding Koog `BedrockModels` constant using reflection
5. WHEN an invalid model name is provided, THE ModelConfiguration SHALL log a warning and fall back to `AmazonNovaPro`

### Requirement 3: Implement Automatic Inference Prefix Selection

**User Story:** As a SAR user, I want the application to automatically select the optimal Bedrock inference profile, so that my AI-powered mock generation works without manual prefix configuration.

#### Acceptance Criteria

1. THE SAM_Template SHALL contain a `BedrockInferenceMode` parameter with allowed values: `AUTO`, `GLOBAL_ONLY`, `GEO_ONLY`
2. THE SAM_Template SHALL set the default value of `BedrockInferenceMode` to `AUTO`
3. THE Lambda_Configuration SHALL set `BEDROCK_INFERENCE_MODE` environment variable from the `BedrockInferenceMode` parameter
4. THE InferencePrefixResolver SHALL determine the geo prefix from `AWS_REGION` using the mapping: `eu-*` → `eu`, `us-*` → `us`, `ap-*` → `ap`, `ca-*` → `ca`, `me-*` → `me`, `sa-*` → `sa`, `af-*` → `af`
5. WHEN `BedrockInferenceMode` is `AUTO`, THE InferencePrefixResolver SHALL return candidate prefixes in order: [`global`, geo_prefix]
6. WHEN `BedrockInferenceMode` is `GLOBAL_ONLY`, THE InferencePrefixResolver SHALL return candidate prefixes: [`global`]
7. WHEN `BedrockInferenceMode` is `GEO_ONLY`, THE InferencePrefixResolver SHALL return candidate prefixes: [geo_prefix]
8. THE ModelConfiguration SHALL attempt to use the first candidate prefix from InferencePrefixResolver
9. IF the first candidate prefix fails with a model-not-found or access-denied error, THEN THE ModelConfiguration SHALL retry with the next candidate prefix
10. IF all candidate prefixes fail, THEN THE ModelConfiguration SHALL attempt to use the model without any inference profile prefix
11. IF all attempts fail, THEN THE ModelConfiguration SHALL throw a descriptive error including the deploy region, model name, and attempted prefixes

### Requirement 4: Implement Inference Prefix Fallback Strategy

**User Story:** As a developer, I want the application to gracefully handle Bedrock model availability differences across regions, so that users get clear error messages when models are unavailable.

#### Acceptance Criteria

1. WHEN a Bedrock invocation fails with an error indicating model not found or access not enabled, THE ModelConfiguration SHALL retry with the next candidate prefix
2. THE ModelConfiguration SHALL NOT retry on genuine service errors such as throttling, validation errors, or internal server errors
3. WHEN all prefixes fail, THE Error_Message SHALL include the deployment region, selected model name, and all attempted prefixes
4. THE ModelConfiguration SHALL log each prefix attempt at DEBUG level
5. WHEN a prefix succeeds, THE ModelConfiguration SHALL cache the successful prefix for subsequent invocations

### Requirement 5: Tighten IAM Permissions

**User Story:** As a security-conscious user, I want MockNest to follow least-privilege principles, so that the Lambda function has only the minimum necessary permissions.

#### Acceptance Criteria

1. THE IAM_Policy SHALL NOT include `bedrock:ListFoundationModels` action
2. THE IAM_Policy SHALL include `bedrock:InvokeModel` action
3. IF streaming responses are used, THEN THE IAM_Policy SHALL include `bedrock:InvokeModelWithResponseStream` action
4. THE IAM_Policy Bedrock actions SHALL apply to resource `*` (scoping to specific model ARNs is not feasible with dynamic model selection)
5. THE IAM_Policy SHALL include a comment documenting why Bedrock resource is `*` rather than scoped

### Requirement 6: Add SAR Metadata

**User Story:** As a SAR user, I want to find MockNest easily in the AWS Serverless Application Repository with clear documentation, so that I can evaluate and deploy it confidently.

#### Acceptance Criteria

1. THE SAM_Template SHALL contain `Metadata: AWS::ServerlessRepo::Application` section
2. THE SAR_Metadata SHALL include `Name` field with value "MockNest-Serverless"
3. THE SAR_Metadata SHALL include `Description` field summarizing the application purpose
4. THE SAR_Metadata SHALL include `Author` field
5. THE SAR_Metadata SHALL include `LicenseUrl` field pointing to the LICENSE file in the repository
6. THE SAR_Metadata SHALL include `ReadmeUrl` field pointing to the README file in the repository
7. THE SAR_Metadata SHALL include `Labels` field with relevant tags: ["mock", "testing", "wiremock", "serverless", "ai", "bedrock"]
8. THE SAR_Metadata SHALL include `HomePageUrl` field pointing to the GitHub repository
9. THE SAR_Metadata SHALL include `SourceCodeUrl` field pointing to the GitHub repository

### Requirement 7: Update Deployment Documentation

**User Story:** As a new user, I want clear instructions for deploying MockNest from SAR, so that I can get started quickly without confusion.

#### Acceptance Criteria

1. THE README SHALL contain a section titled "Deployment from AWS Serverless Application Repository"
2. THE Deployment_Section SHALL explain that users select the deployment region in the AWS Console
3. THE Deployment_Section SHALL explain Bedrock model availability varies by region
4. THE Deployment_Section SHALL explain that model access must be enabled in the AWS account before deployment
5. THE Deployment_Section SHALL document the `BedrockInferenceMode` parameter with descriptions of `AUTO`, `GLOBAL_ONLY`, and `GEO_ONLY` options
6. THE Deployment_Section SHALL recommend using `AUTO` mode for most use cases
7. THE Deployment_Section SHALL explain when to use `GLOBAL_ONLY` (force global inference profile) and `GEO_ONLY` (force geo-specific profile)
8. THE Deployment_Section SHALL include a link to AWS Bedrock model availability documentation
9. THE Deployment_Section SHALL include a "Support" subsection directing users to GitHub Issues for questions and bug reports

### Requirement 8: Implement Health Check Endpoint

**User Story:** As an operator, I want a health check endpoint that shows the resolved Bedrock configuration, so that I can verify the deployment is configured correctly.

#### Acceptance Criteria

1. THE Runtime SHALL expose a `GET /health/ready` endpoint
2. THE Health_Endpoint SHALL return HTTP 200 when the application is ready
3. THE Health_Response SHALL include the deployment region from `AWS_REGION`
4. THE Health_Response SHALL include the selected Bedrock model name
5. THE Health_Response SHALL include the resolved inference profile prefix (or "none" if no prefix is used)
6. THE Health_Response SHALL include the inference mode (`AUTO`, `GLOBAL_ONLY`, or `GEO_ONLY`)
7. IF Bedrock invocation has been attempted, THEN THE Health_Response SHALL include whether the last invocation succeeded
8. THE Health_Response SHALL be formatted as JSON

### Requirement 9: Preserve Koog Integration

**User Story:** As a developer, I want Koog framework integration to remain fully functional after SAR hardening changes, so that AI-powered mock generation continues to work correctly.

#### Acceptance Criteria

1. THE ModelConfiguration SHALL continue to map model names to Koog `BedrockModels` enum values using reflection
2. THE ModelConfiguration SHALL continue to call `withInferenceProfile(prefix)` on Koog `LLModel` instances
3. WHEN no inference profile prefix is needed, THE ModelConfiguration SHALL return the base `LLModel` without calling `withInferenceProfile`
4. THE BedrockServiceAdapter SHALL continue to use Koog `SingleLLMPromptExecutor` and `BedrockLLMClient`
5. THE MockGenerationFunctionalAgent SHALL continue to use Koog `GraphAIAgent` for AI-powered generation
6. ALL existing AI generation use cases SHALL continue to function without modification

### Requirement 10: Remove Application Properties Region Configuration

**User Story:** As a developer, I want the application to use only AWS-provided environment variables for region configuration, so that there is a single source of truth for the deployment region.

#### Acceptance Criteria

1. THE application.properties SHALL NOT define `aws.region` property
2. THE application.properties SHALL NOT reference `MOCKNEST_APP_REGION` environment variable
3. IF AWS SDK clients need region configuration, THEN THE Configuration SHALL use `AWS_REGION` environment variable directly
4. THE BedrockConfiguration SHALL configure `BedrockRuntimeClient` to use the region from `AWS_REGION`
