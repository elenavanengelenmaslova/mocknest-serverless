# Implementation Plan: SAR Deployment Hardening

## Overview

This implementation plan transforms MockNest Serverless into a production-ready application for public AWS Serverless Application Repository (SAR) distribution. The work focuses on removing configuration confusion, automating Bedrock inference profile selection, tightening security, implementing comprehensive health monitoring, and providing documentation for different user personas.

The implementation follows a phased approach: core configuration changes first, then AI enhancements, security hardening, health monitoring, storage simplification, documentation, testing, and finally SAR publication validation.

## Tasks

- [-] 1. Remove misleading region parameters
  - [x] 1.1 Remove AppRegion parameter from SAM template
    - Remove `AppRegion` parameter definition from `deployment/aws/sam/template.yaml`
    - Remove all references to `AppRegion` in parameter overrides and mappings
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 1.1_
  
  - [x] 1.2 Remove MOCKNEST_APP_REGION environment variable
    - Remove `MOCKNEST_APP_REGION` from Lambda function environment variables in SAM template
    - Remove `aws.region` property from `application.properties`
    - Remove any references to `MOCKNEST_APP_REGION` in configuration classes
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 1.2, 10.1, 10.2_
  
  - [x] 1.3 Update runtime to use AWS_REGION environment variable
    - Update `BedrockConfiguration` to read region from `AWS_REGION` environment variable
    - Update any S3 client configuration to use `AWS_REGION`
    - Ensure all AWS SDK clients use the deployment region from `AWS_REGION`
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 1.3, 1.4, 10.3, 10.4_
  
  - [x] 1.4 Write unit tests for region configuration
    - Test that Bedrock client uses region from `AWS_REGION`
    - Test that S3 client uses region from `AWS_REGION`
    - Test fallback behavior when `AWS_REGION` is not set
    - _Requirements: 1.5_

- [x] 2. Implement InferencePrefixResolver component
  - [x] 2.1 Create InferenceMode enum
    - Create `InferenceMode` enum in `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/ai/config/`
    - Define three modes: `AUTO`, `GLOBAL_ONLY`, `GEO_ONLY`
    - Add documentation comments explaining each mode
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.1_
  
  - [x] 2.2 Create InferencePrefixResolver interface
    - Define interface with `getCandidatePrefixes(): List<String>` method
    - Add `deployRegion: String` property
    - Include comprehensive KDoc documentation
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.4_
  
  - [x] 2.3 Implement DefaultInferencePrefixResolver
    - Implement geo prefix derivation logic (eu-* → eu, us-* → us, ap-* → ap, ca-* → ca, me-* → me, sa-* → sa, af-* → af)
    - Implement candidate prefix generation for AUTO mode: [global, geo_prefix]
    - Implement candidate prefix generation for GLOBAL_ONLY mode: [global]
    - Implement candidate prefix generation for GEO_ONLY mode: [geo_prefix]
    - Add warning logging for unknown region prefixes with fallback to "us"
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.4, 3.5, 3.6, 3.7_
  
  - [x] 2.4 Write unit tests for InferencePrefixResolver
    - Test geo prefix derivation for all AWS region patterns
    - Test candidate prefix generation for each InferenceMode
    - Test unknown region handling with default fallback
    - Use parameterized tests for comprehensive region coverage
    - _Requirements: 3.4, 3.5, 3.6, 3.7_
  
  - [x] 2.5 Write property test for geo prefix derivation
    - **Property 3: Geo Prefix Derivation**
    - **Validates: Requirements 3.4**
    - Generate random AWS region strings with various prefixes
    - Verify correct geo prefix derivation for all valid patterns
    - Test edge cases (unknown prefixes, malformed regions)
    - Minimum 100 iterations

