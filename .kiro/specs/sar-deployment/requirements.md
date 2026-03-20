# Requirements Document

## Introduction

This document defines requirements for hardening MockNest Serverless for public AWS Serverless Application Repository (SAR) publication while maintaining full Koog AI framework integration. The feature addresses critical issues with region parameter handling, simplifies user-facing configuration, implements intelligent inference prefix selection, tightens security permissions, adds necessary SAR metadata, and ensures comprehensive documentation and testing.

The primary goal is to make MockNest Serverless production-ready for public SAR distribution by:
- Removing misleading configuration parameters
- Automating complex AWS Bedrock inference profile selection
- Ensuring least-privilege security posture
- Implementing AI features as optional with graceful degradation
- Meeting all AWS SAR technical publication requirements
- Providing comprehensive documentation for different user personas
- Establishing tested configuration boundaries with soft restrictions
- Validating deployment across multiple regions before public release

## Glossary

- **SAR**: AWS Serverless Application Repository - AWS service for publishing and discovering serverless applications
- **Koog**: Kotlin-based AI agent framework used for mock generation, integrating with Amazon Bedrock
- **Inference_Profile**: AWS Bedrock feature for cross-region model routing using prefixes (global, eu, us, etc.)
- **BedrockModels**: Koog enum containing supported Bedrock model definitions
- **CloudFormation**: AWS infrastructure-as-code service that deploys SAR applications
- **Deploy_Region**: The AWS region where the CloudFormation stack is deployed (selected by user in AWS Console)
- **Publish_Region**: The AWS region from which the SAR application is published (must be us-east-1 or us-east-2 for public apps)
- **Model_Capability**: Whether a specific Bedrock model supports global inference profiles or only geo-specific profiles
- **Geo_Prefix**: Region-specific inference profile prefix (eu, us, ap, etc.) derived from deploy region
- **InferencePrefixResolver**: Component that automatically determines the correct inference profile prefix based on deploy region and model capabilities
- **Core_Runtime**: The base WireMock functionality (mock serving, S3 persistence, admin API) that works without AI features
- **AI_Features**: Optional AI-powered mock generation capabilities that require Amazon Bedrock access
- **Tested_Regions**: AWS regions where MockNest has been thoroughly tested (us-east-1, eu-west-1, eu-central-1, ap-southeast-1)
- **Officially_Supported**: Features and configurations that are tested, documented, and supported by the maintainers
- **Experimental**: Features and configurations that may work but are not officially tested or supported
- **Soft_Restriction**: Documentation-based guidance on supported configurations without technical enforcement
- **Private_SAR**: SAR application shared only with specific AWS accounts for testing before public release
- **Public_SAR**: SAR application available to all AWS users in the public SAR catalog
- **SemanticVersion**: Version number following semantic versioning format (MAJOR.MINOR.PATCH) required for public SAR apps
- **OSI_License**: Open Source Initiative approved license required for public SAR applications

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
4. THE IAM_Policy Bedrock actions SHALL be scoped to the deployment region using: `arn:aws:bedrock:${AWS::Region}:*:*`
5. THE IAM_Policy SHALL include a comment documenting the resource scope and why it includes both foundation models and inference profiles
6. THE IAM_Policy SHALL NOT use `Resource: *` for any Bedrock permissions

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

### Requirement 8: Implement Separate Health Check Endpoints

**User Story:** As an operator, I want separate health check endpoints for runtime and AI features, so that I can monitor each component independently.

#### Acceptance Criteria

1. THE Runtime SHALL expose a `GET /__admin/health` endpoint for WireMock runtime status
2. THE Runtime_Health_Endpoint SHALL return HTTP 200 when the WireMock runtime is ready
3. THE Runtime_Health_Response SHALL include: deployment region, S3 bucket name, storage connectivity status
4. THE Runtime_Health_Response SHALL include a timestamp
5. THE Runtime SHALL expose a `GET /ai/health` endpoint for AI features status
6. THE AI_Health_Endpoint SHALL return HTTP 200 when AI features are operational
7. THE AI_Health_Response SHALL include: deployment region, model name, inference prefix, inference mode
8. IF Bedrock invocation has been attempted, THEN THE AI_Health_Response SHALL include whether the last invocation succeeded
9. THE AI_Health_Response SHALL include a timestamp
10. BOTH health endpoints SHALL return responses formatted as JSON

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

