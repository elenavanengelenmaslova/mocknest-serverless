# IAM Rate Limiting Fix — Bugfix Design

## Overview

The MockNest Serverless SAM template has a rate limiting gap: IAM-mode API Gateway deployments have no usage plan or throttling, allowing unlimited requests to hit Lambda functions. Additionally, the API_KEY mode usage plan has hardcoded throttle values that cannot be customized by SAR deployers. This fix adds configurable SAM parameters (`ThrottleBurstLimit`, `ThrottleRateLimit`) and creates a new UsagePlan resource for IAM mode, while updating the existing API_KEY mode UsagePlan to use the same parameters. A secondary fix removes "2026" from the competition name in README.md.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — deploying with `AuthMode=IAM` results in no rate limiting, or deploying with `AuthMode=API_KEY` results in non-configurable hardcoded throttle values
- **Property (P)**: The desired behavior — both auth modes apply configurable throttling via UsagePlan resources with sensible defaults
- **Preservation**: Existing API_KEY mode behavior (API key creation, UsagePlanKey association, default throttle values of BurstLimit=1/RateLimit=100) must remain unchanged when default parameter values are used
- **UsagePlan**: An AWS API Gateway resource that applies throttling (BurstLimit, RateLimit) and optional quotas to an API stage
- **ThrottleBurstLimit**: SAM parameter controlling the maximum number of concurrent requests allowed (burst capacity)
- **ThrottleRateLimit**: SAM parameter controlling the steady-state request rate (requests per second)
- **IsIamMode / IsApiKeyMode**: CloudFormation conditions that determine which resources are deployed based on the `AuthMode` parameter

## Bug Details

### Bug Condition

The bug manifests in two scenarios: (1) when `AuthMode=IAM` is selected, no UsagePlan is created, leaving the API Gateway completely unthrottled; (2) when `AuthMode=API_KEY` is selected, the UsagePlan uses hardcoded values that cannot be customized at deployment time. A secondary documentation bug exists where the README includes "2026" in the competition name.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SAMDeploymentConfig
  OUTPUT: boolean
  
  RETURN (input.AuthMode == 'IAM' AND noUsagePlanExists(input.deployedResources))
         OR (input.AuthMode == 'API_KEY' AND throttleValuesAreHardcoded(input.templateResources))
         OR (input.AuthMode IN ['IAM', 'API_KEY'] AND noThrottleParametersExist(input.templateParameters))
         OR (input.readmeContent CONTAINS '2026' IN competitionReference)