- [x] 3. Enhance ModelConfiguration with fallback strategy
  - [x] 3.1 Add InferencePrefixResolver dependency to ModelConfiguration
    - Update `ModelConfiguration` constructor to accept `InferencePrefixResolver`
    - Update Spring configuration to wire `InferencePrefixResolver` bean
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.8_
  
  - [x] 3.2 Implement prefix retry logic
    - Implement loop through candidate prefixes from resolver
    - Add `isRetryableError()` method to detect model-not-found, access-denied, not-enabled errors
    - Implement retry on retryable errors, immediate throw on non-retryable errors
    - Add DEBUG logging for each prefix attempt
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.8, 3.9, 4.1, 4.2, 4.4_
  
  - [x] 3.3 Implement prefix caching
    - Add `cachedPrefix: String?` property to `ModelConfiguration`
    - Cache successful prefix after first successful invocation
    - Use cached prefix for subsequent invocations without retry
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 4.5_
  
  - [x] 3.4 Implement final fallback and error handling
    - Attempt model without prefix if all candidates fail
    - Throw `ModelConfigurationException` with detailed error message including region, model name, and attempted prefixes
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.10, 3.11, 4.3_
  
  - [x] 3.5 Implement model name fallback
    - Add try-catch around model name mapping using reflection
    - Log warning and fall back to `AmazonNovaPro` for invalid model names
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 2.4, 2.5_
  
  - [x] 3.6 Write unit tests for ModelConfiguration
    - Test valid model name mapping using reflection
    - Test invalid model name fallback to AmazonNovaPro
    - Test prefix retry logic with mock Bedrock errors
    - Test non-retryable error propagation
    - Test successful prefix caching
    - Test logging at appropriate levels
    - Mock Koog BedrockModels and LLModel for isolation
    - _Requirements: 2.4, 2.5, 3.8, 3.9, 3.10, 3.11, 4.1, 4.2, 4.4, 4.5_
  
  - [ ]* 3.7 Write property test for model name mapping robustness
    - **Property 2: Model Name Mapping Robustness**
    - **Validates: Requirements 2.4, 2.5**
    - Generate valid and invalid model name strings
    - Verify either successful mapping or fallback to AmazonNovaPro
    - Verify warning logs for invalid names
    - Minimum 100 iterations
  
  - [ ]* 3.8 Write property test for inference prefix retry
    - **Property 4: Inference Prefix Retry with Fallback**
    - **Validates: Requirements 3.8, 3.9, 4.1**
    - Generate various candidate prefix lists
    - Simulate retryable errors at different positions
    - Verify correct retry sequence and eventual success or failure
    - Minimum 100 iterations

- [x] 4. Update SAM template parameters
  - [x] 4.1 Add BedrockInferenceMode parameter
    - Add parameter with allowed values: AUTO, GLOBAL_ONLY, GEO_ONLY
    - Set default value to AUTO
    - Add comprehensive description explaining each mode
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 3.1, 3.2_
  
  - [x] 4.2 Update BedrockModelName parameter
    - Set default value to `AmazonNovaPro`
    - Update description to indicate "Amazon Nova Pro is officially supported and tested"
    - Keep allowed values matching Koog BedrockModels enum names
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 2.1, 2.2, 11.1, 11.2, 11.3_
  
  - [x] 4.3 Update Lambda environment variables
    - Add `BEDROCK_INFERENCE_MODE` environment variable mapped to `BedrockInferenceMode` parameter
    - Ensure `BEDROCK_MODEL_NAME` is mapped to `BedrockModelName` parameter
    - Remove `MOCKNEST_APP_REGION` environment variable
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 2.3, 3.3_
  
  - [ ]* 4.4 Write SAM template validation tests
    - Parse template YAML and verify parameter structure
    - Verify parameter allowed values and defaults
    - Verify environment variable mappings
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 11.1, 11.2, 11.3_

