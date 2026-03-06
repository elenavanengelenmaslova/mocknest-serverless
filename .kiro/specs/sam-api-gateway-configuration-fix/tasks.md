# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Fault Condition** - SAM Template Missing API Key Resources and Multiple Stages
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bugs exist
  - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing cases to ensure reproducibility
  - Test that SAM template deployment creates API key resources (AWS::ApiGateway::ApiKey, UsagePlan, UsagePlanKey)
  - Test that API Gateway shows only the configured stage (not "Stage" or multiple stages)
  - Test that parameter is named "DeploymentName" (not "StageName") with appropriate default
  - Test that Lambda functions start successfully with minimized JARs (no ClassNotFoundException)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bugs exist)
  - Document counterexamples found:
    - Missing API key resources in CloudFormation stack
    - Multiple stages visible in API Gateway console
    - Parameter named "StageName" with default "v1"
    - Lambda fails with ClassNotFoundException for FunctionInvoker
  - Mark task complete when test is written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Deployment Functionality
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for non-buggy configurations:
    - Lambda function integrations with API Gateway routes
    - CloudWatch logging and metrics collection
    - API Gateway throttling settings (200 burst, 100 rate)
    - Resource creation (S3, Lambda, IAM, DLQ)
    - CloudFormation outputs availability
    - Parameter validation for custom values
  - Write property-based tests capturing observed behavior patterns from Preservation Requirements
  - Property-based testing generates many test cases for stronger guarantees
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

- [ ] 3. Fix SAM template and build configuration

  - [x] 3.1 Add API key resources to SAM template
    - Add AWS::ApiGateway::ApiKey resource with auto-generation enabled
    - Add AWS::ApiGateway::UsagePlan resource linked to the API and stage
    - Add AWS::ApiGateway::UsagePlanKey resource to associate key with usage plan
    - Configure usage plan with throttling limits matching existing MethodSettings
    - Add CloudFormation output exposing the generated API key value
    - _Bug_Condition: isBugCondition(input) where input.template.resources NOT CONTAINS "AWS::ApiGateway::ApiKey"_
    - _Expected_Behavior: API key resources exist and are properly linked, API requires x-api-key header_
    - _Preservation: All Lambda integrations, routing, CloudWatch logging, throttling, and resource creation remain unchanged_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.9, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.9_

  - [x] 3.2 Configure API Gateway to prevent multiple stages
    - Review AWS::Serverless::Api configuration for stage creation behavior
    - Add explicit stage configuration to prevent automatic "Stage" creation
    - Ensure only the configured stage is created
    - _Bug_Condition: isBugCondition(input) where input.apiGateway.stages.count > 1_
    - _Expected_Behavior: Only the configured stage exists in API Gateway_
    - _Preservation: All Lambda integrations, routing, and existing functionality remain unchanged_
    - _Requirements: 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.9_

  - [x] 3.3 Rename StageName parameter to DeploymentName
    - Change parameter name from "StageName" to "DeploymentName"
    - Update description to clarify it's a deployment instance identifier
    - Update default value from "v1" to "default" or "main"
    - Update all references throughout the template (API StageName property, outputs, etc.)
    - _Bug_Condition: isBugCondition(input) where input.template.parameters["StageName"].name == "StageName"_
    - _Expected_Behavior: Parameter named "DeploymentName" with clear deployment instance semantics_
    - _Preservation: Parameter validation and all existing functionality remain unchanged_
    - _Requirements: 2.7, 2.8, 3.8_

  - [x] 3.4 Fix Shadow plugin minimize() to preserve Spring Cloud adapter
    - Add exclude for spring-cloud-function-adapter-aws dependency in minimize() block
    - Ensure FunctionInvoker and related adapter classes are preserved in the JAR
    - Keep existing excludes for spring-boot-autoconfigure and spring-cloud-function-context
    - Apply fix to both shadowJarRuntime and shadowJarGeneration tasks
    - _Bug_Condition: isBugCondition(input) where "org.springframework.cloud.function.adapter.aws" NOT IN input.shadowPlugin.minimize.excludes_
    - _Expected_Behavior: Lambda functions start successfully, no ClassNotFoundException_
    - _Preservation: All existing Lambda functionality and behavior remain unchanged_
    - _Requirements: 2.10, 2.11, 2.12, 3.1, 3.2, 3.3, 3.4_

  - [x] 3.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - API Key Resources and Single Stage Configuration
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bugs are fixed)
    - Verify API key resources exist in CloudFormation stack
    - Verify only configured stage exists in API Gateway
    - Verify parameter is named "DeploymentName"
    - Verify Lambda functions start successfully
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12_

  - [x] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Deployment Functionality
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)
    - Verify Lambda integrations, routing, logging, throttling, and resource creation unchanged
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
