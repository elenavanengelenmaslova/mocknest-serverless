# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - IAM Mode Missing Rate Limiting and Hardcoded Throttle Values
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists in the SAM template
  - **Scoped PBT Approach**: Scope the property to concrete failing cases:
    1. No UsagePlan resource exists with `Condition: IsIamMode` in template.yaml
    2. `MockNestUsagePlan` has literal `BurstLimit: 1` and `RateLimit: 100` instead of `!Ref` parameter references
    3. No `ThrottleBurstLimit` or `ThrottleRateLimit` parameters exist in the Parameters section
    4. README.md contains "2026" in competition name references
  - Write a test (e.g., shell script or Kotlin test) that parses `deployment/aws/sam/template.yaml` and asserts:
    - A UsagePlan resource with `Condition: IsIamMode` exists (will fail on unfixed code)
    - `MockNestUsagePlan.Throttle.BurstLimit` uses `!Ref ThrottleBurstLimit` (will fail - finds hardcoded `1`)
    - `MockNestUsagePlan.Throttle.RateLimit` uses `!Ref ThrottleRateLimit` (will fail - finds hardcoded `100`)
    - Parameters section contains `ThrottleBurstLimit` and `ThrottleRateLimit` (will fail - not found)
    - README.md does not contain "2026" in competition references (will fail - finds two occurrences)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found:
    - No IAM-mode UsagePlan resource exists
    - Throttle values are literal numbers (BurstLimit: 1, RateLimit: 100)
    - No throttle parameters in Parameters section
    - README contains "AWS 10,000 AIdeas 2026 Competition" (two occurrences)
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Throttle Resources and API_KEY Mode Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for non-buggy inputs:
    - Observe: `MockNestUsagePlan` has `Condition: IsApiKeyMode` (preserved)
    - Observe: `MockNestUsagePlanKey` associates API key with usage plan (preserved)
    - Observe: `MockNestApiKey` resource exists with `Condition: IsApiKeyMode` (preserved)
    - Observe: `MockNestIamModeApi` uses `DefaultAuthorizer: AWS_IAM` (preserved)
    - Observe: No `MockNestApiKey` or `MockNestUsagePlanKey` resource has `Condition: IsIamMode` (preserved)
    - Observe: All Lambda functions (Runtime, Generation, RuntimeAsync) have identical configurations regardless of auth mode (preserved)
    - Observe: S3 bucket `MockStorage`, SQS queues (`MockNestDLQ`, `MockNestWebhookDLQ`, `MockNestWebhookQueue`), IAM roles, and CloudWatch log groups are unchanged (preserved)
  - Write property-based test that validates for all non-bug-condition cases:
    - API_KEY mode still creates `MockNestApiKey` and `MockNestUsagePlanKey` resources
    - IAM mode still uses `AWS_IAM` as DefaultAuthorizer
    - IAM mode does NOT create API key or UsagePlanKey resources
    - All Lambda function configurations (MemorySize, Timeout, SnapStart, Environment, Handler, Role) remain identical
    - S3 bucket, SQS queues, IAM roles, and CloudWatch log groups are unchanged
    - Default throttle values (BurstLimit=1, RateLimit=100) are preserved when default parameters are used
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix for IAM mode missing rate limiting and hardcoded throttle values

  - [x] 3.1 Add ThrottleBurstLimit and ThrottleRateLimit SAM parameters
    - Add `ThrottleBurstLimit` parameter to `deployment/aws/sam/template.yaml` Parameters section:
      - Type: Number
      - Default: 1
      - MinValue: 1
      - MaxValue: 5000
      - Description: Maximum number of concurrent requests allowed (burst capacity) for API Gateway throttling
    - Add `ThrottleRateLimit` parameter to `deployment/aws/sam/template.yaml` Parameters section:
      - Type: Number
      - Default: 100
      - MinValue: 1
      - MaxValue: 10000
      - Description: Steady-state request rate (requests per second) for API Gateway throttling
    - _Bug_Condition: isBugCondition(input) where noThrottleParametersExist(input.templateParameters)_
    - _Expected_Behavior: Parameters exist with correct type, defaults, and constraints_
    - _Preservation: Default values (1, 100) match current hardcoded values_
    - _Requirements: 2.2, 2.3, 3.3_

  - [x] 3.2 Create MockNestIamUsagePlan resource
    - Add new `MockNestIamUsagePlan` resource to `deployment/aws/sam/template.yaml`:
      - Type: `AWS::ApiGateway::UsagePlan`
      - Condition: `IsIamMode`
      - DependsOn: `MockNestIamModeApiStage`
      - UsagePlanName: `!Sub "${AWS::StackName}-iam-usage-plan"`
      - Description: Usage plan for MockNest Serverless IAM-mode API
      - ApiStages referencing `!Ref MockNestIamModeApi` and `!Ref DeploymentName`
      - Throttle with `BurstLimit: !Ref ThrottleBurstLimit` and `RateLimit: !Ref ThrottleRateLimit`
      - Tags with Application: MockNest-Serverless
    - Do NOT create a UsagePlanKey for IAM mode (IAM uses SigV4, not API keys)
    - _Bug_Condition: isBugCondition(input) where input.AuthMode == 'IAM' AND noUsagePlanExists_
    - _Expected_Behavior: UsagePlan exists for IAM mode with configurable throttling_
    - _Preservation: IAM mode continues to NOT create API key or UsagePlanKey_
    - _Requirements: 2.1, 2.3, 3.5_

  - [x] 3.3 Update MockNestUsagePlan to use parameter references
    - In `deployment/aws/sam/template.yaml`, modify `MockNestUsagePlan` resource:
      - Replace `BurstLimit: 1` with `BurstLimit: !Ref ThrottleBurstLimit`
      - Replace `RateLimit: 100` with `RateLimit: !Ref ThrottleRateLimit`
    - _Bug_Condition: isBugCondition(input) where throttleValuesAreHardcoded(input.templateResources)_
    - _Expected_Behavior: Throttle values reference SAM parameters instead of hardcoded literals_
    - _Preservation: With default parameter values (1, 100), effective behavior is identical to before_
    - _Requirements: 2.2, 2.3, 3.3_

  - [x] 3.4 Update README.md to remove "2026" from competition name
    - In `README.md`, change "AWS 10,000 AIdeas 2026 Competition" to "AWS 10,000 AIdeas Competition" (both occurrences)
    - _Bug_Condition: isBugCondition(input) where input.readmeContent CONTAINS '2026' IN competitionReference_
    - _Expected_Behavior: Competition name displays without year_
    - _Preservation: All other README content unchanged_
    - _Requirements: 2.4_

  - [x] 3.5 Update README Configuration Reference table
    - Add `ThrottleBurstLimit` and `ThrottleRateLimit` to the Configuration Reference table in README.md
    - Document parameter type, default value, constraints, and description
    - _Requirements: 2.3_

  - [x] 3.6 Update README-SAR.md if it contains a parameters table
    - Check if `README-SAR.md` documents SAM parameters
    - If yes, add `ThrottleBurstLimit` and `ThrottleRateLimit` entries
    - _Requirements: 2.3_

  - [x] 3.7 Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1`
    - Confirm exit code 0 (template is syntactically valid)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.8 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - IAM Mode Rate Limiting and Configurable Throttle Values
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied:
      - IAM-mode UsagePlan resource exists with correct condition and throttle references
      - API_KEY-mode UsagePlan uses parameter references
      - ThrottleBurstLimit and ThrottleRateLimit parameters exist with correct constraints
      - README.md no longer contains "2026" in competition references
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.9 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Throttle Resources and API_KEY Mode Behavior Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix:
      - API_KEY mode still creates API key and UsagePlanKey
      - IAM mode still uses AWS_IAM authorizer
      - IAM mode still does NOT create API key or UsagePlanKey
      - All Lambda, S3, SQS, IAM, and CloudWatch resources unchanged
      - Default throttle values preserved (BurstLimit=1, RateLimit=100)

- [x] 4. Checkpoint - Ensure all tests pass
  - Re-run all exploration and preservation tests
  - Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` one final time
  - Ensure all tests pass, ask the user if questions arise