- [x] 5. Tighten IAM permissions in SAM template
  - [x] 5.1 Remove bedrock:ListFoundationModels action
    - Remove `bedrock:ListFoundationModels` from IAM policy
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 5.1_
  
  - [x] 5.2 Scope Bedrock permissions to deployment region
    - Update Resource ARN to use `!Sub "arn:aws:bedrock:${AWS::Region}:*:*"`
    - Add comment documenting resource scope and why it includes both foundation models and inference profiles
    - Ensure policy includes `bedrock:InvokeModel` and `bedrock:InvokeModelWithResponseStream`
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [ ]* 5.3 Write IAM policy validation tests
    - Parse template and verify IAM policy structure
    - Verify `bedrock:ListFoundationModels` is not present
    - Verify resource scoping to deployment region
    - Verify required actions are present
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 6. Add SAR metadata to SAM template
  - [x] 6.1 Add AWS::ServerlessRepo::Application metadata section
    - Add `Metadata` section with `AWS::ServerlessRepo::Application`
    - Include Name: "MockNest-Serverless"
    - Include comprehensive Description summarizing application purpose
    - Include Author field
    - Include SemanticVersion: "1.0.0"
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 13.1, 13.2_
  
  - [x] 6.2 Add SAR metadata URLs and labels
    - Add LicenseUrl pointing to LICENSE file
    - Add ReadmeUrl pointing to README-SAR.md
    - Add HomePageUrl pointing to GitHub repository
    - Add SourceCodeUrl pointing to GitHub repository
    - Add Labels: ["mock", "testing", "wiremock", "serverless", "ai", "bedrock"]
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 6.5, 6.6, 6.7, 6.8, 6.9, 13.7_
  
  - [ ]* 6.3 Write SAR metadata validation tests
    - Parse template and verify all metadata fields present
    - Verify SemanticVersion format
    - Verify URLs are valid
    - Verify Labels array contains expected values
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 13.1, 13.2, 13.7_

- [x] 7. Implement separate health check endpoints
  - [x] 7.1 Create RuntimeHealthController
    - Create controller at `software/infra/aws/runtime/src/main/kotlin/nl/vintik/mocknest/infra/aws/runtime/health/`
    - Implement `GET /__admin/health` endpoint
    - Return JSON response with status, timestamp, region, storage bucket name, and connectivity status
    - Implement storage connectivity check using `storage.exists()` probe
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  
  - [x] 7.2 Create AIHealthController
    - Create controller at `software/infra/aws/generation/src/main/kotlin/nl/vintik/mocknest/infra/aws/generation/health/`
    - Implement `GET /ai/health` endpoint
    - Return JSON response with status, timestamp, region, model name, inference prefix, inference mode
    - Include `lastInvocationSuccess` field (null if not yet invoked)
    - Include `officiallySupported` field based on model name
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 8.5, 8.6, 8.7, 8.8, 11.6_
  
  - [x] 7.3 Create health response data models
    - Create `RuntimeHealthResponse` and `StorageHealth` data classes
    - Create `AIHealthResponse` and `AIHealth` data classes
    - Ensure all fields are properly serialized to JSON
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 8.3, 8.7, 8.10_
  
  - [x] 7.4 Write unit tests for health controllers
    - Test runtime health endpoint response structure
    - Test AI health endpoint response structure
    - Test storage connectivity checking
    - Test officially supported model indication
    - Mock storage and model configuration dependencies
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 11.6_
  
  - [ ]* 7.5 Write property test for health response completeness
    - **Property 8: Health Response Completeness**
    - **Validates: Requirements 8.3, 8.7, 8.10**
    - Generate various system states (healthy, degraded, error)
    - Verify all required fields present in responses
    - Verify JSON format validity
    - Minimum 100 iterations

- [x] 8. Remove local filesystem support
  - [x] 8.1 Remove CompositeMappingsSource
    - Remove or simplify `CompositeMappingsSource` to only use S3-backed storage
    - Update configuration to use `ObjectStorageMappingsSource` directly
    - Remove any classpath or filesystem fallback logic
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 24.1, 24.2, 24.3_
  
  - [x] 8.2 Remove classpath health check mapping
    - Ensure built-in health check endpoint is not loaded from classpath
    - Implement health check as standard controller endpoint (already done in task 7)
    - Remove any classpath resource loading for mappings
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 24.6_
  
  - [x] 8.3 Update configuration to enforce S3-only storage
    - Remove any filesystem paths from configuration
    - Update documentation to clearly state all mocks must be in S3
    - Run existing unit tests to ensure no regressions: `./gradlew test`
    - _Requirements: 24.4, 24.5_
  
  - [ ]* 8.4 Write integration tests for S3-only storage
    - Verify mappings load only from S3
    - Verify no filesystem or classpath sources are used
    - Test that system works without local filesystem access
    - _Requirements: 24.1, 24.2, 24.3, 24.7_
  
  - [ ]* 8.5 Write property test for S3-only storage
    - **Property 10: S3-Only Storage**
    - **Validates: Requirements 24.1, 24.2, 24.3**
    - Generate various mapping and file operations
    - Verify all operations use S3-backed storage exclusively
    - Verify no filesystem or classpath access attempts
    - Minimum 100 iterations