### Requirement 11: Implement Soft Model Restrictions

**User Story:** As a SAR user, I want clear guidance on which Bedrock models are officially supported, while retaining flexibility to experiment with other models.

#### Acceptance Criteria

1. THE SAM_Template `BedrockModelName` parameter SHALL set `AmazonNovaPro` as the default value
2. THE SAM_Template `BedrockModelName` parameter description SHALL indicate "Amazon Nova Pro is officially supported and tested"
3. THE SAM_Template `BedrockModelName` parameter SHALL allow other Koog-supported model names as valid values
4. THE Documentation SHALL clearly state that only Amazon Nova Pro is officially supported
5. THE Documentation SHALL indicate that other models are experimental and may not work in all regions
6. THE Health_Endpoint response SHALL include a field indicating whether the current model is officially supported

### Requirement 13: Add SAR Publication Requirements

**User Story:** As a publisher, I want to ensure all AWS SAR technical requirements are met, so that the application can be published successfully to the public SAR catalog.

#### Acceptance Criteria

1. THE SAM_Template SHALL include a `SemanticVersion` property in the `Metadata: AWS::ServerlessRepo::Application` section
2. THE SemanticVersion SHALL follow semantic versioning format (e.g., "1.0.0")
3. THE Repository SHALL contain a LICENSE file with an OSI-approved open source license
4. THE Repository SHALL contain a README.md file describing application usage and configuration
5. THE S3_Bucket used for SAM packaging SHALL have a bucket policy granting `serverlessrepo.amazonaws.com` read permissions
6. THE SAR_Publication SHALL be performed from us-east-1 or us-east-2 region (AWS requirement for public apps)
7. THE SAM_Template SHALL include all required metadata fields: Name, Description, Author, LicenseUrl, ReadmeUrl, HomePageUrl, SourceCodeUrl, Labels

### Requirement 14: Implement Private SAR Testing Requirements

**User Story:** As a publisher, I want to thoroughly test the SAR deployment in a fresh AWS account before making it public, so that users have a smooth deployment experience.

#### Acceptance Criteria

1. THE Application SHALL be published as a private SAR application before public release
2. THE Private_SAR_Application SHALL be tested by deploying from a fresh AWS account (not the publisher account)
3. THE Testing SHALL cover deployment to us-east-1, eu-west-1, eu-central-1, and ap-southeast-1 regions
4. THE Testing SHALL verify core runtime functionality (mock creation, serving, persistence) in all tested regions
5. THE Testing SHALL verify AI features (if enabled) work correctly in all tested regions with Amazon Nova Pro
6. THE Testing SHALL verify the health check endpoint returns correct configuration information
7. THE Testing SHALL verify all CloudFormation outputs are correct and accessible
8. THE Testing SHALL verify API Gateway endpoints are accessible with the generated API key
9. WHEN all tests pass in all three regions, THEN THE Application MAY be switched to public sharing

### Requirement 15: Document Tested Regions and Features

**User Story:** As a SAR user, I want to know which regions and features have been tested, so that I can deploy with confidence or understand the risks of deploying to untested regions.

#### Acceptance Criteria

1. THE README SHALL contain a "Tested Configuration" section listing officially tested regions
2. THE Tested_Regions SHALL include: us-east-1 (N. Virginia), eu-west-1 (Ireland), eu-central-1 (Frankfurt), ap-southeast-1 (Singapore)
3. THE README SHALL state that core runtime works in any AWS region with Lambda, API Gateway, and S3
4. THE README SHALL state that AI features are tested only with Amazon Nova Pro in the tested regions
5. THE README SHALL indicate that deployment to other regions is possible but not officially supported
6. THE README SHALL indicate that other Bedrock models may work but are not officially supported
7. THE README SHALL list which WireMock features have been tested in the serverless environment
8. THE README SHALL NOT claim support for WireMock features that have not been tested

