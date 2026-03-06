# Bug Condition Exploration Test Results

## Test Execution Summary

**Date**: 2025-01-03  
**Status**: ✅ Tests FAILED as expected (confirms bugs exist)  
**Test File**: `software/infra/aws/src/test/kotlin/nl/vintik/mocknest/infra/aws/bugfix/SamApiGatewayConfigurationBugTest.kt`

## Counterexamples Found

The bug condition exploration test successfully identified all four bugs in the unfixed code:

### Bug 1: Missing API Key Configuration

**Test**: `Given SAM template WHEN deployed THEN API key resources SHALL be created()`

**Counterexamples**:
- ❌ Missing `AWS::ApiGateway::ApiKey` resource in SAM template
- ❌ Missing `AWS::ApiGateway::UsagePlan` resource in SAM template  
- ❌ Missing `AWS::ApiGateway::UsagePlanKey` resource in SAM template
- ❌ No API key output in CloudFormation outputs

**Evidence**: 
```
org.opentest4j.AssertionFailedError: SAM template MUST contain AWS::ApiGateway::ApiKey resource. 
COUNTEREXAMPLE: Missing API key resource in template.
```

**Impact**: API Gateway endpoints are accessible without authentication, leaving the service unsecured.

---

### Bug 2: Multiple Unwanted Stages

**Test**: `Given SAM template WHEN deployed THEN only configured stage SHALL exist()`

**Counterexamples**:
- ❌ No explicit stage configuration in SAM template (no `OpenApiVersion` or `DefinitionBody`)
- ❌ Will create multiple stages including automatic "Stage" stage

**Evidence**:
```
Bug 2: No explicit stage configuration, will create multiple stages
```

**Impact**: After deployment, API Gateway console shows multiple stages (Stage, v1, and possibly others) instead of just the configured stage, causing confusion and potential routing issues.

---

### Bug 3: Misleading Parameter Naming

**Test**: `Given SAM template WHEN reviewed THEN parameter SHALL be named DeploymentName()`

**Counterexamples**:
- ❌ Parameter named `StageName` (not `DeploymentName`)
- ❌ Default value is `'v1'` which suggests API versioning semantics
- ❌ Parameter description suggests versioning rather than deployment instance identification

**Evidence**:
```
org.opentest4j.AssertionFailedError: SAM template MUST have parameter named 'DeploymentName'. 
COUNTEREXAMPLE: Parameter named 'StageName' found, which suggests API versioning.
```

**Impact**: Users are confused about whether to use version numbers (v1, v2) or instance names (team-alpha, payment-apis), leading to inconsistent naming conventions across deployments.

---

### Bug 4: Shadow Plugin Removing Spring Cloud Adapter

**Test**: `Given Shadow plugin minimize WHEN building JAR THEN Spring Cloud adapter SHALL be preserved()`

**Counterexamples**:
- ✅ `minimize {}` block exists in build configuration
- ❌ No exclude for `spring-cloud-function-adapter-aws` dependency
- ❌ Only excludes `spring-boot-autoconfigure` and `spring-cloud-function-context`

**Evidence**: The build.gradle.kts file shows:
```kotlin
minimize {
    // Only exclude absolute essentials that minimize() might incorrectly remove
    exclude(dependency("org.springframework.boot:spring-boot-autoconfigure"))
    exclude(dependency("org.springframework.cloud:spring-cloud-function-context"))
}
```

**Missing**: `exclude(dependency("org.springframework.cloud:spring-cloud-function-adapter-aws"))`

**Impact**: Lambda functions fail at runtime with `ClassNotFoundException: org.springframework.cloud.function.adapter.aws.FunctionInvoker`, causing all API requests to return 502 Bad Gateway errors.

---

## Comprehensive Bug Check

**Test**: `Given deployment configuration WHEN all bugs fixed THEN all expected behaviors SHALL be satisfied()`

**All Counterexamples Found**:
```
Bug condition exploration test FAILED as expected on unfixed code. Found 6 bug(s):
  - Bug 1: Missing AWS::ApiGateway::ApiKey resource
  - Bug 1: Missing AWS::ApiGateway::UsagePlan resource
  - Bug 1: Missing AWS::ApiGateway::UsagePlanKey resource
  - Bug 2: No explicit stage configuration, will create multiple stages
  - Bug 3: Parameter named 'StageName' with default 'v1' suggests versioning
  - Bug 3: Parameter not named 'DeploymentName'

This is the CORRECT outcome for unfixed code - these failures prove the bugs exist.
```

**Note**: Bug 4 (Shadow plugin) was not included in the comprehensive check because it requires examining the build.gradle.kts file separately, but it was confirmed in its dedicated test.

---

## Test Validation

✅ **All tests failed as expected** - This confirms the bugs exist in the unfixed code  
✅ **Counterexamples documented** - Each bug has specific evidence  
✅ **Test encodes expected behavior** - When the fix is implemented, these same tests will pass  
✅ **No false positives** - All failures correspond to actual bugs  

## Next Steps

1. ✅ Task 1 complete - Bug condition exploration test written and counterexamples documented
2. ⏭️ Task 2 - Write preservation property tests (before implementing fix)
3. ⏭️ Task 3 - Implement the fix
4. ⏭️ Task 3.5 - Verify bug condition exploration test now passes
5. ⏭️ Task 3.6 - Verify preservation tests still pass

## Files Created

- `software/infra/aws/src/test/kotlin/nl/vintik/mocknest/infra/aws/bugfix/SamApiGatewayConfigurationBugTest.kt` - Bug condition exploration test
- `.kiro/specs/sam-api-gateway-configuration-fix/bug-counterexamples.md` - This documentation file