- [x] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Create comprehensive documentation
  - [x] 10.1 Restructure README.md for user personas
    - Add architecture diagram from `docs/images/SolutionDesign.png` to show AWS services and data flow
    - Create "Quick Start for SAR Users" section as primary path
    - Create "Deployment for Developers" section for SAM-based deployment
    - Create "Tested Configuration" section documenting officially supported regions and models
    - Create "Contributing" section linking to CONTRIBUTING.md
    - Clearly separate user concerns from developer concerns
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9_
  
  - [x] 10.2 Create README-SAR.md for SAR users
    - Create SAR-specific README at repository root
    - Include architecture diagram from `docs/images/SolutionDesign.png` to show system overview
    - Include "How to Use" section with post-deployment instructions
    - Include input parameter documentation with examples
    - Include output format documentation with examples
    - Include "Common Use Cases" section with practical examples
    - Include "Error Handling" section explaining common errors
    - Include "Cost" section explaining AWS service costs and Free Tier eligibility
    - Include "Security" section explaining IAM permissions and resource scoping
    - Do NOT include developer setup instructions
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7, 19.8, 19.9, 19.10, 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 21.7, 21.8_
  
  - [x] 10.3 Create USER_GUIDE.md
    - Create comprehensive user guide at `docs/USER_GUIDE.md`
    - Include "Getting Started" section with first mock creation
    - Include "Core Features" section documenting tested WireMock functionality
    - Include "AI-Assisted Mock Generation" section (when AI features enabled)
    - Include "Common Workflows" section with practical examples
    - Include "Troubleshooting" section with common issues and solutions
    - Include "Limitations" section documenting known limitations in serverless environment
    - Include "API Reference" section linking to OpenAPI specification
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8, 17.9_
  
  - [x] 10.4 Create OpenAPI specification
    - Create OpenAPI 3.0 spec at `docs/api/mocknest-openapi.yaml`
    - Document all WireMock admin API endpoints exposed by MockNest
    - Document all AI generation endpoints (when AI features enabled)
    - Document health check endpoints
    - Include request/response schemas for all endpoints
    - Include authentication requirements (API key header)
    - Include example requests and responses
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7_
  
  - [x] 10.5 Update README with documentation links
    - Add link to USER_GUIDE.md in README
    - Add link to OpenAPI specification in README
    - Add link to USER_GUIDE.md in README-SAR
    - _Requirements: 16.8, 17.9_
  
  - [x] 10.6 Create CHANGELOG.md
    - Create CHANGELOG.md at repository root
    - Document version 1.0.0 release with categorized changes
    - Use categories: Added, Changed, Fixed, Removed, Security
    - Follow semantic versioning format
    - _Requirements: 22.1, 22.2, 22.3, 22.4_
  
  - [x] 10.7 Update README with deployment documentation
    - Add "Deployment from AWS Serverless Application Repository" section
    - Explain that users select deployment region in AWS Console
    - Explain Bedrock model availability varies by region
    - Explain that model access must be enabled before deployment
    - Document `BedrockInferenceMode` parameter with descriptions of AUTO, GLOBAL_ONLY, GEO_ONLY
    - Recommend using AUTO mode for most use cases
    - Explain when to use GLOBAL_ONLY and GEO_ONLY modes
    - Include link to AWS Bedrock model availability documentation
    - Include "Support" subsection directing users to GitHub Issues
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9_
  
  - [x] 10.8 Document tested regions and features
    - Add "Tested Configuration" section to README listing us-east-1, eu-west-1, ap-southeast-1
    - State that core runtime works in any AWS region with Lambda, API Gateway, and S3
    - State that AI features are tested only with Amazon Nova Pro in the three tested regions
    - Indicate that deployment to other regions is possible but not officially supported
    - Indicate that other Bedrock models may work but are not officially supported
    - List which WireMock features have been tested in serverless environment
    - Do NOT claim support for untested WireMock features
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8_
  
  - [x] 10.9 Document error handling and troubleshooting
    - Add "Error Handling" section to README-SAR
    - Document common error scenarios with solutions
    - Explain Bedrock access errors (model not available, access not enabled)
    - Explain API Gateway authentication errors (invalid API key)
    - Explain S3 permission errors
    - Explain Lambda timeout errors
    - Include instructions for viewing CloudWatch logs
    - Include link to GitHub Issues for reporting bugs
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7, 23.8_