### Requirement 16: Create OpenAPI Specification

**User Story:** As a developer integrating with MockNest, I want an OpenAPI specification documenting all API endpoints, so that I can understand the API contract and generate client code.

#### Acceptance Criteria

1. THE Repository SHALL contain an OpenAPI 3.0 specification file at `docs/api/mocknest-openapi.yaml`
2. THE OpenAPI_Spec SHALL document all WireMock admin API endpoints exposed by MockNest
3. THE OpenAPI_Spec SHALL document all AI generation endpoints (when AI features are enabled)
4. THE OpenAPI_Spec SHALL document the health check endpoint
5. THE OpenAPI_Spec SHALL include request/response schemas for all endpoints
6. THE OpenAPI_Spec SHALL include authentication requirements (API key header)
7. THE OpenAPI_Spec SHALL include example requests and responses
8. THE README SHALL link to the OpenAPI specification file

### Requirement 17: Create User Guide Documentation

**User Story:** As a new MockNest user, I want a comprehensive user guide that shows me how to use the deployed application, so that I can quickly become productive.

#### Acceptance Criteria

1. THE Repository SHALL contain a user guide at `docs/USER_GUIDE.md`
2. THE User_Guide SHALL include a "Getting Started" section with first mock creation
3. THE User_Guide SHALL include a "Core Features" section documenting tested WireMock functionality
4. THE User_Guide SHALL include an "AI-Assisted Mock Generation" section (when AI features are enabled)
5. THE User_Guide SHALL include a "Common Workflows" section with practical examples
6. THE User_Guide SHALL include a "Troubleshooting" section with common issues and solutions
7. THE User_Guide SHALL include a "Limitations" section documenting known limitations in serverless environment
8. THE User_Guide SHALL include a "API Reference" section linking to the OpenAPI specification
9. THE README SHALL link to the user guide

### Requirement 18: Restructure README for User Personas

**User Story:** As a visitor to the repository, I want the README to be organized by my use case (SAR user, SAM developer, or contributor), so that I can quickly find relevant information.

#### Acceptance Criteria

1. THE README SHALL be restructured with clear sections for different user personas
2. THE README SHALL contain a "Quick Start for SAR Users" section as the primary path
3. THE README SHALL contain a "Deployment for Developers" section for SAM-based deployment
4. THE README SHALL contain a "Contributing" section linking to CONTRIBUTING.md
5. THE Quick_Start_Section SHALL focus on SAR deployment with minimal prerequisites
6. THE Quick_Start_Section SHALL link to the user guide for post-deployment usage
7. THE Developer_Section SHALL document building from source and local testing
8. THE README SHALL clearly separate "what users need to know" from "what developers need to know"
9. THE README SHALL include a "Tested Configuration" section documenting officially supported regions and models

### Requirement 19: Create SAR-Specific README

**User Story:** As a SAR user viewing the application in the AWS Console, I want a focused README that shows me how to use the deployed application, without developer setup instructions.

#### Acceptance Criteria

1. THE Repository SHALL contain a separate `README-SAR.md` file for SAR users
2. THE README-SAR SHALL be referenced in the SAM template `ReadmeUrl` metadata field
3. THE README-SAR SHALL NOT include developer setup instructions (building, testing, contributing)
4. THE README-SAR SHALL include a "How to Use" section with post-deployment instructions
5. THE README-SAR SHALL include clear input parameter documentation with examples
6. THE README-SAR SHALL include output format documentation with examples
7. THE README-SAR SHALL include a "Common Use Cases" section with practical examples
8. THE README-SAR SHALL include an "Error Handling" section explaining common errors
9. THE README-SAR SHALL include a "Cost" section explaining what users will pay
10. THE README-SAR SHALL include a "Security" section explaining IAM permissions and how to restrict them

### Requirement 20: Document Cost Transparency

**User Story:** As a SAR user, I want to understand what AWS costs I will incur by using MockNest, so that I can make informed deployment decisions.

#### Acceptance Criteria