END FUNCTION
```

### Examples

- **IAM mode, no throttling**: Deploy with `AuthMode=IAM` → API Gateway has no UsagePlan → unlimited requests hit Lambda → potential cost overrun
- **API_KEY mode, hardcoded values**: Deploy with `AuthMode=API_KEY` → UsagePlan has BurstLimit=1, RateLimit=100 hardcoded → SAR user with high-throughput workload cannot increase limits without forking the template
- **SAR deployment, no parameters**: User deploys via SAR → no `ThrottleBurstLimit` or `ThrottleRateLimit` parameters available → no way to customize throttling
- **README incorrect name**: User reads README → sees "AWS 10,000 AIdeas 2026 Competition" → incorrect competition name (should be "AWS 10,000 AIdeas Competition")

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- API_KEY mode must continue to create an API key and associate it with the usage plan via a UsagePlanKey resource
- IAM mode must continue to use AWS_IAM as the default authorizer for the API Gateway
- When default parameter values are used with API_KEY mode, the effective throttling must remain BurstLimit=1 and RateLimit=100 (same as current hardcoded values)
- All Lambda functions, S3 bucket, SQS queues, and CloudWatch log groups must remain unchanged regardless of AuthMode
- IAM mode must continue to NOT create an API key or UsagePlanKey resource

**Scope:**
All inputs that do NOT involve rate limiting configuration or the README competition reference should be completely unaffected by this fix. This includes:
- Lambda function configurations (memory, timeout, environment variables, SnapStart)
- S3 bucket configuration and lifecycle rules
- SQS queue configurations (webhook queue, DLQ)
- IAM roles and policies
- CloudWatch log groups
- API Gateway route definitions and Lambda integrations
- All other SAM parameters (DeploymentName, RuntimeLambdaMemorySize, etc.)

## Hypothesized Root Cause

Based on the bug description, the most likely issues are:

1. **Missing IAM-mode UsagePlan resource**: The template only defines `MockNestUsagePlan` with `Condition: IsApiKeyMode`. No equivalent resource exists for IAM mode. This is an oversight — rate limiting is independent of authentication mode and should apply to both.

2. **Hardcoded throttle values in template**: The `MockNestUsagePlan` resource has `BurstLimit: 1` and `RateLimit: 100` as literal values in the YAML rather than referencing SAM parameters. This prevents customization at deployment time.

3. **Missing SAM parameters**: The Parameters section of the template does not include any parameters for throttle configuration, so SAR users have no mechanism to override the defaults.

4. **README typo**: The competition name includes "2026" which is not part of the official competition name "AWS 10,000 AIdeas Competition".

## Correctness Properties

Property 1: Bug Condition - IAM Mode Rate Limiting

_For any_ deployment where `AuthMode=IAM`, the fixed template SHALL create a UsagePlan resource with throttling configuration (using `ThrottleBurstLimit` and `ThrottleRateLimit` parameter values) attached to the IAM-mode API Gateway stage, ensuring rate limiting is applied.

**Validates: Requirements 2.1, 2.3**

Property 2: Bug Condition - Configurable Throttle Parameters

_For any_ deployment (both IAM and API_KEY modes), the fixed template SHALL use the `ThrottleBurstLimit` and `ThrottleRateLimit` SAM parameters for throttle configuration, allowing deployers to customize rate limiting values at deployment time.

**Validates: Requirements 2.2, 2.3**

Property 3: Preservation - Default Behavior Unchanged

_For any_ deployment using default parameter values with `AuthMode=API_KEY`, the fixed template SHALL produce the same effective throttling behavior (BurstLimit=1, RateLimit=100) and the same API key / UsagePlanKey association as the original template.

**Validates: Requirements 3.1, 3.3, 3.5**

Property 4: Preservation - Non-Throttle Resources Unchanged

_For any_ deployment regardless of AuthMode, the fixed template SHALL produce identical Lambda functions, S3 bucket, SQS queues, IAM roles, and CloudWatch log groups as the original template, preserving all non-throttle infrastructure.

**Validates: Requirements 3.2, 3.4**

Property 5: Bug Condition - README Competition Name

_For any_ reader of README.md, the fixed file SHALL display "AWS 10,000 AIdeas Competition" without the year "2026" in the competition name references.

**Validates: Requirements 2.4**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `deployment/aws/sam/template.yaml`

**Specific Changes**:

1. **Add ThrottleBurstLimit parameter**: Add a new SAM parameter in the Parameters section:
   - Type: Number
   - Default: 1
   - MinValue: 1
   - MaxValue: 5000
   - Description explaining it controls the maximum concurrent request burst capacity

2. **Add ThrottleRateLimit parameter**: Add a new SAM parameter in the Parameters section:
   - Type: Number
   - Default: 100
   - MinValue: 1
   - MaxValue: 10000
   - Description explaining it controls the steady-state request rate (requests/second)

3. **Create IAM-mode UsagePlan resource**: Add a new `MockNestIamUsagePlan` resource:
   - Type: `AWS::ApiGateway::UsagePlan`
   - Condition: `IsIamMode`
   - DependsOn: `MockNestIamModeApiStage` (the stage created by SAM for the IAM API)
   - ApiStages referencing `MockNestIamModeApi` and `DeploymentName`
   - Throttle using `!Ref ThrottleBurstLimit` and `!Ref ThrottleRateLimit`

4. **Update existing API_KEY-mode UsagePlan**: Modify `MockNestUsagePlan` to use parameters:
   - Replace `BurstLimit: 1` with `BurstLimit: !Ref ThrottleBurstLimit`
   - Replace `RateLimit: 100` with `RateLimit: !Ref ThrottleRateLimit`

5. **No UsagePlanKey for IAM mode**: The IAM-mode UsagePlan does NOT need a UsagePlanKey association since IAM mode uses SigV4 signing, not API keys.

---

**File**: `README.md`

**Specific Changes**:

1. **Remove "2026" from competition references**: Change both occurrences:
   - Line 22: `AWS 10,000 AIdeas 2026 Competition` → `AWS 10,000 AIdeas Competition`
   - Line 570: `AWS 10,000 AIdeas 2026 Competition` → `AWS 10,000 AIdeas Competition`

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Validate the SAM template structure by inspecting the YAML for the presence/absence of resources and parameter references. Run `sam validate` to confirm template syntax. Deploy with `AuthMode=IAM` and verify no UsagePlan exists.

**Test Cases**:
1. **IAM Mode Missing UsagePlan**: Inspect template for any UsagePlan with `Condition: IsIamMode` (will find none on unfixed code)
2. **Hardcoded Throttle Values**: Inspect `MockNestUsagePlan` for literal `BurstLimit: 1` and `RateLimit: 100` instead of parameter references (will find hardcoded values on unfixed code)
3. **Missing Parameters**: Inspect Parameters section for `ThrottleBurstLimit` and `ThrottleRateLimit` (will find none on unfixed code)
4. **README Incorrect Name**: Search README.md for "2026" in competition references (will find two occurrences on unfixed code)

**Expected Counterexamples**:
- No UsagePlan resource exists with `Condition: IsIamMode`
- Throttle values are literal numbers, not `!Ref` parameter references
- Possible causes: oversight during initial template creation, IAM mode added later without considering throttling parity

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL deployment WHERE isBugCondition(deployment) DO
  result := deployFixedTemplate(deployment)
  ASSERT usagePlanExists(result, deployment.AuthMode)
  ASSERT throttleValuesMatchParameters(result, deployment.ThrottleBurstLimit, deployment.ThrottleRateLimit)
  ASSERT readmeDoesNotContain2026(result)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL deployment WHERE NOT isBugCondition(deployment) DO
  ASSERT deployOriginalTemplate(deployment).nonThrottleResources = deployFixedTemplate(deployment).nonThrottleResources
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many parameter combinations automatically across the input domain
- It catches edge cases that manual unit tests might miss (e.g., boundary values for throttle parameters)
- It provides strong guarantees that non-throttle resources are unchanged for all configurations

**Test Plan**: Observe behavior on UNFIXED code first for non-throttle resources (Lambda configs, S3, SQS, IAM roles), then write property-based tests capturing that behavior.

**Test Cases**:
1. **Default API_KEY Preservation**: Deploy with `AuthMode=API_KEY` and default throttle parameters → verify effective BurstLimit=1, RateLimit=100 (same as before)
2. **API Key Association Preservation**: Deploy with `AuthMode=API_KEY` → verify API key and UsagePlanKey still created and associated
3. **Lambda Configuration Preservation**: Deploy with any AuthMode → verify all Lambda functions have identical configuration
4. **IAM Authorizer Preservation**: Deploy with `AuthMode=IAM` → verify AWS_IAM authorizer still configured
5. **No API Key in IAM Mode**: Deploy with `AuthMode=IAM` → verify no API key or UsagePlanKey created

### Unit Tests

- Validate SAM template syntax with `sam validate` after changes
- Verify ThrottleBurstLimit parameter has correct type, default, min/max constraints
- Verify ThrottleRateLimit parameter has correct type, default, min/max constraints
- Verify IAM-mode UsagePlan has correct condition, API stage reference, and throttle references
- Verify API_KEY-mode UsagePlan uses parameter references instead of hardcoded values
- Verify README.md does not contain "2026" in competition references

### Property-Based Tests

- Generate random valid ThrottleBurstLimit values (1-5000) and verify they are accepted by the template parameter constraints
- Generate random valid ThrottleRateLimit values (1-10000) and verify they are accepted by the template parameter constraints
- Generate deployments with both AuthMode values and verify UsagePlan exists in both cases
- Generate deployments with various parameter combinations and verify non-throttle resources remain identical

### Integration Tests

- Deploy SAM template with `AuthMode=IAM` and verify UsagePlan is created in CloudFormation stack
- Deploy SAM template with `AuthMode=API_KEY` and custom throttle values, verify UsagePlan reflects custom values
- Deploy SAM template with default values and verify backward-compatible behavior
- Run `sam validate --template-file deployment/aws/sam/template.yaml --region eu-west-1` and confirm exit code 0