- [ ] 11. Implement integration tests
  - [ ]* 11.1 Create LocalStack integration test setup
    - Set up TestContainers with LocalStack for S3 and Bedrock
    - Configure proper lifecycle management with @BeforeAll/@AfterAll
    - Use TestContainers waiting strategies for service readiness
    - Share expensive resources (containers, clients) across tests
    - _Requirements: 14.4, 14.5_
  
  - [ ]* 11.2 Write core runtime integration tests
    - Test mock creation, serving, and persistence in LocalStack
    - Test S3 storage operations work correctly
    - Test health endpoint returns correct information
    - _Requirements: 14.4_
  
  - [ ]* 11.3 Write AI features integration tests
    - Test AI generation with Amazon Nova Pro (using LocalStack Bedrock emulation)
    - Test automatic inference prefix selection
    - Test prefix fallback behavior
    - _Requirements: 14.5_
  
  - [ ]* 11.4 Write property test for region configuration consistency
    - **Property 1: Region Configuration Consistency**
    - **Validates: Requirements 1.3, 1.4, 10.3**
    - Generate random AWS region strings
    - Verify all SDK clients use the region from AWS_REGION
    - Minimum 100 iterations

- [x] 12. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Simplify SAR deployment architecture
  - [x] 13.1 Evaluate current SAR deployment complexity
    - Review current dual-template approach: `sam/template.yaml` vs `sar/deploy-sar-app.yml`
    - Analyze parameter synchronization issues between templates
    - Compare against AWS Lambda Power Tuning approach (direct SAM template publishing)
    - Document pros/cons of current approach vs simplified direct deployment
    - _Requirements: Analysis of deployment complexity_
  
  - [x] 13.2 Audit parameter differences between templates
    - Compare parameters in `sam/template.yaml` vs `sar/deploy-sar-app.yml`
    - Identify parameter name, type, and default value mismatches
    - Document all parameter drift issues found
    - Create validation script to detect future parameter drift
    - _Requirements: Deployment consistency_
  
  - [x] 13.3 Choose simplified deployment architecture
    - **Recommended**: Remove SAR folder, publish directly from SAM template
    - Remove `deployment/aws/sar/deploy-sar-app.yml` wrapper template
    - Keep SAR scripts (`deploy-sar-app.sh`, `publish-sar.sh`) but update for direct SAM publishing
    - Update SAM template metadata for direct SAR publication
    - _Requirements: Simplified deployment experience_
  
  - [x] 13.4 Update deployment scripts for simplified architecture
    - Update `deployment/aws/sar/publish-sar.sh` to use `sam publish` directly on SAM template
    - Update `deployment/aws/sar/deploy-sar-app.sh` for direct SAR deployment
    - Remove references to wrapper template from all scripts
    - Test scripts work with simplified architecture
    - _Requirements: Working deployment scripts_
  
  - [x] 13.5 Update documentation for simplified deployment
    - Update README with simplified SAR deployment instructions
    - Remove references to wrapper template from all documentation
    - Update SAR deployment guides to match actual implementation
    - Ensure documentation accuracy with simplified approach
    - _Requirements: Accurate deployment documentation_