1. THE README-SAR SHALL contain a "Cost" section explaining all AWS service costs
2. THE Cost_Section SHALL break down costs by service: Lambda, API Gateway, S3, Step Functions (if used), Bedrock (if AI enabled)
3. THE Cost_Section SHALL explain Free Tier eligibility for each service
4. THE Cost_Section SHALL provide example cost calculations for typical usage scenarios
5. THE Cost_Section SHALL explain that AI features (Bedrock) incur additional costs
6. THE Cost_Section SHALL link to AWS pricing calculators for detailed estimates
7. THE Health_Endpoint response SHALL include estimated monthly cost based on current usage (optional enhancement)

### Requirement 21: Document Security and IAM Permissions

**User Story:** As a security-conscious user, I want to understand what IAM permissions MockNest requires and why, so that I can assess security risks before deployment.

#### Acceptance Criteria

1. THE README-SAR SHALL contain a "Security" section explaining IAM permissions
2. THE Security_Section SHALL list all IAM actions granted to Lambda functions
3. THE Security_Section SHALL explain that Bedrock permissions are scoped to the deployment region only
4. THE Security_Section SHALL document the resource ARN pattern used: `arn:aws:bedrock:${AWS::Region}:*:*`
5. THE Security_Section SHALL explain API Gateway authentication (API key by default)
6. THE Security_Section SHALL document S3 bucket encryption settings
7. THE Security_Section SHALL link to AWS security best practices documentation
8. THE Security_Section SHALL explain that permissions follow least-privilege principles

### Requirement 22: Add Semantic Versioning

**User Story:** As a SAR user, I want to see version numbers for MockNest releases, so that I can track updates and understand what changed between versions.

#### Acceptance Criteria

1. THE SAM_Template SHALL include a `SemanticVersion` property starting at "1.0.0"
2. THE Repository SHALL maintain a CHANGELOG.md file documenting all releases
3. THE CHANGELOG SHALL follow the format: version number, release date, and list of changes
4. THE CHANGELOG SHALL categorize changes as: Added, Changed, Fixed, Removed, Security
5. THE README-SAR SHALL include a "Version History" section linking to CHANGELOG.md
6. WHEN publishing to SAR, THE SemanticVersion SHALL be incremented following semver rules
7. THE Git repository SHALL use tags matching the semantic version (e.g., v1.0.0)

### Requirement 24: Remove Local Filesystem Support

**User Story:** As a developer, I want to simplify the codebase by removing local filesystem support for mock storage, so that the system has a single, consistent storage mechanism.

#### Acceptance Criteria

1. THE Runtime SHALL use S3 as the exclusive storage mechanism for WireMock mappings and files
2. THE Runtime SHALL NOT support loading mappings from local filesystem or classpath
3. THE CompositeMappingsSource SHALL be removed or simplified to only use S3-backed storage
4. THE Configuration SHALL NOT include any local filesystem paths for mock storage
5. THE Documentation SHALL clearly state that all mocks must be stored in S3
6. THE Built-in health check endpoint (if any) SHALL be implemented as a standard WireMock mapping stored in S3, not as a classpath resource
7. THE Codebase SHALL remove any code related to local filesystem mock loading to reduce complexity

### Requirement 23: Document Error Handling and Troubleshooting

**User Story:** As a SAR user encountering errors, I want clear troubleshooting guidance, so that I can resolve issues without contacting support.

#### Acceptance Criteria

1. THE README-SAR SHALL contain an "Error Handling" section
2. THE Error_Handling_Section SHALL document common error scenarios with solutions
3. THE Error_Handling_Section SHALL explain Bedrock access errors (model not available, access not enabled)
4. THE Error_Handling_Section SHALL explain API Gateway authentication errors (invalid API key)
5. THE Error_Handling_Section SHALL explain S3 permission errors
6. THE Error_Handling_Section SHALL explain Lambda timeout errors
7. THE Error_Handling_Section SHALL include instructions for viewing CloudWatch logs
8. THE Error_Handling_Section SHALL link to GitHub Issues for reporting bugs