- [x] 14. Create pipeline-based private SAR deployment
  - [x] 14.1 Create GitHub Actions workflow for SAR deployment
    - Create `.github/workflows/sar-deploy.yml` workflow file
    - Add workflow triggers: manual dispatch with version parameter
    - Configure AWS credentials using OIDC (existing setup)
    - Add environment variables for test AWS account IDs
    - _Requirements: Automated SAR deployment pipeline_
  
  - [x] 14.2 Implement build and package steps
    - Add Kotlin build step: `./gradlew build`
    - Add test execution step: `./gradlew test`
    - Add SAM build step: `sam build`
    - Add SAM package step with S3 bucket for artifacts
    - Ensure build artifacts are properly prepared for SAR publishing
    - _Requirements: Automated build and packaging_
  
  - [x] 14.3 Implement private SAR publication
    - Add step to publish to SAR with beta version (e.g., `0.2.0-beta.1`)
    - Publish from `us-east-1` region (AWS SAR requirement)
    - Share application with specific test AWS account IDs only (not public)
    - Add step to verify SAR publication succeeded
    - _Requirements: Private SAR publication_
  
  - [x] 14.4 Implement multi-region deployment testing
    - Add matrix strategy for 3 regions: `us-east-1`, `eu-west-1`, `ap-southeast-1`
    - Deploy SAR application in test AWS account for each region
    - Use different stack names per region to avoid conflicts
    - Capture CloudFormation outputs (API URL, API Key) for testing
    - _Requirements: Multi-region deployment validation_
  
  - [x] 14.5 Implement automated functionality testing
    - Add test steps for core runtime functionality:
      - Create mock via admin API
      - Serve mock via mocked endpoint
      - Verify mock persistence across Lambda cold starts
      - Test health endpoint response
    - Add test steps for AI functionality (where Bedrock available):
      - Test AI generation endpoint
      - Verify AI health endpoint
      - Test automatic inference prefix selection
    - Use captured API URL and API Key from deployment outputs
    - _Requirements: Automated functionality validation_
  
  - [x] 14.6 Add deployment cleanup and reporting
    - Add cleanup steps to delete test CloudFormation stacks after testing
    - Add step to collect and report test results
    - Add step to update GitHub issue or PR with test results
    - Ensure cleanup runs even if tests fail
    - _Requirements: Clean test environment and reporting_

- [ ] 15. Public SAR release pipeline
  - [ ] 15.1 Create public release workflow
    - Create `.github/workflows/sar-release.yml` workflow file
    - Add workflow trigger: manual dispatch with release version parameter
    - Add prerequisite check: ensure private SAR testing passed
    - Configure same AWS credentials and build steps as private workflow
    - _Requirements: Automated public release pipeline_
  
  - [ ] 15.2 Implement public SAR publication
    - Add step to update SAM template SemanticVersion to release version
    - Add step to publish to SAR with release version (e.g., `0.2.0`)
    - Add step to make SAR application public using `aws serverlessrepo put-application-policy`
    - Set Principals='*' and Actions=Deploy for public access
    - _Requirements: Public SAR publication_
  
  - [ ] 15.3 Implement release documentation updates
    - Add step to update CHANGELOG.md with release date
    - Add step to create Git tag for release version
    - Add step to create GitHub release with changelog content
    - Add step to update README if needed for release
    - _Requirements: Release documentation and tagging_
  
  - [ ] 15.4 Add release validation and rollback
    - Add step to verify public SAR application is accessible
    - Add step to test public deployment in fresh AWS account
    - Add rollback mechanism if public release validation fails
    - Add notification step for successful public release
    - _Requirements: Release validation and safety_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end functionality in AWS environment
- **Pipeline-based approach ensures consistent, repeatable SAR deployments**
- **Private testing in multiple regions before public release ensures quality**
- **Simplified architecture reduces deployment complexity and maintenance overhead**